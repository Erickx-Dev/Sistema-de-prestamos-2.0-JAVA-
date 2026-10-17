package com.credinoda.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class FormattersTest {

    @Test
    @DisplayName("VP-014: Formateo de moneda exacto con puntos de miles y coma decimal")
    void testFormatoMoneda() {
        assertThat(Formatters.formatoMoneda(new BigDecimal("1000000")))
                .isEqualTo("$1.000.000");

        assertThat(Formatters.formatoMoneda(new BigDecimal("113333.33")))
                .isEqualTo("$113.333,33");

        assertThat(Formatters.formatoMoneda(new BigDecimal("0")))
                .isEqualTo("$0");

        assertThat(Formatters.formatoMoneda(null))
                .isEqualTo("$0");

        assertThat(Formatters.formatoMoneda(new BigDecimal("500.5")))
                .isEqualTo("$500,50");

        assertThat(Formatters.formatoMoneda(new BigDecimal("11333.37")))
                .isEqualTo("$11.333,37");
    }

    @Test
    @DisplayName("Formateo de fechas en formato dd/MM/yyyy")
    void testFormatoFecha() {
        LocalDate fecha = LocalDate.of(2026, 10, 16);
        assertThat(Formatters.formatoFecha(fecha)).isEqualTo("16/10/2026");
        assertThat(Formatters.formatoFecha(null)).isEmpty();
    }

    @Test
    @DisplayName("Formateo de porcentajes")
    void testFormatoPorcentaje() {
        assertThat(Formatters.formatoPorcentaje(new BigDecimal("0.12"))).isEqualTo("12%");
        assertThat(Formatters.formatoPorcentaje(new BigDecimal("0.10"))).isEqualTo("10%");
        assertThat(Formatters.formatoPorcentaje(null)).isEqualTo("0%");
    }

    @Test
    @DisplayName("Formateo de moneda compacta para gráficos y ejes")
    void testFormatoMonedaCompacta() {
        assertThat(Formatters.formatoMonedaCompacta(null)).isEqualTo("$0");
        assertThat(Formatters.formatoMonedaCompacta(0)).isEqualTo("$0");
        assertThat(Formatters.formatoMonedaCompacta(0.0)).isEqualTo("$0");
        assertThat(Formatters.formatoMonedaCompacta(500)).isEqualTo("$500");
        assertThat(Formatters.formatoMonedaCompacta(1000)).isEqualTo("$1k");
        assertThat(Formatters.formatoMonedaCompacta(10000)).isEqualTo("$10k");
        assertThat(Formatters.formatoMonedaCompacta(500000)).isEqualTo("$500k");
        assertThat(Formatters.formatoMonedaCompacta(1000000)).isEqualTo("$1M");
        assertThat(Formatters.formatoMonedaCompacta(1500000)).isEqualTo("$1,5M");
        assertThat(Formatters.formatoMonedaCompacta(2000000)).isEqualTo("$2M");
        assertThat(Formatters.formatoMonedaCompacta(-500000)).isEqualTo("-$500k");
    }
}

