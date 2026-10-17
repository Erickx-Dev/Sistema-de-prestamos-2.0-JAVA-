package com.credinoda.ui.components;

import com.credinoda.common.Formatters;
import com.credinoda.common.Validators;
import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.Pago;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Diálogo modal para registrar cobros totales o parciales sobre cuotas de un crédito (CU-009, RN-009 a RN-012, RN-015).
 * Prellena automáticamente el saldo pendiente de la cuota seleccionada y permite emitir el comprobante PDF.
 */
public class PagoDialog {

    private final Stage stage;
    private final Credito credito;
    private final List<Cuota> cuotasDisponibles;
    private final PagoService pagoService;
    private final PdfService pdfService;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final ComboBox<Cuota> cuotaCombo;
    private final Label valorCuotaLabel;
    private final Label pagadoCuotaLabel;
    private final Label pendienteCuotaLabel;
    private final TextField montoField;
    private final CheckBox generarPdfCheck;
    private final Label errorLabel;
    private final Button saveButton;
    private final BorderPane root;

    private Pago pagoRegistrado;
    private Path comprobanteGenerado;

    public PagoDialog(Window owner,
                      Credito credito,
                      List<Cuota> cuotasCredito,
                      Cuota cuotaPreseleccionada,
                      PagoService pagoService,
                      PdfService pdfService,
                      BiConsumer<String, ToastNotification.Type> notifier) {
        this.credito = Objects.requireNonNull(credito, "El crédito no puede ser nulo");
        this.pagoService = Objects.requireNonNull(pagoService, "PagoService no puede ser nulo");
        this.pdfService = pdfService;
        this.notifier = notifier;

        this.cuotasDisponibles = cuotasCredito.stream()
                .filter(c -> c.getEstado() == EstadoCuota.PENDIENTE || c.getEstado() == EstadoCuota.PARCIAL)
                .toList();

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle("Registrar Pago — Crédito #" + credito.getId());
        this.stage.setResizable(false);
        this.stage.setMinWidth(500);
        this.stage.setMinHeight(460);

        this.root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(500);

        // 1. Cabecera
        HBox header = new HBox(10);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label headerTitle = new Label("Registrar Cobro — Crédito #" + credito.getId());
        headerTitle.getStyleClass().add("dialog-header-title");
        header.getChildren().add(headerTitle);

        // 2. Cuerpo
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        // Selector de cuota
        VBox cuotaBox = new VBox(4);
        Label cuotaLbl = new Label("Seleccionar Cuota a Abonar *");
        cuotaLbl.getStyleClass().add("form-label");

        this.cuotaCombo = new ComboBox<>();
        this.cuotaCombo.setMaxWidth(Double.MAX_VALUE);
        this.cuotaCombo.getItems().setAll(cuotasDisponibles);
        this.cuotaCombo.setCellFactory(lv -> createCuotaCell());
        this.cuotaCombo.setButtonCell(createCuotaCell());
        this.cuotaCombo.valueProperty().addListener((obs, oldV, newV) -> onCuotaSelected(newV));
        cuotaBox.getChildren().addAll(cuotaLbl, cuotaCombo);

        // Tarjeta de resumen de la cuota seleccionada
        GridPane resumenGrid = new GridPane();
        resumenGrid.getStyleClass().add("card-subtle");
        resumenGrid.setHgap(18);
        resumenGrid.setVgap(6);

        this.valorCuotaLabel = new Label("$0");
        this.valorCuotaLabel.getStyleClass().add("form-label");

        this.pagadoCuotaLabel = new Label("$0");
        this.pagadoCuotaLabel.getStyleClass().add("form-label");

        this.pendienteCuotaLabel = new Label("$0");
        this.pendienteCuotaLabel.getStyleClass().add("badge-warning");

        resumenGrid.add(new Label("Valor Cuota:"), 0, 0);
        resumenGrid.add(valorCuotaLabel, 0, 1);
        resumenGrid.add(new Label("Abonado:"), 1, 0);
        resumenGrid.add(pagadoCuotaLabel, 1, 1);
        resumenGrid.add(new Label("Saldo Pendiente:"), 2, 0);
        resumenGrid.add(pendienteCuotaLabel, 2, 1);

        // Campo de monto a pagar
        VBox montoBox = new VBox(4);
        Label montoLbl = new Label("Monto Recibido ($) *");
        montoLbl.getStyleClass().add("form-label");
        this.montoField = new TextField();
        this.montoField.setPromptText("Ingrese el monto del abono");
        HBox.setHgrow(montoField, Priority.ALWAYS);
        montoBox.getChildren().addAll(montoLbl, montoField);

        // Checkbox PDF
        this.generarPdfCheck = new CheckBox("Generar y abrir comprobante en PDF");
        this.generarPdfCheck.setSelected(true);

        // Etiqueta de error
        this.errorLabel = new Label();
        this.errorLabel.getStyleClass().add("badge-danger");
        this.errorLabel.setWrapText(true);
        this.errorLabel.setMaxWidth(Double.MAX_VALUE);
        this.errorLabel.setVisible(false);
        this.errorLabel.setManaged(false);

        body.getChildren().addAll(cuotaBox, resumenGrid, montoBox, generarPdfCheck, errorLabel);

        // 3. Pie de botones
        HBox footer = new HBox(10);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button cancelButton = new Button("Cancelar");
        cancelButton.getStyleClass().add("btn-secondary");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> stage.close());

