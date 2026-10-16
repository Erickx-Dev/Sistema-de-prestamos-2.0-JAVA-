package com.credinoda.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad que representa un cliente o prestatario en el sistema.
 */
public class Cliente {
    private Integer id;
    private String nombre;
    private String telefono;
    private String documento;
    private LocalDate fechaRegistro;

    public Cliente() {
    }

    public Cliente(String nombre, String telefono, String documento) {
        this(null, nombre, telefono, documento, LocalDate.now());
    }

    public Cliente(Integer id, String nombre, String telefono, String documento, LocalDate fechaRegistro) {
        this.id = id;
        this.nombre = nombre;
        this.telefono = telefono;
        this.documento = documento;
        this.fechaRegistro = fechaRegistro != null ? fechaRegistro : LocalDate.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public LocalDate getFechaRegistro() {
        return fechaRegistro;
    }

    public void setFechaRegistro(LocalDate fechaRegistro) {
        this.fechaRegistro = fechaRegistro;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cliente cliente = (Cliente) o;
        return Objects.equals(id, cliente.id) && Objects.equals(documento, cliente.documento);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, documento);
    }

    @Override
    public String toString() {
        return "Cliente{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", telefono='" + telefono + '\'' +
                ", documento='" + documento + '\'' +
                ", fechaRegistro=" + fechaRegistro +
                '}';
    }
}
