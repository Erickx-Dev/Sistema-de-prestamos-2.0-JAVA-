package com.credinoda.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entidad de dominio que representa a un usuario del sistema (CU-018, CU-019, RN-022 a RN-025).
 */
public class Usuario {

    private int id;
    private String username;
    private String passwordHash;
    private String nombreCompleto;
    private Rol rol;
    private boolean activo;
    private int intentosFallidos;
    private LocalDateTime bloqueadoHasta;
    private LocalDateTime ultimoAcceso;
    private LocalDateTime fechaCreacion;

    public Usuario() {
        this.activo = true;
        this.intentosFallidos = 0;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Usuario(String username, String passwordHash, String nombreCompleto, Rol rol) {
        this.username = Objects.requireNonNull(username, "El username no puede ser nulo").trim().toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "El passwordHash no puede ser nulo");
        this.nombreCompleto = Objects.requireNonNull(nombreCompleto, "El nombre completo no puede ser nulo").trim();
        this.rol = Objects.requireNonNull(rol, "El rol no puede ser nulo");
        this.activo = true;
        this.intentosFallidos = 0;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Usuario(int id, String username, String passwordHash, String nombreCompleto, Rol rol,
                   boolean activo, int intentosFallidos, LocalDateTime bloqueadoHasta,
                   LocalDateTime ultimoAcceso, LocalDateTime fechaCreacion) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.nombreCompleto = nombreCompleto;
        this.rol = rol;
        this.activo = activo;
        this.intentosFallidos = intentosFallidos;
        this.bloqueadoHasta = bloqueadoHasta;
        this.ultimoAcceso = ultimoAcceso;
        this.fechaCreacion = fechaCreacion;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username != null ? username.trim().toLowerCase() : null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto != null ? nombreCompleto.trim() : null;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public LocalDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(LocalDateTime ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /**
     * Evalúa si la cuenta se encuentra bloqueada en el momento actual especificado.
     *
     * @param ahora Momento de referencia temporal.
     * @return true si bloqueadoHasta no es nulo y es posterior al momento actual.
     */
    public boolean estaBloqueado(LocalDateTime ahora) {
        if (bloqueadoHasta == null) {
            return false;
        }
        return bloqueadoHasta.isAfter(ahora);
    }

    /**
     * Evalúa si la cuenta se encuentra bloqueada en este instante.
     *
     * @return true si la cuenta está actualmente bloqueada.
     */
    public boolean estaBloqueado() {
        return estaBloqueado(LocalDateTime.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return id != 0 && id == usuario.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Usuario{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", nombreCompleto='" + nombreCompleto + '\'' +
                ", rol=" + rol +
                ", activo=" + activo +
                ", intentosFallidos=" + intentosFallidos +
                ", bloqueadoHasta=" + bloqueadoHasta +
                '}';
    }
}
