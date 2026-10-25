package com.credinoda.persistence.repository;

import com.credinoda.domain.model.RegistroControlLicencia;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la gestión de la tabla 'control_licencia' y consulta de marcas de tiempo máximas
 * en el histórico de auditoría y transacciones financieras (RN-030, RN-032).
 */
public class LicenciaRepository {

    private final Connection connection;

    public LicenciaRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión JDBC no puede ser nula");
    }

    /**
     * Consulta el registro único de control de licencia activo (id = 1).
     *
     * @return Optional con RegistroControlLicencia si existe, u Optional.empty().
     * @throws SQLException Si ocurre un error en la consulta SQL.
     */
    public Optional<RegistroControlLicencia> obtenerControl() throws SQLException {
        String sql = """
                SELECT id, licencia_token, hardware_id, titular, fecha_emision, fecha_expiracion, ultimo_timestamp_utc, hmac_integridad
                FROM control_licencia
                WHERE id = 1;
                """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                String feStr = rs.getString("fecha_emision");
                String fxStr = rs.getString("fecha_expiracion");
                LocalDate fe = (feStr != null && !feStr.isBlank()) ? LocalDate.parse(feStr) : null;
                LocalDate fx = (fxStr != null && !fxStr.isBlank()) ? LocalDate.parse(fxStr) : null;

                RegistroControlLicencia registro = new RegistroControlLicencia(
                        rs.getInt("id"),
                        rs.getString("licencia_token"),
                        rs.getString("hardware_id"),
                        rs.getString("titular"),
                        fe,
                        fx,
                        rs.getLong("ultimo_timestamp_utc"),
                        rs.getString("hmac_integridad")
                );
                return Optional.of(registro);
            }
        }
        return Optional.empty();
    }

    /**
     * Guarda o actualiza atómicamente el registro único de control de licencia (id = 1).
     *
     * @param control Instancia del registro con la información a persistir.
     * @throws SQLException Si ocurre un error en la inserción o actualización.
     */
    public void guardarOActualizarControl(RegistroControlLicencia control) throws SQLException {
        Objects.requireNonNull(control, "El registro de control de licencia no puede ser nulo");
        String sql = """
                INSERT INTO control_licencia (id, licencia_token, hardware_id, titular, fecha_emision, fecha_expiracion, ultimo_timestamp_utc, hmac_integridad)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    licencia_token = excluded.licencia_token,
                    hardware_id = excluded.hardware_id,
                    titular = excluded.titular,
                    fecha_emision = excluded.fecha_emision,
                    fecha_expiracion = excluded.fecha_expiracion,
                    ultimo_timestamp_utc = excluded.ultimo_timestamp_utc,
                    hmac_integridad = excluded.hmac_integridad;
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, control.getLicenciaToken());
            ps.setString(2, control.getHardwareId());
            ps.setString(3, control.getTitular());
            ps.setString(4, control.getFechaEmision() != null ? control.getFechaEmision().toString() : null);
            ps.setString(5, control.getFechaExpiracion() != null ? control.getFechaExpiracion().toString() : null);
            ps.setLong(6, control.getUltimoTimestampUtc());
            ps.setString(7, control.getHmacIntegridad());
            ps.executeUpdate();
        }
    }

    /**
     * Consulta la marca de tiempo máxima histórica (en segundos UTC) registrada en las tablas
     * inmutables de 'auditoria' y 'pagos' como tercer ancla del motor Anti-Tampering (RN-032).
     *
     * @return Época Unix en segundos de la transacción más reciente, o 0L si no hay registros.
     * @throws SQLException Si ocurre un error SQL.
     */
    public long obtenerMaximoTimestampHistorico() throws SQLException {
        String sql = """
                SELECT MAX(ts) FROM (
                    SELECT CAST(strftime('%s', fecha_hora) AS INTEGER) AS ts FROM auditoria WHERE fecha_hora IS NOT NULL
                    UNION ALL
                    SELECT CAST(strftime('%s', fecha_pago || ' 00:00:00') AS INTEGER) AS ts FROM pagos WHERE fecha_pago IS NOT NULL
                );
                """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Object obj = rs.getObject(1);
                if (obj instanceof Number n) {
                    return n.longValue();
                }
            }
        }
        return 0L;
    }
}
