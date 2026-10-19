package com.credinoda.service;

import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.DatabaseConnection;
import com.credinoda.persistence.SchemaInitializer;
import com.credinoda.persistence.repository.UsuarioRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de UsuarioService (CU-019, CU-020, RN-022, RN-025, VP-021)")
class UsuarioServiceTest {

    private Connection connection;
    private UsuarioRepository usuarioRepository;
    private BCryptPasswordHasher passwordHasher;
    private UsuarioService usuarioService;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        usuarioRepository = new UsuarioRepository(connection);
        passwordHasher = new BCryptPasswordHasher();
        usuarioService = new UsuarioService(usuarioRepository, passwordHasher);
    }

    @AfterEach
    void tearDown() throws SQLException {
        ContextoSeguridad.limpiar();
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("Debe registrar un nuevo usuario con contraseña hasheada y username normalizado")
    void testCrearUsuarioExitoso() {
        Usuario creado = usuarioService.crearUsuario("Analista_01", "ClaveSegura123", "Ana Morales", Rol.OPERADOR);

        assertThat(creado.getId()).isPositive();
        assertThat(creado.getUsername()).isEqualTo("analista_01");
        assertThat(creado.getNombreCompleto()).isEqualTo("Ana Morales");
        assertThat(creado.getRol()).isEqualTo(Rol.OPERADOR);
        assertThat(creado.isActivo()).isTrue();
        assertThat(passwordHasher.verify("ClaveSegura123", creado.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Debe fallar al registrar un usuario con username duplicado o formato inválido")
    void testCrearUsuarioValidacionesUsername() {
        usuarioService.crearUsuario("admin_app", "ClaveValida123", "Admin Principal", Rol.ADMINISTRADOR);

        // Duplicado
        assertThatThrownBy(() -> usuarioService.crearUsuario("ADMIN_APP", "OtraClave123", "Admin Dos", Rol.ADMINISTRADOR))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe un usuario");

        // Formato inválido (menos de 3 caracteres)
        assertThatThrownBy(() -> usuarioService.crearUsuario("ab", "ClaveValida123", "Corto", Rol.OPERADOR))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("al menos 3 caracteres");

        // Contraseña corta (menos de 6 caracteres)
        assertThatThrownBy(() -> usuarioService.crearUsuario("nuevo_user", "12345", "Test", Rol.OPERADOR))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("al menos 6 caracteres");
    }

    @Test
    @DisplayName("VP-021: Invariante RN-025 - Protección del único Administrador activo del sistema")
    void testProteccionUltimoAdministradorActivo() {
        // 1. Crear el único administrador activo del sistema
        Usuario admin1 = usuarioService.crearUsuario("admin_unico", "ClaveAdmin123", "Admin Único", Rol.ADMINISTRADOR);

        // 2. Intentar desactivar al único administrador debe fallar
        assertThatThrownBy(() -> usuarioService.cambiarEstadoActivo(admin1.getId(), false))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No se puede desactivar ni modificar el rol del único Administrador activo");

        // 3. Intentar degradar el rol del único administrador a OPERADOR debe fallar
        assertThatThrownBy(() -> usuarioService.actualizarUsuario(admin1.getId(), null, Rol.OPERADOR, null))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No se puede desactivar ni modificar el rol del único Administrador activo");

        // 4. Crear un segundo administrador activo
        Usuario admin2 = usuarioService.crearUsuario("admin_segundo", "ClaveAdmin456", "Admin Segundo", Rol.ADMINISTRADOR);

        // 5. Ahora sí se puede desactivar admin1 porque admin2 permanece activo
        Usuario admin1Desactivado = usuarioService.cambiarEstadoActivo(admin1.getId(), false);
        assertThat(admin1Desactivado.isActivo()).isFalse();

        // 6. Ahora admin2 es el único administrador activo restante; intentar desactivarlo debe fallar
        assertThatThrownBy(() -> usuarioService.cambiarEstadoActivo(admin2.getId(), false))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No se puede desactivar ni modificar el rol del único Administrador activo");
    }

    @Test
    @DisplayName("Debe actualizar el nombre y listar todos los usuarios")
    void testActualizarYListar() {
        Usuario u1 = usuarioService.crearUsuario("user_a", "ClaveSegura1", "Nombre A", Rol.OPERADOR);
        usuarioService.crearUsuario("user_b", "ClaveSegura2", "Nombre B", Rol.OPERADOR);

        usuarioService.actualizarUsuario(u1.getId(), "Nombre Modificado", null, null);
        Usuario consultado = usuarioService.obtenerPorId(u1.getId()).orElseThrow();
        assertThat(consultado.getNombreCompleto()).isEqualTo("Nombre Modificado");

        List<Usuario> todos = usuarioService.listarUsuarios();
        assertThat(todos).hasSize(2);
    }

    @Test
    @DisplayName("Debe cambiar la contraseña verificando la clave actual")
    void testCambiarPassword() {
        Usuario user = usuarioService.crearUsuario("operador_pass", "ClaveOriginal123", "Operador", Rol.OPERADOR);

        // Falla con contraseña actual errónea
        assertThatThrownBy(() -> usuarioService.cambiarPassword(user.getId(), "ClaveIncorrecta", "NuevaClave456"))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("La contraseña actual es incorrecta");

        // Falla con nueva contraseña igual a la actual
        assertThatThrownBy(() -> usuarioService.cambiarPassword(user.getId(), "ClaveOriginal123", "ClaveOriginal123"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("debe ser diferente a la anterior");

        // Éxito con contraseña válida
        usuarioService.cambiarPassword(user.getId(), "ClaveOriginal123", "NuevaClaveSegura456");

        Usuario actualizado = usuarioService.obtenerPorId(user.getId()).orElseThrow();
        assertThat(passwordHasher.verify("NuevaClaveSegura456", actualizado.getPasswordHash())).isTrue();
        assertThat(passwordHasher.verify("ClaveOriginal123", actualizado.getPasswordHash())).isFalse();
    }
}
