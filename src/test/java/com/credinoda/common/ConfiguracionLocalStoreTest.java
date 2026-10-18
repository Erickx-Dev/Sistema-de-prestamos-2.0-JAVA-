package com.credinoda.common;

import com.credinoda.domain.exception.ConfiguracionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de ConfiguracionLocalStore (RN-019)")
class ConfiguracionLocalStoreTest {

    @TempDir
    Path tempConfigDir;

    @BeforeEach
    void setUp() {
        System.setProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY, tempConfigDir.toString());
    }

    @AfterEach
    void tearDown() {
        System.clearProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY);
    }

    @Test
    @DisplayName("Debe resolver el directorio configurado mediante la propiedad del sistema -Dcredinoda.config.dir")
    void testResolverDirectorioConOverride() {
        Path resolved = ConfiguracionLocalStore.resolverDirectorioConfiguracion();
        assertThat(resolved).isEqualTo(tempConfigDir.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Debe resolver el directorio estándar del sistema operativo cuando no hay override")
    void testResolverDirectorioSinOverride() {
        System.clearProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY);
        Path standardDir = ConfiguracionLocalStore.resolverDirectorioConfiguracion();

        assertThat(standardDir).isNotNull();
        String pathStr = standardDir.toString().toLowerCase();
        assertThat(pathStr).satisfiesAnyOf(
                p -> assertThat(p).contains("credinoda"),
                p -> assertThat(p).contains("appdata"),
                p -> assertThat(p).contains(".config")
        );
    }

    @Test
    @DisplayName("Debe guardar y cargar la configuración de rutas de forma íntegra (roundtrip)")
    void testGuardarYCargarRutas(@TempDir Path tempWorkingDir) {
        Path dataDir = tempWorkingDir.resolve("data");
        Path backupsDir = tempWorkingDir.resolve("backups");
        RutasSistema rutas = RutasSistema.de(tempConfigDir, dataDir, backupsDir);

        assertThat(ConfiguracionLocalStore.existeConfiguracion()).isFalse();

        ConfiguracionLocalStore.guardarRutas(rutas);

        assertThat(ConfiguracionLocalStore.existeConfiguracion()).isTrue();

        Optional<RutasSistema> cargadasOpt = ConfiguracionLocalStore.cargarRutas();
        assertThat(cargadasOpt).isPresent();

        RutasSistema cargadas = cargadasOpt.get();
        assertThat(cargadas.configDir()).isEqualTo(tempConfigDir.toAbsolutePath().normalize());
        assertThat(cargadas.dataDir()).isEqualTo(dataDir.toAbsolutePath().normalize());
        assertThat(cargadas.databasePath()).isEqualTo(dataDir.resolve("creditos.db").toAbsolutePath().normalize());
        assertThat(cargadas.backupsDir()).isEqualTo(backupsDir.toAbsolutePath().normalize());
        assertThat(cargadas.comprobantesDir()).isEqualTo(dataDir.resolve("comprobantes").toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("Debe admitir rutas con espacios y caracteres especiales de manera robusta")
    void testRutasConEspaciosYCaracteresEspeciales(@TempDir Path tempWorkingDir) {
        Path dataDir = tempWorkingDir.resolve("Mis Datos de CrediNoda 2.0 (Pruebas)");
        Path backupsDir = tempWorkingDir.resolve("Google Drive - Respaldos Créditos");
        RutasSistema rutas = RutasSistema.de(tempConfigDir, dataDir, backupsDir);

        ConfiguracionLocalStore.guardarRutas(rutas);

        Optional<RutasSistema> cargadasOpt = ConfiguracionLocalStore.cargarRutas();
        assertThat(cargadasOpt).isPresent();

        RutasSistema cargadas = cargadasOpt.get();
        assertThat(cargadas.dataDir()).isEqualTo(dataDir.toAbsolutePath().normalize());
        assertThat(cargadas.backupsDir()).isEqualTo(backupsDir.toAbsolutePath().normalize());
    }

    @Test
    @DisplayName("La escritura atómica no debe dejar archivos temporales residuales .tmp")
    void testEscrituraAtomicaSinArchivosResiduales(@TempDir Path tempWorkingDir) throws IOException {
        Path dataDir = tempWorkingDir.resolve("data");
        Path backupsDir = tempWorkingDir.resolve("backups");
        RutasSistema rutas = RutasSistema.de(tempConfigDir, dataDir, backupsDir);

        ConfiguracionLocalStore.guardarRutas(rutas);

        Path tmpFile = tempConfigDir.resolve(ConfiguracionLocalStore.CONFIG_FILE_NAME + ".tmp");
        assertThat(Files.exists(tmpFile)).isFalse();
        assertThat(Files.isRegularFile(tempConfigDir.resolve(ConfiguracionLocalStore.CONFIG_FILE_NAME))).isTrue();
    }

    @Test
    @DisplayName("Debe lanzar ConfiguracionException si el archivo de configuración está corrupto o incompleto")
    void testArchivoConfiguracionIncompleto() throws IOException {
        Path configFile = ConfiguracionLocalStore.resolverArchivoConfiguracion();
        Files.createDirectories(configFile.getParent());
        // Escribir archivo de propiedades con clave faltante
        Files.writeString(configFile, "data.dir=/some/path\n");

        assertThatThrownBy(ConfiguracionLocalStore::cargarRutas)
                .isInstanceOf(ConfiguracionException.class)
                .hasMessageContaining("incompleto o corrupto");
    }

    @Test
    @DisplayName("Debe eliminar el archivo de configuración con borrarConfiguracion")
    void testBorrarConfiguracion(@TempDir Path tempWorkingDir) {
        Path dataDir = tempWorkingDir.resolve("data");
        Path backupsDir = tempWorkingDir.resolve("backups");
        RutasSistema rutas = RutasSistema.de(tempConfigDir, dataDir, backupsDir);

        ConfiguracionLocalStore.guardarRutas(rutas);
        assertThat(ConfiguracionLocalStore.existeConfiguracion()).isTrue();

        boolean borrado = ConfiguracionLocalStore.borrarConfiguracion();
        assertThat(borrado).isTrue();
        assertThat(ConfiguracionLocalStore.existeConfiguracion()).isFalse();

        // Segundo borrado devuelve false al no existir
        assertThat(ConfiguracionLocalStore.borrarConfiguracion()).isFalse();
    }
}
