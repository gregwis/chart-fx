package de.gsi.chart.viewer;

import de.gsi.chart.axes.Axis;
import de.gsi.chart.plugins.ChartPlugin;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.controlsfx.control.MasterDetailPane;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.property.BeanPropertyUtils;

import de.gsi.chart.Chart;
import de.gsi.chart.renderer.Renderer;
import de.gsi.dataset.DataSet;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.javafx.Icon;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A JavaFx control, which can be given a root chart-fx element, and which then allows to reorganize the charts.
 * The root element can be a DataViewer, DataView, DataViewWindow, Chart or Renderer or any JavaFx node.
 * All chart-fx related child elements are shown in a tree View and can be reorganized using drag and drop.
 * The lower part of the control allows to modify the currently selected element's properties in a PropertySheet
 * control.
 * 
 * @author Alexander Krimm
 */
public class ChartManager extends MasterDetailPane {

    /**
     * Creates a new ChartManager
     *
     * @param root The root element on which the manager should act
     */
    public ChartManager(final Object root) {
        super();
        setShowDetailNode(false);
        // Tree view
        TreeView<Object> treeView = new TreeView<>();
        setMasterNode(treeView);
        // Tabs
        TabPane tabPane = new TabPane();
        setDetailNode(tabPane);
        // Property sheet
        // TODO: add filters to property view: only properties defined in chart-fx?
        // TODO: add additional editors for chart related properties
        // TODO: add new child elements (needs a way to add factory methods)
        PropertySheet propertySheet = new PropertySheet();
        final Tab propertyTab = new Tab("Properties", propertySheet);
        propertyTab.setClosable(false);
        tabPane.getTabs().add(propertyTab);
        setDetailSide(Side.BOTTOM);
        treeView.setRoot(AbstractChartFxTreeItem.get(root));
        treeView.setCellFactory(element -> new ChartFxTreeCell(element));
        treeView.getSelectionModel().selectedItemProperty().addListener((prop, oldVal, newVal) -> {
            if (newVal != oldVal && newVal != null) {
                propertySheet.getItems().setAll(BeanPropertyUtils.getProperties(newVal.getValue()));
                setShowDetailNode(true);
            } else {
                setShowDetailNode(false);
            }
        });
        // css
        // TODO: CSS Pane
        final VBox cssVBox = new VBox();
        final Tab cssTab = new Tab("CSS", cssVBox);
        cssTab.setClosable(false);
        tabPane.getTabs().add(cssTab);
    }

    public void addPluginFactory(Node icon, String name, Object factory) {

    }

    public void addDataSetFactory(Node icon, String name, Object factory) {

    }

    public void addRendererFactory(Node icon, String name, Object factory) {

    }

    ////////////////
    // FxTreeCell //
    ////////////////

    protected static class ChartFxTreeCell extends TreeCell<Object> {
        private Object element;
        private HBox hBox = new HBox();
        protected TreeItem<Object> draggedItem = null;

