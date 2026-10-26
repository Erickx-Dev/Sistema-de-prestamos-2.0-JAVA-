package com.credinoda.service;

import com.credinoda.common.AppConfig;
import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.RutasSistema;
import com.credinoda.domain.dto.BackupInfo;
import com.credinoda.domain.exception.BackupException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.persistence.SchemaInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para la gestión de copias de seguridad en caliente (backup), respaldos automáticos diarios
 * con estrategia híbrida (apertura y cierre consolidado), política de retención inteligente y restauración
 * segura de la base de datos SQLite del sistema CrediNoda (CU-014, CU-015, CU-025, RN-017, RN-018, RN-027,
 * RN-029, RN-033, RN-034).
 */
public class BackupService {

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> REQUIRED_TABLES = Set.of("clientes", "creditos", "cuotas", "pagos");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("yyyy-MM-dd_hh-mm-ss-a")
            .toFormatter(Locale.ENGLISH);
    private static final Pattern FILENAME_TIMESTAMP_PATTERN = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-(?:am|pm))(?:_(\\d+))?\\.db$",
            Pattern.CASE_INSENSITIVE
    );

    private final Connection connection;
    private final Path backupsDir;
    private final Autorizador autorizador;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public BackupService(Connection connection,
                         Path backupsDir,
                         Autorizador autorizador,
                         AuditoriaService auditoriaService,
                         Clock clock) {
        this.connection = Objects.requireNonNull(connection, "La conexión a la base de datos no puede ser nula.");
        this.backupsDir = Objects.requireNonNull(backupsDir, "El directorio de respaldos no puede ser nulo.");
        this.autorizador = Objects.requireNonNull(autorizador, "El autorizador no puede ser nulo.");
        this.auditoriaService = auditoriaService;
        this.clock = Objects.requireNonNull(clock, "El reloj (Clock) no puede ser nulo.");
        try {
            Files.createDirectories(this.backupsDir);
        } catch (IOException e) {
            System.err.println("Advertencia al crear directorio de backups: " + e.getMessage());
        }
    }

    public BackupService(Connection connection, Path backupsDir, Autorizador autorizador, AuditoriaService auditoriaService) {
        this(connection, backupsDir, autorizador, auditoriaService, Clock.systemDefaultZone());
    }

    public BackupService(Connection connection, Path backupsDir, Autorizador autorizador) {
        this(connection, backupsDir, autorizador, null, Clock.systemDefaultZone());
    }

    public BackupService(Connection connection, Path backupsDir, Clock clock) {
        this(connection, backupsDir, new AutorizacionService(), null, clock);
    }

    public BackupService(Connection connection, Path backupsDir) {
        this(connection, backupsDir, new AutorizacionService(), null, Clock.systemDefaultZone());
    }

    public BackupService(Connection connection, RutasSistema rutas) {
        this(connection, Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").backupsDir(),
                new AutorizacionService(), null, Clock.systemDefaultZone());
    }

    public BackupService(Connection connection, RutasSistema rutas, AuditoriaService auditoriaService) {
        this(connection, Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").backupsDir(),
                new AutorizacionService(), auditoriaService, Clock.systemDefaultZone());
    }

    public BackupService(Connection connection, RutasSistema rutas, AuditoriaService auditoriaService, Clock clock) {
        this(connection, Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").backupsDir(),
                new AutorizacionService(), auditoriaService, clock);
    }

    public BackupService(Connection connection) {
        this(connection, ConfiguracionLocalStore.cargarRutas()
                .map(RutasSistema::backupsDir)
                .orElseGet(() -> Path.of(System.getProperty("user.dir"), "backups")),
                new AutorizacionService(), null, Clock.systemDefaultZone());
    }

    /**
     * Crea una nueva vista del servicio vinculada a un {@link Clock} específico (útil para pruebas deterministas).
     *
     * @param nuevoClock Reloj a utilizar.
     * @return Nueva instancia de BackupService con el reloj indicado.
     */
    public BackupService withClock(Clock nuevoClock) {
        return new BackupService(this.connection, this.backupsDir, this.autorizador, this.auditoriaService, nuevoClock);
    }

    /**
     * Genera una copia de seguridad consistente en caliente de la base de datos activa (CU-014, RN-017).
     *
     * @param destino Ruta destino opcional (si es nula, se genera en backupsDir con timestamp).
     * @param prefijo Prefijo del nombre del archivo (por defecto 'creditos_backup').
     * @return Path absoluto al archivo de copia de seguridad .db generado.
     */
    public Path crearBackup(Path destino, String prefijo) {
        autorizador.requirePermission(Permiso.BACKUP_GESTIONAR);
        String prefijoSeguro = (prefijo != null && !prefijo.isBlank()) ? prefijo.trim() : AppConfig.MANUAL_BACKUP_PREFIX;

        Path resultado = generarSnapshotInterno(destino, prefijoSeguro);

        if (auditoriaService != null && !AppConfig.PRE_RESTORE_BACKUP_PREFIX.equals(prefijoSeguro)) {
            auditoriaService.registrar(
                    AccionAuditoria.CREAR_BACKUP,
                    "BACKUP",
                    resultado.getFileName().toString(),
                    "Respaldo generado en: " + resultado,
                    ResultadoAuditoria.EXITO
            );
        }

        return resultado;
    }

    /**
     * Asegura la existencia del respaldo automático diario al iniciar el sistema o cruzar la medianoche (CU-025, RN-034, VP-030).
     * Si ya existe un archivo íntegro {@code creditos_auto_yyyy-MM-dd_*.db} para la fecha actual, no genera duplicados.
     * Se ejecuta en contexto de sistema (sin requerir rol ADMINISTRADOR).
     *
     * @return Path al archivo de respaldo automático vigente del día actual.
     */
    public Path asegurarBackupDiarioApertura() {
        List<Path> existentesHoy = listarArchivosAutomaticosDeFecha(LocalDate.now(clock));
        for (Path existente : existentesHoy) {
            if (esArchivoBackupValidoSilencioso(existente)) {
                purgarBackupsAntiguos();
                return existente.toAbsolutePath();
            }
        }

        Path nuevoRespaldo = generarSnapshotInterno(null, AppConfig.AUTO_BACKUP_PREFIX);
        validarArchivoBackup(nuevoRespaldo);
        int purgados = purgarBackupsAntiguos();

        if (auditoriaService != null) {
            auditoriaService.registrar(
                    AccionAuditoria.BACKUP_AUTOMATICO,
                    "BACKUP",
                    nuevoRespaldo.getFileName().toString(),
                    "Respaldo automático diario de apertura generado: " + nuevoRespaldo.getFileName()
                            + " (purgados por retención: " + purgados + ")",
                    ResultadoAuditoria.EXITO
            );
        }

        return nuevoRespaldo;
    }

    /**
     * Genera el respaldo automático final al cerrar la sesión o la aplicación, verifica su integridad
     * y reemplaza cualquier copia automática previa de la misma fecha, garantizando un único archivo
     * diario consolidado con el 100% de las operaciones del día (CU-025, RN-034, VP-030).
     * Se ejecuta en contexto de sistema (sin requerir rol ADMINISTRADOR).
     *
     * @return Path al archivo consolidado definitivo del día.
     */
    public Path consolidarBackupDiarioCierre() {
        LocalDate hoy = LocalDate.now(clock);
        List<Path> previosDeHoy = listarArchivosAutomaticosDeFecha(hoy);

        Path nuevoConsolidado = generarSnapshotInterno(null, AppConfig.AUTO_BACKUP_PREFIX);
        validarArchivoBackup(nuevoConsolidado);

        Path consolidadoNormalizado = nuevoConsolidado.toAbsolutePath().normalize();
        for (Path previo : previosDeHoy) {
            Path previoNormalizado = previo.toAbsolutePath().normalize();
            if (!previoNormalizado.equals(consolidadoNormalizado)) {
                try {
                    Files.deleteIfExists(previoNormalizado);
                } catch (IOException e) {
                    System.err.println("Advertencia al remover copia previa del día: " + e.getMessage());
                }
            }
        }

        // Si el archivo consolidado se generó en el mismo segundo que la copia previa y recibió sufijo (_1),
        // lo renombramos al nombre canónico limpio una vez eliminada la copia previa.
        nuevoConsolidado = normalizarNombreSinSufijoColision(nuevoConsolidado);

        int purgados = purgarBackupsAntiguos();

        if (auditoriaService != null) {
            auditoriaService.registrar(
                    AccionAuditoria.BACKUP_AUTOMATICO,
                    "BACKUP",
                    nuevoConsolidado.getFileName().toString(),
                    "Respaldo automático diario consolidado al cierre: " + nuevoConsolidado.getFileName()
                            + " (copias previas del día reemplazadas: " + previosDeHoy.size()
                            + ", purgados por retención: " + purgados + ")",
                    ResultadoAuditoria.EXITO
            );
        }

        return nuevoConsolidado;
    }

    /**
     * Ejecuta la política triple de retención y purga automática sobre {@code backupsDir} (CU-025, RN-034, VP-031):
     * <ol>
     *   <li>Automáticos ({@code creditos_auto_*.db}): conserva las {@link AppConfig#MAX_AUTO_BACKUPS} (15) copias más recientes.</li>
     *   <li>Manuales ({@code creditos_backup_*.db} en {@code backupsDir}): elimina los que superen {@link AppConfig#MANUAL_BACKUP_RETENTION_DAYS} (30) días calendario.</li>
     *   <li>Preventivos ({@code pre_restauracion_*.db}): conserva los {@link AppConfig#MAX_PRE_RESTORE_BACKUPS} (5) más recientes.</li>
     * </ol>
     * Las copias exportadas fuera de {@code backupsDir} permanecen intactas.
     *
     * @return Cantidad total de archivos de respaldo antiguos eliminados.
     */
    public int purgarBackupsAntiguos() {
        if (!Files.exists(backupsDir)) {
            return 0;
        }

        List<Path> automaticos = new ArrayList<>();
        List<Path> manuales = new ArrayList<>();
        List<Path> preventivos = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.db")) {
            for (Path path : stream) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (fileName.startsWith(AppConfig.AUTO_BACKUP_PREFIX + "_")) {
                    automaticos.add(path);
                } else if (fileName.startsWith(AppConfig.MANUAL_BACKUP_PREFIX + "_")) {
                    manuales.add(path);
                } else if (fileName.startsWith(AppConfig.PRE_RESTORE_BACKUP_PREFIX + "_")) {
                    preventivos.add(path);
                }
            }
        } catch (IOException e) {
            System.err.println("Advertencia al inspeccionar directorio de backups para purga: " + e.getMessage());
            return 0;
        }

        int eliminados = 0;
        Comparator<Path> masRecientePrimero = crearComparadorMasRecientePrimero();

        // 1. Retención de respaldos automáticos: conservar máximo 15 más recientes
        automaticos.sort(masRecientePrimero);
        for (int i = AppConfig.MAX_AUTO_BACKUPS; i < automaticos.size(); i++) {
            if (eliminarSilenciosamente(automaticos.get(i))) {
                eliminados++;
            }
        }

        // 2. Retención de respaldos manuales en backupsDir: plazo máximo de 30 días desde su modificación
        Instant limiteManual = Instant.now(clock).minus(Duration.ofDays(AppConfig.MANUAL_BACKUP_RETENTION_DAYS));
        for (Path manual : manuales) {
            Instant instanteArchivo = obtenerInstanteParaRetencionManual(manual);
            if (instanteArchivo.isBefore(limiteManual)) {
                if (eliminarSilenciosamente(manual)) {
                    eliminados++;
                }
            }
        }

        // 3. Retención de respaldos preventivos pre_restauracion: conservar máximo 5 más recientes
        preventivos.sort(masRecientePrimero);
        for (int i = AppConfig.MAX_PRE_RESTORE_BACKUPS; i < preventivos.size(); i++) {
            if (eliminarSilenciosamente(preventivos.get(i))) {
                eliminados++;
            }
        }

        return eliminados;
    }

    /**
     * Consulta si existe un respaldo automático correspondiente al día actual en {@code backupsDir}.
     *
     * @return Optional con los metadatos {@link BackupInfo} del respaldo automático de hoy.
     */
    public Optional<BackupInfo> obtenerRespaldoAutomaticoDeHoy() {
        List<Path> archivosHoy = listarArchivosAutomaticosDeFecha(LocalDate.now(clock));
        if (archivosHoy.isEmpty()) {
            return Optional.empty();
        }
        archivosHoy.sort(crearComparadorMasRecientePrimero());
        Path masReciente = archivosHoy.getFirst();
        try {
            return Optional.of(construirBackupInfo(masReciente));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Verifica que un archivo sea una base de datos SQLite íntegra y contenga las 4 tablas obligatorias (CU-015, RN-018).
     *
     * @param rutaArchivo Ruta al archivo .db candidato.
     * @return true si supera las tres validaciones estructurales.
     * @throws BackupException Si el archivo no existe, no es legible, no es SQLite o le faltan tablas del negocio.
     */
    public static boolean validarArchivoBackup(Path rutaArchivo) {
        if (rutaArchivo == null || !Files.exists(rutaArchivo)) {
            throw new BackupException("El archivo de respaldo especificado no existe.");
        }

        if (!Files.isRegularFile(rutaArchivo) || !Files.isReadable(rutaArchivo)) {
            throw new BackupException("El archivo de respaldo no es un archivo legible válido.");
        }

        try {
            long tamano = Files.size(rutaArchivo);
            if (tamano < SQLITE_HEADER.length) {
                throw new BackupException("El archivo seleccionado está vacío o no es una base de datos SQLite válida.");
            }

            // 1. Verificación de cabecera binaria mágica (SQLite format 3\0)
            byte[] header = new byte[SQLITE_HEADER.length];
            try (InputStream is = Files.newInputStream(rutaArchivo)) {
                int read = is.read(header);
                if (read < SQLITE_HEADER.length || !Arrays.equals(header, SQLITE_HEADER)) {
                    throw new BackupException("El archivo seleccionado no tiene un formato SQLite válido.");
                }
            }

            // 2. Verificación de integridad SQLite (PRAGMA integrity_check) y tablas obligatorias
            String jdbcUrl = "jdbc:sqlite:" + rutaArchivo.toAbsolutePath().toString().replace("\\", "/");
            try (Connection checkConn = DriverManager.getConnection(jdbcUrl)) {
                try (Statement stmt = checkConn.createStatement();
                     ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
                    if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                        throw new BackupException("El archivo de base de datos está dañado (falló PRAGMA integrity_check).");
                    }
                }

                // 3. Verificación de existencia de las 4 tablas requeridas
                Set<String> tablas = new HashSet<>();
                try (Statement stmt = checkConn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                    while (rs.next()) {
                        tablas.add(rs.getString("name").toLowerCase());
                    }
                }

                Set<String> faltantes = new TreeSet<>(REQUIRED_TABLES);
                faltantes.removeAll(tablas);
                if (!faltantes.isEmpty()) {
                    String lista = String.join(", ", faltantes);
                    throw new BackupException("La base de datos no pertenece a CrediNoda (faltan tablas: " + lista + ").");
                }
            }

            return true;

        } catch (BackupException e) {
            throw e;
        } catch (Exception e) {
            throw new BackupException("Error al inspeccionar el archivo de respaldo: " + e.getMessage(), e);
        }
    }

    /**
     * Valida un archivo de respaldo, crea un respaldo preventivo de la base activa y restaura los datos (CU-015, RN-018, RN-029).
     * Reemplaza exclusivamente las 4 tablas de negocio, preservando usuarios, auditoría y control de licencia intactos (VP-024, VP-029).
     *
     * @param rutaOrigen Ruta al archivo .db validado.
     * @return Path del respaldo preventivo automático 'pre_restauracion_*.db' generado.
     */
    public Path restaurarBackup(Path rutaOrigen) {
        autorizador.requirePermission(Permiso.BACKUP_GESTIONAR);
        validarArchivoBackup(rutaOrigen);

        // 1. Crear respaldo preventivo automático antes de sobrescribir y aplicar retención de preventivos
        Path respaldoPreventivo = generarSnapshotInterno(null, AppConfig.PRE_RESTORE_BACKUP_PREFIX);
        purgarBackupsAntiguos();

        boolean autoCommitOriginal = true;
        boolean attached = false;
        try {
            autoCommitOriginal = connection.getAutoCommit();
            String rutaNormalizada = rutaOrigen.toAbsolutePath().toString().replace("\\", "/");

            try (Statement stmt = connection.createStatement()) {
                stmt.execute("ATTACH DATABASE '" + rutaNormalizada + "' AS backup_src;");
                attached = true;
            }

            connection.setAutoCommit(false);

            try (Statement stmt = connection.createStatement()) {
                // Desactivar temporalmente foreign keys para el volcado
                stmt.execute("PRAGMA foreign_keys = OFF;");

                // Vaciar exclusivamente tablas activas de negocio (RN-029)
                stmt.execute("DELETE FROM pagos;");
                stmt.execute("DELETE FROM cuotas;");
                stmt.execute("DELETE FROM creditos;");
                stmt.execute("DELETE FROM clientes;");

                // Insertar registros desde el backup
                stmt.execute("""
                        INSERT INTO clientes (id, nombre, telefono, documento, fecha_registro)
                        SELECT id, nombre, telefono, documento, fecha_registro FROM backup_src.clientes;
                        """);

                stmt.execute("""
                        INSERT INTO creditos (id, cliente_id, capital, tasa_mensual, plazo_meses, total_intereses, total_pagar, frecuencia_pago, fecha_inicio, estado)
                        SELECT id, cliente_id, capital, tasa_mensual, plazo_meses, total_intereses, total_pagar, frecuencia_pago, fecha_inicio, estado
                        FROM backup_src.creditos;
                        """);

                stmt.execute("""
                        INSERT INTO cuotas (id, credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora)
                        SELECT id, credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora
                        FROM backup_src.cuotas;
                        """);

                stmt.execute("""
                        INSERT INTO pagos (id, credito_id, cuota_id, fecha_pago, monto)
                        SELECT id, credito_id, cuota_id, fecha_pago, monto
                        FROM backup_src.pagos;
                        """);

                // Sincronizar y preservar control de licencia y marcas de tiempo (RN-033, VP-029)
                boolean backupTieneLicencia = false;
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM backup_src.sqlite_master WHERE type='table' AND name='control_licencia';")) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        backupTieneLicencia = true;
                    }
                }
                if (backupTieneLicencia) {
                    stmt.execute("""
                            UPDATE control_licencia
                            SET ultimo_timestamp_utc = MAX(
                                control_licencia.ultimo_timestamp_utc,
                                (SELECT COALESCE(MAX(ultimo_timestamp_utc), 0) FROM backup_src.control_licencia)
                            )
                            WHERE id = 1;
                            """);
                    stmt.execute("""
                            INSERT INTO control_licencia (id, licencia_token, hardware_id, titular, fecha_emision, fecha_expiracion, ultimo_timestamp_utc, hmac_integridad)
                            SELECT id, licencia_token, hardware_id, titular, fecha_emision, fecha_expiracion, ultimo_timestamp_utc, hmac_integridad
                            FROM backup_src.control_licencia
                            WHERE id = 1 AND NOT EXISTS (SELECT 1 FROM control_licencia WHERE id = 1);
                            """);
                }

                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            connection.commit();

            // Garantizar integridad estructural DDL de índices y esquema
            SchemaInitializer.inicializar(connection);

            if (auditoriaService != null) {
                auditoriaService.registrar(
                        AccionAuditoria.RESTAURAR_BACKUP,
                        "BACKUP",
                        rutaOrigen.getFileName().toString(),
                        "Restaurado desde: " + rutaOrigen.getFileName() + ", Preventivo: " + respaldoPreventivo.getFileName(),
                        ResultadoAuditoria.EXITO
                );
            }

        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                System.err.println("Fallo al hacer rollback tras error de restauración: " + rollbackEx.getMessage());
            }
            throw new BackupException("Error durante la restauración de la base de datos: " + e.getMessage(), e);
        } finally {
            if (attached) {
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("DETACH DATABASE backup_src;");
                } catch (SQLException ignored) {
                }
            }
            try {
                connection.setAutoCommit(autoCommitOriginal);
            } catch (SQLException ignored) {
            }
        }

        return respaldoPreventivo;
    }

    /**
     * Retorna la lista de archivos de copia de seguridad .db en el directorio de backups,
     * ordenados del más reciente al más antiguo (CU-014, RN-017).
     *
     * @return Lista de DTO BackupInfo con metadatos del archivo y tamaño en KB.
     */
    public List<BackupInfo> listarBackups() {
        autorizador.requirePermission(Permiso.BACKUP_GESTIONAR);
        if (!Files.exists(backupsDir)) {
            return List.of();
        }

        List<BackupInfo> backups = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.db")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    backups.add(construirBackupInfo(path));
                }
            }
        } catch (IOException e) {
            throw new BackupException("Error al listar archivos de respaldo: " + e.getMessage(), e);
        }

        backups.sort(Comparator.comparing(BackupInfo::fechaModificacion).reversed());
        return backups;
    }

    /**
     * Retorna el directorio configurado para respaldos locales.
     */
    public Path getBackupsDir() {
        return backupsDir;
    }

    private Path generarSnapshotInterno(Path destino, String prefijoSeguro) {
        Path rutaDestino;
        if (destino == null) {
            String timestamp = LocalDateTime.now(clock).format(TIMESTAMP_FORMATTER).toLowerCase(Locale.ROOT);
            Path candidato = backupsDir.resolve(String.format("%s_%s.db", prefijoSeguro, timestamp));
            if (Files.exists(candidato)) {
                int seq = 1;
                while (Files.exists(backupsDir.resolve(String.format("%s_%s_%d.db", prefijoSeguro, timestamp, seq)))) {
                    seq++;
                }
                rutaDestino = backupsDir.resolve(String.format("%s_%s_%d.db", prefijoSeguro, timestamp, seq));
            } else {
                rutaDestino = candidato;
            }
        } else {
            String fileName = destino.getFileName().toString();
            if (!fileName.toLowerCase(Locale.ROOT).endsWith(".db")) {
                rutaDestino = destino.resolveSibling(fileName + ".db");
            } else {
                rutaDestino = destino;
            }
        }

        try {
            if (rutaDestino.getParent() != null) {
                Files.createDirectories(rutaDestino.getParent());
            }

            // Confirmar transacciones pendientes antes de iniciar el snapshot
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            // Si el archivo ya existía, se elimina previamente porque SQLite VACUUM INTO requiere archivo nuevo
            Files.deleteIfExists(rutaDestino);

            String pathNormalizado = rutaDestino.toAbsolutePath().toString().replace("\\", "/");
            String sql = "VACUUM INTO '" + pathNormalizado + "'";

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
            }

            Path resultado = rutaDestino.toAbsolutePath();
            try {
                Files.setLastModifiedTime(resultado, FileTime.from(Instant.now(clock)));
            } catch (IOException ignored) {
            }

            return resultado;

        } catch (SQLException | IOException e) {
            throw new BackupException("No se pudo crear la copia de seguridad: " + e.getMessage(), e);
        }
    }

    private List<Path> listarArchivosAutomaticosDeFecha(LocalDate fecha) {
        if (!Files.exists(backupsDir)) {
            return List.of();
        }
        String prefijoDia = (AppConfig.AUTO_BACKUP_PREFIX + "_" + fecha.format(DateTimeFormatter.ISO_LOCAL_DATE) + "_")
                .toLowerCase(Locale.ROOT);
        List<Path> encontrados = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupsDir, "*.db")) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.startsWith(prefijoDia)) {
                        encontrados.add(path);
                    }
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        encontrados.sort(crearComparadorMasRecientePrimero());
        return encontrados;
    }

    private Path normalizarNombreSinSufijoColision(Path archivo) {
        String nombre = archivo.getFileName().toString();
        Matcher m = FILENAME_TIMESTAMP_PATTERN.matcher(nombre);
        if (m.find() && m.group(2) != null) {
            String nombreLimpio = nombre.substring(0, m.start(2) - 1) + ".db";
            Path destinoLimpio = archivo.resolveSibling(nombreLimpio);
            if (!Files.exists(destinoLimpio)) {
                try {
                    return Files.move(archivo, destinoLimpio, StandardCopyOption.REPLACE_EXISTING).toAbsolutePath();
                } catch (IOException ignored) {
                }
            }
        }
        return archivo;
    }

    private boolean esArchivoBackupValidoSilencioso(Path ruta) {
        try {
            return validarArchivoBackup(ruta);
        } catch (Exception e) {
            return false;
        }
    }

    private Comparator<Path> crearComparadorMasRecientePrimero() {
        return (p1, p2) -> {
            Instant i1 = obtenerInstanteOrdenamiento(p1);
            Instant i2 = obtenerInstanteOrdenamiento(p2);
            int cmp = i2.compareTo(i1);
            if (cmp != 0) {
                return cmp;
            }
            int seq1 = extraerSecuenciaColision(p1);
            int seq2 = extraerSecuenciaColision(p2);
            int seqCmp = Integer.compare(seq2, seq1);
            if (seqCmp != 0) {
                return seqCmp;
            }
            return p2.getFileName().toString().compareToIgnoreCase(p1.getFileName().toString());
        };
    }

    private Instant obtenerInstanteOrdenamiento(Path path) {
        Optional<Instant> fromName = extraerInstanteDeNombre(path);
        if (fromName.isPresent()) {
            return fromName.get();
        }
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private Instant obtenerInstanteParaRetencionManual(Path path) {
        Instant lastMod = Instant.now(clock);
        try {
            lastMod = Files.getLastModifiedTime(path).toInstant();
        } catch (IOException ignored) {
        }
        Optional<Instant> fromName = extraerInstanteDeNombre(path);
        if (fromName.isPresent() && fromName.get().isBefore(lastMod)) {
            return fromName.get();
        }
        return lastMod;
    }

    private Optional<Instant> extraerInstanteDeNombre(Path path) {
        String name = path.getFileName().toString();
        Matcher matcher = FILENAME_TIMESTAMP_PATTERN.matcher(name);
        if (matcher.find()) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(matcher.group(1), TIMESTAMP_FORMATTER);
                return Optional.of(ldt.atZone(clock.getZone()).toInstant());
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    private int extraerSecuenciaColision(Path path) {
        String name = path.getFileName().toString();
        Matcher matcher = FILENAME_TIMESTAMP_PATTERN.matcher(name);
        if (matcher.find() && matcher.group(2) != null) {
            try {
                return Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private boolean eliminarSilenciosamente(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            System.err.println("Advertencia al purgar respaldo antiguo " + path + ": " + e.getMessage());
            return false;
        }
    }

    private BackupInfo construirBackupInfo(Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long sizeBytes = attrs.size();
        double sizeKb = Math.round((sizeBytes / 1024.0) * 10.0) / 10.0;
        Instant instant = attrs.lastModifiedTime().toInstant();
        LocalDateTime fechaMod = LocalDateTime.ofInstant(instant, clock.getZone());

        return new BackupInfo(
                path.getFileName().toString(),
                path.toAbsolutePath().toString(),
                sizeBytes,
                sizeKb,
                fechaMod
        );
    }
}
