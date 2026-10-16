package com.credinoda.service;

import com.credinoda.domain.dto.DistribucionCartera;
import com.credinoda.domain.dto.MetricasReporte;
import com.credinoda.domain.dto.ProyeccionSemanalItem;
import com.credinoda.domain.dto.RangoFechas;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.Pago;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Servicio para generación, consolidación y filtrado de reportes analíticos y métricas financieras (CU-013, RN-014).
 * Calcula los 10 KPIs del sistema, morosidad, ganancia líquida proporcional por prorrateo,
 * distribución de cartera en tres bolsas y comparativa semanal de recaudo vs. proyección.
 */
public class ReporteService {

    private static final DateTimeFormatter DIA_MES_FORMATTER = DateTimeFormatter.ofPattern("dd/MM");

    private final CreditoRepository creditoRepo;
    private final PagoRepository pagoRepo;

    public ReporteService(CreditoRepository creditoRepo, PagoRepository pagoRepo) {
        this.creditoRepo = Objects.requireNonNull(creditoRepo, "CreditoRepository no puede ser nulo.");
        this.pagoRepo = Objects.requireNonNull(pagoRepo, "PagoRepository no puede ser nulo.");
    }

    /**
     * Calcula el rango (fechaDesde, fechaHasta) según un filtro temporal predefinido (RN-014).
     *
     * @param tipo             Tipo de filtro ("TODOS", "HOY", "ESTA_SEMANA", "ESTE_MES", "ANIO_EN_CURSO", "PERSONALIZADO").
     * @param fechaReferencia  Fecha base de cálculo (si es nula, se usa LocalDate.now()).
     * @return Objeto RangoFechas con las fechas calculadas (pueden ser nulas para indicar sin límite).
     */
    public RangoFechas calcularRangoFechas(String tipo, LocalDate fechaReferencia) {
        if (tipo == null) {
            throw new DatosInvalidosException("Tipo de filtro temporal no reconocido: null");
        }

        LocalDate ref = fechaReferencia != null ? fechaReferencia : LocalDate.now();
        String clave = tipo.strip()
                .toUpperCase()
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace(" ", "_");

        return switch (clave) {
            case "TODOS", "HISTORICO", "HISTORICO_COMPLETO", "PERSONALIZADO" -> new RangoFechas(null, null);
            case "HOY" -> new RangoFechas(ref, ref);
            case "ESTA_SEMANA", "SEMANA" -> {
                LocalDate lunes = ref.with(DayOfWeek.MONDAY);
                LocalDate domingo = lunes.plusDays(6);
                yield new RangoFechas(lunes, domingo);
            }
            case "ESTE_MES", "MES" -> {
                LocalDate primerDia = ref.withDayOfMonth(1);
                LocalDate ultimoDia = ref.withDayOfMonth(ref.lengthOfMonth());
                yield new RangoFechas(primerDia, ultimoDia);
            }
            case "ANIO_EN_CURSO", "ESTE_ANIO", "AÑO_EN_CURSO", "ESTE_AÑO", "ANIO", "AÑO" -> {
                LocalDate primerDia = LocalDate.of(ref.getYear(), 1, 1);
                LocalDate ultimoDia = LocalDate.of(ref.getYear(), 12, 31);
                yield new RangoFechas(primerDia, ultimoDia);
            }
            default -> throw new DatosInvalidosException("Tipo de filtro temporal no reconocido: " + tipo);
        };
    }

