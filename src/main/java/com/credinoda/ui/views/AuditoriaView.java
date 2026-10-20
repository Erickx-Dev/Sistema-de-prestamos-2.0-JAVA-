package com.credinoda.ui.views;

import com.credinoda.domain.dto.FiltroAuditoria;
import com.credinoda.domain.dto.RegistroAuditoriaItem;
import com.credinoda.domain.dto.SesionUsuario;
import com.credinoda.domain.exception.AccesoDenegadoException;
import com.credinoda.domain.model.AccionAuditoria;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.ResultadoAuditoria;
import com.credinoda.domain.model.Rol;
import com.credinoda.service.AuditoriaService;
import com.credinoda.service.ContextoSeguridad;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.TableColumnHelper;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Vista de Bitácora de Auditoría Centralizada e Inmutable (CU-021, RN-027, RN-028).
 * Permite a los Administradores auditar las acciones sensibles del sistema, filtrar por rangos
 * temporales, usuarios, acciones y resultados, e inspeccionar los detalles comparativos de los cambios.
 */
public class AuditoriaView extends VBox implements ViewRouter.RoutableView {

    private final AuditoriaService auditoriaService;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final TextField searchField;
    private final ComboBox<String> comboRangoFecha;
    private final ComboBox<AccionAuditoria> comboAccion;
    private final ComboBox<ResultadoAuditoria> comboResultado;
    private final Label lblContador;
    private final TableView<RegistroAuditoriaItem> tablaAuditoria;
    private final ObservableList<RegistroAuditoriaItem> itemsAuditoria = FXCollections.observableArrayList();
    private final TextArea txtDetalle;

