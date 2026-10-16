package com.credinoda.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CuotaTest {

    @Test
    @DisplayName("RN-008: Cuota.etiquetaEstado genera etiquetas de mora preservando estados PENDIENTE y PARCIAL")
    void testEtiquetaEstadoConMora() {
        LocalDate vencimiento = LocalDate.now().minusDays(5);
        BigDecimal monto = new BigDecimal("11333.33");

        Cuota cuotaPendienteMora = new Cuota(1, 1, 1, vencimiento, monto, EstadoCuota.PENDIENTE, true);
        assertThat(cuotaPendienteMora.etiquetaEstado()).isEqualTo("PENDIENTE (EN MORA)");

        Cuota cuotaParcialMora = new Cuota(2, 1, 2, vencimiento, monto, EstadoCuota.PARCIAL, true);
        assertThat(cuotaParcialMora.etiquetaEstado()).isEqualTo("PARCIAL (EN MORA)");

        // Al pagarse una cuota, la mora se extingue y la etiqueta es PAGADA
        Cuota cuotaPagada = new Cuota(3, 1, 3, vencimiento, monto, EstadoCuota.PAGADA, false);
        assertThat(cuotaPagada.etiquetaEstado()).isEqualTo("PAGADA");

        // Si por inconsistencia tuviera enMora=true pero estado PAGADA, no muestra (EN MORA)
        Cuota cuotaPagadaConFlag = new Cuota(4, 1, 4, vencimiento, monto, EstadoCuota.PAGADA, true);
        assertThat(cuotaPagadaConFlag.etiquetaEstado()).isEqualTo("PAGADA");
    }

    @Test
    @DisplayName("Cuota.etiquetaEstado sin mora devuelve estado original")
    void testEtiquetaEstadoSinMora() {
        LocalDate vencimiento = LocalDate.now().plusDays(5);
        BigDecimal monto = new BigDecimal("11333.33");

        Cuota cuotaPendiente = new Cuota(1, 1, 1, vencimiento, monto, EstadoCuota.PENDIENTE, false);
        assertThat(cuotaPendiente.etiquetaEstado()).isEqualTo("PENDIENTE");

        Cuota cuotaParcial = new Cuota(2, 1, 2, vencimiento, monto, EstadoCuota.PARCIAL, false);
        assertThat(cuotaParcial.etiquetaEstado()).isEqualTo("PARCIAL");
    }
}
