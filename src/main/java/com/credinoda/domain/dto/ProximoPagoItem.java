package com.credinoda.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO inmutable para representar una cuota próxima a vencer o vencida en el Dashboard.
 */
public record ProximoPagoItem(
        int creditoId,
        int cuotaId,
        String clienteNombre,
        int numeroCuota,
        BigDecimal monto,
        LocalDate fechaVencimiento,
        String estado,
        boolean enMora
) {
    public String etiquetaEstado() {
        if (enMora && ("PENDIENTE".equalsIgnoreCase(estado) || "PARCIAL".equalsIgnoreCase(estado))) {
            return estado + " (EN MORA)";
        }
        return estado;
    }
}
