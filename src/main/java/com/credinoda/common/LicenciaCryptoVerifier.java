package com.credinoda.common;

import com.credinoda.domain.exception.LicenciaException;
import com.credinoda.domain.model.DatosLicencia;
import com.credinoda.domain.model.EstadoLicencia;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Componente criptográfico para la validación de licencias comerciales offline mediante firmas asimétricas
 * Ed25519 (JEP 339) y sellado de marcas de tiempo con HmacSHA256 (RN-030, RN-032, VP-025).
 * Embebe exclusivamente la llave pública X.509 estática oficial del sistema.
 */
public final class LicenciaCryptoVerifier {

    /**
     * Llave pública X.509/SPKI oficial en Base64 para verificación de firmas Ed25519 en CrediNoda 2.0.
     */
    public static final String DEFAULT_PUBLIC_KEY_BASE64 =
            "MCowBQYDK2VwAyEAmH/wcqjGhUSmXX5yxsgqal2kUBr8gzJ4ib99dN4Kv7k=";

    public static final String TOKEN_PREFIX = "CREDINODA-LIC-V1:";

    private final PublicKey publicKey;
    private final byte[] hmacSecretKeyBytes;

    /**
     * Constructor por defecto: utiliza la llave pública oficial del sistema.
     */
    public LicenciaCryptoVerifier() {
        this(DEFAULT_PUBLIC_KEY_BASE64);
    }

    /**
     * Constructor con llave pública en formato X.509 Base64 (útil para inyección en pruebas automatizadas).
     *
     * @param publicKeyBase64 Cadena Base64 con la llave pública X.509.
     */
    public LicenciaCryptoVerifier(String publicKeyBase64) {
        Objects.requireNonNull(publicKeyBase64, "La llave pública no puede ser nula");
        try {
            byte[] keyBytes = decodeBase64Flexible(publicKeyBase64.trim());
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("Ed25519");
            this.publicKey = kf.generatePublic(spec);
            this.hmacSecretKeyBytes = derivarSecretoHmac(keyBytes);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalArgumentException("No se pudo inicializar la llave pública Ed25519: " + e.getMessage(), e);
        }
    }

    /**
     * Constructor con objeto PublicKey tipado.
     *
     * @param publicKey Llave pública ya instanciada.
     */
    public LicenciaCryptoVerifier(PublicKey publicKey) {
        this.publicKey = Objects.requireNonNull(publicKey, "La llave pública no puede ser nula");
        this.hmacSecretKeyBytes = derivarSecretoHmac(publicKey.getEncoded());
    }

