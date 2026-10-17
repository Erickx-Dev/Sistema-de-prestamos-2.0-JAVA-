package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.service.PagoService;
import com.credinoda.service.ReporteService;
import javafx.application.Platform;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias e integración para los componentes analíticos vectoriales enriquecidos
 * de {@link ReportesView} (Fase 6.1, CU-013, RN-014).
 */
class ReportesViewTest {

    private Connection connection;
    private ClienteService clienteService;
    private CreditoService creditoService;
    private PagoService pagoService;
    private ReporteService reporteService;

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);

        ClienteRepository clienteRepo = new ClienteRepository(connection);
        CreditoRepository creditoRepo = new CreditoRepository(connection);
        PagoRepository pagoRepo = new PagoRepository(connection);

        clienteService = new ClienteService(clienteRepo, creditoRepo);
        creditoService = new CreditoService(connection, creditoRepo, clienteRepo, pagoRepo);
        pagoService = new PagoService(connection, pagoRepo, creditoRepo, creditoService);
        reporteService = new ReporteService(creditoRepo, pagoRepo);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Fase 6.1: BarChart actualiza 2 series, etiquetas descriptivas y badge de cumplimiento")
    void debeActualizarBarChartConSeriesEjeXDescriptivoYBadgeCumplimiento() {
        Cliente cliente = clienteService.crearCliente("Mariana Gómez", "3112223344", "1020304050");

        // Crear crédito vigente con cuotas en el período
        Credito credito = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.10"),
                1,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );
        List<Cuota> cuotas = creditoService.obtenerCuotasCredito(credito.getId());

        // Pagar la cuota 1 en su totalidad
        pagoService.registrarPago(credito.getId(), cuotas.get(0).getId(), cuotas.get(0).getMonto(), LocalDate.now());

        ReportesView reportesView = new ReportesView(reporteService, null);
        reportesView.loadData(null, null);

        BarChart<String, Number> barChart = reportesView.getBarChart();
        assertThat(barChart.isVisible()).isTrue();
        assertThat(reportesView.getBarEmptyLabel().isVisible()).isFalse();

        // 2 series institucionales: Proyectado y Recaudado
        assertThat(barChart.getData()).hasSize(2);
        XYChart.Series<String, Number> serieProy = barChart.getData().get(0);
        XYChart.Series<String, Number> serieRec = barChart.getData().get(1);
        assertThat(serieProy.getName()).isEqualTo("Proyectado");
        assertThat(serieRec.getName()).isEqualTo("Recaudado");

        // Cada punto contiene la etiqueta descriptiva "Sem. X\n..."
        assertThat(serieProy.getData()).isNotEmpty();
        assertThat(serieProy.getData().get(0).getXValue()).startsWith("Sem. ");

        // Badge de cumplimiento presente en la cabecera
        assertThat(reportesView.getBarBadgeCumplimiento().getText()).startsWith("Cumplimiento: ");
        assertThat(reportesView.getBarBadgeCumplimiento().getStyleClass()).contains("badge");
    }

    @Test
    @DisplayName("Fase 6.1: Donut Chart genera centro informativo, conteo exacto de créditos y sectores semánticos")
    void debeTransformarPieChartADonutConCentroInformativoYColoresSemanticos() {
        Cliente cliente = clienteService.crearCliente("Roberto Duarte", "3007778899", "987654321");

        // 1. Crédito Saldado al 100%
        Credito cSaldado = creditoService.crearCredito(
                cliente.getId(), new BigDecimal("500000"), new BigDecimal("0.10"), 1, FrecuenciaPago.QUINCENAL, LocalDate.now());
        for (Cuota q : creditoService.obtenerCuotasCredito(cSaldado.getId())) {
            pagoService.registrarPago(cSaldado.getId(), q.getId(), q.getMonto(), LocalDate.now());
        }

        // 2. Crédito Al Día
        creditoService.crearCredito(
                cliente.getId(), new BigDecimal("800000"), new BigDecimal("0.10"), 1, FrecuenciaPago.QUINCENAL, LocalDate.now());

        // 3. Crédito En Mora (iniciado hace 25 días)
        creditoService.crearCredito(
                cliente.getId(), new BigDecimal("600000"), new BigDecimal("0.10"), 1, FrecuenciaPago.SEMANAL, LocalDate.now().minusDays(25));
        creditoService.actualizarCuotasEnMora(LocalDate.now());

        ReportesView reportesView = new ReportesView(reporteService, null);
        reportesView.loadData(null, null);

        PieChart pieChart = reportesView.getPieChart();
        assertThat(pieChart.isVisible()).isTrue();
        assertThat(reportesView.getPieEmptyLabel().isVisible()).isFalse();

        // Centro Donut flotante activo con conteo total de 3 créditos
        assertThat(reportesView.getDonutCenter().isVisible()).isTrue();
        assertThat(reportesView.getDonutCenterValue().getText()).isEqualTo("3");
        assertThat(reportesView.getDonutCenterLabel().getText()).isEqualTo("Créditos");
        assertThat(reportesView.getPieBadgeTotal().getText()).isEqualTo("Total: 3 créditos");

        // 3 sectores: Al Día, En Mora, Saldados con sus porcentajes calculados
        List<PieChart.Data> slices = pieChart.getData();
        assertThat(slices).hasSize(3);

        boolean tieneAlDia = slices.stream().anyMatch(s -> s.getName().startsWith("Al Día: 1 (33%)"));
        boolean tieneEnMora = slices.stream().anyMatch(s -> s.getName().startsWith("En Mora: 1 (33%)"));
        boolean tieneSaldados = slices.stream().anyMatch(s -> s.getName().startsWith("Saldados: 1 (33%)"));

        assertThat(tieneAlDia).isTrue();
        assertThat(tieneEnMora).isTrue();
        assertThat(tieneSaldados).isTrue();
    }

    @Test
    @DisplayName("Fase 6.1: Donut Chart preserva integridad cuando solo existe una categoría de cartera")
    void debeConservarIntegridadCuandoSoloExisteUnaCategoria() {
        Cliente cliente = clienteService.crearCliente("Elena Vega", "3204445566", "43218765");

        // Solo 1 crédito activo Al Día
        creditoService.crearCredito(
                cliente.getId(), new BigDecimal("1200000"), new BigDecimal("0.12"), 2, FrecuenciaPago.QUINCENAL, LocalDate.now());

        ReportesView reportesView = new ReportesView(reporteService, null);
        reportesView.loadData(null, null);

        assertThat(reportesView.getDonutCenter().isVisible()).isTrue();
        assertThat(reportesView.getDonutCenterValue().getText()).isEqualTo("1");
        assertThat(reportesView.getDonutCenterLabel().getText()).isEqualTo("Crédito");

        List<PieChart.Data> slices = reportesView.getPieChart().getData();
        assertThat(slices).hasSize(1);
        assertThat(slices.get(0).getName()).isEqualTo("Al Día: 1 (100%)");
    }

    @Test
    @DisplayName("Fase 6.1: Despliega estados vacíos y badges neutros cuando no hay datos en el período")
    void debeManejarEstadoVacioSinDatos() {
        ReportesView reportesView = new ReportesView(reporteService, null);
        reportesView.loadData(null, null);

        assertThat(reportesView.getBarChart().isVisible()).isFalse();
        assertThat(reportesView.getBarEmptyLabel().isVisible()).isTrue();
        assertThat(reportesView.getBarBadgeCumplimiento().getText()).isEqualTo("Sin proyección");

        assertThat(reportesView.getPieChart().isVisible()).isFalse();
        assertThat(reportesView.getDonutCenter().isVisible()).isFalse();
        assertThat(reportesView.getPieEmptyLabel().isVisible()).isTrue();
        assertThat(reportesView.getPieBadgeTotal().getText()).isEqualTo("0 créditos");
    }

    @Test
    @DisplayName("Fase 6.1: Formateador de moneda compacta opera coherentemente sobre valores numéricos")
    void debeVerificarFormatoMonedaCompactaEnEjeY() {
        assertThat(Formatters.formatoMonedaCompacta(0)).isEqualTo("$0");
        assertThat(Formatters.formatoMonedaCompacta(500000)).isEqualTo("$500k");
        assertThat(Formatters.formatoMonedaCompacta(1000000)).isEqualTo("$1M");
        assertThat(Formatters.formatoMonedaCompacta(1500000)).isEqualTo("$1,5M");
    }
}
