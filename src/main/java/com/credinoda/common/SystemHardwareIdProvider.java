package com.credinoda.common;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proveedor multiplataforma determinista de Hardware ID para Windows, macOS y Linux (RN-031, VP-026).
 * Extrae identificadores físicos del equipo sin dependencias JNI/JNA externas y aplica hash SHA-256
 * formateado como 'CN20-XXXX-XXXX-XXXX-XXXX' con caché en memoria.
 */
public final class SystemHardwareIdProvider implements HardwareIdProvider {

    private static final String PREFIJO = "CN20";
    private static final long TIMEOUT_PROCESO_SEGUNDOS = 3L;

    private static final Set<String> PLACEHOLDERS_OEM = Set.of(
            "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF",
            "00000000-0000-0000-0000-000000000000",
            "TO BE FILLED BY O.E.M.",
            "DEFAULT STRING",
            "NONE",
            "SYSTEM SERIAL NUMBER",
            "UNKNOWN",
            "CHASSIS SERIAL NUMBER"
    );

    private static final Pattern PATTERN_MACHINE_GUID = Pattern.compile("MachineGuid\\s+REG_SZ\\s+([a-zA-Z0-9\\-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PATTERN_IOREG_UUID = Pattern.compile("\"IOPlatformUUID\"\\s*=\\s*\"([^\"]+)\"");

    private volatile String cachedHardwareId;

    public SystemHardwareIdProvider() {
    }

    @Override
    public String obtenerHardwareId() {
        if (cachedHardwareId == null) {
            synchronized (this) {
                if (cachedHardwareId == null) {
                    cachedHardwareId = detectarHardwareIdNativo();
                }
            }
        }
        return cachedHardwareId;
    }

    /**
     * Limpia la caché en memoria (útil para pruebas unitarias).
     */
    public void invalidarCache() {
        synchronized (this) {
            this.cachedHardwareId = null;
        }
    }

    /**
     * Detecta los identificadores del hardware físico según el sistema operativo en tiempo de ejecución.
     */
    private String detectarHardwareIdNativo() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> componentes = new ArrayList<>();

        if (os.contains("win")) {
            extraerComponentesWindows(componentes);
        } else if (os.contains("mac")) {
            extraerComponentesMac(componentes);
        } else {
            extraerComponentesLinux(componentes);
        }

        // Si los métodos específicos del SO no arrojaron identificadores suficientes, recurrir al fallback
        if (componentes.isEmpty()) {
            extraerComponentesFallback(componentes);
        }

        return formatearDesdeComponentes(componentes);
    }

    private void extraerComponentesWindows(List<String> componentes) {
        // 1. Registro de Windows: MachineGuid
        try {
            String salidaReg = ejecutarComando(List.of("reg", "query", "HKLM\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"));
            if (salidaReg != null) {
                Matcher m = PATTERN_MACHINE_GUID.matcher(salidaReg);
                if (m.find()) {
                    agregarSiValido(componentes, m.group(1));
                }
            }
        } catch (Exception ignored) {
        }

        // 2. PowerShell / CIM: ComputerSystemProduct UUID y ProcessorId
        try {
            String salidaPs = ejecutarComando(List.of(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "(Get-CimInstance -ClassName Win32_ComputerSystemProduct).UUID; (Get-CimInstance -ClassName Win32_Processor).ProcessorId"
            ));
            if (salidaPs != null) {
                for (String line : salidaPs.split("\\r?\\n")) {
                    agregarSiValido(componentes, line);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void extraerComponentesMac(List<String> componentes) {
        try {
            String salidaIoreg = ejecutarComando(List.of("ioreg", "-rd1", "-c", "IOPlatformExpertDevice"));
            if (salidaIoreg != null) {
                Matcher m = PATTERN_IOREG_UUID.matcher(salidaIoreg);
                if (m.find()) {
                    agregarSiValido(componentes, m.group(1));
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void extraerComponentesLinux(List<String> componentes) {
        // /etc/machine-id
        leerArchivoSiExiste("/etc/machine-id", componentes);
        // /var/lib/dbus/machine-id
        leerArchivoSiExiste("/var/lib/dbus/machine-id", componentes);
        // /sys/class/dmi/id/product_uuid
        leerArchivoSiExiste("/sys/class/dmi/id/product_uuid", componentes);
    }

    private void extraerComponentesFallback(List<String> componentes) {
        // MAC addresses de interfaces físicas activas
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface nif : Collections.list(interfaces)) {
                    if (!nif.isLoopback() && !nif.isVirtual() && nif.getHardwareAddress() != null) {
                        byte[] mac = nif.getHardwareAddress();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mac) {
                            sb.append(String.format("%02X", b));
                        }
                        if (!sb.isEmpty()) {
                            agregarSiValido(componentes, sb.toString());
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // Atributos de sistema operativo y usuario
        componentes.add(System.getProperty("os.arch", "generic_arch"));
        try {
            componentes.add(InetAddress.getLocalHost().getHostName());
        } catch (Exception ignored) {
            componentes.add(System.getProperty("user.name", "credinoda_user"));
        }
    }

    private void leerArchivoSiExiste(String rutaArchivo, List<String> componentes) {
        try {
            Path path = Path.of(rutaArchivo);
            if (Files.isRegularFile(path) && Files.isReadable(path)) {
                String contenido = Files.readString(path, StandardCharsets.UTF_8);
                agregarSiValido(componentes, contenido);
            }
        } catch (Exception ignored) {
        }
    }

    private static void agregarSiValido(List<String> lista, String raw) {
        if (raw == null) {
            return;
        }
        String limpio = raw.trim();
        if (limpio.isEmpty()) {
            return;
        }
        String upper = limpio.toUpperCase(Locale.ROOT);
        if (!PLACEHOLDERS_OEM.contains(upper)) {
            lista.add(upper);
        }
    }

    private static String ejecutarComando(List<String> comando) {
        try {
            ProcessBuilder pb = new ProcessBuilder(comando);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean terminado = process.waitFor(TIMEOUT_PROCESO_SEGUNDOS, TimeUnit.SECONDS);
            if (!terminado) {
                process.destroyForcibly();
                return null;
            }
            return output.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Construye y formatea el Hardware ID determinista a partir de los componentes extraídos (VP-026).
     *
     * @param componentes Lista de identificadores del equipo.
     * @return Hardware ID agrupado como 'CN20-XXXX-XXXX-XXXX-XXXX'.
     */
    public static String formatearDesdeComponentes(List<String> componentes) {
        if (componentes == null || componentes.isEmpty()) {
            componentes = List.of("CREDINODA_DEFAULT_HARDWARE_ENTROPY");
        }

        StringBuilder sb = new StringBuilder();
        for (String c : componentes) {
            if (c != null && !c.isBlank()) {
                String norm = c.trim().toUpperCase(Locale.ROOT);
                if (!PLACEHOLDERS_OEM.contains(norm)) {
                    sb.append(norm).append(";");
                }
            }
        }

        if (sb.isEmpty()) {
            sb.append("CREDINODA_RESCUE_ENTROPY");
        }

        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest(sb.toString().getBytes(StandardCharsets.UTF_8));

            // Extraemos los primeros 8 bytes (16 caracteres hexadecimales)
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02X", hash[i]));
            }

            String hexStr = hex.toString();
            // Formatear en 4 grupos de 4 caracteres
            return String.format("%s-%s-%s-%s-%s",
                    PREFIJO,
                    hexStr.substring(0, 4),
                    hexStr.substring(4, 8),
                    hexStr.substring(8, 12),
                    hexStr.substring(12, 16)
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible", e);
        }
    }
}
