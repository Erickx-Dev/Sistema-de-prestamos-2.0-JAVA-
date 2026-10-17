package com.credinoda.ui.navigation;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Coordinador central de enrutamiento de vistas y control de invalidación de caché por {@code dataVersion}.
 * Administra exclusivamente el nodo activo en el {@code ContentArea}, sincroniza el botón activo en
 * {@link NavigationBar} y verifica automáticamente el cambio de día (RN-008) al alternar vistas.
 */
public class ViewRouter {

    /**
     * Contrato opcional para las vistas que requieren refrescar su estado cuando
     * se muestran en el {@code ContentArea} y {@code dataVersion} ha cambiado.
     */
    @FunctionalInterface
    public interface RoutableView {
        /**
         * Invocado por el {@link ViewRouter} cuando la vista se monta y requiere sincronizar datos.
         *
         * @param currentDataVersion Versión actual de los datos del sistema.
         */
        void onShow(int currentDataVersion);
    }

    private final StackPane contentArea;
    private final NavigationBar navigationBar;

    private final Map<String, Supplier<? extends Node>> cachedViewFactories = new HashMap<>();
    private final Map<String, Node> cachedViews = new HashMap<>();
    private final Map<String, Integer> lastSyncedVersion = new HashMap<>();
    private final Map<String, Function<Object, ? extends Node>> dynamicViewFactories = new HashMap<>();

    private Runnable backupActionHandler;
    private Supplier<LocalDate> dateSupplier = LocalDate::now;
    private Function<LocalDate, Integer> dayRolloverMoraHandler;
    private LocalDate lastCheckedDate;

    private int dataVersion = 1;
    private String currentRouteKey;
    private Object currentRouteParam;

    public ViewRouter(StackPane contentArea, NavigationBar navigationBar) {
        this.contentArea = Objects.requireNonNull(contentArea, "El contenedor ContentArea no puede ser nulo");
        this.navigationBar = navigationBar;
        this.lastCheckedDate = dateSupplier.get();

        if (this.navigationBar != null) {
            this.navigationBar.setOnActionSelected(this::navigate);
        }
    }

    /**
     * Registra una instancia de vista en caché asociada a una acción de navegación.
     */
    public void registerView(NavAction action, Node viewNode) {
        Objects.requireNonNull(action, "NavAction no puede ser nulo");
        Objects.requireNonNull(viewNode, "El nodo de la vista no puede ser nulo");
        registerView(action.getRouteKey(), () -> viewNode);
    }

    /**
     * Registra un proveedor perezoso para una vista en caché asociada a una acción de navegación.
     */
    public void registerView(NavAction action, Supplier<? extends Node> viewFactory) {
        Objects.requireNonNull(action, "NavAction no puede ser nulo");
        registerView(action.getRouteKey(), viewFactory);
    }

    /**
     * Registra una vista en caché por su clave de ruta.
     */
    public void registerView(String routeKey, Node viewNode) {
        Objects.requireNonNull(viewNode, "El nodo de la vista no puede ser nulo");
        registerView(routeKey, () -> viewNode);
    }

    /**
     * Registra una fábrica para una vista mantenida en caché en memoria.
     */
    public void registerView(String routeKey, Supplier<? extends Node> viewFactory) {
        String key = normalizeKey(routeKey);
        Objects.requireNonNull(viewFactory, "La fábrica de vista no puede ser nula");
        cachedViewFactories.put(key, viewFactory);
        cachedViews.remove(key);
        lastSyncedVersion.remove(key);
    }

    /**
     * Registra una fábrica para vistas dinámicas o parametrizadas (ej. detalle de cliente o crédito).
     * Estas vistas se construyen o configuran con el parámetro recibido en cada navegación.
     */
    public void registerDynamicView(String routeKey, Function<Object, ? extends Node> dynamicFactory) {
        String key = normalizeKey(routeKey);
        Objects.requireNonNull(dynamicFactory, "La fábrica de vista dinámica no puede ser nula");
        dynamicViewFactories.put(key, dynamicFactory);
    }

    /**
     * Configura la acción a ejecutar cuando el usuario selecciona "Copia de Seguridad".
     */
    public void setBackupActionHandler(Runnable backupActionHandler) {
        this.backupActionHandler = backupActionHandler;
    }

    /**
     * Configura el manejador de sincronización de mora ante cambio de fecha del sistema (RN-008).
     *
     * @param dateSupplier           Proveedor de la fecha actual (por defecto {@code LocalDate::now}).
     * @param dayRolloverMoraHandler Función que recibe la nueva fecha y retorna el número de cuotas actualizadas.
     */
    public void configureDayRolloverCheck(Supplier<LocalDate> dateSupplier,
                                          Function<LocalDate, Integer> dayRolloverMoraHandler) {
        this.dateSupplier = Objects.requireNonNull(dateSupplier, "dateSupplier no puede ser nulo");
        this.dayRolloverMoraHandler = dayRolloverMoraHandler;
        this.lastCheckedDate = this.dateSupplier.get();
    }

