package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.ProximoPagoItem;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la gestión de créditos otorgados y sus cronogramas de cuotas.
 */
public class CreditoRepository {

    private final Connection connection;

    public CreditoRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión a base de datos no puede ser nula");
    }

    /**
     * Inserta un nuevo crédito en la base de datos y le asigna la clave primaria generada.
     *
     * @param credito Entidad del crédito a persistir.
     * @return Crédito con su ID asignado.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Credito crear(Credito credito) throws SQLException {
        String sql = """
                INSERT INTO creditos (cliente_id, capital, tasa_mensual, plazo_meses, total_intereses,
                                      total_pagar, frecuencia_pago, fecha_inicio, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, credito.getClienteId());
            ps.setString(2, credito.getCapital().toPlainString());
            ps.setString(3, credito.getTasaMensual().toPlainString());
            ps.setInt(4, credito.getPlazoMeses());
            ps.setString(5, credito.getTotalIntereses().toPlainString());
            ps.setString(6, credito.getTotalPagar().toPlainString());
            ps.setString(7, credito.getFrecuenciaPago().name());
            ps.setString(8, credito.getFechaInicio().toString());
            ps.setString(9, credito.getEstado().name());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    credito.setId(rs.getInt(1));
                }
            }
        }
        return credito;
    }

    /**
     * Inserta una cuota individual en la base de datos y le asigna la clave primaria generada.
     *
     * @param cuota Entidad cuota a persistir.
     * @return Cuota con su ID asignado.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Cuota crearCuota(Cuota cuota) throws SQLException {
        String sql = """
                INSERT INTO cuotas (credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, cuota.getCreditoId());
            ps.setInt(2, cuota.getNumeroCuota());
            ps.setString(3, cuota.getFechaVencimiento().toString());
            ps.setString(4, cuota.getMonto().toPlainString());
            ps.setString(5, cuota.getEstado().name());
            ps.setInt(6, cuota.isEnMora() ? 1 : 0);

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cuota.setId(rs.getInt(1));
                }
            }
        }
        return cuota;
    }

    /**
     * Obtiene un crédito por su identificador único.
     *
     * @param id ID del crédito.
     * @return Optional con el crédito si existe, o vacío.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Credito> obtenerPorId(int id) throws SQLException {
        String sql = """
                SELECT id, cliente_id, capital, tasa_mensual, plazo_meses, total_intereses,
                       total_pagar, frecuencia_pago, fecha_inicio, estado
                FROM creditos WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCredito(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Lista todos los créditos asociados a un prestatario en particular.
     *
     * @param clienteId ID del cliente.
     * @return Lista de créditos ordenada descendentemente por ID.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Credito> obtenerPorCliente(int clienteId) throws SQLException {
        String sql = """
                SELECT id, cliente_id, capital, tasa_mensual, plazo_meses, total_intereses,
                       total_pagar, frecuencia_pago, fecha_inicio, estado
                FROM creditos WHERE cliente_id = ? ORDER BY id DESC
                """;
        List<Credito> creditos = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    creditos.add(mapCredito(rs));
                }
            }
        }
        return creditos;
    }

    /**
     * Lista la totalidad de créditos registrados en el sistema.
     *
     * @return Lista completa de créditos.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Credito> listarTodos() throws SQLException {
        String sql = """
                SELECT id, cliente_id, capital, tasa_mensual, plazo_meses, total_intereses,
                       total_pagar, frecuencia_pago, fecha_inicio, estado
                FROM creditos ORDER BY id DESC
                """;
        List<Credito> creditos = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                creditos.add(mapCredito(rs));
            }
        }
        return creditos;
    }

    /**
     * Obtiene el cronograma completo de cuotas asociadas a un crédito ordenadas cronológicamente.
     *
     * @param creditoId ID del crédito.
     * @return Lista de cuotas ordenada por número de cuota ascendente.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Cuota> obtenerCuotasPorCredito(int creditoId) throws SQLException {
        String sql = """
                SELECT id, credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora
                FROM cuotas WHERE credito_id = ? ORDER BY numero_cuota ASC
                """;
        List<Cuota> cuotas = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, creditoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cuotas.add(mapCuota(rs));
                }
            }
        }
        return cuotas;
    }

    /**
     * Obtiene una cuota individual por su identificador primario.
     *
     * @param cuotaId ID de la cuota.
     * @return Optional con la cuota si existe.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Cuota> obtenerCuotaPorId(int cuotaId) throws SQLException {
        String sql = """
                SELECT id, credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora
                FROM cuotas WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cuotaId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapCuota(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Actualiza el estado de un crédito (ej. de ACTIVO a PAGADO).
     *
     * @param creditoId ID del crédito.
     * @param nuevoEstado Nuevo estado del crédito.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public void actualizarEstado(int creditoId, EstadoCredito nuevoEstado) throws SQLException {
        String sql = "UPDATE creditos SET estado = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.name());
            ps.setInt(2, creditoId);
            ps.executeUpdate();
        }
    }

    /**
     * Actualiza el estado de una cuota (ej. PENDIENTE -> PARCIAL -> PAGADA).
     * Si la cuota pasa a PAGADA, se limpia la bandera de mora automáticamente.
     *
     * @param cuotaId ID de la cuota.
     * @param nuevoEstado Nuevo estado.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public void actualizarEstadoCuota(int cuotaId, EstadoCuota nuevoEstado) throws SQLException {
        String sql = """
                UPDATE cuotas
                SET estado = ?,
                    en_mora = CASE WHEN ? = 'PAGADA' THEN 0 ELSE en_mora END
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nuevoEstado.name());
            ps.setString(2, nuevoEstado.name());
            ps.setInt(3, cuotaId);
            ps.executeUpdate();
        }
    }

    /**
     * Actualiza masivamente el estado de mora de todas las cuotas del sistema (RN-008).
     * Una cuota está en mora si su fecha_vencimiento es anterior a la fechaReferencia y no está PAGADA.
     * La consulta solo muta las filas cuyo valor en_mora cambie, permitiendo retornar el conteo exacto.
     *
     * @param fechaReferencia Fecha de corte (habitualmente la fecha actual).
     * @return Cantidad de filas cuyo estado de mora fue alterado.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public int actualizarMoraCuotas(LocalDate fechaReferencia) throws SQLException {
        String refStr = fechaReferencia.toString();
        String sql = """
                UPDATE cuotas
                SET en_mora = CASE
                    WHEN fecha_vencimiento < ? AND estado != 'PAGADA' THEN 1
                    ELSE 0
                END
                WHERE en_mora != (
                    CASE
                        WHEN fecha_vencimiento < ? AND estado != 'PAGADA' THEN 1
                        ELSE 0
                    END
                )
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, refStr);
            ps.setString(2, refStr);
            return ps.executeUpdate();
        }
    }

    /**
     * Consulta las próximas cuotas pendientes o parciales de créditos activos para el Dashboard (CU-012, RN-013).
     *
     * @param limite Cantidad máxima de registros a retornar (ej. 10).
     * @return Lista de ProximoPagoItem ordenada por fecha de vencimiento ascendente.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<ProximoPagoItem> obtenerCuotasPendientes(int limite) throws SQLException {
        String sql = """
                SELECT cr.id AS credito_id, cu.id AS cuota_id, cl.nombre AS cliente_nombre,
                       cu.numero_cuota, cu.monto, cu.fecha_vencimiento, cu.estado, cu.en_mora
                FROM cuotas cu
                JOIN creditos cr ON cu.credito_id = cr.id
                JOIN clientes cl ON cr.cliente_id = cl.id
                WHERE cu.estado IN ('PENDIENTE', 'PARCIAL') AND cr.estado = 'ACTIVO'
                ORDER BY cu.fecha_vencimiento ASC, cu.numero_cuota ASC
                LIMIT ?
                """;
        List<ProximoPagoItem> items = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ProximoPagoItem(
                            rs.getInt("credito_id"),
                            rs.getInt("cuota_id"),
                            rs.getString("cliente_nombre"),
                            rs.getInt("numero_cuota"),
                            new BigDecimal(rs.getString("monto")),
                            LocalDate.parse(rs.getString("fecha_vencimiento")),
                            rs.getString("estado"),
                            rs.getInt("en_mora") != 0
                    ));
                }
            }
        }
        return items;
    }

    /**
     * Obtiene la lista completa de todas las cuotas registradas en el sistema.
     * Requerido para agregaciones y proyecciones de reportes financieros.
     *
     * @return Lista de todas las cuotas ordenadas por fecha de vencimiento.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Cuota> listarTodasLasCuotas() throws SQLException {
        String sql = """
                SELECT id, credito_id, numero_cuota, fecha_vencimiento, monto, estado, en_mora
                FROM cuotas
                ORDER BY fecha_vencimiento ASC, numero_cuota ASC
                """;
        List<Cuota> cuotas = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                cuotas.add(mapCuota(rs));
            }
        }
        return cuotas;
    }

    private Credito mapCredito(ResultSet rs) throws SQLException {
        return new Credito(
                rs.getInt("id"),
                rs.getInt("cliente_id"),
                new BigDecimal(rs.getString("capital")),
                new BigDecimal(rs.getString("tasa_mensual")),
                rs.getInt("plazo_meses"),
                new BigDecimal(rs.getString("total_intereses")),
                new BigDecimal(rs.getString("total_pagar")),
                FrecuenciaPago.valueOf(rs.getString("frecuencia_pago")),
                LocalDate.parse(rs.getString("fecha_inicio")),
                EstadoCredito.valueOf(rs.getString("estado"))
        );
    }

    private Cuota mapCuota(ResultSet rs) throws SQLException {
        return new Cuota(
                rs.getInt("id"),
                rs.getInt("credito_id"),
                rs.getInt("numero_cuota"),
                LocalDate.parse(rs.getString("fecha_vencimiento")),
                new BigDecimal(rs.getString("monto")),
                EstadoCuota.valueOf(rs.getString("estado")),
                rs.getInt("en_mora") != 0
        );
    }
}
