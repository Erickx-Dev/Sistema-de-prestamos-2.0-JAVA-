package com.credinoda.service;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.RutasSistema;
import com.credinoda.common.Validators;
import com.credinoda.domain.dto.EstadoInstalacion;
import com.credinoda.domain.dto.ResultadoValidacionUbicacion;
import com.credinoda.domain.exception.BackupException;
import com.credinoda.domain.exception.ConfiguracionException;
import com.credinoda.persistence.SchemaInitializer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Servicio central para la evaluación del estado del sistema, validación de carpetas operativas,
 * aprovisionamiento inicial y recuperación ante bases de datos no disponibles (CU-016, CU-017, RN-019 a RN-021).
 * Completamente desacoplado de cualquier librería de interfaz gráfica (JavaFX).
 */
public class ConfiguracionInicialService {

    public ConfiguracionInicialService() {
        // Constructor estándar
    }

    /**
     * Determina el estado actual de la instalación del sistema (RN-019, RN-021).
     *
     * @return EstadoInstalacion correspondiente.
     */
    public EstadoInstalacion determinarEstadoInstalacion() {
        if (!ConfiguracionLocalStore.existeConfiguracion()) {
            return EstadoInstalacion.NO_CONFIGURADO;
        }

        Optional<RutasSistema> rutasOpt;
        try {
            rutasOpt = ConfiguracionLocalStore.cargarRutas();
        } catch (ConfiguracionException e) {
            return EstadoInstalacion.NO_CONFIGURADO;
        }

        if (rutasOpt.isEmpty()) {
            return EstadoInstalacion.NO_CONFIGURADO;
        }

        RutasSistema rutas = rutasOpt.get();
        // Si el archivo creditos.db configurado no existe físicamente, jamás se recrea en blanco (RN-021)
        if (!rutas.existeBaseDatos()) {
            return EstadoInstalacion.UBICACION_NO_DISPONIBLE;
        }

        return EstadoInstalacion.LISTO;
    }

    /**
     * Obtiene las rutas operativas activas desde la configuración persistida.
     *
     * @return Optional con RutasSistema, o Optional.empty() si no hay configuración válida.
     */
    public Optional<RutasSistema> obtenerRutasConfiguradas() {
        return ConfiguracionLocalStore.cargarRutas();
    }

    /**
     * Valida exhaustivamente una carpeta candidata para almacenar la base de datos local activa (CU-016, RN-020).
     *
     * @param path Ruta del directorio a auditar.
     * @return ResultadoValidacionUbicacion detallado con advertencias o errores.
     */
    public ResultadoValidacionUbicacion validarDirectorioDatos(Path path) {
        if (path == null) {
            return ResultadoValidacionUbicacion.invalida("Debe seleccionar una carpeta para los datos de la aplicación.");
        }

        Validators.ResultadoValidacion resPermisos = Validators.validarPermisosDirectorio(path);
        if (!resPermisos.valido()) {
            return ResultadoValidacionUbicacion.invalida(resPermisos.mensaje());
        }

        boolean esNube = Validators.esCarpetaEnLaNube(path);
        boolean baseExistenteValida = false;
        Path candidateDb = path.resolve("creditos.db");

        if (Files.isRegularFile(candidateDb)) {
            try {
                baseExistenteValida = BackupService.validarArchivoBackup(candidateDb);
            } catch (BackupException e) {
                return ResultadoValidacionUbicacion.invalida(
                        "Se encontró un archivo 'creditos.db' en la carpeta pero está dañado o no pertenece a CrediNoda: " + e.getMessage());
            }
        }

        String mensaje;
        if (esNube) {
            mensaje = "⚠️ Advertencia: La carpeta seleccionada se encuentra en un servicio en la nube (OneDrive/Google Drive). "
                    + "Se recomienda encarecidamente una carpeta local para prevenir daños y bloqueos en SQLite.";
        } else if (baseExistenteValida) {
            mensaje = "ℹ️ Se detectó una base de datos CrediNoda existente y válida. Se reutilizarán los datos existentes.";
        } else {
            mensaje = "Carpeta de datos local válida y con permisos de escritura.";
        }

        return ResultadoValidacionUbicacion.valida(esNube, baseExistenteValida, mensaje);
    }

