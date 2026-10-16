package com.credinoda.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas del gestor de conexiones DatabaseConnection")
class DatabaseConnectionTest {

    @Test
    @DisplayName("Debe abrir conexión en memoria con PRAGMA foreign_keys = 1 habilitado")
    void testConexionEnMemoriaConForeignKeysHabilitadas() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection("jdbc:sqlite::memory:")) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Debe abrir conexión sobre un archivo temporal y verificar operatividad")
    void testConexionArchivoTemporal(@TempDir Path tempDir) throws SQLException {
        Path dbPath = tempDir.resolve("test_credinoda.db");
        try (Connection conn = DatabaseConnection.getConnection(dbPath)) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }
}
