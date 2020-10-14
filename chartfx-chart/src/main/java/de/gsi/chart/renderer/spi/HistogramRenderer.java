package de.gsi.chart.renderer.spi;

import static de.gsi.dataset.DataSet.DIM_X;
import static de.gsi.dataset.DataSet.DIM_Y;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChartCss;
import de.gsi.chart.axes.Axis;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.renderer.LineStyle;
import de.gsi.chart.renderer.Renderer;
import de.gsi.chart.renderer.spi.utils.BezierCurve;
import de.gsi.chart.renderer.spi.utils.DefaultRenderColorScheme;
import de.gsi.chart.utils.StyleParser;
import de.gsi.dataset.DataSet;
import de.gsi.dataset.Histogram;
import de.gsi.dataset.spi.LimitedIndexedTreeDataSet;
import de.gsi.dataset.utils.DoubleArrayCache;
import de.gsi.dataset.utils.ProcessingProfiler;

/**
 * Simple renderer specialised for 1D histograms.
 *
 * N.B. this is _not_ primarily optimised for speed, does not deploy caching, and is intended for DataSets
 * (and Histogram derivatives) with significantly less than 1k data points. Non-histogram DataSets are sorted by default
 * (can be overridden via #autoSortingProperty()).
 * Please have a look at the {@see ErrorDataSetRenderer} for larger DataSets,
 *
 * @author rstein
 */
public class HistogramRenderer extends AbstractDataSetManagement<HistogramRenderer> implements Renderer {
    private final BooleanProperty autoSorting = new SimpleBooleanProperty(this, "autoSorting", true);
    private final ObjectProperty<LineStyle> polyLineStyle = new SimpleObjectProperty<>(this, "polyLineStyle", LineStyle.HISTOGRAM_FILLED);

    public BooleanProperty autoSortingProperty() {
        return autoSorting;
    }

    @Override
    public Canvas drawLegendSymbol(DataSet dataSet, int dsIndex, int width, int height) {
        final Canvas canvas = new Canvas(width, height);
        final GraphicsContext gc = canvas.getGraphicsContext2D();

        final String style = dataSet.getStyle();
        final Integer layoutOffset = StyleParser.getIntegerPropertyValue(style, XYChartCss.DATASET_LAYOUT_OFFSET);
        final Integer dsIndexLocal = StyleParser.getIntegerPropertyValue(style, XYChartCss.DATASET_INDEX);

        final int dsLayoutIndexOffset = layoutOffset == null ? 0 : layoutOffset; // TODO: rationalise

        final int plotingIndex = dsLayoutIndexOffset + (dsIndexLocal == null ? dsIndex : dsIndexLocal);

        gc.save();
        DefaultRenderColorScheme.setLineScheme(gc, dataSet.getStyle(), plotingIndex);
        DefaultRenderColorScheme.setGraphicsContextAttributes(gc, dataSet.getStyle());
        DefaultRenderColorScheme.setFillScheme(gc, dataSet.getStyle(), plotingIndex);

        final double y = height / 2.0;
        gc.fillRect(1, 1, width - 2.0, height - 2.0);
        gc.strokeLine(1, y, width - 2.0, y);
        gc.restore();
        return canvas;
    }

    /**
     * whether renderer should draw no, simple (point-to-point), stair-case, Bezier, ... lines
     *
     * @return LineStyle
     */
    public LineStyle getPolyLineStyle() {
        return polyLineStyleProperty().get();
    }

    public boolean isAutoSorting() {
        return autoSortingProperty().get();
    }

    /**
     * Sets whether renderer should draw no, simple (point-to-point), stair-case, Bezier, ... lines
     *
     * @return property
     */
    public ObjectProperty<LineStyle> polyLineStyleProperty() {
        return polyLineStyle;
    }

