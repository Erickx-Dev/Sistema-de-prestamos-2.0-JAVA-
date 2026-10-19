package com.credinoda.ui.navigation;

import com.credinoda.domain.model.Rol;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Barra de Navegación Superior Horizontal (Navbar, 56px de alto) de CrediNoda 2.0.
 * Diseñada según la pantalla 'DashboardView - Inicio (Navbar Superior)' de Google Stitch:
 * Contiene en disposición horizontal la identidad de marca interactiva a la izquierda (con logout),
 * los módulos principales según el rol activo (Inicio, Clientes, Nuevo Crédito, Pagos, Reportes, Usuarios)
 * y la acción de Copia de Seguridad anclada en el extremo derecho.
 */
public class NavigationBar extends HBox {

    public static final double FIXED_HEIGHT = 56.0;
    public static final String ACTIVE_STYLE_CLASS = "nav-button-active";

    private final Map<NavAction, Button> buttons = new EnumMap<>(NavAction.class);
    private Button usuariosButton;
    private Region rightDivider;
    private Consumer<NavAction> onActionSelected;
    private Consumer<String> onRouteSelected;
    private Runnable onLogoutRequested;
    private NavAction activeAction;
    private String activeRouteKey;

    public NavigationBar() {
        this(null);
    }

    public NavigationBar(Consumer<NavAction> onActionSelected) {
        this.onActionSelected = onActionSelected;
        initializeLayout();
    }

    private void initializeLayout() {
        getStyleClass().add("nav-bar");
        setPrefHeight(FIXED_HEIGHT);
        setMinHeight(FIXED_HEIGHT);
        setMaxHeight(FIXED_HEIGHT);
        setMaxWidth(Double.MAX_VALUE);
        setAlignment(Pos.CENTER_LEFT);

        // 1. Bloque de Marca a la izquierda: Isotipo "C" + CrediNoda 2.0 interactivo para logout
        HBox brandBox = buildBrandHeader();

        // 2. Divisor vertical sutil
        Region leftDivider = createVerticalDivider();

        // 3. Menú horizontal de los módulos principales
        HBox navItemsBox = new HBox(6);
        navItemsBox.getStyleClass().add("nav-menu-items");
        navItemsBox.setAlignment(Pos.CENTER_LEFT);
        for (NavAction action : NavAction.values()) {
            if (!action.isActionOnly()) {
                Button btn = createNavButton(action);
                buttons.put(action, btn);
                navItemsBox.getChildren().add(btn);
            }
        }

        // Botón especial para el módulo de Usuarios (visible para Administrador)
        this.usuariosButton = new Button("👤  Usuarios");
        this.usuariosButton.getStyleClass().add("nav-button");
        this.usuariosButton.setAlignment(Pos.CENTER);
        this.usuariosButton.setOnAction(e -> handleRouteClick(NavAction.ROUTE_USUARIOS));
        navItemsBox.getChildren().add(usuariosButton);

        // 4. Espaciador horizontal elástico para anclar Copia de Seguridad a la derecha
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 5. Divisor vertical derecho y botón de acción Copia de Seguridad
        this.rightDivider = createVerticalDivider();
        Button backupButton = createNavButton(NavAction.BACKUP);
        backupButton.getStyleClass().add("nav-button-backup");
        buttons.put(NavAction.BACKUP, backupButton);

        getChildren().addAll(brandBox, leftDivider, navItemsBox, spacer, rightDivider, backupButton);
    }

    private HBox buildBrandHeader() {
        HBox brandBox = new HBox(10);
        brandBox.getStyleClass().addAll("nav-brand", "nav-brand-clickable");
        brandBox.setAlignment(Pos.CENTER_LEFT);

        Label logoBadge = new Label("C");
        logoBadge.getStyleClass().add("nav-logo-badge");

        VBox titlesBox = new VBox(1);
        titlesBox.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("CrediNoda 2.0");
        titleLabel.getStyleClass().add("nav-brand-title");

        Label subtitleLabel = new Label("Gestión de Créditos");
        subtitleLabel.getStyleClass().add("nav-brand-subtitle");

        titlesBox.getChildren().addAll(titleLabel, subtitleLabel);
        brandBox.getChildren().addAll(logoBadge, titlesBox);

        Tooltip.install(brandBox, new Tooltip("Haga clic para cerrar la sesión activa"));
        brandBox.setOnMouseClicked(e -> {
            if (onLogoutRequested != null) {
                onLogoutRequested.run();
            }
        });

        return brandBox;
    }

