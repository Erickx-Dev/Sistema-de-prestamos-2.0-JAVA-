package com.credinoda.ui.components;

import com.credinoda.ui.navigation.NavAction;
import com.credinoda.ui.navigation.NavigationBar;
import javafx.application.Platform;
import javafx.util.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas unitarias para los componentes visuales reutilizables de la Fase 5:
 * {@link MetricCard}, {@link SectionHeader}, {@link ToastNotification} y {@link NavigationBar}.
 */
class MetricCardTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        try {
            Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
            // El toolkit de JavaFX ya fue inicializado previamente en la JVM
        }
    }

    @Test
    @DisplayName("MetricCard normaliza el título en mayúsculas, aplica la variante de acento y permite actualizar el valor reactivamente")
    void debeCrearYActualizarMetricCardCorrectamente() {
        MetricCard card = new MetricCard("Capital Prestado", "$1.000.000", "💰",
                MetricCard.AccentVariant.SUCCESS, "Cartera activa");

        assertThat(card.getTitle()).isEqualTo("CAPITAL PRESTADO");
        assertThat(card.getValue()).isEqualTo("$1.000.000");
        assertThat(card.getIcon()).isEqualTo("💰");
        assertThat(card.getSubtitle()).isEqualTo("Cartera activa");
        assertThat(card.getAccentVariant()).isEqualTo(MetricCard.AccentVariant.SUCCESS);
        assertThat(card.getStyleClass()).contains("metric-card", "metric-accent-success");

        // Actualización reactiva de valores y variante
        card.setValue("$2.500.000");
        card.setTitle("Saldo Pendiente");
        card.setSubtitle("Actualizado hoy");
        card.setAccentVariant(MetricCard.AccentVariant.DANGER);

        assertThat(card.getValue()).isEqualTo("$2.500.000");
        assertThat(card.getTitle()).isEqualTo("SALDO PENDIENTE");
        assertThat(card.getSubtitle()).isEqualTo("Actualizado hoy");
        assertThat(card.getAccentVariant()).isEqualTo(MetricCard.AccentVariant.DANGER);
        assertThat(card.getStyleClass())
                .contains("metric-accent-danger")
                .doesNotContain("metric-accent-success");
    }

    @Test
    @DisplayName("SectionHeader gestiona título, subtítulo opcional y botón de acción derecho")
    void debeConfigurarSectionHeaderConAccionYSubtitulo() {
        final boolean[] clicked = {false};
        SectionHeader header = new SectionHeader(
                "Clientes",
                "Directorio de prestatarios",
                "+ Nuevo Cliente",
                () -> clicked[0] = true
        );

        assertThat(header.getTitle()).isEqualTo("Clientes");
        assertThat(header.getSubtitle()).isEqualTo("Directorio de prestatarios");
        assertThat(header.getPrimaryActionButton()).isNotNull();
        assertThat(header.getPrimaryActionButton().getText()).isEqualTo("+ Nuevo Cliente");

        header.getPrimaryActionButton().fire();
        assertThat(clicked[0]).isTrue();

        // Ocultar subtítulo cuando es nulo o vacío
        header.setSubtitle("");
        assertThat(header.getSubtitle()).isEmpty();
    }

    @Test
    @DisplayName("ToastNotification alterna visibilidad y clases CSS según el tipo de notificación sin reservar espacio cuando está oculta")
    void debeMostrarYOcultarToastNotificationConClasesSemantica() {
        ToastNotification toast = new ToastNotification();

        assertThat(toast.isVisible()).isFalse();
        assertThat(toast.isManaged()).isFalse();

        toast.show("Pago registrado exitosamente", ToastNotification.Type.SUCCESS, Duration.ZERO);
        assertThat(toast.isVisible()).isTrue();
        assertThat(toast.isManaged()).isTrue();
        assertThat(toast.getMessage()).isEqualTo("Pago registrado exitosamente");
        assertThat(toast.getCurrentType()).isEqualTo(ToastNotification.Type.SUCCESS);
        assertThat(toast.getStyleClass()).contains("toast-success");

        toast.show("El monto excede el saldo pendiente", ToastNotification.Type.ERROR, Duration.ZERO);
        assertThat(toast.getCurrentType()).isEqualTo(ToastNotification.Type.ERROR);
        assertThat(toast.getStyleClass())
                .contains("toast-error")
                .doesNotContain("toast-success");

        toast.hide();
        assertThat(toast.isVisible()).isFalse();
        assertThat(toast.isManaged()).isFalse();
    }

    @Test
    @DisplayName("NavigationBar superior horizontal tiene altura fija de 56px y contiene estrictamente las 6 opciones reales del sistema")
    void navigationBarDebeTenerAltura56YSeisOpcionesReales() {
        NavigationBar navBar = new NavigationBar();

        assertThat(navBar.getPrefHeight()).isEqualTo(NavigationBar.FIXED_HEIGHT);
        assertThat(navBar.getMinHeight()).isEqualTo(NavigationBar.FIXED_HEIGHT);
        assertThat(navBar.getMaxHeight()).isEqualTo(NavigationBar.FIXED_HEIGHT);
        assertThat(navBar.getButtons()).hasSize(6);
        assertThat(navBar.getButtons().keySet()).containsExactlyInAnyOrder(
                NavAction.DASHBOARD,
                NavAction.CLIENTES,
                NavAction.CREDITO_NEW,
                NavAction.PAGOS,
                NavAction.REPORTES,
                NavAction.BACKUP
        );
    }

    @Test
    @DisplayName("NavigationBar adapta visibilidad de botones según rol: Administrador (7 opciones) vs Operador (5 opciones)")
    void navigationBarDebeAdaptarVisibilidadSegunRol() {
        NavigationBar navBar = new NavigationBar();

        // Rol Administrador: Usuarios y Backup visibles
        navBar.aplicarPermisos(com.credinoda.domain.model.Rol.ADMINISTRADOR);
        assertThat(navBar.getUsuariosButton().isVisible()).isTrue();
        assertThat(navBar.getUsuariosButton().isManaged()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.BACKUP).isVisible()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.BACKUP).isManaged()).isTrue();

        // Rol Operador: Usuarios y Backup ocultos
        navBar.aplicarPermisos(com.credinoda.domain.model.Rol.OPERADOR);
        assertThat(navBar.getUsuariosButton().isVisible()).isFalse();
        assertThat(navBar.getUsuariosButton().isManaged()).isFalse();
        assertThat(navBar.getButtons().get(NavAction.BACKUP).isVisible()).isFalse();
        assertThat(navBar.getButtons().get(NavAction.BACKUP).isManaged()).isFalse();

        // Operaciones core visibles para Operador
        assertThat(navBar.getButtons().get(NavAction.DASHBOARD).isVisible()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.CLIENTES).isVisible()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.CREDITO_NEW).isVisible()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.PAGOS).isVisible()).isTrue();
        assertThat(navBar.getButtons().get(NavAction.REPORTES).isVisible()).isTrue();
    }
}
