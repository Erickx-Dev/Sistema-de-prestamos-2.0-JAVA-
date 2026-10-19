package com.credinoda.ui.setup;

import com.credinoda.common.RutasSistema;
import com.credinoda.common.Validators;
import com.credinoda.domain.dto.ResultadoValidacionUbicacion;
import com.credinoda.domain.exception.ConfiguracionException;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.UsuarioRepository;
import com.credinoda.service.BCryptPasswordHasher;
import com.credinoda.service.ConfiguracionInicialService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Asistente de Configuración Inicial (Setup Wizard) para primer arranque (CU-016, RN-019, RN-020, RN-022).
 * Guía al usuario en la configuración de la carpeta local de base de datos, la carpeta de respaldos
 * (con recomendación explícita para Google Drive para Escritorio) y la creación de la cuenta del
 * Administrador Principal del sistema.
 */
public class SetupWizard {

    public static final double WIZARD_WIDTH = 680.0;
    public static final double WIZARD_HEIGHT = 560.0;

    private final Stage stage;
    private final ConfiguracionInicialService configService;
    private final Consumer<RutasSistema> onComplete;

    private int currentStep = 1;
    private Path selectedDataDir;
    private Path selectedBackupsDir;

    private StackPane stepContainer;
    private Button btnAnterior;
    private Button btnSiguiente;

    // Elementos Paso 2 (Datos)
    private TextField txtDataDir;
    private Label lblDataFeedback;

    // Elementos Paso 3 (Respaldos)
    private TextField txtBackupsDir;
    private Label lblBackupsFeedback;

    // Elementos Paso 4 (Administrador Inicial)
    private TextField txtAdminUsername;
    private TextField txtAdminNombre;
    private PasswordField txtAdminPassword;
    private PasswordField txtAdminPasswordConfirm;
    private Label lblAdminFeedback;

    // Elementos Paso 5 (Resumen)
    private Label lblResumenData;
    private Label lblResumenBackups;
    private Label lblResumenAdmin;
    private Label lblErrorGuardado;

    public SetupWizard(Stage stage, ConfiguracionInicialService configService, Consumer<RutasSistema> onComplete) {
        this.stage = Objects.requireNonNull(stage, "El Stage no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "ConfiguracionInicialService no puede ser nulo");
        this.onComplete = Objects.requireNonNull(onComplete, "El callback onComplete no puede ser nulo");

        initUI();
    }

    private void initUI() {
        this.stage.setTitle("CrediNoda 2.0 — Asistente de Configuración Inicial");
        this.stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("wizard-root");
        root.setPadding(new Insets(24));

        // Cabecera superior
        VBox headerBox = new VBox(6);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(0, 0, 16, 0));

        Label brandLabel = new Label("CrediNoda 2.0");
        brandLabel.getStyleClass().addAll("brand-text", "wizard-brand");

        Label subtitleLabel = new Label("Configuración de Almacenamiento, Respaldos y Seguridad");
        subtitleLabel.getStyleClass().add("section-subtitle");

        headerBox.getChildren().addAll(brandLabel, subtitleLabel);
        root.setTop(headerBox);

        // Contenedor central dinámico por pasos
        this.stepContainer = new StackPane();
        this.stepContainer.setAlignment(Pos.TOP_LEFT);
        root.setCenter(stepContainer);

        // Barra inferior de navegación
        HBox footerBox = new HBox(12);
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(16, 0, 0, 0));

        this.btnAnterior = new Button("← Anterior");
        this.btnAnterior.getStyleClass().add("btn-secondary");
        this.btnAnterior.setOnAction(e -> irPasoAnterior());

        this.btnSiguiente = new Button("Comenzar →");
        this.btnSiguiente.getStyleClass().add("btn-primary");
        this.btnSiguiente.setOnAction(e -> irPasoSiguiente());

        footerBox.getChildren().addAll(btnAnterior, btnSiguiente);
        root.setBottom(footerBox);

        Scene scene = new Scene(root, WIZARD_WIDTH, WIZARD_HEIGHT);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        loadApplicationIcon(stage);
        this.stage.setScene(scene);

