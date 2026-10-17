package com.credinoda.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URL;

/**
 * Diálogo modal de confirmación reutilizable (ConfirmDialog).
 * Presenta la cabecera institucional de CrediNoda 2.0, un mensaje descriptivo y los botones
 * Cancelar (Esc) y Confirmar, deshabilitando este último al primer clic para evitar ejecuciones duplicadas.
 */
public class ConfirmDialog {

    private final Stage stage;
    private final Label titleLabel;
    private final Label messageLabel;
    private final Button cancelButton;
    private final Button confirmButton;
    private boolean confirmed = false;

    public ConfirmDialog(Window owner, String title, String message) {
        this(owner, title, message, "Confirmar", false);
    }

    public ConfirmDialog(Window owner, String title, String message, String confirmButtonText, boolean destructive) {
        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle(title != null ? title : "Confirmar Acción");
        this.stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(460);

        // 1. Cabecera institucional
        HBox header = new HBox(10);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        this.titleLabel = new Label(title != null ? title : "Confirmar Acción");
        this.titleLabel.getStyleClass().add("dialog-header-title");
        header.getChildren().add(titleLabel);

        // 2. Cuerpo del mensaje
        VBox body = new VBox(12);
        body.getStyleClass().add("dialog-body");
        body.setAlignment(Pos.CENTER_LEFT);

        this.messageLabel = new Label(message != null ? message : "");
        this.messageLabel.getStyleClass().add("dialog-message");
        this.messageLabel.setWrapText(true);
        body.getChildren().add(messageLabel);

        // 3. Pie de botones
        HBox footer = new HBox(10);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        this.cancelButton = new Button("Cancelar");
        this.cancelButton.getStyleClass().add("btn-secondary");
        this.cancelButton.setCancelButton(true);
        this.cancelButton.setOnAction(e -> {
            this.confirmed = false;
            this.stage.close();
        });

        this.confirmButton = new Button(confirmButtonText != null ? confirmButtonText : "Confirmar");
        this.confirmButton.getStyleClass().add(destructive ? "btn-danger" : "btn-primary");
        this.confirmButton.setDefaultButton(true);
        this.confirmButton.setOnAction(e -> {
            this.confirmButton.setDisable(true);
            this.confirmed = true;
            this.stage.close();
        });

        footer.getChildren().addAll(cancelButton, confirmButton);

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
                this.confirmed = false;
                this.stage.close();
            }
        });

        this.stage.setScene(scene);
    }

    /**
     * Muestra el diálogo de forma modal y bloqueante hasta que el usuario confirme o cancele.
     *
     * @return {@code true} si el usuario pulsó Confirmar; {@code false} en caso contrario.
     */
    public boolean showAndWait() {
        stage.showAndWait();
        return confirmed;
    }

    /**
     * Método utilitario estático para mostrar rápidamente una ventana de confirmación.
     */
    public static boolean show(Window owner, String title, String message) {
        return new ConfirmDialog(owner, title, message).showAndWait();
    }

    public Button getConfirmButton() {
        return confirmButton;
    }

    public Button getCancelButton() {
        return cancelButton;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}