    /**
     * Navega hacia una de las 6 opciones de {@link NavAction}.
     */
    public void navigate(NavAction action) {
        Objects.requireNonNull(action, "NavAction no puede ser nulo");
        if (action.isActionOnly()) {
            if (action == NavAction.BACKUP && backupActionHandler != null) {
                backupActionHandler.run();
            }
            return;
        }
        navigate(action.getRouteKey(), null);
    }

    /**
     * Navega hacia una vista registrada por su clave de ruta sin parámetros.
     */
    public void navigate(String routeKey) {
        navigate(routeKey, null);
    }

    /**
     * Navega hacia una vista registrada por su clave de ruta pasando un parámetro opcional
     * (p. ej. {@code clienteId} o {@code creditoId}).
     */
    public void navigate(String routeKey, Object param) {
        String key = normalizeKey(routeKey);

        Optional<NavAction> navActionOpt = NavAction.fromRouteKey(key);
        if (navActionOpt.isPresent() && navActionOpt.get().isActionOnly()) {
            navigate(navActionOpt.get());
            return;
        }

        // 1. Verificar cambio de día antes de renderizar la vista (RN-008)
        checkDayRollover();

        // 2. Resolver el nodo de la vista (dinámica con parámetro o en caché)
        Node targetNode;
        boolean isDynamic = false;

        if (param != null && dynamicViewFactories.containsKey(key)) {
            targetNode = dynamicViewFactories.get(key).apply(param);
            isDynamic = true;
        } else if (cachedViewFactories.containsKey(key)) {
            targetNode = cachedViews.computeIfAbsent(key, k -> cachedViewFactories.get(k).get());
        } else if (dynamicViewFactories.containsKey(key)) {
            targetNode = dynamicViewFactories.get(key).apply(param);
            isDynamic = true;
        } else {
            throw new IllegalArgumentException("Ruta de vista no registrada en ViewRouter: " + routeKey);
        }

        Objects.requireNonNull(targetNode, "La vista generada para la ruta '" + key + "' no puede ser nula");

        // 3. Montar exclusivamente la vista activa en el ContentArea
        contentArea.getChildren().setAll(targetNode);
        currentRouteKey = key;
        currentRouteParam = param;

        // 4. Sincronizar barra de navegación
        if (navigationBar != null) {
            NavAction.resolveHighlightedNav(key).ifPresent(navigationBar::setActiveAction);
        }

        // 5. Disparar ciclo de vida onShow solo si es dinámica o si dataVersion avanzó respecto a la caché
        if (targetNode instanceof RoutableView routable) {
            if (isDynamic) {
                routable.onShow(dataVersion);
            } else {
                Integer lastVersion = lastSyncedVersion.get(key);
                if (lastVersion == null || lastVersion < dataVersion) {
                    routable.onShow(dataVersion);
                    lastSyncedVersion.put(key, dataVersion);
                }
            }
        }
    }

    /**
     * Incrementa el contador global {@code dataVersion} para invalidar las vistas en caché
     * tras una mutación de datos y refresca inmediatamente la vista activa si aplica.
     */
    public int notifyDataChanged() {
        dataVersion++;
        refreshActiveViewIfNeeded();
        return dataVersion;
    }

    /**
     * Verifica si la fecha del sistema ha cambiado respecto a la última comprobación (RN-008).
     * Si cambió el día, ejecuta la actualización de mora e incrementa {@code dataVersion}.
     *
     * @return {@code true} si se detectó cambio de día, {@code false} en caso contrario.
     */
    public boolean checkDayRollover() {
        LocalDate today = dateSupplier.get();
        if (lastCheckedDate == null) {
            lastCheckedDate = today;
            return false;
        }
        if (!today.equals(lastCheckedDate)) {
            lastCheckedDate = today;
            if (dayRolloverMoraHandler != null) {
                dayRolloverMoraHandler.apply(today);
            }
            dataVersion++;
            return true;
        }
        return false;
    }

    private void refreshActiveViewIfNeeded() {
        if (contentArea.getChildren().isEmpty() || currentRouteKey == null) {
            return;
        }
        Node activeNode = contentArea.getChildren().getFirst();
        if (activeNode instanceof RoutableView routable) {
            routable.onShow(dataVersion);
            if (cachedViews.containsKey(currentRouteKey) && cachedViews.get(currentRouteKey) == activeNode) {
                lastSyncedVersion.put(currentRouteKey, dataVersion);
            }
        }
    }

    public int getDataVersion() {
        return dataVersion;
    }

    public String getCurrentRouteKey() {
        return currentRouteKey;
    }

    public Object getCurrentRouteParam() {
        return currentRouteParam;
    }

    public Node getActiveView() {
        return contentArea.getChildren().isEmpty() ? null : contentArea.getChildren().getFirst();
    }

    public boolean isViewCached(String routeKey) {
        return cachedViews.containsKey(normalizeKey(routeKey));
    }

    private static String normalizeKey(String routeKey) {
        if (routeKey == null || routeKey.isBlank()) {
            throw new IllegalArgumentException("La clave de ruta no puede ser nula ni vacía");
        }
        return routeKey.strip().toLowerCase(Locale.ROOT);
    }
}