    @Override
    public List<DataSet> render(final GraphicsContext gc, final Chart chart, final int dataSetOffset, final ObservableList<DataSet> datasets) {
        final long start = ProcessingProfiler.getTimeStamp();
        final Axis xAxis = getFirstAxis(Orientation.HORIZONTAL);
        final Axis yAxis = getFirstAxis(Orientation.VERTICAL);

        // make local copy and add renderer specific data sets
        final List<DataSet> localDataSetList = new ArrayList<>(datasets);
        localDataSetList.addAll(super.getDatasets());

        // verify that allDataSets are sorted
        for (int i = 0; i < localDataSetList.size(); i++) {
            DataSet dataSet = localDataSetList.get(i);
            final int index = i;
            dataSet.lock().readLockGuardOptimistic(() -> {
                if (!(dataSet instanceof Histogram) && isAutoSorting() && (!isDataSetSorted(dataSet, DIM_X) && isDataSetSorted(dataSet, DIM_Y))) {
                    // replace DataSet with sorted variety
                    // do not need to do this for Histograms as they are always sorted by design
                    LimitedIndexedTreeDataSet newDataSet = new LimitedIndexedTreeDataSet(dataSet.getName(), Integer.MAX_VALUE);
                    newDataSet.set(dataSet);
                    localDataSetList.set(index, newDataSet);
                }

                if (index != 0) {
                    return;
                }
                // update categories for the first (index == '0') indexed data set
                if (xAxis instanceof CategoryAxis) {
                    final CategoryAxis axis = (CategoryAxis) xAxis;
                    axis.updateCategories(dataSet);
                }

                if (yAxis instanceof CategoryAxis) {
                    final CategoryAxis axis = (CategoryAxis) yAxis;
                    axis.updateCategories(dataSet);
                }
            });
        }

        drawHistograms(gc, localDataSetList, xAxis, yAxis, dataSetOffset);

        ProcessingProfiler.getTimeDiff(start);

        return localDataSetList;
    }

    public void setAutoSorting(final boolean autoSorting) {
        this.autoSortingProperty().set(autoSorting);
    }

    /**
     * Sets whether renderer should draw no, simple (point-to-point), stair-case, Bezier, ... lines
     *
     * @param style draw no, simple (point-to-point), stair-case, Bezier, ... lines
     * @return itself (fluent design)
     */
    public HistogramRenderer setPolyLineStyle(final LineStyle style) {
        polyLineStyleProperty().set(style);
        return getThis();
    }

    protected void drawHistograms(final GraphicsContext gc, final List<DataSet> dataSets, final Axis xAxis, final Axis yAxis, final int dataSetOffset) {
        final ArrayDeque<DataSet> lockQueue = new ArrayDeque<>(dataSets.size());
        try {
            dataSets.forEach(ds -> {
                lockQueue.push(ds);
                ds.lock().readLock();
            });

            switch (getPolyLineStyle()) {
            case NONE:
                return;
            case AREA:
                drawPolyLineLine(gc, dataSets, xAxis, yAxis, dataSetOffset, true);
                break;
            case ZERO_ORDER_HOLDER:
            case STAIR_CASE:
                drawPolyLineStairCase(gc, dataSets, xAxis, yAxis, dataSetOffset, false);
                break;
            case HISTOGRAM:
                drawPolyLineHistogram(gc, dataSets, xAxis, yAxis, dataSetOffset, false);
                break;
            case HISTOGRAM_FILLED:
                drawPolyLineHistogram(gc, dataSets, xAxis, yAxis, dataSetOffset, true);
                break;
            case BEZIER_CURVE:
                drawPolyLineHistogramBezier(gc, dataSets, xAxis, yAxis, dataSetOffset, true);
                break;
            case NORMAL:
            default:
                drawPolyLineLine(gc, dataSets, xAxis, yAxis, dataSetOffset, false);
                break;
            }
        } finally {
            // unlock in reverse order
            while (!lockQueue.isEmpty()) {
                lockQueue.pop().lock().readUnLock();
            }
        }
    }

