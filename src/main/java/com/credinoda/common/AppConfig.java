package com.credinoda.common;

import java.math.BigDecimal;

/**
 * Constantes financieras globales e inmutables del sistema CrediNoda 2.0.
 * Las rutas operativas son dinámicas y se gestionan a través de {@link RutasSistema} y {@link ConfiguracionLocalStore}.
 */
public final class AppConfig {

    private AppConfig() {
        // Clase de constantes, no instanciable
    }

    // Parámetros financieros por defecto (CU-005, CU-006, RN-004, RN-005)
    public static final BigDecimal DEFAULT_MONTHLY_RATE = new BigDecimal("0.12");
    public static final int WEEKS_PER_MONTH = 4;
    public static final int FORTNIGHTS_PER_MONTH = 2;
}
