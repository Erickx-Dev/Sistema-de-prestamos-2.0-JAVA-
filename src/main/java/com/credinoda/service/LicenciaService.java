package com.credinoda.service;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.HardwareIdProvider;
import com.credinoda.common.LicenciaCryptoVerifier;
import com.credinoda.common.SystemHardwareIdProvider;
import com.credinoda.domain.dto.ResultadoValidacionLicencia;
import com.credinoda.domain.exception.LicenciaException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.DatosLicencia;
import com.credinoda.domain.model.EstadoLicencia;
import com.credinoda.domain.model.RegistroControlLicencia;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.persistence.repository.LicenciaRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Servicio centralizado para la administración del ciclo de vida de licenciamiento comercial offline,
 * correspondencia con Hardware ID, control de expiración y motor Anti-Tampering de tiempo (RN-030 a RN-033, VP-025 a VP-029).
 * Desacoplado 100% de la capa de interfaz JavaFX.
 */
public class LicenciaService {

    /**
     * Margen de tolerancia de retraso admisible en el reloj del sistema operativo (20 minutos = 1200 segundos) (RN-032).
     */
    public static final long TOLERANCIA_RELOJ_SEGUNDOS = 20 * 60L;

    private final LicenciaRepository repository;
    private final LicenciaCryptoVerifier verifier;
    private final HardwareIdProvider hardwareIdProvider;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public LicenciaService(LicenciaRepository repository,
                           LicenciaCryptoVerifier verifier,
                           HardwareIdProvider hardwareIdProvider,
                           AuditoriaService auditoriaService,
                           Clock clock) {
        this.repository = Objects.requireNonNull(repository, "LicenciaRepository no puede ser nulo");
        this.verifier = Objects.requireNonNull(verifier, "LicenciaCryptoVerifier no puede ser nulo");
        this.hardwareIdProvider = Objects.requireNonNull(hardwareIdProvider, "HardwareIdProvider no puede ser nulo");
        this.auditoriaService = auditoriaService;
        this.clock = Objects.requireNonNull(clock, "El reloj no puede ser nulo");
    }

    public LicenciaService(LicenciaRepository repository,
                           LicenciaCryptoVerifier verifier,
                           HardwareIdProvider hardwareIdProvider,
                           AuditoriaService auditoriaService) {
        this(repository, verifier, hardwareIdProvider, auditoriaService, Clock.systemUTC());
    }

    public LicenciaService(LicenciaRepository repository,
                           HardwareIdProvider hardwareIdProvider,
                           AuditoriaService auditoriaService) {
        this(repository, new LicenciaCryptoVerifier(), hardwareIdProvider, auditoriaService, Clock.systemUTC());
    }

    public LicenciaService(LicenciaRepository repository, HardwareIdProvider hardwareIdProvider) {
        this(repository, new LicenciaCryptoVerifier(), hardwareIdProvider, null, Clock.systemUTC());
    }

    public LicenciaService(LicenciaRepository repository) {
        this(repository, new LicenciaCryptoVerifier(), new SystemHardwareIdProvider(), null, Clock.systemUTC());
    }

    /**
     * Obtiene el identificador de hardware del equipo actual.
     */
    public String obtenerHardwareIdActual() {
        return hardwareIdProvider.obtenerHardwareId();
    }

