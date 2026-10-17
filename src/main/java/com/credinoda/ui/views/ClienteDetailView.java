package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.ui.components.ClienteDialog;
import com.credinoda.ui.components.MetricCard;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Subvista de Ficha Detallada de un Cliente e Historial de sus Créditos (CU-003, CU-004).
 */
public class ClienteDetailView extends VBox implements ViewRouter.RoutableView {

    private final int clienteId;
    private final ClienteService clienteService;
    private final CreditoService creditoService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private Cliente clienteActual;
    private final SectionHeader header;
    private final MetricCard cardDocumento;
    private final MetricCard cardTelefono;
    private final MetricCard cardTotalCreditos;
    private final MetricCard cardDeudaActiva;
    private final TableView<Credito> tablaCreditos;
    private final ObservableList<Credito> itemsCreditos = FXCollections.observableArrayList();

    public ClienteDetailView(int clienteId,
                             ClienteService clienteService,
                             CreditoService creditoService,
                             ViewRouter viewRouter,
                             BiConsumer<String, ToastNotification.Type> notifier) {
        this.clienteId = clienteId;
        this.clienteService = Objects.requireNonNull(clienteService, "ClienteService no puede ser nulo");
        this.creditoService = Objects.requireNonNull(creditoService, "CreditoService no puede ser nulo");
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        this.header = new SectionHeader("Ficha del Cliente", "Expediente e historial de créditos otorgados");

        Button backBtn = new Button("← Volver a Clientes");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> {
            if (this.viewRouter != null) {
                this.viewRouter.navigate(NavAction.CLIENTES);
            }
        });

        Button editBtn = new Button("✏ Editar Cliente");
        editBtn.getStyleClass().add("btn-secondary");
        editBtn.setOnAction(e -> handleEditarCliente());

        Button newCreditoBtn = new Button("+ Nuevo Crédito");
        newCreditoBtn.getStyleClass().add("btn-primary");
        newCreditoBtn.setOnAction(e -> {
            if (this.viewRouter != null) {
                this.viewRouter.navigate(NavAction.CREDITO_NEW.getRouteKey(), this.clienteId);
            }
        });

        HBox actions = new HBox(8, backBtn, editBtn, newCreditoBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);
        this.header.setActionNode(actions);

        // Tarjetas de datos y métricas del cliente
        HBox metricsRow = new HBox(14);
        this.cardDocumento = new MetricCard("Documento", "-", "🪪", MetricCard.AccentVariant.INFO);
        this.cardTelefono = new MetricCard("Teléfono", "-", "📞", MetricCard.AccentVariant.INFO);
        this.cardTotalCreditos = new MetricCard("Créditos Otorgados", "0", "📋", MetricCard.AccentVariant.PRIMARY);
        this.cardDeudaActiva = new MetricCard("Saldo Pendiente", "$0", "⏳", MetricCard.AccentVariant.WARNING);

        for (MetricCard c : List.of(cardDocumento, cardTelefono, cardTotalCreditos, cardDeudaActiva)) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        metricsRow.getChildren().addAll(cardDocumento, cardTelefono, cardTotalCreditos, cardDeudaActiva);

        // Tabla de historial de créditos del cliente
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label tableTitle = new Label("Historial de Créditos del Prestatario");
        tableTitle.getStyleClass().add("card-title");

        this.tablaCreditos = buildTable();
        VBox.setVgrow(tablaCreditos, Priority.ALWAYS);

        tableCard.getChildren().addAll(tableTitle, tablaCreditos);
        getChildren().addAll(header, metricsRow, tableCard);
    }

    private TableView<Credito> buildTable() {
        TableView<Credito> table = new TableView<>(itemsCreditos);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Este cliente aún no registra créditos otorgados."));

        TableColumn<Credito, String> colId = new TableColumn<>("Crédito");
        colId.setMaxWidth(100);
        colId.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getId()));

        TableColumn<Credito, String> colFecha = new TableColumn<>("Fecha Inicio");
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatoFecha(d.getValue().getFechaInicio())));

        TableColumn<Credito, String> colCapital = new TableColumn<>("Capital");
        colCapital.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatoMoneda(d.getValue().getCapital())));

        TableColumn<Credito, String> colTotal = new TableColumn<>("Total a Pagar");
        colTotal.setCellValueFactory(d -> new SimpleStringProperty(Formatters.formatoMoneda(d.getValue().getTotalPagar())));

        TableColumn<Credito, String> colSaldo = new TableColumn<>("Saldo Pendiente");
        colSaldo.setCellValueFactory(d -> {
            BigDecimal saldo = creditoService.calcularSaldoPendiente(d.getValue().getId());
            return new SimpleStringProperty(Formatters.formatoMoneda(saldo));
        });

        TableColumn<Credito, String> colFrecuencia = new TableColumn<>("Frecuencia");
        colFrecuencia.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getFrecuenciaPago().name() + " (" + d.getValue().getPlazoMeses() + " m)"));

        TableColumn<Credito, String> colEstado = new TableColumn<>("Estado");
        colEstado.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEstado().name()));
        colEstado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("PAGADO".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-success");
                    } else if ("ACTIVO".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-info");
                    } else {
                        badge.getStyleClass().add("badge-danger");
                    }
                    setGraphic(badge);
                }
            }
        });

        table.getColumns().setAll(List.of(colId, colFecha, colCapital, colTotal, colSaldo, colFrecuencia, colEstado));

        table.setRowFactory(tv -> {
            TableRow<Credito> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() >= 1 && viewRouter != null) {
                    viewRouter.navigate(NavAction.ROUTE_CREDITO_DETAIL, row.getItem().getId());
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

    public void loadData() {
        try {
            this.clienteActual = clienteService.obtenerClientePorId(clienteId);
            List<Credito> creditos = clienteService.obtenerCreditosCliente(clienteId);

            header.setTitle(clienteActual.getNombre());
            header.setSubtitle("Cliente #" + clienteActual.getId() + " — Expediente crediticio");

            cardDocumento.setValue(clienteActual.getDocumento());
            cardTelefono.setValue(clienteActual.getTelefono());

            long activosCount = creditos.stream().filter(c -> c.getEstado() == EstadoCredito.ACTIVO).count();
            cardTotalCreditos.setValue(String.valueOf(creditos.size()));
            cardTotalCreditos.setSubtitle(activosCount + " activos");

            BigDecimal deudaTotal = BigDecimal.ZERO;
            for (Credito c : creditos) {
                if (c.getEstado() == EstadoCredito.ACTIVO) {
                    deudaTotal = deudaTotal.add(creditoService.calcularSaldoPendiente(c.getId()));
                }
            }
            cardDeudaActiva.setValue(Formatters.formatoMoneda(deudaTotal));

            itemsCreditos.setAll(creditos);
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al cargar ficha del cliente: " + ex.getMessage(), ToastNotification.Type.ERROR);
            }
        }
    }

    private void handleEditarCliente() {
        if (clienteActual == null) {
            return;
        }
        Window owner = getScene() != null ? getScene().getWindow() : null;
        ClienteDialog dialog = new ClienteDialog(owner, clienteService, clienteActual);
        Optional<Cliente> actualizado = dialog.showAndWait();
        actualizado.ifPresent(c -> {
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            loadData();
            if (notifier != null) {
                notifier.accept("Datos del cliente actualizados exitosamente", ToastNotification.Type.SUCCESS);
            }
        });
    }

    public TableView<Credito> getTablaCreditos() {
        return tablaCreditos;
    }
}
