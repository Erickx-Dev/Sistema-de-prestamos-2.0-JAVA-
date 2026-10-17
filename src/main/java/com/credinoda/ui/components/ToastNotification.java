package com.credinoda.ui.components;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Notificación flotante temporal no bloqueante (ToastNotification).
 * Se superpone en la parte superior central del {@code ContentArea} sin reservar espacio
 * fijo permanente y se oculta automáticamente transcurridos 4 segundos.
 */
public class ToastNotification extends HBox {

    public enum Type {
        SUCCESS("toast-success", "✓"),
        ERROR("toast-error", "✕"),
        WARNING("toast-warning", "⚠"),
        INFO("toast-info", "ℹ");

        private final String styleClass;
        private final String symbol;

        Type(String styleClass, String symbol) {
            this.styleClass = styleClass;
            this.symbol = symbol;
        }

        public String getStyleClass() {
            return styleClass;
        }

        public String getSymbol() {
            return symbol;
        }
    }

    public static final Duration DEFAULT_DURATION = Duration.seconds(4);

    private final Label iconLabel;
    private final Label messageLabel;
    private final PauseTransition autoHideTimer;
    private Type currentType = Type.INFO;

    public ToastNotification() {
        getStyleClass().add("toast-notification");
        setAlignment(Pos.CENTER_LEFT);
        setMaxWidth(620);
        setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(this, Pos.TOP_CENTER);
        StackPane.setMargin(this, new Insets(16, 24, 0, 24));

        this.iconLabel = new Label();
        this.messageLabel = new Label();
        this.messageLabel.setWrapText(true);

        getChildren().addAll(iconLabel, messageLabel);

        this.autoHideTimer = new PauseTransition(DEFAULT_DURATION);
        this.autoHideTimer.setOnFinished(e -> hide());

        setVisible(false);
        setManaged(false);
    }

    /**
     * Muestra la notificación flotante durante 4 segundos con el estilo del tipo indicado.
     */
    public void show(String message, Type type) {
        show(message, type, DEFAULT_DURATION);
    }

    /**
     * Muestra la notificación flotante durante la duración especificada.
     */
    public void show(String message, Type type, Duration duration) {
        Type effectiveType = type != null ? type : Type.INFO;
        this.currentType = effectiveType;

        for (Type t : Type.values()) {
            getStyleClass().remove(t.getStyleClass());
        }
        getStyleClass().add(effectiveType.getStyleClass());

        this.iconLabel.setText(effectiveType.getSymbol());
        this.messageLabel.setText(message != null ? message : "");

        setManaged(true);
        setVisible(true);
        toFront();

        autoHideTimer.stop();
        if (duration != null && duration.greaterThan(Duration.ZERO)) {
            autoHideTimer.setDuration(duration);
            autoHideTimer.playFromStart();
        }
    }

    public void showSuccess(String message) {
        show(message, Type.SUCCESS);
    }

    public void showError(String message) {
        show(message, Type.ERROR);
    }

    public void showWarning(String message) {
        show(message, Type.WARNING);
    }

    public void showInfo(String message) {
        show(message, Type.INFO);
    }

    public void hide() {
        autoHideTimer.stop();
        setVisible(false);
        setManaged(false);
    }

    public String getMessage() {
        return messageLabel.getText();
    }

    public Type getCurrentType() {
        return currentType;
    }
}