        mostrarPaso(1);
    }

    private void mostrarPaso(int paso) {
        this.currentStep = paso;
        this.stepContainer.getChildren().clear();

        switch (paso) {
            case 1 -> {
                btnAnterior.setVisible(false);
                btnSiguiente.setText("Comenzar Configuración →");
                btnSiguiente.setDisable(false);
                stepContainer.getChildren().add(buildPaso1Bienvenida());
            }
            case 2 -> {
                btnAnterior.setVisible(true);
                btnSiguiente.setText("Siguiente →");
                stepContainer.getChildren().add(buildPaso2Datos());
                validarPaso2();
            }
            case 3 -> {
                btnAnterior.setVisible(true);
                btnSiguiente.setText("Siguiente →");
                stepContainer.getChildren().add(buildPaso3Respaldos());
                validarPaso3();
            }
            case 4 -> {
                btnAnterior.setVisible(true);
                btnSiguiente.setText("Siguiente →");
                stepContainer.getChildren().add(buildPaso4Admin());
                validarPaso4();
            }
            case 5 -> {
                btnAnterior.setVisible(true);
                btnSiguiente.setText("🚀 Guardar e Iniciar CrediNoda");
                btnSiguiente.setDisable(false);
                stepContainer.getChildren().add(buildPaso5Resumen());
            }
        }
    }

    private VBox buildPaso1Bienvenida() {
        VBox box = new VBox(16);
        box.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("👋 ¡Bienvenido a CrediNoda 2.0!");
        title.getStyleClass().add("dialog-header-title");

        Label desc = new Label(
                "CrediNoda 2.0 requiere definir dos ubicaciones operativas y configurar la cuenta del "
                        + "Administrador Principal para garantizar la integridad, seguridad y control de acceso:\n\n"
                        + "1. Carpeta de Datos Locales: Ubicación donde residirá la base de datos activa ('creditos.db'). "
                        + "Por seguridad y para evitar bloqueos en SQLite, debe ser una carpeta en el disco local de este equipo.\n\n"
                        + "2. Carpeta de Copias de Seguridad: Ubicación donde se generarán los respaldos (.db). "
                        + "Recomendamos elegir una carpeta sincronizada con Google Drive para Escritorio "
                        + "para salvaguardar su información de forma automática ante contingencias.\n\n"
                        + "3. Administrador del Sistema: Creará la cuenta con privilegios totales de administración.");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        box.getChildren().addAll(title, desc);
        return box;
    }

    private VBox buildPaso2Datos() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paso 1: Carpeta para Base de Datos Local");
        title.getStyleClass().add("dialog-header-title");

        Label desc = new Label(
                "Seleccione la carpeta donde se almacenará la base de datos activa de clientes, créditos y pagos. "
                        + "Si la carpeta ya contiene una base de datos válida de CrediNoda, será detectada y reutilizada.");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        HBox chooserBox = new HBox(10);
        chooserBox.setAlignment(Pos.CENTER_LEFT);

        this.txtDataDir = new TextField();
        this.txtDataDir.setPromptText("Seleccione o escriba la ruta de la carpeta local...");
        HBox.setHgrow(txtDataDir, Priority.ALWAYS);
        this.txtDataDir.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                this.selectedDataDir = Path.of(newVal.trim());
            } else {
                this.selectedDataDir = null;
            }
            validarPaso2();
        });

        Button btnExaminar = new Button("📁 Examinar...");
        btnExaminar.getStyleClass().add("btn-secondary");
        btnExaminar.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Seleccionar Carpeta para Datos Locales");
            if (selectedDataDir != null && selectedDataDir.toFile().isDirectory()) {
                dc.setInitialDirectory(selectedDataDir.toFile());
            }
            File chosen = dc.showDialog(stage);
            if (chosen != null) {
                txtDataDir.setText(chosen.getAbsolutePath());
            }
        });

        chooserBox.getChildren().addAll(txtDataDir, btnExaminar);

        this.lblDataFeedback = new Label();
        this.lblDataFeedback.setWrapText(true);

        box.getChildren().addAll(title, desc, chooserBox, lblDataFeedback);

        // Prellenar con ruta sugerida si está vacío
        if (selectedDataDir == null) {
            Path defaultData = Path.of(System.getProperty("user.home"), "CrediNoda_Datos");
            txtDataDir.setText(defaultData.toString());
        } else {
            txtDataDir.setText(selectedDataDir.toString());
        }

        return box;
    }

    private void validarPaso2() {
        if (selectedDataDir == null) {
            lblDataFeedback.setText("Debe seleccionar una carpeta para continuar.");
            lblDataFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        ResultadoValidacionUbicacion res = configService.validarDirectorioDatos(selectedDataDir);
        lblDataFeedback.setText(res.mensaje());

        if (!res.valida()) {
            lblDataFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
        } else if (res.esNube()) {
            lblDataFeedback.setStyle("-fx-text-fill: #b45309; -fx-font-weight: bold;");
            btnSiguiente.setDisable(false);
        } else if (res.baseExistenteValida()) {
            lblDataFeedback.setStyle("-fx-text-fill: #2563eb; -fx-font-weight: bold;");
            btnSiguiente.setDisable(false);
        } else {
            lblDataFeedback.setStyle("-fx-text-fill: #166534;");
            btnSiguiente.setDisable(false);
        }
    }

    private VBox buildPaso3Respaldos() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paso 2: Carpeta de Copias de Seguridad (Google Drive)");
        title.getStyleClass().add("dialog-header-title");

        Label desc = new Label(
                "Seleccione la carpeta donde se depositarán los respaldos en caliente (.db).\n\n"
                        + "💡 Recomendación Pro: Para contar con respaldos automáticos en la nube sin costo adicional, "
                        + "instale Google Drive para Escritorio y seleccione una carpeta dentro de su unidad virtual "
                        + "(por ejemplo, 'G:\\Mi unidad\\CrediNoda_Respaldos').");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        HBox chooserBox = new HBox(10);
        chooserBox.setAlignment(Pos.CENTER_LEFT);

        this.txtBackupsDir = new TextField();
        this.txtBackupsDir.setPromptText("Seleccione la carpeta de respaldos o Google Drive...");
        HBox.setHgrow(txtBackupsDir, Priority.ALWAYS);
        this.txtBackupsDir.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.isBlank()) {
                this.selectedBackupsDir = Path.of(newVal.trim());
            } else {
                this.selectedBackupsDir = null;
            }
            validarPaso3();
        });

        Button btnExaminar = new Button("📁 Examinar...");
        btnExaminar.getStyleClass().add("btn-secondary");
        btnExaminar.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Seleccionar Carpeta para Respaldos (Google Drive)");
            if (selectedBackupsDir != null && selectedBackupsDir.toFile().isDirectory()) {
                dc.setInitialDirectory(selectedBackupsDir.toFile());
            }
            File chosen = dc.showDialog(stage);
            if (chosen != null) {
                txtBackupsDir.setText(chosen.getAbsolutePath());
            }
        });

        chooserBox.getChildren().addAll(txtBackupsDir, btnExaminar);

        this.lblBackupsFeedback = new Label();
        this.lblBackupsFeedback.setWrapText(true);

        box.getChildren().addAll(title, desc, chooserBox, lblBackupsFeedback);

        // Prellenar con ruta sugerida si está vacío
        if (selectedBackupsDir == null) {
            Path defaultBackups = Path.of(System.getProperty("user.home"), "CrediNoda_Respaldos");
            txtBackupsDir.setText(defaultBackups.toString());
        } else {
            txtBackupsDir.setText(selectedBackupsDir.toString());
        }

        return box;
    }

    private void validarPaso3() {
        if (selectedBackupsDir == null) {
            lblBackupsFeedback.setText("Debe seleccionar una carpeta para continuar.");
            lblBackupsFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        ResultadoValidacionUbicacion res = configService.validarDirectorioBackups(selectedBackupsDir);
        lblBackupsFeedback.setText(res.mensaje());

        if (!res.valida()) {
            lblBackupsFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
        } else if (res.esNube()) {
            lblBackupsFeedback.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
            btnSiguiente.setDisable(false);
        } else {
            lblBackupsFeedback.setStyle("-fx-text-fill: #334155;");
            btnSiguiente.setDisable(false);
        }
    }

    private boolean tieneAdminExistente() {
        if (selectedDataDir == null) {
            return false;
        }
        Path backups = selectedBackupsDir != null ? selectedBackupsDir : selectedDataDir;
        return configService.tieneAdministradorConfigurado(RutasSistema.de(selectedDataDir, backups));
    }

    private VBox buildPaso4Admin() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paso 3: Cuenta de Administrador Principal");
        title.getStyleClass().add("dialog-header-title");

        if (tieneAdminExistente()) {
            Label desc = new Label(
                    "✓ Se detectó una base de datos existente con al menos una cuenta de Administrador activa.\n\n"
                            + "No es necesario crear una nueva cuenta en este asistente. Podrá iniciar sesión "
                            + "con las credenciales previamente configuradas en dicha base de datos.");
            desc.setWrapText(true);
            desc.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
            box.getChildren().addAll(title, desc);
            return box;
        }

        Label desc = new Label(
                "Configure la cuenta con privilegios de Administrador para gestionar el sistema, usuarios, "
                        + "respaldos y reportes.");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        VBox formBox = new VBox(10);

        // Username
        VBox userBox = new VBox(3);
        Label lblUser = new Label("Nombre de Usuario (Login) *");
        lblUser.getStyleClass().add("form-label");
        this.txtAdminUsername = new TextField(txtAdminUsername != null ? txtAdminUsername.getText() : "admin");
        this.txtAdminUsername.setPromptText("Ej: admin");
        this.txtAdminUsername.textProperty().addListener((o, ov, nv) -> validarPaso4());
        userBox.getChildren().addAll(lblUser, txtAdminUsername);

        // Nombre Completo
        VBox nombreBox = new VBox(3);
        Label lblNombre = new Label("Nombre Completo *");
        lblNombre.getStyleClass().add("form-label");
        this.txtAdminNombre = new TextField(txtAdminNombre != null ? txtAdminNombre.getText() : "Administrador Principal");
        this.txtAdminNombre.setPromptText("Ej: Erickson Sojo");
        this.txtAdminNombre.textProperty().addListener((o, ov, nv) -> validarPaso4());
        nombreBox.getChildren().addAll(lblNombre, txtAdminNombre);

        // Contraseña
        VBox passBox = new VBox(3);
        Label lblPass = new Label("Contraseña (mínimo 6 caracteres) *");
        lblPass.getStyleClass().add("form-label");
        this.txtAdminPassword = new PasswordField();
        this.txtAdminPassword.setPromptText("Contraseña segura");
        this.txtAdminPassword.textProperty().addListener((o, ov, nv) -> validarPaso4());
        passBox.getChildren().addAll(lblPass, txtAdminPassword);

        // Confirmar Contraseña
        VBox confirmBox = new VBox(3);
        Label lblConfirm = new Label("Confirmar Contraseña *");
        lblConfirm.getStyleClass().add("form-label");
        this.txtAdminPasswordConfirm = new PasswordField();
        this.txtAdminPasswordConfirm.setPromptText("Repita la contraseña");
        this.txtAdminPasswordConfirm.textProperty().addListener((o, ov, nv) -> validarPaso4());
        confirmBox.getChildren().addAll(lblConfirm, txtAdminPasswordConfirm);

        this.lblAdminFeedback = new Label();
        this.lblAdminFeedback.setWrapText(true);

        formBox.getChildren().addAll(userBox, nombreBox, passBox, confirmBox, lblAdminFeedback);
        box.getChildren().addAll(title, desc, formBox);
        return box;
    }

    private void validarPaso4() {
        if (tieneAdminExistente()) {
            btnSiguiente.setDisable(false);
            return;
        }

        if (txtAdminUsername == null || txtAdminNombre == null || txtAdminPassword == null || txtAdminPasswordConfirm == null) {
            btnSiguiente.setDisable(true);
            return;
        }

        String username = txtAdminUsername.getText();
        String nombre = txtAdminNombre.getText();
        String password = txtAdminPassword.getText();
        String confirm = txtAdminPasswordConfirm.getText();

        if (username == null || username.isBlank()) {
            lblAdminFeedback.setText("Ingrese el nombre de usuario.");
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        try {
            Validators.validarUsername(username);
        } catch (Exception ex) {
            lblAdminFeedback.setText(ex.getMessage());
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        if (nombre == null || nombre.isBlank()) {
            lblAdminFeedback.setText("Ingrese el nombre completo del administrador.");
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        if (password == null || password.isEmpty()) {
            lblAdminFeedback.setText("Ingrese una contraseña de al menos 6 caracteres.");
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        try {
            Validators.validarPassword(password);
        } catch (Exception ex) {
            lblAdminFeedback.setText(ex.getMessage());
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        if (!password.equals(confirm)) {
            lblAdminFeedback.setText("Las contraseñas no coinciden.");
            lblAdminFeedback.setStyle("-fx-text-fill: #b91c1c;");
            btnSiguiente.setDisable(true);
            return;
        }

        lblAdminFeedback.setText("✓ Credenciales válidas para el Administrador Principal.");
        lblAdminFeedback.setStyle("-fx-text-fill: #166534; -fx-font-weight: bold;");
        btnSiguiente.setDisable(false);
    }

    private VBox buildPaso5Resumen() {
        VBox box = new VBox(14);
        box.setAlignment(Pos.TOP_LEFT);

        Label title = new Label("Paso 4: Confirmación y Resumen");
        title.getStyleClass().add("dialog-header-title");

        Label desc = new Label(
                "Por favor confirme los parámetros antes de inicializar la aplicación. "
                        + "CrediNoda 2.0 guardará esta configuración e inicializará las estructuras seguras necesarias.");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        VBox summaryCard = new VBox(10);
        summaryCard.getStyleClass().add("card-subtle");
        summaryCard.setPadding(new Insets(14));

        Label lblTitleData = new Label("Base de Datos Activa:");
        lblTitleData.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        this.lblResumenData = new Label(selectedDataDir != null ? selectedDataDir.toString() : "-");
        lblResumenData.setWrapText(true);

        Label lblTitleBackups = new Label("Carpeta de Copias de Seguridad:");
        lblTitleBackups.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        this.lblResumenBackups = new Label(selectedBackupsDir != null ? selectedBackupsDir.toString() : "-");
        lblResumenBackups.setWrapText(true);

        Label lblTitleAdmin = new Label("Administrador Principal:");
        lblTitleAdmin.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        String adminText = tieneAdminExistente()
                ? "Cuenta de Administrador existente en base de datos"
                : (txtAdminUsername != null ? "@" + txtAdminUsername.getText().trim() + " (" + txtAdminNombre.getText().trim() + ")" : "admin");
        this.lblResumenAdmin = new Label(adminText);
        lblResumenAdmin.setWrapText(true);

        summaryCard.getChildren().addAll(lblTitleData, lblResumenData, lblTitleBackups, lblResumenBackups, lblTitleAdmin, lblResumenAdmin);

        this.lblErrorGuardado = new Label();
        this.lblErrorGuardado.setWrapText(true);
        this.lblErrorGuardado.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");

        box.getChildren().addAll(title, desc, summaryCard, lblErrorGuardado);
        return box;
    }

    private void irPasoAnterior() {
        if (currentStep > 1) {
            mostrarPaso(currentStep - 1);
        }
    }

    private void irPasoSiguiente() {
        if (currentStep < 5) {
            mostrarPaso(currentStep + 1);
        } else {
            ejecutarGuardadoYFinalizar();
        }
    }

    private void ejecutarGuardadoYFinalizar() {
        lblErrorGuardado.setText("");
        btnSiguiente.setDisable(true);
        btnAnterior.setDisable(true);

        try {
            RutasSistema rutas = configService.guardarConfiguracion(selectedDataDir, selectedBackupsDir);

            // Crear administrador inicial si la base de datos es nueva
            if (!tieneAdminExistente() && txtAdminUsername != null && txtAdminPassword != null) {
                try (Connection conn = DatabaseConnection.getConnection(rutas.databasePath())) {
                    SchemaInitializer.inicializar(conn);
                    UsuarioRepository repo = new UsuarioRepository(conn);
                    if (repo.contarAdministradoresActivos() == 0) {
                        BCryptPasswordHasher hasher = new BCryptPasswordHasher();
                        Usuario admin = new Usuario(
                                txtAdminUsername.getText().trim(),
                                hasher.hash(txtAdminPassword.getText()),
                                txtAdminNombre.getText().trim(),
                                Rol.ADMINISTRADOR
                        );
                        repo.crear(admin);
                    }
                } catch (Exception ex) {
                    lblErrorGuardado.setText("Error al inicializar administrador: " + ex.getMessage());
                    btnSiguiente.setDisable(false);
                    btnAnterior.setDisable(false);
                    return;
                }
            }

            onComplete.accept(rutas);
        } catch (ConfiguracionException e) {
            lblErrorGuardado.setText("Error al guardar la configuración: " + e.getMessage());
            btnSiguiente.setDisable(false);
            btnAnterior.setDisable(false);
        }
    }

    public void mostrar() {
        this.stage.show();
    }

    private void loadApplicationIcon(Stage stage) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/assets/icon.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {
        }
    }
}
