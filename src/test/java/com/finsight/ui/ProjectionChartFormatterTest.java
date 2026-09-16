package com.finsight.ui;

import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link ProjectionChartFormatter} — no JavaFX toolkit, no database, no
 * {@code CompositionRoot}. Every input is a hand-built {@link Projection}, so these tests verify
 * only the projection-to-chart-point transformation itself: that it produces the right number of
 * points, preserves the projection's own order and dates, and preserves
 * {@link MonthlyFinancialState#getNetWorth()} verbatim — never recomputing it.
 *
 * <p>{@link ProjectionChartView} (the actual JavaFX {@code LineChart} this formatter feeds) is not
 * exercised here: it is a JavaFX {@code Node} subclass, and this project has no JavaFX toolkit
 * available in its sandbox/CI-less build environment and no headless JavaFX test setup (TestFX or
 * otherwise), so — per this milestone's own testing guidance — its behavior (two series once a
 * scenario has run; replacing rather than duplicating the scenario series on a second run) is
 * instead verified by {@code ProjectionChartViewTest} (compiled and run only under the real Maven
 * build, exactly like {@code FinSightApplicationTest}) and by manual GUI verification.
 */
class ProjectionChartFormatterTest {

    private static MonthlyFinancialState state(LocalDate date, BigDecimal netWorth) {
        return new MonthlyFinancialState(
                date, netWorth, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, netWorth, BigDecimal.ZERO);
    }

    private static List<MonthlyFinancialState> twelveDeterministicDemoStates(LocalDate asOfDate) {
        BigDecimal income = new BigDecimal("60000.00");
        BigDecimal expenses = new BigDecimal("40000.00");
        BigDecimal netWorth = new BigDecimal("150000.00");
        List<MonthlyFinancialState> states = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            netWorth = netWorth.add(income).subtract(expenses);
            states.add(state(asOfDate.plusMonths(month), netWorth));
        }
        return states;
    }

    @Test
    void aTwelveMonthProjectionProducesExactlyTwelveChartPoints() {
        List<MonthlyFinancialState> states = twelveDeterministicDemoStates(LocalDate.of(2026, 4, 1));
        Projection projection = new Projection(states.get(0).getDate(), states.get(states.size() - 1).getDate(), states);

        List<ProjectionChartFormatter.ChartPoint> points = ProjectionChartFormatter.toChartPoints(projection);

        assertEquals(12, points.size());
    }

    @Test
    void chartPointsPreserveTheDatesAndOrderOfTheProjection() {
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        List<MonthlyFinancialState> states = twelveDeterministicDemoStates(asOfDate);
        Projection projection = new Projection(states.get(0).getDate(), states.get(states.size() - 1).getDate(), states);

        List<ProjectionChartFormatter.ChartPoint> points = ProjectionChartFormatter.toChartPoints(projection);

        List<String> expectedLabels = List.of(
                "May 2026", "Jun 2026", "Jul 2026", "Aug 2026", "Sep 2026", "Oct 2026",
                "Nov 2026", "Dec 2026", "Jan 2027", "Feb 2027", "Mar 2027", "Apr 2027");
        List<String> actualLabels = points.stream().map(ProjectionChartFormatter.ChartPoint::label).toList();
        assertEquals(expectedLabels, actualLabels,
                "labels must be in the same order as Projection.getStates(), one per simulated month's actual date");
    }

    @Test
    void chartPointsPreserveTheNetWorthValuesFromMonthlyFinancialState() {
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        List<MonthlyFinancialState> states = twelveDeterministicDemoStates(asOfDate);
        Projection projection = new Projection(states.get(0).getDate(), states.get(states.size() - 1).getDate(), states);

        List<ProjectionChartFormatter.ChartPoint> points = ProjectionChartFormatter.toChartPoints(projection);

        // Reproduces the 5E/5F deterministic demo figures: month 1 = 170,000.00, month 12 = 390,000.00.
        assertEquals(0, new BigDecimal("170000.00").compareTo(points.get(0).netWorth()));
        assertEquals(0, new BigDecimal("390000.00").compareTo(points.get(11).netWorth()));
        for (int i = 0; i < states.size(); i++) {
            assertEquals(0, states.get(i).getNetWorth().compareTo(points.get(i).netWorth()),
                    "chart point " + i + " must carry the exact net worth MonthlyFinancialState already computed, not a recalculated value");
        }
    }

    /**
     * Demonstrates the data behind "a baseline + scenario result produces two series": the
     * baseline and scenario projections of a real 5F-style comparison are each transformed
     * independently by this same, single formatter method, each still producing 12 points, and
     * with genuinely different final-month net worth — exactly the two independent point lists
     * {@link ProjectionChartView} plots as its "Baseline" and "Scenario" series.
     */
    @Test
    void baselineAndScenarioProjectionsProduceIndependentTwelvePointSeriesWithDifferentValues() {
        LocalDate asOfDate = LocalDate.of(2026, 4, 1);
        List<MonthlyFinancialState> baselineStates = twelveDeterministicDemoStates(asOfDate);
        Projection baseline = new Projection(baselineStates.get(0).getDate(), baselineStates.get(11).getDate(), baselineStates);

        BigDecimal scenarioIncome = new BigDecimal("60000.00");
        BigDecimal scenarioExpenses = new BigDecimal("50000.00");
        BigDecimal scenarioNetWorth = new BigDecimal("150000.00");
        List<MonthlyFinancialState> scenarioStates = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            scenarioNetWorth = scenarioNetWorth.add(scenarioIncome).subtract(scenarioExpenses);
            scenarioStates.add(state(asOfDate.plusMonths(month), scenarioNetWorth));
        }
        Projection scenario = new Projection(scenarioStates.get(0).getDate(), scenarioStates.get(11).getDate(), scenarioStates);

        List<ProjectionChartFormatter.ChartPoint> baselinePoints = ProjectionChartFormatter.toChartPoints(baseline);
        List<ProjectionChartFormatter.ChartPoint> scenarioPoints = ProjectionChartFormatter.toChartPoints(scenario);

        assertEquals(12, baselinePoints.size());
        assertEquals(12, scenarioPoints.size());
        assertEquals(0, new BigDecimal("390000.00").compareTo(baselinePoints.get(11).netWorth()));
        assertEquals(0, new BigDecimal("270000.00").compareTo(scenarioPoints.get(11).netWorth()));
        assertNotEquals(0, baselinePoints.get(11).netWorth().compareTo(scenarioPoints.get(11).netWorth()),
                "the two series must be genuinely distinguishable, not the same values plotted twice");
    }

    @Test
    void rejectsANullProjection() {
        assertThrows(NullPointerException.class, () -> ProjectionChartFormatter.toChartPoints(null));
    }
}
