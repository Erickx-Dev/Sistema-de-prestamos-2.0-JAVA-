package com.credinoda.domain.dto;

import com.credinoda.common.Formatters;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;

import java.time.LocalDateTime;

/**
 * Record DTO inmutable optimizado para la presentación tabular en {@code AuditoriaView}.
 */
public record RegistroAuditoriaItem(
        Long id,
        LocalDateTime fechaHora,
        String fechaHoraFormateada,
        String usuario,
        String rol,
        AccionAuditoria accion,
        String accionNombre,
        String entidad,
        String entidadId,
        ResultadoAuditoria resultado,
        String resultadoEtiqueta,
        String estacionTrabajo,
        String detalle
) {

    public static RegistroAuditoriaItem desde(EventoAuditoria evento) {
        String fechaFormateada = Formatters.formatearFechaHora(evento.getFechaHora());
        return new RegistroAuditoriaItem(
                evento.getId(),
                evento.getFechaHora(),
                fechaFormateada,
                evento.getUsername(),
                evento.getRol(),
                evento.getAccion(),
                evento.getAccion().getNombreVisible(),
                evento.getEntidad(),
                evento.getEntidadId() != null ? evento.getEntidadId() : "-",
                evento.getResultado(),
                evento.getResultado().getEtiqueta(),
                evento.getEstacionTrabajo(),
                evento.getDetalle() != null ? evento.getDetalle() : ""
        );
    }
}
