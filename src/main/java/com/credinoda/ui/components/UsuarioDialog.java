package com.credinoda.ui.components;

import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.service.UsuarioService;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

/**
 * Diálogo modal para registrar un nuevo usuario o actualizar datos de un usuario existente (CU-019).
 */
public class UsuarioDialog {

    private final Stage stage;
    private final UsuarioService usuarioService;
    private final Usuario usuarioExistente;

    private TextField txtUsername;
    private TextField txtNombreCompleto;
    private ComboBox<Rol> comboRol;
    private PasswordField txtPassword;
    private PasswordField txtPasswordConfirm;
    private CheckBox chkActivo;
    private Label lblError;

    private Usuario usuarioGuardado;

    public UsuarioDialog(Window owner, UsuarioService usuarioService) {
        this(owner, usuarioService, null);
    }

    public UsuarioDialog(Window owner, UsuarioService usuarioService, Usuario usuarioExistente) {
        this.usuarioService = Objects.requireNonNull(usuarioService, "UsuarioService no puede ser nulo");
        this.usuarioExistente = usuarioExistente;

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        boolean esNuevo = (usuarioExistente == null);
        stage.setTitle(esNuevo ? "CrediNoda 2.0 — Registrar Nuevo Usuario" : "CrediNoda 2.0 — Modificar Usuario");
        stage.setResizable(false);

        VBox root = new VBox(16);
        root.getStyleClass().add("dialog-root");
        root.setPadding(new Insets(24));

        Label titleLabel = new Label(esNuevo ? "+ Nuevo Usuario" : "Editar Usuario: @" + usuarioExistente.getUsername());
        titleLabel.getStyleClass().add("card-title");

        Label subtitleLabel = new Label(esNuevo
                ? "Asigne credenciales y privilegios al nuevo operador"
                : "Modifique datos básicos, rol o estado de la cuenta");
        subtitleLabel.getStyleClass().add("section-subtitle");

        VBox formBox = new VBox(12);

        // Username
        VBox userBox = new VBox(4);
        Label lblUser = new Label("Nombre de Usuario (Login) *");
        lblUser.getStyleClass().add("form-label");
        this.txtUsername = new TextField();
        this.txtUsername.setPromptText("Ej: analista_cobros");
        if (!esNuevo) {
            this.txtUsername.setText(usuarioExistente.getUsername());
            this.txtUsername.setDisable(true);
        }
        userBox.getChildren().addAll(lblUser, txtUsername);

        // Nombre completo
        VBox nombreBox = new VBox(4);
        Label lblNombre = new Label("Nombre Completo *");
        lblNombre.getStyleClass().add("form-label");
        this.txtNombreCompleto = new TextField();
        this.txtNombreCompleto.setPromptText("Ej: Carlos Mendoza");
        if (!esNuevo) {
            this.txtNombreCompleto.setText(usuarioExistente.getNombreCompleto());
        }
        nombreBox.getChildren().addAll(lblNombre, txtNombreCompleto);

        // Rol
        VBox rolBox = new VBox(4);
        Label lblRol = new Label("Rol / Privilegios *");
        lblRol.getStyleClass().add("form-label");
        this.comboRol = new ComboBox<>(FXCollections.observableArrayList(Rol.values()));
        this.comboRol.setMaxWidth(Double.MAX_VALUE);
        this.comboRol.setValue(!esNuevo ? usuarioExistente.getRol() : Rol.OPERADOR);
        rolBox.getChildren().addAll(lblRol, comboRol);

        formBox.getChildren().addAll(userBox, nombreBox, rolBox);

        if (esNuevo) {
            VBox passBox = new VBox(4);
            Label lblPass = new Label("Contraseña Inicial (mínimo 6 caracteres) *");
            lblPass.getStyleClass().add("form-label");
            this.txtPassword = new PasswordField();
            this.txtPassword.setPromptText("Contraseña");
            passBox.getChildren().addAll(lblPass, txtPassword);

            VBox confirmBox = new VBox(4);
            Label lblConfirm = new Label("Confirmar Contraseña *");
            lblConfirm.getStyleClass().add("form-label");
            this.txtPasswordConfirm = new PasswordField();
            this.txtPasswordConfirm.setPromptText("Repita la contraseña");
            confirmBox.getChildren().addAll(lblConfirm, txtPasswordConfirm);

            formBox.getChildren().addAll(passBox, confirmBox);
        } else {
            this.chkActivo = new CheckBox("Cuenta activa (permite inicio de sesión)");
            this.chkActivo.setSelected(usuarioExistente.isActivo());
            formBox.getChildren().add(chkActivo);
        }

        this.lblError = new Label();
        this.lblError.getStyleClass().addAll("login-error-label", "text-danger");
        this.lblError.setWrapText(true);
        this.lblError.setVisible(false);
        this.lblError.setManaged(false);

        formBox.getChildren().add(lblError);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 0, 8, 0));

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(e -> stage.close());

        Button btnGuardar = new Button(esNuevo ? "Crear Usuario" : "Guardar Cambios");
        btnGuardar.getStyleClass().add("btn-primary");
        btnGuardar.setDefaultButton(true);
        btnGuardar.setOnAction(e -> handleGuardar());

        buttonBox.getChildren().addAll(btnCancelar, btnGuardar);
        root.getChildren().addAll(titleLabel, subtitleLabel, formBox, buttonBox);

        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        Scene scene = new Scene(root, 480, esNuevo ? 580 : 460);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(480);
        stage.setMinHeight(esNuevo ? 580 : 460);
        stage.sizeToScene();
    }

    private void handleGuardar() {
        String username = txtUsername.getText();
        String nombre = txtNombreCompleto.getText();
        Rol rol = comboRol.getValue();

        try {
            if (usuarioExistente == null) {
                String pass = txtPassword.getText();
                String confirm = txtPasswordConfirm.getText();

                if (!Objects.equals(pass, confirm)) {
                    mostrarError("La contraseña y su confirmación no coinciden.");
                    return;
                }

                this.usuarioGuardado = usuarioService.crearUsuario(username, pass, nombre, rol);
            } else {
                boolean activo = chkActivo.isSelected();
                this.usuarioGuardado = usuarioService.actualizarUsuario(usuarioExistente.getId(), nombre, rol, activo);
            }
            stage.close();
        } catch (CrediNodaException ex) {
            mostrarError(ex.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        lblError.setText(mensaje);
        lblError.setVisible(true);
        lblError.setManaged(true);
    }

    public Optional<Usuario> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(usuarioGuardado);
    }

    public Optional<Usuario> mostrarYEsperar() {
        return showAndWait();
    }
}
