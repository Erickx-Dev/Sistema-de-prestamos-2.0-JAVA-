package com.credinoda.service;

import com.credinoda.domain.dto.DistribucionCartera;
import com.credinoda.domain.dto.MetricasReporte;
import com.credinoda.domain.dto.ProyeccionSemanalItem;
import com.credinoda.domain.dto.RangoFechas;
import com.credinoda.domain.exception.DatosInvalidosException;
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
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de ReporteService (CU-013, RN-014)")
class ReporteServiceTest {

    private Connection connection;
    private ClienteRepository clienteRepo;
    private CreditoRepository creditoRepo;
    private PagoRepository pagoRepo;
    private ReporteService reporteService;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepo = new ClienteRepository(connection);
        creditoRepo = new CreditoRepository(connection);
        pagoRepo = new PagoRepository(connection);
        reporteService = new ReporteService(creditoRepo, pagoRepo);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("calcularRangoFechas() resuelve correctamente los preajustes temporales (RN-014, VP-012)")
    void testCalcularRangoFechasPreajustes() {
        LocalDate ref = LocalDate.of(2026, 10, 16); // Viernes 16 Octubre 2026

        // 1. TODOS y PERSONALIZADO -> nulos
        RangoFechas todos = reporteService.calcularRangoFechas("TODOS", ref);
        assertThat(todos.desde()).isNull();
        assertThat(todos.hasta()).isNull();

        RangoFechas personalizado = reporteService.calcularRangoFechas("Personalizado", ref);
        assertThat(personalizado.desde()).isNull();
        assertThat(personalizado.hasta()).isNull();

        // 2. HOY
        RangoFechas hoy = reporteService.calcularRangoFechas("HOY", ref);
        assertThat(hoy.desde()).isEqualTo(ref);
        assertThat(hoy.hasta()).isEqualTo(ref);

        // 3. ESTA_SEMANA (Lunes 12 a Domingo 18)
        RangoFechas semana = reporteService.calcularRangoFechas("Esta Semana", ref);
        assertThat(semana.desde()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(semana.desde().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(semana.hasta()).isEqualTo(LocalDate.of(2026, 10, 18));
        assertThat(semana.hasta().getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        // 4. ESTE_MES (1 al 31 de Octubre)
        RangoFechas mes = reporteService.calcularRangoFechas("Este Mes", ref);
        assertThat(mes.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(mes.hasta()).isEqualTo(LocalDate.of(2026, 10, 31));

        // 5. ANIO_EN_CURSO (1 de Enero al 31 de Diciembre)
        RangoFechas anio = reporteService.calcularRangoFechas("Año en curso", ref);
        assertThat(anio.desde()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(anio.hasta()).isEqualTo(LocalDate.of(2026, 12, 31));

        // 6. Filtro no reconocido
        assertThatThrownBy(() -> reporteService.calcularRangoFechas("SEMESTRE", ref))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Tipo de filtro temporal no reconocido");
    }

    @Test
    @DisplayName("obtenerMetricas() en base de datos vacía devuelve ceros consistentes")
    void testObtenerMetricasBaseDatosVacia() {
        MetricasReporte m = reporteService.obtenerMetricas(null, null);

        assertThat(m.activos()).isEqualTo(0);
        assertThat(m.pagados()).isEqualTo(0);
        assertThat(m.total()).isEqualTo(0);
        assertThat(m.capitalPrestado()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.interesesGenerados()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.pagosRecibidos()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.saldoVencido()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(m.tasaMorosidad()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(m.interesesCobrados()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(m.margenGanancia()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(m.ticketPromedio()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(m.plazoPromedio()).isEqualByComparingTo(new BigDecimal("0.0"));
    }

    @Test
    @DisplayName("VP-010: Ganancia líquida proporcional e intereses efectivamente cobrados")
    void testVP010GananciaLiquidaProrrateada() throws SQLException {
        // Cliente
        Cliente cliente = clienteRepo.crear(new Cliente("Carlos VP-010", "3001112233", "CC-1010"));

        // Credito: Capital 1.000.000, Intereses 100.000, Total a pagar 1.100.000 (factor de interés: 100.000 / 1.100.000 = 1/11)
        Credito c = creditoRepo.crear(new Credito(
                cliente.getId(),
                new BigDecimal("1000000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("100000.00"),
                new BigDecimal("1100000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));

        // Creamos cuota 1
        Cuota q = creditoRepo.crearCuota(new Cuota(
                null,
                c.getId(),
                1,
                LocalDate.now().plusDays(15),
                new BigDecimal("550000.00"),
                EstadoCuota.PENDIENTE,
                false
        ));

        // Pago recibido: $275.000,00
        // Ganancia líquida = 275.000 * (100.000 / 1.100.000) = 25.000,00
        // Margen de ganancia = (25.000 / 275.000) * 100 = 9.09%
        pagoRepo.crear(new Pago(c.getId(), q.getId(), new BigDecimal("275000.00"), LocalDate.now()));

        MetricasReporte m = reporteService.obtenerMetricas(null, null);

        assertThat(m.pagosRecibidos()).isEqualByComparingTo(new BigDecimal("275000.00"));
        assertThat(m.interesesCobrados()).isEqualByComparingTo(new BigDecimal("25000.00"));
        assertThat(m.margenGanancia()).isEqualByComparingTo(new BigDecimal("9.09"));
    }

    @Test
    @DisplayName("VP-011: Tasa de morosidad con crédito vencido")
    void testVP011TasaMorosidadConVencidos() throws SQLException {
        Cliente cliente = clienteRepo.crear(new Cliente("Ana VP-011", "3004445566", "CC-1011"));

        // Credito: Capital 1.000.000, Intereses 100.000, Total a pagar 1.100.000
        Credito c = creditoRepo.crear(new Credito(
                cliente.getId(),
                new BigDecimal("1000000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("100000.00"),
                new BigDecimal("1100000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now().minusDays(30),
                EstadoCredito.ACTIVO
        ));

        // Cuotas vencidas (hace 10 días): dos cuotas de 550.000 cada una (Total vencido = 1.100.000)
        creditoRepo.crearCuota(new Cuota(null, c.getId(), 1, LocalDate.now().minusDays(15), new BigDecimal("550000.00"), EstadoCuota.PENDIENTE, true));
        creditoRepo.crearCuota(new Cuota(null, c.getId(), 2, LocalDate.now().minusDays(5), new BigDecimal("550000.00"), EstadoCuota.PENDIENTE, true));

        // Saldo vencido = 1.100.000,00
        // Capital prestado = 1.000.000,00
        // Tasa de morosidad = (1.100.000 / 1.000.000) * 100 = 110.00%
        MetricasReporte m = reporteService.obtenerMetricas(null, null);

        assertThat(m.saldoVencido()).isEqualByComparingTo(new BigDecimal("1100000.00"));
        assertThat(m.capitalPrestado()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(m.tasaMorosidad()).isEqualByComparingTo(new BigDecimal("110.00"));
    }

    @Test
    @DisplayName("obtenerMetricas() valida rangos invertidos lanzando DatosInvalidosException")
    void testObtenerMetricasFechasInvertidas() {
        LocalDate desde = LocalDate.of(2026, 10, 20);
        LocalDate hasta = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> reporteService.obtenerMetricas(desde, hasta))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La fecha inicial no puede ser posterior a la fecha final.");
    }

    @Test
    @DisplayName("obtenerDistribucionCartera() clasifica en al día, en mora y saldados (RN-014)")
    void testObtenerDistribucionCartera() throws SQLException {
        Cliente cli = clienteRepo.crear(new Cliente("Maria Distribucion", "3007778899", "CC-DIST"));

        // 1. Crédito Saldado (PAGADO)
        Credito c1 = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("500000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("50000.00"),
                new BigDecimal("550000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));
        Cuota q1 = creditoRepo.crearCuota(new Cuota(null, c1.getId(), 1, LocalDate.now().plusDays(15), new BigDecimal("550000.00"), EstadoCuota.PAGADA, false));
        pagoRepo.crear(new Pago(c1.getId(), q1.getId(), new BigDecimal("550000.00"), LocalDate.now()));
        creditoRepo.actualizarEstado(c1.getId(), EstadoCredito.PAGADO);

        // 2. Crédito En Mora (cuota vencida ayer)
        Credito c2 = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("600000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("60000.00"),
                new BigDecimal("660000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now().minusDays(20),
                EstadoCredito.ACTIVO
        ));
        creditoRepo.crearCuota(new Cuota(null, c2.getId(), 1, LocalDate.now().minusDays(1), new BigDecimal("660000.00"), EstadoCuota.PENDIENTE, true));

        // 3. Crédito Al Día (cuota vence en 10 días)
        Credito c3 = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("700000.00"),
                new BigDecimal("0.10"),
                1,
                new BigDecimal("70000.00"),
                new BigDecimal("770000.00"),
                FrecuenciaPago.QUINCENAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));
        creditoRepo.crearCuota(new Cuota(null, c3.getId(), 1, LocalDate.now().plusDays(10), new BigDecimal("770000.00"), EstadoCuota.PENDIENTE, false));

        DistribucionCartera dist = reporteService.obtenerDistribucionCartera(null, null);

        assertThat(dist.saldados()).isEqualTo(1);
        assertThat(dist.enMora()).isEqualTo(1);
        assertThat(dist.alDia()).isEqualTo(1);
        assertThat(dist.total()).isEqualTo(3);
    }

    @Test
    @DisplayName("obtenerRecaudoVsProyeccionSemanal() agrupa cuotas y recaudos por bloques")
    void testObtenerRecaudoVsProyeccionSemanal() throws SQLException {
        Cliente cli = clienteRepo.crear(new Cliente("Juan Semanal", "3002223344", "CC-SEM"));

        LocalDate lunes = LocalDate.now().with(DayOfWeek.MONDAY);
        LocalDate miercoles = lunes.plusDays(2);

        Credito c = creditoRepo.crear(new Credito(
                cli.getId(),
                new BigDecimal("100000.00"),
                new BigDecimal("0.12"),
                1,
                new BigDecimal("12000.00"),
                new BigDecimal("112000.00"),
                FrecuenciaPago.SEMANAL,
                lunes,
                EstadoCredito.ACTIVO
        ));

        Cuota q = creditoRepo.crearCuota(new Cuota(null, c.getId(), 1, miercoles, new BigDecimal("28000.00"), EstadoCuota.PENDIENTE, false));
        pagoRepo.crear(new Pago(c.getId(), q.getId(), new BigDecimal("20000.00"), miercoles));

        List<ProyeccionSemanalItem> items = reporteService.obtenerRecaudoVsProyeccionSemanal(
                lunes,
                lunes.plusDays(13), // 2 semanas (>= 7 días)
                2
        );

        assertThat(items).isNotEmpty();
        ProyeccionSemanalItem primeraSemana = items.getFirst();
        assertThat(primeraSemana.proyectado()).isEqualByComparingTo(new BigDecimal("28000.00"));
        assertThat(primeraSemana.recaudado()).isEqualByComparingTo(new BigDecimal("20000.00"));
    }
}
