package com.credinoda.persistence.repository;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas unitarias de AuditoriaRepository")
class AuditoriaRepositoryTest {

    private Connection connection;
    private AuditoriaRepository auditoriaRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        auditoriaRepository = new AuditoriaRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe registrar un evento de auditoría y asignar su ID generado automáticamente")
    void debeRegistrarEventoAuditoriaYAsignarId() throws SQLException {
        EventoAuditoria evento = EventoAuditoria.crearNuevo(
                LocalDateTime.now(),
                1L,
                "admin",
                "ADMINISTRADOR",
                AccionAuditoria.CREAR_CLIENTE,
                "CLIENTES",
                "10",
                "Cliente registrado: Juan Perez",
                ResultadoAuditoria.EXITO,
                "WS-01"
        );

        EventoAuditoria guardado = auditoriaRepository.registrar(evento);

        assertThat(guardado.getId()).isNotNull();
        assertThat(guardado.getId()).isPositive();
        assertThat(guardado.getUsername()).isEqualTo("admin");
        assertThat(guardado.getAccion()).isEqualTo(AccionAuditoria.CREAR_CLIENTE);
        assertThat(guardado.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
    }

    @Test
    @DisplayName("Debe registrar un evento en una conexión con transacción activa")
    void debeRegistrarEnTransaccionActiva() throws SQLException {
        connection.setAutoCommit(false);

        EventoAuditoria evento = EventoAuditoria.crearNuevo(
                LocalDateTime.now(),
                2L,
                "operador",
                "OPERADOR",
                AccionAuditoria.REGISTRAR_PAGO,
                "PAGOS",
                "101",
                "Abono de 50.000",
                ResultadoAuditoria.EXITO,
                "WS-02"
        );

        EventoAuditoria guardado = auditoriaRepository.registrarEnTransaccion(connection, evento);
        connection.commit();
        connection.setAutoCommit(true);

        assertThat(guardado.getId()).isNotNull();
        Optional<EventoAuditoria> opt = auditoriaRepository.obtenerPorId(guardado.getId());
        assertThat(opt).isPresent();
        assertThat(opt.get().getDetalle()).isEqualTo("Abono de 50.000");
    }

    @Test
    @DisplayName("Inmutabilidad estricta (VP-023): Disparo del trigger trg_auditoria_no_update ante cualquier intento de UPDATE")
    void debeImpedirActualizacionPorTriggerInmutableVP023() throws SQLException {
        EventoAuditoria evento = auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                LocalDateTime.now(),
                1L,
                "admin",
                "ADMINISTRADOR",
                AccionAuditoria.LOGIN_EXITOSO,
                "USUARIOS",
                "1",
                "Inicio de sesión exitoso",
                ResultadoAuditoria.EXITO,
                "WS-01"
        ));

