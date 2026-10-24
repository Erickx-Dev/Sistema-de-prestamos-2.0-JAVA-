package com.credinoda.ui.components;

import com.credinoda.common.Formatters;
import com.credinoda.domain.model.FrecuenciaPago;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Tarjeta con estética de recibo/ticket térmico para la visualización de la
 * liquidación preliminar de crédito (Fase 6.5, CU-005, RN-004 a RN-007).
 */
public class TicketLiquidacionCard extends VBox {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm:ss", Locale.getDefault());

    private final Label titleLabel;
    private final Label timestampLabel;

    private final Label capitalLabel;
    private final Label capitalValue;

    private final Label interesesLabel;
    private final Label interesesValue;

    private final Label cuotasLabel;
    private final Label cuotasValue;

    private final Label cuotaBaseLabel;
    private final Label cuotaBaseValue;

    private final Label totalPagarPrefix;
    private final Label totalPagarValue;
    private final Label footnoteLabel;

    private final ImageView barcodeImageView;

    public TicketLiquidacionCard() {
        getStyleClass().add("ticket-card");
        setSpacing(6);
        setAlignment(Pos.TOP_CENTER);

        // 1. Encabezado del ticket
        this.titleLabel = new Label("LIQUIDACIÓN PRELIMINAR DE CRÉDITO");
        this.titleLabel.getStyleClass().add("ticket-title");

        this.timestampLabel = new Label("Fecha: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        this.timestampLabel.getStyleClass().add("ticket-timestamp");

        // 2. Divisor punteado superior
        Region dividerTop = createDashedDivider();

        // 3. Filas clave-valor
        this.capitalLabel = new Label("Capital a Prestar");
        this.capitalLabel.getStyleClass().add("ticket-row-label");
        this.capitalValue = new Label("$0");
        this.capitalValue.getStyleClass().add("ticket-row-value");
        HBox rowCapital = createKeyValueRow(capitalLabel, capitalValue);

        this.interesesLabel = new Label("Total de Intereses (0%)");
        this.interesesLabel.getStyleClass().add("ticket-row-label");
        this.interesesValue = new Label("$0");
        this.interesesValue.getStyleClass().add("ticket-row-value");
        HBox rowIntereses = createKeyValueRow(interesesLabel, interesesValue);

        this.cuotasLabel = new Label("Número de Cuotas");
        this.cuotasLabel.getStyleClass().add("ticket-row-label");
        this.cuotasValue = new Label("0");
        this.cuotasValue.getStyleClass().add("ticket-row-value");
        HBox rowCuotas = createKeyValueRow(cuotasLabel, cuotasValue);

        this.cuotaBaseLabel = new Label("Cuota Base");
        this.cuotaBaseLabel.getStyleClass().add("ticket-row-label");
        this.cuotaBaseValue = new Label("$0");
        this.cuotaBaseValue.getStyleClass().add("ticket-row-value");
        HBox rowCuotaBase = createKeyValueRow(cuotaBaseLabel, cuotaBaseValue);

        VBox rowsContainer = new VBox(4, rowCapital, rowIntereses, rowCuotas, rowCuotaBase);
        rowsContainer.getStyleClass().add("ticket-rows-container");

        // 4. Divisor punteado inferior
        Region dividerBottom = createDashedDivider();

        // 5. Total a pagar destacado
        HBox totalRow = new HBox(4);
        totalRow.setAlignment(Pos.CENTER);
        this.totalPagarPrefix = new Label("TOTAL A PAGAR: ");
        this.totalPagarPrefix.getStyleClass().add("ticket-total-label");
        this.totalPagarValue = new Label("$0");
        this.totalPagarValue.getStyleClass().add("ticket-total-value");
        totalRow.getChildren().addAll(totalPagarPrefix, totalPagarValue);

        this.footnoteLabel = new Label("Cálculos estimados para previsualización");
        this.footnoteLabel.getStyleClass().add("ticket-footnote");

        // 6. Código de barras 1D inferior
        this.barcodeImageView = new ImageView();
        this.barcodeImageView.setFitWidth(200);
        this.barcodeImageView.setFitHeight(36);
        this.barcodeImageView.setPreserveRatio(false);
        this.barcodeImageView.getStyleClass().add("ticket-barcode");

        // Generar código inicial
        actualizarBarcode("CN20-0-0-0");

        getChildren().addAll(
                titleLabel,
                timestampLabel,
                dividerTop,
                rowsContainer,
                dividerBottom,
                totalRow,
                footnoteLabel,
                barcodeImageView
        );
    }

    private Region createDashedDivider() {
        Region div = new Region();
        div.getStyleClass().add("ticket-divider");
        div.setMinHeight(1);
        div.setPrefHeight(1);
        div.setMaxHeight(1);
        return div;
    }

    private HBox createKeyValueRow(Label lblKey, Label lblVal) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        row.getChildren().addAll(lblKey, spacer, lblVal);
        return row;
    }

