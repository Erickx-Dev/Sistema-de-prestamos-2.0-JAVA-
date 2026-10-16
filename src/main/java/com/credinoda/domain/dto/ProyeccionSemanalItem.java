package com.credinoda.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO inmutable para una barra semanal del gráfico de recaudos vs proyección esperada.
 */
public record ProyeccionSemanalItem(
        int semanaIndex,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        String etiqueta,
        BigDecimal proyectado,
        BigDecimal recaudado
) {
}
