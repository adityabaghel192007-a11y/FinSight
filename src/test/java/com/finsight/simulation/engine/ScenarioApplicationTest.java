package com.finsight.simulation.engine;

import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.simulation.calculator.InvestmentCalculator;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.ExpenseChangeEvent;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.InvestmentReturnChangeEvent;
import com.finsight.simulation.scenario.OneTimeExpenseEvent;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.simulation.scenario.ScenarioEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 3F: tests for scenario-event application by {@link SimulationEngine}. Dates are
 * always built via {@code AS_OF.plusMonths(n)} (matching the engine's own timeline convention)
 * rather than hand-picked calendar dates, so a test's expected month boundaries can never drift
 * from the engine's actual date arithmetic.
 */
class ScenarioApplicationTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 1);

    private static Investment investment(long id, Currency currency, BigDecimal quantity, BigDecimal price,
                                          BigDecimal expectedAnnualReturn, BigDecimal monthlyContribution) {
        return new Investment(id, 1L, "Inv" + id, null, AssetType.ETF, currency, quantity, price, price,
                expectedAnnualReturn, monthlyContribution);
    }

    private static Loan loan(long id, BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        // A "new" loan: currentBalance == principal, remainingTenureMonths == tenureMonths.
        return new Loan(id, "Loan" + id, principal, annualRate, tenureMonths, AS_OF.minusYears(1), principal, tenureMonths);
    }

    private static FinancialSnapshot snapshot(BigDecimal cash, BigDecimal income, BigDecimal expenses,
                                               List<Investment> investments, List<Loan> loans) {
        return new FinancialSnapshot(AS_OF, cash, income, expenses, investments, loans, List.of());
    }

    private static SimulationParameters params(int months, BigDecimal usdToInr) {
        return new SimulationParameters(months, BigDecimal.ZERO, BigDecimal.ZERO, usdToInr);
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), () -> "expected " + expected + " but was " + actual);
    }

    // ------------------------------------------------------------------
    // 1, 2: income change begins on effective month and persists
    // ------------------------------------------------------------------

    @Test
    void incomeChangeBeginsOnEffectiveMonthAndPersistsThereafter() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(10, new BigDecimal("83"));
        LocalDate effectiveDate = AS_OF.plusMonths(6);
        IncomeChangeEvent event = new IncomeChangeEvent(effectiveDate, new BigDecimal("95000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(event));
        List<MonthlyFinancialState> states = projection.getStates();

        for (int i = 0; i < 5; i++) {
            assertMoneyEquals("80000", states.get(i).getMonthlyIncome());
        }
        for (int i = 5; i < 10; i++) {
            assertMoneyEquals("95000", states.get(i).getMonthlyIncome());
        }
    }

    // ------------------------------------------------------------------
    // 3: income changes multiple times over the projection
    // ------------------------------------------------------------------

    @Test
    void incomeChangesMultipleTimesOverTheProjection() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(12, new BigDecimal("83"));
        LocalDate firstChange = AS_OF.plusMonths(4);
        LocalDate secondChange = AS_OF.plusMonths(9);
        IncomeChangeEvent first = new IncomeChangeEvent(firstChange, new BigDecimal("90000"));
        IncomeChangeEvent second = new IncomeChangeEvent(secondChange, new BigDecimal("100000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(first, second));
        List<MonthlyFinancialState> states = projection.getStates();

        for (int i = 0; i < 3; i++) {
            assertMoneyEquals("80000", states.get(i).getMonthlyIncome());
        }
        for (int i = 3; i < 8; i++) {
            assertMoneyEquals("90000", states.get(i).getMonthlyIncome());
        }
        for (int i = 8; i < 12; i++) {
            assertMoneyEquals("100000", states.get(i).getMonthlyIncome());
        }
    }

    // ------------------------------------------------------------------
    // 4: expense change begins on effective month
    // ------------------------------------------------------------------

    @Test
    void expenseChangeBeginsOnEffectiveMonth() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(6, new BigDecimal("83"));
        LocalDate effectiveDate = AS_OF.plusMonths(3);
        ExpenseChangeEvent event = new ExpenseChangeEvent(effectiveDate, new BigDecimal("30000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(event));
        List<MonthlyFinancialState> states = projection.getStates();

        assertMoneyEquals("50000", states.get(0).getMonthlyExpenses());
        assertMoneyEquals("50000", states.get(1).getMonthlyExpenses());
        assertMoneyEquals("30000", states.get(2).getMonthlyExpenses());
        assertMoneyEquals("30000", states.get(5).getMonthlyExpenses());
    }

    // ------------------------------------------------------------------
    // 5, 6: investment contribution change begins on effective month and targets only that
    // investment
    // ------------------------------------------------------------------

    @Test
    void investmentContributionChangeBeginsOnEffectiveMonthAndTargetsOnlySpecifiedInvestment() {
        Investment inv1 = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("1000"));
        Investment inv2 = investment(2L, Currency.INR, new BigDecimal("50"), new BigDecimal("200"), new BigDecimal("0.08"), new BigDecimal("500"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(inv1, inv2), List.of());
        SimulationParameters p = params(5, new BigDecimal("83"));
        LocalDate effectiveDate = AS_OF.plusMonths(2);
        InvestmentContributionChangeEvent event = new InvestmentContributionChangeEvent(effectiveDate, 1L, new BigDecimal("2000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(event));
        List<MonthlyFinancialState> states = projection.getStates();

        // Only inv1's contribution (1000) changes to 2000; inv2's (500) stays constant, so the
        // total moves from 1500 to 2500 — a jump only consistent with a single-investment change.
        assertMoneyEquals("1500.00", states.get(0).getInvestmentContribution());
        for (int i = 1; i < 5; i++) {
            assertMoneyEquals("2500.00", states.get(i).getInvestmentContribution());
        }
    }

    // ------------------------------------------------------------------
    // 7, 8: investment return change begins on effective month and affects future compounding
    // ------------------------------------------------------------------

    @Test
    void investmentReturnChangeBeginsOnEffectiveMonthAndAffectsFutureCompounding() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.10"), BigDecimal.ZERO);
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(inv), List.of());
        SimulationParameters p = params(5, new BigDecimal("83"));
        LocalDate effectiveDate = AS_OF.plusMonths(2);
        BigDecimal newAnnualReturn = new BigDecimal("0.20");
        InvestmentReturnChangeEvent event = new InvestmentReturnChangeEvent(effectiveDate, 1L, newAnnualReturn);

        Projection projection = SimulationEngine.simulate(snap, p, List.of(event));
        List<MonthlyFinancialState> states = projection.getStates();

        BigDecimal originalRate = InvestmentCalculator.calculateMonthlyRate(inv);
        BigDecimal newRate = InvestmentCalculator.calculateMonthlyRate(newAnnualReturn);
        assertNotEquals(0, originalRate.compareTo(newRate));

        BigDecimal expectedValue = InvestmentCalculator.calculateCurrentValue(inv);
        BigDecimal[] ratesByMonth = {originalRate, newRate, newRate, newRate, newRate};
        for (int i = 0; i < 5; i++) {
            expectedValue = expectedValue.multiply(BigDecimal.ONE.add(ratesByMonth[i])).setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedValue.compareTo(states.get(i).getInvestmentValue()),
                    "investment value mismatch at month " + (i + 1));
        }
    }

    // ------------------------------------------------------------------
    // 9: one-time expense affects only one month
    // ------------------------------------------------------------------

    @Test
    void oneTimeExpenseAffectsOnlyOneMonthAndDoesNotAffectRegularExpensesOrCumulativeSavings() {
        FinancialSnapshot snap = snapshot(new BigDecimal("10000"), new BigDecimal("50000"), new BigDecimal("30000"), List.of(), List.of());
        SimulationParameters p = params(5, new BigDecimal("83"));
        LocalDate effectiveDate = AS_OF.plusMonths(2);
        OneTimeExpenseEvent event = new OneTimeExpenseEvent(effectiveDate, new BigDecimal("25000"));

        Projection withEvent = SimulationEngine.simulate(snap, p, List.of(event));
        Projection without = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> we = withEvent.getStates();
        List<MonthlyFinancialState> wo = without.getStates();

        // Month 1: unaffected (event not yet due).
        assertMoneyEquals("0.00", wo.get(0).getCashBalance().subtract(we.get(0).getCashBalance()));
        // Months 2-5: cash is down by exactly the one-time amount, and stays down by exactly that
        // (never re-applied) as both trajectories evolve identically afterward.
        for (int i = 1; i < 5; i++) {
            assertMoneyEquals("25000.00", wo.get(i).getCashBalance().subtract(we.get(i).getCashBalance()));
        }
        // Never affects the recurring monthly expense figure or cumulative savings, in any month.
        for (int i = 0; i < 5; i++) {
            assertEquals(0, wo.get(i).getMonthlyExpenses().compareTo(we.get(i).getMonthlyExpenses()));
            assertEquals(0, wo.get(i).getCumulativeSavings().compareTo(we.get(i).getCumulativeSavings()));
        }
    }

    @Test
    void multipleOneTimeExpensesOnTheSameDateAreAdditiveNotAConflict() {
        FinancialSnapshot snap = snapshot(new BigDecimal("100000"), new BigDecimal("50000"), new BigDecimal("30000"), List.of(), List.of());
        SimulationParameters p = params(2, new BigDecimal("83"));
        LocalDate sameDate = AS_OF.plusMonths(1);
        OneTimeExpenseEvent e1 = new OneTimeExpenseEvent(sameDate, new BigDecimal("5000"));
        OneTimeExpenseEvent e2 = new OneTimeExpenseEvent(sameDate, new BigDecimal("10000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(e1, e2));
        Projection baseline = SimulationEngine.simulate(snap, p);

        assertMoneyEquals("15000.00", baseline.getStates().get(0).getCashBalance()
                .subtract(projection.getStates().get(0).getCashBalance()));
    }

    // ------------------------------------------------------------------
    // 10: event before snapshot is active from the first simulated month
    // ------------------------------------------------------------------

    @Test
    void eventBeforeOrOnSnapshotDateIsActiveFromTheFirstSimulatedMonth() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));

        IncomeChangeEvent beforeSnapshot = new IncomeChangeEvent(AS_OF.minusMonths(3), new BigDecimal("99999"));
        Projection projBefore = SimulationEngine.simulate(snap, p, List.of(beforeSnapshot));
        assertMoneyEquals("99999", projBefore.getStates().get(0).getMonthlyIncome());

        IncomeChangeEvent onSnapshotDate = new IncomeChangeEvent(AS_OF, new BigDecimal("77777"));
        Projection projOn = SimulationEngine.simulate(snap, p, List.of(onSnapshotDate));
        assertMoneyEquals("77777", projOn.getStates().get(0).getMonthlyIncome());
    }

    // ------------------------------------------------------------------
    // 11: event after the projection has no effect
    // ------------------------------------------------------------------

    @Test
    void eventDatedAfterTheProjectionHasNoEffect() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));
        IncomeChangeEvent farFuture = new IncomeChangeEvent(AS_OF.plusYears(50), new BigDecimal("500000"));

        Projection withEvent = SimulationEngine.simulate(snap, p, List.of(farFuture));
        Projection baseline = SimulationEngine.simulate(snap, p);

        assertEquals(baseline, withEvent);
    }

    // ------------------------------------------------------------------
    // 12, 13: same-date events — different properties allowed, same property rejected
    // ------------------------------------------------------------------

    @Test
    void multipleEventsOnSameDateForDifferentPropertiesBothApply() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(inv), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));
        LocalDate sameDate = AS_OF.plusMonths(1);
        IncomeChangeEvent incomeEvent = new IncomeChangeEvent(sameDate, new BigDecimal("90000"));
        InvestmentContributionChangeEvent sipEvent = new InvestmentContributionChangeEvent(sameDate, 1L, new BigDecimal("2000"));

        Projection projection = SimulationEngine.simulate(snap, p, List.of(incomeEvent, sipEvent));
        MonthlyFinancialState firstMonth = projection.getStates().get(0);

        assertMoneyEquals("90000", firstMonth.getMonthlyIncome());
        assertMoneyEquals("2000.00", firstMonth.getInvestmentContribution());
    }

    @Test
    void conflictingSamePropertySameDateEventsAreRejected() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));
        LocalDate sameDate = AS_OF.plusMonths(1);
        IncomeChangeEvent e1 = new IncomeChangeEvent(sameDate, new BigDecimal("90000"));
        IncomeChangeEvent e2 = new IncomeChangeEvent(sameDate, new BigDecimal("95000"));

        assertThrows(IllegalArgumentException.class, () -> SimulationEngine.simulate(snap, p, List.of(e1, e2)));
    }

    @Test
    void conflictingSamePropertySameDateEventsAreRejectedForInvestmentContribution() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(inv), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));
        LocalDate sameDate = AS_OF.plusMonths(1);
        InvestmentContributionChangeEvent e1 = new InvestmentContributionChangeEvent(sameDate, 1L, new BigDecimal("1500"));
        InvestmentContributionChangeEvent e2 = new InvestmentContributionChangeEvent(sameDate, 1L, new BigDecimal("2000"));

        assertThrows(IllegalArgumentException.class, () -> SimulationEngine.simulate(snap, p, List.of(e1, e2)));
    }

    @Test
    void investmentEventTargetingUnknownInvestmentIdIsRejected() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"), new BigDecimal("0.10"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(inv), List.of());
        SimulationParameters p = params(3, new BigDecimal("83"));
        InvestmentContributionChangeEvent badEvent = new InvestmentContributionChangeEvent(AS_OF, 999L, new BigDecimal("1000"));

        assertThrows(IllegalArgumentException.class, () -> SimulationEngine.simulate(snap, p, List.of(badEvent)));
    }

    // ------------------------------------------------------------------
    // 14: scenario does not mutate its event list
    // ------------------------------------------------------------------

    @Test
    void scenarioDoesNotMutateItsEventListWhenApplied() {
        IncomeChangeEvent event = new IncomeChangeEvent(AS_OF.plusMonths(2), new BigDecimal("90000"));
        Scenario scenario = new Scenario(1L, "Raise", null, AS_OF, List.of(event));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("80000"), new BigDecimal("50000"), List.of(), List.of());
        SimulationParameters p = params(4, new BigDecimal("83"));

        SimulationEngine.simulate(snap, p, scenario.getEvents());

        assertEquals(1, scenario.getEvents().size());
        assertEquals(event, scenario.getEvents().get(0));
        assertMoneyEquals("90000", event.getNewMonthlyIncome());
        assertThrows(UnsupportedOperationException.class, () -> scenario.getEvents().add(event));
    }

    // ------------------------------------------------------------------
    // 15: baseline / scenario isolation, and different scenarios don't affect each other
    // ------------------------------------------------------------------

    @Test
    void baselineSimulationRemainsIdenticalAfterRunningScenarios() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"), new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"), List.of(inv), List.of(l));
        SimulationParameters p = params(10, new BigDecimal("83.5"));

        Projection baselineBefore = SimulationEngine.simulate(snap, p);

        SimulationEngine.simulate(snap, p, List.of(new IncomeChangeEvent(AS_OF.plusMonths(3), new BigDecimal("9000"))));
        SimulationEngine.simulate(snap, p, List.of(new OneTimeExpenseEvent(AS_OF.plusMonths(1), new BigDecimal("15000"))));

        Projection baselineAfter = SimulationEngine.simulate(snap, p);

        assertEquals(baselineBefore, baselineAfter);
    }

    @Test
    void differentScenariosDoNotAffectOneAnother() {
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"), List.of(), List.of());
        SimulationParameters p = params(6, new BigDecimal("83"));

        List<ScenarioEvent> scenarioA = List.of(new IncomeChangeEvent(AS_OF.plusMonths(2), new BigDecimal("9000")));
        List<ScenarioEvent> scenarioB = List.of(new ExpenseChangeEvent(AS_OF.plusMonths(2), new BigDecimal("3000")));

        Projection a1 = SimulationEngine.simulate(snap, p, scenarioA);
        Projection b = SimulationEngine.simulate(snap, p, scenarioB);
        Projection a2 = SimulationEngine.simulate(snap, p, scenarioA);

        assertEquals(a1, a2, "running a different scenario in between must not change scenario A's own result");
        assertNotEquals(a1, b);
    }

    // ------------------------------------------------------------------
    // 16: determinism
    // ------------------------------------------------------------------

    @Test
    void sameScenarioProducesEqualProjectionEveryTime() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"), new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"), List.of(inv), List.of(l));
        SimulationParameters p = params(10, new BigDecimal("83.5"));
        List<ScenarioEvent> events = List.of(
                new IncomeChangeEvent(AS_OF.plusMonths(3), new BigDecimal("9000")),
                new InvestmentReturnChangeEvent(AS_OF.plusMonths(5), 1L, new BigDecimal("0.12")),
                new OneTimeExpenseEvent(AS_OF.plusMonths(1), new BigDecimal("15000")));

        Projection first = SimulationEngine.simulate(snap, p, events);
        Projection second = SimulationEngine.simulate(snap, p, events);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    // ------------------------------------------------------------------
    // Determinism / immutability: nothing scenario application touches is ever mutated
    // ------------------------------------------------------------------

    @Test
    void applyingAScenarioNeverMutatesSnapshotInvestmentLoanScenarioOrEvents() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"), new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"), List.of(inv), List.of(l));
        SimulationParameters p = params(10, new BigDecimal("83.5"));

        IncomeChangeEvent incomeEvent = new IncomeChangeEvent(AS_OF.plusMonths(3), new BigDecimal("9000"));
        InvestmentReturnChangeEvent returnEvent = new InvestmentReturnChangeEvent(AS_OF.plusMonths(5), 1L, new BigDecimal("0.12"));
        List<ScenarioEvent> mutableEvents = new ArrayList<>(List.of(incomeEvent, returnEvent));
        Scenario scenario = new Scenario(1L, "Combined", null, AS_OF, mutableEvents);

        BigDecimal cashBefore = snap.getCash();
        BigDecimal incomeBefore = snap.getMonthlyIncome();
        BigDecimal investmentQuantityBefore = inv.getQuantity();
        BigDecimal investmentReturnBefore = inv.getExpectedAnnualReturn();
        BigDecimal loanPrincipalBefore = l.getPrincipal();
        BigDecimal eventIncomeValueBefore = incomeEvent.getNewMonthlyIncome();
        int scenarioEventsSizeBefore = scenario.getEvents().size();

        SimulationEngine.simulate(snap, p, scenario.getEvents());
        mutableEvents.add(new ExpenseChangeEvent(AS_OF.plusMonths(1), new BigDecimal("1000"))); // caller mutates own list after the call

        assertEquals(0, cashBefore.compareTo(snap.getCash()));
        assertEquals(0, incomeBefore.compareTo(snap.getMonthlyIncome()));
        assertEquals(0, investmentQuantityBefore.compareTo(inv.getQuantity()));
        assertEquals(0, investmentReturnBefore.compareTo(inv.getExpectedAnnualReturn()));
        assertEquals(0, loanPrincipalBefore.compareTo(l.getPrincipal()));
        assertEquals(0, eventIncomeValueBefore.compareTo(incomeEvent.getNewMonthlyIncome()));
        assertEquals(scenarioEventsSizeBefore, scenario.getEvents().size());
    }

    // ------------------------------------------------------------------
    // Net worth invariant holds with a full multi-event scenario in play
    // ------------------------------------------------------------------

    @Test
    void netWorthInvariantHoldsWithAllFiveEventTypesCombined() {
        Investment inv1 = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"), new BigDecimal("0.09"), new BigDecimal("50"));
        Investment inv2 = investment(2L, Currency.INR, new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("0.06"), new BigDecimal("200"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"),
                List.of(inv1, inv2), List.of(l));
        SimulationParameters p = params(12, new BigDecimal("83.5"));

        List<ScenarioEvent> events = List.of(
                new IncomeChangeEvent(AS_OF.plusMonths(4), new BigDecimal("9000")),
                new ExpenseChangeEvent(AS_OF.plusMonths(4), new BigDecimal("3500")),
                new InvestmentContributionChangeEvent(AS_OF.plusMonths(5), 1L, new BigDecimal("100")),
                new InvestmentReturnChangeEvent(AS_OF.plusMonths(6), 2L, new BigDecimal("0.12")),
                new OneTimeExpenseEvent(AS_OF.plusMonths(7), new BigDecimal("15000")));

        Projection projection = SimulationEngine.simulate(snap, p, events);
        for (MonthlyFinancialState s : projection.getStates()) {
            BigDecimal expectedNetWorth = s.getCashBalance().add(s.getInvestmentValue())
                    .subtract(s.getLoanBalance()).setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedNetWorth.compareTo(s.getNetWorth()),
                    () -> "net worth invariant failed at " + s.getDate());
        }
    }
}
