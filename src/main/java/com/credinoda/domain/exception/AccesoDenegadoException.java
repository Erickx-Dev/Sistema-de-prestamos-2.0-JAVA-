package com.credinoda.domain.exception;

import com.credinoda.domain.model.Permiso;

/**
 * Excepción lanzada cuando el usuario autenticado intenta ejecutar una operación
 * o acceder a un módulo para el cual no posee permisos suficientes según su rol (RN-024, RN-026).
 */
public class AccesoDenegadoException extends CrediNodaException {

    private final Permiso permisoRequerido;

    public AccesoDenegadoException(String message) {
        super(message);
        this.permisoRequerido = null;
    }

    public AccesoDenegadoException(Permiso permisoRequerido) {
        super("Acceso denegado. Se requiere el permiso: " + (permisoRequerido != null ? permisoRequerido.name() : "DESCONOCIDO"));
        this.permisoRequerido = permisoRequerido;
    }

    public AccesoDenegadoException(Permiso permisoRequerido, String message) {
        super(message);
        this.permisoRequerido = permisoRequerido;
    }

    public Permiso getPermisoRequerido() {
        return permisoRequerido;
    }
}
