package com.credinoda.domain.dto;

import java.time.LocalDate;

/**
 * DTO inmutable que encapsula un intervalo temporal (fechaDesde, fechaHasta).
 * Los extremos pueden ser nulos para indicar rangos abiertos (ej. histórico completo).
 */
public record RangoFechas(
        LocalDate desde,
        LocalDate hasta
) {
}
