package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.Pago;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.service.PagoService;
import com.credinoda.service.PdfService;
import com.credinoda.ui.components.MetricCard;
import com.credinoda.ui.components.PagoDialog;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.TableColumnHelper;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Subvista de Detalle de un Crédito, Balance de Amortización y Plan de Cuotas (CU-007, CU-008, CU-009).
 * Presenta las 4 tarjetas financieras del crédito, el enlace al cliente titular, el estado de mora
 * de cada cuota y el lanzador de {@link PagoDialog}.
 */
public class CreditoDetailView extends VBox implements ViewRouter.RoutableView {

    /**
     * Fila enriquecida de cuota para mostrar en la tabla con sus montos pagados y pendientes calculados.
     */
    public record CuotaRowItem(
            Cuota cuota,
            BigDecimal montoPagado,
            BigDecimal saldoPendiente,
            String etiquetaEstado
    ) {
    }

    private final int creditoId;
    private final CreditoService creditoService;
    private final ClienteService clienteService;
    private final PagoService pagoService;
    private final PdfService pdfService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private Credito creditoActual;
    private Cliente clienteActual;
    private List<Cuota> cuotasActuales = List.of();

    private final SectionHeader header;
    private final Button clienteLinkButton;
    private final Button registrarPagoButton;

    private final MetricCard cardCapital;
    private final MetricCard cardTotalPagar;
    private final MetricCard cardPagado;
    private final MetricCard cardSaldoPendiente;

    private final TableView<CuotaRowItem> tablaCuotas;
    private final ObservableList<CuotaRowItem> itemsCuotas = FXCollections.observableArrayList();

