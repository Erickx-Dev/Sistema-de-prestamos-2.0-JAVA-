package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.dto.DistribucionCartera;
import com.credinoda.domain.dto.MetricasReporte;
import com.credinoda.domain.dto.ProyeccionSemanalItem;
import com.credinoda.domain.dto.RangoFechas;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.service.ReporteService;
import com.credinoda.ui.components.MetricCard;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Vista de Reportes Financieros, 10 KPIs y Gráficos Vectoriales Nativos (CU-013, RN-014).
 * Incluye selector segmentado de períodos temporales, entradas ISO personalizadas (AAAA-MM-DD),
 * 10 tarjetas de indicadores clave y 2 gráficos nativos JavaFX ({@link BarChart} y {@link PieChart}).
 */
public class ReportesView extends VBox implements ViewRouter.RoutableView {

    public static final String PRESET_TODOS = "TODOS";
    public static final String PRESET_HOY = "HOY";
    public static final String PRESET_ESTA_SEMANA = "ESTA_SEMANA";
    public static final String PRESET_ESTE_MES = "ESTE_MES";
    public static final String PRESET_ANIO_EN_CURSO = "ANIO_EN_CURSO";
    public static final String PRESET_PERSONALIZADO = "PERSONALIZADO";

    private final ReporteService reporteService;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final Map<String, Button> presetButtons = new LinkedHashMap<>();
    private String activePreset = PRESET_TODOS;

    private final TextField fechaDesdeField;
    private final TextField fechaHastaField;
    private final Button aplicarRangoButton;

    // 10 Tarjetas KPI (RN-014)
    private final MetricCard cardCreditosActivos;
    private final MetricCard cardCreditosPagados;
    private final MetricCard cardCapitalPrestado;
    private final MetricCard cardInteresesGenerados;
    private final MetricCard cardPagosRecibidos;
    private final MetricCard cardSaldoVencido;
    private final MetricCard cardTasaMorosidad;
    private final MetricCard cardGananciaLiquida;
    private final MetricCard cardTicketPromedio;
    private final MetricCard cardPlazoPromedio;

    // Gráficos vectoriales nativos JavaFX
    private final BarChart<String, Number> barChart;
    private final Label barEmptyLabel;
    private final PieChart pieChart;
    private final Label pieEmptyLabel;

