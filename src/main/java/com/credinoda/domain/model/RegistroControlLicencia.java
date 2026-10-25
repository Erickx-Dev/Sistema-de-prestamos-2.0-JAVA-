package com.credinoda.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad de persistencia que representa el registro único de control de licencia y marca de tiempo
 * en la tabla 'control_licencia' de SQLite (RN-030, RN-032).
 */
public final class RegistroControlLicencia {

    private final Integer id;
    private final String licenciaToken;
    private final String hardwareId;
    private final String titular;
    private final LocalDate fechaEmision;
    private final LocalDate fechaExpiracion;
    private final long ultimoTimestampUtc;
    private final String hmacIntegridad;

    public RegistroControlLicencia(Integer id,
                                  String licenciaToken,
                                  String hardwareId,
                                  String titular,
                                  LocalDate fechaEmision,
                                  LocalDate fechaExpiracion,
                                  long ultimoTimestampUtc,
                                  String hmacIntegridad) {
        this.id = id != null ? id : 1;
        this.licenciaToken = licenciaToken != null ? licenciaToken.trim() : "";
        this.hardwareId = Objects.requireNonNull(hardwareId, "hardwareId no puede ser nulo").trim().toUpperCase();
        this.titular = titular != null ? titular.trim() : "";
        this.fechaEmision = fechaEmision;
        this.fechaExpiracion = fechaExpiracion;
        this.ultimoTimestampUtc = ultimoTimestampUtc;
        this.hmacIntegridad = Objects.requireNonNull(hmacIntegridad, "hmacIntegridad no puede ser nulo").trim();
    }

    public Integer getId() {
        return id;
    }

    public String getLicenciaToken() {
        return licenciaToken;
    }

    public String getHardwareId() {
        return hardwareId;
    }

    public String getTitular() {
        return titular;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public LocalDate getFechaExpiracion() {
        return fechaExpiracion;
    }

    public long getUltimoTimestampUtc() {
        return ultimoTimestampUtc;
    }

    public String getHmacIntegridad() {
        return hmacIntegridad;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegistroControlLicencia that = (RegistroControlLicencia) o;
        return ultimoTimestampUtc == that.ultimoTimestampUtc
                && Objects.equals(id, that.id)
                && Objects.equals(licenciaToken, that.licenciaToken)
                && Objects.equals(hardwareId, that.hardwareId)
                && Objects.equals(hmacIntegridad, that.hmacIntegridad);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, licenciaToken, hardwareId, ultimoTimestampUtc, hmacIntegridad);
    }

    @Override
    public String toString() {
        return "RegistroControlLicencia{" +
                "id=" + id +
                ", hardwareId='" + hardwareId + '\'' +
                ", titular='" + titular + '\'' +
                ", fechaExpiracion=" + fechaExpiracion +
                ", ultimoTimestampUtc=" + ultimoTimestampUtc +
                '}';
    }
}