    /**
     * Valida integralmente el estado de la licencia y la consistencia del reloj del sistema (RN-030, RN-031, RN-032).
     *
     * @return ResultadoValidacionLicencia con el estado, datos y detalle explicativo.
     */
    public ResultadoValidacionLicencia validarEstadoActual() {
        String hwId = obtenerHardwareIdActual();
        long ahoraUtc = Instant.now(clock).getEpochSecond();

        Optional<RegistroControlLicencia> controlOpt;
        try {
            controlOpt = repository.obtenerControl();
        } catch (SQLException e) {
            return ResultadoValidacionLicencia.error(EstadoLicencia.FORMATO_INVALIDO, hwId,
                    "Error al consultar el repositorio de licencia: " + e.getMessage());
        }

        Optional<Long> propsUtcOpt = ConfiguracionLocalStore.cargarUltimoTimestampUtc();
        long maxHistoricoUtc;
        try {
            maxHistoricoUtc = repository.obtenerMaximoTimestampHistorico();
        } catch (SQLException e) {
            maxHistoricoUtc = 0L;
        }

        // 1. Verificación de integridad HMAC de registros existentes
        if (controlOpt.isPresent()) {
            RegistroControlLicencia r = controlOpt.get();
            if (r.getHmacIntegridad() != null && !r.getHmacIntegridad().isBlank()) {
                boolean hmacValido = verifier.verificarHmacTiempo(r.getHardwareId(), r.getUltimoTimestampUtc(), r.getLicenciaToken(), r.getHmacIntegridad());
                if (!hmacValido) {
                    auditarEvento(AccionAuditoria.BLOQUEO_RELOJ_MANIPULADO, "Registro de control adulterado en BD", ResultadoAuditoria.DENEGADO);
                    return ResultadoValidacionLicencia.relojManipulado(hwId,
                            "Se detectó alteración manual en el registro de seguridad de la base de datos.");
                }
            }
        }

        if (propsUtcOpt.isPresent()) {
            Optional<String> propsHmacOpt = ConfiguracionLocalStore.cargarHmacSeguridad();
            if (propsHmacOpt.isPresent()) {
                String tokenLocal = ConfiguracionLocalStore.cargarTokenLicencia().orElse("");
                boolean hmacPropsValido = verifier.verificarHmacTiempo(hwId, propsUtcOpt.get(), tokenLocal, propsHmacOpt.get());
                if (!hmacPropsValido) {
                    auditarEvento(AccionAuditoria.BLOQUEO_RELOJ_MANIPULADO, "Registro de seguridad adulterado en credinoda.properties", ResultadoAuditoria.DENEGADO);
                    return ResultadoValidacionLicencia.relojManipulado(hwId,
                            "Se detectó alteración manual en el ancla de seguridad del sistema.");
                }
            }
        }

        // 2. Cálculo de la marca máxima de tiempo conocida (RN-032)
        long maxVisto = 0L;
        if (controlOpt.isPresent()) {
            maxVisto = Math.max(maxVisto, controlOpt.get().getUltimoTimestampUtc());
        }
        if (propsUtcOpt.isPresent()) {
            maxVisto = Math.max(maxVisto, propsUtcOpt.get());
        }
        maxVisto = Math.max(maxVisto, maxHistoricoUtc);

        // 3. Evaluación de Retroceso de Reloj (Anti-Tampering)
        if (maxVisto > 0 && ahoraUtc < (maxVisto - TOLERANCIA_RELOJ_SEGUNDOS)) {
            auditarEvento(AccionAuditoria.BLOQUEO_RELOJ_MANIPULADO,
                    String.format("Reloj atrasado: actual=%d, maxVisto=%d", ahoraUtc, maxVisto), ResultadoAuditoria.DENEGADO);
            Instant fechaMinima = Instant.ofEpochSecond(maxVisto);
            return ResultadoValidacionLicencia.relojManipulado(hwId,
                    String.format("El reloj del equipo ha sido atrasado. Hora mínima requerida: %s UTC.", fechaMinima));
        }

        // 4. Obtención del token activo (SQLite con fallback a ancla local)
        String tokenActivo = null;
        if (controlOpt.isPresent() && controlOpt.get().getLicenciaToken() != null && !controlOpt.get().getLicenciaToken().isBlank()) {
            tokenActivo = controlOpt.get().getLicenciaToken().trim();
        } else {
            Optional<String> tokenProps = ConfiguracionLocalStore.cargarTokenLicencia();
            if (tokenProps.isPresent()) {
                tokenActivo = tokenProps.get().trim();
            }
        }

        if (tokenActivo == null || tokenActivo.isBlank()) {
            long nuevoTs = Math.max(ahoraUtc, maxVisto);
            actualizarTimestampMonotono(nuevoTs, "", null);
            return ResultadoValidacionLicencia.noRegistrada(hwId);
        }

        // 5. Verificación criptográfica Ed25519
        DatosLicencia datos;
        try {
            datos = verifier.verificarYParsearToken(tokenActivo);
        } catch (LicenciaException e) {
            return ResultadoValidacionLicencia.error(e.getEstado(), hwId, e.getMessage());
        }

        // 6. Correspondencia de Hardware ID (RN-031)
        if (!datos.getHardwareId().equalsIgnoreCase(hwId.trim())) {
            return ResultadoValidacionLicencia.error(
                    EstadoLicencia.HARDWARE_NO_COINCIDE,
                    hwId,
                    "La licencia registrada pertenece al equipo " + datos.getHardwareId() + " y no coincide con este equipo (" + hwId + ")."
            );
        }

        // 7. Evaluación de vigencia temporal
        LocalDate hoy = LocalDate.now(clock);
        long diasRestantes = ChronoUnit.DAYS.between(hoy, datos.getFechaExpiracion());
        if (hoy.isAfter(datos.getFechaExpiracion())) {
            return new ResultadoValidacionLicencia(
                    EstadoLicencia.EXPIRADA,
                    datos,
                    hwId,
                    diasRestantes,
                    "La licencia comercial expiró el " + datos.getFechaExpiracion() + "."
            );
        }

        // 8. Estado completamente VÁLIDO: persistencia y latido monótono
        long nuevoTs = Math.max(ahoraUtc, maxVisto);
        actualizarTimestampMonotono(nuevoTs, tokenActivo, datos);

        return new ResultadoValidacionLicencia(
                EstadoLicencia.VALIDA,
                datos,
                hwId,
                diasRestantes,
                "Licencia activa y vigente para " + datos.getTitular() + "."
        );
    }

