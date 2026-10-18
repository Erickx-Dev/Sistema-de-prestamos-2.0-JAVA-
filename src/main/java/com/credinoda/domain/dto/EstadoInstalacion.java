package com.credinoda.domain.dto;

/**
 * Estados del ciclo de instalación y configuración de CrediNoda 2.0 (CU-016, CU-017, RN-019, RN-021).
 */
public enum EstadoInstalacion {
    /**
     * No existe archivo de configuración 'credinoda.properties' en el directorio de usuario.
     * Requiere lanzar el Asistente de Configuración Inicial (Setup Wizard).
     */
    NO_CONFIGURADO,

    /**
     * El archivo de configuración existe pero la base de datos no está disponible en la ruta registrada
     * (disco desconectado o carpeta movida). No debe recrearse en silencio; requiere diálogo de recuperación.
     */
    UBICACION_NO_DISPONIBLE,

    /**
     * La base de datos existe y es accesible, pero no contiene ningún usuario Administrador activo.
     * (Preparado para la Fase 6.3 de Seguridad y Autenticación).
     */
    SIN_ADMINISTRADOR,

    /**
     * El sistema está completamente configurado y listo para iniciar la aplicación.
     */
    LISTO
}
