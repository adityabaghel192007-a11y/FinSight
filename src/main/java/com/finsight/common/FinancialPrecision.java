package com.finsight.common;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The project's single, canonical financial precision/rounding policy: every calculator and
 * simulation class that produces a monetary or rate value should use these constants and
 * {@link #roundMoney(BigDecimal)} rather than declaring its own copy.
 *
 * <p>Before this class existed, {@code MONEY_SCALE}, {@code MONEY_ROUNDING}, and (where a
 * high-precision intermediate division or power was needed) {@code CALCULATION_CONTEXT} were each
 * declared independently — identically — in {@code InvestmentCalculator}, {@code LoanCalculator},
 * {@code GoalCalculator}, {@code NetWorthCalculator}, {@code SimulationEngine},
 * {@code ScenarioComparison}, and {@code ScenarioComparisonCalculator}. This class centralizes that
 * policy so it is declared exactly once; it does not change the policy itself in any way — the
 * values and rounding behavior here are byte-for-byte identical to what every one of those classes
 * already declared and used.
 *
 * <p><b>Rounding policy:</b>
 * <ul>
 *   <li>{@link #CALCULATION_CONTEXT} — used for intermediate divisions/powers that generally do
 *       not terminate (loan EMI, investment monthly-rate nth-root, goal required-contribution
 *       division, percentage-change ratios), so that rounding error does not compound before a
 *       final result is produced.</li>
 *   <li>{@link #MONEY_SCALE} / {@link #MONEY_ROUNDING}, applied via {@link #roundMoney(BigDecimal)}
 *       — used exactly once, at the point a value becomes an externally-visible monetary amount
 *       (never for rates, which stay at full calculation precision).</li>
 * </ul>
 *
 * <p>This class is stateless and depends on nothing beyond the JDK — no domain classes, no
 * JavaFX, no JDBC, no persistence — matching every calculator in this project.
 */
public final class FinancialPrecision {

    /**
     * The number of decimal places every externally-visible monetary value is rounded to.
     */
    public static final int MONEY_SCALE = 2;

    /**
     * The rounding mode applied when a monetary value is scaled to {@link #MONEY_SCALE}.
     */
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    /**
     * The precision used for intermediate calculations (divisions, powers, roots) that generally
     * do not terminate, so that rounding error does not compound before {@link #roundMoney} is
     * applied to the final, externally-visible result.
     */
    public static final MathContext CALCULATION_CONTEXT = new MathContext(34, RoundingMode.HALF_UP);

    private FinancialPrecision() {
        // stateless utility class; not instantiable
    }

    /**
     * Rounds {@code value} to {@link #MONEY_SCALE} decimal places using {@link #MONEY_ROUNDING}.
     * The single point every calculator in this project should use to produce an
     * externally-visible monetary amount.
     */
    public static BigDecimal roundMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }
}