    /**
     * Valida una carpeta candidata para almacenar los respaldos de seguridad (CU-016, RN-020).
     * Admite y recomienda expresamente carpetas sincronizadas con Google Drive para Escritorio.
     *
     * @param path Ruta del directorio a auditar.
     * @return ResultadoValidacionUbicacion indicando operatividad.
     */
    public ResultadoValidacionUbicacion validarDirectorioBackups(Path path) {
        if (path == null) {
            return ResultadoValidacionUbicacion.invalida("Debe seleccionar una carpeta para las copias de seguridad.");
        }

        Validators.ResultadoValidacion resPermisos = Validators.validarPermisosDirectorio(path);
        if (!resPermisos.valido()) {
            return ResultadoValidacionUbicacion.invalida(resPermisos.mensaje());
        }

        boolean esNube = Validators.esCarpetaEnLaNube(path);
        String mensaje = esNube
                ? "Carpeta de respaldos en la nube detectada (apta para sincronización automática con Google Drive)."
                : "Carpeta de respaldos local válida y con permisos de escritura.";

        return ResultadoValidacionUbicacion.valida(esNube, false, mensaje);
    }

    /**
     * Guarda la configuración inicial en disco, asegura los directorios operativos y aprovisiona
     * el esquema relacional limpio de SQLite si la base de datos es nueva (CU-016, RN-019, RN-020).
     *
     * @param dataDir    Carpeta contenedora de datos seleccionada.
     * @param backupsDir Carpeta contenedora de respaldos seleccionada.
     * @return Instancia inmutable de RutasSistema configurada.
     * @throws ConfiguracionException Si falla la validación, la creación de directorios o el esquema DDL.
     */
    public RutasSistema guardarConfiguracion(Path dataDir, Path backupsDir) {
        ResultadoValidacionUbicacion valDatos = validarDirectorioDatos(dataDir);
        if (!valDatos.valida()) {
            throw new ConfiguracionException("Directorio de datos inválido: " + valDatos.mensaje());
        }

        ResultadoValidacionUbicacion valBackups = validarDirectorioBackups(backupsDir);
        if (!valBackups.valida()) {
            throw new ConfiguracionException("Directorio de respaldos inválido: " + valBackups.mensaje());
        }

        RutasSistema rutas = RutasSistema.de(dataDir, backupsDir);

        try {
            rutas.asegurarDirectoriosExistentes();

            // Si la base de datos no existe previamente, aprovisionar el esquema relacional DDL limpio
            if (!rutas.existeBaseDatos()) {
                SchemaInitializer.inicializarBaseDatos(rutas.databasePath());
            }

            ConfiguracionLocalStore.guardarRutas(rutas);
            return rutas;

        } catch (IOException e) {
            throw new ConfiguracionException("Error de I/O al crear carpetas operativas del sistema: " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new ConfiguracionException("Error crítico al aprovisionar el esquema SQLite inicial: " + e.getMessage(), e);
        }
    }

    /**
     * Permite reubicar la base de datos cuando la ruta previamente configurada no está disponible (CU-017, RN-021).
     *
     * @param nuevoArchivoDb Ruta hacia el archivo 'creditos.db' seleccionado por el usuario.
     * @return Instancia de RutasSistema actualizada.
     * @throws ConfiguracionException Si el archivo no es válido o no pertenece a CrediNoda.
     */
    public RutasSistema reubicarBaseDatos(Path nuevoArchivoDb) {
        if (nuevoArchivoDb == null || !Files.isRegularFile(nuevoArchivoDb)) {
            throw new ConfiguracionException("Debe seleccionar un archivo de base de datos SQLite válido.");
        }

        try {
            BackupService.validarArchivoBackup(nuevoArchivoDb);
        } catch (BackupException e) {
            throw new ConfiguracionException("El archivo seleccionado no es una base de datos válida de CrediNoda: " + e.getMessage(), e);
        }

        Path nuevoDataDir = nuevoArchivoDb.getParent();
        if (nuevoDataDir == null) {
            throw new ConfiguracionException("No se pudo resolver el directorio contenedor de la base de datos seleccionada.");
        }

        // Recuperar backupsDir existente si estuviese configurado, o usar subcarpeta por defecto
        Path backupsDir = nuevoDataDir.resolve("backups");
        Optional<RutasSistema> prev = ConfiguracionLocalStore.cargarRutas();
        if (prev.isPresent()) {
            backupsDir = prev.get().backupsDir();
        }

        Path configDir = ConfiguracionLocalStore.resolverDirectorioConfiguracion();
        Path comprobantesDir = nuevoDataDir.resolve("comprobantes");
        RutasSistema nuevasRutas = new RutasSistema(configDir, nuevoDataDir, nuevoArchivoDb, backupsDir, comprobantesDir);

        try {
            nuevasRutas.asegurarDirectoriosExistentes();
            ConfiguracionLocalStore.guardarRutas(nuevasRutas);
            return nuevasRutas;
        } catch (IOException e) {
            throw new ConfiguracionException("Error al actualizar la configuración de reubicación: " + e.getMessage(), e);
        }
    }
}
