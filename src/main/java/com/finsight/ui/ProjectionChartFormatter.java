package com.finsight.ui;

import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Transforms an already-computed {@link Projection} into chart-ready points, one
 * {@link ChartPoint} per {@link MonthlyFinancialState}, in the same order the projection produced
 * them.
 *
 * <p>This class performs no financial calculation and holds no JavaFX dependency: every point's
 * label and net worth are read verbatim from {@link MonthlyFinancialState#getDate()} and
 * {@link MonthlyFinancialState#getNetWorth()} — nothing is recomputed, re-derived, or rounded
 * beyond turning a date into a readable axis label. Being JavaFX-independent, it can be
 * unit-tested directly without a JavaFX runtime; {@link ProjectionChartView} is the only class
 * that turns this output into a real JavaFX chart series.
 *
 * <p>The month label uses a fixed {@link Locale#ENGLISH} so it is deterministic regardless of the
 * running JVM's default locale, matching this project's convention of deterministic, testable
 * presentation output (see {@code SimulationResultFormatter}).
 */
public final class ProjectionChartFormatter {

    private static final DateTimeFormatter MONTH_LABEL_FORMAT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private ProjectionChartFormatter() {
        // stateless utility class; not instantiable
    }

    /**
     * @param projection an already-computed projection (baseline or scenario) — never re-simulated
     *     or otherwise modified here
     * @return one {@link ChartPoint} per {@link MonthlyFinancialState} in {@code projection}'s
     *     {@link Projection#getStates()}, in the same order, labeled by that month's date and
     *     valued by that month's net worth
     * @throws NullPointerException if {@code projection} is {@code null}
     */
    public static List<ChartPoint> toChartPoints(Projection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        return projection.getStates().stream()
                .map(state -> new ChartPoint(state.getDate().format(MONTH_LABEL_FORMAT), state.getNetWorth()))
                .collect(Collectors.toList());
    }

    /**
     * One chart-ready point: a simulated month's axis label and its net worth, taken verbatim from
     * an existing {@link MonthlyFinancialState} — no arithmetic performed here.
     */
    public record ChartPoint(String label, BigDecimal netWorth) {
        public ChartPoint {
            Objects.requireNonNull(label, "label must not be null");
            Objects.requireNonNull(netWorth, "netWorth must not be null");
        }
    }
}
