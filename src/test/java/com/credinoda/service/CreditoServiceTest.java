package com.credinoda.service;

import com.credinoda.domain.exception.ClienteNoEncontradoException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.exception.MontoInvalidoException;
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

@DisplayName("Pruebas unitarias de CreditoService (Vectores VP-001 a VP-006, RN-004 a RN-007)")
class CreditoServiceTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;
    private CreditoService creditoService;
    private Cliente clientePrueba;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        pagoRepository = new PagoRepository(connection);
        creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository);

        clientePrueba = clienteRepository.crear(new Cliente("Cliente Base", "3001234567", "DOC-BASE-01"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("VP-001: Interés simple mensual 1.000.000 al 12% por 1 mes (RN-004)")
    void testVectorVP001InteresSimpleUnMes() {
        BigDecimal capital = new BigDecimal("1000000");
        BigDecimal tasa = new BigDecimal("0.12");
        int plazo = 1;

        BigDecimal intereses = creditoService.calcularIntereses(capital, tasa, plazo);
        BigDecimal total = creditoService.calcularTotalPagar(capital, intereses);

        assertThat(intereses).isEqualByComparingTo("120000.00");
        assertThat(total).isEqualByComparingTo("1120000.00");
    }

    @Test
    @DisplayName("VP-002: Interés simple mensual 1.000.000 al 12% por 3 meses (RN-004)")
    void testVectorVP002InteresSimpleTresMeses() {
        BigDecimal capital = new BigDecimal("1000000");
        BigDecimal tasa = new BigDecimal("0.12");
        int plazo = 3;

        BigDecimal intereses = creditoService.calcularIntereses(capital, tasa, plazo);
        BigDecimal total = creditoService.calcularTotalPagar(capital, intereses);

        assertThat(intereses).isEqualByComparingTo("360000.00");
        assertThat(total).isEqualByComparingTo("1360000.00");
    }

    @Test
    @DisplayName("VP-003: Interés simple mensual 500.000 al 12% por 6 meses (RN-004)")
    void testVectorVP003InteresSimpleSeisMeses() {
        BigDecimal capital = new BigDecimal("500000");
        BigDecimal tasa = new BigDecimal("0.12");
        int plazo = 6;

        BigDecimal intereses = creditoService.calcularIntereses(capital, tasa, plazo);
        BigDecimal total = creditoService.calcularTotalPagar(capital, intereses);

        assertThat(intereses).isEqualByComparingTo("360000.00");
        assertThat(total).isEqualByComparingTo("860000.00");
    }

    @Test
    @DisplayName("Debe rechazar cálculos de interés con capital <= 0, tasa < 0 o plazo <= 0 (RN-004)")
    void testValidacionParametrosInteres() {
        assertThatThrownBy(() -> creditoService.calcularIntereses(BigDecimal.ZERO, new BigDecimal("0.12"), 1))
                .isInstanceOf(MontoInvalidoException.class)
                .hasMessageContaining("El capital debe ser mayor a 0.");

        assertThatThrownBy(() -> creditoService.calcularIntereses(new BigDecimal("-100"), new BigDecimal("0.12"), 1))
                .isInstanceOf(MontoInvalidoException.class);

        assertThatThrownBy(() -> creditoService.calcularIntereses(new BigDecimal("1000"), new BigDecimal("-0.05"), 1))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La tasa mensual no puede ser negativa.");

        assertThatThrownBy(() -> creditoService.calcularIntereses(new BigDecimal("1000"), new BigDecimal("0.12"), 0))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El plazo en meses debe ser mayor a 0.");
    }

    @Test
    @DisplayName("VP-004: Ajuste exacto de centavos en cuota 12 semanal para crédito de 100.000 a 3 meses (RN-006)")
    void testVectorVP004AjusteCentavosCienMil() {
        // Capital = 100.000, 12% a 3 meses -> I = 36.000, Total = 136.000. 12 cuotas semanales.
        BigDecimal totalPagar = new BigDecimal("136000.00");
        int numCuotas = 12;
        LocalDate fechaInicio = LocalDate.of(2026, 1, 1);

        List<Cuota> plan = creditoService.generarPlanPagos(totalPagar, numCuotas, FrecuenciaPago.SEMANAL, fechaInicio);

        assertThat(plan).hasSize(12);

        // Cuotas 1 a 11 deben ser exactamente 11333.33
        for (int i = 0; i < 11; i++) {
            assertThat(plan.get(i).getMonto())
                    .as("Cuota " + (i + 1) + " debe ser la cuota base")
                    .isEqualByComparingTo("11333.33");
        }

        // Cuota 12 debe tener el ajuste residual exacto: 11333.37
        assertThat(plan.get(11).getMonto())
                .as("Cuota 12 debe absorber el residuo contable")
                .isEqualByComparingTo("11333.37");

        // Invariante estricta: sum(cuotas) == totalPagar
        BigDecimal sumaTotal = plan.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaTotal).isEqualByComparingTo(totalPagar);
    }

    @Test
    @DisplayName("VP-005: Ajuste exacto de centavos en cuota 12 semanal para crédito de 1.000.000 a 3 meses (RN-006)")
    void testVectorVP005AjusteCentavosUnMillon() {
        // Capital = 1.000.000, 12% a 3 meses -> I = 360.000, Total = 1.360.000. 12 cuotas semanales.
        BigDecimal totalPagar = new BigDecimal("1360000.00");
        int numCuotas = 12;
        LocalDate fechaInicio = LocalDate.of(2026, 1, 1);

        List<Cuota> plan = creditoService.generarPlanPagos(totalPagar, numCuotas, FrecuenciaPago.SEMANAL, fechaInicio);

        assertThat(plan).hasSize(12);

        // Cuotas 1 a 11 deben ser 113333.33
        for (int i = 0; i < 11; i++) {
            assertThat(plan.get(i).getMonto()).isEqualByComparingTo("113333.33");
        }

        // Cuota 12 con residuo: 113333.37
        assertThat(plan.get(11).getMonto()).isEqualByComparingTo("113333.37");

        BigDecimal sumaTotal = plan.stream()
                .map(Cuota::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumaTotal).isEqualByComparingTo(totalPagar);
    }

    @Test
    @DisplayName("VP-006: Proyección de vencimientos cada 7 días en semanal y cada 15 días en quincenal (RN-007)")
    void testVectorVP006ProyeccionFechasVencimiento() {
        LocalDate inicio = LocalDate.of(2023, 1, 1);

        // Semanal: +7, +14, +21...
        List<Cuota> semanal = creditoService.generarPlanPagos(new BigDecimal("100000"), 3, FrecuenciaPago.SEMANAL, inicio);
        assertThat(semanal.get(0).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 1, 8));
        assertThat(semanal.get(1).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(semanal.get(2).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 1, 22));

        // Quincenal: +15, +30, +45...
        List<Cuota> quincenal = creditoService.generarPlanPagos(new BigDecimal("100000"), 3, FrecuenciaPago.QUINCENAL, inicio);
        assertThat(quincenal.get(0).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 1, 16));
        assertThat(quincenal.get(1).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 1, 31));
        assertThat(quincenal.get(2).getFechaVencimiento()).isEqualTo(LocalDate.of(2023, 2, 15));
    }

    @Test
    @DisplayName("Debe crear atómicamente un crédito con todas sus cuotas en base de datos (CU-006)")
    void testCrearCreditoTransaccionalExitoso() {
        Credito credito = creditoService.crearCredito(
                clientePrueba.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.12"),
                1,
                FrecuenciaPago.SEMANAL,
                LocalDate.of(2026, 3, 1)
        );

        assertThat(credito.getId()).isNotNull();
        assertThat(credito.getEstado()).isEqualTo(EstadoCredito.ACTIVO);
        assertThat(credito.getTotalPagar()).isEqualByComparingTo("1120000.00");

        List<Cuota> cuotas = creditoService.obtenerCuotasCredito(credito.getId());
        assertThat(cuotas).hasSize(4); // 1 mes semanal = 4 cuotas
        assertThat(cuotas.get(0).getEstado()).isEqualTo(EstadoCuota.PENDIENTE);
    }

    @Test
    @DisplayName("Debe fallar al crear crédito si el cliente no existe")
    void testCrearCreditoClienteInexistente() {
        assertThatThrownBy(() -> creditoService.crearCredito(
                9999, new BigDecimal("100000"), null, 1, FrecuenciaPago.SEMANAL, null
        )).isInstanceOf(ClienteNoEncontradoException.class);
    }

    @Test
    @DisplayName("Debe calcular el saldo pendiente dinámicamente restando pagos acumulados (RN-010)")
    void testCalcularSaldoPendiente() throws SQLException {
        Credito credito = creditoService.crearCredito(
                clientePrueba.getId(), new BigDecimal("1000000"), new BigDecimal("0.12"), 1,
                FrecuenciaPago.QUINCENAL, LocalDate.now()
        );
        // Total = 1.120.000. Cuota 1 = 560.000, Cuota 2 = 560.000.
        List<Cuota> cuotas = creditoService.obtenerCuotasCredito(credito.getId());

        // Saldo inicial = total a pagar
        assertThat(creditoService.calcularSaldoPendiente(credito.getId())).isEqualByComparingTo("1120000.00");

        // Registrar abono de 200.000 a cuota 1
        pagoRepository.crear(new Pago(credito.getId(), cuotas.get(0).getId(), new BigDecimal("200000"), LocalDate.now()));
        assertThat(creditoService.calcularSaldoPendiente(credito.getId())).isEqualByComparingTo("920000.00");

        // Registrar abono de 360.000 a cuota 1
        pagoRepository.crear(new Pago(credito.getId(), cuotas.get(0).getId(), new BigDecimal("360000"), LocalDate.now()));
        assertThat(creditoService.calcularSaldoPendiente(credito.getId())).isEqualByComparingTo("560000.00");
    }

    @Test
    @DisplayName("Debe determinar el estado del crédito y pasar a PAGADO si todas las cuotas están pagadas (RN-011)")
    void testDeterminarEstadoCredito() throws SQLException {
        Credito credito = creditoService.crearCredito(
                clientePrueba.getId(), new BigDecimal("100000"), new BigDecimal("0.12"), 1,
                FrecuenciaPago.QUINCENAL, LocalDate.now()
        );
        List<Cuota> cuotas = creditoService.obtenerCuotasCredito(credito.getId());

        // Estado inicial = ACTIVO
        assertThat(creditoService.determinarEstadoCredito(credito.getId())).isEqualTo(EstadoCredito.ACTIVO);

        // Pagar cuota 1 -> sigue ACTIVO
        creditoRepository.actualizarEstadoCuota(cuotas.get(0).getId(), EstadoCuota.PAGADA);
        assertThat(creditoService.determinarEstadoCredito(credito.getId())).isEqualTo(EstadoCredito.ACTIVO);

        // Pagar cuota 2 -> pasa a PAGADO
        creditoRepository.actualizarEstadoCuota(cuotas.get(1).getId(), EstadoCuota.PAGADA);
        assertThat(creditoService.determinarEstadoCredito(credito.getId())).isEqualTo(EstadoCredito.PAGADO);

        // Si el crédito estuviera CANCELADO, lo preserva
        creditoRepository.actualizarEstado(credito.getId(), EstadoCredito.CANCELADO);
        assertThat(creditoService.determinarEstadoCredito(credito.getId())).isEqualTo(EstadoCredito.CANCELADO);
    }
}
