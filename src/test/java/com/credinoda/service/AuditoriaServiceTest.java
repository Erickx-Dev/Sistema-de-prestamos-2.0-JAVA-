package com.credinoda.service;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.dto.RegistroAuditoriaItem;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.domain.model.Rol;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.AuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas unitarias de AuditoriaService")
class AuditoriaServiceTest {

    private Connection connection;
    private AuditoriaRepository repository;
    private AuditoriaService auditoriaService;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("UTC"));

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        repository = new AuditoriaRepository(connection);
        auditoriaService = new AuditoriaService(repository, fixedClock);
        ContextoSeguridad.limpiarSesion();
    }

    @AfterEach
    void tearDown() throws SQLException {
        ContextoSeguridad.limpiarSesion();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe registrar un evento capturando automáticamente el usuario y rol de ContextoSeguridad")
    void debeRegistrarCapturandoContextoSeguridad() {
        ContextoSeguridad.setSesionActual(new SesionUsuario(5, "maria.admin", "María Admin", Rol.ADMINISTRADOR, LocalDateTime.now()));

        EventoAuditoria evento = auditoriaService.registrar(
                AccionAuditoria.CREAR_CLIENTE,
                "CLIENTES",
                "15",
                "Cliente registrado: Pedro Perez",
                ResultadoAuditoria.EXITO
        );

        assertThat(evento).isNotNull();
        assertThat(evento.getId()).isPositive();
        assertThat(evento.getUsername()).isEqualTo("maria.admin");
        assertThat(evento.getRol()).isEqualTo("ADMINISTRADOR");
        assertThat(evento.getUsuarioId()).isEqualTo(5L);
        assertThat(evento.getAccion()).isEqualTo(AccionAuditoria.CREAR_CLIENTE);
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(evento.getFechaHora()).isEqualTo(LocalDateTime.now(fixedClock));
    }

    @Test
    @DisplayName("Debe registrar evento como SISTEMA cuando no hay sesión de usuario activa")
    void debeRegistrarComoSistemaSinSesion() {
        EventoAuditoria evento = auditoriaService.registrar(
                AccionAuditoria.SINCRONIZAR_MORA,
                "CUOTAS",
                null,
                "Sincronización automática de medianoche",
                ResultadoAuditoria.EXITO
        );

        assertThat(evento).isNotNull();
        assertThat(evento.getUsername()).isEqualTo("sistema");
        assertThat(evento.getRol()).isEqualTo("ADMINISTRADOR");
        assertThat(evento.getUsuarioId()).isNull();
    }

    @Test
    @DisplayName("Debe registrar atómicamente en conexión transaccional")
    void debeRegistrarEnTransaccion() throws SQLException {
        ContextoSeguridad.setSesionActual(new SesionUsuario(2, "juan.operador", "Juan Operador", Rol.OPERADOR, LocalDateTime.now()));

        connection.setAutoCommit(false);
        EventoAuditoria guardado = auditoriaService.registrarEnTransaccion(
                connection,
                AccionAuditoria.REGISTRAR_PAGO,
                "PAGOS",
                "20",
                "Abono parcial de 30.000",
                ResultadoAuditoria.EXITO
        );
        connection.commit();
        connection.setAutoCommit(true);

        assertThat(guardado).isNotNull();
        assertThat(guardado.getId()).isPositive();

        List<EventoAuditoria> lista = auditoriaService.buscar(new FiltroAuditoria(null, null, null, null, null, null, 10, 0));
        assertThat(lista).hasSize(1);
        assertThat(lista.getFirst().getDetalle()).isEqualTo("Abono parcial de 30.000");
    }

    @Test
    @DisplayName("Debe transformar los eventos a DTOs RegistroAuditoriaItem con formato adecuado para UI")
    void debeBuscarItemsParaUI() {
        ContextoSeguridad.setSesionActual(new SesionUsuario(1, "admin", "Administrador", Rol.ADMINISTRADOR, LocalDateTime.now()));

        auditoriaService.registrar(
                AccionAuditoria.ACTUALIZAR_CLIENTE,
                "CLIENTES",
                "12",
                "telefono: '123' -> '456'",
                ResultadoAuditoria.EXITO
        );

        List<RegistroAuditoriaItem> items = auditoriaService.buscarItems(new FiltroAuditoria(null, null, null, null, null, null, 10, 0));
        assertThat(items).hasSize(1);

        RegistroAuditoriaItem item = items.getFirst();
        assertThat(item.usuario()).isEqualTo("admin");
        assertThat(item.accion()).isEqualTo(AccionAuditoria.ACTUALIZAR_CLIENTE);
        assertThat(item.accionNombre()).isEqualTo(AccionAuditoria.ACTUALIZAR_CLIENTE.getNombreVisible());
        assertThat(item.entidad()).isEqualTo("CLIENTES");
        assertThat(item.resultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(item.resultadoEtiqueta()).isEqualTo("Éxito");
        assertThat(item.detalle()).contains("telefono: '123' -> '456'");
    }

    @Test
    @DisplayName("Debe generar diff legible de cambios entre dos estados comparando campos modificados")
    void debeGenerarDetalleCambiosDiff() {
        Map<String, Object> antes = new LinkedHashMap<>();
        antes.put("nombre", "Juan Perez");
        antes.put("telefono", "3001112233");
        antes.put("documento", "12345");

        Map<String, Object> despues = new LinkedHashMap<>();
        despues.put("nombre", "Juan Carlos Perez");
        despues.put("telefono", "3001112233"); // Sin cambio
        despues.put("documento", "99999");

        String diff = AuditoriaService.generarDetalleCambios(antes, despues);

        assertThat(diff).contains("nombre: 'Juan Perez' -> 'Juan Carlos Perez'");
        assertThat(diff).contains("documento: '12345' -> '99999'");
        assertThat(diff).doesNotContain("telefono");
    }

    @Test
    @DisplayName("Debe contar los eventos registrados con precisión")
    void debeContarEventos() {
        FiltroAuditoria filtro = new FiltroAuditoria(null, null, null, null, null, null, 50, 0);
        assertThat(auditoriaService.contar(filtro)).isZero();

        auditoriaService.registrarConUsuario("user1", "OPERADOR", 1L, AccionAuditoria.LOGIN_EXITOSO, "USUARIOS", "1", "OK", ResultadoAuditoria.EXITO);
        auditoriaService.registrarConUsuario("user2", "OPERADOR", 2L, AccionAuditoria.LOGIN_FALLIDO, "USUARIOS", "2", "Fallo", ResultadoAuditoria.FALLO);

        assertThat(auditoriaService.contar(filtro)).isEqualTo(2);
    }
}
