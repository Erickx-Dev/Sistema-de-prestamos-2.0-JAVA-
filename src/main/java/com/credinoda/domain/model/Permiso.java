package com.credinoda.domain.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Catálogo granular de permisos del sistema CrediNoda 2.0 (RN-024).
 * Gobierna las operaciones permitidas para los roles ADMINISTRADOR y OPERADOR.
 */
public enum Permiso {

    // Clientes
    CLIENTES_VER("Visualizar clientes y expedientes"),
    CLIENTES_CREAR("Registrar nuevos clientes prestatarios"),
    CLIENTES_EDITAR("Modificar datos de clientes prestatarios"),

    // Créditos
    CREDITOS_VER("Visualizar créditos y planes de cuotas"),
    CREDITOS_CREAR("Simular y otorgar nuevos créditos"),

    // Pagos
    PAGOS_VER("Consultar historial de pagos y recibos"),
    PAGOS_REGISTRAR("Registrar abonos totales o parciales"),

    // Reportes
    REPORTES_VER("Consultar métricas y reportes financieros"),

    // Copias de Seguridad
    BACKUP_GESTIONAR("Crear y restaurar copias de seguridad de la base de datos"),

    // Gestión de Usuarios
    USUARIOS_GESTIONAR("Crear, editar, activar/desactivar y listar usuarios"),

    // Auditoría
    AUDITORIA_VER("Consultar la bitácora inmutable de auditoría");

    private final String descripcion;

    private static final Set<Permiso> PERMISOS_ADMINISTRADOR = Collections.unmodifiableSet(EnumSet.allOf(Permiso.class));
    private static final Set<Permiso> PERMISOS_OPERADOR = Collections.unmodifiableSet(EnumSet.of(
            CLIENTES_VER,
            CLIENTES_CREAR,
            CREDITOS_VER,
            CREDITOS_CREAR,
            PAGOS_VER,
            PAGOS_REGISTRAR,
            REPORTES_VER
    ));

    Permiso(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /**
     * Retorna el conjunto inmutable de permisos asociados al rol especificado.
     *
     * @param rol Rol consultado.
     * @return Conjunto inmutable de permisos.
     */
    public static Set<Permiso> getPermisosPorRol(Rol rol) {
        if (rol == null) {
            return Collections.emptySet();
        }
        return switch (rol) {
            case ADMINISTRADOR -> PERMISOS_ADMINISTRADOR;
            case OPERADOR -> PERMISOS_OPERADOR;
        };
    }

    /**
     * Determina si un rol cuenta con este permiso.
     *
     * @param rol Rol a evaluar.
     * @return true si el rol cuenta con el permiso, false de lo contrario.
     */
    public boolean tienePermiso(Rol rol) {
        if (rol == null) {
            return false;
        }
        return getPermisosPorRol(rol).contains(this);
    }

    /**
     * Determina si un rol cuenta con un permiso específico.
     *
     * @param rol Rol a evaluar.
     * @param permiso Permiso a verificar.
     * @return true si el rol cuenta con el permiso, false de lo contrario.
     */
    public static boolean tienePermiso(Rol rol, Permiso permiso) {
        return permiso != null && permiso.tienePermiso(rol);
    }
}
