package com.credinoda.persistence.repository;

import com.credinoda.domain.model.RegistroControlLicencia;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias de LicenciaRepository (RN-030, RN-032)")
class LicenciaRepositoryTest {

    private Connection connection;
    private LicenciaRepository repository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        repository = new LicenciaRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("obtenerControl() en base de datos recién creada retorna Optional.empty()")
    void testObtenerControlVacio() throws SQLException {
        Optional<RegistroControlLicencia> control = repository.obtenerControl();
        assertThat(control).isEmpty();
    }

    @Test
    @DisplayName("guardarOActualizarControl() inserta y actualiza la fila única id = 1 de forma idempotente")
    void testGuardarYActualizarControl() throws SQLException {
        RegistroControlLicencia inicial = new RegistroControlLicencia(
                1,
                "TOKEN-INICIAL",
                "CN20-1111-2222-3333",
                "Titular Uno",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                1767225600L,
                "HMAC-INICIAL"
        );

        repository.guardarOActualizarControl(inicial);

        Optional<RegistroControlLicencia> guardadoOpt = repository.obtenerControl();
        assertThat(guardadoOpt).isPresent();
        RegistroControlLicencia guardado = guardadoOpt.get();
        assertThat(guardado.getId()).isEqualTo(1);
        assertThat(guardado.getLicenciaToken()).isEqualTo("TOKEN-INICIAL");
        assertThat(guardado.getHardwareId()).isEqualTo("CN20-1111-2222-3333");
        assertThat(guardado.getTitular()).isEqualTo("Titular Uno");
        assertThat(guardado.getFechaEmision()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(guardado.getFechaExpiracion()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(guardado.getUltimoTimestampUtc()).isEqualTo(1767225600L);
        assertThat(guardado.getHmacIntegridad()).isEqualTo("HMAC-INICIAL");

        // Actualización sobre la misma fila
        RegistroControlLicencia actualizado = new RegistroControlLicencia(
                1,
                "TOKEN-RENOVADO",
                "CN20-1111-2222-3333",
                "Titular Renovado",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 12, 31),
                1798761600L,
                "HMAC-RENOVADO"
        );
        repository.guardarOActualizarControl(actualizado);

        Optional<RegistroControlLicencia> renovadoOpt = repository.obtenerControl();
        assertThat(renovadoOpt).isPresent();
        RegistroControlLicencia renovado = renovadoOpt.get();
        assertThat(renovado.getLicenciaToken()).isEqualTo("TOKEN-RENOVADO");
        assertThat(renovado.getUltimoTimestampUtc()).isEqualTo(1798761600L);
    }

    @Test
    @DisplayName("obtenerMaximoTimestampHistorico() extrae el timestamp máximo de auditoría y pagos")
    void testObtenerMaximoTimestampHistorico() throws SQLException {
        // Inicialmente vacío -> 0L
        assertThat(repository.obtenerMaximoTimestampHistorico()).isZero();

        try (Statement stmt = connection.createStatement()) {
            // Insertar un evento en auditoría (2026-10-24 15:30:00 UTC)
            stmt.execute("""
                    INSERT INTO auditoria (fecha_hora, username, rol, accion, entidad, resultado, estacion_trabajo)
                    VALUES ('2026-10-24 15:30:00', 'admin', 'ADMINISTRADOR', 'LOGIN_EXITOSO', 'USUARIO', 'EXITO', 'WS-01');
                    """);
        }

        long maxAuditoria = repository.obtenerMaximoTimestampHistorico();
        assertThat(maxAuditoria).isGreaterThan(0L);

        // Insertar un pago con fecha posterior (2026-11-01)
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = OFF;");
            stmt.execute("""
                    INSERT INTO pagos (id, credito_id, cuota_id, fecha_pago, monto)
                    VALUES (1, 1, 1, '2026-11-01', 50000);
                    """);
            stmt.execute("PRAGMA foreign_keys = ON;");
        }

        long maxConPago = repository.obtenerMaximoTimestampHistorico();
        assertThat(maxConPago).isGreaterThan(maxAuditoria);
    }
}
