package com.credinoda.ui.views;

import com.credinoda.common.Formatters;
import com.credinoda.domain.exception.CrediNodaException;
import com.credinoda.domain.model.Permiso;
import com.credinoda.domain.model.Rol;
import com.credinoda.domain.model.Usuario;
import com.credinoda.service.UsuarioService;
import com.credinoda.ui.auth.CambiarContrasenaDialog;
import com.credinoda.ui.components.ConfirmDialog;
import com.credinoda.ui.components.PermissionGuard;
import com.credinoda.ui.components.SectionHeader;
import com.credinoda.ui.components.TableColumnHelper;
import com.credinoda.ui.components.ToastNotification;
import com.credinoda.ui.components.UsuarioDialog;
import com.credinoda.ui.navigation.ViewRouter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Vista de Administración y Gestión de Usuarios y Roles (CU-018, CU-019, CU-020, RN-022, RN-024, RN-025).
 * Permite listar operadores y administradores, registrar nuevas cuentas, actualizar privilegios,
 * cambiar contraseñas y desactivar o reactivar credenciales con salvaguarda del último administrador.
 */
public class UsuariosView extends VBox implements ViewRouter.RoutableView {

    private final UsuarioService usuarioService;
    private final ViewRouter viewRouter;
    private final BiConsumer<String, ToastNotification.Type> notifier;

    private final SectionHeader header;
    private final TextField searchField;
    private final ComboBox<String> comboFiltroRol;
    private final ComboBox<String> comboFiltroEstado;
    private final TableView<Usuario> tablaUsuarios;
    private final ObservableList<Usuario> itemsUsuarios = FXCollections.observableArrayList();
    private List<Usuario> todosLosUsuarios = new ArrayList<>();

