package com.credinoda.domain.exception;

/**
 * Excepción lanzada cuando ocurre un error en la carga, persistencia o validación
 * de la configuración operativa del sistema CrediNoda 2.0 (RN-019, RN-020, RN-021).
 */
public class ConfiguracionException extends CrediNodaException {

    public ConfiguracionException(String mensaje) {
        super(mensaje);
    }

    public ConfiguracionException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
