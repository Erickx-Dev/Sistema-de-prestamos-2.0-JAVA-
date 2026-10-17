package com.credinoda.ui.views;

import com.credinoda.domain.dto.PagoDetalleItem;
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
 * Pruebas unitarias e integración para la heurística de búsqueda manual de {@link PagosView} (CU-010, RN-016).
 */
class PagosViewFilterTest {

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
    @DisplayName("Filtro numérico exacto ('4' vs '#4'): coincide con creditoId=4 o pagoId=4 pero evita falsos positivos con pagoId=14 (RN-016)")
    void debeFiltrarPorNumeroExactoSinFalsosPositivosParciales() {
        List<PagoDetalleItem> muestra = List.of(
                new PagoDetalleItem(4, 1, 1, "María Fernanda", 1, LocalDate.of(2026, 10, 1), new BigDecimal("50000")),
                new PagoDetalleItem(14, 1, 2, "María Fernanda", 2, LocalDate.of(2026, 10, 8), new BigDecimal("50000")),
                new PagoDetalleItem(20, 4, 10, "Pedro Gómez", 1, LocalDate.of(2026, 10, 10), new BigDecimal("80000")),
                new PagoDetalleItem(25, 40, 12, "Laura Ríos", 1, LocalDate.of(2026, 10, 12), new BigDecimal("90000"))
        );

        List<PagoDetalleItem> porNumeroSimple = PagosView.filtrarPagosManual(muestra, "4");
        assertThat(porNumeroSimple)
                .extracting(PagoDetalleItem::pagoId)
                .containsExactly(4, 20)
                .doesNotContain(14, 25);

        List<PagoDetalleItem> porPrefijoHash = PagosView.filtrarPagosManual(muestra, "#4");
        assertThat(porPrefijoHash)
                .extracting(PagoDetalleItem::pagoId)
                .containsExactly(4, 20)
                .doesNotContain(14, 25);
    }

    @Test
    @DisplayName("Filtro textual y por código de recibo ('REC-00014'): localiza por nombre parcial o número de comprobante")
    void debeFiltrarPorNombreDeClienteYCodigoDeRecibo() {
        List<PagoDetalleItem> muestra = List.of(
                new PagoDetalleItem(4, 1, 1, "Erickson Sojo", 1, LocalDate.of(2026, 10, 1), new BigDecimal("50000")),
                new PagoDetalleItem(14, 2, 3, "Andrea Valderrama", 1, LocalDate.of(2026, 10, 8), new BigDecimal("75000"))
        );

        List<PagoDetalleItem> porNombre = PagosView.filtrarPagosManual(muestra, "erickson");
        assertThat(porNombre).hasSize(1);
        assertThat(porNombre.get(0).pagoId()).isEqualTo(4);

        List<PagoDetalleItem> porRecibo = PagosView.filtrarPagosManual(muestra, "REC-00014");
        assertThat(porRecibo).hasSize(1);
        assertThat(porRecibo.get(0).pagoId()).isEqualTo(14);
    }

    @Test
    @DisplayName("PagosView aplica filtro sobre la tabla y restaura todos los registros al pulsar Limpiar")
    void debeAplicarFiltroYRestaurarAlLimpiarEnPagosView() {
        Cliente c1 = clienteService.crearCliente("Erickson Sojo", "3001234567", "1001");
        Cliente c2 = clienteService.crearCliente("Roberto Díaz", "3007654321", "1002");

        Credito cred1 = creditoService.crearCredito(
                c1.getId(), new BigDecimal("200000"), new BigDecimal("0.10"), 1, FrecuenciaPago.QUINCENAL, LocalDate.now());
        Credito cred2 = creditoService.crearCredito(
                c2.getId(), new BigDecimal("300000"), new BigDecimal("0.10"), 1, FrecuenciaPago.QUINCENAL, LocalDate.now());

        Cuota cuota1 = creditoService.obtenerCuotasCredito(cred1.getId()).get(0);
        Cuota cuota2 = creditoService.obtenerCuotasCredito(cred2.getId()).get(0);

        pagoService.registrarPago(cred1.getId(), cuota1.getId(), new BigDecimal("50000"), LocalDate.now());
        pagoService.registrarPago(cred2.getId(), cuota2.getId(), new BigDecimal("60000"), LocalDate.now());

        PagosView pagosView = new PagosView(pagoService, null, null, null);
        pagosView.onShow(1);

        assertThat(pagosView.getItemsPagos()).hasSize(2);

        // Filtrar por nombre "Roberto"
        pagosView.getSearchField().setText("Roberto");
        pagosView.aplicarFiltroBusqueda(pagosView.getSearchField().getText());
        assertThat(pagosView.getItemsPagos()).hasSize(1);
        assertThat(pagosView.getItemsPagos().get(0).clienteNombre()).isEqualTo("Roberto Díaz");

        // Limpiar búsqueda y verificar restauración total
        pagosView.limpiarBusqueda();
        assertThat(pagosView.getSearchField().getText()).isEmpty();
        assertThat(pagosView.getItemsPagos()).hasSize(2);
    }
}
