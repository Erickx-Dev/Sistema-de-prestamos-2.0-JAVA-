package com.credinoda.ui.components;

import com.credinoda.common.Formatters;
import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Diálogo modal de inspección detallada de un cobro realizado y generación/exportación
 * de su comprobante PDF con código QR (CU-011, RN-012).
 */
public class DetallePagoDialog {

    private final Stage stage;
    private final DatosComprobante datos;
    private final PdfService pdfService;
    private final Consumer<Integer> onNavigateToCredito;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final Button verPdfButton;
    private final Button guardarComoButton;
    private final Button irAlCreditoButton;

    public DetallePagoDialog(Window owner,
                             int pagoId,
                             PagoService pagoService,
                             PdfService pdfService,
                             Consumer<Integer> onNavigateToCredito,
                             BiConsumer<String, ToastNotification.Type> notifier) {
        Objects.requireNonNull(pagoService, "PagoService no puede ser nulo");
        this.pdfService = Objects.requireNonNull(pdfService, "PdfService no puede ser nulo");
        this.onNavigateToCredito = onNavigateToCredito;
        this.notifier = notifier;
        this.datos = pagoService.obtenerDatosComprobante(pagoId);

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle("Detalle de Pago — " + datos.numeroRecibo());
        this.stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(540);

        // 1. Cabecera
        HBox header = new HBox(10);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("Comprobante de Abono " + datos.numeroRecibo());
        titleLabel.getStyleClass().add("dialog-header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label dateBadge = new Label(Formatters.formatoFecha(datos.fechaPago()));
        dateBadge.getStyleClass().add("badge-info");

        header.getChildren().addAll(titleLabel, spacer, dateBadge);

        // 2. Cuerpo con resumen del pago
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        GridPane infoGrid = new GridPane();
        infoGrid.getStyleClass().add("card-subtle");
        infoGrid.setHgap(20);
        infoGrid.setVgap(8);

        addRow(infoGrid, 0, "Titular / Cliente:", datos.clienteNombre() + " (" + datos.clienteDocumento() + ")");
        addRow(infoGrid, 1, "Teléfono:", datos.clienteTelefono());
        addRow(infoGrid, 2, "Obligación:", String.format("Crédito #%d (%s)", datos.creditoId(), datos.creditoFrecuencia()));
        addRow(infoGrid, 3, "Cuota Abonada:", String.format("Cuota #%d de %d (Vence: %s)",
                datos.numeroCuota(), datos.totalCuotas(), Formatters.formatoFecha(datos.cuotaFechaVencimiento())));
        addRow(infoGrid, 4, "Monto Abonado:", Formatters.formatoMoneda(datos.pagoMonto()));
        addRow(infoGrid, 5, "Estado Cuota:", datos.cuotaEstado());
        addRow(infoGrid, 6, "Saldo Restante Crédito:",
                String.format("%s (Avance: %s%%)",
                        Formatters.formatoMoneda(datos.saldoCreditoRestante()),
                        datos.porcentajePagadoCredito().toPlainString()));

        // Botones de acciones de comprobante PDF
        VBox actionsBox = new VBox(8);
        this.verPdfButton = new Button("📄 Ver / Imprimir Comprobante PDF");
        this.verPdfButton.getStyleClass().add("btn-primary");
        this.verPdfButton.setMaxWidth(Double.MAX_VALUE);
        this.verPdfButton.setOnAction(e -> handleVerPdf());

        this.guardarComoButton = new Button("💾 Guardar Comprobante Como...");
        this.guardarComoButton.getStyleClass().add("btn-secondary");
        this.guardarComoButton.setMaxWidth(Double.MAX_VALUE);
        this.guardarComoButton.setOnAction(e -> handleGuardarComo());

        this.irAlCreditoButton = new Button("🔍 Ir al Detalle del Crédito #" + datos.creditoId());
        this.irAlCreditoButton.getStyleClass().add("btn-secondary");
        this.irAlCreditoButton.setMaxWidth(Double.MAX_VALUE);
        this.irAlCreditoButton.setOnAction(e -> {
            stage.close();
            if (this.onNavigateToCredito != null) {
                this.onNavigateToCredito.accept(datos.creditoId());
            }
        });

        actionsBox.getChildren().addAll(verPdfButton, guardarComoButton, irAlCreditoButton);
        body.getChildren().addAll(infoGrid, actionsBox);

        // 3. Pie
        HBox footer = new HBox(10);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button closeBtn = new Button("Cerrar");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setCancelButton(true);
        closeBtn.setOnAction(e -> stage.close());
        footer.getChildren().add(closeBtn);

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

    private void addRow(GridPane grid, int row, String label, String value) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("form-label");
        Label val = new Label(value != null ? value : "");
        if (row == 4) {
            val.getStyleClass().add("badge-success");
        }
        grid.add(lbl, 0, row);
        grid.add(val, 1, row);
    }

    public Path handleVerPdf() {
        try {
            Path path = pdfService.generarComprobantePago(datos, null);
            pdfService.abrirDocumento(path);
            if (notifier != null) {
                notifier.accept("Comprobante generado: " + path.getFileName(), ToastNotification.Type.SUCCESS);
            }
            return path;
        } catch (CrediNodaException ex) {
            if (notifier != null) {
                notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
            }
            return null;
        }
    }

    private void handleGuardarComo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Comprobante PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Documento PDF (*.pdf)", "*.pdf"));

        String fechaCompacta = datos.fechaPago().format(DateTimeFormatter.BASIC_ISO_DATE);
        String clienteLimpio = PdfService.sanitizarNombreArchivo(datos.clienteNombre());
        chooser.setInitialFileName(String.format("Recibo_%s_%s_%s.pdf",
                datos.numeroRecibo(), clienteLimpio, fechaCompacta));

        File dest = chooser.showSaveDialog(stage);
        if (dest == null) {
            return;
        }

        try {
            Path path = pdfService.generarComprobantePago(datos, dest.toPath());
            if (notifier != null) {
                notifier.accept("Comprobante guardado en: " + path.getFileName(), ToastNotification.Type.SUCCESS);
            }
        } catch (CrediNodaException ex) {
            if (notifier != null) {
                notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
            }
        }
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public DatosComprobante getDatos() {
        return datos;
    }

    public Button getVerPdfButton() {
        return verPdfButton;
    }

    public Button getIrAlCreditoButton() {
        return irAlCreditoButton;
    }
}