    public AuditoriaView(AuditoriaService auditoriaService,
                         ViewRouter viewRouter,
                         BiConsumer<String, ToastNotification.Type> notifier) {
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(16);

        SectionHeader header = new SectionHeader(
                "Bitácora de Auditoría",
                "Registro inmutable de trazabilidad operativa y eventos sensibles del sistema"
        );

        // Barra de búsqueda y filtros
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        this.searchField = new TextField();
        this.searchField.setPromptText("🔍 Buscar por usuario o entidad...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        this.searchField.setOnAction(e -> aplicarFiltros());

        this.comboRangoFecha = new ComboBox<>();
        this.comboRangoFecha.getItems().addAll("Últimos 7 días", "Hoy", "Últimos 30 días", "Todo el historial");
        this.comboRangoFecha.setValue("Últimos 7 días");
        this.comboRangoFecha.valueProperty().addListener((obs, o, n) -> aplicarFiltros());

        this.comboAccion = new ComboBox<>();
        this.comboAccion.setPromptText("Todas las acciones");
        this.comboAccion.getItems().add(null);
        this.comboAccion.getItems().addAll(AccionAuditoria.values());
        this.comboAccion.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(AccionAuditoria item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Todas las acciones" : item.getNombreVisible());
            }
        });
        this.comboAccion.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(AccionAuditoria item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Todas las acciones" : item.getNombreVisible());
            }
        });
        this.comboAccion.valueProperty().addListener((obs, o, n) -> aplicarFiltros());

        this.comboResultado = new ComboBox<>();
        this.comboResultado.setPromptText("Todos los resultados");
        this.comboResultado.getItems().add(null);
        this.comboResultado.getItems().addAll(ResultadoAuditoria.values());
        this.comboResultado.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ResultadoAuditoria item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Todos los resultados" : item.getEtiqueta());
            }
        });
        this.comboResultado.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ResultadoAuditoria item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Todos los resultados" : item.getEtiqueta());
            }
        });
        this.comboResultado.valueProperty().addListener((obs, o, n) -> aplicarFiltros());

        Button btnBuscar = new Button("Filtrar");
        btnBuscar.getStyleClass().add("btn-primary");
        btnBuscar.setOnAction(e -> aplicarFiltros());

        Button btnLimpiar = new Button("Limpiar");
        btnLimpiar.getStyleClass().add("btn-secondary");
        btnLimpiar.setOnAction(e -> {
            searchField.clear();
            comboRangoFecha.setValue("Últimos 7 días");
            comboAccion.setValue(null);
            comboResultado.setValue(null);
            aplicarFiltros();
        });

        filterBar.getChildren().addAll(searchField, comboRangoFecha, comboAccion, comboResultado, btnBuscar, btnLimpiar);

        // Tarjeta principal con tabla
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        this.tablaAuditoria = buildTable();
        VBox.setVgrow(tablaAuditoria, Priority.ALWAYS);

        HBox footerBar = new HBox(12);
        footerBar.setAlignment(Pos.CENTER_LEFT);
        this.lblContador = new Label("Mostrando 0 registros");
        this.lblContador.getStyleClass().add("text-muted");
        footerBar.getChildren().add(lblContador);

        // Panel inferior para visualización del detalle seleccionado
        VBox detailBox = new VBox(6);
        detailBox.getStyleClass().add("audit-detail-pane");
        detailBox.setPadding(new Insets(8, 0, 0, 0));

        Label lblDetalleTitulo = new Label("Detalle del evento seleccionado:");
        lblDetalleTitulo.getStyleClass().add("form-label");

        this.txtDetalle = new TextArea();
        this.txtDetalle.setEditable(false);
        this.txtDetalle.setWrapText(true);
        this.txtDetalle.setPrefRowCount(3);
        this.txtDetalle.setPromptText("Seleccione una fila en la tabla superior para visualizar las diferencias y parámetros exactos del evento.");

        detailBox.getChildren().addAll(lblDetalleTitulo, txtDetalle);

        this.tablaAuditoria.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null && newSel.detalle() != null && !newSel.detalle().isBlank()) {
                txtDetalle.setText(String.format("Evento #%d [%s]: %s\nEstación: %s\nDetalle: %s",
                        newSel.id(), newSel.accionNombre(), newSel.fechaHoraFormateada(), newSel.estacionTrabajo(), newSel.detalle()));
            } else {
                txtDetalle.setText("");
            }
        });

        tableCard.getChildren().addAll(filterBar, tablaAuditoria, footerBar, detailBox);
        getChildren().addAll(header, tableCard);
    }

    private TableView<RegistroAuditoriaItem> buildTable() {
        TableView<RegistroAuditoriaItem> table = new TableView<>(itemsAuditoria);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No se encontraron registros de auditoría coincidentes."));

        TableColumn<RegistroAuditoriaItem, String> colFecha = TableColumnHelper.textoColumn("Fecha / Hora", RegistroAuditoriaItem::fechaHoraFormateada, Pos.CENTER, 140);
        colFecha.setMaxWidth(160);

        TableColumn<RegistroAuditoriaItem, String> colUsuario = TableColumnHelper.textoColumn("Usuario", RegistroAuditoriaItem::usuario, Pos.CENTER, 100);
        colUsuario.setMaxWidth(130);

        TableColumn<RegistroAuditoriaItem, String> colRol = TableColumnHelper.textoColumn("Rol", RegistroAuditoriaItem::rol, Pos.CENTER, 110);
        colRol.setMaxWidth(130);

        TableColumn<RegistroAuditoriaItem, String> colAccion = TableColumnHelper.textoColumn("Acción", RegistroAuditoriaItem::accionNombre, Pos.CENTER_LEFT, 170);

        TableColumn<RegistroAuditoriaItem, String> colEntidad = TableColumnHelper.textoColumn("Entidad", RegistroAuditoriaItem::entidad, Pos.CENTER, 90);
        colEntidad.setMaxWidth(110);

        TableColumn<RegistroAuditoriaItem, String> colEntidadId = TableColumnHelper.textoColumn("ID", RegistroAuditoriaItem::entidadId, Pos.CENTER, 80);
        colEntidadId.setMaxWidth(100);

        // Columna de resultado con badge de color
        TableColumn<RegistroAuditoriaItem, ResultadoAuditoria> colResultado = new TableColumn<>("Resultado");
        colResultado.setPrefWidth(95);
        colResultado.setMaxWidth(110);
        TableColumnHelper.setHeaderAlignment(colResultado, Pos.CENTER);
        colResultado.setCellValueFactory(cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().resultado()));
        colResultado.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(ResultadoAuditoria resultado, boolean empty) {
                super.updateItem(resultado, empty);
                if (empty || resultado == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    Label badge = new Label(resultado.getEtiqueta());
                    badge.getStyleClass().add("badge");
                    if (resultado == ResultadoAuditoria.EXITO) {
                        badge.getStyleClass().add("badge-al-dia");
                    } else if (resultado == ResultadoAuditoria.FALLO) {
                        badge.getStyleClass().add("badge-en-mora");
                    } else {
                        badge.getStyleClass().add("badge-parcial");
                    }
                    setAlignment(Pos.CENTER);
                    setGraphic(badge);
                }
            }
        });

        TableColumn<RegistroAuditoriaItem, String> colEstacion = TableColumnHelper.textoColumn("Estación", RegistroAuditoriaItem::estacionTrabajo, Pos.CENTER, 100);
        colEstacion.setMaxWidth(130);

        TableColumn<RegistroAuditoriaItem, String> colDetalle = TableColumnHelper.textoColumn("Detalle de la Operación", RegistroAuditoriaItem::detalle, Pos.CENTER_LEFT, 200);

        table.getColumns().addAll(List.of(colFecha, colUsuario, colRol, colAccion, colEntidad, colEntidadId, colResultado, colEstacion, colDetalle));
        return table;
    }

    public void aplicarFiltros() {
        LocalDate desde = null;
        LocalDate hasta = LocalDate.now();

        String rango = comboRangoFecha.getValue();
        if ("Hoy".equals(rango)) {
            desde = LocalDate.now();
        } else if ("Últimos 7 días".equals(rango)) {
            desde = LocalDate.now().minusDays(7);
        } else if ("Últimos 30 días".equals(rango)) {
            desde = LocalDate.now().minusDays(30);
        } else {
            desde = null;
            hasta = null;
        }

        String search = searchField.getText() != null && !searchField.getText().isBlank() ? searchField.getText().trim() : null;
        AccionAuditoria accion = comboAccion.getValue();
        ResultadoAuditoria resultado = comboResultado.getValue();

        FiltroAuditoria filtro = new FiltroAuditoria(
                desde,
                hasta,
                search,
                accion,
                null,
                resultado,
                500,
                0
        );

        List<RegistroAuditoriaItem> items = auditoriaService.buscarItems(filtro);
        itemsAuditoria.setAll(items);
        lblContador.setText(String.format("Mostrando %d registros de auditoría", items.size()));
    }

    @Override
    public void onShow(int currentDataVersion) {
        SesionUsuario sesion = ContextoSeguridad.getSesionActual();
        if (sesion.rol() != Rol.ADMINISTRADOR) {
            if (notifier != null) {
                notifier.accept("Acceso denegado: El módulo de auditoría es exclusivo para Administradores.", ToastNotification.Type.ERROR);
            }
            throw new AccesoDenegadoException(Permiso.AUDITORIA_VER, "Acceso exclusivo para rol ADMINISTRADOR");
        }
        aplicarFiltros();
    }
}
