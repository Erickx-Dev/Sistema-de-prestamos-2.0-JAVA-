package com.credinoda.ui.views;

import com.credinoda.domain.dto.ProximoPagoItem;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.service.PagoService;
import javafx.application.Platform;
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
 * Pruebas unitarias e integración para {@link DashboardView} (CU-012, RN-010, RN-013).
 */
class DashboardViewTest {

    private Connection connection;
    private ClienteService clienteService;
    private CreditoService creditoService;
    private PagoService pagoService;

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
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Calcula las 3 tarjetas KPI de cartera activa ignorando créditos liquidados (PAGADO) — RN-013")
    void debeCalcularTarjetasDeCarteraActivaIgnorandoCreditosPagados() {
        Cliente cliente = clienteService.crearCliente("Carlos Mendoza", "3001112233", "1098765432");

        // Crédito 1: $1.000.000 a 1 mes Quincenal (10% -> Total $1.100.000, 2 cuotas de $550.000), liquidado al 100%
        Credito creditoLiquidado = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.10"),
                1,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );
        List<Cuota> cuotasCred1 = creditoService.obtenerCuotasCredito(creditoLiquidado.getId());
        for (Cuota c : cuotasCred1) {
            pagoService.registrarPago(creditoLiquidado.getId(), c.getId(), c.getMonto(), LocalDate.now());
        }
        assertThat(creditoService.obtenerCreditoPorId(creditoLiquidado.getId()).getEstado())
                .isEqualTo(EstadoCredito.PAGADO);

        // Crédito 2: $500.000 a 1 mes Quincenal (10% -> Total $550.000, 2 cuotas de $275.000), se abona solo la cuota 1
        Credito creditoActivo = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("500000"),
                new BigDecimal("0.10"),
                1,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );
        List<Cuota> cuotasCred2 = creditoService.obtenerCuotasCredito(creditoActivo.getId());
        pagoService.registrarPago(creditoActivo.getId(), cuotasCred2.get(0).getId(), new BigDecimal("275000"), LocalDate.now());

        DashboardView dashboardView = new DashboardView(creditoService, null, null);
        dashboardView.onShow(1);

        // Solo el Crédito 2 está ACTIVO: Capital = $500.000, Saldo Pendiente = $275.000
        assertThat(dashboardView.getCardCreditosActivos().getValue()).isEqualTo("1");
        assertThat(dashboardView.getCardCapitalPrestado().getValue()).isEqualTo("$500.000");
        assertThat(dashboardView.getCardSaldoPendiente().getValue()).isEqualTo("$275.000");

        // En próximos pagos solo debe aparecer la cuota #2 pendiente del crédito activo
        assertThat(dashboardView.getItemsProximosPagos()).hasSize(1);
        assertThat(dashboardView.getItemsProximosPagos().get(0).creditoId()).isEqualTo(creditoActivo.getId());
        assertThat(dashboardView.getItemsProximosPagos().get(0).numeroCuota()).isEqualTo(2);
    }

    @Test
    @DisplayName("Muestra las próximas cuotas ordenadas por vencimiento e incluye el sufijo (EN MORA) en cuotas vencidas")
    void debeMostrarProximasCuotasConEtiquetaDeMoraCuandoVencieron() {
        Cliente cliente = clienteService.crearCliente("Ana Lucía Torres", "3159998877", "52443322");

        // Crear crédito iniciado hace 20 días para que las primeras cuotas semanales estén vencidas
        Credito credito = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("400000"),
                new BigDecimal("0.10"),
                1,
                FrecuenciaPago.SEMANAL,
                LocalDate.now().minusDays(20)
        );
        creditoService.actualizarCuotasEnMora(LocalDate.now());

        DashboardView dashboardView = new DashboardView(creditoService, null, null);
        dashboardView.loadData();

        List<ProximoPagoItem> items = dashboardView.getItemsProximosPagos();
        assertThat(items).hasSize(4);
        assertThat(items.get(0).creditoId()).isEqualTo(credito.getId());
        assertThat(items.get(0).enMora()).isTrue();
        assertThat(items.get(0).etiquetaEstado()).isEqualTo("PENDIENTE (EN MORA)");
    }
}
