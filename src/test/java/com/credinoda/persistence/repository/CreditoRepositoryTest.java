package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.ProximoPagoItem;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
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

@DisplayName("Pruebas unitarias de CreditoRepository")
class CreditoRepositoryTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private Cliente clientePrueba;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);

        clientePrueba = clienteRepository.crear(new Cliente("Juan Valdez", "3001234567", "1122334455"));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe persistir un crédito y asignar su ID autoincremental")
    void testCrearCredito() throws SQLException {
        Credito credito = new Credito(
                clientePrueba.getId(),
                new BigDecimal("1000000"),
                new BigDecimal("0.12"),
                1,
                new BigDecimal("120000"),
                new BigDecimal("1120000"),
                FrecuenciaPago.SEMANAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        );

        Credito creado = creditoRepository.crear(credito);
        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getId()).isPositive();

        Optional<Credito> recuperado = creditoRepository.obtenerPorId(creado.getId());
        assertThat(recuperado).isPresent();
        assertThat(recuperado.get().getCapital()).isEqualByComparingTo("1000000");
        assertThat(recuperado.get().getTotalPagar()).isEqualByComparingTo("1120000");
        assertThat(recuperado.get().getFrecuenciaPago()).isEqualTo(FrecuenciaPago.SEMANAL);
        assertThat(recuperado.get().getEstado()).isEqualTo(EstadoCredito.ACTIVO);
    }

    @Test
    @DisplayName("Debe persistir cuotas y consultarlas ordenadas por número de cuota")
    void testCrearCuotasYConsultarPorCredito() throws SQLException {
        Credito credito = crearCreditoBase();

        Cuota c1 = creditoRepository.crearCuota(new Cuota(credito.getId(), 1, LocalDate.now().plusDays(7), new BigDecimal("280000"), EstadoCuota.PENDIENTE));
        Cuota c2 = creditoRepository.crearCuota(new Cuota(credito.getId(), 2, LocalDate.now().plusDays(14), new BigDecimal("280000"), EstadoCuota.PENDIENTE));

        assertThat(c1.getId()).isNotNull();
        assertThat(c2.getId()).isNotNull();

        List<Cuota> cuotas = creditoRepository.obtenerCuotasPorCredito(credito.getId());
        assertThat(cuotas).hasSize(2);
        assertThat(cuotas.get(0).getNumeroCuota()).isEqualTo(1);
        assertThat(cuotas.get(1).getNumeroCuota()).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe actualizar el estado de un crédito a PAGADO")
    void testActualizarEstadoCredito() throws SQLException {
        Credito credito = crearCreditoBase();
        creditoRepository.actualizarEstado(credito.getId(), EstadoCredito.PAGADO);

        Optional<Credito> actualizado = creditoRepository.obtenerPorId(credito.getId());
        assertThat(actualizado).isPresent();
        assertThat(actualizado.get().getEstado()).isEqualTo(EstadoCredito.PAGADO);
    }

    @Test
    @DisplayName("Debe actualizar el estado de una cuota y limpiar la mora si pasa a PAGADA")
    void testActualizarEstadoCuotaLimpiaMora() throws SQLException {
        Credito credito = crearCreditoBase();
        Cuota cuota = creditoRepository.crearCuota(new Cuota(null, credito.getId(), 1, LocalDate.now().minusDays(5), new BigDecimal("100000"), EstadoCuota.PENDIENTE, true));
        assertThat(cuota.isEnMora()).isTrue();

        creditoRepository.actualizarEstadoCuota(cuota.getId(), EstadoCuota.PAGADA);

        Optional<Cuota> recuperada = creditoRepository.obtenerCuotaPorId(cuota.getId());
        assertThat(recuperada).isPresent();
        assertThat(recuperada.get().getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(recuperada.get().isEnMora()).isFalse();
    }

    @Test
    @DisplayName("Debe ejecutar actualización masiva de mora según fecha de vencimiento (RN-008)")
    void testActualizarMoraCuotas() throws SQLException {
        Credito credito = crearCreditoBase();
        LocalDate hoy = LocalDate.now();

        // Cuota 1: Vencida hace 3 días, PENDIENTE -> Debe marcarse en_mora = 1
        creditoRepository.crearCuota(new Cuota(credito.getId(), 1, hoy.minusDays(3), new BigDecimal("100000"), EstadoCuota.PENDIENTE));
        // Cuota 2: Vencida hace 10 días, pero ya PAGADA -> en_mora = 0
        creditoRepository.crearCuota(new Cuota(credito.getId(), 2, hoy.minusDays(10), new BigDecimal("100000"), EstadoCuota.PAGADA));
        // Cuota 3: Vence en 5 días (futura), PENDIENTE -> en_mora = 0
        creditoRepository.crearCuota(new Cuota(credito.getId(), 3, hoy.plusDays(5), new BigDecimal("100000"), EstadoCuota.PENDIENTE));

        // Primera ejecución: solo debe alterar 1 fila (cuota 1)
        int filasModificadas = creditoRepository.actualizarMoraCuotas(hoy);
        assertThat(filasModificadas).isEqualTo(1);

        List<Cuota> cuotas = creditoRepository.obtenerCuotasPorCredito(credito.getId());
        assertThat(cuotas.get(0).isEnMora()).isTrue();
        assertThat(cuotas.get(1).isEnMora()).isFalse();
        assertThat(cuotas.get(2).isEnMora()).isFalse();

        // Segunda ejecución con la misma fecha: debe retornar 0 (idempotente)
        int segundaEjecucion = creditoRepository.actualizarMoraCuotas(hoy);
        assertThat(segundaEjecucion).isZero();
    }

    @Test
    @DisplayName("Debe consultar próximas cuotas pendientes para el Dashboard excluyendo créditos pagados (CU-012, RN-013)")
    void testObtenerCuotasPendientesDashboard() throws SQLException {
        Credito creditoActivo = crearCreditoBase();
        LocalDate hoy = LocalDate.now();

        creditoRepository.crearCuota(new Cuota(creditoActivo.getId(), 1, hoy.plusDays(2), new BigDecimal("150000"), EstadoCuota.PENDIENTE));
        creditoRepository.crearCuota(new Cuota(creditoActivo.getId(), 2, hoy.plusDays(9), new BigDecimal("150000"), EstadoCuota.PENDIENTE));
        creditoRepository.crearCuota(new Cuota(creditoActivo.getId(), 3, hoy.minusDays(2), new BigDecimal("150000"), EstadoCuota.PARCIAL));

        // Crear otro crédito ya pagado cuyas cuotas no deben aparecer
        Credito creditoPagado = new Credito(
                clientePrueba.getId(), new BigDecimal("200000"), new BigDecimal("0.12"), 1,
                new BigDecimal("24000"), new BigDecimal("224000"), FrecuenciaPago.SEMANAL, hoy.minusMonths(1), EstadoCredito.PAGADO
        );
        creditoPagado = creditoRepository.crear(creditoPagado);
        creditoRepository.crearCuota(new Cuota(creditoPagado.getId(), 1, hoy.plusDays(1), new BigDecimal("50000"), EstadoCuota.PENDIENTE));

        List<ProximoPagoItem> proximas = creditoRepository.obtenerCuotasPendientes(10);
        assertThat(proximas).hasSize(3);
        // Debe ordenar por fecha de vencimiento ascendente (la de hoy.minusDays(2) primero)
        assertThat(proximas.get(0).fechaVencimiento()).isEqualTo(hoy.minusDays(2));
        assertThat(proximas.get(0).clienteNombre()).isEqualTo("Juan Valdez");
        assertThat(proximas.get(0).monto()).isEqualByComparingTo("150000");
    }

    private Credito crearCreditoBase() throws SQLException {
        return creditoRepository.crear(new Credito(
                clientePrueba.getId(),
                new BigDecimal("500000"),
                new BigDecimal("0.12"),
                1,
                new BigDecimal("60000"),
                new BigDecimal("560000"),
                FrecuenciaPago.SEMANAL,
                LocalDate.now(),
                EstadoCredito.ACTIVO
        ));
    }
}
