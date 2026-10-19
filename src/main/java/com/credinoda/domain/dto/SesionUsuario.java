package com.credinoda.domain.dto;

import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Registro inmutable que representa una sesión de usuario autenticada activamente en el sistema (CU-018, RN-024).
 *
 * @param usuarioId Identificador único del usuario.
 * @param username Nombre de usuario único normalizado.
 * @param nombreCompleto Nombre real del operador.
 * @param rol Rol asignado (ADMINISTRADOR u OPERADOR).
 * @param loginTimestamp Momento de autenticación exitosa.
 */
public record SesionUsuario(
        int usuarioId,
        String username,
        String nombreCompleto,
        Rol rol,
        LocalDateTime loginTimestamp
) {

    public SesionUsuario {
        Objects.requireNonNull(username, "El username no puede ser nulo");
        Objects.requireNonNull(nombreCompleto, "El nombre completo no puede ser nulo");
        Objects.requireNonNull(rol, "El rol no puede ser nulo");
        Objects.requireNonNull(loginTimestamp, "El timestamp de login no puede ser nulo");
    }

    public SesionUsuario(int usuarioId, String username, String nombreCompleto, Rol rol) {
        this(usuarioId, username, nombreCompleto, rol, LocalDateTime.now());
    }

    /**
     * Construye una sesión a partir de un objeto Usuario persistido.
     *
     * @param usuario Usuario autenticado.
     * @return Instancia inmutable de SesionUsuario.
     */
    public static SesionUsuario de(Usuario usuario) {
        Objects.requireNonNull(usuario, "El usuario no puede ser nulo");
        return new SesionUsuario(
                usuario.getId(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                usuario.getRol(),
                LocalDateTime.now()
        );
    }

    /**
     * Provee una sesión interna de 'sistema' con rol de Administrador.
     * Empleada para asegurar compatibilidad y transparencia en ejecuciones en segundo plano,
     * inicialización y suites de pruebas automatizadas previas.
     *
     * @return Sesión de sistema con privilegios administrativos.
     */
    public static SesionUsuario sistema() {
        return new SesionUsuario(
                0,
                "sistema",
                "Sistema CrediNoda",
                Rol.ADMINISTRADOR,
                LocalDateTime.now()
        );
    }
}