    /**
     * Activa una licencia comercial mediante un token Base64 o string canónico (RN-030).
     */
    public ResultadoValidacionLicencia activarLicencia(String rawToken) {
        String hwId = obtenerHardwareIdActual();
        long ahoraUtc = Instant.now(clock).getEpochSecond();

        // 1. Evaluar si el reloj está atrasado antes de permitir activación
        long maxHistoricoUtc;
        try {
            maxHistoricoUtc = repository.obtenerMaximoTimestampHistorico();
        } catch (SQLException e) {
            maxHistoricoUtc = 0L;
        }
        Optional<Long> propsUtcOpt = ConfiguracionLocalStore.cargarUltimoTimestampUtc();
        long maxVisto = Math.max(propsUtcOpt.orElse(0L), maxHistoricoUtc);
        try {
            Optional<RegistroControlLicencia> controlOpt = repository.obtenerControl();
            if (controlOpt.isPresent()) {
                maxVisto = Math.max(maxVisto, controlOpt.get().getUltimoTimestampUtc());
            }
        } catch (SQLException ignored) {
        }

        if (maxVisto > 0 && ahoraUtc < (maxVisto - TOLERANCIA_RELOJ_SEGUNDOS)) {
            auditarEvento(AccionAuditoria.LICENCIA_RECHAZADA, "Intento de activación con reloj manipulado", ResultadoAuditoria.FALLO);
            return ResultadoValidacionLicencia.relojManipulado(hwId, "No es posible activar una licencia con el reloj del equipo atrasado.");
        }

        // 2. Verificar formato y firma Ed25519
        DatosLicencia datos;
        try {
            datos = verifier.verificarYParsearToken(rawToken);
        } catch (LicenciaException e) {
            auditarEvento(AccionAuditoria.LICENCIA_RECHAZADA, "Fallo de validación de firma: " + e.getMessage(), ResultadoAuditoria.FALLO);
            return ResultadoValidacionLicencia.error(e.getEstado(), hwId, e.getMessage());
        }

        // 3. Verificar Hardware ID (RN-031)
        if (!datos.getHardwareId().equalsIgnoreCase(hwId.trim())) {
            auditarEvento(AccionAuditoria.LICENCIA_RECHAZADA,
                    "Hardware ID no coincide: esperado=" + hwId + ", licencia=" + datos.getHardwareId(), ResultadoAuditoria.FALLO);
            return ResultadoValidacionLicencia.error(
                    EstadoLicencia.HARDWARE_NO_COINCIDE,
                    hwId,
                    "La licencia no corresponde al Hardware ID de este equipo (" + hwId + ")."
            );
        }

        // 4. Verificar vigencia
        LocalDate hoy = LocalDate.now(clock);
        long diasRestantes = ChronoUnit.DAYS.between(hoy, datos.getFechaExpiracion());
        if (hoy.isAfter(datos.getFechaExpiracion())) {
            auditarEvento(AccionAuditoria.LICENCIA_RECHAZADA, "Licencia expirada: " + datos.getFechaExpiracion(), ResultadoAuditoria.FALLO);
            return new ResultadoValidacionLicencia(
                    EstadoLicencia.EXPIRADA,
                    datos,
                    hwId,
                    diasRestantes,
                    "La licencia proporcionada ya ha expirado el " + datos.getFechaExpiracion() + "."
            );
        }

        // 5. Persistir en persistencia dual
        long nuevoTs = Math.max(ahoraUtc, maxVisto);
        String tokenLimpio = rawToken.trim();
        actualizarTimestampMonotono(nuevoTs, tokenLimpio, datos);

        auditarEvento(AccionAuditoria.ACTIVAR_LICENCIA,
                String.format("Licencia %s activada exitosamente para %s (expira: %s)", datos.getLicenciaId(), datos.getTitular(), datos.getFechaExpiracion()),
                ResultadoAuditoria.EXITO);

        return new ResultadoValidacionLicencia(
                EstadoLicencia.VALIDA,
                datos,
                hwId,
                diasRestantes,
                "Licencia activada exitosamente."
        );
    }

