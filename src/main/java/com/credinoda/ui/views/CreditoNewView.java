package com.credinoda.ui.views;

import com.credinoda.common.AppConfig;
import com.credinoda.common.Formatters;
import com.credinoda.common.Validators;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Permiso;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.ui.components.ClienteDialog;
import com.credinoda.ui.components.ConfirmDialog;
import com.credinoda.ui.components.MetricCard;
import com.credinoda.ui.components.PermissionGuard;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.TableColumnHelper;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Vista de Simulación y Otorgamiento de Nuevo Crédito (CU-005, CU-006, RN-004 a RN-007).
 * Recalcula reactivamente intereses, total a pagar, número de cuotas y cronograma preliminar
 * con ajuste exacto de centavos en la última cuota antes de confirmar la persistencia.
 */
public class CreditoNewView extends VBox implements ViewRouter.RoutableView {

    private final ClienteService clienteService;
    private final CreditoService creditoService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final ComboBox<Cliente> clienteCombo;
    private final Button quickAddClienteBtn;
    private final TextField capitalField;
    private final TextField plazoField;
    private final ComboBox<FrecuenciaPago> frecuenciaCombo;
    private final TextField tasaPorcentajeField;

    private final MetricCard cardIntereses;
    private final MetricCard cardTotalPagar;
    private final MetricCard cardNumCuotas;
    private final MetricCard cardValorCuota;

    private final TableView<Cuota> tablaSimulacion;
    private final ObservableList<Cuota> itemsSimulacion = FXCollections.observableArrayList();
    private final Button crearCreditoButton;

