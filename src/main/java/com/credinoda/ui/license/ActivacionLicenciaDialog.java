package com.credinoda.ui.license;

import com.credinoda.domain.dto.ResultadoValidacionLicencia;
import com.credinoda.domain.model.EstadoLicencia;
import com.credinoda.service.LicenciaService;
import com.credinoda.ui.components.StageIconHelper;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.util.Objects;

/**
 * Diálogo modal bloqueante para el control, activación y recuperación de licencias offline (CU-022 a CU-024, RN-030 a RN-033).
 * Se presenta antes de LoginDialog / MainShell cuando la licencia no está registrada, expiró o el reloj fue manipulado.
 */
public class ActivacionLicenciaDialog {

    public static final double DIALOG_WIDTH = 580.0;
    public static final double DIALOG_HEIGHT = 580.0;

    private final Stage stage;
    private final LicenciaService licenciaService;
    private ResultadoValidacionLicencia estadoActual;

    private VBox bannerBox;
    private Label lblBannerTitulo;
    private Label lblBannerDetalle;

    private TextField txtHardwareId;
    private Button btnCopiarId;

    private TextArea txtLicenciaToken;
    private Button btnCargarArchivo;
    private Label lblFeedback;

    private Button btnSalir;
    private Button btnReintentarHora;
    private Button btnActivar;

    private boolean licenciaActivada = false;

    public ActivacionLicenciaDialog(Window owner, LicenciaService licenciaService, ResultadoValidacionLicencia estadoInicial) {
        this.licenciaService = Objects.requireNonNull(licenciaService, "LicenciaService no puede ser nulo");
        this.estadoActual = estadoInicial != null ? estadoInicial : licenciaService.validarEstadoActual();
        this.stage = new Stage();
        if (owner != null) {
            this.stage.initOwner(owner);
        }
        this.stage.initModality(Modality.APPLICATION_MODAL);
        initUI();
    }

    public ActivacionLicenciaDialog(LicenciaService licenciaService, ResultadoValidacionLicencia estadoInicial) {
        this((Window) null, licenciaService, estadoInicial);
    }

