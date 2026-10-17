package com.credinoda.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Cabecera reutilizable para las vistas internas del área de contenido.
 * Muestra el título principal de la sección, un subtítulo opcional y un botón o nodo de acción derecho opcional.
 */
public class SectionHeader extends HBox {

    private final Label titleLabel;
    private final Label subtitleLabel;
    private final HBox actionContainer;
    private Button primaryActionButton;

    public SectionHeader(String title) {
        this(title, null, null, null);
    }

    public SectionHeader(String title, String subtitle) {
        this(title, subtitle, null, null);
    }

    public SectionHeader(String title, String subtitle, String actionButtonText, Runnable onAction) {
        getStyleClass().add("section-header");
        setAlignment(Pos.CENTER_LEFT);

        VBox textBox = new VBox(2);
        textBox.setAlignment(Pos.CENTER_LEFT);

        this.titleLabel = new Label(title != null ? title : "");
        this.titleLabel.getStyleClass().add("section-title");

        this.subtitleLabel = new Label();
        this.subtitleLabel.getStyleClass().add("section-subtitle");
        setSubtitle(subtitle);

        textBox.getChildren().addAll(titleLabel, subtitleLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.actionContainer = new HBox(8);
        this.actionContainer.setAlignment(Pos.CENTER_RIGHT);

        if (actionButtonText != null && !actionButtonText.isBlank()) {
            this.primaryActionButton = new Button(actionButtonText);
            this.primaryActionButton.getStyleClass().add("btn-primary");
            if (onAction != null) {
                this.primaryActionButton.setOnAction(e -> onAction.run());
            }
            this.actionContainer.getChildren().add(this.primaryActionButton);
        }

        getChildren().addAll(textBox, spacer, actionContainer);
    }

    public void setTitle(String title) {
        this.titleLabel.setText(title != null ? title : "");
    }

    public String getTitle() {
        return this.titleLabel.getText();
    }

    public void setSubtitle(String subtitle) {
        boolean hasSubtitle = subtitle != null && !subtitle.isBlank();
        this.subtitleLabel.setText(hasSubtitle ? subtitle : "");
        this.subtitleLabel.setVisible(hasSubtitle);
        this.subtitleLabel.setManaged(hasSubtitle);
    }

    public String getSubtitle() {
        return this.subtitleLabel.getText();
    }

    public Button getPrimaryActionButton() {
        return primaryActionButton;
    }

    public void setActionNode(Node node) {
        this.actionContainer.getChildren().clear();
        if (node != null) {
            this.actionContainer.getChildren().add(node);
        }
    }
}
