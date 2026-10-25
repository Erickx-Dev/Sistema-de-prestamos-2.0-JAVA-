package com.credinoda.domain.exception;

import com.credinoda.domain.model.EstadoLicencia;

/**
 * Excepción lanzada cuando ocurre un error en la verificación, activación o vigencia de una licencia (RN-030).
 */
public class LicenciaException extends CrediNodaException {

    private final EstadoLicencia estado;

    public LicenciaException(String message) {
        this(message, EstadoLicencia.FORMATO_INVALIDO);
    }

    public LicenciaException(String message, EstadoLicencia estado) {
        super(message);
        this.estado = estado;
    }

    public LicenciaException(String message, EstadoLicencia estado, Throwable cause) {
        super(message, cause);
        this.estado = estado;
    }

    public EstadoLicencia getEstado() {
        return estado;
    }
}
