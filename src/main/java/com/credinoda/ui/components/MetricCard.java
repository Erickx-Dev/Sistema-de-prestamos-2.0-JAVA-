package com.credinoda.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Locale;

/**
 * Tarjeta reutilizable de indicador clave de rendimiento (KPI / MetricCard).
 * Diseñada según la especificación de CrediNoda 2.0 Desktop (Stitch):
 * - Borde superior de acento semántico de 3px (.metric-accent-*)
 * - Fila superior con etiqueta de categoría en mayúsculas e icono opcional
 * - Fila central con cifra principal destacada (.metric-value)
 * - Fila inferior opcional de subtítulo o variación (.metric-subtitle)
 */
public class MetricCard extends VBox {

    public enum AccentVariant {
        PRIMARY("metric-accent-primary"),
        SUCCESS("metric-accent-success"),
        WARNING("metric-accent-warning"),
        DANGER("metric-accent-danger"),
        INFO("metric-accent-info");

        private final String styleClass;

        AccentVariant(String styleClass) {
            this.styleClass = styleClass;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    private final Label titleLabel;
    private final Label iconLabel;
    private final Label valueLabel;
    private final Label subtitleLabel;
    private AccentVariant accentVariant;

    public MetricCard(String title, String initialValue) {
        this(title, initialValue, null, AccentVariant.PRIMARY, null);
    }

    public MetricCard(String title, String initialValue, String icon) {
        this(title, initialValue, icon, AccentVariant.PRIMARY, null);
    }

    public MetricCard(String title, String initialValue, String icon, AccentVariant accentVariant) {
        this(title, initialValue, icon, accentVariant, null);
    }

    public MetricCard(String title, String initialValue, String icon, AccentVariant accentVariant, String subtitle) {
        getStyleClass().add("metric-card");

        HBox headerRow = new HBox(8);
        headerRow.getStyleClass().add("metric-header-row");
        headerRow.setAlignment(Pos.CENTER_LEFT);

        this.titleLabel = new Label();
        this.titleLabel.getStyleClass().add("metric-label");
        setTitle(title);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        this.iconLabel = new Label();
        this.iconLabel.getStyleClass().add("metric-icon");
        setIcon(icon);

        headerRow.getChildren().addAll(titleLabel, spacer, iconLabel);

        this.valueLabel = new Label();
        this.valueLabel.getStyleClass().add("metric-value");
        setValue(initialValue);

        this.subtitleLabel = new Label();
        this.subtitleLabel.getStyleClass().add("metric-subtitle");
        setSubtitle(subtitle);

        setAccentVariant(accentVariant != null ? accentVariant : AccentVariant.PRIMARY);

        getChildren().addAll(headerRow, valueLabel, subtitleLabel);
    }

    public void setTitle(String title) {
        String normalized = title != null ? title.strip().toUpperCase(Locale.ROOT) : "";
        this.titleLabel.setText(normalized);
    }

    public String getTitle() {
        return this.titleLabel.getText();
    }

    public void setValue(String value) {
        this.valueLabel.setText(value != null ? value : "");
    }

    public String getValue() {
        return this.valueLabel.getText();
    }

    public void setIcon(String icon) {
        boolean hasIcon = icon != null && !icon.isBlank();
        this.iconLabel.setText(hasIcon ? icon : "");
        this.iconLabel.setVisible(hasIcon);
        this.iconLabel.setManaged(hasIcon);
    }

    public String getIcon() {
        return this.iconLabel.getText();
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

    public void setAccentVariant(AccentVariant variant) {
        for (AccentVariant v : AccentVariant.values()) {
            getStyleClass().remove(v.getStyleClass());
        }
        this.accentVariant = variant != null ? variant : AccentVariant.PRIMARY;
        getStyleClass().add(this.accentVariant.getStyleClass());
    }

    public AccentVariant getAccentVariant() {
        return accentVariant;
    }

    public Label getValueLabel() {
        return valueLabel;
    }

    public Label getTitleLabel() {
        return titleLabel;
    }
}
