package com.credinoda.ui.views;

import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.service.ClienteService;
import com.credinoda.service.CreditoService;
import com.credinoda.ui.components.TicketLiquidacionCard;
import javafx.application.Platform;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para la vista {@link CreditoNewView} y el recibo térmico
 * {@link TicketLiquidacionCard} (Fase 6.5, CU-005, RN-004 a RN-007).
 */
class CreditoNewViewTest {

    private Connection connection;
    private ClienteService clienteService;
    private CreditoService creditoService;

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
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Fase 6.5: Simulación reactiva actualiza ticket térmico con desglose porcentual, código de barras e índices limpios")
    void debeSimularCreditoYActualizarTicketLiquidacion() {
        Cliente cliente = clienteService.crearCliente("Carlos Moncada", "3001234567", "10203040");

        CreditoNewView view = new CreditoNewView(clienteService, creditoService, null, null);
        view.preselectCliente(cliente.getId());

        // Ingresar condiciones: $1.000.000 a 1 mes al 12% semanal
        view.getCapitalField().setText("1.000.000");
        view.getPlazoField().setText("1");
        view.getTasaPorcentajeField().setText("12");
        view.getFrecuenciaCombo().setValue(FrecuenciaPago.SEMANAL);

        TicketLiquidacionCard ticket = view.getTicketLiquidacionCard();
        assertThat(ticket).isNotNull();

        // Validar desglose en ticket térmico
        assertThat(ticket.getCapitalText()).isEqualTo("$1.000.000");
        assertThat(ticket.getInteresesLabelText()).isEqualTo("Total de Intereses (12%)");
        assertThat(ticket.getInteresesValueText()).isEqualTo("$120.000");
        assertThat(ticket.getNumeroCuotasText()).isEqualTo("4 semanales");
        assertThat(ticket.getCuotaBaseText()).isEqualTo("$280.000");
        assertThat(ticket.getTotalPagarText()).isEqualTo("$1.120.000");

        // Validar código de barras generado
        assertThat(ticket.getBarcodeImageView().getImage()).isNotNull();
        assertThat(ticket.getBarcodeImageView().getImage().getWidth()).isGreaterThan(0);
        assertThat(ticket.getBarcodeImageView().getImage().getHeight()).isGreaterThan(0);

        // Validar tabla de previsualización (4 cuotas de $280.000 con numeración limpia)
        assertThat(view.getItemsSimulacion()).hasSize(4);
        TableColumn<Cuota, ?> colNum = view.getTablaSimulacion().getColumns().get(0);
        assertThat(colNum.getText()).isEqualTo("N° Cuota");
        assertThat(colNum.getCellObservableValue(0).getValue()).isEqualTo("1");
        assertThat(colNum.getCellObservableValue(3).getValue()).isEqualTo("4");
    }

    @Test
    @DisplayName("Fase 6.5: Valida ajuste exacto de centavos en la última cuota y visualización limpia de 12 cuotas (VP-004)")
    void debeAjustarCentavosEnUltimaCuotaYSepararIndices() {
        CreditoNewView view = new CreditoNewView(clienteService, creditoService, null, null);

        // $100.000 a 3 meses al 12% mensual semanal (12 cuotas) -> Total a pagar $136.000
        view.getCapitalField().setText("100.000");
        view.getPlazoField().setText("3");
        view.getTasaPorcentajeField().setText("12");
        view.getFrecuenciaCombo().setValue(FrecuenciaPago.SEMANAL);

        TicketLiquidacionCard ticket = view.getTicketLiquidacionCard();
        assertThat(ticket.getCapitalText()).isEqualTo("$100.000");
        assertThat(ticket.getInteresesLabelText()).isEqualTo("Total de Intereses (12%)");
        assertThat(ticket.getInteresesValueText()).isEqualTo("$36.000");
        assertThat(ticket.getNumeroCuotasText()).isEqualTo("12 semanales");
        assertThat(ticket.getTotalPagarText()).isEqualTo("$136.000");

        // Cuotas C1 a C11 por $11.333,33 y C12 por $11.333,37
        assertThat(view.getItemsSimulacion()).hasSize(12);
        assertThat(view.getItemsSimulacion().get(0).getMonto()).isEqualByComparingTo(new BigDecimal("11333.33"));
        assertThat(view.getItemsSimulacion().get(11).getMonto()).isEqualByComparingTo(new BigDecimal("11333.37"));

        // Verificar suma exacta igual al total a pagar
        BigDecimal suma = view.getItemsSimulacion().stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(suma).isEqualByComparingTo(new BigDecimal("136000.00"));

        TableColumn<Cuota, ?> colNum = view.getTablaSimulacion().getColumns().get(0);
        assertThat(colNum.getCellObservableValue(11).getValue()).isEqualTo("12");
    }

    @Test
    @DisplayName("Fase 6.5: Limpia el ticket térmico y el cronograma ante valores vacíos o inválidos")
    void debeLimpiarTicketYTablaAnteEntradasInvalidas() {
        CreditoNewView view = new CreditoNewView(clienteService, creditoService, null, null);

        view.getCapitalField().setText("500.000");
        view.getPlazoField().setText("1");
        assertThat(view.getItemsSimulacion()).isNotEmpty();

        // Borrar capital
        view.getCapitalField().setText("");
        TicketLiquidacionCard ticket = view.getTicketLiquidacionCard();
        assertThat(ticket.getCapitalText()).isEqualTo("$0");
        assertThat(ticket.getInteresesLabelText()).isEqualTo("Total de Intereses (0%)");
        assertThat(ticket.getNumeroCuotasText()).isEqualTo("0");
        assertThat(ticket.getTotalPagarText()).isEqualTo("$0");
        assertThat(view.getItemsSimulacion()).isEmpty();
    }
}
