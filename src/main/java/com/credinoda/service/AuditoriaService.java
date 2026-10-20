package com.credinoda.service;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.dto.RegistroAuditoriaItem;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.persistence.repository.AuditoriaRepository;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Servicio centralizado para el registro y consulta de la bitácora inmutable de auditoría (CU-021, RN-027, RN-028).
 */
public class AuditoriaService {

    private final AuditoriaRepository repository;
    private final Clock clock;
    private final String estacionTrabajo;

    public AuditoriaService(AuditoriaRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public AuditoriaService(AuditoriaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = Objects.requireNonNull(clock, "El reloj no puede ser nulo");
        this.estacionTrabajo = resolverEstacionTrabajo();
    }

    /**
     * Registra un evento de auditoría capturando automáticamente la sesión activa de {@link ContextoSeguridad}.
     */
    public EventoAuditoria registrar(AccionAuditoria accion,
                                     String entidad,
                                     String entidadId,
                                     String detalle,
                                     ResultadoAuditoria resultado) {
        SesionUsuario sesion = ContextoSeguridad.getSesionActual();
        Long usuarioId = sesion.usuarioId() > 0 ? (long) sesion.usuarioId() : null;
        String username = sesion.username();
        String rol = sesion.rol() != null ? sesion.rol().name() : "SISTEMA";

        return registrarConUsuario(username, rol, usuarioId, accion, entidad, entidadId, detalle, resultado);
    }

    /**
     * Registra un evento de auditoría con información explícita de usuario (útil en logins, bloqueos o fallos).
     */
    public EventoAuditoria registrarConUsuario(String username,
                                              String rol,
                                              Long usuarioId,
                                              AccionAuditoria accion,
                                              String entidad,
                                              String entidadId,
                                              String detalle,
                                              ResultadoAuditoria resultado) {
        if (repository == null) {
            return null;
        }

        EventoAuditoria evento = new EventoAuditoria(
                null,
                LocalDateTime.now(clock),
                usuarioId,
                username != null && !username.isBlank() ? username : "ANONIMO",
                rol != null && !rol.isBlank() ? rol : "SISTEMA",
                accion,
                entidad,
                entidadId,
                detalle,
                resultado,
                estacionTrabajo
        );

        try {
            return repository.registrar(evento);
        } catch (SQLException e) {
            System.err.println("Error al persistir evento de auditoría: " + e.getMessage());
            return evento;
        }
    }

    /**
     * Registra un evento de auditoría de manera atómica dentro de una transacción JDBC en curso (RN-028).
     * Si ocurre un error, la excepción SQLException se propaga para permitir el rollback conjunto.
     */
    public EventoAuditoria registrarEnTransaccion(Connection conn,
                                                 AccionAuditoria accion,
                                                 String entidad,
                                                 String entidadId,
                                                 String detalle,
                                                 ResultadoAuditoria resultado) throws SQLException {
        SesionUsuario sesion = ContextoSeguridad.getSesionActual();
        Long usuarioId = sesion.usuarioId() > 0 ? (long) sesion.usuarioId() : null;
        String username = sesion.username();
        String rol = sesion.rol() != null ? sesion.rol().name() : "SISTEMA";

        return registrarEnTransaccion(conn, username, rol, usuarioId, accion, entidad, entidadId, detalle, resultado);
    }

    /**
     * Registra un evento atómicamente en una conexión transaccional con usuario explícito.
     */
    public EventoAuditoria registrarEnTransaccion(Connection conn,
                                                 String username,
                                                 String rol,
                                                 Long usuarioId,
                                                 AccionAuditoria accion,
                                                 String entidad,
                                                 String entidadId,
                                                 String detalle,
                                                 ResultadoAuditoria resultado) throws SQLException {
        if (repository == null) {
            return null;
        }

        EventoAuditoria evento = new EventoAuditoria(
                null,
                LocalDateTime.now(clock),
                usuarioId,
                username != null && !username.isBlank() ? username : "ANONIMO",
                rol != null && !rol.isBlank() ? rol : "SISTEMA",
                accion,
                entidad,
                entidadId,
                detalle,
                resultado,
                estacionTrabajo
        );

        return repository.registrarEnTransaccion(conn, evento);
    }

    /**
     * Consulta eventos de auditoría según los criterios del filtro.
     */
    public List<EventoAuditoria> buscar(FiltroAuditoria filtro) {
        if (repository == null) {
            return Collections.emptyList();
        }
        try {
            return repository.buscar(filtro);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al consultar la bitácora de auditoría: " + e.getMessage(), e);
        }
    }

    /**
     * Consulta eventos de auditoría y los mapea a items DTO listos para la interfaz gráfica.
     */
    public List<RegistroAuditoriaItem> buscarItems(FiltroAuditoria filtro) {
        List<EventoAuditoria> eventos = buscar(filtro);
        List<RegistroAuditoriaItem> items = new ArrayList<>(eventos.size());
        for (EventoAuditoria evento : eventos) {
            items.add(RegistroAuditoriaItem.desde(evento));
        }
        return items;
    }

    /**
     * Cuenta el total de registros coincidentes con el filtro.
     */
    public long contar(FiltroAuditoria filtro) {
        if (repository == null) {
            return 0L;
        }
        try {
            return repository.contar(filtro);
        } catch (SQLException e) {
            throw new CrediNodaException("Error al contar registros de auditoría: " + e.getMessage(), e);
        }
    }

    /**
     * Genera un detalle legible en formato comparativo 'campo: valor_anterior -> valor_nuevo'.
     */
    public static String generarDetalleCambios(Map<String, ?> antes, Map<String, ?> despues) {
        if (antes == null || despues == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ?> entry : despues.entrySet()) {
            String campo = entry.getKey();
            Object valNuevo = entry.getValue();
            Object valViejo = antes.get(campo);

            if (!Objects.equals(valViejo, valNuevo)) {
                if (!sb.isEmpty()) {
                    sb.append("; ");
                }
                sb.append(campo).append(": '")
                  .append(valViejo != null ? valViejo : "vacio")
                  .append("' -> '")
                  .append(valNuevo != null ? valNuevo : "vacio")
                  .append("'");
            }
        }
        return sb.toString();
    }

    private static String resolverEstacionTrabajo() {
        try {
            String host = System.getenv("COMPUTERNAME");
            if (host == null || host.isBlank()) {
                host = System.getenv("HOSTNAME");
            }
            if (host == null || host.isBlank()) {
                host = InetAddress.getLocalHost().getHostName();
            }
            return host != null ? host : "localhost";
        } catch (Exception e) {
            return "localhost";
        }
    }
}