    public CreditoNewView(ClienteService clienteService,
                          CreditoService creditoService,
                          ViewRouter viewRouter,
                          BiConsumer<String, ToastNotification.Type> notifier) {
        this.clienteService = Objects.requireNonNull(clienteService, "ClienteService no puede ser nulo");
        this.creditoService = Objects.requireNonNull(creditoService, "CreditoService no puede ser nulo");
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        SectionHeader header = new SectionHeader(
                "Otorgar Nuevo Crédito",
                "Simulador financiero de interés simple mensual y proyección de cronograma de pagos"
        );

        HBox mainSplit = new HBox(18);
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        // Panel izquierdo: Formulario de condiciones del crédito
        VBox formCard = new VBox(14);
        formCard.getStyleClass().add("card");
        formCard.setPrefWidth(390);
        formCard.setMinWidth(350);

        Label formTitle = new Label("Condiciones del Préstamo");
        formTitle.getStyleClass().add("card-title");

        // Selector de cliente + botón rápido de nuevo cliente
        VBox clienteBox = new VBox(4);
        Label clienteLbl = new Label("Cliente Prestatario *");
        clienteLbl.getStyleClass().add("form-label");

        HBox clienteRow = new HBox(8);
        this.clienteCombo = new ComboBox<>();
        this.clienteCombo.setPromptText("Seleccione un cliente...");
        this.clienteCombo.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(clienteCombo, Priority.ALWAYS);
        this.clienteCombo.setCellFactory(lv -> createClienteCell());
        this.clienteCombo.setButtonCell(createClienteCell());

        this.quickAddClienteBtn = new Button("+");
        quickAddClienteBtn.getStyleClass().add("btn-secondary");
        quickAddClienteBtn.setOnAction(e -> handleQuickCreateCliente());
        PermissionGuard.apply(quickAddClienteBtn, Permiso.CLIENTES_CREAR);

        clienteRow.getChildren().addAll(clienteCombo, quickAddClienteBtn);
        clienteBox.getChildren().addAll(clienteLbl, clienteRow);

        // Capital
        this.capitalField = new TextField();
        this.capitalField.setPromptText("Ej: 1.000.000");

        // Plazo en meses y Frecuencia
        HBox plazoFreqRow = new HBox(12);
        this.plazoField = new TextField("1");
        this.plazoField.setPromptText("Meses (ej: 3)");

        this.frecuenciaCombo = new ComboBox<>(FXCollections.observableArrayList(FrecuenciaPago.values()));
        this.frecuenciaCombo.setValue(FrecuenciaPago.SEMANAL);
        this.frecuenciaCombo.setMaxWidth(Double.MAX_VALUE);

        VBox plazoBox = buildFieldBox("Plazo (Meses) *", plazoField);
        VBox freqBox = new VBox(4);
        Label freqLbl = new Label("Frecuencia de Cobro *");
        freqLbl.getStyleClass().add("form-label");
        freqBox.getChildren().addAll(freqLbl, frecuenciaCombo);

        HBox.setHgrow(plazoBox, Priority.ALWAYS);
        HBox.setHgrow(freqBox, Priority.ALWAYS);
        plazoFreqRow.getChildren().addAll(plazoBox, freqBox);

        // Tasa de interés mensual (%)
        BigDecimal defaultRatePct = AppConfig.DEFAULT_MONTHLY_RATE
                .multiply(BigDecimal.valueOf(100))
                .stripTrailingZeros();
        this.tasaPorcentajeField = new TextField(defaultRatePct.toPlainString());
        this.tasaPorcentajeField.setPromptText("Ej: 12");

        // Listeners reactivos para el simulador en tiempo real (CU-005)
        this.capitalField.textProperty().addListener((obs, o, n) -> updateSimulacion());
        this.plazoField.textProperty().addListener((obs, o, n) -> updateSimulacion());
        this.tasaPorcentajeField.textProperty().addListener((obs, o, n) -> updateSimulacion());
        this.frecuenciaCombo.valueProperty().addListener((obs, o, n) -> updateSimulacion());

        this.crearCreditoButton = new Button("✓ Confirmar y Crear Crédito");
        this.crearCreditoButton.getStyleClass().add("btn-primary");
        this.crearCreditoButton.setMaxWidth(Double.MAX_VALUE);
        this.crearCreditoButton.setOnAction(e -> handleCrearCredito());

        formCard.getChildren().addAll(
                formTitle,
                clienteBox,
                buildFieldBox("Capital a Prestar ($) *", capitalField),
                plazoFreqRow,
                buildFieldBox("Tasa de Interés Mensual (%) *", tasaPorcentajeField),
                crearCreditoButton
        );

        // Panel derecho: Resumen del simulador y previsualización del cronograma
        VBox previewPane = new VBox(14);
        HBox.setHgrow(previewPane, Priority.ALWAYS);

        HBox simMetrics = new HBox(12);
        this.cardIntereses = new MetricCard("Total Intereses", "$0", "📈", MetricCard.AccentVariant.INFO);
        this.cardTotalPagar = new MetricCard("Total a Pagar", "$0", "💰", MetricCard.AccentVariant.PRIMARY);
        this.cardNumCuotas = new MetricCard("N° de Cuotas", "0", "🗓", MetricCard.AccentVariant.INFO);
        this.cardValorCuota = new MetricCard("Cuota Base", "$0", "💵", MetricCard.AccentVariant.SUCCESS);

        for (MetricCard c : List.of(cardIntereses, cardTotalPagar, cardNumCuotas, cardValorCuota)) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        simMetrics.getChildren().addAll(cardIntereses, cardTotalPagar, cardNumCuotas, cardValorCuota);

        VBox tableCard = new VBox(10);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        Label simTableTitle = new Label("Previsualización del Cronograma de Cuotas");
        simTableTitle.getStyleClass().add("card-title");

        this.tablaSimulacion = buildSimulacionTable();
        VBox.setVgrow(tablaSimulacion, Priority.ALWAYS);

        tableCard.getChildren().addAll(simTableTitle, tablaSimulacion);
        previewPane.getChildren().addAll(simMetrics, tableCard);

        mainSplit.getChildren().addAll(formCard, previewPane);
        getChildren().addAll(header, mainSplit);
    }

