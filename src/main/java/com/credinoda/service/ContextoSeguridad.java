package com.credinoda.service;

import com.credinoda.domain.dto.SesionUsuario;

/**
 * Gestor estático y de hilo para la sesión activa de usuario en la aplicación (CU-018, RN-024, RN-026).
 * Admite tanto sesión global (aplicación de escritorio monolítica) como específica de hilo,
 * proveyendo un fallback transparente a {@link SesionUsuario#sistema()} para preservar la compatibilidad
 * con ejecuciones en segundo plano y tests unitarios.
 */
public final class ContextoSeguridad {

    private static final ThreadLocal<SesionUsuario> SESION_HILO = new ThreadLocal<>();
    private static volatile SesionUsuario sesionGlobal;
    private static volatile boolean modoSoloLectura = false;

    private ContextoSeguridad() {
        // Clase de contexto estático, no instanciable
    }

    /**
     * Retorna la sesión de usuario activa.
     * Si no se ha establecido una sesión interactiva explícita, provee la sesión de sistema por defecto.
     *
     * @return SesionUsuario activa o de sistema.
     */
    public static SesionUsuario getSesionActual() {
        SesionUsuario hilo = SESION_HILO.get();
        if (hilo != null) {
            return hilo;
        }
        if (sesionGlobal != null) {
            return sesionGlobal;
        }
        return SesionUsuario.sistema();
    }

    /**
     * Establece la sesión activa tanto a nivel global como en el hilo actual.
     *
     * @param sesion SesionUsuario autenticada.
     */
    public static void setSesionActual(SesionUsuario sesion) {
        sesionGlobal = sesion;
        if (sesion != null) {
            SESION_HILO.set(sesion);
        } else {
            SESION_HILO.remove();
        }
    }

    /**
     * Establece una sesión temporal exclusiva para el hilo en ejecución (útil en pruebas unitarias concurrentes).
     *
     * @param sesion SesionUsuario a asignar al hilo.
     */
    public static void setSesionHilo(SesionUsuario sesion) {
        if (sesion != null) {
            SESION_HILO.set(sesion);
        } else {
            SESION_HILO.remove();
        }
    }

    /**
     * Limpia la sesión activa en el hilo actual y a nivel global (usado al cerrar sesión).
     */
    public static void limpiar() {
        sesionGlobal = null;
        SESION_HILO.remove();
    }

    /**
     * Alias de conveniencia para limpiar().
     */
    public static void limpiarSesion() {
        limpiar();
    }

    /**
     * Indica si existe una sesión interactiva explícita configurada (distinta de la sesión por defecto de sistema).
     *
     * @return true si hay un usuario real autenticado.
     */
    public static boolean haySesionInteractiva() {
        SesionUsuario hilo = SESION_HILO.get();
        if (hilo != null && hilo.usuarioId() != 0) {
            return true;
        }
        return sesionGlobal != null && sesionGlobal.usuarioId() != 0;
    }

    /**
     * Indica si el sistema opera en Modo Solo Lectura debido a que la licencia comercial está vencida (RN-030).
     *
     * @return true si el modo solo lectura está activo.
     */
    public static boolean isModoSoloLectura() {
        return modoSoloLectura;
    }

    /**
     * Establece o desactiva el Modo Solo Lectura por expiración de licencia.
     *
     * @param soloLectura true para activar solo lectura, false para operación normal.
     */
    public static void setModoSoloLectura(boolean soloLectura) {
        modoSoloLectura = soloLectura;
    }
}
