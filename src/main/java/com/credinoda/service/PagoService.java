package com.credinoda.service;

import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.dto.PagoDetalleItem;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.exception.CreditoNoEncontradoException;
import com.credinoda.domain.exception.CuotaNoEncontradaException;
import com.credinoda.domain.exception.MontoInvalidoException;
import com.credinoda.domain.exception.PagoInvalidoException;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.Pago;
import com.credinoda.domain.model.Permiso;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de lógica de negocio para la gestión de pagos y amortización de cobranzas.
 * Implementa la validación estricta contra sobrepagos, libertad de orden en el pago de cuotas
 * y transiciones atómicas de estado (RN-009 a RN-012).
 */
public class PagoService {

    private final Connection connection;
    private final PagoRepository pagoRepository;
    private final CreditoRepository creditoRepository;
    private final CreditoService creditoService;
    private final Autorizador autorizador;

    public PagoService(Connection connection, PagoRepository pagoRepository,
                       CreditoRepository creditoRepository, CreditoService creditoService,
                       Autorizador autorizador) {
        this.connection = Objects.requireNonNull(connection, "La conexión JDBC no puede ser nula");
        this.pagoRepository = Objects.requireNonNull(pagoRepository, "PagoRepository no puede ser nulo");
        this.creditoRepository = Objects.requireNonNull(creditoRepository, "CreditoRepository no puede ser nulo");
        this.creditoService = creditoService;
        this.autorizador = Objects.requireNonNull(autorizador, "Autorizador no puede ser nulo");
    }

    public PagoService(Connection connection, PagoRepository pagoRepository,
                       CreditoRepository creditoRepository, CreditoService creditoService) {
        this(connection, pagoRepository, creditoRepository, creditoService, new AutorizacionService());
    }

    /**
     * Registra un nuevo pago sobre una cuota específica aplicando validaciones estrictas y
     * transición atómica de estados (CU-009, RN-009, RN-011).
     *
     * @param creditoId ID del crédito.
     * @param cuotaId ID de la cuota a abonar.
     * @param monto Monto a pagar.
     * @param fechaPago Fecha del pago (o null para usar la fecha actual).
     * @return Pago registrado y persistido.
     * @throws MontoInvalidoException Si el monto es <= 0 o excede el saldo pendiente de la cuota.
     * @throws CreditoNoEncontradoException Si el crédito no existe.
     * @throws CuotaNoEncontradaException Si la cuota no existe.
     * @throws PagoInvalidoException Si la cuota no pertenece al crédito o ya está totalmente pagada.
     * @throws CrediNodaException Si ocurre un fallo en la transacción JDBC.
     */
    public Pago registrarPago(int creditoId, int cuotaId, BigDecimal monto, LocalDate fechaPago) {
        autorizador.requirePermission(Permiso.PAGOS_REGISTRAR);
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MontoInvalidoException("El monto del pago debe ser mayor a 0.");
        }

        Cuota cuota;
        try {
            creditoRepository.obtenerPorId(creditoId)
                    .orElseThrow(() -> new CreditoNoEncontradoException("Crédito con ID " + creditoId + " no encontrado."));
            cuota = creditoRepository.obtenerCuotaPorId(cuotaId)
                    .orElseThrow(() -> new CuotaNoEncontradaException("Cuota con ID " + cuotaId + " no encontrada."));
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar entidades de crédito/cuota: " + e.getMessage(), e);
        }

        if (cuota.getCreditoId() != creditoId) {
            throw new PagoInvalidoException("La cuota no pertenece al crédito especificado.");
        }

        if (cuota.getEstado() == EstadoCuota.PAGADA) {
            throw new PagoInvalidoException("La cuota ya está pagada.");
        }

        BigDecimal pagadoHastaAhora;
        try {
            pagadoHastaAhora = pagoRepository.obtenerTotalPagadoCuota(cuotaId);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar saldo de la cuota: " + e.getMessage(), e);
        }

        BigDecimal saldoPendienteCuota = cuota.getMonto().subtract(pagadoHastaAhora);