    private VBox buildFieldBox(String labelText, TextField field) {
        VBox box = new VBox(4);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        box.getChildren().addAll(lbl, field);
        return box;
    }

    private ListCell<Cliente> createClienteCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Cliente item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getNombre() + " — Doc: " + item.getDocumento());
                }
            }
        };
    }

    private TableView<Cuota> buildSimulacionTable() {
        TableView<Cuota> table = new TableView<>(itemsSimulacion);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Ingrese capital y plazo válidos para previsualizar el plan de pagos."));

        TableColumn<Cuota, String> colNum = TableColumnHelper.textoColumn(
                "N° Cuota",
                c -> "Cuota #" + c.getNumeroCuota(),
                Pos.CENTER,
                110
        );
        colNum.setMaxWidth(130);

        TableColumn<Cuota, String> colVenc = TableColumnHelper.fechaColumn(
                "Fecha Vencimiento",
                Cuota::getFechaVencimiento,
                160
        );
        colVenc.setMaxWidth(180);

        TableColumn<Cuota, String> colMonto = TableColumnHelper.monedaColumn(
                "Valor de Cuota",
                Cuota::getMonto,
                180
        );

        table.getColumns().setAll(List.of(colNum, colVenc, colMonto));
        return table;
    }

    @Override
    public void onShow(int currentDataVersion) {
        PermissionGuard.apply(quickAddClienteBtn, Permiso.CLIENTES_CREAR);
        reloadClientes(null);
        updateSimulacion();
    }

    public void preselectCliente(int clienteId) {
        reloadClientes(clienteId);
    }

    private void reloadClientes(Integer selectClienteId) {
        try {
            Cliente prev = clienteCombo.getValue();
            List<Cliente> clientes = clienteService.listarTodos();
            clienteCombo.getItems().setAll(clientes);

            Integer targetId = selectClienteId != null ? selectClienteId : (prev != null ? prev.getId() : null);
            if (targetId != null) {
                clientes.stream()
                        .filter(c -> Objects.equals(c.getId(), targetId))
                        .findFirst()
                        .ifPresent(clienteCombo::setValue);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * Simula en tiempo real el crédito sin tocar la base de datos (CU-005).
     */
    public void updateSimulacion() {
        try {
            BigDecimal capital = Validators.parsearMonto(capitalField.getText());
            int plazo = Integer.parseInt(plazoField.getText().strip());
            BigDecimal tasaPct = Validators.parsearMonto(tasaPorcentajeField.getText());
            BigDecimal tasaDecimal = tasaPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            FrecuenciaPago frecuencia = frecuenciaCombo.getValue() != null
                    ? frecuenciaCombo.getValue()
                    : FrecuenciaPago.SEMANAL;

            if (capital.compareTo(BigDecimal.ZERO) <= 0 || plazo <= 0 || tasaDecimal.compareTo(BigDecimal.ZERO) < 0) {
                clearSimulacion();
                return;
            }

            BigDecimal intereses = creditoService.calcularIntereses(capital, tasaDecimal, plazo);
            BigDecimal totalPagar = creditoService.calcularTotalPagar(capital, intereses);
            int numCuotas = creditoService.calcularNumeroCuotas(plazo, frecuencia);
            List<Cuota> plan = creditoService.generarPlanPagos(totalPagar, numCuotas, frecuencia, LocalDate.now());

            cardIntereses.setValue(Formatters.formatoMoneda(intereses));
            cardTotalPagar.setValue(Formatters.formatoMoneda(totalPagar));
            cardNumCuotas.setValue(String.valueOf(numCuotas));
            if (!plan.isEmpty()) {
                cardValorCuota.setValue(Formatters.formatoMoneda(plan.get(0).getMonto()));
            }
            itemsSimulacion.setAll(plan);
        } catch (Exception e) {
            clearSimulacion();
        }
    }

    private void clearSimulacion() {
        cardIntereses.setValue("$0");
        cardTotalPagar.setValue("$0");
        cardNumCuotas.setValue("0");
        cardValorCuota.setValue("$0");
        itemsSimulacion.clear();
    }

    private void handleQuickCreateCliente() {
        Window owner = getScene() != null ? getScene().getWindow() : null;
        ClienteDialog dialog = new ClienteDialog(owner, clienteService);
        Optional<Cliente> creado = dialog.showAndWait();
        creado.ifPresent(c -> {
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            reloadClientes(c.getId());
            if (notifier != null) {
                notifier.accept("Cliente registrado y seleccionado", ToastNotification.Type.SUCCESS);
            }
        });
    }

    private void handleCrearCredito() {
        Cliente cliente = clienteCombo.getValue();
        if (cliente == null) {
            notifyError("Debe seleccionar un cliente prestatario.");
            return;
        }

        Validators.ResultadoValidacion valCap = Validators.validarMonto(capitalField.getText());
        if (!valCap.valido()) {
            notifyError(valCap.mensaje());
            return;
        }

        Validators.ResultadoValidacion valPlazo = Validators.validarEnteroPositivo(plazoField.getText(), "Plazo en meses");
        if (!valPlazo.valido()) {
            notifyError(valPlazo.mensaje());
            return;
        }

        try {
            BigDecimal capital = Validators.parsearMonto(capitalField.getText());
            int plazo = Integer.parseInt(plazoField.getText().strip());
            BigDecimal tasaPct = Validators.parsearMonto(tasaPorcentajeField.getText());
            BigDecimal tasaDecimal = tasaPct.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
            FrecuenciaPago frecuencia = frecuenciaCombo.getValue();

            BigDecimal intereses = creditoService.calcularIntereses(capital, tasaDecimal, plazo);
            BigDecimal totalPagar = creditoService.calcularTotalPagar(capital, intereses);
            int numCuotas = creditoService.calcularNumeroCuotas(plazo, frecuencia);

            Window owner = getScene() != null ? getScene().getWindow() : null;
            String msg = String.format(
                    "¿Confirmar la creación del crédito para %s?\n\n" +
                            "• Capital: %s\n" +
                            "• Total a Pagar: %s\n" +
                            "• Plan: %d cuotas (%s)",
                    cliente.getNombre(),
                    Formatters.formatoMoneda(capital),
                    Formatters.formatoMoneda(totalPagar),
                    numCuotas,
                    frecuencia.name()
            );

            ConfirmDialog confirm = new ConfirmDialog(owner, "Confirmar Otorgamiento de Crédito", msg, "Crear Crédito", false);
            if (!confirm.showAndWait()) {
                return;
            }

            Credito creado = creditoService.crearCredito(
                    cliente.getId(), capital, tasaDecimal, plazo, frecuencia, LocalDate.now()
            );

            capitalField.clear();
            plazoField.setText("1");
            clearSimulacion();

            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
                viewRouter.navigate(NavAction.ROUTE_CREDITO_DETAIL, creado.getId());
            }
            if (notifier != null) {
                notifier.accept("Crédito creado exitosamente", ToastNotification.Type.SUCCESS);
            }
        } catch (CrediNodaException ex) {
            notifyError(ex.getMessage());
        } catch (Exception ex) {
            notifyError("Datos de tasa o capital inválidos.");
        }
    }

    private void notifyError(String msg) {
        if (notifier != null) {
            notifier.accept(msg, ToastNotification.Type.ERROR);
        }
    }

    public ComboBox<Cliente> getClienteCombo() {
        return clienteCombo;
    }

    public TextField getCapitalField() {
        return capitalField;
    }

    public TextField getPlazoField() {
        return plazoField;
    }

    public ComboBox<FrecuenciaPago> getFrecuenciaCombo() {
        return frecuenciaCombo;
    }

    public TextField getTasaPorcentajeField() {
        return tasaPorcentajeField;
    }

    public ObservableList<Cuota> getItemsSimulacion() {
        return itemsSimulacion;
    }
}
