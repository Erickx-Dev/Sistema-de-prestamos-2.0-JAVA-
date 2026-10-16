package com.credinoda.persistence;

import com.credinoda.common.AppConfig;
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
     * Obtiene una nueva conexión a la base de datos de producción configurada en AppConfig.DATABASE_PATH.
     * Asegura la creación del directorio contenedor si no existe.
     *
     * @return Conexión JDBC configurada y con claves foráneas habilitadas.
     * @throws SQLException Si ocurre un error al abrir la conexión.
     */
    public static Connection getConnection() throws SQLException {
        AppConfig.ensureDirectoriesExist();
        return getConnection(AppConfig.DATABASE_PATH);
    }

    /**
     * Obtiene una nueva conexión a partir de una ruta de archivo específica.
     *
     * @param dbPath Ruta al archivo SQLite.
     * @return Conexión JDBC activa con claves foráneas habilitadas.
     * @throws SQLException Si ocurre un error de conexión.
     */
    public static Connection getConnection(Path dbPath) throws SQLException {
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