    /**
     * Calcula los 10 KPIs financieros del sistema dentro del rango especificado (RN-014, VP-010, VP-011).
     *
     * @param fechaDesde Fecha inicial de corte (inclusive). Nula para sin límite inferior.
     * @param fechaHasta Fecha final de corte (inclusive). Nula para sin límite superior.
     * @return DTO MetricasReporte con los 10 indicadores financieros calculados.
     */
    public MetricasReporte obtenerMetricas(LocalDate fechaDesde, LocalDate fechaHasta) {
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            List<Credito> creditos = creditoRepo.listarTodos();
            if (fechaDesde != null) {
                creditos = creditos.stream()
                        .filter(c -> !c.getFechaInicio().isBefore(fechaDesde))
                        .collect(Collectors.toList());
            }
            if (fechaHasta != null) {
                creditos = creditos.stream()
                        .filter(c -> !c.getFechaInicio().isAfter(fechaHasta))
                        .collect(Collectors.toList());
            }

            List<Pago> pagos = pagoRepo.listarTodos();
            if (fechaDesde != null) {
                pagos = pagos.stream()
                        .filter(p -> !p.getFechaPago().isBefore(fechaDesde))
                        .collect(Collectors.toList());
            }
            if (fechaHasta != null) {
                pagos = pagos.stream()
                        .filter(p -> !p.getFechaPago().isAfter(fechaHasta))
                        .collect(Collectors.toList());
            }

            int activos = (int) creditos.stream().filter(c -> c.getEstado() == EstadoCredito.ACTIVO).count();
            int pagados = (int) creditos.stream().filter(c -> c.getEstado() == EstadoCredito.PAGADO).count();
            int total = creditos.size();

            BigDecimal capitalPrestado = creditos.stream()
                    .map(Credito::getCapital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal interesesGenerados = creditos.stream()
                    .map(Credito::getTotalIntereses)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal pagosRecibidos = pagos.stream()
                    .map(Pago::getMonto)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            LocalDate today = LocalDate.now();

            // 1. Saldo vencido y Tasa de morosidad (VP-011)
            BigDecimal saldoVencido = BigDecimal.ZERO;
            for (Credito c : creditos) {
                List<Cuota> cuotas = creditoRepo.obtenerCuotasPorCredito(c.getId());
                for (Cuota q : cuotas) {
                    if (q.getFechaVencimiento().isBefore(today) && q.getEstado() != EstadoCuota.PAGADA) {
                        BigDecimal pagadoCuota = pagoRepo.obtenerTotalPagadoCuota(q.getId());
                        BigDecimal saldoCuota = q.getMonto().subtract(pagadoCuota);
                        if (saldoCuota.compareTo(BigDecimal.ZERO) > 0) {
                            saldoVencido = saldoVencido.add(saldoCuota);
                        }
                    }
                }
            }

            BigDecimal tasaMorosidad;
            if (capitalPrestado.compareTo(BigDecimal.ZERO) > 0) {
                tasaMorosidad = saldoVencido.multiply(BigDecimal.valueOf(100))
                        .divide(capitalPrestado, 2, RoundingMode.HALF_UP);
            } else {
                tasaMorosidad = new BigDecimal("0.00");
            }

            // 2. Intereses cobrados por prorrateo (Ganancia líquida) y Margen (VP-010)
            Map<Integer, Credito> todosCreditosMap = creditoRepo.listarTodos().stream()
                    .collect(Collectors.toMap(Credito::getId, c -> c));

            BigDecimal interesesCobrados = BigDecimal.ZERO;
            for (Pago p : pagos) {
                Credito cred = todosCreditosMap.get(p.getCreditoId());
                if (cred != null && cred.getTotalPagar().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal factorInteres = cred.getTotalIntereses()
                            .divide(cred.getTotalPagar(), 10, RoundingMode.HALF_UP);
                    interesesCobrados = interesesCobrados.add(p.getMonto().multiply(factorInteres));
                }
            }
            interesesCobrados = interesesCobrados.setScale(2, RoundingMode.HALF_UP);

            BigDecimal margenGanancia;
            if (pagosRecibidos.compareTo(BigDecimal.ZERO) > 0) {
                margenGanancia = interesesCobrados.multiply(BigDecimal.valueOf(100))
                        .divide(pagosRecibidos, 2, RoundingMode.HALF_UP);
            } else {
                margenGanancia = new BigDecimal("0.00");
            }

            // 3. Ticket promedio y Plazo promedio
            BigDecimal ticketPromedio;
            BigDecimal plazoPromedio;
            if (total > 0) {
                ticketPromedio = capitalPrestado.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
                int sumaPlazos = creditos.stream().mapToInt(Credito::getPlazoMeses).sum();
                plazoPromedio = BigDecimal.valueOf(sumaPlazos).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            } else {
                ticketPromedio = new BigDecimal("0.00");
                plazoPromedio = new BigDecimal("0.0");
            }

            return new MetricasReporte(
                    activos,
                    pagados,
                    total,
                    capitalPrestado,
                    interesesGenerados,
                    pagosRecibidos,
                    saldoVencido,
                    tasaMorosidad,
                    interesesCobrados,
                    margenGanancia,
                    ticketPromedio,
                    plazoPromedio
            );

        } catch (SQLException e) {
            throw new CrediNodaException("Error al calcular métricas de reportes: " + e.getMessage(), e);
        }
    }

