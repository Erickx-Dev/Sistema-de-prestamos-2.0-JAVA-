package com.credinoda.common;

import com.credinoda.domain.exception.ConfiguracionException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * Gestor de persistencia multiplataforma de la configuración local en disco (RN-019).
 * Almacena las rutas en 'credinoda.properties' dentro de las carpetas estándar del sistema operativo
 * (Windows %APPDATA%, Linux ~/.config, macOS ~/Library/Application Support) y permite override
 * mediante la propiedad de JVM '-Dcredinoda.config.dir' para pruebas automatizadas aisladas.
 */
public final class ConfiguracionLocalStore {

    public static final String PROPERTY_OVERRIDE_KEY = "credinoda.config.dir";
    public static final String CONFIG_FILE_NAME = "credinoda.properties";

    public static final String KEY_DATA_DIR = "data.dir";
    public static final String KEY_DATABASE_PATH = "database.path";
    public static final String KEY_BACKUPS_DIR = "backups.dir";
    public static final String KEY_COMPROBANTES_DIR = "comprobantes.dir";

    private ConfiguracionLocalStore() {
        // Clase utilitaria estática, no instanciable
    }

    /**
     * Resuelve el directorio estándar donde reside la configuración según el sistema operativo activo,
     * respetando prioritariamente el override '-Dcredinoda.config.dir'.
     *
     * @return Path absoluto al directorio de configuración.
     */
    public static Path resolverDirectorioConfiguracion() {
        String override = System.getProperty(PROPERTY_OVERRIDE_KEY);
        if (override != null && !override.isBlank()) {
            return Path.of(override.trim()).toAbsolutePath().normalize();
        }

        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home", ".");

        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, "CrediNoda").toAbsolutePath().normalize();
            }
            return Path.of(userHome, ".credinoda").toAbsolutePath().normalize();
        } else if (os.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "CrediNoda").toAbsolutePath().normalize();
        } else {
            // Linux y otros sistemas Unix-like (especificación XDG Base Directory)
            String xdgConfig = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfig != null && !xdgConfig.isBlank()) {
                return Path.of(xdgConfig, "credinoda").toAbsolutePath().normalize();
            }
            return Path.of(userHome, ".config", "credinoda").toAbsolutePath().normalize();
        }
    }

    /**
     * Resuelve la ruta completa hacia el archivo 'credinoda.properties'.
     *
     * @return Path absoluto al archivo de propiedades.
     */
    public static Path resolverArchivoConfiguracion() {
        return resolverDirectorioConfiguracion().resolve(CONFIG_FILE_NAME);
    }

    /**
     * Determina si existe físicamente un archivo de configuración registrado.
     *
     * @return true si el archivo existe y es regular, false de lo contrario.
     */
    public static boolean existeConfiguracion() {
        return Files.isRegularFile(resolverArchivoConfiguracion());
    }

    /**
     * Carga las rutas del sistema desde el archivo de configuración.
     *
     * @return Optional con RutasSistema si existe y es válido, o Optional.empty() si no existe.
     * @throws ConfiguracionException Si el archivo existe pero está corrupto, ilegible o incompleto.
     */
    public static Optional<RutasSistema> cargarRutas() {
        Path archivo = resolverArchivoConfiguracion();
        if (!Files.isRegularFile(archivo)) {
            return Optional.empty();
        }

        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(archivo, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new ConfiguracionException("Error de lectura en el archivo de configuración: " + archivo, e);
        }

        String dataDirStr = props.getProperty(KEY_DATA_DIR);
        String databasePathStr = props.getProperty(KEY_DATABASE_PATH);
        String backupsDirStr = props.getProperty(KEY_BACKUPS_DIR);
        String comprobantesDirStr = props.getProperty(KEY_COMPROBANTES_DIR);

        if (dataDirStr == null || dataDirStr.isBlank() ||
                databasePathStr == null || databasePathStr.isBlank() ||
                backupsDirStr == null || backupsDirStr.isBlank() ||
                comprobantesDirStr == null || comprobantesDirStr.isBlank()) {
            throw new ConfiguracionException("El archivo de configuración está incompleto o corrupto: " + archivo);
        }

        Path configDir = archivo.getParent();
        Path dataDir = Path.of(dataDirStr).toAbsolutePath().normalize();
        Path databasePath = Path.of(databasePathStr).toAbsolutePath().normalize();
        Path backupsDir = Path.of(backupsDirStr).toAbsolutePath().normalize();
        Path comprobantesDir = Path.of(comprobantesDirStr).toAbsolutePath().normalize();

        return Optional.of(new RutasSistema(configDir, dataDir, databasePath, backupsDir, comprobantesDir));
    }

    /**
     * Guarda atómicamente la configuración en disco garantizando que nunca quede en un estado corrupto (RN-019).
     *
     * @param rutas Instancia con las rutas operativas a persistir.
     * @throws ConfiguracionException Si ocurre un error al persistir la configuración.
     */
    public static void guardarRutas(RutasSistema rutas) {
        Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo");

        Path configDir = rutas.configDir();
        Path archivoFinal = configDir.resolve(CONFIG_FILE_NAME);
        Path archivoTmp = configDir.resolve(CONFIG_FILE_NAME + ".tmp");

        try {
            Files.createDirectories(configDir);

            Properties props = new Properties();
            props.setProperty(KEY_DATA_DIR, rutas.dataDir().toAbsolutePath().normalize().toString());
            props.setProperty(KEY_DATABASE_PATH, rutas.databasePath().toAbsolutePath().normalize().toString());
            props.setProperty(KEY_BACKUPS_DIR, rutas.backupsDir().toAbsolutePath().normalize().toString());
            props.setProperty(KEY_COMPROBANTES_DIR, rutas.comprobantesDir().toAbsolutePath().normalize().toString());

            try (BufferedWriter writer = Files.newBufferedWriter(archivoTmp, StandardCharsets.UTF_8)) {
                props.store(writer, "CrediNoda 2.0 — Configuración Local del Sistema");
            }

            try {
                Files.move(archivoTmp, archivoFinal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(archivoTmp, archivoFinal, StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            try {
                Files.deleteIfExists(archivoTmp);
            } catch (IOException ignored) {
            }
            throw new ConfiguracionException("Error crítico al guardar la configuración en: " + archivoFinal, e);
        }
    }

    /**
     * Elimina el archivo de configuración del disco si existe.
     * Utilizado principalmente para limpieza de entornos y pruebas unitarias.
     *
     * @return true si el archivo fue eliminado, false si no existía.
     */
    public static boolean borrarConfiguracion() {
        Path archivo = resolverArchivoConfiguracion();
        try {
            return Files.deleteIfExists(archivo);
        } catch (IOException e) {
            throw new ConfiguracionException("No se pudo eliminar el archivo de configuración: " + archivo, e);
        }
    }
}
