package com.credinoda.domain.dto;

import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;

import java.time.LocalDate;

/**
 * Record DTO inmutable para consultas dinámicas y filtradas sobre la bitácora de auditoría.
 */
public record FiltroAuditoria(
        LocalDate desde,
        LocalDate hasta,
        String username,
        AccionAuditoria accion,
        String entidad,
        ResultadoAuditoria resultado,
        int limite,
        int offset
) {

    public FiltroAuditoria {
        if (limite <= 0) {
            limite = 100;
        }
        if (offset < 0) {
            offset = 0;
        }
    }

    public static FiltroAuditoria sinFiltro(int limite) {
        return new FiltroAuditoria(null, null, null, null, null, null, limite, 0);
    }

    public static FiltroAuditoria rango(LocalDate desde, LocalDate hasta, int limite) {
        return new FiltroAuditoria(desde, hasta, null, null, null, null, limite, 0);
    }
}
