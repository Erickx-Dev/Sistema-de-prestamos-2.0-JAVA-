package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.UsuarioBloqueadoException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.repository.UsuarioRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Servicio de autenticación, control de intentos fallidos, gestión de sesiones y trazabilidad de accesos
 * (CU-018, RN-022, RN-023, RN-027, VP-018, VP-019).
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
    private final AuditoriaService auditoriaService;

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordHasher passwordHasher,
                                Supplier<LocalDateTime> clock,
                                AuditoriaService auditoriaService) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "PasswordHasher no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "El proveedor de fecha/hora (clock) no puede ser nulo");
        this.auditoriaService = auditoriaService;
    }

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordHasher passwordHasher,
                                Supplier<LocalDateTime> clock) {
        this(usuarioRepository, passwordHasher, clock, null);
    }

    public AutenticacionService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this(usuarioRepository, passwordHasher, LocalDateTime::now, null);
    }

    public AutenticacionService(UsuarioRepository usuarioRepository,
                                PasswordHasher passwordHasher,
                                AuditoriaService auditoriaService) {
        this(usuarioRepository, passwordHasher, LocalDateTime::now, auditoriaService);
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
            if (auditoriaService != null) {
                auditoriaService.registrarConUsuario(
                        username.trim(),
                        "DESCONOCIDO",
                        null,
                        AccionAuditoria.LOGIN_FALLIDO,
                        "USUARIOS",
                        null,
                        "Usuario inexistente",
                        ResultadoAuditoria.FALLO
                );
            }
            // Mitigación de enumeración de usuarios: mensaje idéntico
            throw new AutenticacionException(MENSAJE_CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = usuarioOpt.get();

        if (!usuario.isActivo()) {
            if (auditoriaService != null) {
                auditoriaService.registrarConUsuario(
                        usuario.getUsername(),
                        usuario.getRol().name(),
                        (long) usuario.getId(),
                        AccionAuditoria.LOGIN_FALLIDO,
                        "USUARIOS",
                        String.valueOf(usuario.getId()),
                        "Cuenta desactivada",
                        ResultadoAuditoria.DENEGADO
                );
            }
            throw new AutenticacionException(MENSAJE_CUENTA_DESACTIVADA);
        }

        // Verificar si la cuenta está actualmente bloqueada
        if (usuario.estaBloqueado(ahora)) {
            if (auditoriaService != null) {
                auditoriaService.registrarConUsuario(
                        usuario.getUsername(),
                        usuario.getRol().name(),
                        (long) usuario.getId(),
                        AccionAuditoria.LOGIN_FALLIDO,
                        "USUARIOS",
                        String.valueOf(usuario.getId()),
                        "Intento de acceso sobre cuenta bloqueada hasta " + usuario.getBloqueadoHasta(),
                        ResultadoAuditoria.DENEGADO
                );
            }
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

            if (auditoriaService != null) {
                auditoriaService.registrarConUsuario(
                        usuario.getUsername(),
                        usuario.getRol().name(),
                        (long) usuario.getId(),
                        AccionAuditoria.LOGIN_FALLIDO,
                        "USUARIOS",
                        String.valueOf(usuario.getId()),
                        "Credenciales incorrectas (intento " + nuevosIntentos + "/" + MAX_INTENTOS_FALLIDOS + ")",
                        ResultadoAuditoria.FALLO
                );
                if (bloqueadoHasta != null) {
                    auditoriaService.registrarConUsuario(
                            usuario.getUsername(),
                            usuario.getRol().name(),
                            (long) usuario.getId(),
                            AccionAuditoria.BLOQUEO_USUARIO,
                            "USUARIOS",
                            String.valueOf(usuario.getId()),
                            "Bloqueado por 15 minutos tras " + MAX_INTENTOS_FALLIDOS + " intentos fallidos consecutivos",
                            ResultadoAuditoria.FALLO
                    );
                }
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

        if (auditoriaService != null) {
            auditoriaService.registrarConUsuario(
                    usuario.getUsername(),
                    usuario.getRol().name(),
                    (long) usuario.getId(),
                    AccionAuditoria.LOGIN_EXITOSO,
                    "USUARIOS",
                    String.valueOf(usuario.getId()),
                    "Inicio de sesión exitoso",
                    ResultadoAuditoria.EXITO
            );
        }

        return sesion;
    }

    /**
     * Cierra la sesión activa actual en {@link ContextoSeguridad} y audita el evento.
     */
    public void cerrarSesion() {
        SesionUsuario sesion = ContextoSeguridad.getSesionActual();
        if (auditoriaService != null && sesion != null && sesion.usuarioId() > 0) {
            auditoriaService.registrarConUsuario(
                    sesion.username(),
                    sesion.rol().name(),
                    (long) sesion.usuarioId(),
                    AccionAuditoria.LOGOUT,
                    "USUARIOS",
                    String.valueOf(sesion.usuarioId()),
                    "Cierre voluntario de sesión",
                    ResultadoAuditoria.EXITO
            );
        }
        ContextoSeguridad.limpiar();
    }
}
