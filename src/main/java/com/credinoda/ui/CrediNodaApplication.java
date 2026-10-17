package com.credinoda.ui;

import com.credinoda.common.AppConfig;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.service.BackupService;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import com.credinoda.service.ReporteService;
import com.credinoda.ui.components.BackupDialog;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.NavigationBar;
import com.credinoda.ui.navigation.ViewRouter;
import com.credinoda.ui.views.ClienteDetailView;
import com.credinoda.ui.views.ClientesView;
import com.credinoda.ui.views.CreditoDetailView;
import com.credinoda.ui.views.CreditoNewView;
import com.credinoda.ui.views.DashboardView;
import com.credinoda.ui.views.PagosView;
import com.credinoda.ui.views.ReportesView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Aplicación principal JavaFX de CrediNoda 2.0.
 * Orquesta la inicialización de la base de datos SQLite, los 6 servicios de negocio,
 * el monitor de cambio de día para detección de mora (RN-008) y el contenedor principal {@code MainShell}.
 *
 * <p>Regla Estructural de Oro (Sección I.2): {@code MainShell} ({@link BorderPane}) está compuesto
 * exclusivamente por dos regiones permanentes: {@link NavigationBar} (Left, 220px) y
 * {@code ContentArea} (Center, 100% del espacio restante).</p>
 */
public class CrediNodaApplication extends Application {

    public static final double INITIAL_WIDTH = 1200.0;
    public static final double INITIAL_HEIGHT = 700.0;
    public static final double MIN_WIDTH = 1000.0;
    public static final double MIN_HEIGHT = 600.0;

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;

    private ClienteService clienteService;
    private CreditoService creditoService;
    private PagoService pagoService;
    private ReporteService reporteService;
    private PdfService pdfService;
    private BackupService backupService;

    private Stage primaryStage;
    private BorderPane mainShell;
    private NavigationBar navigationBar;
    private StackPane contentArea;
    private ToastNotification toastNotification;
    private ViewRouter viewRouter;
    private Timeline moraCheckTimeline;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // 1. Inicializar persistencia SQLite y servicios de negocio
        initializeBackend();

        // 2. Construir el MainShell (exclusivamente NavigationBar + ContentArea)
        this.mainShell = buildMainShell();

        // 3. Configurar ViewRouter y verificación automática de cambio de día (RN-008)
        this.viewRouter = new ViewRouter(contentArea, navigationBar);
        this.viewRouter.configureDayRolloverCheck(LocalDate::now, creditoService::actualizarCuotasEnMora);
        this.viewRouter.setBackupActionHandler(this::handleBackupAction);

        // 4. Registrar las vistas de los módulos en el enrutador
        registerDefaultModuleViews();

        // 5. Iniciar temporizador de verificación de cambio de día cada 60 segundos
        startDayRolloverMonitor();

        // 6. Configurar escena, hoja de estilos e icono de ventana
        Scene scene = new Scene(mainShell, INITIAL_WIDTH, INITIAL_HEIGHT);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        loadApplicationIcon(stage);

        stage.setTitle("CrediNoda 2.0 — Gestión de Créditos y Cobranzas");
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setScene(scene);

        // 7. Navegar a la vista inicial (Dashboard / Inicio)
        viewRouter.navigate(NavAction.DASHBOARD);

