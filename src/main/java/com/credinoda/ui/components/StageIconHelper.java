package com.credinoda.ui.components;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utilidad centralizada para aplicar el ícono oficial de CrediNoda 2.0 a ventanas y diálogos JavaFX.
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
        try (InputStream is = StageIconHelper.class.getResourceAsStream(APP_ICON_RESOURCE)) {
            if (is != null) {
                stage.getIcons().add(new Image(is));
            } else {
                LOGGER.log(Level.WARNING, "No se encontró el recurso del ícono de la aplicación: {0}", APP_ICON_RESOURCE);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error al cargar el ícono de la aplicación", e);
        }
    }
}
