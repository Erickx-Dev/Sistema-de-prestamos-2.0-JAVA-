package com.credinoda.persistence.repository;

import com.credinoda.domain.model.Cliente;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas unitarias de ClienteRepository")
class ClienteRepositoryTest {

    private Connection connection;
    private ClienteRepository clienteRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        clienteRepository = new ClienteRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe crear un cliente y asignar su ID generado automáticamente")
    void testCrearCliente() throws SQLException {
        Cliente cliente = new Cliente("Carlos Mendoza", "3001234567", "1020304050");
        Cliente creado = clienteRepository.crear(cliente);

        assertThat(creado.getId()).isNotNull();
        assertThat(creado.getId()).isPositive();
        assertThat(creado.getNombre()).isEqualTo("Carlos Mendoza");
        assertThat(creado.getTelefono()).isEqualTo("3001234567");
        assertThat(creado.getDocumento()).isEqualTo("1020304050");
        assertThat(creado.getFechaRegistro()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Debe consultar un cliente por su ID")
    void testObtenerPorId() throws SQLException {
        Cliente cliente = clienteRepository.crear(new Cliente("María López", "3119876543", "5544332211"));

        Optional<Cliente> resultado = clienteRepository.obtenerPorId(cliente.getId());
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNombre()).isEqualTo("María López");
        assertThat(resultado.get().getDocumento()).isEqualTo("5544332211");

        Optional<Cliente> noExiste = clienteRepository.obtenerPorId(999);
        assertThat(noExiste).isEmpty();
    }

    @Test
    @DisplayName("Debe consultar un cliente por su documento único")
    void testObtenerPorDocumento() throws SQLException {
        clienteRepository.crear(new Cliente("Andrés Gómez", "3154567890", "987654321"));

        Optional<Cliente> resultado = clienteRepository.obtenerPorDocumento("987654321");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().getNombre()).isEqualTo("Andrés Gómez");

        Optional<Cliente> noExiste = clienteRepository.obtenerPorDocumento("000000000");
        assertThat(noExiste).isEmpty();
    }

    @Test
    @DisplayName("Debe fallar al intentar registrar un documento duplicado (RN-002)")
    void testDocumentoDuplicadoLanzaExcepcion() throws SQLException {
        clienteRepository.crear(new Cliente("Cliente Uno", "3000000001", "DOC-UNICO"));

        Cliente duplicado = new Cliente("Cliente Dos", "3000000002", "DOC-UNICO");
        assertThatThrownBy(() -> clienteRepository.crear(duplicado))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("UNIQUE constraint failed");
    }

    @Test
    @DisplayName("Debe buscar clientes por coincidencia parcial en nombre")
    void testBuscarPorNombre() throws SQLException {
        clienteRepository.crear(new Cliente("Juan Carlos Pérez", "3001", "DOC-1"));
        clienteRepository.crear(new Cliente("Carlos Arturo Martínez", "3002", "DOC-2"));
        clienteRepository.crear(new Cliente("Ana Sofía Ruiz", "3003", "DOC-3"));

        List<Cliente> encontrados = clienteRepository.buscarPorNombre("Carlos");
        assertThat(encontrados).hasSize(2);
        assertThat(encontrados).extracting(Cliente::getNombre)
                .containsExactly("Carlos Arturo Martínez", "Juan Carlos Pérez");
    }

    @Test
    @DisplayName("Debe realizar búsqueda general por nombre o documento (RN-003)")
    void testBuscarGeneral() throws SQLException {
        clienteRepository.crear(new Cliente("Pedro Ramírez", "3001", "11223344"));
        clienteRepository.crear(new Cliente("Laura Restrepo", "3002", "99887766"));

        List<Cliente> porNombre = clienteRepository.buscarGeneral("Pedro");
        assertThat(porNombre).hasSize(1);
        assertThat(porNombre.get(0).getNombre()).isEqualTo("Pedro Ramírez");

        List<Cliente> porDocumento = clienteRepository.buscarGeneral("9988");
        assertThat(porDocumento).hasSize(1);
        assertThat(porDocumento.get(0).getNombre()).isEqualTo("Laura Restrepo");
    }

    @Test
    @DisplayName("Debe actualizar los datos de un cliente existente")
    void testActualizarCliente() throws SQLException {
        Cliente cliente = clienteRepository.crear(new Cliente("Nombre Original", "3001111111", "DOC-ORIGINAL"));

        cliente.setNombre("Nombre Modificado");
        cliente.setTelefono("3009999999");
        cliente.setDocumento("DOC-NUEVO");
        clienteRepository.actualizar(cliente);

        Optional<Cliente> actualizado = clienteRepository.obtenerPorId(cliente.getId());
        assertThat(actualizado).isPresent();
        assertThat(actualizado.get().getNombre()).isEqualTo("Nombre Modificado");
        assertThat(actualizado.get().getTelefono()).isEqualTo("3009999999");
        assertThat(actualizado.get().getDocumento()).isEqualTo("DOC-NUEVO");
    }

    @Test
    @DisplayName("Debe listar todos los clientes en orden alfabético por nombre")
    void testListarTodos() throws SQLException {
        clienteRepository.crear(new Cliente("Zulma Vargas", "3003", "DOC-Z"));
        clienteRepository.crear(new Cliente("Alberto Cano", "3001", "DOC-A"));
        clienteRepository.crear(new Cliente("Mario Bross", "3002", "DOC-M"));

        List<Cliente> todos = clienteRepository.listarTodos();
        assertThat(todos).hasSize(3);
        assertThat(todos).extracting(Cliente::getNombre)
                .containsExactly("Alberto Cano", "Mario Bross", "Zulma Vargas");
    }
}
