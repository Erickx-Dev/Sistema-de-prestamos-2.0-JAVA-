package com.credinoda.common;

import com.credinoda.domain.exception.MontoInvalidoException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Utilidades de validación y parseo flexible de entradas monetarias y de texto.
 * Desacoplado al 100% de cualquier framework o librería gráfica de UI.
 */
public final class Validators {

    private Validators() {
        // Clase utilitaria, no instanciable
    }

    /**
     * Registro para representar el resultado de una validación de interfaz/formulario.
     */
    public record ResultadoValidacion(boolean valido, String mensaje) {
        public static ResultadoValidacion ok() {
            return new ResultadoValidacion(true, "");
        }

        public static ResultadoValidacion error(String mensaje) {
            return new ResultadoValidacion(false, mensaje);
        }
    }

    /**
     * Convierte texto a BigDecimal admitiendo formatos flexibles:
     * - "11333.33" o "11333,33" (decimales con punto o coma)
     * - "11.333,33" o "11,333.33" (separadores de miles y decimales)
     * - "11333" (enteros sin separadores)
     * - "1.000.000" o "1,000,000" (separadores de miles)
     * - "$11.333,33" o "$ 11333.33" (con signo de pesos y espacios)
     *
     * @param texto Texto a parsear
     * @return BigDecimal exacto
     * @throws MontoInvalidoException Si el formato no es válido
     */
    public static BigDecimal parsearMonto(String texto) {
        if (texto == null) {
            throw new MontoInvalidoException("El texto es null");
        }

        String limpio = texto.trim()
                .replace("$", "")
                .replace(" ", "")
                .replace("\u00a0", "");

        if (limpio.isEmpty()) {
            throw new MontoInvalidoException("Texto vacío");
        }

        String sign = "";
        if (limpio.startsWith("-")) {
            sign = "-";
            limpio = limpio.substring(1);
        } else if (limpio.startsWith("+")) {
            limpio = limpio.substring(1);
        }

        if (limpio.isEmpty()) {
            throw new MontoInvalidoException("No contiene dígitos");
        }

        if (!limpio.matches("^[\\d.,]+$")) {
            throw new MontoInvalidoException("Caracteres inválidos en monto: " + limpio);
        }

        if (limpio.contains("..") || limpio.contains(",,") || limpio.contains(".,") || limpio.contains(",.")) {
            throw new MontoInvalidoException("Separadores consecutivos no permitidos");
        }

        long dots = limpio.chars().filter(ch -> ch == '.').count();
        long commas = limpio.chars().filter(ch -> ch == ',').count();
        String textoLimpio;

        // Caso 1: Hay tanto puntos como comas (ej. 1.000,50 o 1,000.50)
        if (dots > 0 && commas > 0) {
            int lastDot = limpio.lastIndexOf('.');
            int lastComma = limpio.lastIndexOf(',');
            if (lastComma > lastDot) {
                // Coma es el separador decimal (ej. 1.000.000,50 o 11.333,33)
                if (commas > 1) {
                    throw new MontoInvalidoException("Múltiples separadores decimales");
                }
                String[] thousandParts = limpio.substring(0, lastComma).split("\\.", -1);
                if (thousandParts.length > 1) {
                    for (int i = 1; i < thousandParts.length; i++) {
                        if (thousandParts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                }
                textoLimpio = limpio.replace(".", "").replace(",", ".");
            } else {
                // Punto es el separador decimal (ej. 1,000,000.50 o 11,333.33)
                if (dots > 1) {
                    throw new MontoInvalidoException("Múltiples separadores decimales");
                }
                String[] thousandParts = limpio.substring(0, lastDot).split(",", -1);
                if (thousandParts.length > 1) {
                    for (int i = 1; i < thousandParts.length; i++) {
                        if (thousandParts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                }
                textoLimpio = limpio.replace(",", "");
            }
        }
        // Caso 2: Solo hay puntos (ej. 11333.33, 1.000.000, 10.000, 500.50, 11.333.33)
        else if (dots > 0) {
            String[] parts = limpio.split("\\.", -1);
            String lastPart = parts[parts.length - 1];
            if (lastPart.length() == 1 || lastPart.length() == 2) {
                // El último punto es decimal; cualquier punto anterior era de miles
                if (parts.length > 2) {
                    for (int i = 1; i < parts.length - 1; i++) {
                        if (parts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                }
                StringBuilder entero = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    entero.append(parts[i]);
                }
                String enteroStr = entero.length() > 0 ? entero.toString() : "0";
                textoLimpio = enteroStr + "." + lastPart;
            } else if (parts.length > 1) {
                if (parts.length == 2 && parts[0].length() > 3) {
                    textoLimpio = parts[0] + "." + parts[1];
                } else {
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String p : parts) {
                        sb.append(p);
                    }
                    textoLimpio = sb.toString();
                }
            } else {
                textoLimpio = limpio;
            }
        }
        // Caso 3: Solo hay comas (ej. 11333,33, 1,000,000, 10,000, 500,50, 11,333,33)
        else if (commas > 0) {
            String[] parts = limpio.split(",", -1);
            String lastPart = parts[parts.length - 1];
            if (lastPart.length() == 1 || lastPart.length() == 2) {
                if (parts.length > 2) {
                    for (int i = 1; i < parts.length - 1; i++) {
                        if (parts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                }
                StringBuilder entero = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    entero.append(parts[i]);
                }
                String enteroStr = entero.length() > 0 ? entero.toString() : "0";
                textoLimpio = enteroStr + "." + lastPart;
            } else if (parts.length > 1) {
                if (parts.length == 2 && parts[0].length() > 3) {
                    textoLimpio = parts[0] + "." + parts[1];
                } else {
                    for (int i = 1; i < parts.length; i++) {
                        if (parts[i].length() != 3) {
                            throw new MontoInvalidoException("Grupos de miles inválidos");
                        }
                    }
                    StringBuilder sb = new StringBuilder();
                    for (String p : parts) {
                        sb.append(p);
                    }
                    textoLimpio = sb.toString();
                }
            } else {
                textoLimpio = limpio;
            }
        }
        // Caso 4: Solo dígitos
        else {
            textoLimpio = limpio;
        }

        try {
            return new BigDecimal(sign + textoLimpio);
        } catch (NumberFormatException e) {
            throw new MontoInvalidoException("Error al convertir monto a BigDecimal: " + texto, e);
        }
    }

    /**
     * Valida que un texto sea un monto monetario válido y mayor a cero.
     *
     * @param texto Texto ingresado
     * @return ResultadoValidacion con booleano y mensaje en caso de error
     */
    public static ResultadoValidacion validarMonto(String texto) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("Ingresa un monto.");
        }
        try {
            BigDecimal valor = parsearMonto(texto);
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                return ResultadoValidacion.error("El monto debe ser mayor a cero.");
            }
            return ResultadoValidacion.ok();
        } catch (Exception e) {
            return ResultadoValidacion.error("Ingresa un monto numérico válido.");
        }
    }

    /**
     * Valida que un texto no sea nulo ni contenga únicamente espacios en blanco.
     */
    public static ResultadoValidacion validarTextoNoVacio(String texto, String campo) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("El campo " + campo + " es obligatorio.");
        }
        return ResultadoValidacion.ok();
    }

