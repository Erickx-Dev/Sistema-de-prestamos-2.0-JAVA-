package com.credinoda.ui.license;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.HardwareIdProvider;
import com.credinoda.common.LicenciaCryptoVerifier;
import com.credinoda.common.LicenciaKeyGeneratorTool;
import com.credinoda.domain.dto.ResultadoValidacionLicencia;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.LicenciaRepository;
import com.credinoda.service.LicenciaService;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.security.KeyPair;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias de ActivacionLicenciaDialog (CU-022 a CU-024)")
class ActivacionLicenciaDialogTest {

    @TempDir
    Path tempConfigDir;

    private Connection connection;
    private LicenciaRepository repository;
    private KeyPair keyPair;
    private LicenciaCryptoVerifier verifier;
    private HardwareIdProvider hardwareIdProvider;
    private String simulatedHardwareId;
    private LicenciaService licenciaService;

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        System.setProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY, tempConfigDir.toString());

        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        repository = new LicenciaRepository(connection);

        keyPair = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        verifier = new LicenciaCryptoVerifier(keyPair.getPublic());

        simulatedHardwareId = "CN20-A1B2-C3D4-E5F6";
        hardwareIdProvider = () -> simulatedHardwareId;

        licenciaService = new LicenciaService(repository, verifier, hardwareIdProvider, null, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        System.clearProperty(ConfiguracionLocalStore.PROPERTY_OVERRIDE_KEY);
    }

    private <T> T ejecutarEnFxThread(Callable<T> callable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> resultado = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                resultado.set(callable.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        boolean ok = latch.await(5, TimeUnit.SECONDS);
        assertThat(ok).as("La ejecución en Platform.runLater debe completarse").isTrue();
        if (error.get() != null) {
            if (error.get() instanceof Exception e) throw e;
            throw new RuntimeException(error.get());
        }
        return resultado.get();
    }

    private void ejecutarEnFxThread(Runnable runnable) throws Exception {
        ejecutarEnFxThread(() -> {
            runnable.run();
            return null;
        });
    }

    @Test
    @DisplayName("El diálogo renderiza el Hardware ID del equipo y el banner de estado inicial NO_REGISTRADA")
    void testRenderizadoInicialDialogo() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoInicial = licenciaService.validarEstadoActual();
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoInicial);

            assertThat(dialog.getHardwareIdField().getText()).isEqualTo(simulatedHardwareId);
            assertThat(dialog.getLblBannerTitulo().getText()).contains("No Registrada");
            assertThat(dialog.isLicenciaActivada()).isFalse();
            assertThat(dialog.getBtnReintentarHora().isVisible()).isFalse();
        });
    }

    @Test
    @DisplayName("Cuando el estado es RELOJ_MANIPULADO, el botón de reintentar hora se muestra visible")
    void testEstadoRelojManipuladoMuestraBotonReintento() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoReloj = ResultadoValidacionLicencia.relojManipulado(
                    simulatedHardwareId, "Hora mínima requerida: 2026-10-24T12:00:00Z UTC"
            );
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoReloj);

            assertThat(dialog.getLblBannerTitulo().getText()).contains("Reloj");
            assertThat(dialog.getBtnReintentarHora().isVisible()).isTrue();
            assertThat(dialog.getBtnReintentarHora().isManaged()).isTrue();
        });
    }

    @Test
    @DisplayName("Intento de activación con campo vacío muestra mensaje de advertencia")
    void testActivacionCampoVacio() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoInicial = licenciaService.validarEstadoActual();
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoInicial);

            dialog.getTxtLicenciaToken().setText("");
            dialog.procesarActivacion();

            assertThat(dialog.isLicenciaActivada()).isFalse();
            assertThat(dialog.getLblFeedback().isVisible()).isTrue();
            assertThat(dialog.getLblFeedback().getText()).contains("ingrese o cargue un código");
        });
    }

    @Test
    @DisplayName("Intento de activación con token inválido muestra feedback de error y no activa")
    void testActivacionTokenInvalido() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoInicial = licenciaService.validarEstadoActual();
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoInicial);

            dialog.getTxtLicenciaToken().setText("CREDINODA-LIC-V1:invalido.firma");
            dialog.procesarActivacion();

            assertThat(dialog.isLicenciaActivada()).isFalse();
            assertThat(dialog.getLblFeedback().isVisible()).isTrue();
        });
    }

    @Test
    @DisplayName("Activación con token Ed25519 válido activa exitosamente la licencia")
    void testActivacionExitosaConTokenValido() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoInicial = licenciaService.validarEstadoActual();
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoInicial);

            String tokenValido = LicenciaKeyGeneratorTool.firmarLicencia(
                    keyPair.getPrivate(),
                    "LIC-2026-GUI",
                    "Usuario Test JavaFX",
                    simulatedHardwareId,
                    LocalDate.now(),
                    LocalDate.now().plusYears(1),
                    "ANUAL"
            );

            dialog.getTxtLicenciaToken().setText(tokenValido);
            dialog.procesarActivacion();

            assertThat(dialog.isLicenciaActivada()).isTrue();
            assertThat(dialog.getLblBannerTitulo().getText()).contains("Activa");
            assertThat(dialog.getLblFeedback().getText()).contains("éxito");
        });
    }

    @Test
    @DisplayName("Copiar Hardware ID al portapapeles actualiza el texto del botón temporalmente")
    void testCopiarHardwareId() throws Exception {
        ejecutarEnFxThread(() -> {
            ResultadoValidacionLicencia estadoInicial = licenciaService.validarEstadoActual();
            ActivacionLicenciaDialog dialog = new ActivacionLicenciaDialog(new Stage(), licenciaService, estadoInicial);

            dialog.copiarHardwareIdAlPortapapeles();
            assertThat(dialog.getBtnCopiarId().getText()).isEqualTo("✓ Copiado");
        });
    }
}
