package com.credinoda.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Record inmutable que encapsula las rutas operativas activas de CrediNoda 2.0 (RN-019).
 * Desacoplado al 100% de cualquier framework de interfaz gráfica.
 *
 * @param configDir       Directorio donde reside el archivo de configuración 'credinoda.properties'.
 * @param dataDir         Directorio contenedor de la base de datos SQLite activa.
 * @param databasePath    Ruta completa hacia el archivo 'creditos.db'.
 * @param backupsDir      Directorio donde se generan y buscan los respaldos .db (compatible con Google Drive para Escritorio).
 * @param comprobantesDir Directorio donde se generan los comprobantes de pago PDF.
 */
public record RutasSistema(
        Path configDir,
        Path dataDir,
        Path databasePath,
        Path backupsDir,
        Path comprobantesDir
) {

    public RutasSistema {
        Objects.requireNonNull(configDir, "El directorio de configuración no puede ser nulo");
        Objects.requireNonNull(dataDir, "El directorio de datos no puede ser nulo");
        Objects.requireNonNull(databasePath, "La ruta de la base de datos no puede ser nula");
        Objects.requireNonNull(backupsDir, "El directorio de respaldos no puede ser nulo");
        Objects.requireNonNull(comprobantesDir, "El directorio de comprobantes no puede ser nulo");
    }

    /**
     * Construye un conjunto de rutas operativas estándar a partir del directorio de datos y respaldos,
     * deduciendo 'creditos.db' y 'comprobantes/' dentro de la carpeta de datos, y resolviendo
     * el directorio de configuración del sistema operativo.
     *
     * @param dataDir    Directorio de datos seleccionado.
     * @param backupsDir Directorio de respaldos seleccionado.
     * @return Instancia inmutable de RutasSistema.
     */
    public static RutasSistema de(Path dataDir, Path backupsDir) {
        Path configDir = ConfiguracionLocalStore.resolverDirectorioConfiguracion();
        return de(configDir, dataDir, backupsDir);
    }

    /**
     * Construye un conjunto de rutas operativas especificando explícitamente el directorio de configuración.
     *
     * @param configDir  Directorio de configuración.
     * @param dataDir    Directorio de datos seleccionado.
     * @param backupsDir Directorio de respaldos seleccionado.
     * @return Instancia inmutable de RutasSistema.
     */
    public static RutasSistema de(Path configDir, Path dataDir, Path backupsDir) {
        Path databasePath = dataDir.resolve("creditos.db");
        Path comprobantesDir = dataDir.resolve("comprobantes");
        return new RutasSistema(configDir, dataDir, databasePath, backupsDir, comprobantesDir);
    }

    /**
     * Asegura de manera idempotente la existencia de todos los directorios operativos en disco.
     *
     * @throws IOException Si ocurre un fallo de I/O al crear alguno de los directorios.
     */
    public void asegurarDirectoriosExistentes() throws IOException {
        Files.createDirectories(configDir);
        Files.createDirectories(dataDir);
        Files.createDirectories(backupsDir);
        Files.createDirectories(comprobantesDir);
    }

    /**
     * Verifica si el archivo de base de datos activa existe físicamente como archivo regular.
     *
     * @return true si existe el archivo creditos.db, false de lo contrario.
     */
    public boolean existeBaseDatos() {
        return Files.isRegularFile(databasePath);
    }
}
