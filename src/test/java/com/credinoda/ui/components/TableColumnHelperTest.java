package com.credinoda.ui.components;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias para TableColumnHelper")
class TableColumnHelperTest {

    record DummyRow(String nombre, BigDecimal monto, LocalDate fecha, String estado) {}

    @BeforeAll
    static void initJavaFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    @DisplayName("monedaColumn debe configurar clase col-right y formatear montos a la derecha")
    void testMonedaColumn() {
        TableColumn<DummyRow, String> col = TableColumnHelper.monedaColumn(
                "Monto Total",
                DummyRow::monto,
                150
        );

        assertThat(col.getText()).isEqualTo("Monto Total");
        assertThat(col.getPrefWidth()).isEqualTo(150);
        assertThat(col.getStyleClass()).contains("col-right");

        DummyRow row = new DummyRow("Prueba", new BigDecimal("1234500.50"), LocalDate.of(2026, 10, 20), "ACTIVO");
        TableColumn.CellDataFeatures<DummyRow, String> features = new TableColumn.CellDataFeatures<>(null, col, row);
        assertThat(col.getCellValueFactory().call(features).getValue()).isEqualTo("$1.234.500,50");

        TableCell<DummyRow, String> cell = col.getCellFactory().call(col);
        assertThat(cell).isNotNull();
    }

    @Test
    @DisplayName("fechaColumn debe configurar clase col-center y formatear fechas centradas")
    void testFechaColumn() {
        TableColumn<DummyRow, String> col = TableColumnHelper.fechaColumn(
                "Vencimiento",
                DummyRow::fecha,
                130
        );

        assertThat(col.getText()).isEqualTo("Vencimiento");
        assertThat(col.getPrefWidth()).isEqualTo(130);
        assertThat(col.getStyleClass()).contains("col-center");

        DummyRow row = new DummyRow("Prueba", BigDecimal.ZERO, LocalDate.of(2026, 12, 31), "PENDIENTE");
        TableColumn.CellDataFeatures<DummyRow, String> features = new TableColumn.CellDataFeatures<>(null, col, row);
        assertThat(col.getCellValueFactory().call(features).getValue()).isEqualTo("31/12/2026");
    }

    @Test
    @DisplayName("textoColumn debe aplicar alineación y clases correspondientes")
    void testTextoColumn() {
        TableColumn<DummyRow, String> colIzq = TableColumnHelper.textoColumn(
                "Cliente",
                DummyRow::nombre,
                Pos.CENTER_LEFT,
                200
        );
        assertThat(colIzq.getStyleClass()).contains("col-left");

        TableColumn<DummyRow, String> colCentro = TableColumnHelper.textoColumn(
                "Código",
                DummyRow::nombre,
                Pos.CENTER,
                100
        );
        assertThat(colCentro.getStyleClass()).contains("col-center");
    }

    @Test
    @DisplayName("badgeColumn debe aplicar col-center y resolver clases de estilo de badges")
    void testBadgeColumn() {
        TableColumn<DummyRow, String> col = TableColumnHelper.badgeColumn(
                "Estado",
                DummyRow::estado,
                s -> "ACTIVO".equals(s) ? "badge-success" : "badge-danger",
                120
        );

        assertThat(col.getStyleClass()).contains("col-center");
        DummyRow row = new DummyRow("Prueba", BigDecimal.TEN, LocalDate.now(), "ACTIVO");
        TableColumn.CellDataFeatures<DummyRow, String> features = new TableColumn.CellDataFeatures<>(null, col, row);
        assertThat(col.getCellValueFactory().call(features).getValue()).isEqualTo("ACTIVO");
    }
}