    protected static void drawPolyLineHistogram(final GraphicsContext gc, final List<DataSet> dataSets, final Axis xAxis, final Axis yAxis, final int dataSetOffset, boolean filled) {
        int lindex = dataSetOffset - 1;
        for (DataSet ds : dataSets) {
            lindex++;
            if (ds.getDataCount() == 0) {
                continue;
            }
            final boolean isVerticalDataSet = isVerticalDataSet(ds);

            final int dimIndexAbscissa = isVerticalDataSet ? DIM_Y : DIM_X;
            final int dimIndexOrdinate = isVerticalDataSet ? DIM_X : DIM_Y;
            final Axis abscissa = isVerticalDataSet ? yAxis : xAxis;
            final Axis ordinate = isVerticalDataSet ? xAxis : yAxis;

            final int indexMin = Math.max(0, ds.getIndex(dimIndexAbscissa, Math.min(abscissa.getMin(), abscissa.getMax())));
            final int indexMax = Math.min(ds.getDataCount(), ds.getIndex(dimIndexAbscissa, Math.max(abscissa.getMin(), abscissa.getMax()) + 1.0));

            // need to allocate new array :-(
            final int nRange = Math.abs(indexMax - indexMin);
            final double[] newX = DoubleArrayCache.getInstance().getArrayExact(2 * (nRange + 1));
            final double[] newY = DoubleArrayCache.getInstance().getArrayExact(2 * (nRange + 1));
            final double axisMin = getAxisMin(xAxis, yAxis, !isVerticalDataSet);

            for (int i = 0; i < nRange; i++) {
                final int index = indexMin + i;
                final double binValue = ordinate.getDisplayPosition(ds.get(dimIndexOrdinate, index));
                final double binStart = abscissa.getDisplayPosition(getBinStart(ds, dimIndexAbscissa, index));
                final double binStop = abscissa.getDisplayPosition(getBinStop(ds, dimIndexAbscissa, index));
                newX[2 * i + 1] = binStart;
                newY[2 * i + 1] = binValue;
                newX[2 * i + 2] = binStop;
                newY[2 * i + 2] = binValue;
            }
            // first point
            newX[0] = newX[1];
            newY[0] = axisMin;

            // last point
            newX[2 * (nRange + 1) - 1] = newX[2 * (nRange + 1) - 2];
            newY[2 * (nRange + 1) - 1] = axisMin;

            gc.save();
            DefaultRenderColorScheme.setLineScheme(gc, ds.getStyle(), lindex);
            DefaultRenderColorScheme.setGraphicsContextAttributes(gc, ds.getStyle());

            drawPolygon(gc, newX, newY, filled, isVerticalDataSet);
            gc.restore();

            // release arrays to cache
            DoubleArrayCache.getInstance().add(newX);
            DoubleArrayCache.getInstance().add(newY);
        }
    }

    protected static void drawPolygon(final GraphicsContext gc, final double[] newX, final double[] newY, final boolean filled, final boolean isVerticalDataSet) {
        if (filled) {
            // use stroke as fill colour
            gc.setFill(gc.getStroke());
            if (isVerticalDataSet) {
                gc.fillPolygon(newY, newX, newX.length); // NOPMD NOSONAR - flip on purpose
            } else {
                gc.fillPolygon(newX, newY, newX.length);
            }
            return;
        }
        // stroke only
        if (isVerticalDataSet) {
            gc.strokePolyline(newY, newX, newX.length); // NOPMD NOSONAR - flip on purpose
        } else {
            gc.strokePolyline(newX, newY, newX.length);
        }
    }

