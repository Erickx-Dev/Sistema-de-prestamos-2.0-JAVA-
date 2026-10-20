package com.credinoda.domain.model;

/**
 * Resultado canónico de una acción registrada en la bitácora de auditoría (RN-027).
 */
public enum ResultadoAuditoria {
    EXITO("Éxito"),
    FALLO("Fallo"),
    DENEGADO("Denegado");

    private final String etiqueta;

    ResultadoAuditoria(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }
}
