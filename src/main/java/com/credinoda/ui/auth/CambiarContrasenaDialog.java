package com.credinoda.ui.auth;

import com.credinoda.common.Validators;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.service.ContextoSeguridad;
import com.credinoda.service.UsuarioService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;

/**
 * Diálogo modal para el cambio seguro de contraseña de un usuario (CU-020, RN-022).
 */
public class CambiarContrasenaDialog {

    private final Stage stage;
    private final UsuarioService usuarioService;
    private final int usuarioId;
    private final boolean esPropio;

    private PasswordField txtPasswordActual;
    private PasswordField txtPasswordNuevo;
    private PasswordField txtPasswordConfirmacion;
    private Label lblError;
    private boolean completado = false;

    public CambiarContrasenaDialog(Window owner, UsuarioService usuarioService, int usuarioId) {
        this.usuarioService = Objects.requireNonNull(usuarioService, "UsuarioService no puede ser nulo");
        this.usuarioId = usuarioId;
        this.esPropio = ContextoSeguridad.getSesionActual().usuarioId() == usuarioId;

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        initUI();
    }

    private void initUI() {
        stage.setTitle("CrediNoda 2.0 — Modificar Contraseña");
        stage.setResizable(false);

        VBox root = new VBox(16);
        root.getStyleClass().add("dialog-root");
        root.setPadding(new Insets(24));

        Label titleLabel = new Label("Cambio de Contraseña");
        titleLabel.getStyleClass().add("card-title");

        Label subtitleLabel = new Label(esPropio
                ? "Actualice su contraseña de acceso personal"
                : "Restablecer contraseña para el usuario #" + usuarioId);
        subtitleLabel.getStyleClass().add("section-subtitle");

        VBox formBox = new VBox(12);

        if (esPropio) {
            VBox actualBox = new VBox(4);
            Label lblActual = new Label("Contraseña Actual *");
            lblActual.getStyleClass().add("form-label");
            this.txtPasswordActual = new PasswordField();
            this.txtPasswordActual.setPromptText("Ingrese su contraseña actual");
            actualBox.getChildren().addAll(lblActual, txtPasswordActual);
            formBox.getChildren().add(actualBox);
        }

        VBox nuevoBox = new VBox(4);
        Label lblNuevo = new Label("Nueva Contraseña (mínimo 6 caracteres) *");
        lblNuevo.getStyleClass().add("form-label");
        this.txtPasswordNuevo = new PasswordField();
        this.txtPasswordNuevo.setPromptText("Nueva contraseña");
        nuevoBox.getChildren().addAll(lblNuevo, txtPasswordNuevo);

        VBox confirmBox = new VBox(4);
        Label lblConfirm = new Label("Confirmar Nueva Contraseña *");
        lblConfirm.getStyleClass().add("form-label");
        this.txtPasswordConfirmacion = new PasswordField();
        this.txtPasswordConfirmacion.setPromptText("Repita la nueva contraseña");
        confirmBox.getChildren().addAll(lblConfirm, txtPasswordConfirmacion);

        this.lblError = new Label();
        this.lblError.getStyleClass().addAll("login-error-label", "text-danger");
        this.lblError.setWrapText(true);
        this.lblError.setVisible(false);
        this.lblError.setManaged(false);

        formBox.getChildren().addAll(nuevoBox, confirmBox, lblError);

        HBox buttonBox = new HBox(12);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.setPadding(new Insets(16, 0, 8, 0));

        Button btnCancelar = new Button("Cancelar");
        btnCancelar.getStyleClass().add("btn-secondary");
        btnCancelar.setOnAction(e -> stage.close());

        Button btnGuardar = new Button("Actualizar Contraseña");
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

        Scene scene = new Scene(root, 440, esPropio ? 440 : 380);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setScene(scene);
        stage.setMinWidth(440);
        stage.setMinHeight(esPropio ? 440 : 380);
        stage.sizeToScene();
    }

    private void handleGuardar() {
        String actual = txtPasswordActual != null ? txtPasswordActual.getText() : null;
        String nuevo = txtPasswordNuevo.getText();
        String confirm = txtPasswordConfirmacion.getText();

        if (esPropio && (actual == null || actual.isEmpty())) {
            mostrarError("Debe ingresar su contraseña actual.");
            return;
        }

        if (nuevo == null || nuevo.isEmpty()) {
            mostrarError("Debe ingresar la nueva contraseña.");
            return;
        }

        if (!Objects.equals(nuevo, confirm)) {
            mostrarError("La nueva contraseña y su confirmación no coinciden.");
            return;
        }

        Validators.ResultadoValidacion val = Validators.validarPassword(nuevo);
        if (!val.valido()) {
            mostrarError(val.mensaje());
            return;
        }

        try {
            usuarioService.cambiarPassword(usuarioId, actual, nuevo);
            this.completado = true;
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

    public boolean showAndWait() {
        stage.showAndWait();
        return completado;
    }

    public boolean mostrarYEsperar() {
        return showAndWait();
    }
}
