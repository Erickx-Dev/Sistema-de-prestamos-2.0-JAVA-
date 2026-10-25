package com.credinoda.common;

import com.credinoda.domain.exception.LicenciaException;
import com.credinoda.domain.model.DatosLicencia;
import com.credinoda.domain.model.EstadoLicencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.LocalDate;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de LicenciaCryptoVerifier (RN-030, VP-025)")
class LicenciaCryptoVerifierTest {

    @Test
    @DisplayName("Debe cargar correctamente la llave pública oficial embebida sin errores")
    void testCargarLlavePublicaOficial() {
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier();
        assertThat(verifier.getPublicKey()).isNotNull();
        assertThat(verifier.getPublicKey().getAlgorithm()).isIn("EdDSA", "Ed25519");
    }

    @Test
    @DisplayName("VP-025: Firma Ed25519 válida es verificada exitosamente extrayendo DatosLicencia íntegros")
    void testVerificacionFirmaValida() {
        KeyPair kp = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier(kp.getPublic());

        LocalDate emision = LocalDate.of(2026, 10, 24);
        LocalDate expiracion = LocalDate.of(2027, 10, 24);
        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                kp.getPrivate(),
                "LIC-2026-001",
                "CrediNoda Demo S.A.S.",
                "CN20-A1B2-C3D4-E5F6",
                emision,
                expiracion,
                "ANUAL"
        );

        DatosLicencia datos = verifier.verificarYParsearToken(token);

        assertThat(datos).isNotNull();
        assertThat(datos.getLicenciaId()).isEqualTo("LIC-2026-001");
        assertThat(datos.getTitular()).isEqualTo("CrediNoda Demo S.A.S.");
        assertThat(datos.getHardwareId()).isEqualTo("CN20-A1B2-C3D4-E5F6");
        assertThat(datos.getFechaEmision()).isEqualTo(emision);
        assertThat(datos.getFechaExpiracion()).isEqualTo(expiracion);
        assertThat(datos.getTipoLicencia()).isEqualTo("ANUAL");
    }

    @Test
    @DisplayName("VP-025: Debe rechazar alteración de un solo carácter en la fecha de expiración con FIRMA_INVALIDA")
    void testRechazoAlteracionFechaExpiracion() {
        KeyPair kp = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier(kp.getPublic());

        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                kp.getPrivate(),
                "LIC-2026-002",
                "Usuario Legítimo",
                "CN20-1111-2222-3333",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 6, 1),
                "SEMESTRAL"
        );

        // Modificamos el payload alterando la fecha de expiración
        String[] partes = token.substring(LicenciaCryptoVerifier.TOKEN_PREFIX.length()).split("\\.");
        String payloadText = new String(Base64.getUrlDecoder().decode(partes[0]));
        String payloadAlteradoText = payloadText.replace("2026-06-01", "2029-06-01");
        String payloadAlteradoB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadAlteradoText.getBytes());

        String tokenAlterado = LicenciaCryptoVerifier.TOKEN_PREFIX + payloadAlteradoB64 + "." + partes[1];

        assertThatThrownBy(() -> verifier.verificarYParsearToken(tokenAlterado))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FIRMA_INVALIDA));
    }

    @Test
    @DisplayName("VP-025: Debe rechazar alteración de un carácter en el Hardware ID con FIRMA_INVALIDA")
    void testRechazoAlteracionHardwareId() {
        KeyPair kp = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier(kp.getPublic());

        String token = LicenciaKeyGeneratorTool.firmarLicencia(
                kp.getPrivate(),
                "LIC-2026-003",
                "Usuario Legítimo",
                "CN20-AAAA-BBBB-CCCC",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                "ANUAL"
        );

        String[] partes = token.substring(LicenciaCryptoVerifier.TOKEN_PREFIX.length()).split("\\.");
        String payloadText = new String(Base64.getUrlDecoder().decode(partes[0]));
        String payloadAlteradoText = payloadText.replace("CN20-AAAA-BBBB-CCCC", "CN20-AAAA-BBBB-CCCD");
        String payloadAlteradoB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadAlteradoText.getBytes());

        String tokenAlterado = LicenciaCryptoVerifier.TOKEN_PREFIX + payloadAlteradoB64 + "." + partes[1];

        assertThatThrownBy(() -> verifier.verificarYParsearToken(tokenAlterado))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FIRMA_INVALIDA));
    }

    @Test
    @DisplayName("Debe rechazar token firmado con otra clave privada Ed25519")
    void testRechazoFirmaConOtraClavePrivada() {
        KeyPair kp1 = LicenciaKeyGeneratorTool.generarParLlavesEd25519();
        KeyPair kp2 = LicenciaKeyGeneratorTool.generarParLlavesEd25519();

        LicenciaCryptoVerifier verifier1 = new LicenciaCryptoVerifier(kp1.getPublic());

        // Firmar con kp2 (ajena)
        String tokenAjeno = LicenciaKeyGeneratorTool.firmarLicencia(
                kp2.getPrivate(),
                "LIC-2026-AJENO",
                "Titular",
                "CN20-0000-0000-0000",
                LocalDate.now(),
                LocalDate.now().plusYears(1),
                "ANUAL"
        );

        assertThatThrownBy(() -> verifier1.verificarYParsearToken(tokenAjeno))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FIRMA_INVALIDA));
    }

    @Test
    @DisplayName("Debe rechazar tokens con estructura incompleta o no delimitada con FORMATO_INVALIDO")
    void testRechazoEstructuraInvalida() {
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier();

        assertThatThrownBy(() -> verifier.verificarYParsearToken(null))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FORMATO_INVALIDO));

        assertThatThrownBy(() -> verifier.verificarYParsearToken("token_sin_punto"))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FORMATO_INVALIDO));

        assertThatThrownBy(() -> verifier.verificarYParsearToken("parte1.parte2.parte3"))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FORMATO_INVALIDO));

        assertThatThrownBy(() -> verifier.verificarYParsearToken("."))
                .isInstanceOf(LicenciaException.class)
                .satisfies(ex -> assertThat(((LicenciaException) ex).getEstado()).isEqualTo(EstadoLicencia.FORMATO_INVALIDO));
    }

    @Test
    @DisplayName("RN-032: Sello HmacSHA256 es determinista y detecta adulteración en hardwareId, timestamp o token")
    void testSelloHmacIntegridad() {
        LicenciaCryptoVerifier verifier = new LicenciaCryptoVerifier();

        String hwId = "CN20-9999-8888-7777";
        long timestamp = 1792886400L;
        String token = "CREDINODA-LIC-V1:mockpayload.mocksignature";

        String hmac1 = verifier.calcularHmacTiempo(hwId, timestamp, token);
        String hmac2 = verifier.calcularHmacTiempo(hwId, timestamp, token);

        assertThat(hmac1).isNotBlank();
        assertThat(hmac1).isEqualTo(hmac2);
        assertThat(verifier.verificarHmacTiempo(hwId, timestamp, token, hmac1)).isTrue();

        // Adulteraciones
        assertThat(verifier.verificarHmacTiempo("CN20-OTRO-8888-7777", timestamp, token, hmac1)).isFalse();
        assertThat(verifier.verificarHmacTiempo(hwId, timestamp + 1, token, hmac1)).isFalse();
        assertThat(verifier.verificarHmacTiempo(hwId, timestamp, "token_diferente", hmac1)).isFalse();
        assertThat(verifier.verificarHmacTiempo(hwId, timestamp, token, "hmac_invalido")).isFalse();
    }
}
