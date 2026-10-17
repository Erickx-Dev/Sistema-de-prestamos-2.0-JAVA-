package com.credinoda.service;

import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.EstadoCuota;
import com.credinoda.domain.model.FrecuenciaPago;
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

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas de detección y tratamiento de mora en cuotas (RN-008, CU-008)")
class MoraCuotasTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;
    private CreditoService creditoService;
    private PagoService pagoService;

    private Cliente clientePrueba;
    private Credito creditoPrueba;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        pagoRepository = new PagoRepository(connection);
        creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository);
        pagoService = new PagoService(connection, pagoRepository, creditoRepository, creditoService);

        clientePrueba = clienteRepository.crear(new Cliente("Cliente Mora", "3001239999", "DOC-MORA-01"));
        creditoPrueba = creditoRepository.crear(new Credito(
                clientePrueba.getId(), new BigDecimal("500000"), new BigDecimal("0.12"), 1,
                new BigDecimal("60000"), new BigDecimal("560000"), FrecuenciaPago.SEMANAL,
                LocalDate.now(), EstadoCredito.ACTIVO
        ));
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe marcar en_mora = 1 si la fecha de vencimiento es estrictamente anterior a la fecha de corte y no está pagada (RN-008)")
    void testCuotaVencidaPasaAEnMora() throws SQLException {
        LocalDate hoy = LocalDate.now();
        Cuota cVencida = creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 1, hoy.minusDays(1), new BigDecimal("140000"), EstadoCuota.PENDIENTE
        ));

        int modificadas = creditoService.actualizarCuotasEnMora(hoy);
        assertThat(modificadas).isEqualTo(1);

        Cuota actualizada = creditoRepository.obtenerCuotaPorId(cVencida.getId()).orElseThrow();
        assertThat(actualizada.isEnMora()).isTrue();
        assertThat(actualizada.getEstado()).isEqualTo(EstadoCuota.PENDIENTE); // Preserva PENDIENTE
    }

    @Test
    @DisplayName("No debe marcar en mora si la cuota vence en el futuro o vence el mismo día de corte (RN-008)")
    void testCuotaFuturaOHoyNoEstaEnMora() throws SQLException {
        LocalDate hoy = LocalDate.now();
        Cuota cHoy = creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 1, hoy, new BigDecimal("140000"), EstadoCuota.PENDIENTE
        ));
        Cuota cFutura = creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 2, hoy.plusDays(7), new BigDecimal("140000"), EstadoCuota.PENDIENTE
        ));

        creditoService.actualizarCuotasEnMora(hoy);

        assertThat(creditoRepository.obtenerCuotaPorId(cHoy.getId()).orElseThrow().isEnMora()).isFalse();
        assertThat(creditoRepository.obtenerCuotaPorId(cFutura.getId()).orElseThrow().isEnMora()).isFalse();
    }

    @Test
    @DisplayName("No debe marcar en mora una cuota con fecha vencida si su estado es PAGADA (RN-008)")
    void testCuotaVencidaPagadaNoEstaEnMora() throws SQLException {
        LocalDate hoy = LocalDate.now();
        Cuota cPagada = creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 1, hoy.minusDays(10), new BigDecimal("140000"), EstadoCuota.PAGADA
        ));

        creditoService.actualizarCuotasEnMora(hoy);

        assertThat(creditoRepository.obtenerCuotaPorId(cPagada.getId()).orElseThrow().isEnMora()).isFalse();
    }

    @Test
    @DisplayName("Debe extinguir automáticamente la mora al liquidar tardíamente una cuota en mora (RN-008, RN-011)")
    void testExtincionMoraAlPagarCuotaTardia() throws SQLException {
        LocalDate hoy = LocalDate.now();
        Cuota cMora = creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 1, hoy.minusDays(5), new BigDecimal("140000"), EstadoCuota.PENDIENTE
        ));
        creditoService.actualizarCuotasEnMora(hoy);
        assertThat(creditoRepository.obtenerCuotaPorId(cMora.getId()).orElseThrow().isEnMora()).isTrue();

        // Pagar la cuota vencida
        pagoService.registrarPago(creditoPrueba.getId(), cMora.getId(), new BigDecimal("140000"), hoy);

        Cuota pagada = creditoRepository.obtenerCuotaPorId(cMora.getId()).orElseThrow();
        assertThat(pagada.getEstado()).isEqualTo(EstadoCuota.PAGADA);
        assertThat(pagada.isEnMora()).isFalse();
    }

    @Test
    @DisplayName("El crédito debe conservar el estado ACTIVO aun cuando tenga cuotas en mora (RN-008)")
    void testCreditoPermaneceActivoConCuotasEnMora() throws SQLException {
        LocalDate hoy = LocalDate.now();
        creditoRepository.crearCuota(new Cuota(
                creditoPrueba.getId(), 1, hoy.minusDays(3), new BigDecimal("140000"), EstadoCuota.PENDIENTE
        ));
        creditoService.actualizarCuotasEnMora(hoy);

        Credito credito = creditoService.obtenerCreditoPorId(creditoPrueba.getId());
        assertThat(credito.getEstado()).isEqualTo(EstadoCredito.ACTIVO);
    }

    @Test
    @DisplayName("Etiqueta visual de estado debe incorporar '(EN MORA)' para PENDIENTE y PARCIAL (RN-008)")
    void testEtiquetaEstadoCalculada() {
        Cuota cuotaMoraPendiente = new Cuota(null, 1, 1, LocalDate.now().minusDays(2), new BigDecimal("100"), EstadoCuota.PENDIENTE, true);
        assertThat(cuotaMoraPendiente.etiquetaEstado()).isEqualTo("PENDIENTE (EN MORA)");

        Cuota cuotaMoraParcial = new Cuota(null, 1, 2, LocalDate.now().minusDays(2), new BigDecimal("100"), EstadoCuota.PARCIAL, true);
        assertThat(cuotaMoraParcial.etiquetaEstado()).isEqualTo("PARCIAL (EN MORA)");

        Cuota cuotaSinMoraPendiente = new Cuota(null, 1, 3, LocalDate.now().plusDays(2), new BigDecimal("100"), EstadoCuota.PENDIENTE, false);
        assertThat(cuotaSinMoraPendiente.etiquetaEstado()).isEqualTo("PENDIENTE");

        Cuota cuotaPagada = new Cuota(null, 1, 4, LocalDate.now().minusDays(2), new BigDecimal("100"), EstadoCuota.PAGADA, false);
        assertThat(cuotaPagada.etiquetaEstado()).isEqualTo("PAGADA");
    }

    @Test
    @DisplayName("La actualización masiva de mora debe ser idempotente y solo alterar filas que cambian")
    void testSincronizacionMoraIdempotente() throws SQLException {
        LocalDate hoy = LocalDate.now();
        creditoRepository.crearCuota(new Cuota(creditoPrueba.getId(), 1, hoy.minusDays(2), new BigDecimal("100000"), EstadoCuota.PENDIENTE));
        creditoRepository.crearCuota(new Cuota(creditoPrueba.getId(), 2, hoy.plusDays(5), new BigDecimal("100000"), EstadoCuota.PENDIENTE));

        // Primera llamada: 1 fila modificada
        int primera = creditoService.actualizarCuotasEnMora(hoy);
        assertThat(primera).isEqualTo(1);

        // Segunda llamada con la misma fecha: 0 filas modificadas
        int segunda = creditoService.actualizarCuotasEnMora(hoy);
        assertThat(segunda).isZero();
    }
}
