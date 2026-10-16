package com.credinoda.service;

import com.credinoda.domain.dto.BackupInfo;
import com.credinoda.domain.exception.BackupException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Pago;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de BackupService (CU-014, CU-015, RN-017, RN-018)")
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

    @BeforeEach
    void setUp() throws SQLException {
        dbFile = tempDir.resolve("test_creditos.db");
        backupsDir = tempDir.resolve("backups");

        connection = DatabaseConnection.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        SchemaInitializer.inicializar(connection);

        backupService = new BackupService(connection, backupsDir);
        clienteRepo = new ClienteRepository(connection);
        creditoRepo = new CreditoRepository(connection);
        pagoRepo = new PagoRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
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
}