        this.saveButton = new Button("Registrar Pago");
        this.saveButton.getStyleClass().add("btn-primary");
        this.saveButton.setDefaultButton(true);
        this.saveButton.setOnAction(e -> handleSave());

        footer.getChildren().addAll(cancelButton, saveButton);

        root.setTop(header);
        root.setCenter(body);
        root.setBottom(footer);

        // Preseleccionar cuota indicada o la primera disponible
        if (cuotaPreseleccionada != null) {
            cuotasDisponibles.stream()
                    .filter(c -> Objects.equals(c.getId(), cuotaPreseleccionada.getId()))
                    .findFirst()
                    .ifPresentOrElse(cuotaCombo::setValue, () -> selectFirstAvailable());
        } else {
            selectFirstAvailable();
        }

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

    private void selectFirstAvailable() {
        if (!cuotasDisponibles.isEmpty()) {
            cuotaCombo.setValue(cuotasDisponibles.get(0));
        }
    }

    private ListCell<Cuota> createCuotaCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Cuota item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String moraTag = item.isEnMora() ? " [EN MORA]" : "";
                    setText(String.format("Cuota #%d — Vence: %s (%s)%s",
                            item.getNumeroCuota(),
                            Formatters.formatoFecha(item.getFechaVencimiento()),
                            Formatters.formatoMoneda(item.getMonto()),
                            moraTag));
                }
            }
        };
    }

    private void onCuotaSelected(Cuota cuota) {
        hideError();
        if (cuota == null) {
            valorCuotaLabel.setText("$0");
            pagadoCuotaLabel.setText("$0");
            pendienteCuotaLabel.setText("$0");
            montoField.clear();
            return;
        }

        BigDecimal pagado = pagoService.obtenerTotalPagadoCuota(cuota.getId());
        BigDecimal pendiente = cuota.getMonto().subtract(pagado).max(BigDecimal.ZERO);

        valorCuotaLabel.setText(Formatters.formatoMoneda(cuota.getMonto()));
        pagadoCuotaLabel.setText(Formatters.formatoMoneda(pagado));
        pendienteCuotaLabel.setText(Formatters.formatoMoneda(pendiente));

        BigDecimal stripped = pendiente.stripTrailingZeros();
        if (stripped.scale() <= 0) {
            montoField.setText(stripped.toPlainString());
        } else {
            montoField.setText(pendiente.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    public boolean handleSave() {
        hideError();
        Cuota cuota = cuotaCombo.getValue();
        if (cuota == null) {
            showError("Debe seleccionar una cuota pendiente.");
            return false;
        }

        String textoMonto = montoField.getText();
        Validators.ResultadoValidacion valMonto = Validators.validarMonto(textoMonto);
        if (!valMonto.valido()) {
            showError(valMonto.mensaje());
            return false;
        }

        try {
            BigDecimal monto = Validators.parsearMonto(textoMonto);
            this.pagoRegistrado = pagoService.registrarPago(credito.getId(), cuota.getId(), monto, LocalDate.now());

            if (generarPdfCheck.isSelected() && pdfService != null) {
                try {
                    DatosComprobante datos = pagoService.obtenerDatosComprobante(pagoRegistrado.getId());
                    this.comprobanteGenerado = pdfService.generarComprobantePago(datos, null);
                    pdfService.abrirDocumento(this.comprobanteGenerado);
                    if (notifier != null) {
                        notifier.accept("Comprobante generado: " + comprobanteGenerado.getFileName(),
                                ToastNotification.Type.SUCCESS);
                    }
                } catch (Exception pdfEx) {
                    if (notifier != null) {
                        notifier.accept("Pago registrado, pero falló el PDF: " + pdfEx.getMessage(),
                                ToastNotification.Type.WARNING);
                    }
                }
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
        if (wasVisible && stage.isShowing()) {
            root.applyCss();
            root.layout();
            stage.sizeToScene();
        }
    }

    public Optional<Pago> showAndWait() {
        stage.showAndWait();
        return Optional.ofNullable(pagoRegistrado);
    }

    public ComboBox<Cuota> getCuotaCombo() {
        return cuotaCombo;
    }

    public TextField getMontoField() {
        return montoField;
    }

    public CheckBox getGenerarPdfCheck() {
        return generarPdfCheck;
    }

    public Label getErrorLabel() {
        return errorLabel;
    }

    public Optional<Pago> getPagoRegistrado() {
        return Optional.ofNullable(pagoRegistrado);
    }

    public Optional<Path> getComprobanteGenerado() {
        return Optional.ofNullable(comprobanteGenerado);
    }
}
