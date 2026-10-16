package com.credinoda.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad que representa una cuota individual dentro del plan de pagos de un crédito.
 */
public class Cuota {
    private Integer id;
    private int creditoId;
    private int numeroCuota;
    private LocalDate fechaVencimiento;
    private BigDecimal monto;
    private EstadoCuota estado;
    private boolean enMora;

    public Cuota() {
    }

    public Cuota(int creditoId, int numeroCuota, LocalDate fechaVencimiento, BigDecimal monto, EstadoCuota estado) {
        this(null, creditoId, numeroCuota, fechaVencimiento, monto, estado, false);
    }

    public Cuota(Integer id, int creditoId, int numeroCuota, LocalDate fechaVencimiento, BigDecimal monto, EstadoCuota estado, boolean enMora) {
        this.id = id;
        this.creditoId = creditoId;
        this.numeroCuota = numeroCuota;
        this.fechaVencimiento = fechaVencimiento;
        this.monto = monto;
        this.estado = estado != null ? estado : EstadoCuota.PENDIENTE;
        this.enMora = enMora;
    }

    /**
     * Calcula la etiqueta textual del estado de la cuota según la regla RN-008.
     * Si la cuota está en mora y su estado es PENDIENTE o PARCIAL, retorna '<ESTADO> (EN MORA)'.
     * De lo contrario, retorna el nombre del estado ('PENDIENTE', 'PARCIAL', 'PAGADA', 'VENCIDA').
     */
    public String etiquetaEstado() {
        if (enMora && (estado == EstadoCuota.PENDIENTE || estado == EstadoCuota.PARCIAL)) {
            return estado.name() + " (EN MORA)";
        }
        return estado != null ? estado.name() : "";
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getCreditoId() {
        return creditoId;
    }

    public void setCreditoId(int creditoId) {
        this.creditoId = creditoId;
    }

    public int getNumeroCuota() {
        return numeroCuota;
    }

    public void setNumeroCuota(int numeroCuota) {
        this.numeroCuota = numeroCuota;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public EstadoCuota getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuota estado) {
        this.estado = estado;
    }

    public boolean isEnMora() {
        return enMora;
    }

    public void setEnMora(boolean enMora) {
        this.enMora = enMora;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cuota cuota = (Cuota) o;
        return Objects.equals(id, cuota.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Cuota{" +
                "id=" + id +
                ", creditoId=" + creditoId +
                ", numeroCuota=" + numeroCuota +
                ", fechaVencimiento=" + fechaVencimiento +
                ", monto=" + monto +
                ", estado=" + estado +
                ", enMora=" + enMora +
                '}';
    }
}