    private Region createVerticalDivider() {
        Region divider = new Region();
        divider.getStyleClass().add("nav-divider");
        return divider;
    }

    private Button createNavButton(NavAction action) {
        String text = action.getIcon() + "  " + action.getLabel();
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setAlignment(Pos.CENTER);
        btn.setOnAction(e -> handleActionClick(action));
        return btn;
    }

    private void handleActionClick(NavAction action) {
        if (!action.isActionOnly()) {
            setActiveAction(action);
        }
        if (onActionSelected != null) {
            onActionSelected.accept(action);
        }
    }

    private void handleRouteClick(String routeKey) {
        setActiveRoute(routeKey);
        if (onRouteSelected != null) {
            onRouteSelected.accept(routeKey);
        }
    }

    /**
     * Aplica la visibilidad de opciones en la barra según el rol del usuario autenticado (RN-024, RN-026).
     *
     * @param rol Rol del usuario autenticado.
     */
    public void aplicarPermisos(Rol rol) {
        boolean esAdmin = (rol == Rol.ADMINISTRADOR);

        if (usuariosButton != null) {
            usuariosButton.setVisible(esAdmin);
            usuariosButton.setManaged(esAdmin);
        }

        Button backupBtn = buttons.get(NavAction.BACKUP);
        if (backupBtn != null) {
            backupBtn.setVisible(esAdmin);
            backupBtn.setManaged(esAdmin);
        }

        if (rightDivider != null) {
            rightDivider.setVisible(esAdmin);
            rightDivider.setManaged(esAdmin);
        }
    }

    public void setOnActionSelected(Consumer<NavAction> onActionSelected) {
        this.onActionSelected = onActionSelected;
    }

    public void setOnRouteSelected(Consumer<String> onRouteSelected) {
        this.onRouteSelected = onRouteSelected;
    }

    public void setOnLogoutRequested(Runnable onLogoutRequested) {
        this.onLogoutRequested = onLogoutRequested;
    }

    public void setActiveAction(NavAction action) {
        if (action != null && action.isActionOnly()) {
            return;
        }
        this.activeAction = action;
        this.activeRouteKey = action != null ? action.getRouteKey() : null;

        if (usuariosButton != null) {
            usuariosButton.getStyleClass().remove(ACTIVE_STYLE_CLASS);
        }

        for (Map.Entry<NavAction, Button> entry : buttons.entrySet()) {
            Button btn = entry.getValue();
            btn.getStyleClass().remove(ACTIVE_STYLE_CLASS);
            if (entry.getKey() == action) {
                btn.getStyleClass().add(ACTIVE_STYLE_CLASS);
            }
        }
    }

    public void setActiveRoute(String routeKey) {
        this.activeRouteKey = routeKey;
        this.activeAction = null;

        // Limpiar estilos de botones de acción canónicos
        for (Button btn : buttons.values()) {
            btn.getStyleClass().remove(ACTIVE_STYLE_CLASS);
        }

        if (NavAction.ROUTE_USUARIOS.equals(routeKey) && usuariosButton != null) {
            usuariosButton.getStyleClass().add(ACTIVE_STYLE_CLASS);
        } else {
            if (usuariosButton != null) {
                usuariosButton.getStyleClass().remove(ACTIVE_STYLE_CLASS);
            }
            NavAction.resolveHighlightedNav(routeKey).ifPresent(this::setActiveAction);
        }
    }

    public NavAction getActiveAction() {
        return activeAction;
    }

    public String getActiveRouteKey() {
        return activeRouteKey;
    }

    public Button getButton(NavAction action) {
        Objects.requireNonNull(action, "NavAction no puede ser nulo");
        return buttons.get(action);
    }

    public Button getUsuariosButton() {
        return usuariosButton;
    }

    public Map<NavAction, Button> getButtons() {
        return Map.copyOf(buttons);
    }
}
