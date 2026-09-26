package com.finsight.ui;

import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.InvestmentReturnChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link ScenarioEventFormatter} — no JavaFX toolkit, no database, no
 * {@code CompositionRoot}, matching {@code InvestmentFormatterTest}'s exact style. Every input is
 * a hand-built {@link com.finsight.simulation.scenario.ScenarioEvent}; the investment-name map is
 * hand-built too, since resolving it from a real {@code Investment} list is the caller's job, not
 * this formatter's (see its own documentation).
 */
class ScenarioEventFormatterTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 1);

    // ===================== IncomeChangeEvent =====================

    @Test
    void formatsAnIncomeChangeEvent() {
        IncomeChangeEvent event = new IncomeChangeEvent(DATE, new BigDecimal("90000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of());

        assertEquals("Change monthly income to ₹90,000.00 — effective 2026-10-01", line);
    }

    // ===================== ExpenseChangeEvent =====================

    @Test
    void formatsAnExpenseChangeEvent() {
        ExpenseChangeEvent event = new ExpenseChangeEvent(DATE, new BigDecimal("50000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of());

        assertEquals("Change monthly expenses to ₹50,000.00 — effective 2026-10-01", line);
    }

    // ===================== OneTimeExpenseEvent =====================

    @Test
    void formatsAOneTimeExpenseEvent() {
        OneTimeExpenseEvent event = new OneTimeExpenseEvent(DATE, new BigDecimal("15000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of());

        assertEquals("One-time expense of ₹15,000.00 — effective 2026-10-01", line);
    }

    // ===================== InvestmentContributionChangeEvent =====================

    @Test
    void formatsAnInvestmentContributionChangeEventWithAResolvedInvestmentName() {
        InvestmentContributionChangeEvent event =
                new InvestmentContributionChangeEvent(DATE, 7L, new BigDecimal("20000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of(7L, "NVIDIA"));

        assertEquals("NVIDIA — change monthly contribution to 20,000.00/month — effective 2026-10-01", line,
                "no currency symbol for an investment-targeting amount — see the formatter's own documentation");
    }

    // ===================== InvestmentReturnChangeEvent =====================

    @Test
    void formatsAnInvestmentReturnChangeEventWithARawDecimalRateAndNoPercentageConversion() {
        InvestmentReturnChangeEvent event = new InvestmentReturnChangeEvent(DATE, 7L, new BigDecimal("0.1200"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of(7L, "NVIDIA"));

        assertEquals("NVIDIA — change expected annual return to 0.12 — effective 2026-10-01", line,
                "the rate is shown exactly as given (trailing zeros stripped), never multiplied into a percentage");
    }

    // ===================== investment name resolution =====================

    @Test
    void resolvesTheInvestmentNameFromTheSuppliedMap() {
        InvestmentContributionChangeEvent event =
                new InvestmentContributionChangeEvent(DATE, 3L, new BigDecimal("5000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of(3L, "Nifty Index Fund", 9L, "Other Fund"));

        assertEquals("Nifty Index Fund — change monthly contribution to 5,000.00/month — effective 2026-10-01", line);
    }

    // ===================== unknown investment fallback =====================

    @Test
    void fallsBackToAClearPlaceholderWhenTheInvestmentIdCannotBeResolved() {
        InvestmentContributionChangeEvent event =
                new InvestmentContributionChangeEvent(DATE, 99L, new BigDecimal("5000.00"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of(1L, "Some Other Fund"));

        assertEquals("Unknown investment — change monthly contribution to 5,000.00/month — effective 2026-10-01", line);
    }

    @Test
    void fallsBackToAClearPlaceholderWhenGivenAnEmptyMap() {
        InvestmentReturnChangeEvent event = new InvestmentReturnChangeEvent(DATE, 1L, new BigDecimal("0.08"));

        String line = ScenarioEventFormatter.formatOneLine(event, Map.of());

        assertEquals("Unknown investment — change expected annual return to 0.08 — effective 2026-10-01", line,
                "must not throw for an unresolvable id — a display fallback, not an exception");
    }

    // ===================== null handling =====================

    @Test
    void rejectsANullEvent() {
        assertThrows(NullPointerException.class, () -> ScenarioEventFormatter.formatOneLine(null, Map.of()));
    }

    @Test
    void rejectsANullInvestmentNameMap() {
        IncomeChangeEvent event = new IncomeChangeEvent(DATE, BigDecimal.ZERO);

        assertThrows(NullPointerException.class, () -> ScenarioEventFormatter.formatOneLine(event, null));
    }
}
