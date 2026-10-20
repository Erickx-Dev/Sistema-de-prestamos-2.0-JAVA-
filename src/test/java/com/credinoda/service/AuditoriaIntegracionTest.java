package com.credinoda.service;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.MontoInvalidoException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.Cuota;
import com.credinoda.domain.model.EventoAuditoria;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Pago;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.AuditoriaRepository;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import com.credinoda.persistence.repository.PagoRepository;
import com.credinoda.persistence.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas de Integración de Auditoría y Bitácora Inmutable (VP-022, VP-023, VP-024)")
class AuditoriaIntegracionTest {

    @TempDir
    Path tempDir;

    private Path dbFile;
    private Path backupsDir;
    private Connection connection;

    private AuditoriaRepository auditoriaRepository;
    private AuditoriaService auditoriaService;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private PagoRepository pagoRepository;
    private UsuarioRepository usuarioRepository;

    private ClienteService clienteService;
    private CreditoService creditoService;
    private PagoService pagoService;
    private BackupService backupService;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() throws SQLException {
        dbFile = tempDir.resolve("test_auditoria.db");
        backupsDir = tempDir.resolve("backups");

        connection = DatabaseConnection.getConnection("jdbc:sqlite:" + dbFile.toAbsolutePath());
        SchemaInitializer.inicializar(connection);

        auditoriaRepository = new AuditoriaRepository(connection);
        auditoriaService = new AuditoriaService(auditoriaRepository);

        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        pagoRepository = new PagoRepository(connection);
        usuarioRepository = new UsuarioRepository(connection);

        Autorizador autorizador = new AutorizacionService();
        PasswordHasher hasher = new BCryptPasswordHasher();

        clienteService = new ClienteService(clienteRepository, creditoRepository, autorizador, auditoriaService);
        creditoService = new CreditoService(connection, creditoRepository, clienteRepository, pagoRepository, autorizador, auditoriaService);
        pagoService = new PagoService(connection, pagoRepository, creditoRepository, creditoService, autorizador, auditoriaService);
        backupService = new BackupService(connection, backupsDir, autorizador, auditoriaService);
        usuarioService = new UsuarioService(usuarioRepository, hasher, autorizador, auditoriaService);

        ContextoSeguridad.setSesionActual(new SesionUsuario(1, "admin", "Administrador General", Rol.ADMINISTRADOR, LocalDateTime.now()));
    }

    @AfterEach
    void tearDown() throws SQLException {
        ContextoSeguridad.limpiarSesion();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("VP-022: Persistencia atómica de crédito y su evento de auditoría en la misma transacción JDBC")
    void testVP022PersistenciaAtomicaCreditoConAuditoria() throws SQLException {
        Cliente cliente = clienteService.crearCliente("Roberto Gomez", "3001234567", "11223344");

        Credito credito = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("100000.00"),
                new BigDecimal("0.10"),
                3,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );

        assertThat(credito.getId()).isPositive();

        // Verificar que el evento de auditoría CREAR_CREDITO fue persistido en la BD
        FiltroAuditoria filtro = new FiltroAuditoria(null, null, null, AccionAuditoria.CREAR_CREDITO, null, null, 10, 0);
        List<EventoAuditoria> eventos = auditoriaService.buscar(filtro);

        assertThat(eventos).hasSize(1);
        EventoAuditoria evento = eventos.getFirst();
        assertThat(evento.getEntidad()).isEqualTo("CREDITOS");
        assertThat(evento.getEntidadId()).isEqualTo(String.valueOf(credito.getId()));
        assertThat(evento.getUsername()).isEqualTo("admin");
        assertThat(evento.getDetalle()).contains("Capital: 100000.00");
    }

    @Test
    @DisplayName("VP-022: Persistencia atómica de pago y su evento de auditoría en la misma transacción JDBC")
    void testVP022PersistenciaAtomicaPagoConAuditoria() throws SQLException {
        Cliente cliente = clienteService.crearCliente("Ana Maria", "3119876543", "55667788");
        Credito credito = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("200000.00"),
                new BigDecimal("0.10"),
                2,
                FrecuenciaPago.SEMANAL,
                LocalDate.now()
        );

        List<Cuota> cuotas = creditoService.obtenerCuotasCredito(credito.getId());
        Cuota cuota1 = cuotas.getFirst();

        Pago pago = pagoService.registrarPago(credito.getId(), cuota1.getId(), new BigDecimal("30000.00"), LocalDate.now());
        assertThat(pago.getId()).isPositive();

        // Verificar que el evento REGISTRAR_PAGO fue persistido
        FiltroAuditoria filtro = new FiltroAuditoria(null, null, null, AccionAuditoria.REGISTRAR_PAGO, null, null, 10, 0);
        List<EventoAuditoria> eventos = auditoriaService.buscar(filtro);

