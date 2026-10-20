package com.credinoda.service;

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
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Servicio para la gestión de copias de seguridad en caliente (backup) y restauración segura
 * de la base de datos SQLite del sistema CrediNoda (CU-014, CU-015, RN-017, RN-018, RN-027, RN-029).
 * Implementa respaldo consistente vía VACUUM INTO, validación triple de integridad estructural
 * y restauración atómica con respaldo preventivo pre_restauracion automático, preservando intactas
 * las tablas de usuarios y auditoría de la base activa (RN-029, VP-024).
 */
public class BackupService {

    private static final byte[] SQLITE_HEADER = "SQLite format 3\0".getBytes(StandardCharsets.US_ASCII);
    private static final Set<String> REQUIRED_TABLES = Set.of("clientes", "creditos", "cuotas", "pagos");
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ss-a", Locale.ENGLISH);

    private final Connection connection;
    private final Path backupsDir;
    private final Autorizador autorizador;
    private final AuditoriaService auditoriaService;

    public BackupService(Connection connection, Path backupsDir, Autorizador autorizador, AuditoriaService auditoriaService) {
        this.connection = Objects.requireNonNull(connection, "La conexión a la base de datos no puede ser nula.");
        this.backupsDir = Objects.requireNonNull(backupsDir, "El directorio de respaldos no puede ser nulo.");
        this.autorizador = Objects.requireNonNull(autorizador, "El autorizador no puede ser nulo.");
        this.auditoriaService = auditoriaService;
        try {
            Files.createDirectories(this.backupsDir);
        } catch (IOException e) {
            System.err.println("Advertencia al crear directorio de backups: " + e.getMessage());
        }
    }

    public BackupService(Connection connection, Path backupsDir, Autorizador autorizador) {
        this(connection, backupsDir, autorizador, null);
    }

    public BackupService(Connection connection, Path backupsDir) {
        this(connection, backupsDir, new AutorizacionService(), null);
    }

    public BackupService(Connection connection, RutasSistema rutas) {
        this(connection, Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").backupsDir(), new AutorizacionService(), null);
    }

    public BackupService(Connection connection, RutasSistema rutas, AuditoriaService auditoriaService) {
        this(connection, Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").backupsDir(), new AutorizacionService(), auditoriaService);
    }

    public BackupService(Connection connection) {
        this(connection, ConfiguracionLocalStore.cargarRutas()
                .map(RutasSistema::backupsDir)
                .orElseGet(() -> Path.of(System.getProperty("user.dir"), "backups")), new AutorizacionService(), null);
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
        String prefijoSeguro = (prefijo != null && !prefijo.isBlank()) ? prefijo.trim() : "creditos_backup";

        Path rutaDestino;
        if (destino == null) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER).toLowerCase(Locale.ROOT);
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
            if (!fileName.toLowerCase().endsWith(".db")) {
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

            if (auditoriaService != null && !"pre_restauracion".equals(prefijo)) {
                auditoriaService.registrar(
                        AccionAuditoria.CREAR_BACKUP,
                        "BACKUP",
                        resultado.getFileName().toString(),
                        "Respaldo generado en: " + resultado,
                        ResultadoAuditoria.EXITO
                );
            }

            return resultado;

        } catch (SQLException | IOException e) {
            throw new BackupException("No se pudo crear la copia de seguridad: " + e.getMessage(), e);
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
     * Reemplaza exclusivamente las 4 tablas de negocio, preservando usuarios y auditoría intactos (VP-024).
     *
     * @param rutaOrigen Ruta al archivo .db validado.
     * @return Path del respaldo preventivo automático 'pre_restauracion_*.db' generado.
     */
    public Path restaurarBackup(Path rutaOrigen) {
        autorizador.requirePermission(Permiso.BACKUP_GESTIONAR);
        validarArchivoBackup(rutaOrigen);

        // 1. Crear respaldo preventivo automático antes de sobrescribir
        Path respaldoPreventivo = crearBackup(null, "pre_restauracion");

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
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    long sizeBytes = attrs.size();
                    double sizeKb = Math.round((sizeBytes / 1024.0) * 10.0) / 10.0;
                    Instant instant = attrs.lastModifiedTime().toInstant();
                    LocalDateTime fechaMod = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());

                    backups.add(new BackupInfo(
                            path.getFileName().toString(),
                            path.toAbsolutePath().toString(),
                            sizeBytes,
                            sizeKb,
                            fechaMod
                    ));
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
}
