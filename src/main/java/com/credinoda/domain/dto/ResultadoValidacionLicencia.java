package com.credinoda.domain.dto;

import com.credinoda.domain.model.DatosLicencia;
import com.credinoda.domain.model.EstadoLicencia;

import java.util.Objects;

/**
 * Record DTO inmutable que encapsula el resultado integral de una verificación de licencia
 * y control de tiempo en el sistema (RN-030 a RN-033).
 *
 * @param estado Estado de validación resultante.
 * @param datos Datos de la licencia parseada (puede ser null si no está registrada o es inválida).
 * @param hardwareIdActual Identificador físico del equipo evaluado.
 * @param diasRestantes Días de vigencia restantes (0 o negativo si expiró).
 * @param mensajeDetalle Descripción legible para visualización al usuario o registro de auditoría.
 */
public record ResultadoValidacionLicencia(
        EstadoLicencia estado,
        DatosLicencia datos,
        String hardwareIdActual,
        long diasRestantes,
        String mensajeDetalle
) {

    public ResultadoValidacionLicencia {
        Objects.requireNonNull(estado, "estado no puede ser nulo");
        hardwareIdActual = (hardwareIdActual != null) ? hardwareIdActual.trim().toUpperCase() : "";
        mensajeDetalle = (mensajeDetalle != null) ? mensajeDetalle.trim() : "";
    }

    /**
     * Determina si la licencia está completamente vigente y activa para operar el sistema.
     *
     * @return true si el estado es EstadoLicencia.VALIDA.
     */
    public boolean esValida() {
        return estado == EstadoLicencia.VALIDA;
    }

    public static ResultadoValidacionLicencia noRegistrada(String hardwareIdActual) {
        return new ResultadoValidacionLicencia(
                EstadoLicencia.NO_REGISTRADA,
                null,
                hardwareIdActual,
                0,
                "No hay una licencia activa registrada en este equipo."
        );
    }

    public static ResultadoValidacionLicencia relojManipulado(String hardwareIdActual, String detalle) {
        return new ResultadoValidacionLicencia(
                EstadoLicencia.RELOJ_MANIPULADO,
                null,
                hardwareIdActual,
                0,
                detalle
        );
    }

    public static ResultadoValidacionLicencia error(EstadoLicencia estado, String hardwareIdActual, String detalle) {
        return new ResultadoValidacionLicencia(
                estado,
                null,
                hardwareIdActual,
                0,
                detalle
        );
    }
}
