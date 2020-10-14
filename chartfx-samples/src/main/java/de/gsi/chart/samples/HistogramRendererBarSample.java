package de.gsi.chart.samples;

import static de.gsi.dataset.DataSet.DIM_X;
import static de.gsi.dataset.DataSet.DIM_Y;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.gsi.chart.Chart;
import de.gsi.chart.XYChart;
import de.gsi.chart.axes.spi.CategoryAxis;
import de.gsi.chart.axes.spi.DefaultNumericAxis;
import de.gsi.chart.plugins.DataPointTooltip;
import de.gsi.chart.plugins.EditAxis;
import de.gsi.chart.plugins.TableViewer;
import de.gsi.chart.plugins.Zoomer;
import de.gsi.chart.renderer.LineStyle;
import de.gsi.chart.renderer.spi.ErrorDataSetRenderer;
import de.gsi.chart.renderer.spi.HistogramRenderer;
import de.gsi.chart.ui.geometry.Side;
import de.gsi.dataset.Histogram;
import de.gsi.dataset.utils.ProcessingProfiler;

public class HistogramRendererBarSample extends Application {
    public static final String TAG_REL = " rel.";
    public static final String TAG_ABS = " abs.";
    public static final int NBINS = 19;
    public static final String DEFAULT_COUNTRY = "OECD-total";
    private static final Logger LOGGER = LoggerFactory.getLogger(HistogramRendererBarSample.class);
    private static final String DEMO_DATA_FILE = "testdata/2007_OECD_population_age_distribution.csv";
    private static final String MEN = "men";
    private static final String WOMEN = "women";
    private static final int TAG_1980 = 1980;
    private static final int TAG_2000 = 2000;
    private static final int TAG_2050 = 2050;
    // country,age,men 1980 [%],men 2000  [%],men 2050 [%],women 1980 [%],women 2000 [%],women 2050 [%],men 1980,men 2000,men 2050,women 1980,women 2000,women 2050
    private final Map<String, Map<String, Map<Integer, Histogram>>> relDistribution = new ConcurrentHashMap<>(); // Map<country name, Map<men/women, Map<year,Histogram>>>
    private final Map<String, Map<String, Map<Integer, Histogram>>> absDistribution = new ConcurrentHashMap<>(); // Map<country name, Map<men/women, Map<year,Histogram>>>

    @Override
    public void start(final Stage primaryStage) {
        // initialises Map based on https://data.oecd.org/ data
        loadDemoData(DEMO_DATA_FILE);

        ProcessingProfiler.setVerboseOutputState(true);
        ProcessingProfiler.setLoggerOutputState(true);
        ProcessingProfiler.setDebugState(false);

        Chart chart1 = getDefaultChart("population distribution by gender and age");
        ErrorDataSetRenderer errorDataSetRenderer = (ErrorDataSetRenderer) chart1.getRenderers().get(0);
        errorDataSetRenderer.setAssumeSortedData(false);
        errorDataSetRenderer.setDrawBars(true);
        //errorDataSetRenderer.getDatasets().addAll(relDistribution.get(DEFAULT_COUNTRY).get(WOMEN).get(TAG_1980), relDistribution.get(DEFAULT_COUNTRY).get(MEN).get(TAG_1980));
        final HistogramRenderer histogramRenderer1 = new HistogramRenderer();
        histogramRenderer1.setPolyLineStyle(LineStyle.NORMAL);
        //        chart1.getRenderers().add(histogramRenderer1);
        histogramRenderer1.getDatasets().addAll(relDistribution.get(DEFAULT_COUNTRY).get(WOMEN).get(TAG_1980), relDistribution.get(DEFAULT_COUNTRY).get(MEN).get(TAG_1980));

        final HistogramRenderer histogramRenderer2 = new HistogramRenderer();
        histogramRenderer2.setPolyLineStyle(LineStyle.HISTOGRAM);
        chart1.getRenderers().add(histogramRenderer2);
        histogramRenderer2.getDatasets().addAll(relDistribution.get(DEFAULT_COUNTRY).get(WOMEN).get(TAG_1980), relDistribution.get(DEFAULT_COUNTRY).get(MEN).get(TAG_1980));

        final Histogram hist = relDistribution.get(DEFAULT_COUNTRY).get(WOMEN).get(TAG_1980);
        for (int i = 0; i < hist.getDataCount(); i++) {
            LOGGER.atInfo().addArgument(i).addArgument(hist.get(DIM_X, i)).addArgument(hist.get(DIM_Y, i)).addArgument(hist.getDataLabel(i)).log("{}: x={} y = {} category: '{}'");
        }

        primaryStage.setTitle(this.getClass().getSimpleName());
        primaryStage.setScene(new Scene(chart1, 800, 600));
        primaryStage.setOnCloseRequest(evt -> Platform.exit());
        primaryStage.show();
    }