        if (monto.compareTo(saldoPendienteCuota) > 0) {
            throw new MontoInvalidoException("El monto excede el saldo pendiente de la cuota.");
        }

        boolean autoCommitOriginal = true;
        try {
            autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);

            LocalDate fecha = (fechaPago != null) ? fechaPago : LocalDate.now();
            Pago pago = new Pago(creditoId, cuotaId, monto, fecha);
            pago = pagoRepository.crear(pago);

            BigDecimal nuevoTotalCuota = pagadoHastaAhora.add(monto);
            EstadoCuota nuevoEstadoCuota = (nuevoTotalCuota.compareTo(cuota.getMonto()) >= 0)
                    ? EstadoCuota.PAGADA
                    : EstadoCuota.PARCIAL;

            creditoRepository.actualizarEstadoCuota(cuotaId, nuevoEstadoCuota);

            if (creditoService != null) {
                creditoService.determinarEstadoCredito(creditoId);
            }

            connection.commit();
            return pago;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            if (e instanceof CrediNodaException ce) {
                throw ce;
            }
            throw new CrediNodaException("Error transaccional al registrar pago: " + e.getMessage(), e);
        } finally {
            try {
                connection.setAutoCommit(autoCommitOriginal);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Lista los pagos con detalle para la vista de historial con filtros opcionales (CU-010, RN-016).
     *
     * @param filtroTexto Filtro de texto (nombre, documento o ID crédito).
     * @param fechaInicio Fecha inicial de corte.
     * @param fechaFin Fecha final de corte.
     * @return Lista de pagos detallados.
     */
    public List<PagoDetalleItem> listarPagosConDetalle(String filtroTexto, LocalDate fechaInicio, LocalDate fechaFin) {
        try {
            return pagoRepository.listarTodosConDetalle(filtroTexto, fechaInicio, fechaFin);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al listar pagos con detalle: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene los datos consolidados requeridos para generar el comprobante PDF y el código QR (CU-011, RN-012).
     *
     * @param pagoId ID del pago.
     * @return DTO DatosComprobante con todos los saldos calculados.
     * @throws PagoInvalidoException Si el pago no existe.
     */
    public DatosComprobante obtenerDatosComprobante(int pagoId) {
        try {
            return pagoRepository.obtenerDetallePago(pagoId)
                    .orElseThrow(() -> new PagoInvalidoException("Pago con ID " + pagoId + " no encontrado."));
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar datos de comprobante: " + e.getMessage(), e);
        }
    }

    /**
     * Calcula el saldo insoluto pendiente de una cuota individual (RN-010).
     *
     * @param cuotaId ID de la cuota.
     * @return Saldo pendiente de la cuota.
     * @throws CuotaNoEncontradaException Si la cuota no existe.
     */
    public BigDecimal calcularSaldoPendienteCuota(int cuotaId) {
        try {
            Cuota cuota = creditoRepository.obtenerCuotaPorId(cuotaId)
                    .orElseThrow(() -> new CuotaNoEncontradaException("Cuota con ID " + cuotaId + " no encontrada."));
            BigDecimal pagado = pagoRepository.obtenerTotalPagadoCuota(cuotaId);
            return cuota.getMonto().subtract(pagado).max(BigDecimal.ZERO);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al calcular saldo de cuota: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el monto total abonado hasta la fecha sobre una cuota específica (RN-010).
     *
     * @param cuotaId ID de la cuota.
     * @return Suma acumulada de pagos realizados a la cuota.
     */
    public BigDecimal obtenerTotalPagadoCuota(int cuotaId) {
        try {
            return pagoRepository.obtenerTotalPagadoCuota(cuotaId);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al obtener total pagado de la cuota: " + e.getMessage(), e);
        }
    }

    /**
     * Lista los abonos individuales realizados sobre una cuota (CU-007).
     *
     * @param cuotaId ID de la cuota.
     * @return Lista de pagos de la cuota.
     */
    public List<Pago> obtenerPagosCuota(int cuotaId) {
        try {
            return pagoRepository.obtenerPorCuota(cuotaId);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al obtener pagos de la cuota: " + e.getMessage(), e);
        }
    }
}
