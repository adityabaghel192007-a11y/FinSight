package com.finsight.simulation.calculator;

import com.finsight.common.FinancialPrecision;
import com.finsight.domain.goal.Goal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Computes the remaining amount, months remaining, and required monthly contribution for a
 * {@link Goal} (an MVP calculation: it does not assume any investment return on the money
 * already saved or on future contributions — see {@link #calculateRequiredMonthlyContribution}).
 *
 * <p>This class is stateless: it holds no goal data of its own, only pure functions of a
 * {@link Goal} and, where needed, an explicitly supplied reference date. It never calls
 * {@link LocalDate#now()} — the caller always supplies {@code asOfDate} — so results are
 * deterministic and testable. It depends on nothing outside the JDK and
 * {@code com.finsight.domain.goal} — no JavaFX, no JDBC, no persistence. It does not modify or
 * re-validate {@link Goal}; a {@code Goal} instance is already guaranteed valid by its own
 * constructor, including {@code currentAmount <= targetAmount}.
 *
 * <p><b>Month-counting convention:</b> {@link #calculateMonthsRemaining} uses
 * {@link ChronoUnit#MONTHS}{@code .between(asOfDate, targetDate)} — the JDK's own "complete
 * months" measure. It counts a month only once {@code asOfDate}'s day-of-month number has been
 * reached again (e.g. Jan 15 to Mar 14 is 1 complete month, Jan 15 to Mar 15 is 2). Because it
 * compares day-of-month numbers directly rather than "does adding a month overshoot", a target
 * date that falls on a shorter month can read as one month short of the naive calendar count
 * (e.g. Jan 31 to Feb 28 is {@code 0}, not {@code 1}, since day 28 is numerically less than day
 * 31) — this is standard {@link ChronoUnit#MONTHS} behavior, called out here rather than worked
 * around. The result is never negative: a target date on or before {@code asOfDate} is clamped
 * to {@code 0}.
 *
 * <p><b>Rounding:</b> follows the same philosophy as {@link LoanCalculator} and
 * {@link InvestmentCalculator}, using the project's shared {@link FinancialPrecision} policy —
 * the division in {@link #calculateRequiredMonthlyContribution} is carried out at high precision
 * ({@link FinancialPrecision#CALCULATION_CONTEXT}), with the result rounded via
 * {@link FinancialPrecision#roundMoney(BigDecimal)} only once it becomes an externally-visible
 * monetary value.
 */
public final class GoalCalculator {

    private GoalCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * {@code targetAmount - currentAmount}, floored at zero. A goal is "already achieved" when
     * {@code currentAmount >= targetAmount} (in practice, {@code ==}, since {@link Goal} already
     * forbids {@code currentAmount > targetAmount}), in which case this returns {@code 0.00}.
     */
    public static BigDecimal calculateRemainingAmount(Goal goal) {
        Objects.requireNonNull(goal, "goal must not be null");

        BigDecimal remaining = goal.getTargetAmount().subtract(goal.getCurrentAmount());
        if (remaining.signum() < 0) {
            remaining = BigDecimal.ZERO;
        }
        return FinancialPrecision.roundMoney(remaining);
    }

    /**
     * The number of complete calendar months between {@code asOfDate} and the goal's target
     * date, per the class-level month-counting convention. Never negative: a target date on or
     * before {@code asOfDate} yields {@code 0}.
     */
    public static long calculateMonthsRemaining(Goal goal, LocalDate asOfDate) {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        long months = ChronoUnit.MONTHS.between(asOfDate, goal.getTargetDate());
        return Math.max(months, 0L);
    }

    /**
     * The flat monthly amount still needed to reach the goal on time:
     * {@code remainingAmount / monthsRemaining}. This is an MVP calculation and deliberately
     * assumes no investment return on savings already accumulated or on future contributions —
     * see {@link InvestmentCalculator} for return-aware projections.
     *
     * <p>Two cases are handled explicitly rather than dividing by zero or a negative number:
     * <ul>
     *   <li>If the goal is already achieved ({@link #calculateRemainingAmount} is zero), this
     *       returns {@code 0.00} regardless of the target date.</li>
     *   <li>Otherwise, if fewer than one full month remains — including a target date on or
     *       before {@code asOfDate} — the divisor is floored at {@code 1}: the entire remaining
     *       amount is treated as due in a single next payment, rather than being undefined.</li>
     * </ul>
     */
    public static BigDecimal calculateRequiredMonthlyContribution(Goal goal, LocalDate asOfDate) {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");

        BigDecimal remainingAmount = calculateRemainingAmount(goal);
        if (remainingAmount.signum() == 0) {
            return FinancialPrecision.roundMoney(BigDecimal.ZERO);
        }

        long monthsRemaining = calculateMonthsRemaining(goal, asOfDate);
        long divisor = Math.max(monthsRemaining, 1L);

        return FinancialPrecision.roundMoney(remainingAmount.divide(BigDecimal.valueOf(divisor), FinancialPrecision.CALCULATION_CONTEXT));
    }
}
