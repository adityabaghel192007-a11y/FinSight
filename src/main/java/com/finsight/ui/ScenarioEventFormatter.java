package com.finsight.ui;

import com.finsight.domain.investment.Investment;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.InvestmentReturnChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.ScenarioEvent;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;

/**
 * Turns a single {@link ScenarioEvent} — of any of the five concrete kinds the sealed type
 * permits — into one concise, human-readable line of display text, for the Decision Lab's pending
 * events list (Milestone 6B). This is the same kind of job every other formatter in this package
 * already does ({@code InvestmentFormatter}, {@code FinancialDataFormatter},
 * {@code SimulationResultFormatter}): read already-computed/already-validated data and produce
 * display text, with no financial calculation, no JavaFX dependency, and no persistence of its
 * own — independently unit-testable exactly like those.
 *
 * <p><b>Investment name resolution:</b> {@link InvestmentContributionChangeEvent} and
 * {@link InvestmentReturnChangeEvent} carry only an {@code investmentId} (see each class's own
 * documentation on why — resolving it is deliberately left to the caller). This class is given an
 * already-built {@code Map<Long, String>} of investment id to name — built by the caller from
 * whatever {@link Investment} list it already has in hand (the same "build a lookup map once,
 * reuse it per row" approach {@code InvestmentFormatter#toInvestmentRows} already uses for account
 * names) — rather than taking a repository or application service itself, keeping this class free
 * of any dependency beyond the JDK and the {@code com.finsight.simulation.scenario} types it
 * formats. An id absent from the map (not expected in practice, since a pending event can only be
 * built from an investment the UI already offered — but this method never throws for a display
 * concern) falls back to {@code "Unknown investment"}, matching
 * {@code InvestmentFormatter#toInvestmentRows}'s identical fallback convention for an unresolvable
 * account.
 *
 * <p><b>No currency symbol for investment-targeting amounts:</b> {@link Investment} figures are
 * shown elsewhere in this application (see {@code InvestmentFormatter}) in the investment's own
 * currency (₹ for INR, $ for USD) — but the two investment-targeting event types here carry only a
 * bare {@link BigDecimal}, and this class is given only investment <em>names</em>, not currencies.
 * Rather than assume INR (which would be wrong for a USD-denominated investment) or expand this
 * formatter's contract to require a currency lookup as well, this class simply omits a currency
 * symbol for those two figures, formatting only the number itself. Household-level events
 * ({@link IncomeChangeEvent}, {@link ExpenseChangeEvent}, {@link OneTimeExpenseEvent}) are always
 * INR (the {@code FinancialProfile} they change has no other currency), so those are shown with
 * the same ₹ prefix used everywhere else in this application for profile-level money.
 *
 * <p>{@link InvestmentReturnChangeEvent#getNewExpectedAnnualReturn()} is a raw decimal rate (e.g.
 * {@code 0.09} for 9%, matching {@link Investment#getExpectedAnnualReturn()}'s own convention — see
 * that event's documentation) — this class displays it exactly as given, via
 * {@link BigDecimal#stripTrailingZeros()}/{@link BigDecimal#toPlainString()} (the same
 * no-calculation formatting technique {@code InvestmentFormatter#formatQuantity} already uses for
 * a quantity), never multiplied into a percentage — multiplying is itself a small calculation this
 * presentation-only class should not perform.
 */
public final class ScenarioEventFormatter {

    private static final String UNKNOWN_INVESTMENT = "Unknown investment";

    private ScenarioEventFormatter() {
        // stateless utility class; not instantiable
    }

    /**
     * Formats {@code event} as one concise, human-readable line, e.g.:
     * <ul>
     *   <li>{@code "Change monthly income to ₹90,000.00 — effective 2026-10-01"}</li>
     *   <li>{@code "Change monthly expenses to ₹50,000.00 — effective 2026-11-01"}</li>
     *   <li>{@code "One-time expense of ₹15,000.00 — effective 2026-12-01"}</li>
     *   <li>{@code "Nifty Index Fund — change monthly contribution to 8,000.00/month — effective 2027-01-01"}</li>
     *   <li>{@code "Nifty Index Fund — change expected annual return to 0.12 — effective 2027-01-01"}</li>
     * </ul>
     *
     * @param event the event to format — must be one of the five permitted {@link ScenarioEvent}
     *     subtypes; the exhaustive {@code switch} below is the compiler's guarantee that a sixth
     *     kind can never silently fall through unformatted
     * @param investmentNamesById investment id to name, for the two investment-targeting event
     *     types — see this class's own documentation on how the fallback works
     * @throws NullPointerException if either argument is {@code null}
     */
    public static String formatOneLine(ScenarioEvent event, Map<Long, String> investmentNamesById) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(investmentNamesById, "investmentNamesById must not be null");

        return switch (event) {
            case IncomeChangeEvent e -> "Change monthly income to " + formatRupees(e.getNewMonthlyIncome())
                    + " — effective " + e.getEffectiveDate();
            case ExpenseChangeEvent e -> "Change monthly expenses to " + formatRupees(e.getNewMonthlyExpenses())
                    + " — effective " + e.getEffectiveDate();
            case OneTimeExpenseEvent e -> "One-time expense of " + formatRupees(e.getAmount())
                    + " — effective " + e.getEffectiveDate();
            case InvestmentContributionChangeEvent e -> investmentName(e.getInvestmentId(), investmentNamesById)
                    + " — change monthly contribution to " + formatPlain(e.getNewMonthlyContribution()) + "/month"
                    + " — effective " + e.getEffectiveDate();
            case InvestmentReturnChangeEvent e -> investmentName(e.getInvestmentId(), investmentNamesById)
                    + " — change expected annual return to " + formatRate(e.getNewExpectedAnnualReturn())
                    + " — effective " + e.getEffectiveDate();
        };
    }

    private static String investmentName(long investmentId, Map<Long, String> investmentNamesById) {
        return investmentNamesById.getOrDefault(investmentId, UNKNOWN_INVESTMENT);
    }

    private static String formatRupees(BigDecimal amount) {
        return "₹" + formatPlain(amount);
    }

    private static String formatPlain(BigDecimal amount) {
        return String.format("%,.2f", amount);
    }

    private static String formatRate(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString();
    }
}
