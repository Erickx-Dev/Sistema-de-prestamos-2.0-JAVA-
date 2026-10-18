package com.credinoda.service;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.RutasSistema;
import com.credinoda.domain.dto.EstadoInstalacion;
import com.credinoda.domain.dto.ResultadoValidacionUbicacion;
import com.credinoda.domain.exception.ConfiguracionException;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de ConfiguracionInicialService (CU-016, CU-017, RN-019, RN-020, RN-021)")
class ConfiguracionInicialServiceTest {

    @TempDir
    Path tempConfigDir;

    @TempDir
    Path tempWorkingDir;

    private ConfiguracionInicialService service;

    @BeforeEach
    void setUp() {
        System.setProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY, tempConfigDir.toString());
        this.service = new ConfiguracionInicialService();
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY);
    }

    @Test
    @DisplayName("VP-015: Primer arranque sin configuración evalúa como NO_CONFIGURADO; tras guardar evalúa como LISTO")
    void testPrimerArranqueYGuardadoConfiguracion() throws SQLException {
        // 1. Estado inicial sin archivo de configuración
        EstadoInstalacion estadoInicial = service.determinarEstadoInstalacion();
        assertThat(estadoInicial).isEqualTo(EstadoInstalacion.NO_CONFIGURADO);
        assertThat(service.obtenerRutasConfiguradas()).isEmpty();

        // 2. Guardar configuración inicial
        Path dataDir = tempWorkingDir.resolve("datos_credinoda");
        Path backupsDir = tempWorkingDir.resolve("respaldos_credinoda");

        RutasSistema rutas = service.guardarConfiguracion(dataDir, backupsDir);

        assertThat(rutas).isNotNull();
        assertThat(rutas.existeBaseDatos()).isTrue();
        assertThat(Files.isDirectory(dataDir)).isTrue();
        assertThat(Files.isDirectory(backupsDir)).isTrue();
        assertThat(Files.isDirectory(rutas.comprobantesDir())).isTrue();

        // 3. Verificar que la base de datos recién creada contiene el esquema limpio DDL
        try (Connection conn = DatabaseConnection.getConnection(rutas.databasePath());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN ('clientes','creditos','cuotas','pagos')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(4);
        }

        // 4. Segundo arranque: evaluación inmediata como LISTO
        EstadoInstalacion segundoArranque = service.determinarEstadoInstalacion();
        assertThat(segundoArranque).isEqualTo(EstadoInstalacion.LISTO);
        assertThat(service.obtenerRutasConfiguradas()).isPresent();
    }

    @Test
    @DisplayName("VP-016: Si la base configurada no existe, evalúa como UBICACION_NO_DISPONIBLE y jamás recrea la BD en silencio")
    void testUbicacionNoDisponibleSinRecreacionSilenciosa() throws IOException {
        Path dataDir = tempWorkingDir.resolve("datos_temporales");
        Path backupsDir = tempWorkingDir.resolve("respaldos_temporales");

        RutasSistema rutas = service.guardarConfiguracion(dataDir, backupsDir);
        assertThat(rutas.existeBaseDatos()).isTrue();

        // Simular desconexión de disco o borrado intencional del archivo creditos.db
        Files.delete(rutas.databasePath());
        assertThat(rutas.existeBaseDatos()).isFalse();

        // Determinar estado de instalación
        EstadoInstalacion estado = service.determinarEstadoInstalacion();
        assertThat(estado).isEqualTo(EstadoInstalacion.UBICACION_NO_DISPONIBLE);

        // Invariante RN-021: la comprobación NUNCA debe haber recreado el archivo creditos.db
        assertThat(Files.exists(rutas.databasePath()))
                .as("El sistema jamás debe recrear automáticamente una base de datos ausente en silencio")
                .isFalse();
    }

    @Test
    @DisplayName("VP-017: Validador de directorio de datos detecta carpetas en la nube y reutiliza BD existente válida")
    void testValidacionDirectorioDatosYNube() throws SQLException, IOException {
        // 1. Directorio normal limpio
        Path dirNormal = tempWorkingDir.resolve("mi_carpeta_local");
        ResultadoValidacionUbicacion resNormal = service.validarDirectorioDatos(dirNormal);
        assertThat(resNormal.valida()).isTrue();
        assertThat(resNormal.esNube()).isFalse();
        assertThat(resNormal.baseExistenteValida()).isFalse();

        // 2. Directorio simulado en la nube (ej. Google Drive)
        Path dirNube = tempWorkingDir.resolve("Google Drive").resolve("CrediNoda");
        ResultadoValidacionUbicacion resNube = service.validarDirectorioDatos(dirNube);
        assertThat(resNube.valida()).isTrue();
        assertThat(resNube.esNube()).isTrue();
        assertThat(resNube.mensaje()).contains("en la nube");

        // 3. Directorio con base de datos existente válida
        Path dirConDb = tempWorkingDir.resolve("datos_preexistentes");
        Files.createDirectories(dirConDb);
        Path dbFile = dirConDb.resolve("creditos.db");
        SchemaInitializer.inicializarBaseDatos(dbFile);

        ResultadoValidacionUbicacion resConDb = service.validarDirectorioDatos(dirConDb);
        assertThat(resConDb.valida()).isTrue();
        assertThat(resConDb.baseExistenteValida()).isTrue();
        assertThat(resConDb.mensaje()).contains("detectó una base de datos CrediNoda existente");

        // 4. Directorio con archivo creditos.db dañado o corrupto
        Path dirCorrupto = tempWorkingDir.resolve("datos_corruptos");
        Files.createDirectories(dirCorrupto);
        Files.writeString(dirCorrupto.resolve("creditos.db"), "archivo de texto invalido");

        ResultadoValidacionUbicacion resCorrupto = service.validarDirectorioDatos(dirCorrupto);
        assertThat(resCorrupto.valida()).isFalse();
        assertThat(resCorrupto.mensaje()).contains("dañado");
    }

    @Test
    @DisplayName("Validación de directorio de respaldos permite y alienta carpetas en la nube")
    void testValidacionDirectorioBackups() {
        Path dirNubeBackups = tempWorkingDir.resolve("Google Drive").resolve("CrediNoda_Backups");
        ResultadoValidacionUbicacion res = service.validarDirectorioBackups(dirNubeBackups);

        assertThat(res.valida()).isTrue();
        assertThat(res.esNube()).isTrue();
        assertThat(res.mensaje()).contains("Google Drive");
    }

    @Test
    @DisplayName("Reubicación de base de datos actualiza configuración y permite recuperación exitosa (CU-017)")
    void testReubicacionBaseDatos() throws SQLException {
        Path dataDir1 = tempWorkingDir.resolve("data_origen");
        Path backupsDir = tempWorkingDir.resolve("backups_comunes");
        service.guardarConfiguracion(dataDir1, backupsDir);

        // Crear una base de datos válida en otra ubicación (data_destino)
        Path dataDir2 = tempWorkingDir.resolve("data_destino");
        Path nuevoDb = dataDir2.resolve("creditos.db");
        SchemaInitializer.inicializarBaseDatos(nuevoDb);

        // Ejecutar reubicación hacia nuevoDb
        RutasSistema rutasReubicadas = service.reubicarBaseDatos(nuevoDb);

        assertThat(rutasReubicadas.databasePath()).isEqualTo(nuevoDb.toAbsolutePath().normalize());
        assertThat(rutasReubicadas.dataDir()).isEqualTo(dataDir2.toAbsolutePath().normalize());
        assertThat(service.determinarEstadoInstalacion()).isEqualTo(EstadoInstalacion.LISTO);

        // Intentar reubicar hacia un archivo no válido debe fallar
        Path archivoInvalido = tempWorkingDir.resolve("invalido.txt");
        assertThatThrownBy(() -> service.reubicarBaseDatos(archivoInvalido))
                .isInstanceOf(ConfiguracionException.class);
    }
}
