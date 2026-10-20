package com.credinoda.service;

import com.credinoda.common.ConfiguracionLocalStore;
import com.credinoda.common.Formatters;
import com.credinoda.common.RutasSistema;
import com.credinoda.domain.dto.DatosComprobante;
import com.credinoda.domain.exception.PdfServiceException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Servicio para la maquetación y generación de comprobantes de pago en PDF tamaño carta con código QR (CU-011, RN-012, VP-009, RN-027).
 * Utiliza OpenPDF para maquetar el documento en 5 secciones y ZXing para incrustar la matriz QR de validación digital en memoria.
 */
public class PdfService {

    private static final DateTimeFormatter BASIC_ISO = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern CARACTERES_PROHIBIDOS = Pattern.compile("[\\\\/*?:\"<>|]");
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");

    // Paleta de colores corporativos CrediNoda
    private static final Color COLOR_PRIMARIO_OSCURO = new Color(0x0F, 0x17, 0x2A); // #0F172A
    private static final Color COLOR_SECUNDARIO_GRIS = new Color(0x47, 0x55, 0x69); // #475569
    private static final Color COLOR_BORDE = new Color(0xCB, 0xD5, 0xE1);            // #CBD5E1
    private static final Color COLOR_FONDO_TABLA = new Color(0xF8, 0xFA, 0xFC);      // #F8FAFC
    private static final Color COLOR_RESALTADO_AZUL = new Color(0xEF, 0xF6, 0xFF);   // #EFF6FF
    private static final Color COLOR_ALERTA_ROJO = new Color(0xB9, 0x1C, 0x1C);      // #B91C1C
    private static final Color COLOR_ENCABEZADO_TABLA = new Color(0x1E, 0x29, 0x3B); // #1E293B

