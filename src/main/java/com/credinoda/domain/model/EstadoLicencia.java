package com.credinoda.domain.model;

/**
 * Enumeración canónica que representa los posibles estados del ciclo de vida y validación
 * de la licencia comercial y control de tiempo de CrediNoda 2.0 (RN-030, RN-031, RN-032, RN-033).
 */
public enum EstadoLicencia {
    /**
     * Licencia válida, verificada criptográficamente con Ed25519, vinculada al Hardware ID actual,
     * dentro de vigencia temporal y con reloj consistente.
     */
    VALIDA("Licencia Válida", "La licencia se encuentra activa y vigente."),

    /**
     * No existe una licencia registrada en el sistema.
     */
    NO_REGISTRADA("No Registrada", "El sistema no cuenta con una licencia de activación."),

    /**
     * El formato o estructura del token de licencia no es válido o está incompleto.
     */
    FORMATO_INVALIDO("Formato Inválido", "El formato del código o archivo de licencia es incorrecto."),

    /**
     * La firma digital asimétrica Ed25519 no corresponde al contenido o fue adulterada.
     */
    FIRMA_INVALIDA("Firma Inválida", "La firma digital no es válida o la licencia fue alterada."),

    /**
     * La licencia pertenece a otro equipo físico y no coincide con el Hardware ID actual.
     */
    HARDWARE_NO_COINCIDE("Equipo No Coincide", "La licencia no corresponde al Hardware ID de este equipo."),

    /**
     * La fecha de expiración de la licencia ha sido superada.
     */
    EXPIRADA("Licencia Expirada", "La vigencia de la licencia ha caducado."),

    /**
     * Se detectó retroceso del reloj del sistema operativo respecto al último timestamp registrado.
     */
    RELOJ_MANIPULADO("Reloj Manipulado", "Se detectó una alteración en la fecha u hora del sistema.");

    private final String etiqueta;
    private final String descripcion;

    EstadoLicencia(String etiqueta, String descripcion) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
