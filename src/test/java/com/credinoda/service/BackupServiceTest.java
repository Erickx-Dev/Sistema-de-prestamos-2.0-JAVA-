package com.credinoda.service;

import com.credinoda.common.AppConfig;
import com.credinoda.domain.dto.BackupInfo;
import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.BackupException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Pago;
import com.credinoda.domain.model.Rol;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.AuditoriaRepository;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de BackupService (CU-014, CU-015, CU-025, RN-017, RN-018, RN-034, VP-030, VP-031)")
class BackupServiceTest {

    @TempDir
    Path tempDir;

    private Path dbFile;
    private Path backupsDir;
    private Connection connection;
    private BackupService backupService;
    private ClienteRepository clienteRepo;
    private CreditoRepository creditoRepo;
    private PagoRepository pagoRepo;
    private AuditoriaRepository auditoriaRepo;
    private AuditoriaService auditoriaService;

    @BeforeEach
    void setUp() throws SQLException {
        dbFile = tempDir.resolve("test_creditos.db");
        backupsDir = tempDir.resolve("backups");

        connection = DatabaseConnection.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        SchemaInitializer.inicializar(connection);

        auditoriaRepo = new AuditoriaRepository(connection);
        auditoriaService = new AuditoriaService(auditoriaRepo);
        backupService = new BackupService(connection, backupsDir, new AutorizacionService(), auditoriaService);
        clienteRepo = new ClienteRepository(connection);
        creditoRepo = new CreditoRepository(connection);
        pagoRepo = new PagoRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ContextoSeguridad.limpiarSesion();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("crearBackup() por defecto genera archivo .db válido en backupsDir (RN-017)")
    void testCrearBackupPorDefecto() {
        Path backupGenerado = backupService.crearBackup(null, null);

        assertThat(backupGenerado).isNotNull();
        assertThat(Files.exists(backupGenerado)).isTrue();
        assertThat(backupGenerado.getFileName().toString()).startsWith("creditos_backup_");
        assertThat(backupGenerado.getFileName().toString()).endsWith(".db");
        assertThat(backupGenerado.getFileName().toString())
                .matches("^creditos_backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}-(am|pm)(_\\d+)?\\.db$");
        assertThat(backupGenerado.getParent()).isEqualTo(backupsDir.toAbsolutePath());

        // Debe superar la validación triple
        assertThat(BackupService.validarArchivoBackup(backupGenerado)).isTrue();
    }

    @Test
    @DisplayName("crearBackup() con ruta personalizada sin extensión .db agrega la extensión automáticamente")
    void testCrearBackupRutaPersonalizadaSinExtension() {
        Path destinoSinExt = tempDir.resolve("copia_personalizada");
        Path backupGenerado = backupService.crearBackup(destinoSinExt, "mi_backup");

        assertThat(backupGenerado.getFileName().toString()).isEqualTo("copia_personalizada.db");
        assertThat(Files.exists(backupGenerado)).isTrue();
        assertThat(BackupService.validarArchivoBackup(backupGenerado)).isTrue();
    }

    @Test
    @DisplayName("crearBackup() en el mismo segundo con mismo prefijo genera sufijo numérico para evitar colisiones")
    void testCrearBackupMismoSegundoSinColision() {
        Path b1 = backupService.crearBackup(null, "colision");
        Path b2 = backupService.crearBackup(null, "colision");

        assertThat(b1).isNotEqualTo(b2);
        assertThat(Files.exists(b1)).isTrue();
        assertThat(Files.exists(b2)).isTrue();
        assertThat(b2.getFileName().toString()).contains("colision_");
        assertThat(BackupService.validarArchivoBackup(b1)).isTrue();
        assertThat(BackupService.validarArchivoBackup(b2)).isTrue();
    }

    @Test
    @DisplayName("validarArchivoBackup() rechaza archivos inexistentes, vacíos, sin firma SQLite o sin tablas")
    void testValidarArchivoBackupCasosInvalidos() throws IOException, SQLException {
        // 1. Archivo inexistente
        Path inexistente = tempDir.resolve("fantasma.db");
        assertThatThrownBy(() -> BackupService.validarArchivoBackup(inexistente))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("no existe");

        // 2. Archivo vacío (0 bytes)
        Path vacio = tempDir.resolve("vacio.db");
        Files.createFile(vacio);
        assertThatThrownBy(() -> BackupService.validarArchivoBackup(vacio))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("está vacío");

        // 3. Archivo sin formato SQLite válido
        Path textoPlano = tempDir.resolve("falso.db");
        Files.writeString(textoPlano, "Este no es un archivo SQLite pero tiene mas de dieciseis bytes");
        assertThatThrownBy(() -> BackupService.validarArchivoBackup(textoPlano))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("no tiene un formato SQLite válido");

        // 4. Base SQLite válida pero ajena a CrediNoda (sin las 4 tablas)
        Path dbAjena = tempDir.resolve("ajena.db");
        try (Connection cAjena = DriverManager.getConnection("jdbc:sqlite:" + dbAjena.toAbsolutePath())) {
            try (Statement s = cAjena.createStatement()) {
                s.execute("CREATE TABLE usuarios (id INTEGER PRIMARY KEY, login TEXT);");
            }
        }
        assertThatThrownBy(() -> BackupService.validarArchivoBackup(dbAjena))
                .isInstanceOf(BackupException.class)
                .hasMessageContaining("no pertenece a CrediNoda")
                .hasMessageContaining("faltan tablas");
    }

    @Test
    @DisplayName("listarBackups() retorna archivos ordenados por fecha de modificación descendente (RN-017)")
    void testListarBackupsOrdenCronologico() throws InterruptedException {
        backupService.crearBackup(null, "primero");
        Thread.sleep(50);
        backupService.crearBackup(null, "segundo");

        List<BackupInfo> lista = backupService.listarBackups();
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).nombreArchivo()).contains("segundo");
        assertThat(lista.get(1).nombreArchivo()).contains("primero");
        assertThat(lista.get(0).tamanoKb()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("CU-015 y RN-018: Ciclo completo de backup -> borrado -> validación -> restauración en caliente")
    void testCicloCompletoRestauracionEnCaliente() throws SQLException {
        // 1. Insertamos datos originales
        Cliente cli = clienteRepo.crear(new Cliente("Erickson Original", "3001234567", "DOC-ORIGINAL"));
        Credito cred = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("1000000.00"),
                new BigDecimal("0.12"),
                1,
                new BigDecimal("120000.00"),
                new BigDecimal("1120000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));
        Cuota q = creditoRepo.crearCuota(new Cuota(null, cred.getId(), 1, LocalDate.now().plusDays(15), new BigDecimal("560000.00"), EstadoCuota.PENDIENTE, false));
        pagoRepo.crear(new Pago(cred.getId(), q.getId(), new BigDecimal("200000.00"), LocalDate.now()));

        // 2. Creamos copia de seguridad del estado original
        Path backupOriginal = backupService.crearBackup(null, "punto_restauracion");

        // 3. Mutamos/vaciamos la base activa simulando pérdida o corrupción de datos
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            stmt.execute("DELETE FROM pagos;");
            stmt.execute("DELETE FROM cuotas;");
            stmt.execute("DELETE FROM creditos;");
            stmt.execute("DELETE FROM clientes;");
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        assertThat(clienteRepo.listarTodos()).isEmpty();
        assertThat(creditoRepo.listarTodos()).isEmpty();
        assertThat(pagoRepo.listarTodos()).isEmpty();

        // 4. Ejecutamos restauración en caliente
        Path respaldoPreventivo = backupService.restaurarBackup(backupOriginal);

        // Verificamos que se creó un respaldo preventivo automático 'pre_restauracion_*.db'
        assertThat(respaldoPreventivo).isNotNull();
        assertThat(respaldoPreventivo.getFileName().toString()).startsWith("pre_restauracion_");
        assertThat(Files.exists(respaldoPreventivo)).isTrue();

        // Verificamos que los datos fueron 100% restaurados
        List<Cliente> clientesRestaurados = clienteRepo.listarTodos();
        assertThat(clientesRestaurados).hasSize(1);
        assertThat(clientesRestaurados.getFirst().getNombre()).isEqualTo("Erickson Original");

        List<Credito> creditosRestaurados = creditoRepo.listarTodos();
        assertThat(creditosRestaurados).hasSize(1);
        assertThat(creditosRestaurados.getFirst().getCapital()).isEqualByComparingTo(new BigDecimal("1000000.00"));

        List<Pago> pagosRestaurados = pagoRepo.listarTodos();
        assertThat(pagosRestaurados).hasSize(1);
        assertThat(pagosRestaurados.getFirst().getMonto()).isEqualByComparingTo(new BigDecimal("200000.00"));
    }

    @Test
    @DisplayName("VP-030: Consolidación diaria apertura (am) + cierre (pm) reemplaza copia de la mañana y conserva todos los pagos del día")
    void testVP030ConsolidacionDiariaAperturaYCierre() throws SQLException, IOException {
        ZoneId zone = ZoneId.of("America/Bogota");
        Clock clockManana = Clock.fixed(LocalDateTime.of(2026, 10, 25, 8, 15, 30).atZone(zone).toInstant(), zone);
        Clock clockTarde = Clock.fixed(LocalDateTime.of(2026, 10, 25, 18, 45, 10).atZone(zone).toInstant(), zone);

        // Simulamos jornada operada por un usuario con rol OPERADOR (RN-034: ejecución en contexto de sistema)
        ContextoSeguridad.setSesionActual(new SesionUsuario(2, "cajero_dia", "Cajero Operador", Rol.OPERADOR, LocalDateTime.now(clockManana)));

        // 1. Apertura en la mañana (08:15:30 AM)
        BackupService serviceManana = backupService.withClock(clockManana);
        Path backupApertura = serviceManana.asegurarBackupDiarioApertura();

        assertThat(backupApertura).isNotNull();
        assertThat(Files.exists(backupApertura)).isTrue();
        assertThat(backupApertura.getFileName().toString())
                .isEqualTo("creditos_auto_2026-10-25_08-15-30-am.db");
        assertThat(BackupService.validarArchivoBackup(backupApertura)).isTrue();

        // Invocar de nuevo en la mañana no debe duplicar archivos
        Path segundaInvocacionManana = serviceManana.asegurarBackupDiarioApertura();
        assertThat(segundaInvocacionManana).isEqualTo(backupApertura);

        // 2. Operaciones financieras durante el día
        Cliente cli = clienteRepo.crear(new Cliente("Maria Consolidacion", "3159998877", "DOC-VP030"));
        Credito cred = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("500000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("50000.00"),
                new BigDecimal("550000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.of(2026, 10, 25),
                EstadoCredito.ACTIVO
        ));
        Cuota cuota = creditoRepo.crearCuota(new Cuota(null, cred.getId(), 1, LocalDate.of(2026, 11, 9), new BigDecimal("275000.00"), EstadoCuota.PENDIENTE, false));
        pagoRepo.crear(new Pago(cred.getId(), cuota.getId(), new BigDecimal("275000.00"), LocalDate.of(2026, 10, 25)));

        // 3. Cierre y consolidación en la tarde (06:45:10 PM)
        BackupService serviceTarde = backupService.withClock(clockTarde);
        Path backupCierre = serviceTarde.consolidarBackupDiarioCierre();

        assertThat(backupCierre).isNotNull();
        assertThat(Files.exists(backupCierre)).isTrue();
        assertThat(backupCierre.getFileName().toString())
                .isEqualTo("creditos_auto_2026-10-25_06-45-10-pm.db");
        assertThat(BackupService.validarArchivoBackup(backupCierre)).isTrue();

        // La copia de la mañana fue reemplazada y eliminada; solo queda 1 archivo del día
        assertThat(Files.exists(backupApertura)).isFalse();
        try (Stream<Path> files = Files.list(backupsDir)) {
            List<Path> archivosDia = files.filter(Files::isRegularFile).toList();
            assertThat(archivosDia).hasSize(1);
            assertThat(archivosDia.getFirst()).isEqualTo(backupCierre);
        }

        // 4. Verificar que el archivo consolidado de la tarde contiene el pago registrado durante la jornada
        try (Connection connBackup = DatabaseConnection.getConnection(backupCierre)) {
            PagoRepository pagoRepoBackup = new PagoRepository(connBackup);
            List<Pago> pagosEnBackup = pagoRepoBackup.listarTodos();
            assertThat(pagosEnBackup).hasSize(1);
            assertThat(pagosEnBackup.getFirst().getMonto()).isEqualByComparingTo(new BigDecimal("275000.00"));
        }

        // 5. Verificar trazabilidad en bitácora de auditoría (AccionAuditoria.BACKUP_AUTOMATICO)
        List<EventoAuditoria> eventosAuto = auditoriaService.buscar(
                new FiltroAuditoria(null, null, null, AccionAuditoria.BACKUP_AUTOMATICO, null, null, 10, 0)
        );
        assertThat(eventosAuto).hasSize(2);
    }

    @Test
    @DisplayName("VP-031 (Parte 1): Purga de retención conserva exactamente los 15 respaldos automáticos más recientes de 17 días distintos")
    void testVP031PurgaRetencionAutomaticosTop15() throws IOException {
        ZoneId zone = ZoneId.of("America/Bogota");
        List<Path> generados = new ArrayList<>();

        // Simulamos 17 días consecutivos de respaldos automáticos (del 1 al 17 de octubre de 2026)
        for (int dia = 1; dia <= 17; dia++) {
            Instant instanteDia = LocalDateTime.of(2026, 10, dia, 9, 0, 0).atZone(zone).toInstant();
            BackupService serviceDia = backupService.withClock(Clock.fixed(instanteDia, zone));
            Path generado = serviceDia.asegurarBackupDiarioApertura();
            generados.add(generado);
        }

        try (Stream<Path> stream = Files.list(backupsDir)) {
            List<String> nombresRestantes = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(AppConfig.AUTO_BACKUP_PREFIX + "_"))
                    .sorted()
                    .toList();

            // Debe conservar exactamente los 15 más recientes (días 03 al 17) y haber purgado los días 01 y 02
            assertThat(nombresRestantes).hasSize(AppConfig.MAX_AUTO_BACKUPS);
            assertThat(Files.exists(generados.get(0))).isFalse(); // 2026-10-01 purgado
            assertThat(Files.exists(generados.get(1))).isFalse(); // 2026-10-02 purgado
            for (int i = 2; i < 17; i++) {
                assertThat(Files.exists(generados.get(i))).isTrue();
            }
        }
    }

    @Test
    @DisplayName("VP-031 (Parte 2): Purga de retención elimina manuales > 30 días en backupsDir, conserva manuales <= 30 días, respeta externos y conserva 5 preventivos")
    void testVP031PurgaRetencionManualesYPreventivos() throws IOException {
        ZoneId zone = ZoneId.of("America/Bogota");
        LocalDateTime fechaReferencia = LocalDateTime.of(2026, 10, 25, 12, 0, 0);

        // 1. Crear respaldos manuales en backupsDir con distintas antigüedades
        Path manual45Dias = backupService.withClock(Clock.fixed(fechaReferencia.minusDays(45).atZone(zone).toInstant(), zone))
                .crearBackup(null, AppConfig.MANUAL_BACKUP_PREFIX);
        Path manual31Dias = backupService.withClock(Clock.fixed(fechaReferencia.minusDays(31).atZone(zone).toInstant(), zone))
                .crearBackup(null, AppConfig.MANUAL_BACKUP_PREFIX);
        Path manual29Dias = backupService.withClock(Clock.fixed(fechaReferencia.minusDays(29).atZone(zone).toInstant(), zone))
                .crearBackup(null, AppConfig.MANUAL_BACKUP_PREFIX);
        Path manual5Dias = backupService.withClock(Clock.fixed(fechaReferencia.minusDays(5).atZone(zone).toInstant(), zone))
                .crearBackup(null, AppConfig.MANUAL_BACKUP_PREFIX);

        // Respaldo manual exportado fuera de backupsDir con 60 días de antigüedad (jamás debe borrarse)
        Path carpetaExterna = tempDir.resolve("exportados_usuario");
        Path manualExterno60Dias = backupService.withClock(Clock.fixed(fechaReferencia.minusDays(60).atZone(zone).toInstant(), zone))
                .crearBackup(carpetaExterna.resolve("creditos_backup_externo.db"), AppConfig.MANUAL_BACKUP_PREFIX);

        // 2. Crear 7 respaldos preventivos (pre_restauracion_*.db) en fechas distintas
        List<Path> preventivos = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Clock clockPrev = Clock.fixed(fechaReferencia.minusDays(10 - i).atZone(zone).toInstant(), zone);
            Path prev = backupService.withClock(clockPrev).crearBackup(null, AppConfig.PRE_RESTORE_BACKUP_PREFIX);
            preventivos.add(prev);
        }

        // 3. Ejecutar purga en la fecha de referencia (2026-10-25)
        BackupService serviceReferencia = backupService.withClock(Clock.fixed(fechaReferencia.atZone(zone).toInstant(), zone));
        int totalPurgados = serviceReferencia.purgarBackupsAntiguos();

        // Se deben haber purgado los 2 manuales vencidos (> 30 días) y los 2 preventivos más antiguos (de 7 quedan 5)
        assertThat(totalPurgados).isGreaterThanOrEqualTo(2);

        // Verificar manuales en backupsDir
        assertThat(Files.exists(manual45Dias)).isFalse();
        assertThat(Files.exists(manual31Dias)).isFalse();
        assertThat(Files.exists(manual29Dias)).isTrue();
        assertThat(Files.exists(manual5Dias)).isTrue();

        // Verificar que la copia externa permanece intacta
        assertThat(Files.exists(manualExterno60Dias)).isTrue();

        // Verificar preventivos: los 2 más antiguos eliminados, los 5 más recientes conservados
        assertThat(Files.exists(preventivos.get(0))).isFalse();
        assertThat(Files.exists(preventivos.get(1))).isFalse();
        for (int i = 2; i < 7; i++) {
            assertThat(Files.exists(preventivos.get(i))).isTrue();
        }

        try (Stream<Path> stream = Files.list(backupsDir)) {
            long countPreventivos = stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith(AppConfig.PRE_RESTORE_BACKUP_PREFIX + "_"))
                    .count();
            assertThat(countPreventivos).isEqualTo(AppConfig.MAX_PRE_RESTORE_BACKUPS);
        }
    }
}
