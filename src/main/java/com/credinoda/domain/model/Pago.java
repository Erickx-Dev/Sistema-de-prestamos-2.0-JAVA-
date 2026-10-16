package com.credinoda.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad que representa un abono o pago aplicado a una cuota de un crédito.
 */
public class Pago {
    private Integer id;
    private int creditoId;
    private int cuotaId;
    private BigDecimal monto;
    private LocalDate fechaPago;

    public Pago() {
    }

    public Pago(int creditoId, int cuotaId, BigDecimal monto) {
        this(null, creditoId, cuotaId, monto, LocalDate.now());
    }

    public Pago(int creditoId, int cuotaId, BigDecimal monto, LocalDate fechaPago) {
        this(null, creditoId, cuotaId, monto, fechaPago);
    }

    public Pago(Integer id, int creditoId, int cuotaId, BigDecimal monto, LocalDate fechaPago) {
        this.id = id;
        this.creditoId = creditoId;
        this.cuotaId = cuotaId;
        this.monto = monto;
        this.fechaPago = fechaPago != null ? fechaPago : LocalDate.now();
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

    public int getCuotaId() {
        return cuotaId;
    }

    public void setCuotaId(int cuotaId) {
        this.cuotaId = cuotaId;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pago pago = (Pago) o;
        return Objects.equals(id, pago.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Pago{" +
                "id=" + id +
                ", creditoId=" + creditoId +
                ", cuotaId=" + cuotaId +
                ", monto=" + monto +
                ", fechaPago=" + fechaPago +
                '}';
    }
}
