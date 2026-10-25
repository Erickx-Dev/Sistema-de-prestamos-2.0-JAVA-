package com.credinoda.domain.exception;

import com.credinoda.domain.model.EstadoLicencia;

/**
 * Excepción lanzada cuando se detecta una alteración regresiva en el reloj del sistema operativo (RN-032).
 */
public class RelojManipuladoException extends LicenciaException {

    public RelojManipuladoException(String message) {
        super(message, EstadoLicencia.RELOJ_MANIPULADO);
    }

    public RelojManipuladoException(String message, Throwable cause) {
        super(message, EstadoLicencia.RELOJ_MANIPULADO, cause);
    }
}
