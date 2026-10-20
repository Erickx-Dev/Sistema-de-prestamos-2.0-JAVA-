package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AccesoDenegadoException;
import com.credinoda.domain.exception.ClienteNoEncontradoException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.model.Cliente;
import com.credinoda.domain.model.Credito;
import com.credinoda.domain.model.EstadoCredito;
import com.credinoda.domain.model.FrecuenciaPago;
import com.credinoda.domain.model.Rol;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.ClienteRepository;
import com.credinoda.persistence.repository.CreditoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas unitarias de ClienteService")
class ClienteServiceTest {

    private Connection connection;
    private ClienteRepository clienteRepository;
    private CreditoRepository creditoRepository;
    private ClienteService clienteService;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
        creditoRepository = new CreditoRepository(connection);
        clienteService = new ClienteService(clienteRepository, creditoRepository);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ContextoSeguridad.limpiar();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe registrar un cliente válido con sanitización de espacios en blanco (RN-001)")
    void testCrearClienteValido() {
        Cliente creado = clienteService.crearCliente("  Sofía Vergara  ", "  3105551234 ", "  CC10987654  ");

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getNombre()).isEqualTo("Sofía Vergara");
        assertThat(creado.getTelefono()).isEqualTo("3105551234");
        assertThat(creado.getDocumento()).isEqualTo("CC10987654");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("Debe rechazar la creación si el nombre es nulo o vacío tras trim (RN-001)")
    void testCrearClienteNombreInvalido(String nombreInvalido) {
        assertThatThrownBy(() -> clienteService.crearCliente(nombreInvalido, "3001234567", "DOC-001"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("nombre");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Debe rechazar la creación si el teléfono o documento son vacíos tras trim (RN-001)")
    void testCrearClienteTelefonoODocumentoInvalido(String valorInvalido) {
        assertThatThrownBy(() -> clienteService.crearCliente("Pedro", valorInvalido, "DOC-001"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("teléfono");

        assertThatThrownBy(() -> clienteService.crearCliente("Pedro", "3001234567", valorInvalido))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("documento");
    }

    @Test
    @DisplayName("Debe rechazar creación con documento ya existente en el sistema (RN-002)")
    void testCrearClienteDocumentoDuplicado() {
        clienteService.crearCliente("Cliente Uno", "3001112233", "DOC-DUPLICADO");

        assertThatThrownBy(() -> clienteService.crearCliente("Cliente Dos", "3009998877", "DOC-DUPLICADO"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe un cliente con el documento: DOC-DUPLICADO");
    }

    @Test
    @DisplayName("Debe actualizar un cliente existente y sanitizar sus nuevos campos (CU-003, RN-001)")
    void testActualizarClienteExitoso() {
        Cliente creado = clienteService.crearCliente("Nombre Antiguo", "3001", "DOC-1");

        Cliente actualizado = clienteService.actualizarCliente(creado.getId(), "  Nombre Nuevo  ", " 3002 ", " DOC-2 ");

        assertThat(actualizado.getNombre()).isEqualTo("Nombre Nuevo");
        assertThat(actualizado.getTelefono()).isEqualTo("3002");
        assertThat(actualizado.getDocumento()).isEqualTo("DOC-2");
    }

    @Test
    @DisplayName("Debe conservar valores previos si los parámetros son nulos en actualización (RN-001)")
    void testActualizarClienteConservaValoresNulos() {
        Cliente creado = clienteService.crearCliente("Andrés", "300111", "DOC-A");

        Cliente actualizado = clienteService.actualizarCliente(creado.getId(), null, "300999", null);

        assertThat(actualizado.getNombre()).isEqualTo("Andrés");
        assertThat(actualizado.getTelefono()).isEqualTo("300999");
        assertThat(actualizado.getDocumento()).isEqualTo("DOC-A");
    }

    @Test
    @DisplayName("Debe permitir actualizar otros datos conservando el mismo documento sin lanzar duplicidad (RN-002)")
    void testActualizarClienteMismoDocumentoPermitido() {
        Cliente creado = clienteService.crearCliente("Carlos", "3001", "DOC-MISMO");

        Cliente actualizado = clienteService.actualizarCliente(creado.getId(), "Carlos Segundo", "3002", "DOC-MISMO");
        assertThat(actualizado.getNombre()).isEqualTo("Carlos Segundo");
        assertThat(actualizado.getDocumento()).isEqualTo("DOC-MISMO");
    }

    @Test
    @DisplayName("Debe rechazar actualización si el nuevo documento colisiona con otro cliente (RN-002)")
    void testActualizarClienteDocumentoColisionaConOtro() {
        clienteService.crearCliente("Cliente A", "3001", "DOC-A");
        Cliente creadoB = clienteService.crearCliente("Cliente B", "3002", "DOC-B");

        assertThatThrownBy(() -> clienteService.actualizarCliente(creadoB.getId(), null, null, "DOC-A"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe un cliente con el documento: DOC-A");
    }

    @Test
    @DisplayName("Debe lanzar ClienteNoEncontradoException al consultar un ID que no existe")
    void testObtenerClientePorIdInexistente() {
        assertThatThrownBy(() -> clienteService.obtenerClientePorId(9999))
                .isInstanceOf(ClienteNoEncontradoException.class)
                .hasMessageContaining("Cliente con ID 9999 no encontrado.");
    }

    @Test
    @DisplayName("Debe buscar clientes por término general y retornar todos si el término es vacío (RN-003)")
    void testBuscarClientes() {
        clienteService.crearCliente("Ana Morales", "3001", "12345");
        clienteService.crearCliente("Beatriz Morales", "3002", "67890");
        clienteService.crearCliente("Camilo Echeverry", "3003", "11223");

        List<Cliente> filtroMorales = clienteService.buscarClientes("Morales");
        assertThat(filtroMorales).hasSize(2);

        List<Cliente> filtroDoc = clienteService.buscarClientes("6789");
        assertThat(filtroDoc).hasSize(1);
        assertThat(filtroDoc.get(0).getNombre()).isEqualTo("Beatriz Morales");

        List<Cliente> todos = clienteService.buscarClientes("   ");
        assertThat(todos).hasSize(3);
    }

    @Test
    @DisplayName("Debe consultar los créditos otorgados a un cliente (CU-004)")
    void testObtenerCreditosCliente() throws SQLException {
        Cliente cliente = clienteService.crearCliente("Diana Prince", "3001", "DOC-DP");

        creditoRepository.crear(new Credito(
                cliente.getId(), new BigDecimal("100000"), new BigDecimal("0.12"), 1,
                new BigDecimal("12000"), new BigDecimal("112000"), FrecuenciaPago.SEMANAL,
                LocalDate.now(), EstadoCredito.ACTIVO
        ));

        List<Credito> creditos = clienteService.obtenerCreditosCliente(cliente.getId());
        assertThat(creditos).hasSize(1);
        assertThat(creditos.get(0).getClienteId()).isEqualTo(cliente.getId());
    }

    @Test
    @DisplayName("Debe rechazar clientes con datos falsos o erróneos como 'mk' en documento o teléfono")
    void testRechazarClienteConDatosFalsosOMalFormato() {
        // Documento 'mk' (muy corto, sin números ni guión)
        assertThatThrownBy(() -> clienteService.crearCliente("Erickson Sojo", "3001234567", "mk"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("documento");

        // Teléfono 'mk' (letras en vez de números)
        assertThatThrownBy(() -> clienteService.crearCliente("Erickson Sojo", "mk", "10987654"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("teléfono");

        // Nombre de solo 1 letra
        assertThatThrownBy(() -> clienteService.crearCliente("E", "3001234567", "10987654"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("nombre");
    }

    @Test
    @DisplayName("Debe rechazar la actualización de cliente si el usuario activo tiene rol OPERADOR (RN-024, RN-026)")
    void testActualizarClienteDenegadoParaOperador() {
        // Administrador crea el cliente (o sesión de sistema)
        Cliente creado = clienteService.crearCliente("Cliente Prueba", "3001234567", "10203040");

        // Se conmuta a sesión con rol OPERADOR
        SesionUsuario sesionOperador = new SesionUsuario(10, "operador1", "Operador Cobranzas", Rol.OPERADOR);
        ContextoSeguridad.setSesionActual(sesionOperador);

        // Operador intenta actualizar y debe ser denegado estrictamente
        assertThatThrownBy(() -> clienteService.actualizarCliente(creado.getId(), "Nombre Modificado", "3009999999", "10203040"))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("No cuenta con privilegios suficientes");

        // Operador SÍ puede crear nuevos clientes según matriz de permisos
        Cliente nuevoClienteOperador = clienteService.crearCliente("Cliente Registrado Por Operador", "3008888888", "99887766");
        assertThat(nuevoClienteOperador).isNotNull();
        assertThat(nuevoClienteOperador.getId()).isGreaterThan(0);

        // Pero el Operador NO puede modificar ni siquiera el cliente que él mismo creó
        assertThatThrownBy(() -> clienteService.actualizarCliente(nuevoClienteOperador.getId(), "Cliente Mutado", "3008888888", "99887766"))
                .isInstanceOf(AccesoDenegadoException.class)
                .hasMessageContaining("No cuenta con privilegios suficientes");
    }
}