        stage.show();
    }

    /**
     * Inicializa carpetas operativas, esquema DDL limpio, conexión JDBC compartida y los 6 servicios.
     */
    protected void initializeBackend() {
        try {
            AppConfig.ensureDirectoriesExist();
            SchemaInitializer.inicializarBaseDatos();
            this.connection = DatabaseConnection.getConnection();

            this.clienteRepository = new ClienteRepository(connection);
            this.creditoRepository = new CreditoRepository(connection);
            this.pagoRepository = new PagoRepository(connection);

            this.clienteService = new ClienteService(clienteRepository, creditoRepository);
            this.creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository);
            this.pagoService = new PagoService(connection, pagoRepository, creditoRepository, creditoService);
            this.reporteService = new ReporteService(creditoRepository, pagoRepository);
            this.pdfService = new PdfService();
            this.backupService = new BackupService(connection);

            // Sincronización idempotente de mora al iniciar la aplicación (CU-008, RN-008)
            this.creditoService.actualizarCuotasEnMora(LocalDate.now());
        } catch (SQLException e) {
            throw new IllegalStateException("Error crítico al inicializar la base de datos SQLite de CrediNoda 2.0", e);
        }
    }

    /**
     * Construye el contenedor raíz MainShell respetando estrictamente la restricción de dos regiones:
     * Top = NavigationBar horizontal (56px), Center = ContentArea (100% restante con ToastNotification flotante).
     */
    public BorderPane buildMainShell() {
        BorderPane shell = new BorderPane();
        shell.getStyleClass().add("main-shell");

        this.navigationBar = new NavigationBar();
        this.contentArea = new StackPane();
        this.contentArea.getStyleClass().add("content-area");
        this.contentArea.setAlignment(Pos.TOP_LEFT);

        this.toastNotification = new ToastNotification();

        StackPane centerWrapper = new StackPane(contentArea, toastNotification);
        centerWrapper.getStyleClass().add("content-root");
        StackPane.setAlignment(toastNotification, Pos.TOP_CENTER);

        shell.setTop(navigationBar);
        shell.setCenter(centerWrapper);
        return shell;
    }

    /**
     * Registra las 7 vistas funcionales de los módulos principales y subvistas en el {@link ViewRouter}.
     */
    protected void registerDefaultModuleViews() {
        viewRouter.registerView(NavAction.DASHBOARD,
                () -> new DashboardView(creditoService, viewRouter, this::showNotification));

        viewRouter.registerView(NavAction.CLIENTES,
                () -> new ClientesView(clienteService, viewRouter, this::showNotification));

        CreditoNewView creditoNewView = new CreditoNewView(
                clienteService, creditoService, viewRouter, this::showNotification);
        viewRouter.registerView(NavAction.CREDITO_NEW, () -> creditoNewView);
        viewRouter.registerDynamicView(NavAction.CREDITO_NEW.getRouteKey(), param -> {
            if (param instanceof Integer clienteId) {
                creditoNewView.preselectCliente(clienteId);
            }
            return creditoNewView;
        });

        viewRouter.registerView(NavAction.PAGOS,
                () -> new PagosView(pagoService, pdfService, viewRouter, this::showNotification));

        viewRouter.registerView(NavAction.REPORTES,
                () -> new ReportesView(reporteService, this::showNotification));

        viewRouter.registerDynamicView(NavAction.ROUTE_CLIENTE_DETAIL, param -> {
            int clienteId = (param instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(param));
            return new ClienteDetailView(clienteId, clienteService, creditoService, viewRouter, this::showNotification);
        });

        viewRouter.registerDynamicView(NavAction.ROUTE_CREDITO_DETAIL, param -> {
            int creditoId = (param instanceof Number n) ? n.intValue() : Integer.parseInt(String.valueOf(param));
            return new CreditoDetailView(
                    creditoId, creditoService, clienteService, pagoService, pdfService, viewRouter, this::showNotification);
        });
    }

    protected void handleBackupAction() {
        BackupDialog dialog = new BackupDialog(
                primaryStage,
                backupService,
                creditoService,
                this::notifyDataChanged,
                this::showNotification
        );
        dialog.showAndWait();
    }

    private void startDayRolloverMonitor() {
        this.moraCheckTimeline = new Timeline(new KeyFrame(Duration.seconds(60), e -> {
            if (viewRouter != null && viewRouter.checkDayRollover()) {
                viewRouter.notifyDataChanged();
            }
        }));
        this.moraCheckTimeline.setCycleCount(Timeline.INDEFINITE);
        this.moraCheckTimeline.play();
    }

    private void loadApplicationIcon(Stage stage) {
        try (InputStream iconStream = getClass().getResourceAsStream("/assets/icon.png")) {
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {
            // Si el icono no pudiera cargarse en un entorno headless, continuar sin bloquear el arranque
        }
    }

    /**
     * Muestra una notificación flotante temporal no bloqueante en el área central.
     */
    public void showNotification(String message, ToastNotification.Type type) {
        if (toastNotification != null) {
            toastNotification.show(message, type);
        }
    }

    /**
     * Incrementa la versión de los datos en el enrutador para invalidar la caché de vistas.
     */
    public int notifyDataChanged() {
        return viewRouter != null ? viewRouter.notifyDataChanged() : 0;
    }

    @Override
    public void stop() {
        if (moraCheckTimeline != null) {
            moraCheckTimeline.stop();
        }
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                System.err.println("Advertencia al cerrar la conexión SQLite: " + e.getMessage());
            }
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public BorderPane getMainShell() {
        return mainShell;
    }

    public NavigationBar getNavigationBar() {
        return navigationBar;
    }

    public StackPane getContentArea() {
        return contentArea;
    }

    public ToastNotification getToastNotification() {
        return toastNotification;
    }

    public ViewRouter getViewRouter() {
        return viewRouter;
    }

    public ClienteService getClienteService() {
        return clienteService;
    }

    public CreditoService getCreditoService() {
        return creditoService;
    }

    public PagoService getPagoService() {
        return pagoService;
    }

    public ReporteService getReporteService() {
        return reporteService;
    }

    public PdfService getPdfService() {
        return pdfService;
    }

    public BackupService getBackupService() {
        return backupService;
    }
}
