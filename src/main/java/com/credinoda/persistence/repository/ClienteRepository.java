package com.credinoda.persistence.repository;

import com.credinoda.domain.model.Cliente;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la gestión relacional de clientes prestatarios.
 */
public class ClienteRepository {

    private final Connection connection;

    public ClienteRepository(Connection connection) {
        this.connection = Objects.requireNonNull(connection, "La conexión a base de datos no puede ser nula");
    }

    /**
     * Inserta un nuevo cliente en la base de datos y le asigna la clave primaria generada.
     *
     * @param cliente Entidad cliente a persistir.
     * @return Cliente con su ID asignado.
     * @throws SQLException Si ocurre un fallo en la inserción o se viola la unicidad del documento.
     */
    public Cliente crear(Cliente cliente) throws SQLException {
        String sql = "INSERT INTO clientes (nombre, telefono, documento, fecha_registro) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getTelefono());
            ps.setString(3, cliente.getDocumento());
            ps.setString(4, cliente.getFechaRegistro() != null ? cliente.getFechaRegistro().toString() : LocalDate.now().toString());

            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cliente.setId(rs.getInt(1));
                }
            }
        }
        return cliente;
    }

    /**
     * Obtiene un cliente por su identificador primario.
     *
     * @param id Identificador del cliente.
     * @return Optional con el cliente si existe, o vacío.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Cliente> obtenerPorId(int id) throws SQLException {
        String sql = "SELECT id, nombre, telefono, documento, fecha_registro FROM clientes WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Obtiene un cliente por su documento de identidad único (RN-002).
     *
     * @param documento Documento del cliente.
     * @return Optional con el cliente si existe, o vacío.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public Optional<Cliente> obtenerPorDocumento(String documento) throws SQLException {
        String sql = "SELECT id, nombre, telefono, documento, fecha_registro FROM clientes WHERE documento = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, documento != null ? documento.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Búsqueda general por coincidencia parcial en nombre o documento (RN-003).
     *
     * @param query Término de búsqueda.
     * @return Lista de clientes ordenados alfabéticamente por nombre.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Cliente> buscarGeneral(String query) throws SQLException {
        String pattern = "%" + (query != null ? query.trim() : "") + "%";
        String sql = "SELECT id, nombre, telefono, documento, fecha_registro FROM clientes "
                + "WHERE nombre LIKE ? OR documento LIKE ? ORDER BY nombre ASC";
        List<Cliente> clientes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pattern);
            ps.setString(2, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapRow(rs));
                }
            }
        }
        return clientes;
    }

    /**
     * Búsqueda por coincidencia parcial en nombre.
     *
     * @param query Nombre o fragmento a buscar.
     * @return Lista de clientes coincidentes.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Cliente> buscarPorNombre(String query) throws SQLException {
        String pattern = "%" + (query != null ? query.trim() : "") + "%";
        String sql = "SELECT id, nombre, telefono, documento, fecha_registro FROM clientes WHERE nombre LIKE ? ORDER BY nombre ASC";
        List<Cliente> clientes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapRow(rs));
                }
            }
        }
        return clientes;
    }

    /**
     * Actualiza los datos de un cliente existente (nombre, teléfono y documento).
     *
     * @param cliente Entidad con los datos modificados.
     * @throws SQLException Si ocurre un fallo en la actualización o violación de unicidad.
     */
    public void actualizar(Cliente cliente) throws SQLException {
        String sql = "UPDATE clientes SET nombre = ?, telefono = ?, documento = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getTelefono());
            ps.setString(3, cliente.getDocumento());
            ps.setInt(4, cliente.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Retorna la totalidad de clientes registrados en el sistema ordenados alfabéticamente.
     *
     * @return Lista completa de clientes.
     * @throws SQLException Si ocurre un error JDBC.
     */
    public List<Cliente> listarTodos() throws SQLException {
        String sql = "SELECT id, nombre, telefono, documento, fecha_registro FROM clientes ORDER BY nombre ASC";
        List<Cliente> clientes = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                clientes.add(mapRow(rs));
            }
        }
        return clientes;
    }

    private Cliente mapRow(ResultSet rs) throws SQLException {
        return new Cliente(
                rs.getInt("id"),
                rs.getString("nombre"),
                rs.getString("telefono"),
                rs.getString("documento"),
                LocalDate.parse(rs.getString("fecha_registro"))
        );
    }
}
