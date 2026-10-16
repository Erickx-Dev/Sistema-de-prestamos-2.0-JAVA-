package com.credinoda.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad que representa un crédito otorgado a un cliente.
 */
public class Credito {
    private Integer id;
    private int clienteId;
    private BigDecimal capital;
    private BigDecimal tasaMensual;
    private int plazoMeses;
    private BigDecimal totalIntereses;
    private BigDecimal totalPagar;
    private FrecuenciaPago frecuenciaPago;
    private LocalDate fechaInicio;
    private EstadoCredito estado;

    public Credito() {
    }

    public Credito(int clienteId, BigDecimal capital, BigDecimal tasaMensual, int plazoMeses,
                   BigDecimal totalIntereses, BigDecimal totalPagar, FrecuenciaPago frecuenciaPago,
                   LocalDate fechaInicio, EstadoCredito estado) {
        this(null, clienteId, capital, tasaMensual, plazoMeses, totalIntereses, totalPagar, frecuenciaPago, fechaInicio, estado);
    }

    public Credito(Integer id, int clienteId, BigDecimal capital, BigDecimal tasaMensual, int plazoMeses,
                   BigDecimal totalIntereses, BigDecimal totalPagar, FrecuenciaPago frecuenciaPago,
                   LocalDate fechaInicio, EstadoCredito estado) {
        this.id = id;
        this.clienteId = clienteId;
        this.capital = capital;
        this.tasaMensual = tasaMensual;
        this.plazoMeses = plazoMeses;
        this.totalIntereses = totalIntereses;
        this.totalPagar = totalPagar;
        this.frecuenciaPago = frecuenciaPago;
        this.fechaInicio = fechaInicio != null ? fechaInicio : LocalDate.now();
        this.estado = estado != null ? estado : EstadoCredito.ACTIVO;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public int getClienteId() {
        return clienteId;
    }

    public void setClienteId(int clienteId) {
        this.clienteId = clienteId;
    }

    public BigDecimal getCapital() {
        return capital;
    }

    public void setCapital(BigDecimal capital) {
        this.capital = capital;
    }

    public BigDecimal getTasaMensual() {
        return tasaMensual;
    }

    public void setTasaMensual(BigDecimal tasaMensual) {
        this.tasaMensual = tasaMensual;
    }

    public int getPlazoMeses() {
        return plazoMeses;
    }

    public void setPlazoMeses(int plazoMeses) {
        this.plazoMeses = plazoMeses;
    }

    public BigDecimal getTotalIntereses() {
        return totalIntereses;
    }

    public void setTotalIntereses(BigDecimal totalIntereses) {
        this.totalIntereses = totalIntereses;
    }

    public BigDecimal getTotalPagar() {
        return totalPagar;
    }

    public void setTotalPagar(BigDecimal totalPagar) {
        this.totalPagar = totalPagar;
    }

    public FrecuenciaPago getFrecuenciaPago() {
        return frecuenciaPago;
    }

    public void setFrecuenciaPago(FrecuenciaPago frecuenciaPago) {
        this.frecuenciaPago = frecuenciaPago;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public EstadoCredito getEstado() {
        return estado;
    }

    public void setEstado(EstadoCredito estado) {
        this.estado = estado;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Credito credito = (Credito) o;
        return Objects.equals(id, credito.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Credito{" +
                "id=" + id +
                ", clienteId=" + clienteId +
                ", capital=" + capital +
                ", tasaMensual=" + tasaMensual +
                ", plazoMeses=" + plazoMeses +
                ", totalPagar=" + totalPagar +
                ", frecuenciaPago=" + frecuenciaPago +
                ", estado=" + estado +
                '}';
    }
}