    private void initUI() {
        stage.setTitle("CrediNoda 2.0 — Activación y Control de Licencia");
        stage.setResizable(false);

        VBox root = new VBox(16);
        root.getStyleClass().add("license-dialog-root");
        root.setPadding(new Insets(24, 24, 20, 24));
        root.setAlignment(Pos.TOP_CENTER);

        // 1. Bloque de Marca Superior
        HBox brandHeader = new HBox(12);
        brandHeader.setAlignment(Pos.CENTER_LEFT);

        Node logoNode;
        ImageView logoView = StageIconHelper.createIconView(44);
        if (logoView != null) {
            logoNode = logoView;
        } else {
            Label logoBadge = new Label("C");
            logoBadge.getStyleClass().add("nav-logo-badge");
            logoBadge.setStyle("-fx-font-size: 20px; -fx-min-width: 40px; -fx-min-height: 40px;");
            logoNode = logoBadge;
        }

        VBox titleBox = new VBox(2);
        Label titleLabel = new Label("CrediNoda 2.0");
        titleLabel.getStyleClass().add("brand-text");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label subtitleLabel = new Label("Activación y Seguridad Comercial Offline");
        subtitleLabel.getStyleClass().add("section-subtitle");
        titleBox.getChildren().addAll(titleLabel, subtitleLabel);

        brandHeader.getChildren().addAll(logoNode, titleBox);

        // 2. Banner de Estado Dinámico
        this.bannerBox = new VBox(4);
        this.bannerBox.getStyleClass().add("license-status-banner");
        this.lblBannerTitulo = new Label();
        this.lblBannerTitulo.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        this.lblBannerDetalle = new Label();
        this.lblBannerDetalle.setWrapText(true);
        this.lblBannerDetalle.setStyle("-fx-font-size: 11px;");
        this.bannerBox.getChildren().addAll(lblBannerTitulo, lblBannerDetalle);

        actualizarVisualizacionBanner(estadoActual);

        // 3. Tarjeta de Hardware ID
        VBox hwCard = new VBox(6);
        hwCard.getStyleClass().add("hardware-id-card");

        Label lblHwTitle = new Label("Identificador Único del Equipo (Hardware ID):");
        lblHwTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #475569;");

        HBox hwFieldBox = new HBox(8);
        hwFieldBox.setAlignment(Pos.CENTER_LEFT);

        this.txtHardwareId = new TextField(licenciaService.obtenerHardwareIdActual());
        this.txtHardwareId.setEditable(false);
        this.txtHardwareId.getStyleClass().add("hardware-id-code");
        HBox.setHgrow(txtHardwareId, Priority.ALWAYS);

        this.btnCopiarId = new Button("📋 Copiar ID");
        this.btnCopiarId.getStyleClass().add("btn-secondary");
        this.btnCopiarId.setOnAction(e -> copiarHardwareIdAlPortapapeles());

        hwFieldBox.getChildren().addAll(txtHardwareId, btnCopiarId);

        Label lblHwNota = new Label("Proporcione este identificador al administrador para la emisión de su licencia offline.");
        lblHwNota.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");

        hwCard.getChildren().addAll(lblHwTitle, hwFieldBox, lblHwNota);

        // 4. Formulario de Licencia
        VBox inputSection = new VBox(8);

        HBox inputHeader = new HBox(8);
        inputHeader.setAlignment(Pos.CENTER_LEFT);

        Label lblInputTitle = new Label("Código de Activación o Archivo .lic:");
        lblInputTitle.getStyleClass().add("form-label");
        HBox.setHgrow(lblInputTitle, Priority.ALWAYS);

        this.btnCargarArchivo = new Button("📂 Cargar archivo .lic...");
        this.btnCargarArchivo.getStyleClass().add("btn-secondary");
        this.btnCargarArchivo.setStyle("-fx-font-size: 11px; -fx-padding: 4px 10px;");
        this.btnCargarArchivo.setOnAction(e -> abrirSelectorArchivo());

        inputHeader.getChildren().addAll(lblInputTitle, btnCargarArchivo);

        this.txtLicenciaToken = new TextArea();
        this.txtLicenciaToken.setPromptText("Pegue aquí el código de licencia (CREDINODA-LIC-V1:...) o cargue el archivo .lic");
        this.txtLicenciaToken.setWrapText(true);
        this.txtLicenciaToken.getStyleClass().add("license-textarea");

        this.lblFeedback = new Label();
        this.lblFeedback.setWrapText(true);
        this.lblFeedback.setStyle("-fx-font-size: 11px; -fx-font-weight: bold;");
        this.lblFeedback.setVisible(false);
        this.lblFeedback.setManaged(false);

        inputSection.getChildren().addAll(inputHeader, txtLicenciaToken, lblFeedback);

        // 5. Botones de Acción Inferiores
        HBox footerBox = new HBox(10);
        footerBox.setAlignment(Pos.CENTER_RIGHT);
        footerBox.setPadding(new Insets(10, 0, 0, 0));

        this.btnSalir = new Button("Salir");
        this.btnSalir.getStyleClass().add("btn-secondary");
        this.btnSalir.setOnAction(e -> stage.close());

        this.btnReintentarHora = new Button("🔄 Reintentar Verificación de Hora");
        this.btnReintentarHora.getStyleClass().add("btn-secondary");
        this.btnReintentarHora.setOnAction(e -> reintentarVerificacionHora());

        this.btnActivar = new Button("🔑 Validar y Activar Licencia");
        this.btnActivar.getStyleClass().add("btn-primary");
        this.btnActivar.setDefaultButton(true);
        this.btnActivar.setOnAction(e -> procesarActivacion());

        actualizarVisibilidadBotones(estadoActual.estado());

        footerBox.getChildren().addAll(btnSalir, btnReintentarHora, btnActivar);

        root.getChildren().addAll(brandHeader, bannerBox, hwCard, inputSection, footerBox);

        // Atajos de teclado
        root.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        Scene scene = new Scene(root, DIALOG_WIDTH, DIALOG_HEIGHT);
        URL cssUrl = getClass().getResource("/css/credinoda.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }

        StageIconHelper.applyAppIcon(stage);
        stage.setScene(scene);
    }

    public void copiarHardwareIdAlPortapapeles() {
        String hwId = txtHardwareId.getText();
        if (hwId != null && !hwId.isBlank()) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(hwId.trim());
            clipboard.setContent(content);

            btnCopiarId.setText("✓ Copiado");
            btnCopiarId.setStyle("-fx-background-color: #DCFCE7; -fx-text-fill: #15803D;");

            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(e -> {
                btnCopiarId.setText("📋 Copiar ID");
                btnCopiarId.setStyle("");
            });
            pause.play();
        }
    }

    public void procesarActivacion() {
        String rawToken = txtLicenciaToken.getText();
        if (rawToken == null || rawToken.isBlank()) {
            mostrarFeedback("Por favor, ingrese o cargue un código de licencia válido.", true);
            return;
        }

        ResultadoValidacionLicencia res = licenciaService.activarLicencia(rawToken.trim());
        this.estadoActual = res;
        actualizarVisualizacionBanner(res);
        actualizarVisibilidadBotones(res.estado());

        if (res.esValida()) {
            this.licenciaActivada = true;
            mostrarFeedback("¡Licencia activada con éxito!", false);
            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(e -> stage.close());
            pause.play();
        } else {
            mostrarFeedback(res.mensajeDetalle(), true);
        }
    }