    private void loadDemoData(final String fileName) {
        try (BufferedReader csvReader = Files.newBufferedReader(Paths.get(this.getClass().getResource(fileName).toURI()))) {
            // skip first row
            String row = csvReader.readLine();
            // country,age,men 1980 [%],men 2000  [%],men 2050 [%],women 1980 [%],women 2000 [%],women 2050 [%],men 1980,men 2000,men 2050,women 1980,women 2000,women 2050
            assert !row.isBlank() && row.contains("country");
            while ((row = csvReader.readLine()) != null) {
                String[] data = row.split(",");
                final String country = data[0];
                final String ageCategory = data[1];

                final Map<String, Map<Integer, Histogram>> relCountryMap = relDistribution.computeIfAbsent(country, c -> new ConcurrentHashMap<>());
                final Map<Integer, Histogram> relWomenDistribution = relCountryMap.computeIfAbsent(WOMEN, g -> new ConcurrentHashMap<>());
                final Map<Integer, Histogram> relMenDistribution = relCountryMap.computeIfAbsent(MEN, g -> new ConcurrentHashMap<>());
                final MyHistogram relWomenDistribution1980 = (MyHistogram) relWomenDistribution.computeIfAbsent(TAG_1980, y -> new MyHistogram(TAG_1980 + ": " + country + " - " + WOMEN + TAG_REL));
                final MyHistogram relMenDistribution1980 = (MyHistogram) relMenDistribution.computeIfAbsent(TAG_1980, y -> new MyHistogram(TAG_1980 + ": " + country + " - " + MEN + TAG_REL));
                final MyHistogram relWomenDistribution2000 = (MyHistogram) relWomenDistribution.computeIfAbsent(TAG_2000, y -> new MyHistogram(TAG_2000 + ": " + country + " - " + WOMEN + TAG_REL));
                final MyHistogram relMenDistribution2000 = (MyHistogram) relMenDistribution.computeIfAbsent(TAG_2000, y -> new MyHistogram(TAG_2000 + ": " + country + " - " + MEN + TAG_REL));
                final MyHistogram relWomenDistribution2050 = (MyHistogram) relWomenDistribution.computeIfAbsent(TAG_2050, y -> new MyHistogram(TAG_2050 + ": " + country + " - " + WOMEN + TAG_REL));
                final MyHistogram relMenDistribution2050 = (MyHistogram) relMenDistribution.computeIfAbsent(TAG_2050, y -> new MyHistogram(TAG_2050 + ": " + country + " - " + MEN + TAG_REL));

                relWomenDistribution1980.fill(ageCategory, Double.parseDouble(data[5]));
                relWomenDistribution2000.fill(ageCategory, Double.parseDouble(data[6]));
                relWomenDistribution2050.fill(ageCategory, Double.parseDouble(data[7]));
                relMenDistribution1980.fill(ageCategory, -Double.parseDouble(data[2]));
                relMenDistribution2000.fill(ageCategory, -Double.parseDouble(data[3]));
                relMenDistribution2050.fill(ageCategory, -Double.parseDouble(data[4]));

                final Map<String, Map<Integer, Histogram>> absCountryMap = absDistribution.computeIfAbsent(country, c -> new ConcurrentHashMap<>());
                final Map<Integer, Histogram> absWomenDistribution = absCountryMap.computeIfAbsent(WOMEN, g -> new ConcurrentHashMap<>());
                final Map<Integer, Histogram> absMenDistribution = absCountryMap.computeIfAbsent(MEN, g -> new ConcurrentHashMap<>());
                final MyHistogram absWomenDistribution1980 = (MyHistogram) absWomenDistribution.computeIfAbsent(TAG_1980, y -> new MyHistogram(TAG_1980 + ": " + country + " - " + WOMEN + TAG_ABS));
                final MyHistogram absMenDistribution1980 = (MyHistogram) absMenDistribution.computeIfAbsent(TAG_1980, y -> new MyHistogram(TAG_1980 + ": " + country + " - " + MEN + TAG_ABS));
                final MyHistogram absWomenDistribution2000 = (MyHistogram) absWomenDistribution.computeIfAbsent(TAG_2000, y -> new MyHistogram(TAG_2000 + ": " + country + " - " + WOMEN + TAG_ABS));
                final MyHistogram absMenDistribution2000 = (MyHistogram) absMenDistribution.computeIfAbsent(TAG_2000, y -> new MyHistogram(TAG_2000 + ": " + country + " - " + MEN + TAG_ABS));
                final MyHistogram absWomenDistribution2050 = (MyHistogram) absWomenDistribution.computeIfAbsent(TAG_2050, y -> new MyHistogram(TAG_2050 + ": " + country + " - " + WOMEN + TAG_ABS));
                final MyHistogram absMenDistribution2050 = (MyHistogram) absMenDistribution.computeIfAbsent(TAG_2050, y -> new MyHistogram(TAG_2050 + ": " + country + " - " + MEN + TAG_REL));

                absWomenDistribution1980.fill(ageCategory, Double.parseDouble(data[11]));
                absWomenDistribution2000.fill(ageCategory, Double.parseDouble(data[12]));
                absWomenDistribution2050.fill(ageCategory, Double.parseDouble(data[13]));
                absMenDistribution1980.fill(ageCategory, -Double.parseDouble(data[8]));
                absMenDistribution2000.fill(ageCategory, -Double.parseDouble(data[9]));
                absMenDistribution2050.fill(ageCategory, -Double.parseDouble(data[10]));
            }
        } catch (IOException | URISyntaxException e) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.atError().setCause(e).log("InterruptedException");
            }
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
        Application.launch(args);
    }

    private static Chart getDefaultChart(final String title) {
        DefaultNumericAxis xAxis = new DefaultNumericAxis("relative distribution", "%");
        xAxis.setAutoRangeRounding(true);
        xAxis.setAutoRangePadding(0.2);

        CategoryAxis yAxis = new CategoryAxis("age group");
        yAxis.setSide(Side.CENTER_VER);

        DefaultNumericAxis yAxis2 = new DefaultNumericAxis("age group", null);
        yAxis2.setSide(Side.CENTER_VER);

        XYChart chart = new XYChart(xAxis, yAxis);
        chart.setTitle(title);
        chart.getPlugins().addAll(new Zoomer(), new DataPointTooltip(), new TableViewer(), new EditAxis());
        HBox.setHgrow(chart, Priority.ALWAYS);
        yAxis.setSide(Side.CENTER_VER);

        return chart;
    }

    public class MyHistogram extends de.gsi.dataset.spi.Histogram {
        public MyHistogram(final String name) {
            super(name, NBINS, 0, NBINS - 1.0, false, HistogramOuterBounds.BINS_CENTERED_ON_BOUNDARY);
            this.addDataLabel(18, "-");
            this.addDataLabel(17, "85+");
            this.addDataLabel(16, "80 - 84");
            this.addDataLabel(15, "75 - 79");
            this.addDataLabel(14, "70 - 74");
            this.addDataLabel(13, "65 - 69");
            this.addDataLabel(12, "60 - 64");
            this.addDataLabel(11, "55 - 59");
            this.addDataLabel(10, "50 - 54");
            this.addDataLabel(9, "45 - 49");
            this.addDataLabel(8, "40 - 44");
            this.addDataLabel(7, "35 - 39");
            this.addDataLabel(6, "30 - 34");
            this.addDataLabel(5, "25 - 29");
            this.addDataLabel(4, "20 - 24");
            this.addDataLabel(3, "15 - 19");
            this.addDataLabel(2, "10 - 14");
            this.addDataLabel(1, "5 - 9");
            this.addDataLabel(0, "0 - 4");
        }

        public void fill(final String dataLabel, final double value) {
            for (int i = 0; i < getDataCount(); i++) {
                if (getDataLabel(i) != null && getDataLabel(i).equals(dataLabel)) {
                    addBinContent(i + 1, value); // +1 because histogram starts with under-flow bin at 'binIndex==0'
                    return;
                }
            }
            LOGGER.atWarn().addArgument(dataLabel).addArgument(value).log("encountered unknown label '{}' -  adding value = {} to under-flow bin");
            addBinContent(0, value); // fill to under-flow bin
        }
    }
}