        assertThatThrownBy(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE auditoria SET detalle = 'ALTERADO' WHERE id = " + evento.getId());
            }
        }).isInstanceOf(SQLException.class)
          .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("Inmutabilidad estricta (VP-023): Disparo del trigger trg_auditoria_no_delete ante cualquier intento de DELETE")
    void debeImpedirEliminacionPorTriggerInmutableVP023() throws SQLException {
        EventoAuditoria evento = auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                LocalDateTime.now(),
                1L,
                "admin",
                "ADMINISTRADOR",
                AccionAuditoria.LOGIN_EXITOSO,
                "USUARIOS",
                "1",
                "Inicio de sesión exitoso",
                ResultadoAuditoria.EXITO,
                "WS-01"
        ));

        assertThatThrownBy(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM auditoria WHERE id = " + evento.getId());
            }
        }).isInstanceOf(SQLException.class)
          .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("Debe buscar eventos con filtros dinámicos (usuario, entidad, acción, resultado y fechas)")
    void debeBuscarConFiltrosDinamicos() throws SQLException {
        LocalDateTime ahora = LocalDateTime.now();

        auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                ahora.minusDays(3), 1L, "admin", "ADMINISTRADOR",
                AccionAuditoria.CREAR_CLIENTE, "CLIENTES", "1", "Cliente C1",
                ResultadoAuditoria.EXITO, "PC-1"
        ));

        auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                ahora.minusDays(1), 2L, "operador", "OPERADOR",
                AccionAuditoria.REGISTRAR_PAGO, "PAGOS", "1", "Pago P1",
                ResultadoAuditoria.EXITO, "PC-2"
        ));

        auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                ahora, 2L, "operador", "OPERADOR",
                AccionAuditoria.ACCESO_DENEGADO, "SISTEMA", null, "Permiso denegado",
                ResultadoAuditoria.DENEGADO, "PC-2"
        ));

        // 1. Filtrar por término de búsqueda (username o detalle)
        FiltroAuditoria fAdmin = new FiltroAuditoria(null, null, "admin", null, null, null, 10, 0);
        List<EventoAuditoria> resAdmin = auditoriaRepository.buscar(fAdmin);
        assertThat(resAdmin).hasSize(1);
        assertThat(resAdmin.getFirst().getUsername()).isEqualTo("admin");

        // 2. Filtrar por acción
        FiltroAuditoria fPago = new FiltroAuditoria(null, null, null, AccionAuditoria.REGISTRAR_PAGO, null, null, 10, 0);
        List<EventoAuditoria> resPago = auditoriaRepository.buscar(fPago);
        assertThat(resPago).hasSize(1);
        assertThat(resPago.getFirst().getAccion()).isEqualTo(AccionAuditoria.REGISTRAR_PAGO);

        // 3. Filtrar por resultado
        FiltroAuditoria fDenegado = new FiltroAuditoria(null, null, null, null, null, ResultadoAuditoria.DENEGADO, 10, 0);
        List<EventoAuditoria> resDenegado = auditoriaRepository.buscar(fDenegado);
        assertThat(resDenegado).hasSize(1);
        assertThat(resDenegado.getFirst().getResultado()).isEqualTo(ResultadoAuditoria.DENEGADO);

        // 4. Filtrar por rango de fechas
        LocalDate hoy = LocalDate.now();
        FiltroAuditoria fFechas = new FiltroAuditoria(hoy.minusDays(2), hoy, null, null, null, null, 10, 0);
        List<EventoAuditoria> resFechas = auditoriaRepository.buscar(fFechas);
        assertThat(resFechas).hasSize(2);
    }

    @Test
    @DisplayName("Debe contar y paginar registros correctamente con límite y offset")
    void debeContarRegistrosYPaginarse() throws SQLException {
        LocalDateTime ahora = LocalDateTime.now();
        for (int i = 1; i <= 15; i++) {
            auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                    ahora.minusMinutes(i), 1L, "admin", "ADMINISTRADOR",
                    AccionAuditoria.CREAR_CLIENTE, "CLIENTES", String.valueOf(i),
                    "Cliente " + i, ResultadoAuditoria.EXITO, "PC-1"
            ));
        }

        FiltroAuditoria todos = new FiltroAuditoria(null, null, null, null, null, null, 5, 0);
        assertThat(auditoriaRepository.contar(todos)).isEqualTo(15);

        List<EventoAuditoria> pagina1 = auditoriaRepository.buscar(todos);
        assertThat(pagina1).hasSize(5);

        FiltroAuditoria pagina2Filtro = new FiltroAuditoria(null, null, null, null, null, null, 5, 5);
        List<EventoAuditoria> pagina2 = auditoriaRepository.buscar(pagina2Filtro);
        assertThat(pagina2).hasSize(5);
        assertThat(pagina2.getFirst().getId()).isNotEqualTo(pagina1.getFirst().getId());
    }

    @Test
    @DisplayName("Debe obtener un evento de auditoría por su ID o retornar Optional vacío")
    void debeObtenerPorId() throws SQLException {
        EventoAuditoria evento = auditoriaRepository.registrar(EventoAuditoria.crearNuevo(
                LocalDateTime.now(), null, "SISTEMA", "SISTEMA",
                AccionAuditoria.CREAR_BACKUP, "BACKUP", "bk-1",
                "Backup creado", ResultadoAuditoria.EXITO, "SERVIDOR"
        ));

        Optional<EventoAuditoria> encontrado = auditoriaRepository.obtenerPorId(evento.getId());
        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getAccion()).isEqualTo(AccionAuditoria.CREAR_BACKUP);

        Optional<EventoAuditoria> noEncontrado = auditoriaRepository.obtenerPorId(999999L);
        assertThat(noEncontrado).isEmpty();
    }
}