    /**
     * Actualiza todos los valores numéricos y el desglose de intereses del ticket.
     */
    public void actualizar(BigDecimal capital,
                           BigDecimal tasaPorcentaje,
                           BigDecimal intereses,
                           int numCuotas,
                           FrecuenciaPago frecuencia,
                           BigDecimal cuotaBase,
                           BigDecimal totalPagar) {
        this.timestampLabel.setText("Fecha: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));

        if (capital != null) {
            this.capitalValue.setText(Formatters.formatoMoneda(capital));
        } else {
            this.capitalValue.setText("$0");
        }

        String pctStr = "0%";
        if (tasaPorcentaje != null) {
            pctStr = tasaPorcentaje.stripTrailingZeros().toPlainString().replace('.', ',') + "%";
        }
        this.interesesLabel.setText("Total de Intereses (" + pctStr + ")");
        this.interesesValue.setText(intereses != null ? Formatters.formatoMoneda(intereses) : "$0");

        String freqNombre = "";
        if (frecuencia != null) {
            freqNombre = switch (frecuencia) {
                case SEMANAL -> numCuotas == 1 ? " semanal" : " semanales";
                case QUINCENAL -> numCuotas == 1 ? " quincenal" : " quincenales";
            };
        }
        this.cuotasValue.setText(numCuotas + freqNombre);
        this.cuotaBaseValue.setText(cuotaBase != null ? Formatters.formatoMoneda(cuotaBase) : "$0");

        String formattedTotal = totalPagar != null ? Formatters.formatoMoneda(totalPagar) : "$0";
        this.totalPagarValue.setText(formattedTotal);

        // Actualizar código de barras con metadatos del cálculo
        String codePayload = String.format("CN20-%s-%d-%s",
                capital != null ? capital.toPlainString() : "0",
                numCuotas,
                totalPagar != null ? totalPagar.toPlainString() : "0");
        actualizarBarcode(codePayload);
    }

    /**
     * Restablece el ticket al estado neutro vacío.
     */
    public void limpiar() {
        this.timestampLabel.setText("Fecha: " + LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        this.capitalValue.setText("$0");
        this.interesesLabel.setText("Total de Intereses (0%)");
        this.interesesValue.setText("$0");
        this.cuotasValue.setText("0");
        this.cuotaBaseValue.setText("$0");
        this.totalPagarValue.setText("$0");
        actualizarBarcode("CN20-0-0-0");
    }

    private void actualizarBarcode(String content) {
        WritableImage img = BarcodeHelper.generateCode128(content, 200, 36);
        this.barcodeImageView.setImage(img);
    }

    // Getters para verificación en pruebas unitarias e inspección de UI

    public String getCapitalText() {
        return capitalValue.getText();
    }

    public String getInteresesLabelText() {
        return interesesLabel.getText();
    }

    public String getInteresesValueText() {
        return interesesValue.getText();
    }

    public String getNumeroCuotasText() {
        return cuotasValue.getText();
    }

    public String getCuotaBaseText() {
        return cuotaBaseValue.getText();
    }

    public String getTotalPagarText() {
        return totalPagarValue.getText();
    }

    public Label getTotalPagarValue() {
        return totalPagarValue;
    }

    public ImageView getBarcodeImageView() {
        return barcodeImageView;
    }
}
