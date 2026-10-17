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
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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

    // Gráficos vectoriales nativos JavaFX y elementos analíticos enriquecidos (Fase 6.1)
    private final BarChart<String, Number> barChart;
    private final Label barEmptyLabel;
    private final Label barBadgeCumplimiento;

    private final PieChart pieChart;
    private final Label pieEmptyLabel;
    private final Label pieBadgeTotal;
    private final VBox donutCenter;
    private final Label donutCenterValue;
    private final Label donutCenterLabel;
    private final Label donutCenterSublabel;

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

        // Gráfico 1: BarChart Recaudo vs Proyección Semanal con eje Y compacto y badge
        VBox barCard = new VBox(8);
        barCard.getStyleClass().add("card");
        HBox.setHgrow(barCard, Priority.ALWAYS);

        HBox barHeader = new HBox(8);
        barHeader.getStyleClass().add("card-header-row");
        Label barTitle = new Label("Recaudo vs. Proyección Semanal");
        barTitle.getStyleClass().add("card-title");
        Region barSpacer = new Region();
        HBox.setHgrow(barSpacer, Priority.ALWAYS);
        this.barBadgeCumplimiento = new Label("Cumplimiento: 0%");
        this.barBadgeCumplimiento.getStyleClass().addAll("badge", "badge-info");
        barHeader.getChildren().addAll(barTitle, barSpacer, barBadgeCumplimiento);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override
            public String toString(Number object) {
                return Formatters.formatoMonedaCompacta(object);
            }

            @Override
            public Number fromString(String string) {
                return 0;
            }
        });

        this.barChart = new BarChart<>(xAxis, yAxis);
        this.barChart.setAnimated(false);
        this.barChart.setLegendVisible(true);
        this.barChart.setMinHeight(260);
        this.barChart.getStyleClass().add("chart-credinoda");

        this.barEmptyLabel = new Label("Sin cuotas ni recaudos registrados en este período");
        this.barEmptyLabel.getStyleClass().add("section-subtitle");
        this.barEmptyLabel.setVisible(false);

        StackPane barStack = new StackPane(barChart, barEmptyLabel);
        VBox.setVgrow(barStack, Priority.ALWAYS);
        barCard.getChildren().addAll(barHeader, barStack);

        // Gráfico 2: PieChart transformado en Donut Chart con centro informativo
        VBox pieCard = new VBox(8);
        pieCard.getStyleClass().add("card");
        pieCard.setPrefWidth(390);
        pieCard.setMinWidth(330);

        HBox pieHeader = new HBox(8);
        pieHeader.getStyleClass().add("card-header-row");
        Label pieTitle = new Label("Distribución de Cartera");
        pieTitle.getStyleClass().add("card-title");
        Region pieSpacer = new Region();
        HBox.setHgrow(pieSpacer, Priority.ALWAYS);
        this.pieBadgeTotal = new Label("0 créditos");
        this.pieBadgeTotal.getStyleClass().addAll("badge", "badge-info");
        pieHeader.getChildren().addAll(pieTitle, pieSpacer, pieBadgeTotal);

        this.pieChart = new PieChart();
        this.pieChart.setAnimated(false);
        this.pieChart.setLabelsVisible(true);
        this.pieChart.setMinHeight(260);
        this.pieChart.getStyleClass().add("chart-credinoda");

        this.pieEmptyLabel = new Label("Sin créditos en este período");
        this.pieEmptyLabel.getStyleClass().add("section-subtitle");
        this.pieEmptyLabel.setVisible(false);

        this.donutCenterValue = new Label("0");
        this.donutCenterValue.getStyleClass().add("donut-center-value");

        this.donutCenterLabel = new Label("Créditos");
        this.donutCenterLabel.getStyleClass().add("donut-center-label");

        this.donutCenterSublabel = new Label("100% Cartera");
        this.donutCenterSublabel.getStyleClass().add("donut-center-sublabel");

        this.donutCenter = new VBox(2, donutCenterValue, donutCenterLabel, donutCenterSublabel);
        this.donutCenter.getStyleClass().add("donut-center");
        this.donutCenter.setAlignment(Pos.CENTER);
        this.donutCenter.setMouseTransparent(true);
        this.donutCenter.setVisible(false);

        StackPane pieStack = new StackPane(pieChart, donutCenter, pieEmptyLabel);
        VBox.setVgrow(pieStack, Priority.ALWAYS);
        pieCard.getChildren().addAll(pieHeader, pieStack);

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
            barBadgeCumplimiento.setText("Sin proyección");
            barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-info");
            return;
        }

        barChart.setVisible(true);
        barEmptyLabel.setVisible(false);

        BigDecimal totalProyectado = BigDecimal.ZERO;
        BigDecimal totalRecaudado = BigDecimal.ZERO;

        XYChart.Series<String, Number> serieProyectado = new XYChart.Series<>();
        serieProyectado.setName("Proyectado");

        XYChart.Series<String, Number> serieRecaudado = new XYChart.Series<>();
        serieRecaudado.setName("Recaudado");

        for (ProyeccionSemanalItem item : items) {
            String etiquetaEjeX = "Sem. " + item.semanaIndex() + "\n" + item.etiqueta();
            totalProyectado = totalProyectado.add(item.proyectado());
            totalRecaudado = totalRecaudado.add(item.recaudado());

            XYChart.Data<String, Number> dataProy = new XYChart.Data<>(etiquetaEjeX, item.proyectado());
            XYChart.Data<String, Number> dataRec = new XYChart.Data<>(etiquetaEjeX, item.recaudado());

            serieProyectado.getData().add(dataProy);
            serieRecaudado.getData().add(dataRec);

            // Instalar tooltips interactivos enriquecidos
            String tooltipProyText = String.format(
                    "Semana %d: %s\nTipo: Proyección esperada\nMonto: %s\nTotal cuotas por vencer en el período",
                    item.semanaIndex(), item.etiqueta(), Formatters.formatoMoneda(item.proyectado())
            );
            instalarTooltip(dataProy, tooltipProyText);

            BigDecimal pctSemana = BigDecimal.ZERO;
            if (item.proyectado().compareTo(BigDecimal.ZERO) > 0) {
                pctSemana = item.recaudado().multiply(BigDecimal.valueOf(100))
                        .divide(item.proyectado(), 1, RoundingMode.HALF_UP);
            }
            String tooltipRecText = String.format(
                    "Semana %d: %s\nTipo: Recaudo cobrado\nMonto: %s\nCumplimiento: %s%% del proyectado (%s)",
                    item.semanaIndex(), item.etiqueta(), Formatters.formatoMoneda(item.recaudado()),
                    pctSemana.toPlainString().replace('.', ','), Formatters.formatoMoneda(item.proyectado())
            );
            instalarTooltip(dataRec, tooltipRecText);
        }

        barChart.getData().addAll(List.of(serieProyectado, serieRecaudado));

        // Calcular y asignar badge de cumplimiento global
        if (totalProyectado.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pctGlobal = totalRecaudado.multiply(BigDecimal.valueOf(100))
                    .divide(totalProyectado, 1, RoundingMode.HALF_UP);
            String pctStr = pctGlobal.toPlainString().replace('.', ',') + "%";
            barBadgeCumplimiento.setText("Cumplimiento: " + pctStr);
            if (pctGlobal.compareTo(BigDecimal.valueOf(90)) >= 0) {
                barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-activo");
            } else if (pctGlobal.compareTo(BigDecimal.valueOf(70)) >= 0) {
                barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-parcial");
            } else {
                barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-mora");
            }
        } else if (totalRecaudado.compareTo(BigDecimal.ZERO) > 0) {
            barBadgeCumplimiento.setText("Cumplimiento: 100% (Anticipado)");
            barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-activo");
        } else {
            barBadgeCumplimiento.setText("Sin proyección");
            barBadgeCumplimiento.getStyleClass().setAll("badge", "badge-info");
        }
    }

    private void instalarTooltip(XYChart.Data<String, Number> data, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.getStyleClass().add("chart-tooltip");
        if (data.getNode() != null) {
            Tooltip.install(data.getNode(), tooltip);
        }
        data.nodeProperty().addListener((obs, oldNode, newNode) -> {
            if (newNode != null) {
                Tooltip.install(newNode, tooltip);
            }
        });
    }

    private void updatePieChart(DistribucionCartera dist) {
        pieChart.getData().clear();
        if (dist.total() <= 0) {
            pieChart.setVisible(false);
            pieEmptyLabel.setVisible(true);
            donutCenter.setVisible(false);
            pieBadgeTotal.setText("0 créditos");
            pieBadgeTotal.getStyleClass().setAll("badge", "badge-info");
            return;
        }

        pieChart.setVisible(true);
        pieEmptyLabel.setVisible(false);
        donutCenter.setVisible(true);

        donutCenterValue.setText(String.valueOf(dist.total()));
        donutCenterLabel.setText(dist.total() == 1 ? "Crédito" : "Créditos");
        pieBadgeTotal.setText("Total: " + dist.total() + " créditos");
        pieBadgeTotal.getStyleClass().setAll("badge", "badge-info");

        List<PieChart.Data> slices = new ArrayList<>();

        if (dist.alDia() > 0) {
            int pct = (int) Math.round((dist.alDia() * 100.0) / dist.total());
            PieChart.Data sliceAlDia = new PieChart.Data("Al Día: " + dist.alDia() + " (" + pct + "%)", dist.alDia());
            slices.add(sliceAlDia);
            String tt = String.format("Categoría: Cartera Al Día\nCantidad: %d créditos (%d%%)\nEstado: Créditos al corriente sin mora activa",
                    dist.alDia(), pct);
            configurarSectorDonut(sliceAlDia, "#166534", tt);
        }

        if (dist.enMora() > 0) {
            int pct = (int) Math.round((dist.enMora() * 100.0) / dist.total());
            PieChart.Data sliceMora = new PieChart.Data("En Mora: " + dist.enMora() + " (" + pct + "%)", dist.enMora());
            slices.add(sliceMora);
            String tt = String.format("Categoría: Cartera En Mora\nCantidad: %d créditos (%d%%)\nEstado: Requiere gestión de cobranza inmediata",
                    dist.enMora(), pct);
            configurarSectorDonut(sliceMora, "#B91C1C", tt);
        }

        if (dist.saldados() > 0) {
            int pct = (int) Math.round((dist.saldados() * 100.0) / dist.total());
            PieChart.Data sliceSaldados = new PieChart.Data("Saldados: " + dist.saldados() + " (" + pct + "%)", dist.saldados());
            slices.add(sliceSaldados);
            String tt = String.format("Categoría: Créditos Saldados\nCantidad: %d créditos (%d%%)\nEstado: Totalmente liquidados y cancelados",
                    dist.saldados(), pct);
            configurarSectorDonut(sliceSaldados, "#2563EB", tt);
        }

        pieChart.setData(FXCollections.observableArrayList(slices));
    }

    private void configurarSectorDonut(PieChart.Data slice, String colorHex, String tooltipText) {
        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.getStyleClass().add("chart-tooltip");

        Runnable apply = () -> {
            Node node = slice.getNode();
            if (node != null) {
                node.setStyle("-fx-pie-color: " + colorHex + ";");
                Tooltip.install(node, tooltip);
            }
        };

        if (slice.getNode() != null) {
            apply.run();
        }
        slice.nodeProperty().addListener((obs, oldN, newN) -> {
            if (newN != null) {
                apply.run();
            }
        });
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

    public Label getBarBadgeCumplimiento() {
        return barBadgeCumplimiento;
    }

    public Label getBarEmptyLabel() {
        return barEmptyLabel;
    }

    public PieChart getPieChart() {
        return pieChart;
    }

    public Label getPieBadgeTotal() {
        return pieBadgeTotal;
    }

    public Label getPieEmptyLabel() {
        return pieEmptyLabel;
    }

    public VBox getDonutCenter() {
        return donutCenter;
    }

    public Label getDonutCenterValue() {
        return donutCenterValue;
    }

    public Label getDonutCenterLabel() {
        return donutCenterLabel;
    }
}
