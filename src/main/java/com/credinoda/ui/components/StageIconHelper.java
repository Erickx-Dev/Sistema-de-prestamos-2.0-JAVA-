package com.credinoda.ui.components;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilidad centralizada para aplicar y desplegar el ícono oficial de CrediNoda 2.0 a ventanas, diálogos y vistas JavaFX.
 */
public final class StageIconHelper {

    private static final Logger LOGGER = Logger.getLogger(StageIconHelper.class.getName());
    public static final String APP_ICON_RESOURCE = "/assets/Credinoda.png";

    private StageIconHelper() {
        // Clase utilitaria, no instanciable
    }

    /**
     * Aplica el ícono oficial de la aplicación al Stage suministrado.
     *
     * @param stage Ventana o diálogo JavaFX a la que se le asociará el ícono.
     */
    public static void applyAppIcon(Stage stage) {
        if (stage == null) {
            return;
        }
        Image iconImage = getAppIconImage();
        if (iconImage != null) {
            stage.getIcons().add(iconImage);
        }
    }

    /**
     * Obtiene una nueva instancia de {@link Image} decodificada a partir del recurso oficial de la aplicación.
     *
     * @return Instancia de Image, o null si el recurso no pudo ser localizado o decodificado.
     */
    public static Image getAppIconImage() {
        try (InputStream is = StageIconHelper.class.getResourceAsStream(APP_ICON_RESOURCE)) {
            if (is != null) {
                return new Image(is);
            } else {
                LOGGER.log(Level.WARNING, "No se encontró el recurso del ícono de la aplicación: {0}", APP_ICON_RESOURCE);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cargar el ícono de la aplicación", e);
        }
        return null;
    }

    /**
     * Crea un nodo {@link ImageView} dimensionado con escalado suave para incrustar el ícono oficial en vistas UI.
     *
     * @param size Ancho y alto deseados en píxeles.
     * @return ImageView configurado, o null si no se pudo cargar la imagen del ícono.
     */
    public static ImageView createIconView(double size) {
        Image image = getAppIconImage();
        if (image == null) {
            return null;
        }
        ImageView imageView = new ImageView(image);
        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        return imageView;
    }
}
