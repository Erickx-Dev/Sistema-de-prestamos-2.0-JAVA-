package com.credinoda.common;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Constantes financieras globales y resolución de rutas dinámicas del sistema.
 */
public final class AppConfig {

    private AppConfig() {
        // Clase de constantes, no instanciable
    }

    // Parámetros financieros por defecto
    public static final BigDecimal DEFAULT_MONTHLY_RATE = new BigDecimal("0.12");
    public static final int WEEKS_PER_MONTH = 4;
    public static final int FORTNIGHTS_PER_MONTH = 2;

    // Rutas operativas del sistema (relativas al directorio de ejecución)
    public static final Path BASE_DIR = Path.of(System.getProperty("user.dir"));
    public static final Path DATA_DIR = BASE_DIR.resolve("data");
    public static final Path DATABASE_PATH = DATA_DIR.resolve("creditos.db");
    public static final Path BACKUPS_DIR = BASE_DIR.resolve("backups");
    public static final Path COMPROBANTES_DIR = BASE_DIR.resolve("comprobantes");

    /**
     * Asegura que existan las carpetas operativas locales (data, backups, comprobantes).
     */
    public static void ensureDirectoriesExist() {
        try {
            Files.createDirectories(DATA_DIR);
            Files.createDirectories(BACKUPS_DIR);
            Files.createDirectories(COMPROBANTES_DIR);
        } catch (IOException e) {
            System.err.println("Advertencia al asegurar directorios del sistema: " + e.getMessage());
        }
    }
}
