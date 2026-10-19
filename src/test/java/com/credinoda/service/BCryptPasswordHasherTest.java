package com.credinoda.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de BCryptPasswordHasher (RN-022, VP-018)")
class BCryptPasswordHasherTest {

    private BCryptPasswordHasher hasher;

    @BeforeEach
    void setUp() {
        this.hasher = new BCryptPasswordHasher();
    }

    @Test
    @DisplayName("VP-018: El hash generado cumple con el formato BCrypt estándar con costo 12 ($2a$12$ o $2b$12$)")
    void testFormatoHashBCryptCosto12() {
        String password = "MiPasswordSeguro123";
        String hash = hasher.hash(password);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(60);
        assertThat(hash).matches("^\\$2[ab]\\$12\\$[./A-Za-z0-9]{53}$");
    }

    @Test
    @DisplayName("VP-018: Dos hashes de la misma contraseña son diferentes debido a la sal aleatoria")
    void testSalAleatoriaProduceHashesDiferentes() {
        String password = "ContraseñaIdentica456";
        String hash1 = hasher.hash(password);
        String hash2 = hasher.hash(password);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(hasher.verify(password, hash1)).isTrue();
        assertThat(hasher.verify(password, hash2)).isTrue();
    }

    @Test
    @DisplayName("VP-018: La verificación es exitosa con la contraseña correcta y falla con contraseña incorrecta")
    void testVerificacionExitosaYFallida() {
        String password = "ClaveValida2026";
        String hash = hasher.hash(password);

        assertThat(hasher.verify("ClaveValida2026", hash)).isTrue();
        assertThat(hasher.verify("ClaveErronea2026", hash)).isFalse();
        assertThat(hasher.verify("clavevalida2026", hash)).isFalse();
        assertThat(hasher.verify("", hash)).isFalse();
        assertThat(hasher.verify(null, hash)).isFalse();
    }

    @Test
    @DisplayName("Debe lanzar excepción si se intenta hashear una contraseña nula o vacía")
    void testHashearContrasenaInvalida() {
        assertThatThrownBy(() -> hasher.hash(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> hasher.hash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
