package com.credinoda.ui.views;

import com.credinoda.domain.model.Cliente;
import com.credinoda.service.ClienteService;
import com.credinoda.ui.components.ClienteDialog;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Vista de Gestión de Clientes Prestatarios (CU-001, CU-002, CU-003, CU-004, RN-001 a RN-003).
 * Permite listar, buscar en tiempo real por nombre o documento, crear nuevos clientes y
 * navegar al expediente detallado de cada prestatario.
 */
public class ClientesView extends VBox implements ViewRouter.RoutableView {

    private final ClienteService clienteService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final TextField searchField;
    private final TableView<Cliente> tablaClientes;
    private final ObservableList<Cliente> itemsClientes = FXCollections.observableArrayList();

    public ClientesView(ClienteService clienteService,
                        ViewRouter viewRouter,
                        BiConsumer<String, ToastNotification.Type> notifier) {
        this.clienteService = Objects.requireNonNull(clienteService, "ClienteService no puede ser nulo");
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        SectionHeader header = new SectionHeader(
                "Gestión de Clientes",
                "Directorio de prestatarios y expedientes crediticios",
                "+ Nuevo Cliente",
                this::openNuevoClienteDialog
        );

        // Barra de búsqueda en tiempo real (CU-002, RN-003)
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);

        this.searchField = new TextField();
        this.searchField.setPromptText("🔍 Buscar cliente por nombre o documento de identidad...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        this.searchField.textProperty().addListener((obs, oldV, newV) -> filterClientes(newV));

        Button clearBtn = new Button("Limpiar");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> searchField.clear());

        searchBar.getChildren().addAll(searchField, clearBtn);

        // Tarjeta de tabla
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        this.tablaClientes = buildTable();
        VBox.setVgrow(tablaClientes, Priority.ALWAYS);

        tableCard.getChildren().addAll(searchBar, tablaClientes);
        getChildren().addAll(header, tableCard);
    }

    private TableView<Cliente> buildTable() {
        TableView<Cliente> table = new TableView<>(itemsClientes);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No se encontraron clientes registrados."));

        TableColumn<Cliente, String> colId = new TableColumn<>("ID");
        colId.setMaxWidth(90);
        colId.setCellValueFactory(d -> new SimpleStringProperty("#" + d.getValue().getId()));

        TableColumn<Cliente, String> colNombre = new TableColumn<>("Nombre Completo");
        colNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getNombre()));

        TableColumn<Cliente, String> colDocumento = new TableColumn<>("Documento");
        colDocumento.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDocumento()));

        TableColumn<Cliente, String> colTelefono = new TableColumn<>("Teléfono");
        colTelefono.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getTelefono()));

        TableColumn<Cliente, Void> colAcciones = new TableColumn<>("Acciones");
        colAcciones.setMaxWidth(220);
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnVer = new Button("Ver Ficha");
            private final Button btnEditar = new Button("Editar");
            private final HBox box = new HBox(8, btnVer, btnEditar);

            {
                box.setAlignment(Pos.CENTER_LEFT);
                btnVer.getStyleClass().addAll("btn-secondary", "btn-sm");
                btnEditar.getStyleClass().addAll("btn-secondary", "btn-sm");

                btnVer.setOnAction(e -> {
                    Cliente c = getTableView().getItems().get(getIndex());
                    if (viewRouter != null && c != null) {
                        viewRouter.navigate(NavAction.ROUTE_CLIENTE_DETAIL, c.getId());
                    }
                });

                btnEditar.setOnAction(e -> {
                    Cliente c = getTableView().getItems().get(getIndex());
                    if (c != null) {
                        openEditarClienteDialog(c);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : box);
            }
        });

        table.getColumns().setAll(List.of(colId, colNombre, colDocumento, colTelefono, colAcciones));

        table.setRowFactory(tv -> {
            TableRow<Cliente> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getClickCount() == 2 && viewRouter != null) {
                    viewRouter.navigate(NavAction.ROUTE_CLIENTE_DETAIL, row.getItem().getId());
                }
            });
            return row;
        });

        return table;
    }

    @Override
    public void onShow(int currentDataVersion) {
        loadData();
    }

    public void loadData() {
        filterClientes(searchField.getText());
    }

    public void filterClientes(String query) {
        try {
            List<Cliente> resultados = clienteService.buscarClientes(query);
            itemsClientes.setAll(resultados);
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al consultar clientes: " + ex.getMessage(), ToastNotification.Type.ERROR);
            }
        }
    }

    private void openNuevoClienteDialog() {
        Window owner = getScene() != null ? getScene().getWindow() : null;
        ClienteDialog dialog = new ClienteDialog(owner, clienteService);
        Optional<Cliente> creado = dialog.showAndWait();
        creado.ifPresent(c -> {
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            } else {
                loadData();
            }
            if (notifier != null) {
                notifier.accept("Cliente registrado exitosamente", ToastNotification.Type.SUCCESS);
            }
        });
    }

    private void openEditarClienteDialog(Cliente cliente) {
        Window owner = getScene() != null ? getScene().getWindow() : null;
        ClienteDialog dialog = new ClienteDialog(owner, clienteService, cliente);
        Optional<Cliente> actualizado = dialog.showAndWait();
        actualizado.ifPresent(c -> {
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            } else {
                loadData();
            }
            if (notifier != null) {
                notifier.accept("Cliente actualizado exitosamente", ToastNotification.Type.SUCCESS);
            }
        });
    }

    public TextField getSearchField() {
        return searchField;
    }

    public TableView<Cliente> getTablaClientes() {
        return tablaClientes;
    }

    public ObservableList<Cliente> getItemsClientes() {
        return itemsClientes;
    }
}
