package com.credinoda.persistence;

import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestor de conexiones JDBC hacia la base de datos SQLite local.
 * Aplica mandatoriamente PRAGMA foreign_keys = ON para garantizar integridad referencial.
 */
public final class DatabaseConnection {

    private DatabaseConnection() {
        // Clase de utilidades/fábrica de conexiones, no instanciable
    }

    /**
     * Obtiene una nueva conexión a partir de una ruta de archivo específica.
     *
     * @param dbPath Ruta al archivo SQLite.
     * @return Conexión JDBC activa con claves foráneas habilitadas.
     * @throws SQLException Si ocurre un error de conexión.
     */
    public static Connection getConnection(Path dbPath) throws SQLException {
        try {
            if (dbPath != null && dbPath.getParent() != null) {
                java.nio.file.Files.createDirectories(dbPath.getParent());
            }
        } catch (java.io.IOException e) {
            throw new SQLException("No se pudo crear el directorio contenedor de la base de datos: " + dbPath.getParent(), e);
        }
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath().toString().replace('\\', '/');
        return getConnection(url);
    }

    /**
     * Obtiene una nueva conexión a partir de una URL JDBC SQLite (ej. 'jdbc:sqlite::memory:').
     *
     * @param jdbcUrl URL JDBC completa.
     * @return Conexión JDBC activa con claves foráneas habilitadas.
     * @throws SQLException Si ocurre un error de conexión.
     */
    public static Connection getConnection(String jdbcUrl) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(5000);

        Connection conn = DriverManager.getConnection(jdbcUrl, config.toProperties());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
    }
}