    public ReportesView(ReporteService reporteService,
                        BiConsumer<String, ToastNotification.Type> notifier) {
        this.reporteService = Objects.requireNonNull(reporteService, "ReporteService no puede ser nulo");
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(16);

        SectionHeader header = new SectionHeader(
                "Reportes y Analítica Financiera",
                "Rentabilidad real por prorrateo, exposición en mora, distribución de cartera y recaudo semanal"
        );

        // Barra de filtros temporales (Selector segmentado + Rango personalizado ISO)
        HBox filterBar = buildFilterBar();
        this.fechaDesdeField = (TextField) filterBar.lookup("#fechaDesdeField");
        this.fechaHastaField = (TextField) filterBar.lookup("#fechaHastaField");
        this.aplicarRangoButton = (Button) filterBar.lookup("#aplicarRangoButton");

        // 10 Tarjetas KPI en 2 filas de 5 columnas
        this.cardCreditosActivos = new MetricCard("Créditos Activos", "0", "💳", MetricCard.AccentVariant.PRIMARY);
        this.cardCreditosPagados = new MetricCard("Créditos Liquidados", "0", "✓", MetricCard.AccentVariant.SUCCESS);
        this.cardCapitalPrestado = new MetricCard("Capital Colocado", "$0", "💼", MetricCard.AccentVariant.INFO);
        this.cardInteresesGenerados = new MetricCard("Intereses Proyectados", "$0", "📈", MetricCard.AccentVariant.INFO);
        this.cardPagosRecibidos = new MetricCard("Recaudo Total", "$0", "💵", MetricCard.AccentVariant.SUCCESS);

        this.cardSaldoVencido = new MetricCard("Saldo en Mora", "$0", "⚠", MetricCard.AccentVariant.DANGER);
        this.cardTasaMorosidad = new MetricCard("Tasa de Morosidad", "0%", "🚨", MetricCard.AccentVariant.SUCCESS);
        this.cardGananciaLiquida = new MetricCard("Ganancia Líquida", "$0", "🏆", MetricCard.AccentVariant.SUCCESS);
        this.cardTicketPromedio = new MetricCard("Ticket Promedio", "$0", "📊", MetricCard.AccentVariant.PRIMARY);
        this.cardPlazoPromedio = new MetricCard("Plazo Promedio", "0 meses", "🗓", MetricCard.AccentVariant.INFO);

        GridPane kpiGrid = new GridPane();
        kpiGrid.setHgap(12);
        kpiGrid.setVgap(12);

        List<MetricCard> row1 = List.of(
                cardCreditosActivos, cardCreditosPagados, cardCapitalPrestado, cardInteresesGenerados, cardPagosRecibidos);
        List<MetricCard> row2 = List.of(
                cardSaldoVencido, cardTasaMorosidad, cardGananciaLiquida, cardTicketPromedio, cardPlazoPromedio);

        for (int i = 0; i < 5; i++) {
            MetricCard c1 = row1.get(i);
            MetricCard c2 = row2.get(i);
            GridPane.setHgrow(c1, Priority.ALWAYS);
            GridPane.setHgrow(c2, Priority.ALWAYS);
            c1.setMaxWidth(Double.MAX_VALUE);
            c2.setMaxWidth(Double.MAX_VALUE);
            kpiGrid.add(c1, i, 0);
            kpiGrid.add(c2, i, 1);
        }

        // Sección de Gráficos Nativos Vectoriales (BarChart + PieChart)
        HBox chartsRow = new HBox(16);
        VBox.setVgrow(chartsRow, Priority.ALWAYS);

        // Gráfico 1: BarChart Recaudo vs Proyección Semanal
        VBox barCard = new VBox(8);
        barCard.getStyleClass().add("card");
        HBox.setHgrow(barCard, Priority.ALWAYS);

        Label barTitle = new Label("Recaudo vs. Proyección Semanal");
        barTitle.getStyleClass().add("card-title");

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        this.barChart = new BarChart<>(xAxis, yAxis);
        this.barChart.setAnimated(false);
        this.barChart.setLegendVisible(true);
        this.barChart.setMinHeight(250);

        this.barEmptyLabel = new Label("Sin cuotas ni recaudos registrados en este período");
        this.barEmptyLabel.getStyleClass().add("section-subtitle");
        this.barEmptyLabel.setVisible(false);

        StackPane barStack = new StackPane(barChart, barEmptyLabel);
        VBox.setVgrow(barStack, Priority.ALWAYS);
        barCard.getChildren().addAll(barTitle, barStack);

        // Gráfico 2: PieChart Distribución de Cartera
        VBox pieCard = new VBox(8);
        pieCard.getStyleClass().add("card");
        pieCard.setPrefWidth(380);
        pieCard.setMinWidth(320);

        Label pieTitle = new Label("Distribución de Cartera");
        pieTitle.getStyleClass().add("card-title");

        this.pieChart = new PieChart();
        this.pieChart.setAnimated(false);
        this.pieChart.setLabelsVisible(true);
        this.pieChart.setMinHeight(250);

        this.pieEmptyLabel = new Label("Sin créditos en este período");
        this.pieEmptyLabel.getStyleClass().add("section-subtitle");
        this.pieEmptyLabel.setVisible(false);

        StackPane pieStack = new StackPane(pieChart, pieEmptyLabel);
        VBox.setVgrow(pieStack, Priority.ALWAYS);
        pieCard.getChildren().addAll(pieTitle, pieStack);

        chartsRow.getChildren().addAll(barCard, pieCard);

        VBox contentBox = new VBox(16, filterBar, kpiGrid, chartsRow);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        ScrollPane scrollPane = new ScrollPane(contentBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().addAll(header, scrollPane);
    }

    private HBox buildFilterBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("card");
        bar.setAlignment(Pos.CENTER_LEFT);

        HBox segmented = new HBox(4);
        segmented.getStyleClass().add("segmented-bar");
        segmented.setAlignment(Pos.CENTER_LEFT);

        addPresetButton(segmented, PRESET_TODOS, "Todos");
        addPresetButton(segmented, PRESET_HOY, "Hoy");
        addPresetButton(segmented, PRESET_ESTA_SEMANA, "Esta Semana");
        addPresetButton(segmented, PRESET_ESTE_MES, "Este Mes");
        addPresetButton(segmented, PRESET_ANIO_EN_CURSO, "Año en curso");
        addPresetButton(segmented, PRESET_PERSONALIZADO, "Personalizado");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblDesde = new Label("Desde:");
        lblDesde.getStyleClass().add("form-label");
        TextField desde = new TextField();
        desde.setId("fechaDesdeField");
        desde.setPromptText("AAAA-MM-DD");
        desde.setPrefWidth(120);
        desde.setOnAction(e -> aplicarRangoPersonalizado());

        Label lblHasta = new Label("Hasta:");
        lblHasta.getStyleClass().add("form-label");
        TextField hasta = new TextField();
        hasta.setId("fechaHastaField");
        hasta.setPromptText("AAAA-MM-DD");
        hasta.setPrefWidth(120);
        hasta.setOnAction(e -> aplicarRangoPersonalizado());

        Button btnAplicar = new Button("Aplicar");
        btnAplicar.setId("aplicarRangoButton");
        btnAplicar.getStyleClass().addAll("btn-primary", "btn-sm");
        btnAplicar.setOnAction(e -> aplicarRangoPersonalizado());

        bar.getChildren().addAll(segmented, spacer, lblDesde, desde, lblHasta, hasta, btnAplicar);
        updateActivePresetStyle(PRESET_TODOS);
        return bar;
    }

