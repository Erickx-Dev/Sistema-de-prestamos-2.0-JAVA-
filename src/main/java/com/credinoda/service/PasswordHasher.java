package com.credinoda.service;

/**
 * Interfaz para el cómputo y verificación criptográfica de contraseñas de usuarios.
 */
public interface PasswordHasher {

    /**
     * Genera un hash seguro para la contraseña en texto claro provista.
     *
     * @param rawPassword Contraseña en texto plano.
     * @return Cadena representativa del hash seguro.
     */
    String hash(String rawPassword);

    /**
     * Verifica si una contraseña en texto plano coincide con el hash almacenado.
     *
     * @param rawPassword Contraseña en texto plano a verificar.
     * @param hashedPassword Hash de referencia previamente generado.
     * @return true si la contraseña coincide con el hash, false en caso contrario.
     */
    boolean verify(String rawPassword, String hashedPassword);
}