    protected static void drawPolyLineHistogramBezier(final GraphicsContext gc, final List<DataSet> dataSets, final Axis xAxis, final Axis yAxis, final int dataSetOffset, boolean filled) {
        final double xAxisWidth = xAxis.getWidth();
        final double xMin = xAxis.getValueForDisplay(0);
        final double xMax = xAxis.getValueForDisplay(xAxisWidth);

        final List<Double> xValuesAll = new ArrayList<>();
        xValuesAll.add(xAxis.getMin());
        xValuesAll.add(xAxis.getMax());

        int lindex = dataSetOffset - 1;
        for (DataSet ds : dataSets) {
            lindex++;

            if (ds.getDataCount() == 0) {
                continue;
            }

            final int indexMin = Math.max(0, ds.getIndex(DIM_X, xMin));
            final int indexMax = Math.min(ds.getIndex(DIM_X, xMax) + 1, ds.getDataCount());
            final int min = Math.min(indexMin, indexMax);
            final int nRange = Math.abs(indexMax - indexMin);

            if (nRange <= 2) {
                drawPolyLineLine(gc, List.of(ds), xAxis, yAxis, lindex, filled);
                return;
            }

            gc.save();
            DefaultRenderColorScheme.setLineScheme(gc, ds.getStyle(), lindex);
            DefaultRenderColorScheme.setGraphicsContextAttributes(gc, ds.getStyle());

            // need to allocate new array :-(
            final double[] xCp1 = DoubleArrayCache.getInstance().getArrayExact(nRange);
            final double[] yCp1 = DoubleArrayCache.getInstance().getArrayExact(nRange);
            final double[] xCp2 = DoubleArrayCache.getInstance().getArrayExact(nRange);
            final double[] yCp2 = DoubleArrayCache.getInstance().getArrayExact(nRange);

            final double[] xValues = DoubleArrayCache.getInstance().getArrayExact(nRange);
            final double[] yValues = DoubleArrayCache.getInstance().getArrayExact(nRange);

            for (int i = 0; i < nRange; i++) {
                xValues[i] = xAxis.getDisplayPosition(ds.get(DIM_X, min + i));
                yValues[i] = yAxis.getDisplayPosition(ds.get(DIM_Y, min + i));
            }

            BezierCurve.calcCurveControlPoints(xValues, yValues, xCp1, yCp1, xCp2, yCp2, nRange);

            // use stroke as fill colour
            gc.setFill(gc.getStroke());
            gc.beginPath();
            for (int i = 0; i < nRange - 1; i++) {
                final double x0 = xValues[i];
                final double x1 = xValues[i + 1];
                final double y0 = yValues[i];
                final double y1 = yValues[i + 1];

                // coordinates of first Bezier control point.
                final double xc0 = xCp1[i];
                final double yc0 = yCp1[i];
                // coordinates of the second Bezier control point.
                final double xc1 = xCp2[i];
                final double yc1 = yCp2[i];

                gc.moveTo(x0, y0);
                gc.bezierCurveTo(xc0, yc0, xc1, yc1, x1, y1);
            }
            gc.moveTo(xValues[nRange - 1], yValues[nRange - 1]);
            gc.closePath();
            gc.stroke();
            gc.restore();

            // release arrays to Cache
            DoubleArrayCache.getInstance().add(xValues);
            DoubleArrayCache.getInstance().add(yValues);
            DoubleArrayCache.getInstance().add(xCp1);
            DoubleArrayCache.getInstance().add(yCp1);
            DoubleArrayCache.getInstance().add(xCp2);
            DoubleArrayCache.getInstance().add(yCp2);
        }
    }

    protected static void drawPolyLineLine(final GraphicsContext gc, final List<DataSet> dataSets, final Axis xAxis, final Axis yAxis, final int dataSetOffset, boolean filled) {
        final double xAxisWidth = xAxis.getWidth();
        final double xMin = xAxis.getValueForDisplay(0);
        final double xMax = xAxis.getValueForDisplay(xAxisWidth);

        int lindex = dataSetOffset - 1;
        for (DataSet ds : dataSets) {
            lindex++;
            if (ds.getDataCount() == 0) {
                continue;
            }

            gc.save();
            DefaultRenderColorScheme.setLineScheme(gc, ds.getStyle(), lindex);
            DefaultRenderColorScheme.setGraphicsContextAttributes(gc, ds.getStyle());

            final int indexMin = Math.max(0, ds.getIndex(DIM_X, xMin));
            final int indexMax = Math.min(ds.getIndex(DIM_X, xMax) + 1, ds.getDataCount());

            gc.beginPath();
            double x0 = xAxis.getDisplayPosition(ds.get(DIM_X, indexMin));
            double y0 = yAxis.getDisplayPosition(ds.get(DIM_Y, indexMin));
            gc.moveTo(x0, y0);
            boolean lastIsFinite = true;
            double xLastValid = 0.0;
            double yLastValid = 0.0;
            for (int i = indexMin + 1; i < indexMax; i++) {
                x0 = xAxis.getDisplayPosition(ds.get(DIM_X, i));
                y0 = yAxis.getDisplayPosition(ds.get(DIM_Y, i));
                if (Double.isFinite(x0) && Double.isFinite(y0)) {
                    if (!lastIsFinite) {
                        gc.moveTo(x0, y0);
                        lastIsFinite = true;
                        continue;
                    }
                    gc.lineTo(x0, y0);
                    xLastValid = x0;
                    yLastValid = y0;
                    lastIsFinite = true;
                } else {
                    lastIsFinite = false;
                }
            }
            gc.moveTo(xLastValid, yLastValid);
            gc.closePath();

            if (filled) {
                gc.fill();
            } else {
                gc.stroke();
            }

            gc.restore();
        }
    }