        /**
         * @param element
         */
        public ChartFxTreeCell(Object element) {
            super();
            this.element = element;
            hBox.setAlignment(Pos.BASELINE_LEFT);
            setGraphic(hBox);
            // TODO: drag and drop reorganization
            //setOnDragDetected(ev -> {
            //    draggedItem = getTreeItem();
            //    if (draggedItem == null)  return;
            //    final Dragboard db = startDragAndDrop(TransferMode.MOVE);
            //    ClipboardContent content = new ClipboardContent();
            //    content.put(new DataFormat("application/x-java-serialized-object"), draggedItem.getValue());
            //    db.setContent(content);
            //    db.setDragView(snapshot(null, null));
            //    ev.consume();
            //});
            //setOnDragOver(ev -> {    if (!event.getDragboard().hasContent(JAVA_FORMAT)) return;
            //    TreeItem thisItem = getTreeItem();
            //    // can't drop on itself
            //    if (draggedItem == null || thisItem == null || thisItem == draggedItem) return;
            //    // ignore if this is the root
            //    if (draggedItem.getParent() == null) {
            //        clearDropLocation();
            //        return;
            //    }
            //    ev.acceptTransferModes(TransferMode.MOVE);
            //    if (!Objects.equals(dropZone, this)) {
            //        clearDropLocation();
            //        this.dropZone = this;
            //        dropZone.setStyle(DROP_HINT_STYLE);
            //    }
            //});
            //setOnDragDropped(ev -> {

            //});
            //setOnDragDone(ev -> {

            //});
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (!empty && item != null) {
                setText(null);
                if (item instanceof DataViewer) {
                    FontIcon icon = new FontIcon("fa-object-group");
                    Label label = new Label(((DataViewer) item).toString());
                    hBox.getChildren().setAll(icon, label);
                } else if (item instanceof DataView) {
                    FontIcon icon = new FontIcon("fa-object-ungroup");
                    Label label = new Label(((DataView) item).toString());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    // TODO: how to get DataViewer instance? / pass parent to cell?
                    // deleteButton.setOnAction(evt -> ((DataViewer) ((DataView) item).getParent()).getViews().remove(item));
                    deleteButton.setDisable(true);
                    hBox.getChildren().add(deleteButton);
                } else if (item instanceof DataViewWindow) {
                    FontIcon icon = new FontIcon("fa-window-maximize");
                    Label label = new Label(((DataViewWindow) item).toString());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    deleteButton.setOnAction(evt -> ((DataViewWindow) item).closeButtonAction.run());
                    hBox.getChildren().add(deleteButton);
                    Button minimizeButton = new Button("", new FontIcon("fa-window-minimize"));
                    minimizeButton.setOnAction(evt -> ((DataViewWindow) item).minimizeButtonAction.run());
                    hBox.getChildren().add(minimizeButton);
                    Button maximizeButton = new Button("", new FontIcon("fa-image"));
                    maximizeButton.setOnAction(evt -> ((DataViewWindow) item).maximizeButtonAction.run());
                    hBox.getChildren().add(maximizeButton);
                    Button detachButton = new Button("", new FontIcon("fas-sign-out-alt"));
                    detachButton.setOnAction(evt -> ((DataViewWindow) item).setDetached(true));
                    hBox.getChildren().add(detachButton);
                } else if (item instanceof DataSet) {
                    FontIcon icon = new FontIcon("fas-file-excel");
                    Label label = new Label(((DataSet) item).getName());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    deleteButton.setDisable(true); // TODO: how to get the chart/renderer containing the DataSet
                    hBox.getChildren().add(deleteButton);
                } else if (item instanceof Renderer) {
                    FontIcon icon = new FontIcon("fas-pen");
                    Label label = new Label(((Renderer) item).toString());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    deleteButton.setDisable(true); // TODO: how to get the chart containing the renderer
                    hBox.getChildren().add(deleteButton);
                } else if (item instanceof Chart) {
                    FontIcon icon = new FontIcon("fas-chart-line");
                    Label label = new Label(((Chart) item).toString());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    deleteButton.setDisable(true); // TODO: how to get the element containing the chart
                    hBox.getChildren().add(deleteButton);
                } else if (item instanceof Axis) {
                    FontIcon icon = new FontIcon("fas-ruler-combined");
                    Label label = new Label(((Axis) item).toString());
                    hBox.getChildren().setAll(icon, label);
                } else if (item instanceof ChartPlugin) {
                    FontIcon icon = new FontIcon("fas-puzzle-piece");
                    Label label = new Label(((ChartPlugin) item).toString());
                    hBox.getChildren().setAll(icon, label);
                    Button deleteButton = new Button("", new FontIcon("fa-trash"));
                    deleteButton.setOnAction(evt -> (((ChartPlugin) item).getChart()).getPlugins().remove(item));
                    hBox.getChildren().add(deleteButton);
                } else {
                    FontIcon icon = new FontIcon("fas-question");
                    Label label = new Label((item).toString());
                    hBox.getChildren().setAll(icon, label);
                }
                // setContextMenu() // TODO: add menu/buttons here to remove/add additional components
            } else {
                setText("");
                hBox.getChildren().clear();
            }
        }
    }

    ////////////////
    // FxTreeItem //
    ////////////////

    public static abstract class AbstractChartFxTreeItem extends TreeItem<Object> {
        protected String name;
        protected Object reference;
        protected boolean isLeaf = false;

        protected AbstractChartFxTreeItem(Object root) {
            super(root);
            reference = root;
        }

        @Override
        public boolean isLeaf() {
            return isLeaf;
        }

