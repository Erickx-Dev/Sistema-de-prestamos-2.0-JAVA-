package com.credinoda.service;

import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.exception.PdfServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas Unitarias de PdfService (CU-011, RN-012, VP-009)")
class PdfServiceTest {

    @TempDir
    Path tempDir;

    private PdfService pdfService;

    @BeforeEach
    void setUp() {
        pdfService = new PdfService(tempDir);
    }

    @Test
    @DisplayName("sanitizarNombreArchivo() limpia caracteres prohibidos y reemplaza espacios por guiones")
    void testSanitizarNombreArchivo() {
        assertThat(PdfService.sanitizarNombreArchivo("Juan Perez")).isEqualTo("Juan_Perez");
        assertThat(PdfService.sanitizarNombreArchivo("María/Ana*López?")).isEqualTo("MaríaAnaLópez");
        assertThat(PdfService.sanitizarNombreArchivo("Carlos : \"El Rápido\" <VIP>|")).isEqualTo("Carlos_El_Rápido_VIP");
        assertThat(PdfService.sanitizarNombreArchivo("   ")).isEqualTo("cliente");
        assertThat(PdfService.sanitizarNombreArchivo(null)).isEqualTo("cliente");
    }

    @Test
    @DisplayName("generarCodigoQr() produce una imagen PNG válida en memoria")
    void testGenerarCodigoQr() {
        String texto = "CREDINODA | REC-00001\nCliente: Carlos Test";
        byte[] qrPng = pdfService.generarCodigoQr(texto, 100, 100);

        assertThat(qrPng).isNotNull();
        assertThat(qrPng.length).isGreaterThan(50);
        // Firma mágica PNG: 0x89 0x50 0x4E 0x47 (\x89PNG)
        assertThat(qrPng[0]).isEqualTo((byte) 0x89);
        assertThat(qrPng[1]).isEqualTo((byte) 0x50);
        assertThat(qrPng[2]).isEqualTo((byte) 0x4E);
        assertThat(qrPng[3]).isEqualTo((byte) 0x47);
    }

    @Test
    @DisplayName("VP-009 y CU-011: Genera recibo de pago PDF tamaño carta con cabecera %PDF- y código QR")
    void testGenerarComprobantePagoExitoso() throws IOException {
        // Datos correspondientes a VP-009
        DatosComprobante datos = new DatosComprobante(
                1,
                "REC-00001",
                LocalDate.of(2026, 10, 16),
                new BigDecimal("113333.33"),
                10,
                "Erickson Sojo",
                "123456789",
                "3001234567",
                100,
                new BigDecimal("1000000.00"),
                new BigDecimal("1360000.00"),
                3,
                "SEMANAL",
                LocalDate.of(2026, 10, 1),
                1001,
                2,
                12,
                LocalDate.of(2026, 10, 15),
                new BigDecimal("113333.33"),
                new BigDecimal("113333.33"),
                new BigDecimal("113333.33"),
                BigDecimal.ZERO,
                "PAGADA",
                new BigDecimal("267333.32"),
                new BigDecimal("1092666.68"),
                new BigDecimal("19.7") // VP-009: 19.7% de amortización
        );

        Path pdfGenerado = pdfService.generarComprobantePago(datos, null);

        assertThat(pdfGenerado).isNotNull();
        assertThat(Files.exists(pdfGenerado)).isTrue();
        assertThat(Files.isRegularFile(pdfGenerado)).isTrue();

        // El nombre debe seguir el patrón sanitizado
        assertThat(pdfGenerado.getFileName().toString())
                .isEqualTo("Recibo_REC-00001_Erickson_Sojo_20261016.pdf");

        // Tamaño mayor a 1 KB
        long fileSize = Files.size(pdfGenerado);
        assertThat(fileSize).isGreaterThan(1024L);

        // Cabecera mágica de PDF (%PDF-)
        byte[] header = new byte[5];
        try (InputStream is = Files.newInputStream(pdfGenerado)) {
            int read = is.read(header);
            assertThat(read).isEqualTo(5);
            assertThat(new String(header)).isEqualTo("%PDF-");
        }
    }

    @Test
    @DisplayName("generarComprobantePago() con destino personalizado escribe en la ruta solicitada")
    void testGenerarComprobantePagoDestinoPersonalizado() throws IOException {
        Path destinoCustom = tempDir.resolve("subcarpeta").resolve("comprobante_custom.pdf");

        DatosComprobante datos = new DatosComprobante(
                24,
                "REC-00024",
                LocalDate.of(2026, 10, 16),
                new BigDecimal("50000.00"),
                1,
                "Ana Maria",
                "987654321",
                null,
                5,
                new BigDecimal("500000.00"),
                new BigDecimal("550000.00"),
                1,
                "QUINCENAL",
                LocalDate.of(2026, 10, 1),
                501,
                1,
                2,
                LocalDate.of(2026, 10, 16),
                new BigDecimal("275000.00"),
                new BigDecimal("50000.00"),
                new BigDecimal("275000.00"),
                new BigDecimal("225000.00"),
                "PARCIAL",
                new BigDecimal("50000.00"),
                new BigDecimal("500000.00"),
                new BigDecimal("9.1")
        );

        Path resultado = pdfService.generarComprobantePago(datos, destinoCustom);

        assertThat(resultado).isEqualTo(destinoCustom);
        assertThat(Files.exists(destinoCustom)).isTrue();
        assertThat(Files.size(destinoCustom)).isGreaterThan(1024L);
    }

    @Test
    @DisplayName("generarComprobantePago() con datos nulos lanza PdfServiceException")
    void testGenerarComprobanteDatosNulos() {
        assertThatThrownBy(() -> pdfService.generarComprobantePago(null, null))
                .isInstanceOf(PdfServiceException.class)
                .hasMessageContaining("Los datos del comprobante no pueden ser nulos.");
    }
}
