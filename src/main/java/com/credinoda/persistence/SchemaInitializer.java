package com.credinoda.persistence;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Inicializador del esquema DDL limpio de la base de datos relacional SQLite de CrediNoda 2.0.
 * Aprovisiona de forma idempotente las 4 tablas definitivas e índices optimizados, con
 * la columna cuotas.en_mora nativa desde su creación.
 */
public final class SchemaInitializer {

    private static final String DDL_CLIENTES = """
            CREATE TABLE IF NOT EXISTS clientes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                telefono NUMERIC NOT NULL,
                documento NUMERIC NOT NULL UNIQUE,
                fecha_registro TEXT NOT NULL
            );
            """;

    private static final String DDL_CREDITOS = """
            CREATE TABLE IF NOT EXISTS creditos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                cliente_id INTEGER NOT NULL,
                capital NUMERIC NOT NULL,
                tasa_mensual NUMERIC NOT NULL,
                plazo_meses INTEGER NOT NULL,
                total_intereses NUMERIC NOT NULL,
                total_pagar NUMERIC NOT NULL,
                frecuencia_pago TEXT NOT NULL,
                fecha_inicio TEXT NOT NULL,
                estado TEXT NOT NULL DEFAULT 'ACTIVO',
                FOREIGN KEY (cliente_id) REFERENCES clientes(id)
            );
            """;

    private static final String DDL_CUOTAS = """
            CREATE TABLE IF NOT EXISTS cuotas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                credito_id INTEGER NOT NULL,
                numero_cuota INTEGER NOT NULL,
                fecha_vencimiento TEXT NOT NULL,
                monto NUMERIC NOT NULL,
                estado TEXT NOT NULL DEFAULT 'PENDIENTE',
                en_mora INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (credito_id) REFERENCES creditos(id)
            );
            """;

    private static final String DDL_PAGOS = """
            CREATE TABLE IF NOT EXISTS pagos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                credito_id INTEGER NOT NULL,
                cuota_id INTEGER NOT NULL,
                fecha_pago TEXT NOT NULL,
                monto NUMERIC NOT NULL,
                FOREIGN KEY (credito_id) REFERENCES creditos(id),
                FOREIGN KEY (cuota_id) REFERENCES cuotas(id)
            );
            """;

    private static final String[] DDL_INDICES = {
            "CREATE INDEX IF NOT EXISTS idx_creditos_cliente ON creditos(cliente_id);",
            "CREATE INDEX IF NOT EXISTS idx_cuotas_credito ON cuotas(credito_id);",
            "CREATE INDEX IF NOT EXISTS idx_cuotas_vencimiento ON cuotas(estado, fecha_vencimiento);",
            "CREATE INDEX IF NOT EXISTS idx_pagos_cuota ON pagos(cuota_id);",
            "CREATE INDEX IF NOT EXISTS idx_pagos_credito ON pagos(credito_id);"
    };

    private SchemaInitializer() {
        // Clase de utilidades DDL, no instanciable
    }

    /**
     * Aprovisiona las 4 tablas e índices en una conexión SQLite existente.
     *
     * @param conn Conexión activa a la base de datos.
     * @throws SQLException Si ocurre un error al ejecutar las sentencias DDL.
     */
    public static void inicializar(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(DDL_CLIENTES);
            stmt.execute(DDL_CREDITOS);
            stmt.execute(DDL_CUOTAS);
            stmt.execute(DDL_PAGOS);
            for (String indexSql : DDL_INDICES) {
                stmt.execute(indexSql);
            }
        }
    }


    /**
     * Inicializa una base de datos específica por ruta (útil para pruebas o respaldos).
     *
     * @param dbPath Ruta al archivo SQLite.
     * @throws SQLException Si ocurre un error DDL.
     */
    public static void inicializarBaseDatos(Path dbPath) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(dbPath)) {
            inicializar(conn);
        }
    }

    /**
     * Inicializa una base de datos a partir de una URL JDBC (útil para 'jdbc:sqlite::memory:').
     *
     * @param jdbcUrl URL JDBC.
     * @throws SQLException Si ocurre un error DDL.
     */
    public static void inicializarBaseDatos(String jdbcUrl) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection(jdbcUrl)) {
            inicializar(conn);
        }
    }
}
