package com.credinoda.ui;

import com.credinoda.common.RutasSistema;
import com.credinoda.domain.dto.EstadoInstalacion;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.persistence.repository.UsuarioRepository;
import com.credinoda.service.AutenticacionService;
import com.credinoda.service.BCryptPasswordHasher;
import com.credinoda.service.BackupService;
import com.credinoda.service.ClienteService;
import com.credinoda.service.ConfiguracionInicialService;
import com.credinoda.service.ContextoSeguridad;
import com.credinoda.service.CreditoService;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import com.credinoda.service.ReporteService;
import com.credinoda.service.UsuarioService;
import com.credinoda.ui.auth.LoginDialog;
import com.credinoda.ui.components.BackupDialog;
import com.credinoda.ui.components.ConfirmDialog;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.components.UsuarioDialog;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.NavigationBar;
import com.credinoda.ui.navigation.ViewRouter;
import com.credinoda.ui.setup.SetupWizard;
import com.credinoda.ui.setup.UbicacionNoDisponibleDialog;
import com.credinoda.ui.views.ClienteDetailView;
import com.credinoda.ui.views.ClientesView;
import com.credinoda.ui.views.CreditoDetailView;
import com.credinoda.ui.views.CreditoNewView;
import com.credinoda.ui.views.DashboardView;
import com.credinoda.ui.views.PagosView;
import com.credinoda.ui.views.ReportesView;
import com.credinoda.ui.views.UsuariosView;
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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Aplicación principal JavaFX de CrediNoda 2.0.
 * Orquesta la inicialización de la base de datos SQLite, los servicios de negocio,
 * la autenticación y control de accesos por roles (Fase 6.3), el monitor de cambio de día
 * para detección de mora (RN-008) y el contenedor principal {@code MainShell}.
 *
 * <p>Regla Estructural de Oro (Sección I.2): {@code MainShell} ({@link BorderPane}) está compuesto
 * exclusivamente por dos regiones permanentes: {@link NavigationBar} (Top, 56px) y
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
    private UsuarioRepository usuarioRepository;

    private ClienteService clienteService;
    private CreditoService creditoService;
    private PagoService pagoService;
    private ReporteService reporteService;
    private PdfService pdfService;
    private BackupService backupService;
    private BCryptPasswordHasher passwordHasher;
    private AutenticacionService autenticacionService;
    private UsuarioService usuarioService;

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

        ConfiguracionInicialService configService = new ConfiguracionInicialService();
        EstadoInstalacion estado = configService.determinarEstadoInstalacion();

        if (estado == EstadoInstalacion.NO_CONFIGURADO) {
            SetupWizard wizard = new SetupWizard(stage, configService, rutas -> {
                iniciarConRutas(stage, rutas);
            });
            wizard.mostrar();
        } else if (estado == EstadoInstalacion.UBICACION_NO_DISPONIBLE) {
            UbicacionNoDisponibleDialog dialog = new UbicacionNoDisponibleDialog(stage, configService, rutas -> {
                iniciarConRutas(stage, rutas);
            });
            dialog.mostrar();
        } else {
            RutasSistema rutas = configService.obtenerRutasConfiguradas()
                    .orElseThrow(() -> new IllegalStateException("Configuración no disponible"));
            iniciarConRutas(stage, rutas);
        }
    }

    /**
     * Inicializa los servicios con las rutas configuradas, valida autenticación y monta el MainShell de la aplicación.
     *
     * @param stage Escenario principal JavaFX.
     * @param rutas Rutas operativas validadas.
     */
    public void iniciarConRutas(Stage stage, RutasSistema rutas) {
        initializeBackend(rutas);

        // 1. Si no existe ningún administrador activo, solicitar creación inicial
        try {
            if (usuarioRepository.contarAdministradoresActivos() == 0) {
                UsuarioDialog adminDialog = new UsuarioDialog(stage, usuarioService);
                Optional<Usuario> adminOpt = adminDialog.mostrarYEsperar();
                if (adminOpt.isEmpty()) {
                    stage.close();
                    return;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error al verificar cuentas de administrador: " + e.getMessage(), e);
        }

        // 2. Diálogo modal de inicio de sesión
        LoginDialog loginDialog = new LoginDialog(stage, autenticacionService);
        Optional<SesionUsuario> sesionOpt = loginDialog.mostrarYEsperar();
        if (sesionOpt.isEmpty()) {
            stage.close();
            return;
        }

        ContextoSeguridad.setSesionActual(sesionOpt.get());
        montarYMostrarMainShell(stage);
    }

    /**
     * Monta y despliega la ventana principal MainShell.
     *
     * @param stage Escenario principal.
     */
    public void montarYMostrarMainShell(Stage stage) {
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

        // 6. Aplicar permisos según rol de la sesión actual y registrar logout
        navigationBar.aplicarPermisos(ContextoSeguridad.getSesionActual().rol());
        navigationBar.setOnLogoutRequested(() -> cerrarSesionYReautenticar(stage));

        // 7. Configurar escena, hoja de estilos e icono de ventana
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

        // 8. Navegar a la vista inicial (Dashboard / Inicio)
        viewRouter.navigate(NavAction.DASHBOARD);

        stage.show();
    }

    private void cerrarSesionYReautenticar(Stage stage) {
        String username = ContextoSeguridad.getSesionActual() != null ? ContextoSeguridad.getSesionActual().username() : "usuario";
        ConfirmDialog confirm = new ConfirmDialog(
                stage,
                "Cerrar Sesión",
                "¿Desea cerrar la sesión actual de @" + username + "? Será redirigido a la pantalla de inicio de sesión.",
                "Cerrar Sesión",
                false
        );
        if (confirm.showAndWait()) {
            ContextoSeguridad.limpiarSesion();
            if (viewRouter != null) {
                viewRouter.clearCache();
            }
            stage.hide();
            LoginDialog loginDialog = new LoginDialog(stage, autenticacionService);
            Optional<SesionUsuario> nuevaSesionOpt = loginDialog.mostrarYEsperar();
            if (nuevaSesionOpt.isPresent()) {
                SesionUsuario nuevaSesion = nuevaSesionOpt.get();
                ContextoSeguridad.setSesionActual(nuevaSesion);
                navigationBar.aplicarPermisos(nuevaSesion.rol());
                viewRouter.navigate(NavAction.DASHBOARD);
                stage.show();
            } else {
                stage.close();
            }
        }
    }

    /**
     * Inicializa persistencia SQLite y los servicios a partir de las rutas operativas inyectadas.
     *
     * @param rutas Instancia de RutasSistema con ubicaciones validadas.
     */
    public void initializeBackend(RutasSistema rutas) {
        try {
            rutas.asegurarDirectoriosExistentes();
            this.connection = DatabaseConnection.getConnection(rutas.databasePath());
            SchemaInitializer.inicializar(connection);

            this.clienteRepository = new ClienteRepository(connection);
            this.creditoRepository = new CreditoRepository(connection);
            this.pagoRepository = new PagoRepository(connection);
            this.usuarioRepository = new UsuarioRepository(connection);

            this.passwordHasher = new BCryptPasswordHasher();
            this.autenticacionService = new AutenticacionService(usuarioRepository, passwordHasher);
            this.usuarioService = new UsuarioService(usuarioRepository, passwordHasher);

            this.clienteService = new ClienteService(clienteRepository, creditoRepository);
            this.creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository);
            this.pagoService = new PagoService(connection, pagoRepository, creditoRepository, creditoService);
            this.reporteService = new ReporteService(creditoRepository, pagoRepository);
            this.pdfService = new PdfService(rutas);
            this.backupService = new BackupService(connection, rutas);

            // Sincronización idempotente de mora al iniciar la aplicación (CU-008, RN-008)
            this.creditoService.actualizarCuotasEnMora(LocalDate.now());
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Error crítico al inicializar el backend de CrediNoda 2.0", e);
        }
    }

    /**
     * Inicializa el backend utilizando las rutas configuradas en disco o un entorno temporal de contingencia.
     */
    public void initializeBackend() {
        ConfiguracionInicialService configService = new ConfiguracionInicialService();
        RutasSistema rutas = configService.obtenerRutasConfiguradas().orElseGet(() -> {
            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "credinoda_fallback");
            return RutasSistema.de(tempDir, tempDir.resolve("backups"));
        });
        initializeBackend(rutas);
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
     * Registra las vistas funcionales de los módulos principales y subvistas en el {@link ViewRouter}.
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

        viewRouter.registerView(NavAction.ROUTE_USUARIOS,
                () -> new UsuariosView(usuarioService, viewRouter, this::showNotification));

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

    public UsuarioRepository getUsuarioRepository() {
        return usuarioRepository;
    }

    public BCryptPasswordHasher getPasswordHasher() {
        return passwordHasher;
    }

    public AutenticacionService getAutenticacionService() {
        return autenticacionService;
    }

    public UsuarioService getUsuarioService() {
        return usuarioService;
    }
}
