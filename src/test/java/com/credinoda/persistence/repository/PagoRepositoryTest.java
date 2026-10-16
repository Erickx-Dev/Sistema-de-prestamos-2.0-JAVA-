package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.dto.PagoDetalleItem;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Pago;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas unitarias de PagoRepository")
class PagoRepositoryTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;

    private Cliente cliente;
    private Credito credito;
    private Cuota cuota1;
    private Cuota cuota2;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        pagoRepository = new PagoRepository(connection);

        cliente = clienteRepository.crear(new Cliente("Lucía Domínguez", "3205558899", "40506070"));
        credito = creditoRepository.crear(new Credito(
                cliente.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.12"),
                1,
                new BigDecimal("120000"),
                new BigDecimal("1120000"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));

        cuota1 = creditoRepository.crearCuota(new Cuota(credito.getId(), 1, LocalDate.now().plusDays(15), new BigDecimal("560000"), EstadoCuota.PENDIENTE));
        cuota2 = creditoRepository.crearCuota(new Cuota(credito.getId(), 2, LocalDate.now().plusDays(30), new BigDecimal("560000"), EstadoCuota.PENDIENTE));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe registrar un pago y asignar su clave autoincremental")
    void testCrearPago() throws SQLException {
        Pago pago = new Pago(credito.getId(), cuota1.getId(), new BigDecimal("200000"), LocalDate.now());
        Pago creado = pagoRepository.crear(pago);

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getId()).isPositive();

        Optional<Pago> recuperado = pagoRepository.obtenerPorId(creado.getId());
        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().getMonto()).isEqualByComparingTo("200000");
        assertThat(recuperado.get().getCuotaId()).isEqualTo(cuota1.getId());
    }

    @Test
    @DisplayName("Debe acumular sumas de pagos de cuota y crédito con precisión exacta BigDecimal (RN-010)")
    void testAcumulacionPagosExacta() throws SQLException {
        // Abono parcial a cuota 1
        pagoRepository.crear(new Pago(credito.getId(), cuota1.getId(), new BigDecimal("200000.50"), LocalDate.now()));
        // Segundo abono a cuota 1
        pagoRepository.crear(new Pago(credito.getId(), cuota1.getId(), new BigDecimal("359999.50"), LocalDate.now()));
        // Abono a cuota 2
        pagoRepository.crear(new Pago(credito.getId(), cuota2.getId(), new BigDecimal("100000.00"), LocalDate.now()));

        BigDecimal totalCuota1 = pagoRepository.obtenerTotalPagadoCuota(cuota1.getId());
        assertThat(totalCuota1).isEqualByComparingTo("560000.00");

        BigDecimal totalCuota2 = pagoRepository.obtenerTotalPagadoCuota(cuota2.getId());
        assertThat(totalCuota2).isEqualByComparingTo("100000.00");

        BigDecimal totalCredito = pagoRepository.obtenerTotalPagadoCredito(credito.getId());
        assertThat(totalCredito).isEqualByComparingTo("660000.00");
    }

    @Test
    @DisplayName("Debe listar pagos con detalle y permitir filtrado por cliente, crédito o fecha (CU-010, RN-016)")
    void testListarTodosConDetalleYFiltros() throws SQLException {
        LocalDate hoy = LocalDate.now();
        pagoRepository.crear(new Pago(credito.getId(), cuota1.getId(), new BigDecimal("100000"), hoy.minusDays(5)));
        pagoRepository.crear(new Pago(credito.getId(), cuota1.getId(), new BigDecimal("150000"), hoy.minusDays(1)));

        // Listar todos sin filtro
        List<PagoDetalleItem> todos = pagoRepository.listarTodosConDetalle(null, null, null);
        assertThat(todos).hasSize(2);
        assertThat(todos.get(0).clienteNombre()).isEqualTo("Lucía Domínguez");
        assertThat(todos.get(0).codigoRecibo()).matches("REC-\\d{5}");

        // Filtrar por nombre
        List<PagoDetalleItem> porNombre = pagoRepository.listarTodosConDetalle("Lucía", null, null);
        assertThat(porNombre).hasSize(2);

        // Filtrar por ID de crédito
        List<PagoDetalleItem> porCredito = pagoRepository.listarTodosConDetalle(String.valueOf(credito.getId()), null, null);
        assertThat(porCredito).hasSize(2);

        // Filtrar por rango de fechas (solo ayer y hoy)
        List<PagoDetalleItem> porRango = pagoRepository.listarTodosConDetalle(null, hoy.minusDays(2), hoy);
        assertThat(porRango).hasSize(1);
        assertThat(porRango.get(0).montoAbonado()).isEqualByComparingTo("150000");
    }

    @Test
    @DisplayName("Debe resolver datos consolidados del comprobante para recibo y código QR (CU-011, RN-012)")
    void testObtenerDetallePagoParaComprobante() throws SQLException {
        Pago pago = pagoRepository.crear(new Pago(credito.getId(), cuota1.getId(), new BigDecimal("200000"), LocalDate.now()));

        Optional<DatosComprobante> comprobanteOpt = pagoRepository.obtenerDetallePago(pago.getId());
        assertThat(comprobanteOpt).isPresent();

        DatosComprobante c = comprobanteOpt.get();
        assertThat(c.pagoId()).isEqualTo(pago.getId());
        assertThat(c.numeroRecibo()).isEqualTo(String.format("REC-%05d", pago.getId()));
        assertThat(c.clienteNombre()).isEqualTo("Lucía Domínguez");
        assertThat(c.clienteDocumento()).isEqualTo("40506070");
        assertThat(c.creditoId()).isEqualTo(credito.getId());
        assertThat(c.numeroCuota()).isEqualTo(1);
        assertThat(c.totalCuotas()).isEqualTo(2);
        assertThat(c.cuotaMonto()).isEqualByComparingTo("560000");
        assertThat(c.pagoMonto()).isEqualByComparingTo("200000");
        assertThat(c.totalPagadoCuota()).isEqualByComparingTo("200000");
        // Saldo después = 560000 - 200000 = 360000
        assertThat(c.saldoCuotaDespues()).isEqualByComparingTo("360000");
        // Saldo antes = 360000 + 200000 = 560000
        assertThat(c.saldoCuotaAntes()).isEqualByComparingTo("560000");
        // Saldo restante crédito = 1120000 - 200000 = 920000
        assertThat(c.saldoCreditoRestante()).isEqualByComparingTo("920000");
        // % avance: (200000 / 1120000) * 100 = 17.857... -> 17.9%
        assertThat(c.porcentajePagadoCredito()).isEqualByComparingTo("17.9");
    }
}
