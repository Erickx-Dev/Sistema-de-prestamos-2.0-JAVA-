package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.dto.PagoDetalleItem;
import com.credinoda.domain.model.Pago;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Repositorio JDBC para la gestión de pagos, recaudaciones contables e historial de cobros.
 */
public class PagoRepository {

    private final Connection connection;

    public PagoRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión a base de datos no puede ser nula");
    }

    /**
     * Registra un nuevo pago en la base de datos y le asigna el ID generado.
     *
     * @param pago Entidad del pago a persistir.
     * @return Pago con su clave primaria asignada.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Pago crear(Pago pago) throws SQLException {
        String sql = "INSERT INTO pagos (credito_id, cuota_id, fecha_pago, monto) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pago.getCreditoId());
            ps.setInt(2, pago.getCuotaId());
            ps.setString(3, pago.getFechaPago().toString());
            ps.setString(4, pago.getMonto().toPlainString());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    pago.setId(rs.getInt(1));
                }
            }
        }
        return pago;
    }

    /**
     * Obtiene un pago básico por su ID.
     *
     * @param id Identificador del pago.
     * @return Optional con el pago si existe.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Pago> obtenerPorId(int id) throws SQLException {
        String sql = "SELECT id, credito_id, cuota_id, fecha_pago, monto FROM pagos WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPago(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Lista todos los pagos registrados para una cuota específica.
     *
     * @param cuotaId ID de la cuota.
     * @return Lista de pagos ordenada cronológicamente.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Pago> obtenerPorCuota(int cuotaId) throws SQLException {
        String sql = "SELECT id, credito_id, cuota_id, fecha_pago, monto FROM pagos WHERE cuota_id = ? ORDER BY fecha_pago ASC, id ASC";
        List<Pago> pagos = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cuotaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pagos.add(mapPago(rs));
                }
            }
        }
        return pagos;
    }

    /**
     * Lista todos los pagos registrados para un crédito completo.
     *
     * @param creditoId ID del crédito.
     * @return Lista de pagos ordenada cronológicamente.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Pago> obtenerPorCredito(int creditoId) throws SQLException {
        String sql = "SELECT id, credito_id, cuota_id, fecha_pago, monto FROM pagos WHERE credito_id = ? ORDER BY fecha_pago ASC, id ASC";
        List<Pago> pagos = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, creditoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pagos.add(mapPago(rs));
                }
            }
        }
        return pagos;
    }

    /**
     * Calcula la suma acumulada de pagos para una cuota específica (RN-010).
     * Se acumula en memoria con BigDecimal.add para evitar pérdida de precisión con SQLite SUM.
     *
     * @param cuotaId ID de la cuota.
     * @return Total pagado en la cuota (0 si no tiene pagos).
     * @throws SQLException Si ocurre un error JDBC.
     */
    public BigDecimal obtenerTotalPagadoCuota(int cuotaId) throws SQLException {
        String sql = "SELECT monto FROM pagos WHERE cuota_id = ?";
        BigDecimal total = BigDecimal.ZERO;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, cuotaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total = total.add(new BigDecimal(rs.getString("monto")));
                }
            }
        }
        return total;
    }

    /**
     * Calcula la suma acumulada de pagos para un crédito completo.
     *
     * @param creditoId ID del crédito.
     * @return Total pagado en el crédito (0 si no tiene pagos).
     * @throws SQLException Si ocurre un error JDBC.
     */
    public BigDecimal obtenerTotalPagadoCredito(int creditoId) throws SQLException {
        String sql = "SELECT monto FROM pagos WHERE credito_id = ?";
        BigDecimal total = BigDecimal.ZERO;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, creditoId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    total = total.add(new BigDecimal(rs.getString("monto")));
                }
            }
        }
        return total;
    }

    /**
     * Lista todos los pagos con información enriquecida para la vista de historial (CU-010, RN-016).
     * Permite filtrado por coincidencia de texto (nombre, documento o ID crédito) y rango de fechas.
     *
     * @param filtroTexto Filtro de búsqueda por cliente o crédito (opcional).
     * @param fechaInicio Fecha inicial del rango (opcional).
     * @param fechaFin Fecha final del rango (opcional).
     * @return Lista de PagoDetalleItem ordenada por fecha descendente e ID descendente.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<PagoDetalleItem> listarTodosConDetalle(String filtroTexto, LocalDate fechaInicio, LocalDate fechaFin) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id AS pago_id, p.credito_id, p.cuota_id, cl.nombre AS cliente_nombre,
                       cu.numero_cuota, p.fecha_pago, p.monto
                FROM pagos p
                JOIN cuotas cu ON p.cuota_id = cu.id
                JOIN creditos cr ON p.credito_id = cr.id
                JOIN clientes cl ON cr.cliente_id = cl.id
                WHERE 1=1
                """);

        List<Object> params = new ArrayList<>();

        if (filtroTexto != null && !filtroTexto.isBlank()) {
            String trimmed = filtroTexto.trim();
            sql.append(" AND (cl.nombre LIKE ? OR cl.documento LIKE ?");
            params.add("%" + trimmed + "%");
            params.add("%" + trimmed + "%");
            try {
                int credId = Integer.parseInt(trimmed);
                sql.append(" OR cr.id = ?)");
                params.add(credId);
            } catch (NumberFormatException ignored) {
                sql.append(")");
            }
        }

        if (fechaInicio != null) {
            sql.append(" AND p.fecha_pago >= ?");
            params.add(fechaInicio.toString());
        }

        if (fechaFin != null) {
            sql.append(" AND p.fecha_pago <= ?");
            params.add(fechaFin.toString());
        }

        sql.append(" ORDER BY p.fecha_pago DESC, p.id DESC");

        List<PagoDetalleItem> resultados = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultados.add(new PagoDetalleItem(
                            rs.getInt("pago_id"),
                            rs.getInt("credito_id"),
                            rs.getInt("cuota_id"),
                            rs.getString("cliente_nombre"),
                            rs.getInt("numero_cuota"),
                            LocalDate.parse(rs.getString("fecha_pago")),
                            new BigDecimal(rs.getString("monto"))
                    ));
                }
            }
        }
        return resultados;
    }

    /**
     * Consulta consolidada de datos requeridos para la emisión de comprobante en PDF con QR (CU-011, RN-012).
     *
     * @param pagoId ID del pago.
     * @return Optional con DatosComprobante completamente calculados si el pago existe.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<DatosComprobante> obtenerDetallePago(int pagoId) throws SQLException {
        String sql = """
                SELECT p.id AS pago_id, p.fecha_pago, p.monto AS pago_monto,
                       cl.id AS cliente_id, cl.nombre AS cliente_nombre, cl.documento AS cliente_documento, cl.telefono AS cliente_telefono,
                       cr.id AS credito_id, cr.capital AS credito_capital, cr.total_pagar AS credito_total_pagar,
                       cr.plazo_meses AS credito_plazo_meses, cr.frecuencia_pago AS credito_frecuencia, cr.fecha_inicio AS credito_fecha_inicio,
                       cu.id AS cuota_id, cu.numero_cuota,
                       (SELECT COUNT(*) FROM cuotas WHERE credito_id = cr.id) AS total_cuotas,
                       cu.fecha_vencimiento AS cuota_fecha_vencimiento, cu.monto AS cuota_monto, cu.estado AS cuota_estado
                FROM pagos p
                JOIN cuotas cu ON p.cuota_id = cu.id
                JOIN creditos cr ON p.credito_id = cr.id
                JOIN clientes cl ON cr.cliente_id = cl.id
                WHERE p.id = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, pagoId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int cId = rs.getInt("cuota_id");
                    int crId = rs.getInt("credito_id");
                    BigDecimal pagoMonto = new BigDecimal(rs.getString("pago_monto"));
                    BigDecimal cuotaMonto = new BigDecimal(rs.getString("cuota_monto"));
                    BigDecimal creditoTotalPagar = new BigDecimal(rs.getString("credito_total_pagar"));

                    BigDecimal totalPagadoCuota = obtenerTotalPagadoCuota(cId);
                    BigDecimal totalPagadoCredito = obtenerTotalPagadoCredito(crId);

                    BigDecimal saldoCuotaDespues = cuotaMonto.subtract(totalPagadoCuota).max(BigDecimal.ZERO);
                    BigDecimal saldoCuotaAntes = saldoCuotaDespues.add(pagoMonto);
                    BigDecimal saldoCreditoRestante = creditoTotalPagar.subtract(totalPagadoCredito).max(BigDecimal.ZERO);

                    BigDecimal porcentajePagadoCredito = BigDecimal.ZERO;
                    if (creditoTotalPagar.compareTo(BigDecimal.ZERO) > 0) {
                        porcentajePagadoCredito = totalPagadoCredito
                                .divide(creditoTotalPagar, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(1, RoundingMode.HALF_UP);
                    }

                    String numeroRecibo = String.format("REC-%05d", pagoId);

                    DatosComprobante comprobante = new DatosComprobante(
                            pagoId,
                            numeroRecibo,
                            LocalDate.parse(rs.getString("fecha_pago")),
                            pagoMonto,
                            rs.getInt("cliente_id"),
                            rs.getString("cliente_nombre"),
                            rs.getString("cliente_documento"),
                            rs.getString("cliente_telefono"),
                            crId,
                            new BigDecimal(rs.getString("credito_capital")),
                            creditoTotalPagar,
                            rs.getInt("credito_plazo_meses"),
                            rs.getString("credito_frecuencia"),
                            LocalDate.parse(rs.getString("credito_fecha_inicio")),
                            cId,
                            rs.getInt("numero_cuota"),
                            rs.getInt("total_cuotas"),
                            LocalDate.parse(rs.getString("cuota_fecha_vencimiento")),
                            cuotaMonto,
                            totalPagadoCuota,
                            saldoCuotaAntes,
                            saldoCuotaDespues,
                            rs.getString("cuota_estado"),
                            totalPagadoCredito,
                            saldoCreditoRestante,
                            porcentajePagadoCredito
                    );

                    return Optional.of(comprobante);
                }
            }
        }
        return Optional.empty();
    }

    private Pago mapPago(ResultSet rs) throws SQLException {
        return new Pago(
                rs.getInt("id"),
                rs.getInt("credito_id"),
                rs.getInt("cuota_id"),
                new BigDecimal(rs.getString("monto")),
                LocalDate.parse(rs.getString("fecha_pago"))
        );
    }
}
