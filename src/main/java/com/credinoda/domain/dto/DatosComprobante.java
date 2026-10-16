package com.credinoda.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO inmutable con todos los datos requeridos para maquetar el comprobante de pago en PDF y su código QR.
 */
public record DatosComprobante(
        int pagoId,
        String numeroRecibo,
        LocalDate fechaPago,
        BigDecimal pagoMonto,
        int clienteId,
        String clienteNombre,
        String clienteDocumento,
        String clienteTelefono,
        int creditoId,
        BigDecimal creditoCapital,
        BigDecimal creditoTotalPagar,
        int creditoPlazoMeses,
        String creditoFrecuencia,
        LocalDate creditoFechaInicio,
        int cuotaId,
        int numeroCuota,
        int totalCuotas,
        LocalDate cuotaFechaVencimiento,
        BigDecimal cuotaMonto,
        BigDecimal totalPagadoCuota,
        BigDecimal saldoCuotaAntes,
        BigDecimal saldoCuotaDespues,
        String cuotaEstado,
        BigDecimal totalPagadoCredito,
        BigDecimal saldoCreditoRestante,
        BigDecimal porcentajePagadoCredito
) {
}
