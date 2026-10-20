package com.credinoda.service;

import com.credinoda.common.Validators;
import com.credinoda.domain.exception.AutenticacionException;
import com.credinoda.domain.exception.DatosInvalidosException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.persistence.repository.UsuarioRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión integral de cuentas de usuario, roles, políticas de seguridad
 * y registro de auditoría (CU-019, CU-020, RN-022, RN-024, RN-025, RN-027, VP-021).
 */
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordHasher passwordHasher;
    private final Autorizador autorizador;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordHasher passwordHasher,
                          Autorizador autorizador,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "PasswordHasher no puede ser nulo");
        this.autorizador = Objects.requireNonNull(autorizador, "Autorizador no puede ser nulo");
        this.auditoriaService = auditoriaService;
    }

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher, Autorizador autorizador) {
        this(usuarioRepository, passwordHasher, autorizador, null);
    }

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordHasher passwordHasher) {
        this(usuarioRepository, passwordHasher, new AutorizacionService(), null);
    }

    /**
     * Registra un nuevo usuario en el sistema previa validación de permisos, formato y unicidad (CU-019, RN-022).
     *
     * @param username Nombre de usuario único.
     * @param rawPassword Contraseña en texto claro.
     * @param nombreCompleto Nombre real del operador.
     * @param rol Rol a asignar (ADMINISTRADOR u OPERADOR).
     * @return Usuario persistido con ID autogenerado.
     */
    public Usuario crearUsuario(String username, String rawPassword, String nombreCompleto, Rol rol) {
        autorizador.requirePermission(Permiso.USUARIOS_GESTIONAR);

        Validators.ResultadoValidacion valUser = Validators.validarUsername(username);
        if (!valUser.valido()) {
            throw new DatosInvalidosException(valUser.mensaje());
        }

        Validators.ResultadoValidacion valPass = Validators.validarPassword(rawPassword);
        if (!valPass.valido()) {
            throw new DatosInvalidosException(valPass.mensaje());
        }

        if (nombreCompleto == null || nombreCompleto.isBlank()) {
            throw new DatosInvalidosException("El nombre completo no puede estar vacío.");
        }

        if (rol == null) {
            throw new DatosInvalidosException("Debe seleccionar un rol para el usuario.");
        }

        String usernameNormalizado = username.trim().toLowerCase();

        try {
            Optional<Usuario> existente = usuarioRepository.obtenerPorUsername(usernameNormalizado);
            if (existente.isPresent()) {
                throw new DatosInvalidosException("Ya existe un usuario con el nombre: " + usernameNormalizado);
            }

            String passwordHash = passwordHasher.hash(rawPassword);
            Usuario nuevo = new Usuario(usernameNormalizado, passwordHash, nombreCompleto.trim(), rol);
            Usuario persistido = usuarioRepository.crear(nuevo);

            if (auditoriaService != null) {
                auditoriaService.registrar(
                        AccionAuditoria.CREAR_USUARIO,
                        "USUARIOS",
                        String.valueOf(persistido.getId()),
                        "Username: " + persistido.getUsername() + ", Rol: " + persistido.getRol().name() + ", Nombre: " + persistido.getNombreCompleto(),
                        ResultadoAuditoria.EXITO
                );
            }

            return persistido;
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al registrar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * Actualiza los datos de un usuario existente garantizando la preservación del último administrador activo (RN-025, VP-021).
     *
     * @param id ID del usuario.
     * @param nuevoNombre Nuevo nombre completo (opcional si es null).
     * @param nuevoRol Nuevo rol (opcional si es null).
     * @param activo Nuevo estado activo (opcional si es null).
     * @return Usuario actualizado.
     */
    public Usuario actualizarUsuario(int id, String nuevoNombre, Rol nuevoRol, Boolean activo) {
        autorizador.requirePermission(Permiso.USUARIOS_GESTIONAR);

        Usuario actual = obtenerPorId(id)
                .orElseThrow(() -> new DatosInvalidosException("Usuario no encontrado con ID: " + id));

        // Validación de Invariante RN-025: no degradar ni desactivar al último administrador
        boolean esAdminActivo = actual.isActivo() && actual.getRol() == Rol.ADMINISTRADOR;
        boolean intentaDesactivar = activo != null && !activo && esAdminActivo;
        boolean intentaCambiarRol = nuevoRol != null && nuevoRol != Rol.ADMINISTRADOR && esAdminActivo;

        if (intentaDesactivar || intentaCambiarRol) {
            try {
                int totalAdmins = usuarioRepository.contarAdministradoresActivos();
                if (totalAdmins <= 1) {
                    throw new DatosInvalidosException("No se puede desactivar ni modificar el rol del único Administrador activo del sistema.");
                }
            } catch (SQLException e) {
                throw new DatosInvalidosException("Error al validar invariante de administrador: " + e.getMessage(), e);
            }
        }

        if (nuevoNombre != null) {
            if (nuevoNombre.isBlank()) {
                throw new DatosInvalidosException("El nombre completo no puede estar vacío.");
            }
            actual.setNombreCompleto(nuevoNombre.trim());
        }

        if (nuevoRol != null) {
            actual.setRol(nuevoRol);
        }

        boolean estadoAnteriorActivo = actual.isActivo();
        if (activo != null) {
            actual.setActivo(activo);
        }

        try {
            usuarioRepository.actualizar(actual);

            if (auditoriaService != null) {
                AccionAuditoria accion = (activo != null && !activo && estadoAnteriorActivo)
                        ? AccionAuditoria.DESACTIVAR_USUARIO
                        : (activo != null && activo && !estadoAnteriorActivo)
                        ? AccionAuditoria.ACTIVAR_USUARIO
                        : AccionAuditoria.ACTUALIZAR_USUARIO;

                auditoriaService.registrar(
                        accion,
                        "USUARIOS",
                        String.valueOf(actual.getId()),
                        "Modificado usuario: " + actual.getUsername() + ", Rol: " + actual.getRol().name() + ", Activo: " + actual.isActivo(),
                        ResultadoAuditoria.EXITO
                );
            }

            return actual;
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al actualizar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * Habilita o deshabilita un usuario en el sistema.
     *
     * @param id ID del usuario.
     * @param activo Nuevo estado de activación.
     * @return Usuario actualizado.
     */
    public Usuario cambiarEstadoActivo(int id, boolean activo) {
        return actualizarUsuario(id, null, null, activo);
    }

    /**
     * Modifica la contraseña de un usuario validando la clave actual y las políticas de complejidad (CU-020, RN-022).
     *
     * @param id ID del usuario.
     * @param rawCurrentPassword Contraseña actual.
     * @param rawNewPassword Nueva contraseña.
     */
    public void cambiarPassword(int id, String rawCurrentPassword, String rawNewPassword) {
        Usuario usuario = obtenerPorId(id)
                .orElseThrow(() -> new DatosInvalidosException("Usuario no encontrado con ID: " + id));

        // Puede ser cambiado por el propio usuario o por un Administrador
        boolean esPropio = ContextoSeguridad.getSesionActual().usuarioId() == id;
        if (!esPropio && !autorizador.hasPermission(Permiso.USUARIOS_GESTIONAR)) {
            throw new DatosInvalidosException("No tiene permisos para modificar la contraseña de otro usuario.");
        }

        // Si es el propio usuario o si se suministró una contraseña actual, verificar su validez
        if (esPropio || (rawCurrentPassword != null && !rawCurrentPassword.isEmpty())) {
            if (rawCurrentPassword == null || !passwordHasher.verify(rawCurrentPassword, usuario.getPasswordHash())) {
                throw new AutenticacionException("La contraseña actual es incorrecta.");
            }
        }

        Validators.ResultadoValidacion valNew = Validators.validarPassword(rawNewPassword);
        if (!valNew.valido()) {
            throw new DatosInvalidosException(valNew.mensaje());
        }

        if (passwordHasher.verify(rawNewPassword, usuario.getPasswordHash())) {
            throw new DatosInvalidosException("La nueva contraseña debe ser diferente a la anterior.");
        }

        try {
            String nuevoHash = passwordHasher.hash(rawNewPassword);
            usuarioRepository.actualizarPassword(id, nuevoHash);

            if (auditoriaService != null) {
                auditoriaService.registrar(
                        AccionAuditoria.CAMBIO_CONTRASENA,
                        "USUARIOS",
                        String.valueOf(id),
                        "Contraseña actualizada para usuario: " + usuario.getUsername(),
                        ResultadoAuditoria.EXITO
                );
            }
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al actualizar la contraseña: " + e.getMessage(), e);
        }
    }

    /**
     * Lista todos los usuarios registrados en el sistema (CU-019).
     *
     * @return Lista de usuarios.
     */
    public List<Usuario> listarUsuarios() {
        autorizador.requirePermission(Permiso.USUARIOS_GESTIONAR);
        try {
            return usuarioRepository.listarTodos();
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al listar usuarios: " + e.getMessage(), e);
        }
    }

    /**
     * Obtiene un usuario por su identificador primario.
     *
     * @param id ID del usuario.
     * @return Optional con el usuario si existe.
     */
    public Optional<Usuario> obtenerPorId(int id) {
        try {
            return usuarioRepository.obtenerPorId(id);
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al consultar usuario: " + e.getMessage(), e);
        }
    }

    /**
     * Cuenta cuántos administradores activos existen en la base de datos (RN-025, VP-021).
     *
     * @return Total de administradores activos.
     */
    public int contarAdministradoresActivos() {
        try {
            return usuarioRepository.contarAdministradoresActivos();
        } catch (SQLException e) {
            throw new DatosInvalidosException("Error al consultar administradores activos: " + e.getMessage(), e);
        }
    }

    /**
     * Determina si existe al menos un usuario administrador activo registrado en el sistema.
     *
     * @return true si existe al menos un administrador activo.
     */
    public boolean existeAlgunAdministrador() {
        return contarAdministradoresActivos() > 0;
    }
}
