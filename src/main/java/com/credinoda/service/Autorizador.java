package com.credinoda.service;

import com.credinoda.domain.exception.AccesoDenegadoException;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.Rol;

/**
 * Contrato para la verificación y exigencia de permisos de acceso en CrediNoda 2.0 (RN-024, RN-026).
 */
public interface Autorizador {

    /**
     * Evalúa si la sesión activa cuenta con el permiso solicitado.
     *
     * @param permiso Permiso a consultar.
     * @return true si la sesión actual posee el permiso, false en caso contrario.
     */
    boolean hasPermission(Permiso permiso);

    /**
     * Exige que la sesión activa posea el permiso solicitado.
     *
     * @param permiso Permiso requerido para continuar.
     * @throws AccesoDenegadoException Si la sesión actual carece del permiso.
     */
    void requirePermission(Permiso permiso) throws AccesoDenegadoException;

    /**
     * Evalúa si un rol específico posee el permiso indicado.
     *
     * @param rol Rol a evaluar.
     * @param permiso Permiso consultado.
     * @return true si el rol cuenta con el permiso.
     */
    boolean hasPermission(Rol rol, Permiso permiso);
}