        assertThat(eventos).hasSize(1);
        EventoAuditoria ev = eventos.getFirst();
        assertThat(ev.getEntidad()).isEqualTo("PAGOS");
        assertThat(ev.getEntidadId()).isEqualTo(String.valueOf(pago.getId()));
        assertThat(ev.getDetalle()).contains("30000.00");
    }

    @Test
    @DisplayName("VP-022: Rollback atómico conjunto - Si la operación financiera falla por sobrepago de cuota, no se persiste ni el pago ni el evento de auditoría")
    void testVP022RollbackAtomicoPagoConAuditoria() throws SQLException {
        Cliente cliente = clienteService.crearCliente("Luis Diaz", "3201112233", "99887766");
        Credito credito = creditoService.crearCredito(
                cliente.getId(),
                new BigDecimal("50000.00"),
                new BigDecimal("0.10"),
                1,
                FrecuenciaPago.QUINCENAL,
                LocalDate.now()
        );

        Cuota cuota = creditoService.obtenerCuotasCredito(credito.getId()).getFirst();
        BigDecimal sobrepago = cuota.getMonto().add(new BigDecimal("1000.00"));

        assertThatThrownBy(() -> pagoService.registrarPago(credito.getId(), cuota.getId(), sobrepago, LocalDate.now()))
                .isInstanceOf(MontoInvalidoException.class);

        // Verificar que no se creó ningún pago para el crédito
        BigDecimal pagado = pagoRepository.obtenerTotalPagadoCredito(credito.getId());
        assertThat(pagado).isEqualByComparingTo(BigDecimal.ZERO);

        // Verificar que no se creó ningún evento REGISTRAR_PAGO en auditoría
        FiltroAuditoria filtro = new FiltroAuditoria(null, null, null, AccionAuditoria.REGISTRAR_PAGO, null, null, 10, 0);
        List<EventoAuditoria> eventos = auditoriaService.buscar(filtro);
        assertThat(eventos).isEmpty();
    }

    @Test
    @DisplayName("VP-023: Inmutabilidad estricta garantizada por triggers SQLite (trg_auditoria_no_update, trg_auditoria_no_delete)")
    void testVP023InmutabilidadBitacoraAuditoriaEnIntegracion() throws SQLException {
        clienteService.crearCliente("Cliente Auditoria", "3000000000", "00000001");

        FiltroAuditoria f = new FiltroAuditoria(null, null, null, AccionAuditoria.CREAR_CLIENTE, null, null, 1, 0);
        EventoAuditoria evento = auditoriaService.buscar(f).getFirst();

        // Intento directo de UPDATE
        assertThatThrownBy(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("UPDATE auditoria SET detalle = 'CORRUPTO' WHERE id = " + evento.getId());
            }
        }).isInstanceOf(SQLException.class)
          .hasMessageContaining("inmutable");

        // Intento directo de DELETE
        assertThatThrownBy(() -> {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("DELETE FROM auditoria WHERE id = " + evento.getId());
            }
        }).isInstanceOf(SQLException.class)
          .hasMessageContaining("inmutable");
    }

    @Test
    @DisplayName("VP-024: Restauración de respaldo preserva íntegramente las tablas 'usuarios' y 'auditoria' (RN-029)")
    void testVP024RestauracionPreservaUsuariosYAuditoria() throws SQLException {
        // 1. Estado inicial: Crear usuario administrador, operador y cliente original
        usuarioService.crearUsuario("admin", "Admin1234*", "Administrador Principal", Rol.ADMINISTRADOR);
        usuarioService.crearUsuario("cajero1", "ClaveSegura123*", "Cajero Uno", Rol.OPERADOR);
        clienteService.crearCliente("Cliente Base", "3001112233", "11111111");

        // 2. Generar backup del estado original
        Path backupPath = backupService.crearBackup(null, "snapshot_inicial");

        // 3. Modificaciones posteriores al backup:
        // Crear un nuevo usuario 'cajero2'
        usuarioService.crearUsuario("cajero2", "ClaveSegura456*", "Cajero Dos", Rol.OPERADOR);
        // Crear un nuevo cliente posterior
        clienteService.crearCliente("Cliente Posterior", "3002223344", "22222222");

        // Total de usuarios activos antes de restaurar: admin (de inicialización) + cajero1 + cajero2 = 3
        assertThat(usuarioService.listarUsuarios()).hasSize(3);
        // Total de clientes antes de restaurar: 2
        assertThat(clienteService.buscarClientes(null)).hasSize(2);

        int auditoriasAntesRestauracion = auditoriaService.buscar(new FiltroAuditoria(null, null, null, null, null, null, 1000, 0)).size();

        // 4. Restaurar el backup inicial
        backupService.restaurarBackup(backupPath);

        // 5. Validar que la tabla de negocio 'clientes' fue restaurada al estado del backup
        List<Cliente> clientesRestaurados = clienteService.buscarClientes(null);
        assertThat(clientesRestaurados).hasSize(1);
        assertThat(clientesRestaurados.getFirst().getDocumento()).isEqualTo("11111111");

        // 6. Validar que la tabla 'usuarios' se preservó intacta (cajero2 NO fue eliminado)
        List<Usuario> usuariosRestaurados = usuarioService.listarUsuarios();
        assertThat(usuariosRestaurados).hasSize(3);
        assertThat(usuariosRestaurados.stream().map(Usuario::getUsername))
                .contains("admin", "cajero1", "cajero2");

        // 7. Validar que la tabla 'auditoria' se preservó intacta (no se vació y contiene el evento RESTAURAR_BACKUP)
        List<EventoAuditoria> eventosDespues = auditoriaService.buscar(new FiltroAuditoria(null, null, null, null, null, null, 1000, 0));
        // Se preservaron todos los eventos anteriores + el evento RESTAURAR_BACKUP
        assertThat(eventosDespues.size()).isGreaterThanOrEqualTo(auditoriasAntesRestauracion + 1);
        assertThat(eventosDespues.stream().anyMatch(e -> e.getAccion() == AccionAuditoria.RESTAURAR_BACKUP)).isTrue();
    }
}
