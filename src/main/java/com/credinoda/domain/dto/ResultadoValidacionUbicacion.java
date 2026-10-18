package com.credinoda.domain.dto;

import java.util.Objects;

/**
 * Representa el resultado detallado de la validación de una carpeta operativa (datos o respaldos).
 * (CU-016, RN-020).
 *
 * @param valida                Indica si la carpeta cumple con permisos de escritura y requisitos mínimos.
 * @param esNube                Indica si se detectó que la carpeta pertenece a un servicio sincronizado en la nube (OneDrive, Google Drive, etc.).
 * @param baseExistenteValida   Indica si en la carpeta se detectó un archivo 'creditos.db' válido e íntegro de CrediNoda.
 * @param mensaje               Mensaje explicativo o advertencia para el usuario.
 */
public record ResultadoValidacionUbicacion(
        boolean valida,
        boolean esNube,
        boolean baseExistenteValida,
        String mensaje
) {
    public ResultadoValidacionUbicacion {
        Objects.requireNonNull(mensaje, "El mensaje de validación no puede ser nulo");
    }

    public static ResultadoValidacionUbicacion valida(boolean esNube, boolean baseExistenteValida, String mensaje) {
        return new ResultadoValidacionUbicacion(true, esNube, baseExistenteValida, mensaje);
    }

    public static ResultadoValidacionUbicacion invalida(String mensaje) {
        return new ResultadoValidacionUbicacion(false, false, false, mensaje);
    }
}