    public UsuariosView(UsuarioService usuarioService,
                        ViewRouter viewRouter,
                        BiConsumer<String, ToastNotification.Type> notifier) {
        this.usuarioService = Objects.requireNonNull(usuarioService, "UsuarioService no puede ser nulo");
        this.viewRouter = viewRouter;
        this.notifier = notifier;

        getStyleClass().add("view-container");
        setSpacing(18);

        this.header = new SectionHeader(
                "Gestión de Usuarios",
                "Control de accesos, cuentas de operador y privilegios administrativos",
                "+ Nuevo Usuario",
                this::openNuevoUsuarioDialog
        );
        PermissionGuard.apply(header.getPrimaryActionButton(), Permiso.USUARIOS_GESTIONAR);

        // Barra de búsqueda y filtros
        HBox filterBar = new HBox(10);
        filterBar.setAlignment(Pos.CENTER_LEFT);

        this.searchField = new TextField();
        this.searchField.setPromptText("🔍 Buscar usuario por login o nombre completo...");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        this.searchField.textProperty().addListener((obs, o, n) -> aplicarFiltros());

        this.comboFiltroRol = new ComboBox<>();
        this.comboFiltroRol.getItems().addAll("Todos los Roles", "Administradores", "Operadores");
        this.comboFiltroRol.setValue("Todos los Roles");
        this.comboFiltroRol.valueProperty().addListener((obs, o, n) -> aplicarFiltros());

        this.comboFiltroEstado = new ComboBox<>();
        this.comboFiltroEstado.getItems().addAll("Todos los Estados", "Solo Activos", "Solo Inactivos");
        this.comboFiltroEstado.setValue("Todos los Estados");
        this.comboFiltroEstado.valueProperty().addListener((obs, o, n) -> aplicarFiltros());

        Button clearBtn = new Button("Limpiar");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            searchField.clear();
            comboFiltroRol.setValue("Todos los Roles");
            comboFiltroEstado.setValue("Todos los Estados");
        });

        filterBar.getChildren().addAll(searchField, comboFiltroRol, comboFiltroEstado, clearBtn);

        // Contenedor tipo Tarjeta
        VBox tableCard = new VBox(12);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        this.tablaUsuarios = buildTable();
        VBox.setVgrow(tablaUsuarios, Priority.ALWAYS);

        tableCard.getChildren().addAll(filterBar, tablaUsuarios);
        getChildren().addAll(header, tableCard);
    }

    private TableView<Usuario> buildTable() {
        TableView<Usuario> table = new TableView<>(itemsUsuarios);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No se encontraron usuarios registrados."));

        TableColumn<Usuario, String> colId = TableColumnHelper.textoColumn("ID", u -> "#" + u.getId(), Pos.CENTER, 60);
        colId.setMaxWidth(80);

        TableColumn<Usuario, String> colUsername = TableColumnHelper.textoColumn("Usuario", Usuario::getUsername, Pos.CENTER, 130);

        TableColumn<Usuario, String> colNombre = TableColumnHelper.textoColumn("Nombre Completo", Usuario::getNombreCompleto, Pos.CENTER, 200);

        // Columna Rol con Badge
        TableColumn<Usuario, Rol> colRol = new TableColumn<>("Rol");
        colRol.setPrefWidth(130);
        colRol.setMaxWidth(150);
        TableColumnHelper.setHeaderAlignment(colRol, Pos.CENTER);
        colRol.getStyleClass().add("col-center");
        colRol.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getRol()));
        colRol.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                setAlignment(Pos.CENTER);
                getStyleClass().add("cell-center");
            }
            @Override
            protected void updateItem(Rol rol, boolean empty) {
                super.updateItem(rol, empty);
                if (empty || rol == null) {
                    setGraphic(null);
                } else {
                    badge.setText(rol.getNombreVisible());
                    badge.getStyleClass().setAll("badge",
                            rol == Rol.ADMINISTRADOR ? "role-badge-admin" : "role-badge-operador");
                    setGraphic(badge);
                }
            }
        });

        // Columna Estado (Activo / Inactivo) con Badge
        TableColumn<Usuario, Boolean> colEstado = new TableColumn<>("Estado");
        colEstado.setPrefWidth(110);
        colEstado.setMaxWidth(130);
        TableColumnHelper.setHeaderAlignment(colEstado, Pos.CENTER);
        colEstado.getStyleClass().add("col-center");
        colEstado.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().isActivo()));
        colEstado.setCellFactory(col -> new TableCell<>() {
            private final Label badge = new Label();
            {
                setAlignment(Pos.CENTER);
                getStyleClass().add("cell-center");
            }
            @Override
            protected void updateItem(Boolean activo, boolean empty) {
                super.updateItem(activo, empty);
                if (empty || activo == null) {
                    setGraphic(null);
                } else {
                    if (activo) {
                        badge.setText("Activo");
                        badge.getStyleClass().setAll("badge", "status-badge-active");
                    } else {
                        badge.setText("Inactivo");
                        badge.getStyleClass().setAll("badge", "status-badge-inactive");
                    }
                    setGraphic(badge);
                }
            }
        });

        // Columna Seguridad / Bloqueo
        TableColumn<Usuario, Usuario> colSeguridad = new TableColumn<>("Seguridad");
        colSeguridad.setPrefWidth(120);
        colSeguridad.setMaxWidth(140);
        TableColumnHelper.setHeaderAlignment(colSeguridad, Pos.CENTER);
        colSeguridad.getStyleClass().add("col-center");
        colSeguridad.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue()));
        colSeguridad.setCellFactory(col -> new TableCell<>() {
            private final Label lbl = new Label();
            {
                setAlignment(Pos.CENTER);
                getStyleClass().add("cell-center");
            }
            @Override
            protected void updateItem(Usuario user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setGraphic(null);
                } else {
                    if (user.estaBloqueado()) {
                        lbl.setText("Bloqueado");
                        lbl.getStyleClass().setAll("badge", "badge-danger");
                    } else if (user.getIntentosFallidos() > 0) {
                        lbl.setText(user.getIntentosFallidos() + " fallos");
                        lbl.getStyleClass().setAll("badge", "badge-warning");
                    } else {
                        lbl.setText("Normal");
                        lbl.getStyleClass().setAll("text-muted");
                    }
                    setGraphic(lbl);
                }
            }
        });

        // Columna Último Acceso
        TableColumn<Usuario, String> colUltimoAcceso = TableColumnHelper.textoColumn("Último Acceso",
                u -> u.getUltimoAcceso() != null ? Formatters.formatearFechaHora(u.getUltimoAcceso()) : "—",
                Pos.CENTER, 140);
        colUltimoAcceso.setMaxWidth(160);

        // Columna de Acciones
        TableColumn<Usuario, Void> colAcciones = new TableColumn<>("Acciones");
        colAcciones.setPrefWidth(240);
        colAcciones.setMaxWidth(260);
        TableColumnHelper.setHeaderAlignment(colAcciones, Pos.CENTER);
        colAcciones.getStyleClass().add("col-center");
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button btnEditar = new Button("Editar");
            private final Button btnClave = new Button("Clave");
            private final Button btnToggleActivo = new Button();
            private final HBox box = new HBox(8, btnEditar, btnClave, btnToggleActivo);

            {
                setAlignment(Pos.CENTER);
                getStyleClass().add("cell-center");
                box.setAlignment(Pos.CENTER);
                btnEditar.getStyleClass().setAll("button", "btn", "btn-secondary", "btn-sm");
                btnClave.getStyleClass().setAll("button", "btn", "btn-secondary", "btn-sm");
                btnToggleActivo.getStyleClass().setAll("button", "btn", "btn-secondary", "btn-sm");

                btnEditar.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    if (u != null) {
                        openEditarUsuarioDialog(u);
                    }
                });

                btnClave.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    if (u != null) {
                        openCambiarClaveDialog(u);
                    }
                });

                btnToggleActivo.setOnAction(e -> {
                    Usuario u = getTableView().getItems().get(getIndex());
                    if (u != null) {
                        toggleEstadoUsuario(u);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    Usuario u = getTableView().getItems().get(getIndex());
                    if (u != null) {
                        if (u.isActivo()) {
                            btnToggleActivo.setText("Desactivar");
                            btnToggleActivo.getStyleClass().setAll("button", "btn", "btn-danger", "btn-sm");
                        } else {
                            btnToggleActivo.setText("Activar");
                            btnToggleActivo.getStyleClass().setAll("button", "btn", "btn-success-outline", "btn-sm");
                        }
                        PermissionGuard.apply(btnEditar, Permiso.USUARIOS_GESTIONAR);
                        PermissionGuard.apply(btnClave, Permiso.USUARIOS_GESTIONAR);
                        PermissionGuard.apply(btnToggleActivo, Permiso.USUARIOS_GESTIONAR);
                    }
                    setGraphic(box);
                }
            }
        });

        table.getColumns().setAll(List.of(colId, colUsername, colNombre, colRol, colEstado, colSeguridad, colUltimoAcceso, colAcciones));
        return table;
    }

    private void openNuevoUsuarioDialog() {
        UsuarioDialog dialog = new UsuarioDialog(getScene().getWindow(), usuarioService);
        dialog.mostrarYEsperar().ifPresent(usuario -> {
            cargarDatos();
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            if (notifier != null) {
                notifier.accept("Usuario @" + usuario.getUsername() + " registrado exitosamente.", ToastNotification.Type.SUCCESS);
            }
        });
    }

    private void openEditarUsuarioDialog(Usuario usuario) {
        UsuarioDialog dialog = new UsuarioDialog(getScene().getWindow(), usuarioService, usuario);
        dialog.mostrarYEsperar().ifPresent(usuarioActualizado -> {
            cargarDatos();
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            if (notifier != null) {
                notifier.accept("Usuario @" + usuarioActualizado.getUsername() + " actualizado correctamente.", ToastNotification.Type.SUCCESS);
            }
        });
    }

    private void openCambiarClaveDialog(Usuario usuario) {
        CambiarContrasenaDialog dialog = new CambiarContrasenaDialog(getScene().getWindow(), usuarioService, usuario.getId());
        if (dialog.mostrarYEsperar()) {
            cargarDatos();
            if (viewRouter != null) {
                viewRouter.notifyDataChanged();
            }
            if (notifier != null) {
                notifier.accept("Contraseña actualizada para @" + usuario.getUsername(), ToastNotification.Type.SUCCESS);
            }
        }
    }

    private void toggleEstadoUsuario(Usuario usuario) {
        boolean nuevoEstado = !usuario.isActivo();
        String titulo = nuevoEstado ? "Confirmar Activación de Usuario" : "Confirmar Desactivación de Usuario";
        String mensaje = nuevoEstado
                ? "¿Está seguro de activar la cuenta de @" + usuario.getUsername() + "? El usuario podrá volver a iniciar sesión y operar el sistema."
                : "¿Está seguro de desactivar la cuenta de @" + usuario.getUsername() + "? El usuario perderá el acceso al sistema de forma inmediata.";

        ConfirmDialog confirm = new ConfirmDialog(
                getScene().getWindow(),
                titulo,
                mensaje,
                nuevoEstado ? "Activar" : "Desactivar",
                !nuevoEstado
        );

        if (confirm.showAndWait()) {
            try {
                usuarioService.cambiarEstadoActivo(usuario.getId(), nuevoEstado);
                cargarDatos();
                if (viewRouter != null) {
                    viewRouter.notifyDataChanged();
                }
                if (notifier != null) {
                    notifier.accept("Usuario @" + usuario.getUsername() + (nuevoEstado ? " activado con éxito." : " desactivado."),
                            ToastNotification.Type.SUCCESS);
                }
            } catch (CrediNodaException ex) {
                if (notifier != null) {
                    notifier.accept(ex.getMessage(), ToastNotification.Type.ERROR);
                }
            }
        }
    }

    public void cargarDatos() {
        try {
            this.todosLosUsuarios = usuarioService.listarUsuarios();
            aplicarFiltros();
        } catch (Exception ex) {
            if (notifier != null) {
                notifier.accept("Error al cargar usuarios: " + ex.getMessage(), ToastNotification.Type.ERROR);
            }
        }
    }

    private void aplicarFiltros() {
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        String rolFiltro = comboFiltroRol.getValue();
        String estadoFiltro = comboFiltroEstado.getValue();

        List<Usuario> filtrados = todosLosUsuarios.stream()
                .filter(u -> {
                    if (query.isEmpty()) {
                        return true;
                    }
                    boolean matchUsername = u.getUsername().toLowerCase().contains(query);
                    boolean matchNombre = u.getNombreCompleto() != null && u.getNombreCompleto().toLowerCase().contains(query);
                    return matchUsername || matchNombre;
                })
                .filter(u -> {
                    if (rolFiltro == null || "Todos los Roles".equals(rolFiltro)) {
                        return true;
                    }
                    if ("Administradores".equals(rolFiltro)) {
                        return u.getRol() == Rol.ADMINISTRADOR;
                    }
                    if ("Operadores".equals(rolFiltro)) {
                        return u.getRol() == Rol.OPERADOR;
                    }
                    return true;
                })
                .filter(u -> {
                    if (estadoFiltro == null || "Todos los Estados".equals(estadoFiltro)) {
                        return true;
                    }
                    if ("Solo Activos".equals(estadoFiltro)) {
                        return u.isActivo();
                    }
                    if ("Solo Inactivos".equals(estadoFiltro)) {
                        return !u.isActivo();
                    }
                    return true;
                })
                .toList();

        itemsUsuarios.setAll(filtrados);
    }

    @Override
    public void onShow(int currentDataVersion) {
        cargarDatos();
    }

    public TableView<Usuario> getTablaUsuarios() {
        return tablaUsuarios;
    }

    public TextField getSearchField() {
        return searchField;
    }
}
