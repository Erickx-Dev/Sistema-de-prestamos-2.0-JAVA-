package com.credinoda.ui.components;

import com.credinoda.common.Formatters;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.Function;

/**
 * Utilitario para la construcción y estandarización visual de columnas de {@link javafx.scene.control.TableView}.
 * <p>
 * Garantiza que las cabeceras y las celdas compartan estrictamente la misma alineación horizontal,
 * aplicando el estándar contable (montos a la derecha, fechas y números centrados, textos a la izquierda).
 */
public final class TableColumnHelper {

    private TableColumnHelper() {
    }

    /**
     * Construye una columna formateada para montos financieros con alineación a la derecha.
     */
    public static <S> TableColumn<S, String> monedaColumn(String title, Function<S, BigDecimal> extractor, double prefWidth) {
        TableColumn<S, String> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.getStyleClass().add("col-right");
        col.setCellValueFactory(d -> {
            BigDecimal val = extractor.apply(d.getValue());
            return new SimpleStringProperty(val != null ? Formatters.formatoMoneda(val) : "-");
        });
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER_RIGHT);
                    if (!getStyleClass().contains("cell-right")) {
                        getStyleClass().add("cell-right");
                    }
                }
            }
        });
        return col;
    }

    /**
     * Construye una columna formateada para fechas con alineación centrada.
     */
    public static <S> TableColumn<S, String> fechaColumn(String title, Function<S, LocalDate> extractor, double prefWidth) {
        TableColumn<S, String> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.getStyleClass().add("col-center");
        col.setCellValueFactory(d -> {
            LocalDate date = extractor.apply(d.getValue());
            return new SimpleStringProperty(date != null ? Formatters.formatoFecha(date) : "-");
        });
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(Pos.CENTER);
                    if (!getStyleClass().contains("cell-center")) {
                        getStyleClass().add("cell-center");
                    }
                }
            }
        });
        return col;
    }

    /**
     * Construye una columna para texto o códigos alfanuméricos con alineación configurable.
     */
    public static <S> TableColumn<S, String> textoColumn(String title, Function<S, String> extractor, Pos alignment, double prefWidth) {
        TableColumn<S, String> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        String headerClass = resolveHeaderClass(alignment);
        col.getStyleClass().add(headerClass);
        col.setCellValueFactory(d -> {
            String val = extractor.apply(d.getValue());
            return new SimpleStringProperty(val != null ? val : "");
        });
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item);
                    setAlignment(alignment);
                    String cellClass = resolveCellClass(alignment);
                    if (!getStyleClass().contains(cellClass)) {
                        getStyleClass().add(cellClass);
                    }
                }
            }
        });
        return col;
    }

    /**
     * Construye una columna para badges de estado con alineación centrada.
     */
    public static <S> TableColumn<S, String> badgeColumn(String title,
                                                         Function<S, String> extractor,
                                                         Function<String, String> badgeClassResolver,
                                                         double prefWidth) {
        TableColumn<S, String> col = new TableColumn<>(title);
        col.setPrefWidth(prefWidth);
        col.getStyleClass().add("col-center");
        col.setCellValueFactory(d -> {
            String val = extractor.apply(d.getValue());
            return new SimpleStringProperty(val != null ? val : "");
        });
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(null);
                    Label badge = new Label(item);
                    badge.getStyleClass().add("badge");
                    if (badgeClassResolver != null) {
                        String specificClass = badgeClassResolver.apply(item);
                        if (specificClass != null && !specificClass.isBlank()) {
                            badge.getStyleClass().add(specificClass);
                        }
                    }
                    setAlignment(Pos.CENTER);
                    if (!getStyleClass().contains("cell-center")) {
                        getStyleClass().add("cell-center");
                    }
                    setGraphic(badge);
                }
            }
        });
        return col;
    }

    /**
     * Aplica la clase CSS de alineación a la cabecera de una columna existente.
     */
    public static void setHeaderAlignment(TableColumn<?, ?> col, Pos alignment) {
        col.getStyleClass().removeAll("col-left", "col-center", "col-right");
        col.getStyleClass().add(resolveHeaderClass(alignment));
    }

    private static String resolveHeaderClass(Pos alignment) {
        if (alignment == Pos.CENTER || alignment == Pos.TOP_CENTER || alignment == Pos.BOTTOM_CENTER) {
            return "col-center";
        } else if (alignment == Pos.CENTER_RIGHT || alignment == Pos.TOP_RIGHT || alignment == Pos.BOTTOM_RIGHT) {
            return "col-right";
        }
        return "col-left";
    }

    private static String resolveCellClass(Pos alignment) {
        if (alignment == Pos.CENTER || alignment == Pos.TOP_CENTER || alignment == Pos.BOTTOM_CENTER) {
            return "cell-center";
        } else if (alignment == Pos.CENTER_RIGHT || alignment == Pos.TOP_RIGHT || alignment == Pos.BOTTOM_RIGHT) {
            return "cell-right";
        }
        return "cell-left";
    }
}
