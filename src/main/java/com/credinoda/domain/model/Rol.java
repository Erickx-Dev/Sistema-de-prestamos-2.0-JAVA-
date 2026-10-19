package com.credinoda.domain.model;

/**
 * Roles canónicos del sistema de seguridad y control de acceso de CrediNoda 2.0 (RN-024).
 */
public enum Rol {
    ADMINISTRADOR("Administrador", "Acceso total a la administración, configuración, usuarios y respaldos"),
    OPERADOR("Operador", "Enfoque operativo en cobranza y créditos a prestatarios preexistentes");

    private final String nombreVisible;
    private final String descripcion;

    Rol(String nombreVisible, String descripcion) {
        this.nombreVisible = nombreVisible;
        this.descripcion = descripcion;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
