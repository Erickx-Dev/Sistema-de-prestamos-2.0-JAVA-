package com.credinoda.domain.dto;

import java.time.LocalDateTime;

/**
 * DTO inmutable con información de un archivo de copia de seguridad .db en el sistema.
 */
public record BackupInfo(
        String nombreArchivo,
        String rutaCompleta,
        long tamanoBytes,
        double tamanoKb,
        LocalDateTime fechaModificacion
) {
}
