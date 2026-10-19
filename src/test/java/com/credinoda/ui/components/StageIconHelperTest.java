package com.credinoda.ui.components;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pruebas unitarias para la utilidad {@link StageIconHelper}.
 */
class StageIconHelperTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // El toolkit de JavaFX ya fue inicializado previamente en la JVM
        }
    }

    @Test
    @DisplayName("El recurso oficial Credinoda.png debe existir en classpath, cargarse sin error y medir 256x256")
    void debeCargarRecursoPngOficial() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(StageIconHelper.APP_ICON_RESOURCE)) {
            assertThat(is).as("El archivo /assets/Credinoda.png debe existir en el classpath").isNotNull();
            Image img = new Image(is);
            assertThat(img.isError()).as("La imagen no debe tener errores de decodificación").isFalse();
            assertThat(img.getWidth()).isEqualTo(256.0);
            assertThat(img.getHeight()).isEqualTo(256.0);
        }
    }

    @Test
    @DisplayName("El archivo Credinoda.ico debe existir en classpath para empaquetado de Windows")
    void debeExistirArchivoIco() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/assets/Credinoda.ico")) {
            assertThat(is).as("El archivo /assets/Credinoda.ico debe existir en el classpath").isNotNull();
            byte[] bytes = is.readAllBytes();
            assertThat(bytes.length).as("El archivo ICO debe contener datos").isGreaterThan(1000);
        }
    }

    @Test
    @DisplayName("applyAppIcon debe tolerar stage nulo sin arrojar excepciones")
    void debeTolerarStageNulo() {
        assertThatCode(() -> StageIconHelper.applyAppIcon(null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("applyAppIcon debe asociar el ícono oficial al Stage suministrado en el FX Application Thread")
    void debeAsociarIconoAStage() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Stage> stageRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                Stage stage = new Stage();
                StageIconHelper.applyAppIcon(stage);
                stageRef.set(stage);
            } finally {
                latch.countDown();
            }
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).as("La inicialización de Stage en Platform.runLater debe completarse en menos de 5 segundos").isTrue();

        Stage stage = stageRef.get();
        assertThat(stage).isNotNull();
        assertThat(stage.getIcons()).isNotEmpty();
        Image icono = stage.getIcons().getFirst();
        assertThat(icono.isError()).isFalse();
        assertThat(icono.getWidth()).isEqualTo(256.0);
        assertThat(icono.getHeight()).isEqualTo(256.0);
    }

    @Test
    @DisplayName("getAppIconImage debe retornar una instancia válida de Image de 256x256")
    void debeObtenerAppIconImageValido() {
        Image img = StageIconHelper.getAppIconImage();
        assertThat(img).isNotNull();
        assertThat(img.isError()).isFalse();
        assertThat(img.getWidth()).isEqualTo(256.0);
        assertThat(img.getHeight()).isEqualTo(256.0);
    }

    @Test
    @DisplayName("createIconView debe configurar un ImageView con dimensiones y propiedades correctas")
    void debeCrearIconViewConDimensionesYSuavizado() {
        javafx.scene.image.ImageView icon34 = StageIconHelper.createIconView(34);
        assertThat(icon34).isNotNull();
        assertThat(icon34.getFitWidth()).isEqualTo(34.0);
        assertThat(icon34.getFitHeight()).isEqualTo(34.0);
        assertThat(icon34.isPreserveRatio()).isTrue();
        assertThat(icon34.isSmooth()).isTrue();
        assertThat(icon34.getImage()).isNotNull();

        javafx.scene.image.ImageView icon56 = StageIconHelper.createIconView(56);
        assertThat(icon56).isNotNull();
        assertThat(icon56.getFitWidth()).isEqualTo(56.0);
        assertThat(icon56.getFitHeight()).isEqualTo(56.0);
    }
}