    public CreditoDetailView(int creditoId,
                             CreditoService creditoService,
                             ClienteService clienteService,
                             PagoService pagoService,
                             PdfService pdfService,
                             ViewRouter viewRouter,
                             BiConsumer<String, ToastNotification.Type> notifier) {
        this.creditoId = creditoId;
        this.creditoService = Objects.requireNonNull(creditoService, "CreditoService no puede ser nulo");
        this.clienteService = Objects.requireNonNull(clienteService, "ClienteService no puede ser nulo");
        this.pagoService = Objects.requireNonNull(pagoService, "PagoService no puede ser nulo");
        this.pdfService = pdfService;
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        this.header = new SectionHeader("Detalle del Crédito #" + creditoId, "Condiciones pactadas y plan de amortización");

        Button backBtn = new Button("← Volver");
        backBtn.getStyleClass().add("btn-secondary");
        backBtn.setOnAction(e -> {
            if (this.viewRouter != null) {
                this.viewRouter.navigate(NavAction.DASHBOARD);
            }
        });

        this.clienteLinkButton = new Button("Cliente: -");
        this.clienteLinkButton.getStyleClass().add("btn-secondary");
        this.clienteLinkButton.setOnAction(e -> {
            if (clienteActual != null && this.viewRouter != null) {
                this.viewRouter.navigate(NavAction.ROUTE_CLIENTE_DETAIL, clienteActual.getId());
            }
        });

        this.registrarPagoButton = new Button("💵 Registrar Pago");
        this.registrarPagoButton.getStyleClass().add("btn-primary");
        this.registrarPagoButton.setOnAction(e -> openPagoDialog(null));

        HBox actions = new HBox(8, backBtn, clienteLinkButton, registrarPagoButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        this.header.setActionNode(actions);

        // 4 Tarjetas de resumen financiero del crédito
        HBox metricsRow = new HBox(14);
        this.cardCapital = new MetricCard("Capital Prestado", "$0", "💼", MetricCard.AccentVariant.INFO);
        this.cardTotalPagar = new MetricCard("Total Acordado", "$0", "📋", MetricCard.AccentVariant.PRIMARY);
        this.cardPagado = new MetricCard("Total Pagado", "$0", "✓", MetricCard.AccentVariant.SUCCESS);
        this.cardSaldoPendiente = new MetricCard("Saldo Pendiente", "$0", "⏳", MetricCard.AccentVariant.WARNING);

        for (MetricCard c : List.of(cardCapital, cardTotalPagar, cardPagado, cardSaldoPendiente)) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        metricsRow.getChildren().addAll(cardCapital, cardTotalPagar, cardPagado, cardSaldoPendiente);

        // Tabla de plan de cuotas
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label tableTitle = new Label("Cronograma de Cuotas (Haga clic en una cuota pendiente para registrar un abono)");
        tableTitle.getStyleClass().add("card-title");

        this.tablaCuotas = buildTable();
        VBox.setVgrow(tablaCuotas, Priority.ALWAYS);

        tableCard.getChildren().addAll(tableTitle, tablaCuotas);
        getChildren().addAll(header, metricsRow, tableCard);
    }

    private TableView<CuotaRowItem> buildTable() {
        TableView<CuotaRowItem> table = new TableView<>(itemsCuotas);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No hay cuotas asociadas a este crédito."));

        TableColumn<CuotaRowItem, String> colNum = TableColumnHelper.textoColumn(
                "N° Cuota",
                d -> "#" + d.cuota().getNumeroCuota(),
                Pos.CENTER,
                90
        );
        colNum.setMaxWidth(100);

        TableColumn<CuotaRowItem, String> colVenc = TableColumnHelper.fechaColumn(
                "Vencimiento",
                d -> d.cuota().getFechaVencimiento(),
                120
        );

        TableColumn<CuotaRowItem, String> colMonto = TableColumnHelper.monedaColumn(
                "Valor Cuota",
                d -> d.cuota().getMonto(),
                Pos.CENTER,
                140
        );

        TableColumn<CuotaRowItem, String> colPagado = TableColumnHelper.monedaColumn(
                "Abonado",
                CuotaRowItem::montoPagado,
                Pos.CENTER,
                140
        );

        TableColumn<CuotaRowItem, String> colPendiente = TableColumnHelper.monedaColumn(
                "Pendiente",
                CuotaRowItem::saldoPendiente,
                Pos.CENTER,
                140
        );

        TableColumn<CuotaRowItem, String> colEstado = TableColumnHelper.badgeColumn(
                "Estado",
                CuotaRowItem::etiquetaEstado,
                item -> item.contains("EN MORA") ? "badge-danger" : (item.startsWith("PAGADA") ? "badge-success" : (item.startsWith("PARCIAL") ? "badge-warning" : "badge-info")),
                140
        );

        table.getColumns().setAll(List.of(colNum, colVenc, colMonto, colPagado, colPendiente, colEstado));

        table.setRowFactory(tv -> {
            TableRow<CuotaRowItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() >= 1) {
                    Cuota c = row.getItem().cuota();
                    if (c.getEstado() == EstadoCuota.PENDIENTE || c.getEstado() == EstadoCuota.PARCIAL) {
                        openPagoDialog(c);
                    }
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
            creditoService.determinarEstadoCredito(creditoId);
            this.creditoActual = creditoService.obtenerCreditoPorId(creditoId);
            this.clienteActual = clienteService.obtenerClientePorId(creditoActual.getClienteId());
            this.cuotasActuales = creditoService.obtenerCuotasCredito(creditoId);

            BigDecimal saldoPendiente = creditoService.calcularSaldoPendiente(creditoId);
            BigDecimal totalPagado = creditoActual.getTotalPagar().subtract(saldoPendiente).max(BigDecimal.ZERO);

            header.setTitle(String.format("Crédito #%d — Estado: %s",
                    creditoActual.getId(), creditoActual.getEstado().name()));
            header.setSubtitle(String.format("Inicio: %s | Tasa mensual: %s | Plazo: %d meses (%s)",
                    Formatters.formatoFecha(creditoActual.getFechaInicio()),
                    Formatters.formatoPorcentaje(creditoActual.getTasaMensual()),
                    creditoActual.getPlazoMeses(),
                    creditoActual.getFrecuenciaPago().name()));

            clienteLinkButton.setText(String.format("👤 Cliente: %s (%s)",
                    clienteActual.getNombre(), clienteActual.getDocumento()));

            cardCapital.setValue(Formatters.formatoMoneda(creditoActual.getCapital()));
            cardTotalPagar.setValue(Formatters.formatoMoneda(creditoActual.getTotalPagar()));
            cardTotalPagar.setSubtitle("Intereses: " + Formatters.formatoMoneda(creditoActual.getTotalIntereses()));
            cardPagado.setValue(Formatters.formatoMoneda(totalPagado));
            cardSaldoPendiente.setValue(Formatters.formatoMoneda(saldoPendiente));

            List<CuotaRowItem> rows = new ArrayList<>(cuotasActuales.size());
            for (Cuota c : cuotasActuales) {
                BigDecimal pagadoCuota = pagoService.obtenerTotalPagadoCuota(c.getId());
                BigDecimal pendCuota = c.getMonto().subtract(pagadoCuota).max(BigDecimal.ZERO);
                rows.add(new CuotaRowItem(c, pagadoCuota, pendCuota, c.etiquetaEstado()));
            }
            itemsCuotas.setAll(rows);
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al cargar detalle del crédito: " + ex.getMessage(),
                        ToastNotification.Type.ERROR);
            }
        }
    }

    public void openPagoDialog(Cuota cuotaPreseleccionada) {
        if (creditoActual == null) {
            return;
        }
        List<Cuota> pendientes = cuotasActuales.stream()
                .filter(c -> c.getEstado() == EstadoCuota.PENDIENTE || c.getEstado() == EstadoCuota.PARCIAL)
                .toList();

        if (pendientes.isEmpty()) {
            if (notifier != null) {
                notifier.accept("No hay cuotas pendientes para este crédito.", ToastNotification.Type.WARNING);
            }
            return;
        }

        Window owner = getScene() != null ? getScene().getWindow() : null;
        PagoDialog dialog = new PagoDialog(
                owner, creditoActual, cuotasActuales, cuotaPreseleccionada, pagoService, pdfService, notifier
        );
        Optional<Pago> pagoOpt = dialog.showAndWait();
        pagoOpt.ifPresent(p -> {
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            loadData();
            if (notifier != null) {
                notifier.accept("Pago registrado exitosamente", ToastNotification.Type.SUCCESS);
            }
        });
    }

    public MetricCard getCardCapital() {
        return cardCapital;
    }

    public MetricCard getCardTotalPagar() {
        return cardTotalPagar;
    }

    public MetricCard getCardPagado() {
        return cardPagado;
    }

    public MetricCard getCardSaldoPendiente() {
        return cardSaldoPendiente;
    }

    public ObservableList<CuotaRowItem> getItemsCuotas() {
        return itemsCuotas;
    }
}