    /**
     * Activa una licencia cargada directamente desde un archivo local (.lic o .txt).
     */
    public ResultadoValidacionLicencia activarLicenciaDesdeArchivo(Path archivoLic) throws IOException {
        if (archivoLic == null || !Files.isRegularFile(archivoLic)) {
            return ResultadoValidacionLicencia.error(
                    EstadoLicencia.FORMATO_INVALIDO,
                    obtenerHardwareIdActual(),
                    "El archivo de licencia especificado no existe o no es accesible."
            );
        }
        String token = Files.readString(archivoLic, StandardCharsets.UTF_8);
        return activarLicencia(token);
    }

    /**
     * Registra un latido periódico de tiempo en la sesión activa para actualizar la marca de última ejecución.
     */
    public ResultadoValidacionLicencia registrarLatidoTiempo() {
        return validarEstadoActual();
    }

    private void actualizarTimestampMonotono(long timestampUtc, String token, DatosLicencia datos) {
        String hwId = obtenerHardwareIdActual();
        String tokenParaHmac = token != null ? token : "";
        String hmac = verifier.calcularHmacTiempo(hwId, timestampUtc, tokenParaHmac);

        // 1. Guardar en SQLite
        try {
            RegistroControlLicencia registro = new RegistroControlLicencia(
                    1,
                    tokenParaHmac,
                    hwId,
                    datos != null ? datos.getTitular() : "",
                    datos != null ? datos.getFechaEmision() : null,
                    datos != null ? datos.getFechaExpiracion() : null,
                    timestampUtc,
                    hmac
            );
            repository.guardarOActualizarControl(registro);
        } catch (SQLException e) {
            System.err.println("Advertencia al actualizar control_licencia en SQLite: " + e.getMessage());
        }

        // 2. Guardar en ConfiguracionLocalStore
        try {
            ConfiguracionLocalStore.guardarAnclaLicenciaTiempo(tokenParaHmac, timestampUtc, hmac);
        } catch (Exception e) {
            System.err.println("Advertencia al actualizar ancla en ConfiguracionLocalStore: " + e.getMessage());
        }
    }

    private void auditarEvento(AccionAuditoria accion, String detalle, ResultadoAuditoria resultado) {
        if (auditoriaService != null) {
            try {
                auditoriaService.registrarConUsuario(
                        "SISTEMA",
                        "SISTEMA",
                        null,
                        accion,
                        "LICENCIA",
                        obtenerHardwareIdActual(),
                        detalle,
                        resultado
                );
            } catch (Exception ignored) {
            }
        }
    }
}