    protected static void drawPolyLineStairCase(final GraphicsContext gc, final List<DataSet> dataSets, final Axis xAxis, final Axis yAxis, final int dataSetOffset, boolean filled) {
        final double xAxisWidth = xAxis.getWidth();
        final double xMin = xAxis.getValueForDisplay(0);
        final double xMax = xAxis.getValueForDisplay(xAxisWidth);

        final List<Double> xValuesAll = new ArrayList<>();
        xValuesAll.add(xAxis.getMin());
        xValuesAll.add(xAxis.getMax());

        int lindex = dataSetOffset - 1;
        for (DataSet ds : dataSets) {
            lindex++;
            final int indexMin = Math.max(0, ds.getIndex(DIM_X, xMin));
            final int indexMax = Math.min(ds.getIndex(DIM_X, xMax) + 1, ds.getDataCount());
            final int min = Math.min(indexMin, indexMax);
            final int max = Math.max(indexMin, indexMax);
            final int nRange = Math.abs(indexMax - indexMin);
            if (ds.getDataCount() == 0 || nRange == 0) {
                continue;
            }

            // need to allocate new array :-(
            final double[] newX = DoubleArrayCache.getInstance().getArrayExact(2 * nRange);
            final double[] newY = DoubleArrayCache.getInstance().getArrayExact(2 * nRange);

            for (int i = min; i < min + nRange - 1; i++) {
                newX[2 * i] = xAxis.getDisplayPosition(ds.get(DIM_X, i));
                newY[2 * i] = yAxis.getDisplayPosition(ds.get(DIM_Y, i));
                newX[2 * i + 1] = xAxis.getDisplayPosition(ds.get(DIM_X, i + 1));
                newY[2 * i + 1] = newY[2 * i];
            }
            // last point
            newX[2 * (nRange - 1)] = xAxis.getDisplayPosition(ds.get(DIM_X, min + nRange - 1));
            newY[2 * (nRange - 1)] = yAxis.getDisplayPosition(ds.get(DIM_Y, min + nRange - 1));
            newX[2 * nRange - 1] = xAxis.getDisplayPosition(max);
            newY[2 * nRange - 1] = newY[2 * (nRange - 1)];

            gc.save();
            DefaultRenderColorScheme.setLineScheme(gc, ds.getStyle(), lindex);
            DefaultRenderColorScheme.setGraphicsContextAttributes(gc, ds.getStyle());
            if (filled) {
                // use stroke as fill colour
                gc.setFill(gc.getStroke());
                gc.fillPolygon(newX, newY, 2 * nRange);
            } else {
                gc.strokePolyline(newX, newY, 2 * nRange);
            }

            gc.restore();

            // release arrays to cache
            DoubleArrayCache.getInstance().add(newX);
            DoubleArrayCache.getInstance().add(newY);
        }
    }

