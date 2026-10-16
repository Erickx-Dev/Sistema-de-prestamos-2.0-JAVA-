package com.credinoda.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO inmutable para una fila del historial consolidado de pagos.
 */
public record PagoDetalleItem(
        int pagoId,
        int creditoId,
        int cuotaId,
        String clienteNombre,
        int numeroCuota,
        LocalDate fechaPago,
        BigDecimal montoAbonado
) {
    public String codigoRecibo() {
        return String.format("REC-%05d", pagoId);
    }
}
