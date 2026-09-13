package com.finsight.simulation.calculator;

import com.finsight.domain.goal.Goal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 15);

    private static Goal goal(String target, String current, LocalDate targetDate) {
        return new Goal(1L, "Test Goal", new BigDecimal(target), new BigDecimal(current), targetDate, BigDecimal.ZERO);
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // 1. Remaining amount
    @Test
    void remainingAmountIsTargetMinusCurrent() {
        Goal goal = goal("500000", "100000", AS_OF.plusMonths(12));

        assertMoneyEquals("400000.00", GoalCalculator.calculateRemainingAmount(goal));
    }

    // 2. Already-achieved goal
    @Test
    void alreadyAchievedGoalHasZeroRemainingAndZeroContribution() {
        Goal goal = goal("500000", "500000", AS_OF.minusYears(6)); // achieved AND overdue

        assertMoneyEquals("0.00", GoalCalculator.calculateRemainingAmount(goal));
        assertMoneyEquals("0.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 3. Required monthly contribution (straightforward)
    @Test
    void requiredMonthlyContributionDividesRemainingByMonthsRemaining() {
        Goal goal = goal("120000", "0", AS_OF.plusMonths(12));

        assertMoneyEquals("10000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 4. One-month remaining goal
    @Test
    void oneMonthRemainingRequiresEntireRemainingAmountThatMonth() {
        Goal goal = goal("5000", "1000", AS_OF.plusMonths(1));

        assertEquals(1L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        assertMoneyEquals("4000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 5. Exact division
    @Test
    void exactDivisionProducesNoRoundingArtifact() {
        Goal goal = goal("120000", "0", AS_OF.plusMonths(12));

        assertMoneyEquals("10000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 6. Division requiring rounding
    @Test
    void divisionRequiringRoundingUsesHalfUp() {
        Goal goal = goal("100000", "0", AS_OF.plusMonths(3));

        assertEquals(3L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        // 100000 / 3 = 33333.3333... -> HALF_UP -> 33333.33
        assertMoneyEquals("33333.33", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 7. Large monetary values
    @Test
    void largeMonetaryValuesAreHandledCorrectly() {
        Goal goal = goal("100000000.00", "0", AS_OF.plusMonths(100));

        assertEquals(100L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        assertMoneyEquals("1000000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 8. Fractional monetary values
    @Test
    void fractionalMonetaryValuesAreHandledPrecisely() {
        Goal goal = goal("10000.37", "2500.12", AS_OF.plusMonths(7));

        assertMoneyEquals("7500.25", GoalCalculator.calculateRemainingAmount(goal));
        assertEquals(7L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        // 7500.25 / 7 = 1071.464285714... -> HALF_UP -> 1071.46
        assertMoneyEquals("1071.46", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 9. Deterministic result with explicitly supplied asOfDate
    @Test
    void resultIsDeterministicForTheSameInputs() {
        Goal goal = goal("500000", "100000", AS_OF.plusMonths(12));

        BigDecimal first = GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF);
        BigDecimal second = GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF);

        assertEquals(0, first.compareTo(second));
        assertEquals(GoalCalculator.calculateMonthsRemaining(goal, AS_OF), GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
    }

    // 10. Target date equal to asOfDate
    @Test
    void targetDateEqualToAsOfDateTreatsWholeAmountAsDueNow() {
        Goal goal = goal("5000", "0", AS_OF);

        assertEquals(0L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        assertMoneyEquals("5000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 11. Target date before asOfDate (overdue, not yet achieved)
    @Test
    void targetDateBeforeAsOfDateClampsMonthsToZeroNotNegative() {
        Goal goal = goal("5000", "1000", AS_OF.minusMonths(3));

        assertEquals(0L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
        assertMoneyEquals("4000.00", GoalCalculator.calculateRequiredMonthlyContribution(goal, AS_OF));
    }

    // 12. Null Goal
    @Test
    void rejectsNullGoal() {
        assertThrows(NullPointerException.class, () -> GoalCalculator.calculateRemainingAmount(null));
        assertThrows(NullPointerException.class, () -> GoalCalculator.calculateMonthsRemaining(null, AS_OF));
        assertThrows(NullPointerException.class, () -> GoalCalculator.calculateRequiredMonthlyContribution(null, AS_OF));
    }

    // 13. Null asOfDate
    @Test
    void rejectsNullAsOfDate() {
        Goal goal = goal("5000", "0", AS_OF.plusMonths(1));

        assertThrows(NullPointerException.class, () -> GoalCalculator.calculateMonthsRemaining(goal, null));
        assertThrows(NullPointerException.class, () -> GoalCalculator.calculateRequiredMonthlyContribution(goal, null));
    }

    // 14. No negative required contribution
    @Test
    void requiredContributionIsNeverNegative() {
        Goal achieved = goal("5000", "5000", AS_OF.minusYears(1));
        Goal overdue = goal("5000", "1000", AS_OF.minusMonths(6));
        Goal sameDay = goal("5000", "0", AS_OF);
        Goal normal = goal("5000", "1000", AS_OF.plusMonths(4));

        assertTrue(GoalCalculator.calculateRequiredMonthlyContribution(achieved, AS_OF).signum() >= 0);
        assertTrue(GoalCalculator.calculateRequiredMonthlyContribution(overdue, AS_OF).signum() >= 0);
        assertTrue(GoalCalculator.calculateRequiredMonthlyContribution(sameDay, AS_OF).signum() >= 0);
        assertTrue(GoalCalculator.calculateRequiredMonthlyContribution(normal, AS_OF).signum() >= 0);
    }

    // 15. Zero remaining amount produces zero required contribution (independent of date)
    @Test
    void zeroRemainingAmountShortCircuitsToZeroRegardlessOfDate() {
        Goal achievedButOverdue = goal("5000", "5000", AS_OF.minusYears(10));
        Goal achievedFarInFuture = goal("5000", "5000", AS_OF.plusYears(10));

        assertMoneyEquals("0.00", GoalCalculator.calculateRequiredMonthlyContribution(achievedButOverdue, AS_OF));
        assertMoneyEquals("0.00", GoalCalculator.calculateRequiredMonthlyContribution(achievedFarInFuture, AS_OF));
    }

    // ===== Month-counting convention =====

    @Test
    void monthConventionCountsOnlyCompleteMonthsByDayOfMonth() {
        Goal oneDayShort = goal("10", "0", LocalDate.of(2026, 3, 14));
        Goal exactlyTwoMonths = goal("10", "0", LocalDate.of(2026, 3, 15));

        assertEquals(1L, GoalCalculator.calculateMonthsRemaining(oneDayShort, LocalDate.of(2026, 1, 15)));
        assertEquals(2L, GoalCalculator.calculateMonthsRemaining(exactlyTwoMonths, LocalDate.of(2026, 1, 15)));
    }

    @Test
    void monthConventionMonthEndDayOfMonthComparisonQuirkIsDocumentedBehavior() {
        // Jan 31 -> Feb 28 (2026 is not a leap year): day 28 < day 31 numerically, so this
        // reads as 0 complete months under ChronoUnit.MONTHS, not 1 - this is intentional,
        // documented JDK behavior, not a bug in this calculator.
        Goal goal = goal("10", "0", LocalDate.of(2026, 2, 28));

        assertEquals(0L, GoalCalculator.calculateMonthsRemaining(goal, LocalDate.of(2026, 1, 31)));
    }

    @Test
    void sameDateProducesZeroMonthsRemaining() {
        Goal goal = goal("10", "0", AS_OF);

        assertEquals(0L, GoalCalculator.calculateMonthsRemaining(goal, AS_OF));
    }
}
