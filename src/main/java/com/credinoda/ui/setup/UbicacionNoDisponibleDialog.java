package com.credinoda.ui.setup;

import com.credinoda.common.RutasSistema;
import com.credinoda.domain.dto.EstadoInstalacion;
import com.credinoda.domain.exception.ConfiguracionException;
import com.credinoda.service.ConfiguracionInicialService;
import com.credinoda.ui.components.StageIconHelper;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Diálogo modal presentado cuando la base de datos configurada no está disponible (CU-017, RN-021).
 * Evita la recreación silenciosa de bases de datos vacías y ofrece reintentar la conexión,
 * reubicar el archivo 'creditos.db' o salir de forma segura.
 */
public class UbicacionNoDisponibleDialog {

    public static final double DIALOG_WIDTH = 580.0;
    public static final double DIALOG_HEIGHT = 420.0;

    private final Stage stage;
    private final ConfiguracionInicialService configService;
    private final Consumer<RutasSistema> onResuelto;

    private Label lblFeedback;

    public UbicacionNoDisponibleDialog(Stage stage, ConfiguracionInicialService configService, Consumer<RutasSistema> onResuelto) {
        this.stage = Objects.requireNonNull(stage, "El Stage no puede ser nulo");
        this.configService = Objects.requireNonNull(configService, "ConfiguracionInicialService no puede ser nulo");
        this.onResuelto = Objects.requireNonNull(onResuelto, "El callback onResuelto no puede ser nulo");

        initUI();
    }

    private void initUI() {
        this.stage.setTitle("CrediNoda 2.0 — Base de Datos No Disponible");
        this.stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPadding(new Insets(24));

        // Cabecera de advertencia
        VBox headerBox = new VBox(6);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.setPadding(new Insets(0, 0, 14, 0));

        Label title = new Label("⚠️ Base de Datos No Disponible");
        title.getStyleClass().add("dialog-header-title");
        title.setStyle("-fx-text-fill: #b91c1c;");

        Label subtitle = new Label("No se pudo localizar el archivo de datos de CrediNoda 2.0");
        subtitle.getStyleClass().add("section-subtitle");

        headerBox.getChildren().addAll(title, subtitle);
        root.setTop(headerBox);

        // Cuerpo con explicación y tarjeta de ruta
        VBox bodyBox = new VBox(14);
        bodyBox.setAlignment(Pos.TOP_LEFT);

        Optional<RutasSistema> rutasOpt = configService.obtenerRutasConfiguradas();
        String rutaActual = rutasOpt.map(r -> r.databasePath().toString()).orElse("(Ruta no configurada)");

        VBox pathCard = new VBox(6);
        pathCard.getStyleClass().add("card-subtle");
        pathCard.setPadding(new Insets(12));

        Label lblRutaTitle = new Label("Ruta de base de datos registrada:");
        lblRutaTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #475569;");

        Label lblRuta = new Label(rutaActual);
        lblRuta.setWrapText(true);
        lblRuta.setStyle("-fx-font-family: monospace; -fx-text-fill: #0f172a;");

        pathCard.getChildren().addAll(lblRutaTitle, lblRuta);

        Label desc = new Label(
                "La base de datos no fue encontrada en la ubicación registrada. Esto puede suceder si:\n"
                        + "• La unidad de almacenamiento externo (USB o disco) fue desconectada.\n"
                        + "• La carpeta fue renombrada o movida a otra ubicación.\n\n"
                        + "🛡️ Por seguridad y para evitar la pérdida o sobrescritura de datos, CrediNoda 2.0 NUNCA "
                        + "creará una base de datos vacía automáticamente.");
        desc.setWrapText(true);
        desc.getStyleClass().add("section-subtitle");

        this.lblFeedback = new Label();
        this.lblFeedback.setWrapText(true);
        this.lblFeedback.setStyle("-fx-text-fill: #b91c1c; -fx-font-weight: bold;");

        bodyBox.getChildren().addAll(pathCard, desc, lblFeedback);
        root.setCenter(bodyBox);

        // Barra inferior con acciones
        HBox footerBox = new HBox(10);
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(16, 0, 0, 0));

        Button btnSalir = new Button("Salir");
        btnSalir.getStyleClass().add("btn-secondary");
        btnSalir.setOnAction(e -> Platform.exit());

        Button btnReintentar = new Button("🔄 Reintentar");
        btnReintentar.getStyleClass().add("btn-secondary");
        btnReintentar.setOnAction(e -> ejecutarReintento());

        Button btnReubicar = new Button("📂 Reubicar Archivo .db...");
        btnReubicar.getStyleClass().add("btn-primary");
        btnReubicar.setOnAction(e -> ejecutarReubicacion());

        footerBox.getChildren().addAll(btnSalir, btnReintentar, btnReubicar);
        root.setBottom(footerBox);

        Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        loadApplicationIcon(stage);
        this.stage.setScene(scene);
    }

    private void ejecutarReintento() {
        lblFeedback.setText("");
        EstadoInstalacion estado = configService.determinarEstadoInstalacion();
        if (estado == EstadoInstalacion.LISTO) {
            Optional<RutasSistema> rutas = configService.obtenerRutasConfiguradas();
            rutas.ifPresent(onResuelto);
        } else {
            lblFeedback.setText("El archivo 'creditos.db' sigue sin estar disponible en la ruta configurada. "
                    + "Verifique la conexión del disco o use 'Reubicar'.");
        }
    }

    private void ejecutarReubicacion() {
        lblFeedback.setText("");
        FileChooser fc = new FileChooser();
        fc.setTitle("Seleccionar Archivo de Base de Datos SQLite (creditos.db)");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de Datos SQLite (*.db)", "*.db"));

        File chosen = fc.showOpenDialog(stage);
        if (chosen != null) {
            try {
                Path nuevoDb = chosen.toPath();
                RutasSistema nuevasRutas = configService.reubicarBaseDatos(nuevoDb);
                onResuelto.accept(nuevasRutas);
            } catch (ConfiguracionException e) {
                lblFeedback.setText("Error al reubicar: " + e.getMessage());
            }
        }
    }

    public void mostrar() {
        this.stage.show();
    }

    private void loadApplicationIcon(Stage stage) {
        StageIconHelper.applyAppIcon(stage);
    }
}
