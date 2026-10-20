package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la bitácora inmutable de eventos de auditoría (CU-021, RN-027, RN-028).
 */
public class AuditoriaRepository {

    private final Connection connection;

    public AuditoriaRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión a base de datos no puede ser nula");
    }

    /**
     * Registra un evento de auditoría en la conexión principal del repositorio.
     */
    public EventoAuditoria registrar(EventoAuditoria evento) throws SQLException {
        return registrarEnTransaccion(this.connection, evento);
    }

    /**
     * Registra un evento de auditoría dentro de una conexión transaccional externa (RN-028).
     */
    public EventoAuditoria registrarEnTransaccion(Connection conn, EventoAuditoria evento) throws SQLException {
        Objects.requireNonNull(conn, "La conexión transaccional no puede ser nula");
        Objects.requireNonNull(evento, "El evento de auditoría no puede ser nulo");

        String sql = """
                INSERT INTO auditoria (fecha_hora, usuario_id, username, rol, accion, entidad, entidad_id, detalle, resultado, estacion_trabajo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, evento.getFechaHora().toString());
            if (evento.getUsuarioId() != null) {
                ps.setLong(2, evento.getUsuarioId());
            } else {
                ps.setNull(2, Types.INTEGER);
            }
            ps.setString(3, evento.getUsername());
            ps.setString(4, evento.getRol());
            ps.setString(5, evento.getAccion().name());
            ps.setString(6, evento.getEntidad());
            if (evento.getEntidadId() != null) {
                ps.setString(7, evento.getEntidadId());
            } else {
                ps.setNull(7, Types.VARCHAR);
            }
            if (evento.getDetalle() != null) {
                ps.setString(8, evento.getDetalle());
            } else {
                ps.setNull(8, Types.VARCHAR);
            }
            ps.setString(9, evento.getResultado().name());
            ps.setString(10, evento.getEstacionTrabajo());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    long idGenerado = rs.getLong(1);
                    return new EventoAuditoria(
                            idGenerado,
                            evento.getFechaHora(),
                            evento.getUsuarioId(),
                            evento.getUsername(),
                            evento.getRol(),
                            evento.getAccion(),
                            evento.getEntidad(),
                            evento.getEntidadId(),
                            evento.getDetalle(),
                            evento.getResultado(),
                            evento.getEstacionTrabajo()
                    );
                }
            }
        }
        return evento;
    }

    /**
     * Obtiene un evento de auditoría por su ID.
     */
    public Optional<EventoAuditoria> obtenerPorId(long id) throws SQLException {
        String sql = "SELECT * FROM auditoria WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearFila(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Consulta eventos de auditoría según los criterios del filtro con paginación y orden descendente.
     */
    public List<EventoAuditoria> buscar(FiltroAuditoria filtro) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM auditoria WHERE 1=1");
        List<Object> parametros = construirClausulaWhere(filtro, sql);

        sql.append(" ORDER BY fecha_hora DESC, id DESC LIMIT ? OFFSET ?");
        parametros.add(filtro.limite());
        parametros.add(filtro.offset());

        List<EventoAuditoria> resultado = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    resultado.add(mapearFila(rs));
                }
            }
        }
        return resultado;
    }

    /**
     * Cuenta el total de eventos que coinciden con los criterios del filtro.
     */
    public long contar(FiltroAuditoria filtro) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM auditoria WHERE 1=1");
        List<Object> parametros = construirClausulaWhere(filtro, sql);

        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    private List<Object> construirClausulaWhere(FiltroAuditoria filtro, StringBuilder sql) {
        List<Object> parametros = new ArrayList<>();
        if (filtro == null) {
            return parametros;
        }

        if (filtro.desde() != null) {
            sql.append(" AND fecha_hora >= ?");
            parametros.add(filtro.desde().atStartOfDay().toString());
        }
        if (filtro.hasta() != null) {
            sql.append(" AND fecha_hora <= ?");
            parametros.add(filtro.hasta().atTime(23, 59, 59, 999999999).toString());
        }
        if (filtro.username() != null && !filtro.username().isBlank()) {
            sql.append(" AND LOWER(username) LIKE ?");
            parametros.add("%" + filtro.username().trim().toLowerCase() + "%");
        }
        if (filtro.accion() != null) {
            sql.append(" AND accion = ?");
            parametros.add(filtro.accion().name());
        }
        if (filtro.entidad() != null && !filtro.entidad().isBlank()) {
            sql.append(" AND entidad = ?");
            parametros.add(filtro.entidad().trim().toUpperCase());
        }
        if (filtro.resultado() != null) {
            sql.append(" AND resultado = ?");
            parametros.add(filtro.resultado().name());
        }

        return parametros;
    }

    private EventoAuditoria mapearFila(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        LocalDateTime fechaHora = LocalDateTime.parse(rs.getString("fecha_hora"));
        long uIdVal = rs.getLong("usuario_id");
        Long usuarioId = rs.wasNull() ? null : uIdVal;
        String username = rs.getString("username");
        String rol = rs.getString("rol");
        AccionAuditoria accion = AccionAuditoria.valueOf(rs.getString("accion"));
        String entidad = rs.getString("entidad");
        String entidadId = rs.getString("entidad_id");
        String detalle = rs.getString("detalle");
        ResultadoAuditoria resultado = ResultadoAuditoria.valueOf(rs.getString("resultado"));
        String estacionTrabajo = rs.getString("estacion_trabajo");

        return new EventoAuditoria(
                id,
                fechaHora,
                usuarioId,
                username,
                rol,
                accion,
                entidad,
                entidadId,
                detalle,
                resultado,
                estacionTrabajo
        );
    }
}
