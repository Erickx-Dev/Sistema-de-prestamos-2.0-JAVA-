package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.UsuarioBloqueadoException;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.repository.UsuarioRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Servicio de autenticación, control de intentos fallidos y gestión de sesiones (CU-018, RN-022, RN-023, VP-018, VP-019).
 */
public class AutenticacionService {

    public static final int MAX_INTENTOS_FALLIDOS = 5;
    public static final int DURACION_BLOQUEO_MINUTOS = 15;
    public static final String MENSAJE_CREDENCIALES_INVALIDAS = "Credenciales inválidas. Verifique su usuario y contraseña.";
    public static final String MENSAJE_CUENTA_BLOQUEADA = "La cuenta se encuentra bloqueada temporalmente por 15 minutos debido a exceso de intentos fallidos. Intente nuevamente más tarde.";
    public static final String MENSAJE_CUENTA_DESACTIVADA = "La cuenta de usuario se encuentra desactivada. Contacte al Administrador.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private final Supplier<LocalDateTime> clock;

    public AutenticacionService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this(usuarioRepository, passwordHasher, LocalDateTime::now);
    }

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordHasher passwordHasher,
                                Supplier<LocalDateTime> clock) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "PasswordHasher no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "El proveedor de fecha/hora (clock) no puede ser nulo");
    }

    /**
     * Autentica a un usuario verificando credenciales, estado de la cuenta y bloqueos por intentos fallidos.
     *
     * @param username Nombre de usuario.
     * @param rawPassword Contraseña en texto plano.
     * @return SesionUsuario autenticada y establecida en {@link ContextoSeguridad}.
     * @throws AutenticacionException Si las credenciales son incorrectas o la cuenta está inactiva.
     * @throws UsuarioBloqueadoException Si la cuenta está bloqueada temporalmente por intentos fallidos.
     */
    public SesionUsuario autenticar(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null || rawPassword.isEmpty()) {
            throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        LocalDateTime ahora = clock.get();
        Optional<Usuario> usuarioOpt;
        try {
            usuarioOpt = usuarioRepository.obtenerPorUsername(username.trim());
        } catch (SQLException e) {
            throw new AutenticacionException("Error interno de persistencia al verificar usuario", e);
        }

        if (usuarioOpt.isEmpty()) {
            // Mitigación de enumeración de usuarios: mensaje idéntico
            throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = usuarioOpt.get();

        if (!usuario.isActivo()) {
            throw new AutenticacionException(MENSAJE_CUENTA_DESACTIVADA);
        }

        // Verificar si la cuenta está actualmente bloqueada
        if (usuario.estaBloqueado(ahora)) {
            throw new UsuarioBloqueadoException(MENSAJE_CUENTA_BLOQUEADA, usuario.getBloqueadoHasta());
        }

        boolean coincide = passwordHasher.verify(rawPassword, usuario.getPasswordHash());
        if (!coincide) {
            int nuevosIntentos = usuario.getIntentosFallidos() + 1;
            LocalDateTime bloqueadoHasta = null;

            if (nuevosIntentos >= MAX_INTENTOS_FALLIDOS) {
                bloqueadoHasta = ahora.plusMinutes(DURACION_BLOQUEO_MINUTOS);
            }

            try {
                usuarioRepository.actualizarSeguridad(usuario.getId(), nuevosIntentos, bloqueadoHasta, null);
            } catch (SQLException e) {
                // Registrar advertencia interna
            }

            if (bloqueadoHasta != null) {
                throw new UsuarioBloqueadoException(MENSAJE_CUENTA_BLOQUEADA, bloqueadoHasta);
            } else {
                throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
            }
        }

        // Login exitoso: resetear contador de intentos fallidos y registrar último acceso
        try {
            usuarioRepository.actualizarSeguridad(usuario.getId(), 0, null, ahora);
        } catch (SQLException e) {
            // Ignorar fallo no crítico de registro de último acceso
        }

        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuario.setUltimoAcceso(ahora);

        SesionUsuario sesion = SesionUsuario.de(usuario);
        ContextoSeguridad.setSesionActual(sesion);
        return sesion;
    }

    /**
     * Cierra la sesión activa actual en {@link ContextoSeguridad}.
     */
    public void cerrarSesion() {
        ContextoSeguridad.limpiar();
    }
}