    private void addPresetButton(HBox container, String key, String label) {
        Button btn = new Button(label);
        btn.getStyleClass().add("segmented-btn");
        btn.setOnAction(e -> selectPreset(key));
        presetButtons.put(key, btn);
        container.getChildren().add(btn);
    }

    private void updateActivePresetStyle(String presetKey) {
        this.activePreset = presetKey;
        presetButtons.forEach((k, btn) -> {
            btn.getStyleClass().remove("segmented-btn-active");
            if (k.equals(presetKey)) {
                btn.getStyleClass().add("segmented-btn-active");
            }
        });
    }

    public void selectPreset(String presetKey) {
        updateActivePresetStyle(presetKey);
        if (!PRESET_PERSONALIZADO.equals(presetKey)) {
            RangoFechas rango = reporteService.calcularRangoFechas(presetKey, LocalDate.now());
            fechaDesdeField.setText(rango.desde() != null ? rango.desde().toString() : "");
            fechaHastaField.setText(rango.hasta() != null ? rango.hasta().toString() : "");
            loadData(rango.desde(), rango.hasta());
        }
    }

    public void aplicarRangoPersonalizado() {
        LocalDate desde = null;
        LocalDate hasta = null;

        String txtDesde = fechaDesdeField.getText() != null ? fechaDesdeField.getText().strip() : "";
        String txtHasta = fechaHastaField.getText() != null ? fechaHastaField.getText().strip() : "";

        if (!txtDesde.isEmpty()) {
            try {
                desde = LocalDate.parse(txtDesde);
            } catch (DateTimeParseException e) {
                notifyError("Fecha inicial inválida. Use el formato AAAA-MM-DD (ej: 2026-09-01).");
                return;
            }
        }

        if (!txtHasta.isEmpty()) {
            try {
                hasta = LocalDate.parse(txtHasta);
            } catch (DateTimeParseException e) {
                notifyError("Fecha final inválida. Use el formato AAAA-MM-DD (ej: 2026-09-30).");
                return;
            }
        }

        updateActivePresetStyle(PRESET_PERSONALIZADO);
        loadData(desde, hasta);
    }

    @Override
    public void onShow(int currentDataVersion) {
        if (PRESET_PERSONALIZADO.equals(activePreset)) {
            aplicarRangoPersonalizado();
        } else {
            selectPreset(activePreset);
        }
    }

