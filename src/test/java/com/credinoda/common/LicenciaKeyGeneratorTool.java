package com.credinoda.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.LocalDate;
import java.util.Base64;

/**
 * Herramienta de soporte y utilidades para pruebas automatizadas y emisión de licencias offline (VP-025).
 * Permite generar pares de claves Ed25519 en memoria y firmar tokens para pruebas.
 */
public final class LicenciaKeyGeneratorTool {

    private LicenciaKeyGeneratorTool() {
    }

    /**
     * Genera un nuevo par de llaves Ed25519.
     */
    public static KeyPair generarParLlavesEd25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Algoritmo Ed25519 no disponible en este JDK: " + e.getMessage(), e);
        }
    }

    /**
     * Exporta una llave pública a formato X.509 Base64 estándar.
     */
    public static String exportarLlavePublicaBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Exporta una llave privada a formato PKCS#8 Base64 estándar.
     */
    public static String exportarLlavePrivadaBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * Genera y firma digitalmente un token offline de CrediNoda 2.0.
     */
    public static String firmarLicencia(PrivateKey privateKey,
                                       String licenciaId,
                                       String titular,
                                       String hardwareId,
                                       LocalDate fechaEmision,
                                       LocalDate fechaExpiracion,
                                       String tipo) {
        String payloadText = LicenciaCryptoVerifier.construirPayloadCanonica(
                licenciaId, titular, hardwareId, fechaEmision, fechaExpiracion, tipo
        );
        byte[] payloadBytes = payloadText.getBytes(StandardCharsets.UTF_8);

        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initSign(privateKey);
            sig.update(payloadBytes);
            byte[] signatureBytes = sig.sign();

            String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadBytes);
            String signatureB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            return LicenciaCryptoVerifier.TOKEN_PREFIX + payloadB64 + "." + signatureB64;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al firmar token con Ed25519: " + e.getMessage(), e);
        }
    }
}
