package com.credinoda.common;

/**
 * Interfaz funcional desacoplada para la obtención del identificador físico del equipo (RN-031).
 * Facilita la inyección de dependencias y pruebas unitarias aisladas con Hardware IDs simulados.
 */
@FunctionalInterface
public interface HardwareIdProvider {

    /**
     * Obtiene el identificador determinista del equipo en formato 'CN20-XXXX-XXXX-XXXX-XXXX'.
     *
     * @return Hardware ID formateado y normalizado.
     */
    String obtenerHardwareId();
}
