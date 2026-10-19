package com.credinoda.service;

import com.credinoda.common.AppConfig;
import com.credinoda.domain.dto.ProximoPagoItem;
import com.credinoda.domain.exception.ClienteNoEncontradoException;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.exception.CreditoNoEncontradoException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.exception.MontoInvalidoException;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Permiso;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de lógica financiera y amortización de créditos.
 * Implementa el cálculo de interés simple mensual, cronogramas de pago con ajuste exacto de centavos
 * y persistencia atómica transaccional (RN-004 a RN-008, RN-010, RN-011).
 */
public class CreditoService {

    private final Connection connection;
    private final CreditoRepository creditoRepository;
    private final ClienteRepository clienteRepository;
    private final PagoRepository pagoRepository;
    private final Autorizador autorizador;

    public CreditoService(Connection connection, CreditoRepository creditoRepository,
                          ClienteRepository clienteRepository, PagoRepository pagoRepository,
                          Autorizador autorizador) {
        this.connection = Objects.requireNonNull(connection, "La conexión JDBC no puede ser nula");
        this.creditoRepository = Objects.requireNonNull(creditoRepository, "CreditoRepository no puede ser nulo");
        this.clienteRepository = Objects.requireNonNull(clienteRepository, "ClienteRepository no puede ser nulo");
        this.pagoRepository = Objects.requireNonNull(pagoRepository, "PagoRepository no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "Autorizador no puede ser nulo");
    }

    public CreditoService(Connection connection, CreditoRepository creditoRepository,
                          ClienteRepository clienteRepository, PagoRepository pagoRepository) {
        this(connection, creditoRepository, clienteRepository, pagoRepository, new AutorizacionService());
    }

    /**
     * Calcula los intereses bajo el régimen de interés simple mensual con redondeo bancario HALF_UP (RN-004).
     * Fórmula: Intereses = round_half_up(Capital * TasaMensual * PlazoMeses, 2).
     *
     * @param capital Monto del capital solicitado.
     * @param tasaMensual Tasa mensual en valor decimal (ej. 0.12 para 12%).
     * @param plazoMeses Duración del crédito en meses.
     * @return Total de intereses calculados con 2 decimales.
     * @throws MontoInvalidoException Si el capital es nulo o <= 0.
     * @throws DatosInvalidosException Si la tasa es negativa o el plazo <= 0.
     */
    public BigDecimal calcularIntereses(BigDecimal capital, BigDecimal tasaMensual, int plazoMeses) {
        if (capital == null || capital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new MontoInvalidoException("El capital debe ser mayor a 0.");
        }
        if (tasaMensual == null || tasaMensual.compareTo(BigDecimal.ZERO) < 0) {
            throw new DatosInvalidosException("La tasa mensual no puede ser negativa.");
        }
        if (plazoMeses <= 0) {
            throw new DatosInvalidosException("El plazo en meses debe ser mayor a 0.");
        }

        return capital.multiply(tasaMensual)
                .multiply(BigDecimal.valueOf(plazoMeses))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el monto total a pagar sumando capital e intereses.
     *
     * @param capital Capital prestado.
     * @param intereses Intereses calculados.
     * @return Total a pagar con escala de 2 decimales.
     */
    public BigDecimal calcularTotalPagar(BigDecimal capital, BigDecimal intereses) {
        return capital.add(intereses).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determina el número exacto de cuotas según el plazo y la frecuencia pactada (RN-005).
     * Semanal: meses * 4 | Quincenal: meses * 2.
     *
     * @param plazoMeses Plazo en meses.
     * @param frecuencia Frecuencia de pago.
     * @return Cantidad de cuotas.
     */
    public int calcularNumeroCuotas(int plazoMeses, FrecuenciaPago frecuencia) {
        if (frecuencia == null || plazoMeses <= 0) {
            return 0;
        }
        return switch (frecuencia) {
            case SEMANAL -> plazoMeses * AppConfig.WEEKS_PER_MONTH;
            case QUINCENAL -> plazoMeses * AppConfig.FORTNIGHTS_PER_MONTH;
        };
    }

    /**
     * Genera el plan de cuotas proyectando fechas de vencimiento y aplicando el ajuste de centavos
     * en la última cuota para garantizar la igualdad estricta sum(cuotas) == total_pagar (RN-006, RN-007).
     *
     * @param totalPagar Monto total pactado a amortizar.
     * @param numeroCuotas Cantidad de cuotas del plan.
     * @param frecuencia Periodicidad (SEMANAL o QUINCENAL).
     * @param fechaInicio Fecha de desembolso del crédito.
     * @return Lista de cuotas generadas en estado PENDIENTE.
     */
    public List<Cuota> generarPlanPagos(BigDecimal totalPagar, int numeroCuotas, FrecuenciaPago frecuencia, LocalDate fechaInicio) {
        if (numeroCuotas <= 0) {
            throw new DatosInvalidosException("El número de cuotas debe ser mayor a cero.");
        }

        BigDecimal cuotaBase = totalPagar.divide(BigDecimal.valueOf(numeroCuotas), 2, RoundingMode.HALF_UP);
        BigDecimal sumaBase = cuotaBase.multiply(BigDecimal.valueOf(numeroCuotas));
        BigDecimal ajuste = totalPagar.subtract(sumaBase);

        int diasPaso = (frecuencia == FrecuenciaPago.SEMANAL) ? 7 : 15;
        List<Cuota> cuotas = new ArrayList<>(numeroCuotas);

        for (int i = 1; i <= numeroCuotas; i++) {
            BigDecimal montoCuota = (i == numeroCuotas) ? cuotaBase.add(ajuste) : cuotaBase;
            LocalDate fechaVencimiento = fechaInicio.plusDays((long) diasPaso * i);

            cuotas.add(new Cuota(
                    null,
                    0,
                    i,
                    fechaVencimiento,
                    montoCuota,
                    EstadoCuota.PENDIENTE,
                    false
            ));
        }

        return cuotas;
    }

    /**
     * Crea y persiste atómicamente un nuevo crédito junto con todo su plan de cuotas bajo una única
     * transacción JDBC (CU-006).
     *
     * @param clienteId ID del cliente prestatario.
     * @param capital Monto del capital solicitado.
     * @param tasaMensual Tasa mensual (o null para usar la tasa por defecto del 12%).
     * @param plazoMeses Plazo en meses.
     * @param frecuencia Periodicidad (SEMANAL o QUINCENAL).
     * @param fechaInicio Fecha de inicio (o null para la fecha actual).
     * @return Crédito persistido con sus claves primarias asignadas.
     * @throws ClienteNoEncontradoException Si el cliente no existe.
     * @throws MontoInvalidoException Si el capital es inválido.
     * @throws DatosInvalidosException Si los parámetros de crédito son inconsistentes.
     * @throws CrediNodaException Si ocurre un fallo en la persistencia o en la transacción.
     */
    public Credito crearCredito(int clienteId, BigDecimal capital, BigDecimal tasaMensual,
                                int plazoMeses, FrecuenciaPago frecuencia, LocalDate fechaInicio) {
        autorizador.requirePermission(Permiso.CREDITOS_CREAR);
        // Valida existencia del cliente
        try {
            clienteRepository.obtenerPorId(clienteId)
                    .orElseThrow(() -> new ClienteNoEncontradoException("Cliente con ID " + clienteId + " no encontrado."));
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar cliente: " + e.getMessage(), e);
        }

        BigDecimal tasa = (tasaMensual != null) ? tasaMensual : AppConfig.DEFAULT_MONTHLY_RATE;
        LocalDate inicio = (fechaInicio != null) ? fechaInicio : LocalDate.now();

        BigDecimal intereses = calcularIntereses(capital, tasa, plazoMeses);
        BigDecimal totalPagar = calcularTotalPagar(capital, intereses);
        int numeroCuotas = calcularNumeroCuotas(plazoMeses, frecuencia);
        List<Cuota> cuotas = generarPlanPagos(totalPagar, numeroCuotas, frecuencia, inicio);

        boolean autoCommitOriginal = true;
        try {
            autoCommitOriginal = connection.getAutoCommit();
            connection.setAutoCommit(false);

            Credito credito = new Credito(
                    clienteId, capital, tasa, plazoMeses, intereses, totalPagar,
                    frecuencia, inicio, EstadoCredito.ACTIVO
            );
            credito = creditoRepository.crear(credito);

            for (Cuota c : cuotas) {
                c.setCreditoId(credito.getId());
                creditoRepository.crearCuota(c);
            }

            connection.commit();
            return credito;
        } catch (Exception e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                // Registro silencioso de fallo en rollback
            }
            if (e instanceof CrediNodaException ce) {
                throw ce;
            }
            throw new CrediNodaException("Error transaccional al crear crédito: " + e.getMessage(), e);
        } finally {
            try {
                connection.setAutoCommit(autoCommitOriginal);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Obtiene un crédito por su identificador primario (CU-007).
     *
     * @param id ID del crédito.
     * @return Entidad del crédito.
     * @throws CreditoNoEncontradoException Si no existe.
     */
    public Credito obtenerCreditoPorId(int id) {
        try {
            return creditoRepository.obtenerPorId(id)
                    .orElseThrow(() -> new CreditoNoEncontradoException("Crédito con ID " + id + " no encontrado."));
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar crédito: " + e.getMessage(), e);
        }
    }

    /**
     * Lista la totalidad de créditos registrados en el sistema (CU-012).
     *
     * @return Lista completa de créditos.
     */
    public List<Credito> listarCreditos() {
        try {
            return creditoRepository.listarTodos();
        } catch (SQLException e) {
            throw new CrediNodaException("Error al listar créditos: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene el cronograma de cuotas asociadas a un crédito ordenadas cronológicamente.
     *
     * @param creditoId ID del crédito.
     * @return Lista de cuotas del crédito.
     */
    public List<Cuota> obtenerCuotasCredito(int creditoId) {
        try {
            return creditoRepository.obtenerCuotasPorCredito(creditoId);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar cuotas del crédito: " + e.getMessage(), e);
        }
    }

    /**
     * Calcula dinámicamente el saldo insoluto restante de un crédito (RN-010).
     * Saldo = total_pagar - total_pagado_credito.
     *
     * @param creditoId ID del crédito.
     * @return Saldo pendiente (cero si ya fue cancelado en su totalidad).
     */
    public BigDecimal calcularSaldoPendiente(int creditoId) {
        Credito credito = obtenerCreditoPorId(creditoId);
        try {
            BigDecimal pagado = pagoRepository.obtenerTotalPagadoCredito(creditoId);
            return credito.getTotalPagar().subtract(pagado).max(BigDecimal.ZERO);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al calcular saldo pendiente: " + e.getMessage(), e);
        }
    }

    /**
     * Evalúa y actualiza el estado del crédito según la situación de sus cuotas (RN-011).
     * Si todas las cuotas están PAGADA, pasa a PAGADO. Si está CANCELADO, lo preserva.
     *
     * @param creditoId ID del crédito.
     * @return Estado definitivo asignado al crédito.
     */
    public EstadoCredito determinarEstadoCredito(int creditoId) {
        Credito credito = obtenerCreditoPorId(creditoId);
        if (credito.getEstado() == EstadoCredito.CANCELADO) {
            return EstadoCredito.CANCELADO;
        }

        List<Cuota> cuotas = obtenerCuotasCredito(creditoId);
        if (cuotas.isEmpty()) {
            return EstadoCredito.ACTIVO;
        }

        boolean todasPagadas = cuotas.stream().allMatch(c -> c.getEstado() == EstadoCuota.PAGADA);
        EstadoCredito nuevoEstado = todasPagadas ? EstadoCredito.PAGADO : EstadoCredito.ACTIVO;

        if (credito.getEstado() != nuevoEstado) {
            try {
                creditoRepository.actualizarEstado(creditoId, nuevoEstado);
                credito.setEstado(nuevoEstado);
            } catch (SQLException e) {
                throw new CrediNodaException("Error al actualizar estado del crédito: " + e.getMessage(), e);
            }
        }
        return nuevoEstado;
    }

    /**
     * Sincroniza masivamente el estado de mora de todas las cuotas del sistema (CU-008, RN-008).
     *
     * @param fechaReferencia Fecha de corte (o null para usar la fecha actual).
     * @return Cantidad de cuotas cuyo indicador de mora cambió.
     */
    public int actualizarCuotasEnMora(LocalDate fechaReferencia) {
        LocalDate corte = (fechaReferencia != null) ? fechaReferencia : LocalDate.now();
        try {
            return creditoRepository.actualizarMoraCuotas(corte);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al sincronizar cuotas en mora: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta las próximas cuotas por cobrar para el Dashboard (CU-012, RN-013).
     *
     * @param limite Límite de resultados a consultar.
     * @return Lista de DTOs ProximoPagoItem.
     */
    public List<ProximoPagoItem> obtenerProximasCuotas(int limite) {
        try {
            return creditoRepository.obtenerCuotasPendientes(limite);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar próximas cuotas: " + e.getMessage(), e);
        }
    }
}
