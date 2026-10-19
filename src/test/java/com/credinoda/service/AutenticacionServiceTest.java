package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.UsuarioBloqueadoException;
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
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de AutenticacionService (CU-018, RN-024, VP-019)")
class AutenticacionServiceTest {

    private Connection connection;
    private UsuarioRepository usuarioRepository;
    private BCryptPasswordHasher passwordHasher;
    private LocalDateTime currentTime;
    private AutenticacionService authService;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DatabaseConnection.getConnection("jdbc:sqlite::memory:");
        SchemaInitializer.inicializar(connection);
        usuarioRepository = new UsuarioRepository(connection);
        passwordHasher = new BCryptPasswordHasher();

        currentTime = LocalDateTime.of(2026, 10, 18, 10, 0, 0);
        authService = new AutenticacionService(usuarioRepository, passwordHasher, () -> currentTime);

        // Crear usuario de prueba
        Usuario u = new Usuario("operador1", passwordHasher.hash("ClaveSegura123"), "Juan Perez", Rol.OPERADOR);
        usuarioRepository.crear(u);
    }

    @AfterEach
    void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }

    @Test
    @DisplayName("VP-019: Autenticación exitosa con credenciales válidas")
    void testAutenticacionExitosa() throws SQLException {
        SesionUsuario sesion = authService.autenticar("operador1", "ClaveSegura123");

        assertThat(sesion).isNotNull();
        assertThat(sesion.username()).isEqualTo("operador1");
        assertThat(sesion.nombreCompleto()).isEqualTo("Juan Perez");
        assertThat(sesion.rol()).isEqualTo(Rol.OPERADOR);

        Usuario enBd = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
        assertThat(enBd.getIntentosFallidos()).isZero();
        assertThat(enBd.getUltimoAcceso()).isNotNull();
    }

    @Test
    @DisplayName("VP-019: Falla con credenciales inválidas e incrementa intentos fallidos")
    void testCredencialesInvalidasIncrementaIntentos() throws SQLException {
        assertThatThrownBy(() -> authService.autenticar("operador1", "PasswordErroneo"))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("Credenciales inválidas");

        Usuario enBd = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
        assertThat(enBd.getIntentosFallidos()).isEqualTo(1);
        assertThat(enBd.getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("VP-019: Bloqueo de cuenta tras 5 intentos fallidos consecutivos durante 15 minutos")
    void testBloqueoTras5IntentosFallidosYDesbloqueoTemporal() throws SQLException {
        // Ejecutar 4 intentos fallidos
        for (int i = 1; i <= 4; i++) {
            assertThatThrownBy(() -> authService.autenticar("operador1", "PasswordErroneo"))
                    .isInstanceOf(AutenticacionException.class);
            Usuario u = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
            assertThat(u.getIntentosFallidos()).isEqualTo(i);
            assertThat(u.getBloqueadoHasta()).isNull();
        }

        // 5to intento fallido: debe bloquear la cuenta
        assertThatThrownBy(() -> authService.autenticar("operador1", "PasswordErroneo"))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("bloqueada temporalmente por 15 minutos");

        Usuario bloqueado = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
        assertThat(bloqueado.getIntentosFallidos()).isEqualTo(5);
        assertThat(bloqueado.getBloqueadoHasta()).isNotNull();

        // 6to intento durante el periodo de bloqueo (incluso con la clave correcta): debe lanzar UsuarioBloqueadoException
        assertThatThrownBy(() -> authService.autenticar("operador1", "ClaveSegura123"))
                .isInstanceOf(UsuarioBloqueadoException.class)
                .hasMessageContaining("bloqueada temporalmente");

        // Simular el avance del tiempo 10 minutos (aún bloqueada)
        currentTime = currentTime.plusMinutes(10);
        assertThatThrownBy(() -> authService.autenticar("operador1", "ClaveSegura123"))
                .isInstanceOf(UsuarioBloqueadoException.class);

        // Simular avance de 5 minutos y 1 segundo más (total 15m 1s -> expirado el bloqueo)
        currentTime = currentTime.plusMinutes(5).plusSeconds(1);

        // Debe autenticar exitosamente y resetear el contador de intentos
        SesionUsuario sesion = authService.autenticar("operador1", "ClaveSegura123");
        assertThat(sesion).isNotNull();

        Usuario restablecido = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
        assertThat(restablecido.getIntentosFallidos()).isZero();
        assertThat(restablecido.getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("Debe rechazar autenticación si la cuenta se encuentra desactivada")
    void testCuentaDesactivadaRechazaAutenticacion() throws SQLException {
        Usuario u = usuarioRepository.obtenerPorUsername("operador1").orElseThrow();
        u.setActivo(false);
        usuarioRepository.actualizar(u);

        assertThatThrownBy(() -> authService.autenticar("operador1", "ClaveSegura123"))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("desactivada");
    }

    @Test
    @DisplayName("Debe rechazar autenticación para un usuario inexistente")
    void testUsuarioInexistenteRechazaAutenticacion() {
        assertThatThrownBy(() -> authService.autenticar("fantasma", "ClaveCualquiera"))
                .isInstanceOf(AutenticacionException.class)
                .hasMessageContaining("Credenciales inválidas");
    }
}