    protected static double estimateHalfBinWidth(final DataSet ds, final int dimIndex, final int index) {
        final int nMax = ds.getDataCount();
        if (nMax == 0) {
            return 0.5;
        } else if (nMax == 1) {
            return 0.5 * Math.abs(ds.get(dimIndex, 1) - ds.get(dimIndex, 0));
        }
        final double binCentre = ds.get(dimIndex, index);
        final double diffLeft = index - 1 >= 0 ? Math.abs(binCentre - ds.get(dimIndex, index - 1)) : -1;
        final double diffRight = index + 1 < nMax ? Math.abs(ds.get(dimIndex, index + 1)) - binCentre : -1;
        final boolean isInValidLeft = diffLeft < 0;
        final boolean isInValidRight = diffRight < 0;
        if (isInValidLeft && isInValidRight) {
            return 0.5;
        } else if (isInValidLeft || isInValidRight) {
            return 0.5 * Math.max(diffLeft, diffRight);
        }
        return 0.5 * Math.min(diffLeft, diffRight);
    }

    protected static double getBinStart(final DataSet ds, final int dimIndex, final int index) {
        if (ds instanceof Histogram) {
            return ((Histogram) ds).getBinLimits(dimIndex, Histogram.Boundary.LOWER, index + 1); // '+1' because binIndex starts with '0' (under-flow bin)
        }
        return ds.get(dimIndex, index) - estimateHalfBinWidth(ds, dimIndex, index);
    }

    protected static double getBinStop(final DataSet ds, final int dimIndex, final int index) {
        if (ds instanceof Histogram) {
            return ((Histogram) ds).getBinLimits(dimIndex, Histogram.Boundary.UPPER, index + 1); // '+1' because binIndex starts with '0' (under-flow bin)
        }
        return ds.get(dimIndex, index) + estimateHalfBinWidth(ds, dimIndex, index);
    }

    @Override
    protected HistogramRenderer getThis() {
        return this;
    }

    protected static boolean isDataSetSorted(final DataSet dataSet, final int dimIndex) {
        if (dataSet.getDataCount() < 2) {
            return true;
        }
        double xLast = dataSet.get(dimIndex, 0);
        for (int i = 1; i < dataSet.getDataCount(); i++) {
            final double x = dataSet.get(dimIndex, i);
            if (x < xLast) {
                return false;
            }
            xLast = x;
        }

        return true;
    }

    protected static boolean isVerticalDataSet(final DataSet ds) {
        final boolean isVerticalDataSet;
        if (ds instanceof Histogram) {
            Histogram histogram = (Histogram) ds;
            isVerticalDataSet = histogram.getBinCount(DIM_X) == 0 && histogram.getBinCount(DIM_Y) > 0;
        } else {
            isVerticalDataSet = !isDataSetSorted(ds, DIM_X) && isDataSetSorted(ds, DIM_Y); // if sorted both in X and Y -> default to horizontal
        }
        return isVerticalDataSet;
    }

    private static double getAxisMin(final Axis xAxis, final Axis yAxis, final boolean isHorizontalDataSet) { // NOPMD NOSONAR -- unavoidable complexity
        final double xMin = Math.min(xAxis.getMin(), xAxis.getMax());
        final double yMin = Math.min(yAxis.getMin(), yAxis.getMax());

        if (isHorizontalDataSet) {
            // horizontal DataSet - draw bars/filling towards y-axis (N.B. most common case)
            if (yAxis.isLogAxis()) {
                // draws axis towards the bottom side or  -- if axis is inverted towards the top side
                return yAxis.isInvertedAxis() ? 0.0 : yAxis.getLength();
            } else {
                return yAxis.isInvertedAxis() ? Math.max(yAxis.getDisplayPosition(0), yAxis.getDisplayPosition(yMin)) : Math.min(yAxis.getDisplayPosition(0), yAxis.getDisplayPosition(yMin));
            }
        }

        // vertical DataSet - draw bars/filling towards y-axis
        if (xAxis.isLogAxis()) {
            // draws axis towards the left side or  -- if axis is inverted towards the right side
            return xAxis.isInvertedAxis() ? xAxis.getLength() : 0.0;
        } else {
            return yAxis.isInvertedAxis() ? Math.min(xAxis.getDisplayPosition(0), xAxis.getDisplayPosition(xMin)) : Math.max(xAxis.getDisplayPosition(0), xAxis.getDisplayPosition(xMin));
        }
    }
}
