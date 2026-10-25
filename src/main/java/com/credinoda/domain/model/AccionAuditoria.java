package com.credinoda.domain.model;

/**
 * Catálogo exhaustivo de acciones auditables en CrediNoda 2.0 (RN-027).
 */
public enum AccionAuditoria {
    LOGIN_EXITOSO("Inicio de sesión exitoso", "Autenticación correcta en el sistema"),
    LOGIN_FALLIDO("Intento de acceso fallido", "Credenciales inválidas proporcionadas"),
    BLOQUEO_USUARIO("Bloqueo temporal de usuario", "Bloqueo por reiterados intentos fallidos"),
    LOGOUT("Cierre de sesión", "Cierre voluntario de sesión de usuario"),
    CAMBIO_CONTRASENA("Cambio de contraseña", "Actualización de clave de acceso"),

    CREAR_CLIENTE("Creación de cliente", "Registro de nuevo prestatario"),
    ACTUALIZAR_CLIENTE("Actualización de cliente", "Modificación de datos de prestatario"),

    CREAR_CREDITO("Otorgamiento de crédito", "Simulación y desembolso de préstamo"),
    SINCRONIZAR_MORA("Sincronización de mora", "Actualización programada o automática de cuotas vencidas"),

    REGISTRAR_PAGO("Registro de pago", "Cobranza de abono o liquidación de cuota"),

    GENERAR_PDF("Generación de comprobante PDF", "Emisión o reimpresión de recibo con código QR"),

    CREAR_BACKUP("Creación de respaldo", "Generación de copia de seguridad de la base de datos"),
    RESTAURAR_BACKUP("Restauración de respaldo", "Restauración de datos de negocio desde copia"),

    CREAR_USUARIO("Creación de usuario", "Alta de nuevo usuario en el sistema"),
    ACTUALIZAR_USUARIO("Actualización de usuario", "Modificación de datos o rol de usuario"),
    ACTIVAR_USUARIO("Activación de usuario", "Habilitación de acceso para un usuario"),
    DESACTIVAR_USUARIO("Desactivación de usuario", "Baja lógica de usuario"),

    ACTIVAR_LICENCIA("Activación de licencia", "Activación o renovación exitosa de licencia offline"),
    LICENCIA_RECHAZADA("Licencia rechazada", "Intento fallido de activación o validación de licencia"),
    BLOQUEO_RELOJ_MANIPULADO("Bloqueo por manipulación de reloj", "Detección de retroceso no autorizado en el reloj del sistema"),

    ACCESO_DENEGADO("Acceso denegado", "Intento de ejecución de acción sin permisos requeridos");

    private final String nombreVisible;
    private final String descripcion;

    AccionAuditoria(String nombreVisible, String descripcion) {
        this.nombreVisible = nombreVisible;
        this.descripcion = descripcion;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
