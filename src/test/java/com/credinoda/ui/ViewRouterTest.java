package com.credinoda.ui;

import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.NavigationBar;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias para {@link ViewRouter}, {@link NavigationBar} y la estructura de {@code MainShell}.
 */
class ViewRouterTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // El toolkit de JavaFX ya fue inicializado previamente en la JVM
        }
    }

    /**
     * Vista de prueba que implementa {@link ViewRouter.RoutableView} para auditar invocaciones a onShow.
     */
    private static class TestRoutableView extends VBox implements ViewRouter.RoutableView {
        private int showCount = 0;
        private int lastVersionReceived = 0;

        TestRoutableView(String name) {
            getChildren().add(new Label(name));
        }

        @Override
        public void onShow(int currentDataVersion) {
            this.showCount++;
            this.lastVersionReceived = currentDataVersion;
        }
    }

    @Test
    @DisplayName("Navega entre las 5 vistas principales reemplazando limpiamente el nodo en ContentArea y sincronizando NavigationBar")
    void debeNavegarEntreVistasYSincronizarBarraDeNavegacion() {
        StackPane contentArea = new StackPane();
        NavigationBar navBar = new NavigationBar();
        ViewRouter router = new ViewRouter(contentArea, navBar);

        TestRoutableView dashboardView = new TestRoutableView("Dashboard");
        TestRoutableView clientesView = new TestRoutableView("Clientes");
        TestRoutableView creditoNewView = new TestRoutableView("NuevoCredito");
        TestRoutableView pagosView = new TestRoutableView("Pagos");
        TestRoutableView reportesView = new TestRoutableView("Reportes");

        router.registerView(NavAction.DASHBOARD, dashboardView);
        router.registerView(NavAction.CLIENTES, clientesView);
        router.registerView(NavAction.CREDITO_NEW, creditoNewView);
        router.registerView(NavAction.PAGOS, pagosView);
        router.registerView(NavAction.REPORTES, reportesView);

        // Navegar a Inicio
        router.navigate(NavAction.DASHBOARD);
        assertThat(contentArea.getChildren()).containsExactly(dashboardView);
        assertThat(navBar.getActiveAction()).isEqualTo(NavAction.DASHBOARD);
        assertThat(navBar.getButton(NavAction.DASHBOARD).getStyleClass()).contains(NavigationBar.ACTIVE_STYLE_CLASS);

        // Navegar a Clientes
        router.navigate(NavAction.CLIENTES);
        assertThat(contentArea.getChildren()).containsExactly(clientesView);
        assertThat(navBar.getActiveAction()).isEqualTo(NavAction.CLIENTES);
        assertThat(navBar.getButton(NavAction.CLIENTES).getStyleClass()).contains(NavigationBar.ACTIVE_STYLE_CLASS);
        assertThat(navBar.getButton(NavAction.DASHBOARD).getStyleClass()).doesNotContain(NavigationBar.ACTIVE_STYLE_CLASS);

        // Navegar a Reportes
        router.navigate(NavAction.REPORTES);
        assertThat(contentArea.getChildren()).containsExactly(reportesView);
        assertThat(navBar.getActiveAction()).isEqualTo(NavAction.REPORTES);
    }

    @Test
    @DisplayName("Controla la caché de vistas por dataVersion evitando recargas innecesarias y refrescando tras notifyDataChanged")
    void debeControlarCacheDeVistasPorDataVersion() {
        StackPane contentArea = new StackPane();
        NavigationBar navBar = new NavigationBar();
        ViewRouter router = new ViewRouter(contentArea, navBar);

        TestRoutableView dashboardView = new TestRoutableView("Dashboard");
        TestRoutableView clientesView = new TestRoutableView("Clientes");

        router.registerView(NavAction.DASHBOARD, dashboardView);
        router.registerView(NavAction.CLIENTES, clientesView);

        // 1. Primera visita a Dashboard -> ejecuta onShow(1)
        router.navigate(NavAction.DASHBOARD);
        assertThat(dashboardView.showCount).isEqualTo(1);
        assertThat(dashboardView.lastVersionReceived).isEqualTo(1);

        // 2. Alternar a Clientes y volver a Dashboard sin cambios de datos -> no vuelve a invocar onShow en Dashboard
        router.navigate(NavAction.CLIENTES);
        assertThat(clientesView.showCount).isEqualTo(1);

        router.navigate(NavAction.DASHBOARD);
        assertThat(dashboardView.showCount).isEqualTo(1);

        // 3. Notificar cambio de datos (dataVersion pasa a 2) -> refresca inmediatamente la vista activa (Dashboard)
        int newVersion = router.notifyDataChanged();
        assertThat(newVersion).isEqualTo(2);
        assertThat(dashboardView.showCount).isEqualTo(2);
        assertThat(dashboardView.lastVersionReceived).isEqualTo(2);

        // 4. Al volver a Clientes (que seguía en versión 1), ahora sí ejecuta onShow(2)
        router.navigate(NavAction.CLIENTES);
        assertThat(clientesView.showCount).isEqualTo(2);
        assertThat(clientesView.lastVersionReceived).isEqualTo(2);
    }

    @Test
    @DisplayName("Soporta subvistas dinámicas parametrizadas manteniendo resaltado el módulo padre en NavigationBar")
    void debeSoportarSubvistasContextualizadasConParametro() {
        StackPane contentArea = new StackPane();
        NavigationBar navBar = new NavigationBar();
        ViewRouter router = new ViewRouter(contentArea, navBar);

        AtomicReference<Object> receivedParam = new AtomicReference<>();
        router.registerDynamicView(NavAction.ROUTE_CLIENTE_DETAIL, param -> {
            receivedParam.set(param);
            return new TestRoutableView("ClienteDetail-" + param);
        });

        router.navigate(NavAction.ROUTE_CLIENTE_DETAIL, 42);

        assertThat(receivedParam.get()).isEqualTo(42);
        assertThat(router.getCurrentRouteKey()).isEqualTo(NavAction.ROUTE_CLIENTE_DETAIL);
        assertThat(router.getCurrentRouteParam()).isEqualTo(42);
        assertThat(navBar.getActiveAction()).isEqualTo(NavAction.CLIENTES);
    }

    @Test
    @DisplayName("La opción Copia de Seguridad dispara su manejador modal sin desmontar la vista activa en ContentArea")
    void debeEjecutarAccionDeBackupSinCambiarVistaActiva() {
        StackPane contentArea = new StackPane();
        NavigationBar navBar = new NavigationBar();
        ViewRouter router = new ViewRouter(contentArea, navBar);

        TestRoutableView pagosView = new TestRoutableView("Pagos");
        router.registerView(NavAction.PAGOS, pagosView);

        AtomicBoolean backupOpened = new AtomicBoolean(false);
        router.setBackupActionHandler(() -> backupOpened.set(true));

        router.navigate(NavAction.PAGOS);
        router.navigate(NavAction.BACKUP);

        assertThat(backupOpened.get()).isTrue();
        assertThat(contentArea.getChildren()).containsExactly(pagosView);
        assertThat(navBar.getActiveAction()).isEqualTo(NavAction.PAGOS);
    }

    @Test
    @DisplayName("RN-008: Detecta cambio de día al navegar, recalcula mora e invalida la caché incrementando dataVersion")
    void debeDetectarCambioDeDiaYActualizarMoraAlNavegar() {
        StackPane contentArea = new StackPane();
        NavigationBar navBar = new NavigationBar();
        ViewRouter router = new ViewRouter(contentArea, navBar);

        AtomicReference<LocalDate> simulatedClock = new AtomicReference<>(LocalDate.of(2026, 10, 16));
        AtomicInteger moraCheckCalls = new AtomicInteger(0);

        router.configureDayRolloverCheck(simulatedClock::get, date -> {
            moraCheckCalls.incrementAndGet();
            return 3;
        });

        TestRoutableView dashboardView = new TestRoutableView("Dashboard");
        TestRoutableView clientesView = new TestRoutableView("Clientes");
        router.registerView(NavAction.DASHBOARD, dashboardView);
        router.registerView(NavAction.CLIENTES, clientesView);

        router.navigate(NavAction.DASHBOARD);
        assertThat(moraCheckCalls.get()).isZero();
        assertThat(dashboardView.showCount).isEqualTo(1);

        // Avanzar reloj al día siguiente
        simulatedClock.set(LocalDate.of(2026, 10, 17));

        // Navegar a Clientes y regresar a Dashboard: debe haberse disparado la sincronización de mora y recargado Dashboard
        router.navigate(NavAction.CLIENTES);
        assertThat(moraCheckCalls.get()).isEqualTo(1);
        assertThat(router.getDataVersion()).isEqualTo(2);

        router.navigate(NavAction.DASHBOARD);
        assertThat(dashboardView.showCount).isEqualTo(2);
        assertThat(dashboardView.lastVersionReceived).isEqualTo(2);
    }

    @Test
    @DisplayName("Restricción I.2: MainShell contiene exclusivamente NavigationBar superior (Top) y ContentArea (Center)")
    void mainShellDebeContenerExclusivamenteNavigationBarYContentArea() {
        CrediNodaApplication app = new CrediNodaApplication();
        BorderPane mainShell = app.buildMainShell();

        assertThat(mainShell.getTop()).isInstanceOf(NavigationBar.class);
        assertThat(mainShell.getCenter()).isInstanceOf(StackPane.class);
        assertThat(mainShell.getLeft()).isNull();
        assertThat(mainShell.getRight()).isNull();
        assertThat(mainShell.getBottom()).isNull();
        assertThat(app.getNavigationBar().getPrefHeight()).isEqualTo(NavigationBar.FIXED_HEIGHT);
        assertThat(CrediNodaApplication.INITIAL_WIDTH).isEqualTo(1200.0);
        assertThat(CrediNodaApplication.INITIAL_HEIGHT).isEqualTo(700.0);
        assertThat(CrediNodaApplication.MIN_WIDTH).isEqualTo(1000.0);
        assertThat(CrediNodaApplication.MIN_HEIGHT).isEqualTo(600.0);
    }

    @Test
    @DisplayName("Lanza IllegalArgumentException si se intenta navegar a una ruta no registrada")
    void debeLanzarExcepcionEnRutaNoRegistrada() {
        ViewRouter router = new ViewRouter(new StackPane(), new NavigationBar());
        assertThatThrownBy(() -> router.navigate("ruta_inexistente"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ruta_inexistente");
    }
}
