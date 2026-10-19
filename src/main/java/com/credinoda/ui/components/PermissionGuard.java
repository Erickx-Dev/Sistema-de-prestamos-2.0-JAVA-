package com.credinoda.ui.components;

import com.credinoda.domain.model.Permiso;
import com.credinoda.service.AutorizacionService;
import com.credinoda.service.Autorizador;
import javafx.scene.Node;

import java.util.Objects;

/**
 * Utilidad gráfica para aplicar control de acceso declarativo sobre nodos de la interfaz (RN-024, RN-026).
 * Modifica las propiedades visible y managed de los componentes de acuerdo con los permisos del usuario activo.
 */
public final class PermissionGuard {

    private static Autorizador autorizador = new AutorizacionService();

    private PermissionGuard() {
        // Clase de utilidades UI, no instanciable
    }

    public static void setAutorizador(Autorizador customAutorizador) {
        autorizador = Objects.requireNonNull(customAutorizador, "El autorizador no puede ser nulo");
    }

    /**
     * Aplica la visibilidad de un nodo JavaFX según si el usuario activo cuenta con el permiso requerido.
     * Si no cuenta con el permiso, el nodo se oculta y no ocupa espacio en el layout (setManaged(false)).
     *
     * @param node Nodo JavaFX a proteger.
     * @param permiso Permiso requerido.
     */
    public static void apply(Node node, Permiso permiso) {
        if (node == null || permiso == null) {
            return;
        }
        boolean permitido = autorizador.hasPermission(permiso);
        node.setVisible(permitido);
        node.setManaged(permitido);
    }

    /**
     * Evalúa directamente si el usuario activo posee el permiso indicado.
     *
     * @param permiso Permiso consultado.
     * @return true si cuenta con el permiso.
     */
    public static boolean hasPermission(Permiso permiso) {
        return autorizador.hasPermission(permiso);
    }
}