    /**
     * Clasifica los créditos del período en tres categorías excluyentes: al día, en mora y saldados (RN-014).
     *
     * @param fechaDesde Fecha inicial de corte.
     * @param fechaHasta Fecha final de corte.
     * @return DTO DistribucionCartera con las cantidades de créditos por categoría.
     */
    public DistribucionCartera obtenerDistribucionCartera(LocalDate fechaDesde, LocalDate fechaHasta) {
        validarRangoFechas(fechaDesde, fechaHasta);

        try {
            List<Credito> creditos = creditoRepo.listarTodos();
            if (fechaDesde != null) {
                creditos = creditos.stream()
                        .filter(c -> !c.getFechaInicio().isBefore(fechaDesde))
                        .collect(Collectors.toList());
            }
            if (fechaHasta != null) {
                creditos = creditos.stream()
                        .filter(c -> !c.getFechaInicio().isAfter(fechaHasta))
                        .collect(Collectors.toList());
            }

            LocalDate today = LocalDate.now();
            int alDiaCount = 0;
            int enMoraCount = 0;
            int saldadosCount = 0;

            for (Credito c : creditos) {
                BigDecimal pagado = pagoRepo.obtenerTotalPagadoCredito(c.getId());
                BigDecimal saldo = c.getTotalPagar().subtract(pagado);

                if (c.getEstado() == EstadoCredito.PAGADO || saldo.compareTo(BigDecimal.ZERO) <= 0) {
                    saldadosCount++;
                } else {
                    List<Cuota> cuotas = creditoRepo.obtenerCuotasPorCredito(c.getId());
                    boolean tieneMora = cuotas.stream().anyMatch(
                            q -> q.getEstado() != EstadoCuota.PAGADA && q.getFechaVencimiento().isBefore(today)
                    );

                    if (tieneMora || c.getEstado() == EstadoCredito.VENCIDO) {
                        enMoraCount++;
                    } else {
                        alDiaCount++;
                    }
                }
            }

            return new DistribucionCartera(alDiaCount, enMoraCount, saldadosCount, creditos.size());

        } catch (SQLException e) {
            throw new CrediNodaException("Error al obtener distribución de cartera: " + e.getMessage(), e);
        }
    }

    /**
     * Agrupa cronológicamente por semanas la proyección esperada frente al recaudo real (RN-014).
     *
     * @param fechaDesde  Fecha inicial de corte.
     * @param fechaHasta  Fecha final de corte.
     * @param numSemanas  Número de semanas a proyectar cuando el rango no está acotado (por defecto 5).
     * @return Lista de ProyeccionSemanalItem con los bloques calculados.
     */
    public List<ProyeccionSemanalItem> obtenerRecaudoVsProyeccionSemanal(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            int numSemanas
    ) {
        validarRangoFechas(fechaDesde, fechaHasta);
        int semanasAConsiderar = numSemanas > 0 ? numSemanas : 5;

        record Bloque(LocalDate inicio, LocalDate fin, String etiqueta) {}
        List<Bloque> bloques = new ArrayList<>();

        if (fechaDesde != null && fechaHasta != null && ChronoUnit.DAYS.between(fechaDesde, fechaHasta) >= 7) {
            LocalDate cursorInicio = fechaDesde;
            while (!cursorInicio.isAfter(fechaHasta)) {
                LocalDate cursorFin = cursorInicio.plusDays(6);
                if (cursorFin.isAfter(fechaHasta)) {
                    cursorFin = fechaHasta;
                }
                String etiqueta = cursorInicio.format(DIA_MES_FORMATTER) + " - " + cursorFin.format(DIA_MES_FORMATTER);
                bloques.add(new Bloque(cursorInicio, cursorFin, etiqueta));
                cursorInicio = cursorFin.plusDays(1);
                if (bloques.size() >= 8) {
                    break;
                }
            }
        } else {
            LocalDate today = LocalDate.now();
            LocalDate lunesActual = today.with(DayOfWeek.MONDAY);
            for (int i = semanasAConsiderar - 1; i >= 0; i--) {
                LocalDate lunes = lunesActual.minusWeeks(i);
                LocalDate domingo = lunes.plusDays(6);
                String etiqueta = lunes.format(DIA_MES_FORMATTER) + " - " + domingo.format(DIA_MES_FORMATTER);
                bloques.add(new Bloque(lunes, domingo, etiqueta));
            }
        }

        try {
            List<Cuota> todasCuotas = creditoRepo.listarTodasLasCuotas();
            List<Pago> todosPagos = pagoRepo.listarTodos();

            List<ProyeccionSemanalItem> resultado = new ArrayList<>();
            for (int index = 0; index < bloques.size(); index++) {
                Bloque b = bloques.get(index);
                BigDecimal proyectado = todasCuotas.stream()
                        .filter(q -> !q.getFechaVencimiento().isBefore(b.inicio()) && !q.getFechaVencimiento().isAfter(b.fin()))
                        .map(Cuota::getMonto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal recaudado = todosPagos.stream()
                        .filter(p -> !p.getFechaPago().isBefore(b.inicio()) && !p.getFechaPago().isAfter(b.fin()))
                        .map(Pago::getMonto)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                resultado.add(new ProyeccionSemanalItem(
                        index + 1,
                        b.inicio(),
                        b.fin(),
                        b.etiqueta(),
                        proyectado,
                        recaudado
                ));
            }

            return resultado;

        } catch (SQLException e) {
            throw new CrediNodaException("Error al calcular recaudo vs proyección semanal: " + e.getMessage(), e);
        }
    }

    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new DatosInvalidosException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }
}
