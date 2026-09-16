package com.finsight.ui;

import com.finsight.simulation.model.Projection;

import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A 12-month net-worth line chart built directly from already-computed {@link Projection} data.
 *
 * <p>This class performs no financial calculation of its own and knows nothing about
 * repositories, application services, {@code SimulationEngine}, {@code ScenarioComparisonCalculator},
 * or the database. Every point plotted is exactly a projection month's date and net worth,
 * transformed by {@link ProjectionChartFormatter} — this class's only job is turning that already-
 * computed data into JavaFX chart series.
 *
 * <p>Two fixed {@link XYChart.Series} — named {@value #BASELINE_SERIES_NAME} and
 * {@value #SCENARIO_SERIES_NAME} — are created once, in the constructor, and never replaced or
 * duplicated:
 * <ul>
 *   <li>The baseline series is added to the chart the first time {@link #showBaseline(Projection)}
 *       is called.</li>
 *   <li>The scenario series is added the first time {@link #showScenario(Projection)} is
 *       called.</li>
 *   <li>Calling either method again replaces that same series' points in place — the series'
 *       data list is cleared and repopulated via {@link javafx.collections.ObservableList#setAll}
 *       — rather than adding a second series or appending duplicate points. Re-running a scenario
 *       with a different value therefore updates the existing scenario line instead of
 *       accumulating extra ones.</li>
 * </ul>
 */
public final class ProjectionChartView extends LineChart<String, Number> {

    static final String BASELINE_SERIES_NAME = "Baseline";
    static final String SCENARIO_SERIES_NAME = "Scenario";

    private final XYChart.Series<String, Number> baselineSeries = new XYChart.Series<>();
    private final XYChart.Series<String, Number> scenarioSeries = new XYChart.Series<>();

    private boolean baselineShown;
    private boolean scenarioShown;

    public ProjectionChartView() {
        super(new CategoryAxis(), new NumberAxis());
        setTitle("Net Worth Projection");
        getXAxis().setLabel("Month");
        getYAxis().setLabel("Net Worth (₹)");
        setAnimated(false);
        baselineSeries.setName(BASELINE_SERIES_NAME);
        scenarioSeries.setName(SCENARIO_SERIES_NAME);
    }

    /**
     * Shows the baseline series, or replaces its points in place if it is already shown.
     *
     * @param baseline the baseline projection to plot — its 12 {@code MonthlyFinancialState}s
     *     become exactly 12 chart points, via {@link ProjectionChartFormatter}
     */
    public void showBaseline(Projection baseline) {
        replaceData(baselineSeries, baseline);
        if (!baselineShown) {
            getData().add(baselineSeries);
            baselineShown = true;
        }
    }

    /**
     * Shows the scenario series, or replaces its points in place if it is already shown — running
     * a new scenario updates this same series rather than adding another one.
     *
     * @param scenario the scenario projection to plot — its 12 {@code MonthlyFinancialState}s
     *     become exactly 12 chart points, via {@link ProjectionChartFormatter}
     */
    public void showScenario(Projection scenario) {
        replaceData(scenarioSeries, scenario);
        if (!scenarioShown) {
            getData().add(scenarioSeries);
            scenarioShown = true;
        }
    }

    private static void replaceData(XYChart.Series<String, Number> series, Projection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        List<XYChart.Data<String, Number>> points = ProjectionChartFormatter.toChartPoints(projection).stream()
                .map(point -> new XYChart.Data<String, Number>(point.label(), point.netWorth()))
                .collect(Collectors.toList());
        series.getData().setAll(points);
    }
}
