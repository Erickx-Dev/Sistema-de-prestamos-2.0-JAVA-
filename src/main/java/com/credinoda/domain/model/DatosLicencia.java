package com.credinoda.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Modelo inmutable que representa los datos de una licencia comercial decodificada y firmada (RN-030).
 */
public final class DatosLicencia {

    private final String licenciaId;
    private final String titular;
    private final String hardwareId;
    private final LocalDate fechaEmision;
    private final LocalDate fechaExpiracion;
    private final String tipoLicencia;
    private final String firmaBase64;

    public DatosLicencia(String licenciaId,
                         String titular,
                         String hardwareId,
                         LocalDate fechaEmision,
                         LocalDate fechaExpiracion,
                         String tipoLicencia,
                         String firmaBase64) {
        this.licenciaId = Objects.requireNonNull(licenciaId, "licenciaId no puede ser nulo").trim();
        this.titular = Objects.requireNonNull(titular, "titular no puede ser nulo").trim();
        this.hardwareId = Objects.requireNonNull(hardwareId, "hardwareId no puede ser nulo").trim().toUpperCase();
        this.fechaEmision = Objects.requireNonNull(fechaEmision, "fechaEmision no puede ser nula");
        this.fechaExpiracion = Objects.requireNonNull(fechaExpiracion, "fechaExpiracion no puede ser nula");
        this.tipoLicencia = (tipoLicencia != null && !tipoLicencia.isBlank()) ? tipoLicencia.trim().toUpperCase() : "COMERCIAL";
        this.firmaBase64 = firmaBase64 != null ? firmaBase64.trim() : "";
    }

    public String getLicenciaId() {
        return licenciaId;
    }

    public String getTitular() {
        return titular;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getFechaExpiracion() {
        return fechaExpiracion;
    }

    public String getTipoLicencia() {
        return tipoLicencia;
    }

    public String getFirmaBase64() {
        return firmaBase64;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DatosLicencia that = (DatosLicencia) o;
        return Objects.equals(licenciaId, that.licenciaId)
                && Objects.equals(titular, that.titular)
                && Objects.equals(hardwareId, that.hardwareId)
                && Objects.equals(fechaEmision, that.fechaEmision)
                && Objects.equals(fechaExpiracion, that.fechaExpiracion)
                && Objects.equals(tipoLicencia, that.tipoLicencia);
    }

    @Override
    public int hashCode() {
        return Objects.hash(licenciaId, titular, hardwareId, fechaEmision, fechaExpiracion, tipoLicencia);
    }

    @Override
    public String toString() {
        return "DatosLicencia{" +
                "licenciaId='" + licenciaId + '\'' +
                ", titular='" + titular + '\'' +
                ", hardwareId='" + hardwareId + '\'' +
                ", fechaEmision=" + fechaEmision +
                ", fechaExpiracion=" + fechaExpiracion +
                ", tipoLicencia='" + tipoLicencia + '\'' +
                '}';
    }
}
