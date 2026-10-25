package com.credinoda.ui.components;

import com.credinoda.domain.dto.BackupInfo;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.service.BackupService;
import com.credinoda.service.CreditoService;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Diálogo modal de Copias de Seguridad y Restauración en Caliente (CU-014, CU-015, RN-017, RN-018).
 * Permite crear un respaldo rápido en un clic, exportar una copia a otra ubicación o restaurar
 * un archivo .db con validación triple y respaldo preventivo automático.
 */
public class BackupDialog {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FILE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_hh-mm-ss-a", Locale.ENGLISH);

    private final Stage stage;
    private final BackupService backupService;
    private final CreditoService creditoService;
    private final Runnable onDataRestored;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final Label ultimoRespaldoLabel;
    private final Button backupRapidoButton;
    private final Button backupPersonalizadoButton;
    private final Button restaurarButton;

    public BackupDialog(Window owner,
                        BackupService backupService,
                        CreditoService creditoService,
                        Runnable onDataRestored,
                        BiConsumer<String, ToastNotification.Type> notifier) {
        this.backupService = Objects.requireNonNull(backupService, "BackupService no puede ser nulo");
        this.creditoService = Objects.requireNonNull(creditoService, "CreditoService no puede ser nulo");
        this.onDataRestored = onDataRestored;
        this.notifier = notifier;

        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle("Copia de Seguridad y Restauración");
        this.stage.setResizable(false);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(520);

        // 1. Cabecera
        HBox header = new HBox(10);
        header.getStyleClass().add("dialog-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label("💾 Gestión de Copias de Seguridad");
        titleLabel.getStyleClass().add("dialog-header-title");
        header.getChildren().add(titleLabel);

        // 2. Cuerpo
        VBox body = new VBox(14);
        body.getStyleClass().add("dialog-body");

        // Estado del último respaldo
        VBox statusCard = new VBox(6);
        statusCard.getStyleClass().add("card-subtle");
        Label statusTitle = new Label("Último respaldo local registrado:");
        statusTitle.getStyleClass().add("form-label");
        this.ultimoRespaldoLabel = new Label();
        this.ultimoRespaldoLabel.getStyleClass().add("section-subtitle");
        this.ultimoRespaldoLabel.setWrapText(true);
        statusCard.getChildren().addAll(statusTitle, ultimoRespaldoLabel);

        // Sección Crear Respaldo
        VBox createSection = new VBox(8);
        Label createTitle = new Label("1. Resguardar Base de Datos Actual");
        createTitle.getStyleClass().add("form-label");

        Path dirBackups = backupService.getBackupsDir();
        String nombreCarpeta = (dirBackups.getFileName() != null) ? dirBackups.getFileName().toString() : dirBackups.toString();
        this.backupRapidoButton = new Button("⚡ Crear Respaldo Rápido (Carpeta " + nombreCarpeta + "/)");
        this.backupRapidoButton.getStyleClass().add("btn-primary");
        this.backupRapidoButton.setMaxWidth(Double.MAX_VALUE);
        this.backupRapidoButton.setOnAction(e -> ejecutarBackupRapido());

        this.backupPersonalizadoButton = new Button("📁 Guardar Copia En Otra Ubicación...");
        this.backupPersonalizadoButton.getStyleClass().add("btn-secondary");
        this.backupPersonalizadoButton.setMaxWidth(Double.MAX_VALUE);
        this.backupPersonalizadoButton.setOnAction(e -> handleBackupPersonalizado());

        createSection.getChildren().addAll(createTitle, backupRapidoButton, backupPersonalizadoButton);

        // Sección Restaurar
        VBox restoreSection = new VBox(8);
        Label restoreTitle = new Label("2. Restaurar desde un Archivo de Respaldo (.db)");
        restoreTitle.getStyleClass().add("form-label");

        Label restoreNote = new Label(
                "Se verificará la integridad del archivo y se creará automáticamente un respaldo preventivo antes de restaurar.");
        restoreNote.getStyleClass().add("section-subtitle");
        restoreNote.setWrapText(true);

        this.restaurarButton = new Button("📂 Seleccionar Archivo .db y Restaurar...");
        this.restaurarButton.getStyleClass().add("btn-danger");
        this.restaurarButton.setMaxWidth(Double.MAX_VALUE);
        this.restaurarButton.setOnAction(e -> handleSeleccionarYRestaurar());
        if (com.credinoda.service.ContextoSeguridad.isModoSoloLectura()) {
            this.restaurarButton.setDisable(true);
            restoreNote.setText("La restauración de copias de seguridad está deshabilitada en Modo Solo Lectura (Licencia comercial vencida). Puede generar respaldos para proteger sus datos.");
        }

        restoreSection.getChildren().addAll(restoreTitle, restoreNote, restaurarButton);

        body.getChildren().addAll(statusCard, createSection, restoreSection);

        // 3. Pie
        HBox footer = new HBox(10);
        footer.getStyleClass().add("dialog-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        Button closeButton = new Button("Cerrar");
        closeButton.getStyleClass().add("btn-secondary");
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> stage.close());
        footer.getChildren().add(closeButton);

        root.setTop(header);
        root.setCenter(body);
        root.setBottom(footer);

        refreshUltimoRespaldoInfo();

        Scene scene = new Scene(root);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        this.stage.setScene(scene);
    }

    public final void refreshUltimoRespaldoInfo() {
        try {
            List<BackupInfo> backups = backupService.listarBackups();
            if (backups.isEmpty()) {
                ultimoRespaldoLabel.setText("Sin respaldos locales aún en la carpeta backups/");
            } else {
                BackupInfo latest = backups.get(0);
                ultimoRespaldoLabel.setText(String.format("%s — %s (%.1f KB)",
                        latest.nombreArchivo(),
                        latest.fechaModificacion().format(DISPLAY_FMT),
                        latest.tamanoKb()));
            }
        } catch (Exception e) {
            ultimoRespaldoLabel.setText("No se pudo consultar la carpeta backups/");
        }
    }

    public Path ejecutarBackupRapido() {
        try {
            Path path = backupService.crearBackup(null, "creditos_backup");
            refreshUltimoRespaldoInfo();
            if (notifier != null) {
                notifier.accept("Copia de seguridad creada: " + path.getFileName(), ToastNotification.Type.SUCCESS);
            }
            return path;
        } catch (CrediNodaException ex) {
            if (notifier != null) {
                notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
            }
            return null;
        }
    }

    private void handleBackupPersonalizado() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Copia de Seguridad");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite (*.db)", "*.db"));
        chooser.setInitialFileName("creditos_backup_" + LocalDateTime.now().format(FILE_FMT).toLowerCase(Locale.ROOT) + ".db");

        File dest = chooser.showSaveDialog(stage);
        if (dest == null) {
            return;
        }

        try {
            Path path = backupService.crearBackup(dest.toPath(), "creditos_backup");
            refreshUltimoRespaldoInfo();
            if (notifier != null) {
                notifier.accept("Copia de seguridad guardada: " + path.getFileName(), ToastNotification.Type.SUCCESS);
            }
        } catch (CrediNodaException ex) {
            if (notifier != null) {
                notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
            }
        }
    }

    private void handleSeleccionarYRestaurar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Seleccionar Copia de Seguridad para Restaurar");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base de datos SQLite (*.db)", "*.db"));
        Path backupsDir = backupService.getBackupsDir();
        if (backupsDir != null && Files.exists(backupsDir)) {
            chooser.setInitialDirectory(backupsDir.toFile());
        }

        File selected = chooser.showOpenDialog(stage);
        if (selected == null) {
            return;
        }

        ConfirmDialog confirm = new ConfirmDialog(
                stage,
                "Confirmar Restauración de Base de Datos",
                "¿Está seguro de restaurar esta copia de seguridad?\n" +
                        "Se creará un respaldo preventivo automático antes de reemplazar los datos actuales.",
                "Sí, Restaurar",
                true
        );

        if (confirm.showAndWait()) {
            ejecutarRestauracion(selected.toPath());
        }
    }

    public boolean ejecutarRestauracion(Path rutaArchivo) {
        try {
            backupService.restaurarBackup(rutaArchivo);
            creditoService.actualizarCuotasEnMora(LocalDate.now());
            if (onDataRestored != null) {
                onDataRestored.run();
            }
            if (notifier != null) {
                notifier.accept("Base de datos restaurada exitosamente.", ToastNotification.Type.SUCCESS);
            }
            if (stage.isShowing()) {
                stage.close();
            }
            return true;
        } catch (CrediNodaException ex) {
            if (notifier != null) {
                notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
            }
            return false;
        }
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    public Label getUltimoRespaldoLabel() {
        return ultimoRespaldoLabel;
    }

    public Button getBackupRapidoButton() {
        return backupRapidoButton;
    }

    public Button getRestaurarButton() {
        return restaurarButton;
    }
}
