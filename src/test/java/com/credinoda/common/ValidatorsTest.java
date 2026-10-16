package com.credinoda.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorsTest {

    @DisplayName("VP-013: Parseo de montos en formatos válidos (33 subcasos exactos)")
    @ParameterizedTest(name = "[{index}] texto=''{0}'' -> esperado={1}")
    @CsvSource(delimiter = ';', value = {
            "11333.33; 11333.33",
            "11333,33; 11333.33",
            "11.333,33; 11333.33",
            "11,333.33; 11333.33",
            "11.333.33; 11333.33",
            "11,333,33; 11333.33",
            "$11.333,33; 11333.33",
            "$ 11333.33; 11333.33",
            "11333; 11333",
            "500; 500",
            "500.00; 500.00",
            "500,00; 500.00",
            "500.50; 500.50",
            "500,50; 500.50",
            "500.5; 500.5",
            "500,5; 500.5",
            "1.000.000; 1000000",
            "1,000,000; 1000000",
            "1.000.000,50; 1000000.50",
            "1,000,000.50; 1000000.50",
            "10.000; 10000",
            "10,000; 10000",
            "100.000; 100000",
            "1.000; 1000",
            "1,000; 1000",
            "1.500; 1500",
            "1.50; 1.50",
            "1.5; 1.5",
            ".50; 0.50",
            ",50; 0.50",
            "0.5; 0.5",
            "0,5; 0.5",
            "11333.330; 11333.330"
    })
    void testParsearMontoFormatosValidos(String texto, String esperadoStr) {
        BigDecimal esperado = new BigDecimal(esperadoStr.trim());
        BigDecimal resultado = Validators.parsearMonto(texto);

        assertThat(resultado)
                .as("El resultado parseado de '%s' debe coincidir exactamente con %s", texto, esperado)
                .isEqualByComparingTo(esperado);

        Validators.ResultadoValidacion validacion = Validators.validarMonto(texto);
        assertThat(validacion.valido())
                .as("Validación de '%s' debe ser válida pero falló con mensaje: %s", texto, validacion.mensaje())
                .isTrue();
    }

    @DisplayName("VP-013: Validación de montos inválidos (11 subcasos exactos)")
    @ParameterizedTest(name = "[{index}] texto=''{0}'' -> mensaje=''{1}''")
    @CsvSource(delimiter = ';', value = {
            "''; 'Ingresa un monto.'",
            "'   '; 'Ingresa un monto.'",
            "'0'; 'El monto debe ser mayor a cero.'",
            "'0.00'; 'El monto debe ser mayor a cero.'",
            "'-100'; 'El monto debe ser mayor a cero.'",
            "'-500.25'; 'El monto debe ser mayor a cero.'",
            "'abc'; 'Ingresa un monto numérico válido.'",
            "'$'; 'Ingresa un monto numérico válido.'",
            "'12.34.56.78'; 'Ingresa un monto numérico válido.'",
            "'12..34'; 'Ingresa un monto numérico válido.'",
            "'1.23.456'; 'Ingresa un monto numérico válido.'"
    })
    void testValidarMontoInvalidos(String texto, String msgEsperado) {
        Validators.ResultadoValidacion res = Validators.validarMonto(texto);
        assertThat(res.valido())
                .as("El texto '%s' debería ser marcado como inválido", texto)
                .isFalse();
        assertThat(res.mensaje())
                .as("Mensaje de error para '%s' debe coincidir", texto)
                .isEqualTo(msgEsperado);
    }

    @Test
    @DisplayName("Validación de texto no vacío")
    void testValidarTextoNoVacio() {
        Validators.ResultadoValidacion valido = Validators.validarTextoNoVacio("Juan", "Nombre");
        assertThat(valido.valido()).isTrue();

        Validators.ResultadoValidacion invalido = Validators.validarTextoNoVacio("   ", "Nombre");
        assertThat(invalido.valido()).isFalse();
        assertThat(invalido.mensaje()).contains("obligatorio");
    }

    @Test
    @DisplayName("Validación de entero positivo")
    void testValidarEnteroPositivo() {
        Validators.ResultadoValidacion valido = Validators.validarEnteroPositivo("5", "Plazo");
        assertThat(valido.valido()).isTrue();

        Validators.ResultadoValidacion cero = Validators.validarEnteroPositivo("0", "Plazo");
        assertThat(cero.valido()).isFalse();
        assertThat(cero.mensaje()).contains("mayor a cero");

        Validators.ResultadoValidacion noNumerico = Validators.validarEnteroPositivo("abc", "Plazo");
        assertThat(noNumerico.valido()).isFalse();
        assertThat(noNumerico.mensaje()).contains("número entero");
    }
}
