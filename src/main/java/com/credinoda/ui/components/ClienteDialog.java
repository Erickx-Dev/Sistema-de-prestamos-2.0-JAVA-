package com.credinoda.ui.components;

import com.credinoda.common.Validators;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.service.ClienteService;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;

/**
 * Diálogo modal para registrar un nuevo cliente prestatario o editar los datos de uno existente
 * (CU-001, CU-003, RN-001, RN-002).
 */
public class ClienteDialog {

    private final Stage stage;
    private final ClienteService clienteService;
    private final Cliente clienteExistente;

    private final TextField nombreField;
    private final TextField documentoField;
    private final TextField telefonoField;
    private final Label errorLabel;
    private final Button cancelButton;
    private final Button saveButton;
    private final BorderPane root;

    private Cliente savedCliente;

    public ClienteDialog(Window owner, ClienteService clienteService) {
        this(owner, clienteService, null);
    }

    public ClienteDialog(Window owner, ClienteService clienteService, Cliente clienteExistente) {
        this.clienteService = Objects.requireNonNull(clienteService, "ClienteService no puede ser nulo");
        this.clienteExistente = clienteExistente;
        boolean editMode = clienteExistente != null;

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle(editMode ? "Editar Cliente" : "Nuevo Cliente");
        this.stage.setResizable(false);
        this.stage.setMinWidth(480);
        this.stage.setMinHeight(360);

        this.root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(480);

        // 1. Cabecera
        HBox header = new HBox(10);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerTitle = new Label(editMode ? "Editar Datos del Cliente" : "Registrar Nuevo Cliente");
        headerTitle.getStyleClass().add("dialog-header-title");
        header.getChildren().add(headerTitle);

        // 2. Cuerpo del formulario
        VBox body = new VBox(12);
        body.getStyleClass().add("dialog-body");

        this.nombreField = new TextField();
        this.nombreField.setPromptText("Ej: Juan Pérez Gómez");

        this.documentoField = new TextField();
        this.documentoField.setPromptText("Ej: 1020304050");
        this.documentoField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("^[A-Za-z0-9-]*$")) {
                change.setText(change.getText().toUpperCase());
                return change;
            }
            return null;
        }));

        this.telefonoField = new TextField();
        this.telefonoField.setPromptText("Ej: 3001234567");
        this.telefonoField.setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            if (newText.isEmpty() || newText.matches("^[+]?[0-9\\s\\-()]*$")) {
                return change;
            }
            return null;
        }));

        if (editMode) {
            this.nombreField.setText(clienteExistente.getNombre());
            this.documentoField.setText(clienteExistente.getDocumento());
            this.telefonoField.setText(clienteExistente.getTelefono());
        }

        this.errorLabel = new Label();
        this.errorLabel.getStyleClass().add("badge-danger");
        this.errorLabel.setWrapText(true);
        this.errorLabel.setMaxWidth(Double.MAX_VALUE);
        this.errorLabel.setVisible(false);
        this.errorLabel.setManaged(false);

        body.getChildren().addAll(
                buildFormField("Nombre Completo *", nombreField),
                buildFormField("Documento de Identidad (Único) *", documentoField),
                buildFormField("Teléfono de Contacto *", telefonoField),
                errorLabel
        );

        // 3. Pie de botones
        HBox footer = new HBox(10);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        this.cancelButton = new Button("Cancelar");
        this.cancelButton.getStyleClass().add("btn-secondary");
        this.cancelButton.setCancelButton(true);
        this.cancelButton.setOnAction(e -> stage.close());

        this.saveButton = new Button(editMode ? "Guardar Cambios" : "Registrar Cliente");
        this.saveButton.getStyleClass().add("btn-primary");
        this.saveButton.setDefaultButton(true);
        this.saveButton.setOnAction(e -> handleSave());

        footer.getChildren().addAll(cancelButton, saveButton);

        root.setTop(header);
        root.setCenter(body);
        root.setBottom(footer);

        Scene scene = new Scene(root);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        this.stage.setScene(scene);
    }

    private VBox buildFormField(String labelText, TextField field) {
        VBox box = new VBox(4);
        Label label = new Label(labelText);
        label.getStyleClass().add("form-label");
        box.getChildren().addAll(label, field);
        return box;
    }

    public boolean handleSave() {
        hideError();

        String nombre = nombreField.getText();
        String documento = documentoField.getText();
        String telefono = telefonoField.getText();

        Validators.ResultadoValidacion valNombre = Validators.validarNombreCliente(nombre);
        if (!valNombre.valido()) {
            if (!nombreField.getStyleClass().contains("input-error")) {
                nombreField.getStyleClass().add("input-error");
            }
            nombreField.requestFocus();
            showError(valNombre.mensaje());
            return false;
        }

        Validators.ResultadoValidacion valDoc = Validators.validarDocumentoCliente(documento);
        if (!valDoc.valido()) {
            if (!documentoField.getStyleClass().contains("input-error")) {
                documentoField.getStyleClass().add("input-error");
            }
            documentoField.requestFocus();
            showError(valDoc.mensaje());
            return false;
        }

        Validators.ResultadoValidacion valTel = Validators.validarTelefonoCliente(telefono);
        if (!valTel.valido()) {
            if (!telefonoField.getStyleClass().contains("input-error")) {
                telefonoField.getStyleClass().add("input-error");
            }
            telefonoField.requestFocus();
            showError(valTel.mensaje());
            return false;
        }

        try {
            if (clienteExistente == null) {
                this.savedCliente = clienteService.crearCliente(nombre, telefono, documento);
            } else {
                this.savedCliente = clienteService.actualizarCliente(
                        clienteExistente.getId(), nombre, telefono, documento);
            }
            if (stage.isShowing()) {
                stage.close();
            }
            return true;
        } catch (CrediNodaException ex) {
            showError(ex.getMessage());
            return false;
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setManaged(true);
        errorLabel.setVisible(true);
        if (stage.isShowing()) {
            root.applyCss();
            root.layout();
            stage.sizeToScene();
        }
    }

    private void hideError() {
        boolean wasVisible = errorLabel.isVisible();
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        nombreField.getStyleClass().remove("input-error");
        documentoField.getStyleClass().remove("input-error");
        telefonoField.getStyleClass().remove("input-error");
        if (wasVisible && stage.isShowing()) {
            root.applyCss();
            root.layout();
            stage.sizeToScene();
        }
    }

    public Optional<Cliente> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(savedCliente);
    }

    public TextField getNombreField() {
        return nombreField;
    }

    public TextField getDocumentoField() {
        return documentoField;
    }

    public TextField getTelefonoField() {
        return telefonoField;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public Button getSaveButton() {
        return saveButton;
    }

    public Optional<Cliente> getSavedCliente() {
        return Optional.ofNullable(savedCliente);
    }
}
