package com.credinoda.service;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.HardwareIdProvider;
import com.credinoda.common.LicenciaCryptoVerifier;
import com.credinoda.common.LicenciaKeyGeneratorTool;
import com.credinoda.domain.dto.ResultadoValidacionLicencia;
import com.credinoda.domain.model.EstadoLicencia;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.LicenciaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias de LicenciaService (RN-030 a RN-033, VP-026 a VP-029)")
class LicenciaServiceTest {

    @TempDir
    Path tempConfigDir;

    private Connection connection;
    private LicenciaRepository repository;
    private KeyPair keyPair;
    private LicenciaCryptoVerifier verifier;
    private HardwareIdProvider hardwareIdProvider;
    private String simulatedHardwareId;

    @BeforeEach
    void setUp() throws SQLException {
        System.setProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY, tempConfigDir.toString());

        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        repository = new LicenciaRepository(connection);

        keyPair = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        verifier = new LicenciaCryptoVerifier(keyPair.getPublic());

        simulatedHardwareId = "CN20-1234-5678-90AB";
        hardwareIdProvider = () -> simulatedHardwareId;
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        System.clearProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY);
    }

    @Test
    @DisplayName("Sin licencia registrada en el primer arranque debe retornar NO_REGISTRADA")
    void testPrimerArranqueSinLicencia() {
        LicenciaService service = new LicenciaService(repository, verifier, hardwareIdProvider, null, Clock.systemUTC());
        ResultadoValidacionLicencia resultado = service.validarEstadoActual();

        assertThat(resultado.esValida()).isFalse();
        assertThat(resultado.estado()).isEqualTo(EstadoLicencia.NO_REGISTRADA);
        assertThat(resultado.hardwareIdActual()).isEqualTo(simulatedHardwareId);
    }

    @Test
    @DisplayName("Activación exitosa persiste en persistencia dual y estado pasa a VALIDA")
    void testActivacionExitosa() {
        Instant instanteBase = Instant.parse("2026-10-24T12:00:00Z");
        Clock clock = Clock.fixed(instanteBase, ZoneOffset.UTC);
        LicenciaService service = new LicenciaService(repository, verifier, hardwareIdProvider, null, clock);

        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-001",
                "Cooperativa San Juan",
                simulatedHardwareId,
                LocalDate.of(2026, 10, 24),
                LocalDate.of(2027, 10, 24),
                "ANUAL"
        );

        ResultadoValidacionLicencia resultadoActivacion = service.activarLicencia(token);
        assertThat(resultadoActivacion.esValida()).isTrue();
        assertThat(resultadoActivacion.estado()).isEqualTo(EstadoLicencia.VALIDA);
        assertThat(resultadoActivacion.datos().getTitular()).isEqualTo("Cooperativa San Juan");

        // Al consultar nuevamente el estado actual, debe ser VALIDA
        ResultadoValidacionLicencia resultadoConsulta = service.validarEstadoActual();
        assertThat(resultadoConsulta.esValida()).isTrue();
        assertThat(resultadoConsulta.estado()).isEqualTo(EstadoLicencia.VALIDA);
        assertThat(resultadoConsulta.diasRestantes()).isEqualTo(365);
    }

    @Test
    @DisplayName("VP-026: Licencia emitida para otro equipo físico es rechazada con HARDWARE_NO_COINCIDE")
    void testRechazoHardwareNoCoincide() {
        LicenciaService service = new LicenciaService(repository, verifier, hardwareIdProvider, null, Clock.systemUTC());

        // Token firmado para otro Hardware ID
        String tokenOtroEquipo = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-002",
                "Comercializadora Norte",
                "CN20-9999-8888-7777",
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                "ANUAL"
        );

        ResultadoValidacionLicencia resultado = service.activarLicencia(tokenOtroEquipo);
        assertThat(resultado.esValida()).isFalse();
        assertThat(resultado.estado()).isEqualTo(EstadoLicencia.HARDWARE_NO_COINCIDE);
    }

    @Test
    @DisplayName("VP-027: Licencia con fecha expira_en superada es clasificada como EXPIRADA y permite renovación")
    void testExpiracionYRenovacionLicencia() {
        Instant instanteActivo = Instant.parse("2026-06-01T12:00:00Z");
        Clock clockActivo = Clock.fixed(instanteActivo, ZoneOffset.UTC);
        LicenciaService service = new LicenciaService(repository, verifier, hardwareIdProvider, null, clockActivo);

        // Licencia que expiraba el 2026-05-31
        String tokenExpirado = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-003",
                "Cliente Vencido",
                simulatedHardwareId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 5, 31),
                "SEMESTRAL"
        );

        ResultadoValidacionLicencia resExpirada = service.activarLicencia(tokenExpirado);
        assertThat(resExpirada.esValida()).isFalse();
        assertThat(resExpirada.estado()).isEqualTo(EstadoLicencia.EXPIRADA);

        // Renovación con nueva licencia vigente hasta fin de año
        String tokenRenovado = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-003-R",
                "Cliente Renovado",
                simulatedHardwareId,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 12, 31),
                "SEMESTRAL"
        );

        ResultadoValidacionLicencia resRenovada = service.activarLicencia(tokenRenovado);
        assertThat(resRenovada.esValida()).isTrue();
        assertThat(resRenovada.estado()).isEqualTo(EstadoLicencia.VALIDA);
        assertThat(resRenovada.diasRestantes()).isEqualTo(213);
    }

    @Test
    @DisplayName("VP-028: Tolerancia de reloj <= 20 min es admitida y atraso malicioso > 20 min es bloqueado como RELOJ_MANIPULADO")
    void testAntiTamperingRelojYTolerancia() {
        Instant instanteT0 = Instant.parse("2026-10-24T12:00:00Z");
        Clock clockT0 = Clock.fixed(instanteT0, ZoneOffset.UTC);
        LicenciaService serviceT0 = new LicenciaService(repository, verifier, hardwareIdProvider, null, clockT0);

        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-004",
                "Empresa Segura",
                simulatedHardwareId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                "ANUAL"
        );
        serviceT0.activarLicencia(token);
        assertThat(serviceT0.validarEstadoActual().esValida()).isTrue();

        // 1. Simulación de leve desfase dentro de tolerancia (15 minutos atrás = 900 segundos)
        Instant instanteDesfasePermitido = instanteT0.minusSeconds(15 * 60L);
        Clock clockDesfase = Clock.fixed(instanteDesfasePermitido, ZoneOffset.UTC);
        LicenciaService serviceDesfase = new LicenciaService(repository, verifier, hardwareIdProvider, null, clockDesfase);
        ResultadoValidacionLicencia resTolerado = serviceDesfase.validarEstadoActual();
        assertThat(resTolerado.esValida()).as("Un desfase de 15 min debe ser tolerado dentro de los 20 min").isTrue();

        // 2. Simulación de retroceso malicioso de reloj de 2 horas (7200 segundos atrás)
        Instant instanteRetroceso = instanteT0.minusSeconds(2 * 3600L);
        Clock clockRetroceso = Clock.fixed(instanteRetroceso, ZoneOffset.UTC);
        LicenciaService serviceRetroceso = new LicenciaService(repository, verifier, hardwareIdProvider, null, clockRetroceso);
        ResultadoValidacionLicencia resBloqueado = serviceRetroceso.validarEstadoActual();
        assertThat(resBloqueado.esValida()).isFalse();
        assertThat(resBloqueado.estado()).isEqualTo(EstadoLicencia.RELOJ_MANIPULADO);
        assertThat(resBloqueado.mensajeDetalle()).contains("El reloj del equipo ha sido atrasado");

        // 3. Restablecimiento a la hora real (T0 + 5 minutos): desbloqueo automático
        Instant instanteRestablecido = instanteT0.plusSeconds(300);
        Clock clockRestablecido = Clock.fixed(instanteRestablecido, ZoneOffset.UTC);
        LicenciaService serviceRestablecido = new LicenciaService(repository, verifier, hardwareIdProvider, null, clockRestablecido);
        ResultadoValidacionLicencia resDesbloqueado = serviceRestablecido.validarEstadoActual();
        assertThat(resDesbloqueado.esValida()).isTrue();
        assertThat(resDesbloqueado.estado()).isEqualTo(EstadoLicencia.VALIDA);
    }

    @Test
    @DisplayName("VP-029: Restaurar un backup antiguo preserva la licencia activa y la marca de tiempo más reciente")
    void testRestauracionBackupPreservaLicenciaYTimestamp(@TempDir Path tempBackupDir) throws SQLException, IOException {
        Path dbFile = tempBackupDir.resolve("active_creditos.db");
        Connection fileConn = DatabaseConnection.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        SchemaInitializer.inicializar(fileConn);
        LicenciaRepository fileLicRepo = new LicenciaRepository(fileConn);

        Instant instanteT0 = Instant.parse("2026-10-24T10:00:00Z");
        LicenciaService fileService = new LicenciaService(fileLicRepo, verifier, hardwareIdProvider, null, Clock.fixed(instanteT0, ZoneOffset.UTC));

        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                keyPair.getPrivate(),
                "LIC-2026-005",
                "Titular Permanente",
                simulatedHardwareId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                "ANUAL"
        );
        fileService.activarLicencia(token);

        BackupService backupService = new BackupService(fileConn, tempBackupDir);
        // Creamos backup cuando el timestamp era T0 (10:00)
        Path backupAntiguo = backupService.crearBackup(null, "antiguo");

        // El tiempo avanza a T1 (16:00, 6 horas después) y la aplicación registra un latido
        Instant instanteT1 = instanteT0.plusSeconds(6 * 3600L);
        LicenciaService serviceT1 = new LicenciaService(fileLicRepo, verifier, hardwareIdProvider, null, Clock.fixed(instanteT1, ZoneOffset.UTC));
        serviceT1.registrarLatidoTiempo();
        long tsAntesRestaurar = fileLicRepo.obtenerControl().orElseThrow().getUltimoTimestampUtc();
        assertThat(tsAntesRestaurar).isEqualTo(instanteT1.getEpochSecond());

        // Restauramos el backup antiguo de las 10:00
        backupService.restaurarBackup(backupAntiguo);

        // Verificamos que la tabla control_licencia sigue intacta con la licencia y conserva el timestamp de T1 (16:00)
        var controlPostRestaurar = fileLicRepo.obtenerControl().orElseThrow();
        assertThat(controlPostRestaurar.getLicenciaToken()).isEqualTo(token);
        assertThat(controlPostRestaurar.getUltimoTimestampUtc()).isEqualTo(tsAntesRestaurar);

        fileConn.close();
    }
}