        public static AbstractChartFxTreeItem get(final Object root) {
            if (root instanceof Chart) {
                return new ChartFxChartTreeItem(root);
            } else if (root instanceof DataSet) {
                return new ChartFxDataSetTreeItem(root);
            } else if (root instanceof Renderer) {
                return new ChartFxRendererTreeItem(root);
            } else if (root instanceof DataViewWindow) {
                return new ChartFxDataViewWindowTreeItem(root);
            } else if (root instanceof DataView) {
                return new ChartFxDataViewTreeItem(root);
            } else if (root instanceof DataViewer) {
                return new ChartFxDataViewerTreeItem(root);
            } else if (root instanceof ChartPlugin) {
                return new ChartFxPluginTreeItem(root);
            } else if (root instanceof Axis) {
                return new ChartFxAxisTreeItem(root);
            }
            return null; // TODO: return default Object item
        }
    }

    public static class ChartFxChartTreeItem extends AbstractChartFxTreeItem {
        protected ChartFxChartTreeItem(Object root) {
            super(root);
            Chart chart = (Chart) root;
            name = chart.getTitle();
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            final ObservableList<TreeItem<Object>> result = super.getChildren();
            if (result.size() > 0) {
                return result;
            }
            result.setAll(((Chart) reference).getDatasets().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(((Chart) reference).getPlugins().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(((Chart) reference).getRenderers().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(((Chart) reference).getAxes().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            if (result.size() == 0) {
                this.isLeaf = true;
            }
            // TODO: add listeners
            return result;
        }
    }

    public static class ChartFxDataSetTreeItem extends AbstractChartFxTreeItem {
        public ChartFxDataSetTreeItem(Object root) {
            super(root);
            isLeaf = true;
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            return FXCollections.emptyObservableList();
        }
    }

    public static class ChartFxRendererTreeItem extends AbstractChartFxTreeItem {
        public ChartFxRendererTreeItem(Object root) {
            super(root);
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            final ObservableList<TreeItem<Object>> result = super.getChildren();
            if (result.size() > 0) {
                return result;
            }
            result.clear();
            result.addAll(((Renderer) reference).getDatasets().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(((Renderer) reference).getAxes().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            if (result.size() == 0) {
                this.isLeaf = true;
            }
            // TODO: add listeners
            return result;
        }
    }

    public static class ChartFxDataViewerTreeItem extends AbstractChartFxTreeItem {
        public ChartFxDataViewerTreeItem(Object root) {
            super(root);
            getChildren();
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            final ObservableList<TreeItem<Object>> result = super.getChildren();
            if (result.size() > 0) {
                return result;
            }
            result.setAll(((DataViewer) reference).getViews().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            if (result.size() == 0) {
                this.isLeaf = true;
            }
            // TODO: add listeners
            return result;
        }
    }

    public static class ChartFxDataViewTreeItem extends AbstractChartFxTreeItem {
        public ChartFxDataViewTreeItem(Object root) {
            super(root);
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            final ObservableList<TreeItem<Object>> result = super.getChildren();
            if (result.size() > 0) {
                return result;
            }
            result.setAll(((DataView) reference).getVisibleChildren().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(((DataView) reference).getMinimisedChildren().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            result.addAll(AbstractChartFxTreeItem.get(((DataView) reference).getMaximizedChild()));
            // TODO: find out whether to add SubDataViews (filter out HBox, VBox, Grid, Maximise)
            // result.addAll(((DataView) reference).getSubDataViews().stream().map(a -> AbstractChartFxTreeItem.get(a)).collect(Collectors.toList()));
            if (result.size() == 0) {
                this.isLeaf = true;
            }
            // TODO: add listeners
            return result;
        }
    }

    public static class ChartFxDataViewWindowTreeItem extends AbstractChartFxTreeItem {
        public ChartFxDataViewWindowTreeItem(Object root) {
            super(root);
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            final ObservableList<TreeItem<Object>> result = super.getChildren();
            if (result.size() > 0) {
                return result;
            }
            result.clear();
            result.addAll(AbstractChartFxTreeItem.get(((DataViewWindow) reference).getContent()));
            if (result.size() == 0) {
                this.isLeaf = true;
            }
            // TODO: add listeners
            return result;
        }
    }

    public static class ChartFxPluginTreeItem extends AbstractChartFxTreeItem {
        public ChartFxPluginTreeItem(Object root) {
            super(root);
            isLeaf = true;
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            return FXCollections.emptyObservableList();
        }
    }

    public static class ChartFxAxisTreeItem extends AbstractChartFxTreeItem {
        public ChartFxAxisTreeItem(Object root) {
            super(root);
            isLeaf = true;
        }

        @Override
        public ObservableList<TreeItem<Object>> getChildren() {
            return FXCollections.emptyObservableList();
        }
    }
}

