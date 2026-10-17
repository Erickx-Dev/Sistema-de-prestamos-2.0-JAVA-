package com.credinoda.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utilidades de formateo para presentación contable de moneda, fechas y porcentajes.
 * Desacoplado al 100% de cualquier framework o librería gráfica de UI.
 */
public final class Formatters {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private Formatters() {
        // Clase utilitaria, no instanciable
    }

    /**
     * Formatea un valor monetario según la regla RN-015:
     * - Si es null o cero entero: "$0".
     * - Si no tiene parte decimal fraccionaria: sin decimales y con puntos de miles (ej: "$1.000.000").
     * - Si tiene decimales no nulos: 2 decimales con coma decimal y puntos de miles (ej: "$113.333,33").
     */
    public static String formatoMoneda(BigDecimal valor) {
        if (valor == null) {
            return "$0";
        }

        // Configuración de símbolos: punto para miles, coma para decimales
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');

        // Verificar si es un número entero exacto
        BigDecimal stripped = valor.stripTrailingZeros();
        boolean esEntero = stripped.scale() <= 0;

        if (esEntero) {
            DecimalFormat df = new DecimalFormat("$#,##0", symbols);
            return df.format(valor);
        } else {
            BigDecimal rounded = valor.setScale(2, RoundingMode.HALF_UP);
            DecimalFormat df = new DecimalFormat("$#,##0.00", symbols);
            return df.format(rounded);
        }
    }

    /**
     * Formatea una fecha en formato estándar latinoamericano: dd/MM/yyyy.
     */
    public static String formatoFecha(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return fecha.format(DATE_FORMATTER);
    }

    /**
     * Formatea una tasa mensual o porcentaje:
     * Ejemplo: 0.12 -> "12%".
     */
    public static String formatoPorcentaje(BigDecimal tasa) {
        if (tasa == null) {
            return "0%";
        }
        BigDecimal pct = tasa.multiply(BigDecimal.valueOf(100)).stripTrailingZeros();
        if (pct.scale() <= 0) {
            return pct.toPlainString() + "%";
        }
        // Si tiene decimales, redondear a 1 o 2 decimales
        return pct.setScale(1, RoundingMode.HALF_UP).toPlainString().replace('.', ',') + "%";
    }

    /**
     * Formatea un valor monetario de manera compacta para ejes y etiquetas reducidas:
     * - null o 0 -> "$0"
     * - >= 1.000.000 -> "$1M", "$1,5M"
     * - >= 1.000 -> "$500k", "$10k"
     * - < 1.000 -> "$500"
     */
    public static String formatoMonedaCompacta(Number valor) {
        if (valor == null) {
            return "$0";
        }
        double val = valor.doubleValue();
        double abs = Math.abs(val);
        if (abs < 0.0001) {
            return "$0";
        }
        String sign = val < 0 ? "-" : "";
        if (abs >= 1_000_000) {
            double m = abs / 1_000_000.0;
            BigDecimal bd = BigDecimal.valueOf(m).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            return sign + "$" + bd.toPlainString().replace('.', ',') + "M";
        }
        if (abs >= 1_000) {
            double k = abs / 1_000.0;
            BigDecimal bd = BigDecimal.valueOf(k).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
            return sign + "$" + bd.toPlainString().replace('.', ',') + "k";
        }
        return sign + "$" + Math.round(abs);
    }
}

