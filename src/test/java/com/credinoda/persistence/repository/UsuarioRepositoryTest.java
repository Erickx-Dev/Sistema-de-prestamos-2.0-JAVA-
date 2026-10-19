package com.credinoda.persistence.repository;

import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de UsuarioRepository (CU-018, CU-019)")
class UsuarioRepositoryTest {

    private Connection connection;
    private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        usuarioRepository = new UsuarioRepository(connection);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe crear un usuario y asignar su ID autogenerado")
    void testCrearUsuario() throws SQLException {
        Usuario usuario = new Usuario("esojo", "$2b$12$fakeHash", "Erickson Sojo", Rol.ADMINISTRADOR);
        Usuario creado = usuarioRepository.crear(usuario);

        assertThat(creado.getId()).isPositive();
        assertThat(creado.getUsername()).isEqualTo("esojo");
        assertThat(creado.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(creado.isActivo()).isTrue();
        assertThat(creado.getIntentosFallidos()).isZero();
        assertThat(creado.getBloqueadoHasta()).isNull();

        Optional<Usuario> consultado = usuarioRepository.obtenerPorId(creado.getId());
        assertThat(consultado).isPresent();
        assertThat(consultado.get().getUsername()).isEqualTo("esojo");
        assertThat(consultado.get().getNombreCompleto()).isEqualTo("Erickson Sojo");
    }

    @Test
    @DisplayName("Debe buscar usuario por username de forma insensible a mayúsculas")
    void testBuscarPorUsername() throws SQLException {
        usuarioRepository.crear(new Usuario("carlos_c", "$2b$12$hash", "Carlos Cobrador", Rol.OPERADOR));

        Optional<Usuario> u1 = usuarioRepository.obtenerPorUsername("carlos_c");
        Optional<Usuario> u2 = usuarioRepository.obtenerPorUsername("CARLOS_C");

        assertThat(u1).isPresent();
        assertThat(u2).isPresent();
        assertThat(u1.get().getId()).isEqualTo(u2.get().getId());
    }

    @Test
    @DisplayName("Debe fallar al intentar registrar un username duplicado")
    void testUnicidadUsername() throws SQLException {
        usuarioRepository.crear(new Usuario("admin", "$2b$12$hash1", "Admin Uno", Rol.ADMINISTRADOR));

        Usuario duplicado = new Usuario("admin", "$2b$12$hash2", "Admin Dos", Rol.ADMINISTRADOR);
        assertThatThrownBy(() -> usuarioRepository.crear(duplicado))
                .isInstanceOf(SQLException.class);
    }

    @Test
    @DisplayName("Debe actualizar los datos generales de un usuario")
    void testActualizarUsuario() throws SQLException {
        Usuario creado = usuarioRepository.crear(new Usuario("operador1", "$2b$12$hash", "Juan Perez", Rol.OPERADOR));

        creado.setNombreCompleto("Juan Carlos Perez");
        creado.setRol(Rol.ADMINISTRADOR);
        creado.setActivo(false);
        usuarioRepository.actualizar(creado);

        Usuario modificado = usuarioRepository.obtenerPorId(creado.getId()).orElseThrow();
        assertThat(modificado.getNombreCompleto()).isEqualTo("Juan Carlos Perez");
        assertThat(modificado.getRol()).isEqualTo(Rol.ADMINISTRADOR);
        assertThat(modificado.isActivo()).isFalse();
    }

    @Test
    @DisplayName("Debe actualizar la contraseña de un usuario")
    void testActualizarPassword() throws SQLException {
        Usuario creado = usuarioRepository.crear(new Usuario("user1", "$2b$12$oldHash", "User Uno", Rol.OPERADOR));

        usuarioRepository.actualizarPassword(creado.getId(), "$2b$12$newHash");

        Usuario modificado = usuarioRepository.obtenerPorId(creado.getId()).orElseThrow();
        assertThat(modificado.getPasswordHash()).isEqualTo("$2b$12$newHash");
    }

    @Test
    @DisplayName("Debe actualizar el estado de seguridad (intentos y bloqueo)")
    void testActualizarSeguridad() throws SQLException {
        Usuario creado = usuarioRepository.crear(new Usuario("user2", "$2b$12$hash", "User Dos", Rol.OPERADOR));

        LocalDateTime bloqueo = LocalDateTime.now().plusMinutes(15);
        usuarioRepository.actualizarSeguridad(creado.getId(), 5, bloqueo);

        Usuario modificado = usuarioRepository.obtenerPorId(creado.getId()).orElseThrow();
        assertThat(modificado.getIntentosFallidos()).isEqualTo(5);
        assertThat(modificado.getBloqueadoHasta()).isNotNull();
        assertThat(modificado.estaBloqueado(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("Debe contar exclusivamente administradores activos")
    void testContarAdministradoresActivos() throws SQLException {
        assertThat(usuarioRepository.contarAdministradoresActivos()).isZero();

        // 1. Admin activo
        usuarioRepository.crear(new Usuario("admin1", "$2b$12$h", "Admin 1", Rol.ADMINISTRADOR));
        assertThat(usuarioRepository.contarAdministradoresActivos()).isEqualTo(1);

        // 2. Operador activo (no cuenta)
        usuarioRepository.crear(new Usuario("op1", "$2b$12$h", "Operador 1", Rol.OPERADOR));
        assertThat(usuarioRepository.contarAdministradoresActivos()).isEqualTo(1);

        // 3. Admin inactivo (no cuenta)
        Usuario a2 = new Usuario("admin2", "$2b$12$h", "Admin 2", Rol.ADMINISTRADOR);
        a2.setActivo(false);
        usuarioRepository.crear(a2);
        assertThat(usuarioRepository.contarAdministradoresActivos()).isEqualTo(1);

        // 4. Segundo admin activo
        usuarioRepository.crear(new Usuario("admin3", "$2b$12$h", "Admin 3", Rol.ADMINISTRADOR));
        assertThat(usuarioRepository.contarAdministradoresActivos()).isEqualTo(2);
    }

    @Test
    @DisplayName("Debe contar el total de usuarios y listar todos")
    void testListarYContarUsuarios() throws SQLException {
        assertThat(usuarioRepository.contarUsuarios()).isZero();

        usuarioRepository.crear(new Usuario("u1", "$2b$12$h", "Usuario 1", Rol.ADMINISTRADOR));
        usuarioRepository.crear(new Usuario("u2", "$2b$12$h", "Usuario 2", Rol.OPERADOR));

        assertThat(usuarioRepository.contarUsuarios()).isEqualTo(2);
        List<Usuario> lista = usuarioRepository.listarTodos();
        assertThat(lista).hasSize(2);
        assertThat(lista).extracting(Usuario::getUsername).containsExactly("u1", "u2");
    }
}
