package com.credinoda.ui.navigation;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Barra de Navegación Persistente lateral izquierda (220px) de CrediNoda 2.0.
 * Contiene exclusivamente la identidad de marca, los 5 módulos principales de vista
 * (Inicio, Clientes, Nuevo Crédito, Pagos, Reportes) y la acción inferior de Copia de Seguridad.
 */
public class NavigationBar extends VBox {

    public static final double FIXED_WIDTH = 220.0;
    public static final String ACTIVE_STYLE_CLASS = "nav-button-active";

    private final Map<NavAction, Button> buttons = new EnumMap<>(NavAction.class);
    private Consumer<NavAction> onActionSelected;
    private NavAction activeAction;

    public NavigationBar() {
        this(null);
    }

    public NavigationBar(Consumer<NavAction> onActionSelected) {
        this.onActionSelected = onActionSelected;
        initializeLayout();
    }

    private void initializeLayout() {
        getStyleClass().add("nav-bar");
        setPrefWidth(FIXED_WIDTH);
        setMinWidth(FIXED_WIDTH);
        setMaxWidth(FIXED_WIDTH);
        setFillWidth(true);

        // 1. Cabecera de Marca: CrediNoda 2.0
        HBox brandBox = buildBrandHeader();

        // 2. Separador superior
        Region topSeparator = createSeparator();

        // 3. Opciones de navegación de módulos principales
        VBox navItemsBox = new VBox(6);
        navItemsBox.setFillWidth(true);
        for (NavAction action : NavAction.values()) {
            if (!action.isActionOnly()) {
                Button btn = createNavButton(action);
                buttons.put(action, btn);
                navItemsBox.getChildren().add(btn);
            }
        }

        // 4. Espaciador elástico para anclar Copia de Seguridad al fondo
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // 5. Separador inferior y botón de Copia de Seguridad
        Region bottomSeparator = createSeparator();
        Button backupButton = createNavButton(NavAction.BACKUP);
        backupButton.getStyleClass().add("nav-button-backup");
        buttons.put(NavAction.BACKUP, backupButton);

        getChildren().addAll(brandBox, topSeparator, navItemsBox, spacer, bottomSeparator, backupButton);
    }

    private HBox buildBrandHeader() {
        HBox brandBox = new HBox(10);
        brandBox.getStyleClass().add("nav-brand");
        brandBox.setAlignment(Pos.CENTER_LEFT);

        Label logoBadge = new Label("C");
        logoBadge.getStyleClass().add("nav-logo-badge");

        VBox titlesBox = new VBox(1);
        Label titleLabel = new Label("CrediNoda 2.0");
        titleLabel.getStyleClass().add("nav-brand-title");

        Label subtitleLabel = new Label("Gestión de Créditos");
        subtitleLabel.getStyleClass().add("nav-brand-subtitle");

        titlesBox.getChildren().addAll(titleLabel, subtitleLabel);
        brandBox.getChildren().addAll(logoBadge, titlesBox);
        return brandBox;
    }

    private Region createSeparator() {
        Region sep = new Region();
        sep.getStyleClass().add("nav-separator");
        return sep;
    }

    private Button createNavButton(NavAction action) {
        String text = action.getIcon() + "  " + action.getLabel();
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
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

    /**
     * Establece el manejador invocado cuando el usuario pulsa una opción de la barra.
     */
    public void setOnActionSelected(Consumer<NavAction> onActionSelected) {
        this.onActionSelected = onActionSelected;
    }

    /**
     * Marca visualmente como activo el botón correspondiente a la acción indicada.
     */
    public void setActiveAction(NavAction action) {
        if (action != null && action.isActionOnly()) {
            return;
        }
        this.activeAction = action;
        for (Map.Entry<NavAction, Button> entry : buttons.entrySet()) {
            Button btn = entry.getValue();
            btn.getStyleClass().remove(ACTIVE_STYLE_CLASS);
            if (entry.getKey() == action) {
                btn.getStyleClass().add(ACTIVE_STYLE_CLASS);
            }
        }
    }

    public NavAction getActiveAction() {
        return activeAction;
    }

    public Button getButton(NavAction action) {
        Objects.requireNonNull(action, "NavAction no puede ser nulo");
        return buttons.get(action);
    }

    public Map<NavAction, Button> getButtons() {
        return Map.copyOf(buttons);
    }
}
