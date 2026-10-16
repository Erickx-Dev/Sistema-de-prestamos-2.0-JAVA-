package com.credinoda.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas del inicializador de esquema limpio SchemaInitializer")
class SchemaInitializerTest {

    @Test
    @DisplayName("Debe crear las 4 tablas relacionales limpias e índices en SQLite")
    void testCreacionTablasLimpias() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection("jdbc:sqlite::memory:")) {
            SchemaInitializer.inicializar(conn);

            DatabaseMetaData meta = conn.getMetaData();
            List<String> tablas = new ArrayList<>();
            try (ResultSet rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    tablas.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }

            assertThat(tablas).contains("clientes", "creditos", "cuotas", "pagos");
        }
    }

    @Test
    @DisplayName("Debe verificar que la tabla cuotas contiene nativamente la columna en_mora")
    void testColumnaEnMoraNativaEnCuotas() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection("jdbc:sqlite::memory:")) {
            SchemaInitializer.inicializar(conn);

            boolean columnaEncontrada = false;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(cuotas);")) {
                while (rs.next()) {
                    String nombreCol = rs.getString("name");
                    if ("en_mora".equalsIgnoreCase(nombreCol)) {
                        columnaEncontrada = true;
                        assertThat(rs.getString("type")).isEqualTo("INTEGER");
                        assertThat(rs.getInt("notnull")).isEqualTo(1);
                        assertThat(rs.getString("dflt_value")).isEqualTo("0");
                    }
                }
            }

            assertThat(columnaEncontrada).as("La columna en_mora debe existir nativamente").isTrue();
        }
    }

    @Test
    @DisplayName("Debe ser idempotente y permitir ejecuciones repetidas sin error")
    void testIdempotenciaInicializacion() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection("jdbc:sqlite::memory:")) {
            SchemaInitializer.inicializar(conn);
            // Segunda ejecución consecutiva
            SchemaInitializer.inicializar(conn);

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT count(*) FROM clientes;")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("Debe bloquear inserciones que violen la integridad referencial (FOREIGN KEY)")
    void testBloqueoViolacionForeignKey() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection("jdbc:sqlite::memory:")) {
            SchemaInitializer.inicializar(conn);

            // Intentar insertar un crédito para un cliente inexistente (id = 9999)
            String sqlCreditoInvalido = """
                    INSERT INTO creditos (cliente_id, capital, tasa_mensual, plazo_meses, total_intereses,
                                          total_pagar, frecuencia_pago, fecha_inicio, estado)
                    VALUES (9999, '100000', '0.12', 1, '12000', '112000', 'SEMANAL', '2026-01-01', 'ACTIVO')
                    """;

            try (Statement stmt = conn.createStatement()) {
                assertThatThrownBy(() -> stmt.execute(sqlCreditoInvalido))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("FOREIGN KEY");
            }
        }
    }
}