    /**
     * Parsea y verifica criptográficamente un token de licencia offline (RN-030, VP-025).
     *
     * @param rawToken Código del token en formato 'CREDINODA-LIC-V1:<payload_b64>.<sig_b64>' o sólo '<payload>.<sig>'.
     * @return Instancia inmutable de DatosLicencia si la firma y la estructura son auténticas.
     * @throws LicenciaException Si el formato es erróneo o la firma digital no es válida.
     */
    public DatosLicencia verificarYParsearToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new LicenciaException("El token de licencia está vacío o no fue proporcionado.", EstadoLicencia.FORMATO_INVALIDO);
        }

        String limpio = rawToken.trim();
        if (limpio.startsWith(TOKEN_PREFIX)) {
            limpio = limpio.substring(TOKEN_PREFIX.length()).trim();
        }

        // El token canónico se separa por un único punto '.'
        int dotIndex = limpio.indexOf('.');
        if (dotIndex <= 0 || dotIndex == limpio.length() - 1 || limpio.indexOf('.', dotIndex + 1) != -1) {
            throw new LicenciaException("Estructura de token inválida: se esperaba '<payload>.<firma>'.", EstadoLicencia.FORMATO_INVALIDO);
        }

        String payloadB64 = limpio.substring(0, dotIndex).trim();
        String signatureB64 = limpio.substring(dotIndex + 1).trim();

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = decodeBase64Flexible(payloadB64);
            signatureBytes = decodeBase64Flexible(signatureB64);
        } catch (IllegalArgumentException e) {
            throw new LicenciaException("Error de decodificación Base64 en el token de licencia.", EstadoLicencia.FORMATO_INVALIDO, e);
        }

        // 1. Verificación criptográfica asimétrica Ed25519 sobre los bytes exactos del payload
        try {
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(payloadBytes);
            if (!sig.verify(signatureBytes)) {
                throw new LicenciaException("Firma digital inválida: el contenido fue adulterado o no coincide con la llave pública.", EstadoLicencia.FIRMA_INVALIDA);
            }
        } catch (GeneralSecurityException e) {
            throw new LicenciaException("Error al ejecutar verificación de firma Ed25519: " + e.getMessage(), EstadoLicencia.FIRMA_INVALIDA, e);
        }

        // 2. Parseo determinista del payload UTF-8 canónico
        String payloadText = new String(payloadBytes, StandardCharsets.UTF_8);
        Map<String, String> mapaCampos = parsearLineasClaveValor(payloadText);

        String licenciaId = mapaCampos.get("licencia_id");
        String titular = mapaCampos.get("titular");
        String hardwareId = mapaCampos.get("hardware_id");
        String emitidaEnStr = mapaCampos.get("emitida_en");
        String expiraEnStr = mapaCampos.get("expira_en");
        String tipo = mapaCampos.getOrDefault("tipo", "COMERCIAL");

        if (licenciaId == null || titular == null || hardwareId == null || emitidaEnStr == null || expiraEnStr == null) {
            throw new LicenciaException("El payload de la licencia carece de campos obligatorios requeridos.", EstadoLicencia.FORMATO_INVALIDO);
        }

        LocalDate fechaEmision;
        LocalDate fechaExpiracion;
        try {
            fechaEmision = LocalDate.parse(emitidaEnStr);
            fechaExpiracion = LocalDate.parse(expiraEnStr);
        } catch (DateTimeParseException e) {
            throw new LicenciaException("Formato de fecha inválido en el payload de la licencia (se requiere YYYY-MM-DD).", EstadoLicencia.FORMATO_INVALIDO, e);
        }

        return new DatosLicencia(
                licenciaId,
                titular,
                hardwareId,
                fechaEmision,
                fechaExpiracion,
                tipo,
                signatureB64
        );
    }

    /**
     * Construye la representación canónica del payload determinista de una licencia.
     */
    public static String construirPayloadCanonica(String licenciaId,
                                                 String titular,
                                                 String hardwareId,
                                                 LocalDate fechaEmision,
                                                 LocalDate fechaExpiracion,
                                                 String tipo) {
        return "licencia_id=" + licenciaId.trim() + "\n" +
                "titular=" + titular.trim() + "\n" +
                "hardware_id=" + hardwareId.trim().toUpperCase() + "\n" +
                "emitida_en=" + fechaEmision.toString() + "\n" +
                "expira_en=" + fechaExpiracion.toString() + "\n" +
                "tipo=" + (tipo != null ? tipo.trim().toUpperCase() : "COMERCIAL");
    }

    /**
     * Genera un sello de autenticidad HmacSHA256 para proteger registros locales de tiempo (RN-032).
     */
    public String calcularHmacTiempo(String hardwareId, long ultimoTimestampUtc, String licenciaToken) {
        String data = (hardwareId != null ? hardwareId.trim().toUpperCase() : "") + "|" +
                ultimoTimestampUtc + "|" +
                (licenciaToken != null ? hashSha256Resumen(licenciaToken.trim()) : "");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacSecretKeyBytes, "HmacSHA256"));
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Error al calcular sello HmacSHA256: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica la integridad de un sello HmacSHA256 en tiempo constante.
     */
    public boolean verificarHmacTiempo(String hardwareId, long ultimoTimestampUtc, String licenciaToken, String hmacEsperado) {
        if (hmacEsperado == null || hmacEsperado.isBlank()) {
            return false;
        }
        String hmacCalculado = calcularHmacTiempo(hardwareId, ultimoTimestampUtc, licenciaToken);
        return MessageDigest.isEqual(
                hmacCalculado.getBytes(StandardCharsets.UTF_8),
                hmacEsperado.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    private static Map<String, String> parsearLineasClaveValor(String text) {
        Map<String, String> map = new HashMap<>();
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                String key = trimmed.substring(0, eq).trim().toLowerCase();
                String val = trimmed.substring(eq + 1).trim();
                map.put(key, val);
            }
        }
        return map;
    }

    private static byte[] decodeBase64Flexible(String b64) {
        String str = b64.replaceAll("\\s+", "");
        try {
            return Base64.getUrlDecoder().decode(str);
        } catch (IllegalArgumentException ignored) {
            return Base64.getDecoder().decode(str);
        }
    }

    private static byte[] derivarSecretoHmac(byte[] keyBytes) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update("CrediNoda_2.0_Licencia_AntiTampering_Salt".getBytes(StandardCharsets.UTF_8));
            return sha.digest(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }

    private static String hashSha256Resumen(String texto) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(texto.hashCode());
        }
    }
}