    // Fuentes tipográficas
    private static final Font FONT_HEADER_TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, COLOR_PRIMARIO_OSCURO);
    private static final Font FONT_HEADER_SUBTITULO = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_SECUNDARIO_GRIS);
    private static final Font FONT_RECIBO_BADGE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, COLOR_PRIMARIO_OSCURO);
    private static final Font FONT_SECCION_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
    private static final Font FONT_CELDA_LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, COLOR_SECUNDARIO_GRIS);
    private static final Font FONT_CELDA_VALOR = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, COLOR_PRIMARIO_OSCURO);
    private static final Font FONT_CELDA_DESTACADA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_PRIMARIO_OSCURO);
    private static final Font FONT_CELDA_ROJA = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, COLOR_ALERTA_ROJO);
    private static final Font FONT_LEGAL = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, COLOR_SECUNDARIO_GRIS);

    private final Path defaultOutputDir;
    private final AuditoriaService auditoriaService;

    public PdfService() {
        this((AuditoriaService) null);
    }

    public PdfService(AuditoriaService auditoriaService) {
        this(ConfiguracionLocalStore.cargarRutas()
                .map(RutasSistema::comprobantesDir)
                .orElseGet(() -> Path.of(System.getProperty("user.dir"), "comprobantes")), auditoriaService);
    }

    public PdfService(RutasSistema rutas) {
        this(rutas, null);
    }

    public PdfService(RutasSistema rutas, AuditoriaService auditoriaService) {
        this(Objects.requireNonNull(rutas, "RutasSistema no puede ser nulo.").comprobantesDir(), auditoriaService);
    }

    public PdfService(Path defaultOutputDir) {
        this(defaultOutputDir, null);
    }

    public PdfService(Path defaultOutputDir, AuditoriaService auditoriaService) {
        this.defaultOutputDir = Objects.requireNonNull(defaultOutputDir, "Directorio de salida no puede ser nulo.");
        this.auditoriaService = auditoriaService;
        try {
            Files.createDirectories(this.defaultOutputDir);
        } catch (IOException e) {
            System.err.println("Advertencia al crear directorio de salida de comprobantes: " + e.getMessage());
        }
    }

    /**
     * Sanitiza una cadena para su uso seguro como parte de un nombre de archivo (RN-012).
     *
     * @param nombre Cadena original (ej. nombre del cliente).
     * @return Cadena sanitizada sin caracteres prohibidos y con espacios convertidos a guión bajo.
     */
    public static String sanitizarNombreArchivo(String nombre) {
        if (nombre == null) {
            return "cliente";
        }
        String sinProhibidos = CARACTERES_PROHIBIDOS.matcher(nombre).replaceAll("");
        String conGuiones = ESPACIOS_MULTIPLES.matcher(sinProhibidos.trim()).replaceAll("_");
        return conGuiones.isEmpty() ? "cliente" : conGuiones;
    }

    /**
     * Genera un comprobante de pago en formato PDF a partir de los datos consolidados (CU-011, RN-012, VP-009).
     *
     * @param datos      Datos consolidados del pago, crédito, cuota y cliente.
     * @param outputPath Ruta de destino específica (opcional; si es nula, se genera en defaultOutputDir).
     * @return Path absoluto al archivo PDF generado.
     */
    public Path generarComprobantePago(DatosComprobante datos, Path outputPath) {
        if (datos == null) {
            throw new PdfServiceException("Los datos del comprobante no pueden ser nulos.");
        }

        Path destino;
        if (outputPath == null) {
            String slug = sanitizarNombreArchivo(datos.clienteNombre());
            String fechaStr = datos.fechaPago() != null ? datos.fechaPago().format(BASIC_ISO) : "FECHA";
            String nombreArchivo = String.format("Recibo_%s_%s_%s.pdf", datos.numeroRecibo(), slug, fechaStr);
            destino = defaultOutputDir.resolve(nombreArchivo);
        } else {
            destino = outputPath;
        }

        try {
            if (destino.getParent() != null) {
                Files.createDirectories(destino.getParent());
            }

            Document document = new Document(PageSize.LETTER, 36, 36, 36, 36);
            try (OutputStream os = Files.newOutputStream(destino)) {
                PdfWriter.getInstance(document, os);
                document.open();

                // 1. Encabezado institucional
                document.add(crearEncabezadoInstitucional(datos));
                document.add(crearEspaciador(10));

                // 2. Tablas paralelas de datos del cliente y datos del crédito
                document.add(crearTablasParalelas(datos));
                document.add(crearEspaciador(10));

                // 3. Tabla de detalle del abono
                document.add(crearTablaDetalleAbono(datos));
                document.add(crearEspaciador(10));

                // 4. Tabla de balance post-pago y avance de amortización (VP-009)
                document.add(crearTablaBalancePostPago(datos));
                document.add(crearEspaciador(12));

                // 5. Bloque de firmas, código QR incrustado y nota legal
                document.add(crearBloqueFirmasYQr(datos));

                document.close();
            }

            if (auditoriaService != null) {
                auditoriaService.registrar(
                        AccionAuditoria.GENERAR_PDF,
                        "PAGOS",
                        datos.numeroRecibo(),
                        "Recibo: " + datos.numeroRecibo() + ", Cliente: " + datos.clienteNombre() + ", Archivo: " + destino.getFileName(),
                        ResultadoAuditoria.EXITO
                );
            }

            return destino;

        } catch (DocumentException | IOException e) {
            throw new PdfServiceException("Error al generar el comprobante PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Genera una imagen PNG con la matriz del código QR en memoria utilizando ZXing (RN-012).
     *
     * @param contenido Contenido textual a codificar en el QR.
     * @param width     Ancho en píxeles.
     * @param height    Alto en píxeles.
     * @return Arreglo de bytes en formato PNG con la imagen del QR.
     */
    public byte[] generarCodigoQr(String contenido, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(contenido, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return pngOutputStream.toByteArray();
        } catch (Exception e) {
            System.err.println("Advertencia al generar matriz QR: " + e.getMessage());
            return null;
        }
    }

    // =========================================================================
    // Métodos auxiliares de maquetación OpenPDF
    // =========================================================================

    private PdfPTable crearEncabezadoInstitucional(DatosComprobante datos) {
        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{65, 35});

        // Columna izquierda: Identidad comercial
        PdfPCell left = new PdfPCell();
        left.setBorder(PdfPCell.NO_BORDER);
        left.addElement(new Paragraph("CREDINODA", FONT_HEADER_TITULO));
        left.addElement(new Paragraph("SISTEMA INTEGRAL DE GESTIÓN CREDITICIA", FONT_HEADER_SUBTITULO));
        left.addElement(new Paragraph("NIT / C.C. 123456789-0 | Línea de Cobranza: (+57) 300 000 0000", FONT_HEADER_SUBTITULO));
        header.addCell(left);

        // Columna derecha: Badge del comprobante
        PdfPCell right = new PdfPCell();
        right.setBorder(PdfPCell.BOX);
        right.setBorderColor(COLOR_BORDE);
        right.setBackgroundColor(COLOR_FONDO_TABLA);
        right.setPadding(6);
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph pComp = new Paragraph("COMPROBANTE DE PAGO", FONT_CELDA_LABEL);
        pComp.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pComp);

        Paragraph pNum = new Paragraph(datos.numeroRecibo(), FONT_RECIBO_BADGE);
        pNum.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pNum);

        String fechaEmision = datos.fechaPago() != null ? Formatters.formatoFecha(datos.fechaPago()) : "";
        Paragraph pFecha = new Paragraph("Fecha: " + fechaEmision, FONT_CELDA_VALOR);
        pFecha.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(pFecha);

        header.addCell(right);
        return header;
    }

    private PdfPTable crearTablasParalelas(DatosComprobante datos) {
        PdfPTable container = new PdfPTable(2);
        container.setWidthPercentage(100);
        container.setWidths(new float[]{50, 50});

        // Subtabla Izquierda: Datos del Titular
        PdfPTable cliTable = new PdfPTable(2);
        cliTable.setWidthPercentage(100);
        cliTable.setWidths(new float[]{40, 60});

        PdfPCell cliHeader = new PdfPCell(new Phrase("DATOS DEL TITULAR / CLIENTE", FONT_SECCION_HEADER));
        cliHeader.setColspan(2);
        cliHeader.setBackgroundColor(COLOR_ENCABEZADO_TABLA);
        cliHeader.setPadding(4);
        cliTable.addCell(cliHeader);

        agregarFilaKV(cliTable, "Nombre Completo:", datos.clienteNombre());
        agregarFilaKV(cliTable, "Documento:", datos.clienteDocumento());
        agregarFilaKV(cliTable, "Teléfono:", datos.clienteTelefono() != null ? datos.clienteTelefono() : "N/A");
        agregarFilaKV(cliTable, "ID Cliente:", String.valueOf(datos.clienteId()));

        PdfPCell cellLeft = new PdfPCell(cliTable);
        cellLeft.setBorder(PdfPCell.NO_BORDER);
        cellLeft.setPaddingRight(5);
        container.addCell(cellLeft);

        // Subtabla Derecha: Datos del Crédito
        PdfPTable credTable = new PdfPTable(2);
        credTable.setWidthPercentage(100);
        credTable.setWidths(new float[]{40, 60});

        PdfPCell credHeader = new PdfPCell(new Phrase("DATOS DEL CRÉDITO", FONT_SECCION_HEADER));
        credHeader.setColspan(2);
        credHeader.setBackgroundColor(COLOR_ENCABEZADO_TABLA);
        credHeader.setPadding(4);
        credTable.addCell(credHeader);

        agregarFilaKV(credTable, "Crédito ID:", "#" + datos.creditoId());
        agregarFilaKV(credTable, "Capital Prestado:", Formatters.formatoMoneda(datos.creditoCapital()));
        agregarFilaKV(credTable, "Total a Pagar:", Formatters.formatoMoneda(datos.creditoTotalPagar()));
        String frecuenciaPlazo = String.format("%d meses (%s)", datos.creditoPlazoMeses(), datos.creditoFrecuencia());
        agregarFilaKV(credTable, "Modalidad / Plazo:", frecuenciaPlazo);

        PdfPCell cellRight = new PdfPCell(credTable);
        cellRight.setBorder(PdfPCell.NO_BORDER);
        cellRight.setPaddingLeft(5);
        container.addCell(cellRight);

        return container;
    }

    private PdfPTable crearTablaDetalleAbono(DatosComprobante datos) {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{28, 18, 18, 18, 18});

        PdfPCell header = new PdfPCell(new Phrase("DETALLE DEL ABONO APLICADO", FONT_SECCION_HEADER));
        header.setColspan(5);
        header.setBackgroundColor(COLOR_ENCABEZADO_TABLA);
        header.setPadding(4);
        table.addCell(header);

        // Cabeceras de columnas
        agregarCeldaHeader(table, "Concepto de Cobro");
        agregarCeldaHeader(table, "Fecha Venc.");
        agregarCeldaHeader(table, "Valor Cuota");
        agregarCeldaHeader(table, "Monto Abonado");
        agregarCeldaHeader(table, "Estado Cuota");

        // Fila de datos
        String concepto = String.format("Cuota %d de %d", datos.numeroCuota(), datos.totalCuotas());
        agregarCeldaDato(table, concepto, Element.ALIGN_LEFT, null);
        agregarCeldaDato(table, Formatters.formatoFecha(datos.cuotaFechaVencimiento()), Element.ALIGN_CENTER, null);
        agregarCeldaDato(table, Formatters.formatoMoneda(datos.cuotaMonto()), Element.ALIGN_RIGHT, null);
        agregarCeldaDato(table, Formatters.formatoMoneda(datos.pagoMonto()), Element.ALIGN_RIGHT, COLOR_RESALTADO_AZUL);
        agregarCeldaDato(table, datos.cuotaEstado(), Element.ALIGN_CENTER, null);

        return table;
    }

    private PdfPTable crearTablaBalancePostPago(DatosComprobante datos) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25, 25, 25, 25});

        PdfPCell header = new PdfPCell(new Phrase("BALANCE FINANCIERO DE LA OBLIGACIÓN POST-PAGO", FONT_SECCION_HEADER));
        header.setColspan(4);
        header.setBackgroundColor(COLOR_ENCABEZADO_TABLA);
        header.setPadding(4);
        table.addCell(header);

        // Fila 1: Total acordado vs Recaudado acumulado
        agregarCeldaBalance(table, "Total Acordado a Pagar:", Formatters.formatoMoneda(datos.creditoTotalPagar()), FONT_CELDA_DESTACADA);
        agregarCeldaBalance(table, "Total Recaudado Acumulado:", Formatters.formatoMoneda(datos.totalPagadoCredito()), FONT_CELDA_DESTACADA);

        // Fila 2: Saldo pendiente vs Avance amortización
        agregarCeldaBalance(table, "Saldo Pendiente Total:", Formatters.formatoMoneda(datos.saldoCreditoRestante()), FONT_CELDA_ROJA);
        String pctStr = datos.porcentajePagadoCredito() != null ? datos.porcentajePagadoCredito().toPlainString() + "%" : "0.0%";
        agregarCeldaBalance(table, "Avance de Amortización:", pctStr, FONT_CELDA_DESTACADA);

        return table;
    }

    private PdfPTable crearBloqueFirmasYQr(DatosComprobante datos) {
        String qrText = String.format(
                "CREDINODA | %s\nCliente: %s\nDoc: %s\nCredito: #%d | Cuota: %d\nAbono: %s\nSaldo Pend: %s\nFecha: %s",
                datos.numeroRecibo(),
                datos.clienteNombre(),
                datos.clienteDocumento(),
                datos.creditoId(),
                datos.numeroCuota(),
                Formatters.formatoMoneda(datos.pagoMonto()),
                Formatters.formatoMoneda(datos.saldoCreditoRestante()),
                datos.fechaPago() != null ? Formatters.formatoFecha(datos.fechaPago()) : ""
        );

        byte[] qrBytes = generarCodigoQr(qrText, 130, 130);

        PdfPTable table = new PdfPTable(qrBytes != null ? 3 : 2);
        table.setWidthPercentage(100);
        if (qrBytes != null) {
            table.setWidths(new float[]{40, 40, 20});
        } else {
            table.setWidths(new float[]{50, 50});
        }

        // Columna 1: Firma Prestatario
        PdfPCell cFirma1 = new PdfPCell();
        cFirma1.setBorder(PdfPCell.NO_BORDER);
        cFirma1.setPaddingTop(35);
        cFirma1.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph line1 = new Paragraph("___________________________________", FONT_CELDA_LABEL);
        line1.setAlignment(Element.ALIGN_CENTER);
        Paragraph t1 = new Paragraph("Firma del Prestatario / Cliente", FONT_CELDA_DESTACADA);
        t1.setAlignment(Element.ALIGN_CENTER);
        Paragraph d1 = new Paragraph("C.C. " + datos.clienteDocumento(), FONT_CELDA_VALOR);
        d1.setAlignment(Element.ALIGN_CENTER);
        cFirma1.addElement(line1);
        cFirma1.addElement(t1);
        cFirma1.addElement(d1);
        table.addCell(cFirma1);

        // Columna 2: Firma Cobrador
        PdfPCell cFirma2 = new PdfPCell();
        cFirma2.setBorder(PdfPCell.NO_BORDER);
        cFirma2.setPaddingTop(35);
        cFirma2.setHorizontalAlignment(Element.ALIGN_CENTER);
        Paragraph line2 = new Paragraph("___________________________________", FONT_CELDA_LABEL);
        line2.setAlignment(Element.ALIGN_CENTER);
        Paragraph t2 = new Paragraph("Recibido Conforme / Sello", FONT_CELDA_DESTACADA);
        t2.setAlignment(Element.ALIGN_CENTER);
        Paragraph d2 = new Paragraph("CrediNoda Cobranza", FONT_CELDA_VALOR);
        d2.setAlignment(Element.ALIGN_CENTER);
        cFirma2.addElement(line2);
        cFirma2.addElement(t2);
        cFirma2.addElement(d2);
        table.addCell(cFirma2);

        // Columna 3: Código QR incrustado
        if (qrBytes != null) {
            PdfPCell cQr = new PdfPCell();
            cQr.setBorder(PdfPCell.NO_BORDER);
            cQr.setHorizontalAlignment(Element.ALIGN_CENTER);
            cQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                Image img = Image.getInstance(qrBytes);
                img.scaleAbsolute(65, 65);
                img.setAlignment(Element.ALIGN_CENTER);
                cQr.addElement(img);
                Paragraph qrLbl = new Paragraph("Validación Digital", FONT_LEGAL);
                qrLbl.setAlignment(Element.ALIGN_CENTER);
                cQr.addElement(qrLbl);
            } catch (Exception e) {
                cQr.addElement(new Paragraph("QR no disponible", FONT_LEGAL));
            }
            table.addCell(cQr);
        }

        // Fila inferior: Leyenda legal
        PdfPCell legalCell = new PdfPCell();
        legalCell.setColspan(qrBytes != null ? 3 : 2);
        legalCell.setBorder(PdfPCell.NO_BORDER);
        legalCell.setPaddingTop(15);
        Paragraph pLegal = new Paragraph(
                "Este comprobante constituye prueba válida del abono efectuado a la obligación descrita. " +
                        "Consérvelo para cualquier aclaración o conciliación contable. Impreso mediante CrediNoda 2.0.",
                FONT_LEGAL
        );
        pLegal.setAlignment(Element.ALIGN_CENTER);
        legalCell.addElement(pLegal);
        table.addCell(legalCell);

        return table;
    }

    private void agregarFilaKV(PdfPTable table, String label, String value) {
        PdfPCell cLabel = new PdfPCell(new Phrase(label, FONT_CELDA_LABEL));
        cLabel.setBackgroundColor(COLOR_FONDO_TABLA);
        cLabel.setBorderColor(COLOR_BORDE);
        cLabel.setPadding(3);
        table.addCell(cLabel);

        PdfPCell cVal = new PdfPCell(new Phrase(value != null ? value : "", FONT_CELDA_VALOR));
        cVal.setBorderColor(COLOR_BORDE);
        cVal.setPadding(3);
        table.addCell(cVal);
    }

    private void agregarCeldaHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_CELDA_LABEL));
        cell.setBackgroundColor(COLOR_FONDO_TABLA);
        cell.setBorderColor(COLOR_BORDE);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private void agregarCeldaDato(PdfPTable table, String text, int align, Color bg) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", FONT_CELDA_VALOR));
        cell.setBorderColor(COLOR_BORDE);
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        if (bg != null) {
            cell.setBackgroundColor(bg);
        }
        table.addCell(cell);
    }

    private void agregarCeldaBalance(PdfPTable table, String label, String val, Font fontVal) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(COLOR_FONDO_TABLA);
        cell.setBorderColor(COLOR_BORDE);
        cell.setPadding(4);
        cell.addElement(new Paragraph(label, FONT_CELDA_LABEL));
        Paragraph pVal = new Paragraph(val, fontVal);
        pVal.setAlignment(Element.ALIGN_RIGHT);
        cell.addElement(pVal);
        table.addCell(cell);
    }

    private Paragraph crearEspaciador(float spacing) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(spacing);
        p.setFont(FontFactory.getFont(FontFactory.HELVETICA, 1));
        return p;
    }

    /**
     * Retorna el directorio configurado por defecto para almacenar comprobantes PDF.
     */
    public Path getDefaultOutputDir() {
        return defaultOutputDir;
    }

    /**
     * Abre un archivo PDF con el visor predeterminado del sistema operativo si está disponible (CU-011).
     *
     * @param pdfPath Ruta al archivo PDF.
     */
    public void abrirDocumento(Path pdfPath) {
        if (pdfPath == null || !Files.exists(pdfPath)) {
            return;
        }
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                    desktop.open(pdfPath.toFile());
                }
            }
        } catch (Exception e) {
            System.err.println("Advertencia al abrir documento PDF: " + e.getMessage());
        }
    }
}
