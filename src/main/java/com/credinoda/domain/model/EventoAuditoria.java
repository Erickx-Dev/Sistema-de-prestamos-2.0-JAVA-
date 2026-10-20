package com.credinoda.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad inmutable que representa un evento sensible registrado en la bitácora de auditoría (RN-027).
 */
public final class EventoAuditoria {

    private final Long id;
    private final LocalDateTime fechaHora;
    private final Long usuarioId;
    private final String username;
    private final String rol;
    private final AccionAuditoria accion;
    private final String entidad;
    private final String entidadId;
    private final String detalle;
    private final ResultadoAuditoria resultado;
    private final String estacionTrabajo;

    public EventoAuditoria(Long id,
                           LocalDateTime fechaHora,
                           Long usuarioId,
                           String username,
                           String rol,
                           AccionAuditoria accion,
                           String entidad,
                           String entidadId,
                           String detalle,
                           ResultadoAuditoria resultado,
                           String estacionTrabajo) {
        this.id = id;
        this.fechaHora = Objects.requireNonNull(fechaHora, "fechaHora no puede ser nula");
        this.usuarioId = usuarioId;
        this.username = Objects.requireNonNull(username, "username no puede ser nulo");
        this.rol = Objects.requireNonNull(rol, "rol no puede ser nulo");
        this.accion = Objects.requireNonNull(accion, "accion no puede ser nula");
        this.entidad = Objects.requireNonNull(entidad, "entidad no puede ser nula");
        this.entidadId = entidadId;
        this.detalle = detalle;
        this.resultado = Objects.requireNonNull(resultado, "resultado no puede ser nulo");
        this.estacionTrabajo = Objects.requireNonNull(estacionTrabajo, "estacionTrabajo no puede ser nula");
    }

    public static EventoAuditoria crearNuevo(LocalDateTime fechaHora,
                                             Long usuarioId,
                                             String username,
                                             String rol,
                                             AccionAuditoria accion,
                                             String entidad,
                                             String entidadId,
                                             String detalle,
                                             ResultadoAuditoria resultado,
                                             String estacionTrabajo) {
        return new EventoAuditoria(null, fechaHora, usuarioId, username, rol, accion, entidad, entidadId, detalle, resultado, estacionTrabajo);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public String getUsername() {
        return username;
    }

    public String getRol() {
        return rol;
    }

    public AccionAuditoria getAccion() {
        return accion;
    }

    public String getEntidad() {
        return entidad;
    }

    public String getEntidadId() {
        return entidadId;
    }

    public String getDetalle() {
        return detalle;
    }

    public ResultadoAuditoria getResultado() {
        return resultado;
    }

    public String getEstacionTrabajo() {
        return estacionTrabajo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventoAuditoria that = (EventoAuditoria) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(fechaHora, that.fechaHora) &&
                Objects.equals(usuarioId, that.usuarioId) &&
                Objects.equals(username, that.username) &&
                Objects.equals(rol, that.rol) &&
                accion == that.accion &&
                Objects.equals(entidad, that.entidad) &&
                Objects.equals(entidadId, that.entidadId) &&
                Objects.equals(detalle, that.detalle) &&
                resultado == that.resultado &&
                Objects.equals(estacionTrabajo, that.estacionTrabajo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fechaHora, usuarioId, username, rol, accion, entidad, entidadId, detalle, resultado, estacionTrabajo);
    }

    @Override
    public String toString() {
        return "EventoAuditoria{" +
                "id=" + id +
                ", fechaHora=" + fechaHora +
                ", username='" + username + '\'' +
                ", rol='" + rol + '\'' +
                ", accion=" + accion +
                ", entidad='" + entidad + '\'' +
                ", entidadId='" + entidadId + '\'' +
                ", resultado=" + resultado +
                ", estacionTrabajo='" + estacionTrabajo + '\'' +
                '}';
    }
}