    /**
     * Valida que un texto represente un número entero estrictamente positivo (> 0).
     */
    public static ResultadoValidacion validarEnteroPositivo(String texto, String campo) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("El campo " + campo + " es obligatorio.");
        }
        try {
            int valor = Integer.parseInt(texto.strip());
            if (valor <= 0) {
                return ResultadoValidacion.error("El campo " + campo + " debe ser mayor a cero.");
            }
            return ResultadoValidacion.ok();
        } catch (NumberFormatException e) {
            return ResultadoValidacion.error("El campo " + campo + " debe ser un número entero.");
        }
    }

    /**
     * Valida el nombre completo de un cliente (2 a 100 caracteres, presencia de letras reales).
     */
    public static ResultadoValidacion validarNombreCliente(String nombre) {
        if (nombre == null || nombre.strip().isEmpty()) {
            return ResultadoValidacion.error("El nombre del cliente es obligatorio.");
        }
        String limpio = nombre.strip();
        if (limpio.length() < 2 || limpio.length() > 100) {
            return ResultadoValidacion.error("El nombre debe tener entre 2 y 100 caracteres.");
        }
        if (!limpio.matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s.'-]+$")) {
            return ResultadoValidacion.error("El nombre contiene caracteres especiales o números no permitidos.");
        }
        long letras = limpio.chars().filter(Character::isLetter).count();
        if (letras < 2) {
            return ResultadoValidacion.error("El nombre debe contener al menos 2 letras válidas.");
        }
        return ResultadoValidacion.ok();
    }

    /**
     * Valida el documento de identidad de un cliente (4 a 20 caracteres alfanuméricos con presencia obligatoria de dígitos o formato).
     * Rechaza entradas inválidas o abreviaturas como "mk" o cadenas de solo letras.
     */
    public static ResultadoValidacion validarDocumentoCliente(String documento) {
        if (documento == null || documento.strip().isEmpty()) {
            return ResultadoValidacion.error("El documento de identidad es obligatorio.");
        }
        String limpio = documento.strip();
        if (limpio.length() < 4 || limpio.length() > 20) {
            return ResultadoValidacion.error("El documento debe tener entre 4 y 20 caracteres.");
        }
        if (!limpio.matches("^[A-Za-z0-9-]+$")) {
            return ResultadoValidacion.error("El documento solo puede contener números, letras y guiones (sin espacios).");
        }
        boolean tieneDigitoOGuion = limpio.chars().anyMatch(ch -> Character.isDigit(ch) || ch == '-');
        if (!tieneDigitoOGuion) {
            return ResultadoValidacion.error("El documento de identidad debe contener números o formato válido.");
        }
        return ResultadoValidacion.ok();
    }

    /**
     * Valida el número de teléfono (4 a 20 caracteres, 4 a 15 dígitos reales, prohíbe letras).
     */
    public static ResultadoValidacion validarTelefonoCliente(String telefono) {
        if (telefono == null || telefono.strip().isEmpty()) {
            return ResultadoValidacion.error("El teléfono del cliente es obligatorio.");
        }
        String limpio = telefono.strip();
        if (!limpio.matches("^[+]?[0-9\\s\\-()]+$")) {
            return ResultadoValidacion.error("El teléfono debe contener únicamente números y signos de formato (+, -, ()).");
        }
        long digitos = limpio.chars().filter(Character::isDigit).count();
        if (digitos < 4 || digitos > 15) {
            return ResultadoValidacion.error("El teléfono debe contener entre 4 y 15 dígitos numéricos.");
        }
        return ResultadoValidacion.ok();
    }

    /**
     * Valida el plazo en meses de un crédito (entre 1 y 120 meses).
     */
    public static ResultadoValidacion validarPlazoMeses(String texto) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("El plazo en meses es obligatorio.");
        }
        try {
            int plazo = Integer.parseInt(texto.strip());
            if (plazo < 1 || plazo > 120) {
                return ResultadoValidacion.error("El plazo debe ser de entre 1 y 120 meses.");
            }
            return ResultadoValidacion.ok();
        } catch (NumberFormatException e) {
            return ResultadoValidacion.error("El plazo debe ser un número entero de meses.");
        }
    }

    /**
     * Valida la tasa de interés mensual en porcentaje (entre 0% y 100%).
     */
    public static ResultadoValidacion validarTasaMensual(String texto) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("La tasa de interés mensual es obligatoria.");
        }
        try {
            BigDecimal tasa = parsearMonto(texto);
            if (tasa.compareTo(BigDecimal.ZERO) < 0 || tasa.compareTo(BigDecimal.valueOf(100)) > 0) {
                return ResultadoValidacion.error("La tasa mensual debe estar entre 0% y 100%.");
            }
            return ResultadoValidacion.ok();
        } catch (Exception e) {
            return ResultadoValidacion.error("La tasa de interés debe ser un valor numérico válido.");
        }
    }

    /**
     * Valida el capital a prestar (monto mayor a cero).
     */
    public static ResultadoValidacion validarCapital(String texto) {
        if (texto == null || texto.strip().isEmpty()) {
            return ResultadoValidacion.error("El capital a prestar es obligatorio.");
        }
        try {
            BigDecimal capital = parsearMonto(texto);
            if (capital.compareTo(BigDecimal.ZERO) <= 0) {
                return ResultadoValidacion.error("El capital a prestar debe ser mayor a cero.");
            }
            return ResultadoValidacion.ok();
        } catch (Exception e) {
            return ResultadoValidacion.error("El capital debe ser un monto numérico válido.");
        }
    }

    /**
     * Valida permisos reales de escritura en un directorio creando y eliminando un archivo temporal testigo.
     * (CU-016, RN-020).
     *
     * @param dir Ruta del directorio a auditar.
     * @return ResultadoValidacion indicando éxito o mensaje de error detallado.
     */
    public static ResultadoValidacion validarPermisosDirectorio(Path dir) {
        if (dir == null) {
            return ResultadoValidacion.error("La ruta de la carpeta no puede ser nula.");
        }

        if (esDirectorioProtegido(dir)) {
            return ResultadoValidacion.error("La carpeta seleccionada es un directorio protegido del sistema operativo.");
        }

        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            return ResultadoValidacion.error("No se pudo acceder o crear la carpeta: " + e.getMessage());
        }

        Path testigo = dir.resolve(".test_credinoda_access_" + System.currentTimeMillis() + ".tmp");
        try {
            Files.writeString(testigo, "credinoda_write_test", StandardCharsets.UTF_8);
            Files.deleteIfExists(testigo);
            return ResultadoValidacion.ok();
        } catch (Exception e) {
            try {
                Files.deleteIfExists(testigo);
            } catch (IOException ignored) {
            }
            return ResultadoValidacion.error("No se tienen permisos de escritura en la carpeta: " + e.getMessage());
        }
    }

    /**
     * Detecta si una ruta se ubica dentro de carpetas sincronizadas en la nube (OneDrive, Google Drive, Dropbox, etc.).
     * (RN-020).
     *
     * @param dir Ruta del directorio a auditar.
     * @return true si la ruta coincide con un proveedor de almacenamiento en la nube, false de lo contrario.
     */
    public static boolean esCarpetaEnLaNube(Path dir) {
        if (dir == null) {
            return false;
        }

        String pathStr = dir.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);

        List<String> cloudKeywords = List.of(
                "onedrive", "dropbox", "google drive", "google_drive", "gdrive",
                "icloud", "box sync", "box\\", "box/", "nextcloud", "owncloud"
        );
        for (String keyword : cloudKeywords) {
            if (pathStr.contains(keyword)) {
                return true;
            }
        }

        for (String envKey : List.of("OneDrive", "OneDriveConsumer", "OneDriveCommercial")) {
            String envVal = System.getenv(envKey);
            if (envVal != null && !envVal.isBlank()) {
                String envNorm = Path.of(envVal).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
                if (pathStr.equals(envNorm) || pathStr.startsWith(envNorm + "\\") || pathStr.startsWith(envNorm + "/")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determina si la ruta apunta a un directorio raíz o protegido del sistema operativo.
     * (RN-020, VP-017).
     *
     * @param dir Ruta del directorio a auditar.
     * @return true si es un directorio protegido o raíz, false si es una carpeta de usuario permitida.
     */
    public static boolean esDirectorioProtegido(Path dir) {
        if (dir == null) {
            return true;
        }

        Path abs = dir.toAbsolutePath().normalize();

        // Raíz absoluta del disco (ej. C:\ o /)
        if (abs.getParent() == null) {
            return true;
        }

        String pathStr = abs.toString().toLowerCase(Locale.ROOT);

        // Directorios estándar protegidos en Windows
        for (String envKey : List.of("SystemRoot", "windir", "ProgramFiles", "ProgramFiles(x86)", "ProgramData")) {
            String envVal = System.getenv(envKey);
            if (envVal != null && !envVal.isBlank()) {
                String envNorm = Path.of(envVal).toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
                if (pathStr.equals(envNorm) || pathStr.startsWith(envNorm + "\\") || pathStr.startsWith(envNorm + "/")) {
                    return true;
                }
            }
        }

        // Chequeos literales de protección
        if (pathStr.startsWith("c:\\windows") ||
                pathStr.startsWith("c:\\program files") ||
                pathStr.startsWith("c:\\program files (x86)") ||
                pathStr.startsWith("c:\\programdata")) {
            return true;
        }

        // Directorios protegidos en Linux/macOS
        List<String> unixProtected = List.of("/bin", "/sbin", "/usr", "/etc", "/var", "/sys", "/proc", "/dev", "/root");
        for (String prot : unixProtected) {
            if (pathStr.equals(prot) || pathStr.startsWith(prot + "/")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Valida una contraseña de usuario según la política de seguridad (RN-022).
     * Requiere no ser nula ni vacía, longitud mínima de 6 caracteres y sin espacios al inicio o final.
     *
     * @param password Contraseña en texto plano a evaluar.
     * @return ResultadoValidacion indicando si cumple la política.
     */
    public static ResultadoValidacion validarPassword(String password) {
        if (password == null || password.isEmpty()) {
            return ResultadoValidacion.error("La contraseña no puede estar vacía.");
        }
        if (password.startsWith(" ") || password.endsWith(" ")) {
            return ResultadoValidacion.error("La contraseña no puede iniciar ni terminar con espacios.");
        }
        if (password.length() < 6) {
            return ResultadoValidacion.error("La contraseña debe tener al menos 6 caracteres.");
        }
        return ResultadoValidacion.ok();
    }

    /**
     * Valida un nombre de usuario (username) del sistema.
     * Requiere longitud entre 3 y 30 caracteres alfanuméricos, puntos, guiones o guiones bajos.
     *
     * @param username Nombre de usuario a evaluar.
     * @return ResultadoValidacion indicando validez.
     */
    public static ResultadoValidacion validarUsername(String username) {
        if (username == null || username.isBlank()) {
            return ResultadoValidacion.error("El nombre de usuario no puede estar vacío.");
        }
        String limpio = username.trim();
        if (limpio.length() < 3 || limpio.length() > 30) {
            return ResultadoValidacion.error("El nombre de usuario debe tener al menos 3 caracteres y un máximo de 30.");
        }
        if (!limpio.matches("^[a-zA-Z0-9._-]+$")) {
            return ResultadoValidacion.error("El nombre de usuario solo puede contener letras, números, puntos, guiones y guiones bajos.");
        }
        return ResultadoValidacion.ok();
    }
}

