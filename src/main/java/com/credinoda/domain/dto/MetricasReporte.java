package com.credinoda.domain.dto;

import java.math.BigDecimal;

/**
 * DTO inmutable con los 10 KPIs financieros calculados para el panel de Reportes.
 */
public record MetricasReporte(
        int activos,
        int pagados,
        int total,
        BigDecimal capitalPrestado,
        BigDecimal interesesGenerados,
        BigDecimal pagosRecibidos,
        BigDecimal saldoVencido,
        BigDecimal tasaMorosidad,
        BigDecimal interesesCobrados,
        BigDecimal margenGanancia,
        BigDecimal ticketPromedio,
        BigDecimal plazoPromedio
) {
}
