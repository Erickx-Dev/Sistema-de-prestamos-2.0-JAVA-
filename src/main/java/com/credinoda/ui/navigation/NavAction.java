package com.credinoda.ui.navigation;

import java.util.Locale;
import java.util.Optional;

/**
 * Representa las 6 opciones reales de la Barra de Navegación Persistente de CrediNoda 2.0
 * y las claves canónicas de enrutamiento hacia vistas principales y subvistas contextuales.
 */
public enum NavAction {

    DASHBOARD("dashboard", "Inicio", "🏠", false),
    CLIENTES("clientes", "Clientes", "👥", false),
    CREDITO_NEW("credito_new", "Nuevo Crédito", "💳", false),
    PAGOS("pagos", "Pagos", "💵", false),
    REPORTES("reportes", "Reportes", "📊", false),
    BACKUP("backup", "Copia de Seguridad", "💾", true);

    /** Clave de ruta para la subvista de ficha detallada de cliente. */
    public static final String ROUTE_CLIENTE_DETAIL = "cliente_detail";

    /** Clave de ruta para la subvista de ficha detallada de crédito. */
    public static final String ROUTE_CREDITO_DETAIL = "credito_detail";

    /** Clave de ruta para la vista de gestión y administración de usuarios. */
    public static final String ROUTE_USUARIOS = "usuarios";

    private final String routeKey;
    private final String label;
    private final String icon;
    private final boolean actionOnly;

    NavAction(String routeKey, String label, String icon, boolean actionOnly) {
        this.routeKey = routeKey;
        this.label = label;
        this.icon = icon;
        this.actionOnly = actionOnly;
    }

    public String getRouteKey() {
        return routeKey;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Indica si la opción ejecuta una acción modal (como Copia de Seguridad)
     * en lugar de reemplazar la vista activa en el ContentArea.
     */
    public boolean isActionOnly() {
        return actionOnly;
    }

    /**
     * Busca la opción de navegación a partir de su clave de ruta.
     */
    public static Optional<NavAction> fromRouteKey(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        String normalized = key.strip().toLowerCase(Locale.ROOT);
        for (NavAction action : values()) {
            if (action.routeKey.equals(normalized)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    /**
     * Resuelve cuál botón de la barra de navegación debe permanecer resaltado como activo
     * para una clave de ruta dada (incluyendo subvistas contextuales).
     */
    public static Optional<NavAction> resolveHighlightedNav(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            return Optional.empty();
        }
        String normalized = routeKey.strip().toLowerCase(Locale.ROOT);
        if (ROUTE_CLIENTE_DETAIL.equals(normalized)) {
            return Optional.of(CLIENTES);
        }
        if (ROUTE_CREDITO_DETAIL.equals(normalized)) {
            return Optional.of(DASHBOARD);
        }
        Optional<NavAction> direct = fromRouteKey(normalized);
        if (direct.isPresent() && !direct.get().isActionOnly()) {
            return direct;
        }
        return Optional.empty();
    }
}
