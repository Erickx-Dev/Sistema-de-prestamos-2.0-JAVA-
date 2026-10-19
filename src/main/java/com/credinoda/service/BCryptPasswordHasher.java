package com.credinoda.service;

import at.favre.lib.crypto.bcrypt.BCrypt;

/**
 * Implementación de {@link PasswordHasher} utilizando BCrypt con factor de costo 12
 * y sal criptográfica aleatoria nativa (RN-022, VP-018).
 */
public class BCryptPasswordHasher implements PasswordHasher {

    public static final int DEFAULT_COST = 12;

    private final int cost;

    public BCryptPasswordHasher() {
        this(DEFAULT_COST);
    }

    public BCryptPasswordHasher(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("El factor de costo BCrypt debe estar entre 4 y 31");
        }
        this.cost = cost;
    }

    @Override
    public String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("La contraseña no puede ser nula ni vacía");
        }
        return BCrypt.withDefaults().hashToString(cost, rawPassword.toCharArray());
    }

    @Override
    public boolean verify(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        try {
            BCrypt.Result result = BCrypt.verifyer().verify(rawPassword.toCharArray(), hashedPassword.toCharArray());
            return result.verified;
        } catch (Exception e) {
            return false;
        }
    }
}