    public void loadData(LocalDate fechaDesde, LocalDate fechaHasta) {
        try {
            MetricasReporte m = reporteService.obtenerMetricas(fechaDesde, fechaHasta);
            DistribucionCartera dist = reporteService.obtenerDistribucionCartera(fechaDesde, fechaHasta);
            List<ProyeccionSemanalItem> semanal = reporteService.obtenerRecaudoVsProyeccionSemanal(fechaDesde, fechaHasta);

            // Actualizar las 10 tarjetas KPI
            cardCreditosActivos.setValue(String.valueOf(m.activos()));
            cardCreditosActivos.setSubtitle("Total del período: " + m.total());

            cardCreditosPagados.setValue(String.valueOf(m.pagados()));
            cardCapitalPrestado.setValue(Formatters.formatoMoneda(m.capitalPrestado()));
            cardInteresesGenerados.setValue(Formatters.formatoMoneda(m.interesesGenerados()));
            cardPagosRecibidos.setValue(Formatters.formatoMoneda(m.pagosRecibidos()));
            cardSaldoVencido.setValue(Formatters.formatoMoneda(m.saldoVencido()));

            String morosidadStr = m.tasaMorosidad().toPlainString().replace('.', ',') + "%";
            cardTasaMorosidad.setValue(morosidadStr);
            if (m.tasaMorosidad().compareTo(BigDecimal.ZERO) > 0) {
                cardTasaMorosidad.setAccentVariant(MetricCard.AccentVariant.DANGER);
            } else {
                cardTasaMorosidad.setAccentVariant(MetricCard.AccentVariant.SUCCESS);
            }

            cardGananciaLiquida.setValue(Formatters.formatoMoneda(m.interesesCobrados()));
            cardGananciaLiquida.setSubtitle("Margen: " + m.margenGanancia().toPlainString().replace('.', ',') + "%");

            cardTicketPromedio.setValue(Formatters.formatoMoneda(m.ticketPromedio()));
            cardPlazoPromedio.setValue(m.plazoPromedio().stripTrailingZeros().toPlainString() + " meses");

            // Actualizar BarChart
            updateBarChart(semanal);

            // Actualizar PieChart
            updatePieChart(dist);
        } catch (CrediNodaException ex) {
            notifyError(ex.getMessage());
        }
    }

    private void updateBarChart(List<ProyeccionSemanalItem> items) {
        barChart.getData().clear();
        boolean hasValues = items.stream().anyMatch(i ->
                i.proyectado().compareTo(BigDecimal.ZERO) > 0 || i.recaudado().compareTo(BigDecimal.ZERO) > 0);

        if (!hasValues) {
            barChart.setVisible(false);
            barEmptyLabel.setVisible(true);
            return;
        }

        barChart.setVisible(true);
        barEmptyLabel.setVisible(false);

        XYChart.Series<String, Number> serieProyectado = new XYChart.Series<>();
        serieProyectado.setName("Proyectado");

        XYChart.Series<String, Number> serieRecaudado = new XYChart.Series<>();
        serieRecaudado.setName("Recaudado");

        for (ProyeccionSemanalItem item : items) {
            serieProyectado.getData().add(new XYChart.Data<>(item.etiqueta(), item.proyectado()));
            serieRecaudado.getData().add(new XYChart.Data<>(item.etiqueta(), item.recaudado()));
        }

        barChart.getData().addAll(List.of(serieProyectado, serieRecaudado));
    }

    private void updatePieChart(DistribucionCartera dist) {
        pieChart.getData().clear();
        if (dist.total() <= 0) {
            pieChart.setVisible(false);
            pieEmptyLabel.setVisible(true);
            return;
        }

        pieChart.setVisible(true);
        pieEmptyLabel.setVisible(false);

        pieChart.setData(FXCollections.observableArrayList(
                new PieChart.Data("Al Día (" + dist.alDia() + ")", dist.alDia()),
                new PieChart.Data("En Mora (" + dist.enMora() + ")", dist.enMora()),
                new PieChart.Data("Saldados (" + dist.saldados() + ")", dist.saldados())
        ));
    }

    private void notifyError(String msg) {
        if (notifier != null) {
            notifier.accept(msg, ToastNotification.Type.ERROR);
        }
    }

    public MetricCard getCardTasaMorosidad() {
        return cardTasaMorosidad;
    }

    public MetricCard getCardGananciaLiquida() {
        return cardGananciaLiquida;
    }

    public BarChart<String, Number> getBarChart() {
        return barChart;
    }

    public PieChart getPieChart() {
        return pieChart;
    }
}
