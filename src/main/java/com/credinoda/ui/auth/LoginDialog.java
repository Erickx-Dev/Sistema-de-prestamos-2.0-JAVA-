package com.credinoda.ui.auth;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.UsuarioBloqueadoException;
import com.credinoda.service.AutenticacionService;
import com.credinoda.ui.components.StageIconHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

/**
 * Diálogo modal para el inicio de sesión de usuarios (CU-018, RN-022, RN-023).
 * Valida credenciales contra {@link AutenticacionService} con retroalimentación no reveladora.
 */
public class LoginDialog {

    public static final double DIALOG_WIDTH = 420.0;
    public static final double DIALOG_HEIGHT = 460.0;

    private final Stage stage;
    private final AutenticacionService authService;

    private TextField txtUsername;
    private PasswordField txtPassword;
    private Label lblError;
    private Button btnLogin;
    private Button btnSalir;

    private SesionUsuario sesionAutenticada;

    public LoginDialog(Window owner, AutenticacionService authService) {
        this.authService = Objects.requireNonNull(authService, "AutenticacionService no puede ser nulo");
        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        stage.setTitle("CrediNoda 2.0 — Inicio de Sesión");
        stage.setResizable(false);

        VBox root = new VBox(20);
        root.getStyleClass().add("login-root");
        root.setPadding(new Insets(32, 28, 28, 28));
        root.setAlignment(Pos.TOP_CENTER);

        // Bloque de Marca Superior
        VBox brandBox = new VBox(6);
        brandBox.setAlignment(Pos.CENTER);

        Node logoNode;
        ImageView logoView = StageIconHelper.createIconView(56);
        if (logoView != null) {
            logoNode = logoView;
        } else {
            Label logoBadge = new Label("C");
            logoBadge.getStyleClass().add("nav-logo-badge");
            logoBadge.setStyle("-fx-font-size: 24px; -fx-min-width: 48px; -fx-min-height: 48px;");
            logoNode = logoBadge;
        }

        Label titleLabel = new Label("CrediNoda 2.0");
        titleLabel.getStyleClass().add("brand-text");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Gestión de Créditos y Cobranzas");
        subtitleLabel.getStyleClass().add("section-subtitle");

        brandBox.getChildren().addAll(logoNode, titleLabel, subtitleLabel);

        // Formulario
        VBox formBox = new VBox(14);
        formBox.setAlignment(Pos.CENTER_LEFT);

        VBox userBox = new VBox(4);
        Label lblUser = new Label("Usuario *");
        lblUser.getStyleClass().add("form-label");
        this.txtUsername = new TextField();
        this.txtUsername.setPromptText("Ej: admin o operador");
        userBox.getChildren().addAll(lblUser, txtUsername);

        VBox passBox = new VBox(4);
        Label lblPass = new Label("Contraseña *");
        lblPass.getStyleClass().add("form-label");
        this.txtPassword = new PasswordField();
        this.txtPassword.setPromptText("Ingrese su contraseña");
        passBox.getChildren().addAll(lblPass, txtPassword);

        this.lblError = new Label();
        this.lblError.getStyleClass().addAll("login-error-label", "text-danger");
        this.lblError.setWrapText(true);
        this.lblError.setVisible(false);
        this.lblError.setManaged(false);

        formBox.getChildren().addAll(userBox, passBox, lblError);

        // Botones de acción
        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        this.btnSalir = new Button("Salir");
        this.btnSalir.getStyleClass().add("btn-secondary");
        this.btnSalir.setOnAction(e -> stage.close());

        this.btnLogin = new Button("Iniciar Sesión →");
        this.btnLogin.getStyleClass().add("btn-primary");
        this.btnLogin.setDefaultButton(true);
        this.btnLogin.setOnAction(e -> handleLogin());

        HBox.setHgrow(btnLogin, Priority.ALWAYS);
        btnLogin.setMaxWidth(Double.MAX_VALUE);

        buttonBox.getChildren().addAll(btnSalir, btnLogin);

        root.getChildren().addAll(brandBox, formBox, buttonBox);

        // Atajos de teclado
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        loadApplicationIcon(stage);
        stage.setScene(scene);
    }

    private void handleLogin() {
        String username = txtUsername.getText();
        String password = txtPassword.getText();

        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            mostrarError("Por favor, ingrese usuario y contraseña.");
            return;
        }

        try {
            this.sesionAutenticada = authService.autenticar(username, password);
            stage.close();
        } catch (UsuarioBloqueadoException ex) {
            mostrarError(ex.getMessage());
        } catch (AutenticacionException ex) {
            mostrarError(ex.getMessage());
            txtPassword.clear();
            txtPassword.requestFocus();
        } catch (Exception ex) {
            mostrarError("Error inesperado al intentar iniciar sesión: " + ex.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    private void loadApplicationIcon(Stage stage) {
        StageIconHelper.applyAppIcon(stage);
    }

    /**
     * Muestra el diálogo modal y espera a que el usuario se autentique o cierre la ventana.
     *
     * @return Optional con SesionUsuario si la autenticación fue exitosa.
     */
    public Optional<SesionUsuario> mostrarYEsperar() {
        stage.showAndWait();
        return Optional.ofNullable(sesionAutenticada);
    }
}
