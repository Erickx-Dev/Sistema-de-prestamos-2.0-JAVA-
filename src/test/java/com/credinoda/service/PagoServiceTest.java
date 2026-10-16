package com.credinoda.service;

import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.exception.CreditoNoEncontradoException;
import com.credinoda.domain.exception.CuotaNoEncontradaException;
import com.credinoda.domain.exception.MontoInvalidoException;
import com.credinoda.domain.exception.PagoInvalidoException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Pago;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas unitarias de PagoService (Vectores VP-007, VP-008, RN-009, RN-011)")
class PagoServiceTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;
    private CreditoService creditoService;
    private PagoService pagoService;

    private Cliente clientePrueba;
    private Credito creditoPrueba;
    private List<Cuota> cuotas;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        pagoRepository = new PagoRepository(connection);
        creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository);
        pagoService = new PagoService(connection, pagoRepository, creditoRepository, creditoService);

        clientePrueba = clienteRepository.crear(new Cliente("Mariana Restrepo", "3004445566", "CC778899"));
        // Crédito de 1.000.000 a 1 mes quincenal (2 cuotas de 560.000 c/u, Total = 1.120.000)
        creditoPrueba = creditoService.crearCredito(
                clientePrueba.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.12"),
                1,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );
        cuotas = creditoService.obtenerCuotasCredito(creditoPrueba.getId());
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("VP-007: Abonos parciales y liquidación completa de cuota (RN-009, RN-011)")
    void testVectorVP007AbonosParcialesYLiquidacion() throws Exception {
        Cuota cuota1 = cuotas.get(0);
        assertThat(cuota1.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
        assertThat(cuota1.getMonto()).isEqualByComparingTo("560000.00");

        // Primer abono parcial: 200.000 -> Estado debe pasar a PARCIAL
        Pago pago1 = pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("200000.00"), LocalDate.now());
        assertThat(pago1.getId()).isNotNull();

        Cuota c1Actualizada = creditoRepository.obtenerCuotaPorId(cuota1.getId()).orElseThrow();
        assertThat(c1Actualizada.getEstado()).isEqualTo(EstadoCuota.PARCIAL);
        assertThat(pagoService.calcularSaldoPendienteCuota(cuota1.getId())).isEqualByComparingTo("360000.00");

        // Segundo abono de liquidación: 360.000 -> Estado debe pasar a PAGADA
        Pago pago2 = pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("360000.00"), LocalDate.now());
        assertThat(pago2.getId()).isNotNull();

        Cuota c1Liquidada = creditoRepository.obtenerCuotaPorId(cuota1.getId()).orElseThrow();
        assertThat(c1Liquidada.getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(pagoService.calcularSaldoPendienteCuota(cuota1.getId())).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("VP-008: Prevención estricta de sobrepago por 1 centavo adicional (RN-009)")
    void testVectorVP008PrevencionSobrepagoUnCentavo() {
        Cuota cuota1 = cuotas.get(0);

        // Abono previo de 200.000 -> Saldo restante = 360.000.00
        pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("200000.00"), LocalDate.now());

        // Intentar pagar 360.000,01 (saldo + 0.01) debe ser estrictamente rechazado
        BigDecimal montoExcedido = new BigDecimal("360000.01");
        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), montoExcedido, LocalDate.now()))
                .isInstanceOf(MontoInvalidoException.class)
                .hasMessageContaining("El monto excede el saldo pendiente de la cuota.");

        // Intentar pagar directamente el monto total de la cuota (560.000) también debe ser rechazado
        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("560000.00"), LocalDate.now()))
                .isInstanceOf(MontoInvalidoException.class)
                .hasMessageContaining("El monto excede el saldo pendiente de la cuota.");
    }

    @Test
    @DisplayName("Debe rechazar pagos con monto menor o igual a cero (RN-009)")
    void testRechazoMontoMenorOIgualACero() {
        Cuota cuota1 = cuotas.get(0);

        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), BigDecimal.ZERO, LocalDate.now()))
                .isInstanceOf(MontoInvalidoException.class)
                .hasMessageContaining("El monto del pago debe ser mayor a 0.");

        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("-50000"), LocalDate.now()))
                .isInstanceOf(MontoInvalidoException.class);
    }

    @Test
    @DisplayName("Debe rechazar pagos a créditos o cuotas inexistentes")
    void testRechazoEntidadesInexistentes() {
        Cuota cuota1 = cuotas.get(0);

        assertThatThrownBy(() -> pagoService.registrarPago(9999, cuota1.getId(), new BigDecimal("10000"), LocalDate.now()))
                .isInstanceOf(CreditoNoEncontradoException.class);

        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), 9999, new BigDecimal("10000"), LocalDate.now()))
                .isInstanceOf(CuotaNoEncontradaException.class);
    }

    @Test
    @DisplayName("Debe rechazar pago si la cuota pertenece a otro crédito (RN-009)")
    void testRechazoCuotaDeOtroCredito() {
        // Crear un segundo crédito
        Credito otroCredito = creditoService.crearCredito(
                clientePrueba.getId(), new BigDecimal("200000"), new BigDecimal("0.12"), 1,
                FrecuenciaPago.QUINCENAL, LocalDate.now()
        );
        Cuota cuotaOtroCredito = creditoService.obtenerCuotasCredito(otroCredito.getId()).get(0);

        // Intentar pagar cuota del segundo crédito bajo el ID del primer crédito
        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuotaOtroCredito.getId(), new BigDecimal("10000"), LocalDate.now()))
                .isInstanceOf(PagoInvalidoException.class)
                .hasMessageContaining("La cuota no pertenece al crédito especificado.");
    }

    @Test
    @DisplayName("Debe rechazar pagos adicionales si la cuota ya está PAGADA (RN-009)")
    void testRechazoPagoACuotaPagada() {
        Cuota cuota1 = cuotas.get(0);
        // Liquidar cuota 1
        pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), cuota1.getMonto(), LocalDate.now());

        // Intentar pagar de nuevo sobre la cuota ya liquidada
        assertThatThrownBy(() -> pagoService.registrarPago(creditoPrueba.getId(), cuota1.getId(), new BigDecimal("5000"), LocalDate.now()))
                .isInstanceOf(PagoInvalidoException.class)
                .hasMessageContaining("La cuota ya está pagada.");
    }

    @Test
    @DisplayName("Debe permitir libertad de orden en el pago de cuotas (RN-009)")
    void testLibertadDeOrdenDeCuotas() throws Exception {
        Cuota cuota2 = cuotas.get(1); // Cuota #2
        // Abonar a la cuota #2 estando la cuota #1 aún PENDIENTE
        Pago pago = pagoService.registrarPago(creditoPrueba.getId(), cuota2.getId(), new BigDecimal("100000"), LocalDate.now());

        assertThat(pago).isNotNull();
        Cuota c2Actualizada = creditoRepository.obtenerCuotaPorId(cuota2.getId()).orElseThrow();
        assertThat(c2Actualizada.getEstado()).isEqualTo(EstadoCuota.PARCIAL);

        Cuota c1SiguePendiente = creditoRepository.obtenerCuotaPorId(cuotas.get(0).getId()).orElseThrow();
        assertThat(c1SiguePendiente.getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
    }

    @Test
    @DisplayName("Debe transicionar el crédito a PAGADO al liquidar todas sus cuotas (RN-011)")
    void testLiquidacionCompletaPasaCreditoAPagado() {
        Cuota c1 = cuotas.get(0);
        Cuota c2 = cuotas.get(1);

        pagoService.registrarPago(creditoPrueba.getId(), c1.getId(), c1.getMonto(), LocalDate.now());
        assertThat(creditoService.obtenerCreditoPorId(creditoPrueba.getId()).getEstado()).isEqualTo(EstadoCredito.ACTIVO);

        pagoService.registrarPago(creditoPrueba.getId(), c2.getId(), c2.getMonto(), LocalDate.now());
        assertThat(creditoService.obtenerCreditoPorId(creditoPrueba.getId()).getEstado()).isEqualTo(EstadoCredito.PAGADO);
    }

    @Test
    @DisplayName("Debe resolver datos de comprobante para impresión y QR (CU-011, RN-012)")
    void testObtenerDatosComprobante() {
        Cuota c1 = cuotas.get(0);
        Pago pago = pagoService.registrarPago(creditoPrueba.getId(), c1.getId(), new BigDecimal("200000"), LocalDate.now());

        DatosComprobante datos = pagoService.obtenerDatosComprobante(pago.getId());

        assertThat(datos).isNotNull();
        assertThat(datos.numeroRecibo()).isEqualTo(String.format("REC-%05d", pago.getId()));
        assertThat(datos.clienteNombre()).isEqualTo("Mariana Restrepo");
        assertThat(datos.clienteDocumento()).isEqualTo("CC778899");
        assertThat(datos.pagoMonto()).isEqualByComparingTo("200000");
        assertThat(datos.saldoCuotaAntes()).isEqualByComparingTo("560000");
        assertThat(datos.saldoCuotaDespues()).isEqualByComparingTo("360000");
    }
}
