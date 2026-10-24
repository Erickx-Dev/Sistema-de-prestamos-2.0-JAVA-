package com.credinoda.ui.components;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Utilitario para la generación de códigos de barras 1D (Code 128)
 * directamente en componentes JavaFX {@link WritableImage} mediante ZXing.
 */
public final class BarcodeHelper {

    private static final String DEFAULT_FALLBACK = "CREDINODA-SIM-0000";
    private static final int BAR_COLOR = 0xFF26352D;
    private static final int BG_COLOR = 0x00000000; // Transparente

    private BarcodeHelper() {
    }

    /**
     * Genera una imagen JavaFX con un código de barras Code 128.
     *
     * @param content Texto o código a representar.
     * @param width   Ancho deseado en píxeles.
     * @param height  Alto deseado en píxeles.
     * @return {@link WritableImage} con el código de barras renderizado.
     */
    public static WritableImage generateCode128(String content, int width, int height) {
        int w = Math.max(width, 100);
        int h = Math.max(height, 20);
        String text = (content != null && !content.isBlank()) ? content.trim() : DEFAULT_FALLBACK;

        try {
            Code128Writer writer = new Code128Writer();
            BitMatrix matrix = writer.encode(text, BarcodeFormat.CODE_128, w, h);

            int matrixW = matrix.getWidth();
            int matrixH = matrix.getHeight();
            WritableImage image = new WritableImage(matrixW, matrixH);
            PixelWriter pw = image.getPixelWriter();

            for (int y = 0; y < matrixH; y++) {
                for (int x = 0; x < matrixW; x++) {
                    pw.setArgb(x, y, matrix.get(x, y) ? BAR_COLOR : BG_COLOR);
                }
            }
            return image;
        } catch (Exception e) {
            // Imagen de respaldo mínima transparente si ocurre error de codificación
            WritableImage fallbackImg = new WritableImage(w, h);
            PixelWriter pw = fallbackImg.getPixelWriter();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    pw.setArgb(x, y, BG_COLOR);
                }
            }
            return fallbackImg;
        }
    }
}
