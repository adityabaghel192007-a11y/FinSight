package com.finsight.simulation.scenario;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Cross-cutting behavior shared by every {@link ScenarioEvent} subtype: the common
 * {@code effectiveDate} field, exhaustive handling of the sealed hierarchy, and that events of
 * different concrete types are never equal even when they happen to share an effective date.
 */
class ScenarioEventTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 1);

    @Test
    void everyEventTypeExposesItsEffectiveDate() {
        List<ScenarioEvent> events = List.of(
                new IncomeChangeEvent(DATE, BigDecimal.TEN),
                new ExpenseChangeEvent(DATE, BigDecimal.TEN),
                new InvestmentContributionChangeEvent(DATE, 1L, BigDecimal.TEN),
                new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.1")),
                new OneTimeExpenseEvent(DATE, BigDecimal.TEN));

        for (ScenarioEvent event : events) {
            assertEquals(DATE, event.getEffectiveDate());
        }
    }

    @Test
    void sealedHierarchySupportsExhaustiveSwitchOverAllFiveTypes() {
        // This method is expected to compile without a `default` branch: the compiler proves the
        // switch exhaustive purely from ScenarioEvent's sealed `permits` list. If a future event
        // type is added to that list without a corresponding case here, this file stops compiling
        // — a compile-time safety net rather than a runtime one.
        assertEquals("income", kindOf(new IncomeChangeEvent(DATE, BigDecimal.TEN)));
        assertEquals("expense", kindOf(new ExpenseChangeEvent(DATE, BigDecimal.TEN)));
        assertEquals("contribution", kindOf(new InvestmentContributionChangeEvent(DATE, 1L, BigDecimal.TEN)));
        assertEquals("return", kindOf(new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.1"))));
        assertEquals("onetime", kindOf(new OneTimeExpenseEvent(DATE, BigDecimal.TEN)));
    }

    private static String kindOf(ScenarioEvent event) {
        return switch (event) {
            case IncomeChangeEvent e -> "income";
            case ExpenseChangeEvent e -> "expense";
            case InvestmentContributionChangeEvent e -> "contribution";
            case InvestmentReturnChangeEvent e -> "return";
            case OneTimeExpenseEvent e -> "onetime";
        };
    }

    @Test
    void differentConcreteEventTypesAreNeverEqualEvenWithTheSameEffectiveDateAndAmount() {
        BigDecimal amount = new BigDecimal("1000");
        IncomeChangeEvent income = new IncomeChangeEvent(DATE, amount);
        ExpenseChangeEvent expense = new ExpenseChangeEvent(DATE, amount);
        OneTimeExpenseEvent oneTime = new OneTimeExpenseEvent(DATE, amount);

        assertNotEquals(income, expense);
        assertNotEquals(income, oneTime);
        assertNotEquals(expense, oneTime);
    }
}