    public void reintentarVerificacionHora() {
        ResultadoValidacionLicencia res = licenciaService.validarEstadoActual();
        this.estadoActual = res;
        actualizarVisualizacionBanner(res);
        actualizarVisibilidadBotones(res.estado());

        if (res.esValida()) {
            this.licenciaActivada = true;
            mostrarFeedback("Hora del sistema verificada y correcta. Acceso concedido.", false);
            PauseTransition pause = new PauseTransition(Duration.millis(800));
            pause.setOnFinished(e -> stage.close());
            pause.play();
        } else if (res.estado() == EstadoLicencia.RELOJ_MANIPULADO) {
            mostrarFeedback("El reloj del equipo sigue atrasado respecto a la última ejecución registrada.", true);
        } else {
            mostrarFeedback(res.mensajeDetalle(), true);
        }
    }

    private void abrirSelectorArchivo() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Archivo de Licencia");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Archivos de Licencia (*.lic, *.txt)", "*.lic", "*.txt"),
                new FileChooser.ExtensionFilter("Todos los archivos", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            try {
                String content = Files.readString(selectedFile.toPath());
                txtLicenciaToken.setText(content.trim());
                mostrarFeedback("Archivo '" + selectedFile.getName() + "' cargado. Pulse 'Validar y Activar'.", false);
            } catch (Exception e) {
                mostrarFeedback("Error al leer el archivo de licencia: " + e.getMessage(), true);
            }
        }
    }

    private void actualizarVisualizacionBanner(ResultadoValidacionLicencia res) {
        bannerBox.getStyleClass().removeAll("license-banner-warning", "license-banner-danger", "license-banner-success");

        switch (res.estado()) {
            case VALIDA -> {
                bannerBox.getStyleClass().add("license-banner-success");
                lblBannerTitulo.setText("✓ Licencia Activa y Vigente");
                lblBannerDetalle.setText(res.mensajeDetalle());
            }
            case NO_REGISTRADA -> {
                bannerBox.getStyleClass().add("license-banner-warning");
                lblBannerTitulo.setText("⚠️ Licencia No Registrada");
                lblBannerDetalle.setText("CrediNoda 2.0 requiere una licencia comercial activada para operar en este equipo.");
            }
            case EXPIRADA -> {
                bannerBox.getStyleClass().add("license-banner-danger");
                lblBannerTitulo.setText("⏰ Licencia Expirada");
                lblBannerDetalle.setText(res.mensajeDetalle() + " Ingrese un nuevo código para renovar su suscripción.");
            }
            case HARDWARE_NO_COINCIDE -> {
                bannerBox.getStyleClass().add("license-banner-danger");
                lblBannerTitulo.setText("🚫 Equipo No Autorizado (Hardware ID No Coincide)");
                lblBannerDetalle.setText(res.mensajeDetalle());
            }
            case RELOJ_MANIPULADO -> {
                bannerBox.getStyleClass().add("license-banner-danger");
                lblBannerTitulo.setText("⏱️ Bloqueo Preventivo: Reloj del Sistema Atrasado");
                lblBannerDetalle.setText(res.mensajeDetalle() + " Corrija la hora y pulse 'Reintentar Verificación de Hora'.");
            }
            default -> {
                bannerBox.getStyleClass().add("license-banner-danger");
                lblBannerTitulo.setText("❌ Error de Validación de Licencia");
                lblBannerDetalle.setText(res.mensajeDetalle());
            }
        }
    }

    private void actualizarVisibilidadBotones(EstadoLicencia estado) {
        boolean esReloj = (estado == EstadoLicencia.RELOJ_MANIPULADO);
        btnReintentarHora.setVisible(esReloj);
        btnReintentarHora.setManaged(esReloj);
    }

    private void mostrarFeedback(String mensaje, boolean esError) {
        lblFeedback.setText(mensaje);
        lblFeedback.setStyle(esError ? "-fx-text-fill: #b91c1c; -fx-font-weight: bold;" : "-fx-text-fill: #15803D; -fx-font-weight: bold;" );
        lblFeedback.setVisible(true);
        lblFeedback.setManaged(true);
    }

    /**
     * Despliega la ventana modal de activación y espera a que el usuario complete la activación o cierre la ventana.
     *
     * @return true si la licencia quedó en estado VALIDA, false si el usuario salió sin activar.
     */
    public boolean mostrarYEsperar() {
        stage.showAndWait();
        return licenciaActivada;
    }

    public boolean isLicenciaActivada() {
        return licenciaActivada;
    }

    public TextField getHardwareIdField() {
        return txtHardwareId;
    }

    public Button getBtnCopiarId() {
        return btnCopiarId;
    }

    public TextArea getTxtLicenciaToken() {
        return txtLicenciaToken;
    }

    public Button getBtnActivar() {
        return btnActivar;
    }

    public Button getBtnReintentarHora() {
        return btnReintentarHora;
    }

    public Label getLblBannerTitulo() {
        return lblBannerTitulo;
    }

    public Label getLblFeedback() {
        return lblFeedback;
    }

    public Stage getStage() {
        return stage;
    }
}
