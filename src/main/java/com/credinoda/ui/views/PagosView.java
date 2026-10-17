package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.dto.PagoDetalleItem;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import com.credinoda.ui.components.DetallePagoDialog;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Vista de Historial General de Pagos y Buscador Manual Inteligente (CU-010, CU-011, RN-016).
 * Presenta todos los cobros ordenados cronológicamente (más recientes primero) y aplica
 * la heurística diferenciada para búsquedas numéricas exactas (#4 vs 4) y textuales/recibo (REC-xxxxx).
 */
public class PagosView extends VBox implements ViewRouter.RoutableView {

    private final PagoService pagoService;
    private final PdfService pdfService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private List<PagoDetalleItem> todosLosPagos = new ArrayList<>();
    private final TextField searchField;
    private final Button buscarButton;
    private final Button limpiarButton;
    private final TableView<PagoDetalleItem> tablaPagos;
    private final ObservableList<PagoDetalleItem> itemsPagos = FXCollections.observableArrayList();

    public PagosView(PagoService pagoService,
                     PdfService pdfService,
                     ViewRouter viewRouter,
                     BiConsumer<String, ToastNotification.Type> notifier) {
        this.pagoService = Objects.requireNonNull(pagoService, "PagoService no puede ser nulo");
        this.pdfService = pdfService;
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        SectionHeader header = new SectionHeader(
                "Historial de Pagos y Recaudos",
                "Auditoría de transacciones, búsqueda de recibos y reimpresión de comprobantes PDF"
        );

        // Barra de búsqueda manual (CU-010, RN-016)
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        this.searchField = new TextField();
        this.searchField.setPromptText("🔍 Buscar por cliente, #crédito (ej: #4), ID de pago o recibo (ej: REC-00015)...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        this.searchField.setOnAction(e -> aplicarFiltroBusqueda(searchField.getText()));

        this.buscarButton = new Button("Buscar");
        this.buscarButton.getStyleClass().add("btn-primary");
        this.buscarButton.setOnAction(e -> aplicarFiltroBusqueda(searchField.getText()));

        this.limpiarButton = new Button("Limpiar");
        this.limpiarButton.getStyleClass().add("btn-secondary");
        this.limpiarButton.setOnAction(e -> limpiarBusqueda());

        searchBar.getChildren().addAll(searchField, buscarButton, limpiarButton);

        // Tarjeta de tabla
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        this.tablaPagos = buildTable();
        VBox.setVgrow(tablaPagos, Priority.ALWAYS);

        tableCard.getChildren().addAll(searchBar, tablaPagos);
        getChildren().addAll(header, tableCard);
    }

    private TableView<PagoDetalleItem> buildTable() {
        TableView<PagoDetalleItem> table = new TableView<>(itemsPagos);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No se encontraron pagos registrados."));

        TableColumn<PagoDetalleItem, String> colRecibo = new TableColumn<>("Recibo");
        colRecibo.setMaxWidth(130);
        colRecibo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().codigoRecibo()));

        TableColumn<PagoDetalleItem, String> colFecha = new TableColumn<>("Fecha");
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(
                Formatters.formatoFecha(d.getValue().fechaPago())));

        TableColumn<PagoDetalleItem, String> colCliente = new TableColumn<>("Cliente");
        colCliente.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().clienteNombre()));

        TableColumn<PagoDetalleItem, String> colCredito = new TableColumn<>("Crédito");
        colCredito.setMaxWidth(110);
        colCredito.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().creditoId()));

        TableColumn<PagoDetalleItem, String> colCuota = new TableColumn<>("Cuota");
        colCuota.setMaxWidth(110);
        colCuota.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().numeroCuota())));

        TableColumn<PagoDetalleItem, String> colMonto = new TableColumn<>("Monto Abonado");
        colMonto.setCellValueFactory(d -> new SimpleStringProperty(
                Formatters.formatoMoneda(d.getValue().montoAbonado())));

        TableColumn<PagoDetalleItem, Void> colAccion = new TableColumn<>("Comprobante");
        colAccion.setMaxWidth(150);
        colAccion.setCellFactory(col -> new TableCell<>() {
            private final Button btnDetalle = new Button("📄 Recibo PDF");

            {
                btnDetalle.getStyleClass().addAll("btn-secondary", "btn-sm");
                btnDetalle.setOnAction(e -> {
                    PagoDetalleItem item = getTableView().getItems().get(getIndex());
                    if (item != null) {
                        openDetallePagoDialog(item.pagoId());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDetalle);
            }
        });

        table.getColumns().setAll(List.of(colRecibo, colFecha, colCliente, colCredito, colCuota, colMonto, colAccion));

        table.setRowFactory(tv -> {
            TableRow<PagoDetalleItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2) {
                    openDetallePagoDialog(row.getItem().pagoId());
                }
            });
            return row;
        });

        return table;
    }

    @Override
    public void onShow(int currentDataVersion) {
        loadData();
    }

    /**
     * Carga todos los pagos desde el servicio y reaplica el filtro activo si existe.
     */
    public void loadData() {
        try {
            this.todosLosPagos = pagoService.listarPagosConDetalle(null, null, null);
            aplicarFiltroBusqueda(searchField.getText());
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al cargar el historial de pagos: " + ex.getMessage(),
                        ToastNotification.Type.ERROR);
            }
        }
    }

    /**
     * Aplica la regla de negocio RN-016 para filtrado manual de pagos sobre la colección cargada.
     */
    public void aplicarFiltroBusqueda(String texto) {
        List<PagoDetalleItem> filtrados = filtrarPagosManual(todosLosPagos, texto);
        itemsPagos.setAll(filtrados);
    }

    /**
     * Limpia el campo de búsqueda y restaura la totalidad de pagos en la tabla.
     */
    public void limpiarBusqueda() {
        searchField.clear();
        itemsPagos.setAll(todosLosPagos);
    }

    /**
     * Implementación pura y determinista de la heurística de búsqueda manual de pagos (RN-016):
     * - Si {@code query_num} (sin '#' inicial) es puramente numérico: coincide exactamente con
     *   {@code creditoId == query_num} o {@code pagoId == query_num} o {@code query in clienteNombre}.
     *   No hace coincidencia parcial sobre números para evitar que buscar '4' traiga el pago '14'.
     * - Si no es puramente numérico: coincide con {@code query in clienteNombre}, {@code query == '#' + creditoId}
     *   o {@code query in codigoRecibo} (ej. 'rec-00015').
     */
    public static List<PagoDetalleItem> filtrarPagosManual(List<PagoDetalleItem> origen, String texto) {
        if (origen == null || origen.isEmpty()) {
            return List.of();
        }
        if (texto == null || texto.isBlank()) {
            return List.copyOf(origen);
        }

        String query = texto.strip().toLowerCase(Locale.ROOT);
        String queryNum = stripLeadingHashes(query).strip();
        boolean esNumerico = !queryNum.isEmpty() && queryNum.chars().allMatch(Character::isDigit);

        List<PagoDetalleItem> resultado = new ArrayList<>();
        for (PagoDetalleItem item : origen) {
            String nombreLower = item.clienteNombre() != null
                    ? item.clienteNombre().toLowerCase(Locale.ROOT)
                    : "";
            String creditoIdStr = String.valueOf(item.creditoId());
            String pagoIdStr = String.valueOf(item.pagoId());
            String reciboLower = item.codigoRecibo().toLowerCase(Locale.ROOT);

            if (esNumerico) {
                if (creditoIdStr.equals(queryNum)
                        || pagoIdStr.equals(queryNum)
                        || nombreLower.contains(query)) {
                    resultado.add(item);
                }
            } else {
                if (nombreLower.contains(query)
                        || ("#" + creditoIdStr).equals(query)
                        || reciboLower.contains(query)) {
                    resultado.add(item);
                }
            }
        }
        return resultado;
    }

    private static String stripLeadingHashes(String input) {
        int idx = 0;
        while (idx < input.length() && input.charAt(idx) == '#') {
            idx++;
        }
        return input.substring(idx);
    }

    private void openDetallePagoDialog(int pagoId) {
        if (pdfService == null) {
            return;
        }
        Window owner = getScene() != null ? getScene().getWindow() : null;
        DetallePagoDialog dialog = new DetallePagoDialog(
                owner,
                pagoId,
                pagoService,
                pdfService,
                creditoId -> {
                    if (viewRouter != null) {
                        viewRouter.navigate(NavAction.ROUTE_CREDITO_DETAIL, creditoId);
                    }
                },
                notifier
        );
        dialog.showAndWait();
    }

    public TextField getSearchField() {
        return searchField;
    }

    public Button getBuscarButton() {
        return buscarButton;
    }

    public Button getLimpiarButton() {
        return limpiarButton;
    }

    public TableView<PagoDetalleItem> getTablaPagos() {
        return tablaPagos;
    }

    public ObservableList<PagoDetalleItem> getItemsPagos() {
        return itemsPagos;
    }
}
