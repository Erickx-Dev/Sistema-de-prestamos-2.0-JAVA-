package com.credinoda.domain.dto;

/**
 * DTO inmutable para la clasificación de cartera en Reportes (al día, en mora, saldados).
 */
public record DistribucionCartera(
        int alDia,
        int enMora,
        int saldados,
        int total
) {
}
