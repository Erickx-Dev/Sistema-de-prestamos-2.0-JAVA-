package com.credinoda.persistence.repository;

import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la gestión relacional de usuarios y credenciales del sistema (CU-018, CU-019, RN-022 a RN-025).
 */
public class UsuarioRepository {

    private final Connection connection;

    public UsuarioRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión a base de datos no puede ser nula");
    }

    /**
     * Inserta un nuevo usuario en la base de datos y le asigna el ID autogenerado.
     *
     * @param usuario Entidad usuario con datos validados y contraseña hasheada.
     * @return Instancia del usuario persistido con ID.
     * @throws SQLException Si ocurre un error JDBC o violación de unicidad en username.
     */
    public Usuario crear(Usuario usuario) throws SQLException {
        String sql = """
                INSERT INTO usuarios (username, password_hash, nombre_completo, rol, activo,
                                      intentos_fallidos, bloqueado_hasta, ultimo_acceso, fecha_creacion)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, usuario.getUsername().toLowerCase());
            ps.setString(2, usuario.getPasswordHash());
            ps.setString(3, usuario.getNombreCompleto());
            ps.setString(4, usuario.getRol().name());
            ps.setInt(5, usuario.isActivo() ? 1 : 0);
            ps.setInt(6, usuario.getIntentosFallidos());
            ps.setString(7, usuario.getBloqueadoHasta() != null ? usuario.getBloqueadoHasta().toString() : null);
            ps.setString(8, usuario.getUltimoAcceso() != null ? usuario.getUltimoAcceso().toString() : null);
            ps.setString(9, usuario.getFechaCreacion() != null ? usuario.getFechaCreacion().toString() : LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    usuario.setId(rs.getInt(1));
                }
            }
        }
        return usuario;
    }

    /**
     * Alias de conveniencia para crear(usuario).
     */
    public Usuario insertar(Usuario usuario) throws SQLException {
        return crear(usuario);
    }

    /**
     * Obtiene un usuario a partir de su ID primario.
     *
     * @param id ID del usuario.
     * @return Optional con el usuario si existe, o vacío.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Usuario> obtenerPorId(int id) throws SQLException {
        String sql = """
                SELECT id, username, password_hash, nombre_completo, rol, activo,
                       intentos_fallidos, bloqueado_hasta, ultimo_acceso, fecha_creacion
                FROM usuarios WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Obtiene un usuario por su nombre de usuario único (insensible a mayúsculas/minúsculas).
     *
     * @param username Nombre de usuario consultado.
     * @return Optional con el usuario si existe, o vacío.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Usuario> obtenerPorUsername(String username) throws SQLException {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, username, password_hash, nombre_completo, rol, activo,
                       intentos_fallidos, bloqueado_hasta, ultimo_acceso, fecha_creacion
                FROM usuarios WHERE username = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapearUsuario(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Actualiza la información básica del usuario (nombre completo, rol y estado activo).
     *
     * @param usuario Usuario con datos modificados.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public void actualizar(Usuario usuario) throws SQLException {
        String sql = """
                UPDATE usuarios
                SET nombre_completo = ?, rol = ?, activo = ?
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, usuario.getNombreCompleto());
            ps.setString(2, usuario.getRol().name());
            ps.setInt(3, usuario.isActivo() ? 1 : 0);
            ps.setInt(4, usuario.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Actualiza el hash de la contraseña de un usuario.
     *
     * @param id ID del usuario.
     * @param nuevoHash Nuevo hash BCrypt.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public void actualizarPassword(int id, String nuevoHash) throws SQLException {
        String sql = "UPDATE usuarios SET password_hash = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, nuevoHash);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Actualiza el tracking de seguridad de un usuario tras intentos o éxito de autenticación.
     *
     * @param id ID del usuario.
     * @param intentosFallidos Contador de fallos acumulados.
     * @param bloqueadoHasta Fecha/hora hasta la cual está bloqueado, o null si está desbloqueado.
     * @param ultimoAcceso Fecha/hora de acceso exitoso, o null para mantener el previo.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public void actualizarSeguridad(int id, int intentosFallidos, LocalDateTime bloqueadoHasta, LocalDateTime ultimoAcceso) throws SQLException {
        String sql = """
                UPDATE usuarios
                SET intentos_fallidos = ?,
                    bloqueado_hasta = ?,
                    ultimo_acceso = COALESCE(?, ultimo_acceso)
                WHERE id = ?
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, intentosFallidos);
            ps.setString(2, bloqueadoHasta != null ? bloqueadoHasta.toString() : null);
            ps.setString(3, ultimoAcceso != null ? ultimoAcceso.toString() : null);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    /**
     * Sobrecarga de actualizarSeguridad que no altera el último acceso registrado.
     */
    public void actualizarSeguridad(int id, int intentosFallidos, LocalDateTime bloqueadoHasta) throws SQLException {
        actualizarSeguridad(id, intentosFallidos, bloqueadoHasta, null);
    }

    /**
     * Lista todos los usuarios registrados ordenados por fecha de creación descendente.
     *
     * @return Lista de entidades Usuario.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Usuario> listarTodos() throws SQLException {
        List<Usuario> usuarios = new ArrayList<>();
        String sql = """
                SELECT id, username, password_hash, nombre_completo, rol, activo,
                       intentos_fallidos, bloqueado_hasta, ultimo_acceso, fecha_creacion
                FROM usuarios
                ORDER BY id ASC
                """;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                usuarios.add(mapearUsuario(rs));
            }
        }
        return usuarios;
    }

    /**
     * Cuenta el número total de usuarios activos con rol de ADMINISTRADOR (RN-025, VP-021).
     *
     * @return Cantidad de administradores activos en el sistema.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public int contarAdministradoresActivos() throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios WHERE rol = 'ADMINISTRADOR' AND activo = 1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    /**
     * Cuenta el número total de usuarios registrados en el sistema.
     *
     * @return Cantidad de usuarios.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public int contarUsuarios() throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuarios";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private Usuario mapearUsuario(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        String username = rs.getString("username");
        String passwordHash = rs.getString("password_hash");
        String nombreCompleto = rs.getString("nombre_completo");
        Rol rol = Rol.valueOf(rs.getString("rol"));
        boolean activo = rs.getInt("activo") == 1;
        int intentosFallidos = rs.getInt("intentos_fallidos");

        String strBloqueado = rs.getString("bloqueado_hasta");
        LocalDateTime bloqueadoHasta = (strBloqueado != null && !strBloqueado.isBlank())
                ? LocalDateTime.parse(strBloqueado) : null;

        String strUltimoAcceso = rs.getString("ultimo_acceso");
        LocalDateTime ultimoAcceso = (strUltimoAcceso != null && !strUltimoAcceso.isBlank())
                ? LocalDateTime.parse(strUltimoAcceso) : null;

        String strCreacion = rs.getString("fecha_creacion");
        LocalDateTime fechaCreacion = (strCreacion != null && !strCreacion.isBlank())
                ? LocalDateTime.parse(strCreacion) : LocalDateTime.now();

        return new Usuario(id, username, passwordHash, nombreCompleto, rol,
                activo, intentosFallidos, bloqueadoHasta, ultimoAcceso, fechaCreacion);
    }
}
