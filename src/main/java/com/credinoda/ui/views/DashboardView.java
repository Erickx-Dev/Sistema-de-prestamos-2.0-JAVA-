package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.dto.ProximoPagoItem;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.service.CreditoService;
import com.credinoda.ui.components.MetricCard;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.TableColumnHelper;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Vista principal del Panel de Inicio / Dashboard (CU-012, RN-010, RN-013).
 * Muestra las 3 tarjetas exclusivas de la cartera activa (Créditos Activos, Capital Prestado, Saldo Pendiente)
 * y la tabla con las próximas 10 cuotas por cobrar (incluyendo cuotas vencidas en mora).
 */
public class DashboardView extends VBox implements ViewRouter.RoutableView {

    public static final int LIMITE_PROXIMOS_PAGOS = 10;

    private final CreditoService creditoService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final MetricCard cardCreditosActivos;
    private final MetricCard cardCapitalPrestado;
    private final MetricCard cardSaldoPendiente;
    private final TableView<ProximoPagoItem> tablaProximosPagos;
    private final ObservableList<ProximoPagoItem> itemsProximosPagos = FXCollections.observableArrayList();

    public DashboardView(CreditoService creditoService,
                         ViewRouter viewRouter,
                         BiConsumer<String, ToastNotification.Type> notifier) {
        this.creditoService = Objects.requireNonNull(creditoService, "CreditoService no puede ser nulo");
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        SectionHeader header = new SectionHeader(
                "Panel de Control",
                "Resumen general de cartera activa y próximos cobros programados",
                "+ Nuevo Crédito",
                () -> {
                    if (this.viewRouter != null) {
                        this.viewRouter.navigate(NavAction.CREDITO_NEW);
                    }
                }
        );

        // Fila de 3 tarjetas KPI de cartera activa (RN-013)
        HBox metricsRow = new HBox(16);
        this.cardCreditosActivos = new MetricCard(
                "Créditos Activos", "0", "💳", MetricCard.AccentVariant.PRIMARY, "Obligaciones vigentes");
        this.cardCapitalPrestado = new MetricCard(
                "Capital Prestado", "$0", "💰", MetricCard.AccentVariant.SUCCESS, "Capital en cartera activa");
        this.cardSaldoPendiente = new MetricCard(
                "Saldo Pendiente", "$0", "⏳", MetricCard.AccentVariant.WARNING, "Total insoluto por recaudar");

        HBox.setHgrow(cardCreditosActivos, Priority.ALWAYS);
        HBox.setHgrow(cardCapitalPrestado, Priority.ALWAYS);
        HBox.setHgrow(cardSaldoPendiente, Priority.ALWAYS);
        cardCreditosActivos.setMaxWidth(Double.MAX_VALUE);
        cardCapitalPrestado.setMaxWidth(Double.MAX_VALUE);
        cardSaldoPendiente.setMaxWidth(Double.MAX_VALUE);

        metricsRow.getChildren().addAll(cardCreditosActivos, cardCapitalPrestado, cardSaldoPendiente);

        // Tarjeta contenedora de tabla de próximos cobros
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label tableTitle = new Label("Próximas Cuotas por Cobrar (Top 10)");
        tableTitle.getStyleClass().add("card-title");

        this.tablaProximosPagos = buildTable();
        VBox.setVgrow(tablaProximosPagos, Priority.ALWAYS);

        tableCard.getChildren().addAll(tableTitle, tablaProximosPagos);
        getChildren().addAll(header, metricsRow, tableCard);
    }

    private TableView<ProximoPagoItem> buildTable() {
        TableView<ProximoPagoItem> table = new TableView<>(itemsProximosPagos);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No hay cuotas pendientes por cobrar."));

        TableColumn<ProximoPagoItem, String> colCliente = TableColumnHelper.textoColumn(
                "Cliente",
                ProximoPagoItem::clienteNombre,
                Pos.CENTER,
                220
        );

        TableColumn<ProximoPagoItem, String> colCuota = TableColumnHelper.textoColumn(
                "Cuota",
                d -> "Crédito #" + d.creditoId() + " — Cuota #" + d.numeroCuota(),
                Pos.CENTER,
                180
        );

        TableColumn<ProximoPagoItem, String> colMonto = TableColumnHelper.monedaColumn(
                "Monto",
                ProximoPagoItem::monto,
                Pos.CENTER,
                140
        );

        TableColumn<ProximoPagoItem, String> colVencimiento = TableColumnHelper.fechaColumn(
                "Vencimiento",
                ProximoPagoItem::fechaVencimiento,
                130
        );

        TableColumn<ProximoPagoItem, String> colEstado = TableColumnHelper.badgeColumn(
                "Estado",
                ProximoPagoItem::etiquetaEstado,
                item -> item.contains("EN MORA") ? "badge-danger" : (item.startsWith("PARCIAL") ? "badge-warning" : "badge-info"),
                130
        );

        table.getColumns().setAll(List.of(colCliente, colCuota, colMonto, colVencimiento, colEstado));

        table.setRowFactory(tv -> {
            TableRow<ProximoPagoItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() >= 1 && viewRouter != null) {
                    ProximoPagoItem selected = row.getItem();
                    viewRouter.navigate(NavAction.ROUTE_CREDITO_DETAIL, selected.creditoId());
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
     * Carga las métricas de cartera activa (RN-013) y las próximas 10 cuotas pendientes.
     */
    public void loadData() {
        try {
            List<Credito> todos = creditoService.listarCreditos();
            List<Credito> activos = todos.stream()
                    .filter(c -> c.getEstado() == EstadoCredito.ACTIVO)
                    .toList();

            int numActivos = activos.size();
            BigDecimal capitalPrestado = activos.stream()
                    .map(Credito::getCapital)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saldoPendiente = BigDecimal.ZERO;
            for (Credito c : activos) {
                saldoPendiente = saldoPendiente.add(creditoService.calcularSaldoPendiente(c.getId()));
            }

            cardCreditosActivos.setValue(String.valueOf(numActivos));
            cardCapitalPrestado.setValue(Formatters.formatoMoneda(capitalPrestado));
            cardSaldoPendiente.setValue(Formatters.formatoMoneda(saldoPendiente));

            List<ProximoPagoItem> proximos = creditoService.obtenerProximasCuotas(LIMITE_PROXIMOS_PAGOS);
            itemsProximosPagos.setAll(proximos);
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al cargar el panel de inicio: " + ex.getMessage(),
                        ToastNotification.Type.ERROR);
            }
        }
    }

    public MetricCard getCardCreditosActivos() {
        return cardCreditosActivos;
    }

    public MetricCard getCardCapitalPrestado() {
        return cardCapitalPrestado;
    }

    public MetricCard getCardSaldoPendiente() {
        return cardSaldoPendiente;
    }

    public TableView<ProximoPagoItem> getTablaProximosPagos() {
        return tablaProximosPagos;
    }

    public ObservableList<ProximoPagoItem> getItemsProximosPagos() {
        return itemsProximosPagos;
    }
}
