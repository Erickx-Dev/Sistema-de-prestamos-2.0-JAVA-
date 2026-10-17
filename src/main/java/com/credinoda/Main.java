package com.credinoda;

import com.credinoda.ui.CrediNodaApplication;
import javafx.application.Application;

/**
 * Punto de entrada bootstrap de CrediNoda 2.0.
 * Lanza el ciclo de vida de JavaFX delegando en {@link CrediNodaApplication}.
 */
public final class Main {

    private Main() {
        // Clase de arranque, no instanciable
    }

    public static void main(String[] args) {
        Application.launch(CrediNodaApplication.class, args);
    }
}
