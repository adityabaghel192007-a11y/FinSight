package com.finsight.simulation.engine;

import com.finsight.domain.account.Currency;
import com.finsight.domain.goal.Goal;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.simulation.calculator.AmortizationSchedule;
import com.finsight.simulation.calculator.InvestmentCalculator;
import com.finsight.simulation.calculator.LoanCalculator;
import com.finsight.simulation.calculator.LoanPayment;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.MonthlyFinancialState;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.ScenarioEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 1);

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Investment investment(long id, Currency currency, BigDecimal quantity, BigDecimal price,
                                          BigDecimal expectedAnnualReturn, BigDecimal monthlyContribution) {
        return new Investment(id, 1L, "Inv" + id, null, AssetType.ETF, currency, quantity, price, price,
                expectedAnnualReturn, monthlyContribution);
    }

    private static Loan loan(long id, BigDecimal principal, BigDecimal annualRate, int tenureMonths) {
        // A "new" loan: currentBalance == principal, remainingTenureMonths == tenureMonths.
        return new Loan(id, "Loan" + id, principal, annualRate, tenureMonths, AS_OF.minusYears(1), principal, tenureMonths);
    }

    private static Loan loanWithCurrentState(long id, BigDecimal principal, BigDecimal annualRate, int tenureMonths,
                                              BigDecimal currentBalance, int remainingTenureMonths) {
        return new Loan(id, "Loan" + id, principal, annualRate, tenureMonths, AS_OF.minusYears(1),
                currentBalance, remainingTenureMonths);
    }

    private static Goal goal(long id) {
        return new Goal(id, "Goal" + id, new BigDecimal("5000"), new BigDecimal("1000"), AS_OF.plusYears(1), BigDecimal.ZERO);
    }

    private static FinancialSnapshot snapshot(BigDecimal cash, BigDecimal income, BigDecimal expenses,
                                               List<Investment> investments, List<Loan> loans, List<Goal> goals) {
        return new FinancialSnapshot(AS_OF, cash, income, expenses, investments, loans, goals);
    }

    private static SimulationParameters params(int months, BigDecimal incomeGrowth, BigDecimal expenseGrowth, BigDecimal usdToInr) {
        return new SimulationParameters(months, incomeGrowth, expenseGrowth, usdToInr);
    }

    private static void assertMoneyEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // 1, 2: zero investments/loans, basic cash flow
    // ------------------------------------------------------------------

    @Test
    void zeroInvestmentsAndZeroLoansProduceBasicCashFlow() {
        FinancialSnapshot snap = snapshot(new BigDecimal("10000"), new BigDecimal("5000"), new BigDecimal("3000"),
                List.of(), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        assertMoneyEquals("12000.00", states.get(0).getCashBalance());
        assertMoneyEquals("14000.00", states.get(1).getCashBalance());
        assertMoneyEquals("16000.00", states.get(2).getCashBalance());

        for (MonthlyFinancialState s : states) {
            assertMoneyEquals("0.00", s.getInvestmentValue());
            assertMoneyEquals("0.00", s.getLoanBalance());
            assertMoneyEquals("0.00", s.getInvestmentContribution());
            assertMoneyEquals("0.00", s.getLoanPayment());
        }
    }

    // ------------------------------------------------------------------
    // 3, 4, 5: investment growth, contribution, contribution after growth
    // ------------------------------------------------------------------

    @Test
    void investmentGrowsByMonthlyRateEachMonth() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(inv), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        // Verified against InvestmentCalculator.calculateMonthlyRate applied by hand.
        assertMoneyEquals("11094.89", states.get(0).getInvestmentValue());
        assertMoneyEquals("12200.17", states.get(1).getInvestmentValue());
        assertMoneyEquals("13315.93", states.get(2).getInvestmentValue());
    }

    @Test
    void investmentContributionIsRecordedEachMonth() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(inv), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        for (MonthlyFinancialState s : projection.getStates()) {
            assertMoneyEquals("1000.00", s.getInvestmentContribution());
        }
    }

    @Test
    void contributionDoesNotEarnReturnInTheMonthItIsAdded() {
        // No contribution: value should be exactly currentValue * (1 + monthlyRate), rounded.
        Investment noContribution = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), BigDecimal.ZERO);
        BigDecimal rate = InvestmentCalculator.calculateMonthlyRate(noContribution);
        BigDecimal expectedGrowthOnly = InvestmentCalculator.calculateCurrentValue(noContribution)
                .multiply(BigDecimal.ONE.add(rate)).setScale(2, RoundingMode.HALF_UP);

        FinancialSnapshot snapNoContribution = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(noContribution), List.of(), List.of());
        Projection noContributionProjection = SimulationEngine.simulate(
                snapNoContribution, params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
        assertEquals(0, expectedGrowthOnly.compareTo(noContributionProjection.getStates().get(0).getInvestmentValue()));

        // With contribution: value should be exactly (currentValue * (1 + monthlyRate)) + contribution,
        // i.e. growth computed on the pre-contribution balance only.
        Investment withContribution = investment(2L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), new BigDecimal("1000"));
        BigDecimal expectedWithContribution = InvestmentCalculator.calculateCurrentValue(withContribution)
                .multiply(BigDecimal.ONE.add(rate)).add(new BigDecimal("1000.00")).setScale(2, RoundingMode.HALF_UP);

        FinancialSnapshot snapWithContribution = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(withContribution), List.of(), List.of());
        Projection withContributionProjection = SimulationEngine.simulate(
                snapWithContribution, params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
        assertEquals(0, expectedWithContribution.compareTo(withContributionProjection.getStates().get(0).getInvestmentValue()));
    }

    // ------------------------------------------------------------------
    // 6, 30, 32: loan amortization, non-negative balance, carry-forward
    // ------------------------------------------------------------------

    @Test
    void loanAmortizationMatchesLoanCalculatorScheduleExactlyEachMonth() {
        Loan l = loan(1L, new BigDecimal("120000"), new BigDecimal("0.12"), 12);
        AmortizationSchedule expectedSchedule = LoanCalculator.calculateAmortizationSchedule(l);

        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(), List.of(l), List.of());
        SimulationParameters p = params(12, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();
        List<LoanPayment> expectedPayments = expectedSchedule.getPayments();

        for (int i = 0; i < 12; i++) {
            assertEquals(0, expectedPayments.get(i).getPayment().compareTo(states.get(i).getLoanPayment()),
                    "payment mismatch at month " + (i + 1));
            assertEquals(0, expectedPayments.get(i).getRemainingBalance().compareTo(states.get(i).getLoanBalance()),
                    "balance mismatch at month " + (i + 1));
        }
    }

    @Test
    void loanScheduleEndingProducesZeroPaymentAndZeroBalanceThereafter() {
        Loan l = loan(1L, new BigDecimal("120000"), new BigDecimal("0.12"), 12);
        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(), List.of(l), List.of());
        SimulationParameters p = params(14, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        // Month 12 is the final scheduled payment; the loan is fully paid off by then.
        assertMoneyEquals("0.00", states.get(11).getLoanBalance());
        // Months 13 and 14 (indices 12, 13) are beyond the tenure: zero payment, zero balance.
        assertMoneyEquals("0.00", states.get(12).getLoanPayment());
        assertMoneyEquals("0.00", states.get(12).getLoanBalance());
        assertMoneyEquals("0.00", states.get(13).getLoanPayment());
        assertMoneyEquals("0.00", states.get(13).getLoanBalance());
    }

    @Test
    void loanBalanceNeverBecomesNegative() {
        Loan l = loan(1L, new BigDecimal("15000"), new BigDecimal("0.10"), 6);
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("500"),
                List.of(), List.of(l), List.of());
        SimulationParameters p = params(20, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        for (MonthlyFinancialState s : projection.getStates()) {
            assertTrue(s.getLoanBalance().signum() >= 0);
        }
    }

    // ------------------------------------------------------------------
    // Current-state loan amortization (currentBalance + remainingTenureMonths)
    // ------------------------------------------------------------------

    @Test
    void simulationStartsFromCurrentBalanceRatherThanOriginalPrincipal() {
        // Matches the architecture decision's own example: original 10,00,000 over 60 months,
        // currently 7,20,000 outstanding with 42 months left.
        Loan l = loanWithCurrentState(1L, new BigDecimal("1000000"), new BigDecimal("0.09"), 60,
                new BigDecimal("720000"), 42);
        AmortizationSchedule expectedSchedule = LoanCalculator.calculateAmortizationSchedule(l);

        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(), List.of(l), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        MonthlyFinancialState month1 = projection.getStates().get(0);
        LoanPayment expectedFirstPayment = expectedSchedule.getPayments().get(0);

        // Month 1's balance must reflect amortizing the CURRENT balance (720000), never the
        // original principal (1000000) — the schedule is exactly 42 payments long, not 60.
        assertEquals(42, expectedSchedule.getPayments().size());
        assertEquals(0, expectedFirstPayment.getPayment().compareTo(month1.getLoanPayment()));
        assertEquals(0, expectedFirstPayment.getRemainingBalance().compareTo(month1.getLoanBalance()));
        assertTrue(month1.getLoanBalance().compareTo(new BigDecimal("720000")) < 0,
                "balance after one payment must be less than the current balance, not the original principal");
    }

    @Test
    void loanReachesZeroExactlyAfterRemainingTenureMonths() {
        Loan l = loanWithCurrentState(1L, new BigDecimal("1000000"), new BigDecimal("0.09"), 60,
                new BigDecimal("720000"), 42);
        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(), List.of(l), List.of());
        SimulationParameters p = params(42, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        for (int i = 0; i < 41; i++) {
            assertTrue(states.get(i).getLoanBalance().signum() > 0,
                    "balance should still be positive before the final remaining-tenure month " + (i + 1));
        }
        assertMoneyEquals("0.00", states.get(41).getLoanBalance());
    }

    @Test
    void loanPaymentBecomesZeroAfterPayoffFromCurrentState() {
        Loan l = loanWithCurrentState(1L, new BigDecimal("1000000"), new BigDecimal("0.09"), 60,
                new BigDecimal("720000"), 42);
        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(), List.of(l), List.of());
        SimulationParameters p = params(45, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        // Month 42 (index 41) is the final scheduled payment for the remaining tenure.
        assertMoneyEquals("0.00", states.get(41).getLoanBalance());
        // Months 43-45 (indices 42-44) are beyond the remaining tenure: zero payment, zero balance.
        for (int i = 42; i < 45; i++) {
            assertMoneyEquals("0.00", states.get(i).getLoanPayment());
            assertMoneyEquals("0.00", states.get(i).getLoanBalance());
        }
    }

    @Test
    void alreadyFullyPaidLoanContributesZeroPaymentAndZeroBalanceFromMonthOne() {
        Loan l = loanWithCurrentState(1L, new BigDecimal("50000"), new BigDecimal("0.10"), 12,
                BigDecimal.ZERO, 0);
        FinancialSnapshot snap = snapshot(new BigDecimal("10000"), new BigDecimal("5000"), new BigDecimal("2000"),
                List.of(), List.of(l), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        for (MonthlyFinancialState s : projection.getStates()) {
            assertMoneyEquals("0.00", s.getLoanPayment());
            assertMoneyEquals("0.00", s.getLoanBalance());
        }
    }

    // ------------------------------------------------------------------
    // 7, 8: multiple loans, multiple investments
    // ------------------------------------------------------------------

    @Test
    void multipleLoansAggregateCorrectlyAndDropOffWhenExhausted() {
        Loan l1 = loan(1L, new BigDecimal("60000"), new BigDecimal("0.10"), 6);
        Loan l2 = loan(2L, new BigDecimal("30000"), new BigDecimal("0.08"), 4);
        AmortizationSchedule s1 = LoanCalculator.calculateAmortizationSchedule(l1);
        AmortizationSchedule s2 = LoanCalculator.calculateAmortizationSchedule(l2);

        FinancialSnapshot snap = snapshot(new BigDecimal("100000"), new BigDecimal("40000"), new BigDecimal("15000"),
                List.of(), List.of(l1, l2), List.of());
        SimulationParameters p = params(6, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        for (int i = 0; i < 6; i++) {
            BigDecimal expectedPayment = s1.getPayments().get(i).getPayment();
            BigDecimal expectedBalance = s1.getPayments().get(i).getRemainingBalance();
            if (i < 4) {
                expectedPayment = expectedPayment.add(s2.getPayments().get(i).getPayment());
                expectedBalance = expectedBalance.add(s2.getPayments().get(i).getRemainingBalance());
            }
            assertEquals(0, expectedPayment.compareTo(states.get(i).getLoanPayment()), "payment at month " + (i + 1));
            assertEquals(0, expectedBalance.compareTo(states.get(i).getLoanBalance()), "balance at month " + (i + 1));
        }
    }

    @Test
    void multipleInvestmentsAggregateCorrectly() {
        Investment inv1 = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.10"), new BigDecimal("500"));
        Investment inv2 = investment(2L, Currency.INR, new BigDecimal("50"), new BigDecimal("200"),
                new BigDecimal("0.08"), new BigDecimal("300"));

        FinancialSnapshot snap = snapshot(new BigDecimal("100000"), new BigDecimal("40000"), new BigDecimal("15000"),
                List.of(inv1, inv2), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        for (MonthlyFinancialState s : projection.getStates()) {
            assertMoneyEquals("800.00", s.getInvestmentContribution());
        }
    }

    // ------------------------------------------------------------------
    // 9, 29: net worth invariant
    // ------------------------------------------------------------------

    @Test
    void netWorthAlwaysEqualsCashPlusInvestmentsMinusLoans() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"),
                new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"),
                List.of(inv), List.of(l), List.of());
        SimulationParameters p = params(10, new BigDecimal("0.03"), new BigDecimal("0.02"), new BigDecimal("83.5"));

        Projection projection = SimulationEngine.simulate(snap, p);
        for (MonthlyFinancialState s : projection.getStates()) {
            BigDecimal expectedNetWorth = s.getCashBalance().add(s.getInvestmentValue())
                    .subtract(s.getLoanBalance()).setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedNetWorth.compareTo(s.getNetWorth()),
                    () -> "net worth invariant failed at " + s.getDate());
        }
    }

    // ------------------------------------------------------------------
    // 10: cumulative savings
    // ------------------------------------------------------------------

    @Test
    void cumulativeSavingsAccumulatesIncomeMinusExpensesIgnoringContributionsAndPayments() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("10"), new BigDecimal("100"),
                new BigDecimal("0.10"), new BigDecimal("5000"));
        Loan l = loan(1L, new BigDecimal("50000"), new BigDecimal("0.10"), 12);
        FinancialSnapshot snap = snapshot(new BigDecimal("100000"), new BigDecimal("20000"), new BigDecimal("8000"),
                List.of(inv), List.of(l), List.of());
        SimulationParameters p = params(4, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        // Monthly savings = 20000 - 8000 = 12000, regardless of investment contribution or loan payment.
        assertMoneyEquals("12000.00", states.get(0).getCumulativeSavings());
        assertMoneyEquals("24000.00", states.get(1).getCumulativeSavings());
        assertMoneyEquals("36000.00", states.get(2).getCumulativeSavings());
        assertMoneyEquals("48000.00", states.get(3).getCumulativeSavings());
    }

    // ------------------------------------------------------------------
    // 11: negative cash preserved
    // ------------------------------------------------------------------

    @Test
    void negativeCashIsPreservedNotClamped() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), new BigDecimal("1000"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(inv), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        assertMoneyEquals("-1000.00", states.get(0).getCashBalance());
        assertMoneyEquals("-2000.00", states.get(1).getCashBalance());
        assertMoneyEquals("-3000.00", states.get(2).getCashBalance());
        for (MonthlyFinancialState s : states) {
            assertTrue(s.getCashBalance().signum() < 0);
        }
    }

    // ------------------------------------------------------------------
    // 12: zero projection months rejected by parameters already
    // ------------------------------------------------------------------

    @Test
    void zeroProjectionMonthsIsRejectedBySimulationParametersConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> params(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE));
    }

    // ------------------------------------------------------------------
    // 13, 14, 15: one-month, multi-month, exact state count
    // ------------------------------------------------------------------

    @Test
    void oneMonthSimulationProducesExactlyOneState() {
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("1500"),
                List.of(), List.of(), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        assertEquals(1, projection.getStates().size());
        assertMoneyEquals("1500.00", projection.getStates().get(0).getCashBalance());
    }

    @Test
    void multiMonthSimulationProducesExactlyRequestedStateCountForVariousLengths() {
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("1500"),
                List.of(), List.of(), List.of());

        for (int months : new int[] {2, 5, 12, 24, 36}) {
            Projection projection = SimulationEngine.simulate(snap, params(months, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
            assertEquals(months, projection.getStates().size(), "state count for " + months + " months");
        }
    }

    // ------------------------------------------------------------------
    // 16: correct date progression
    // ------------------------------------------------------------------

    @Test
    void datesProgressByExactlyOneMonthPerState() {
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("1500"),
                List.of(), List.of(), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(6, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        assertEquals(AS_OF, projection.getStartDate());
        assertEquals(AS_OF.plusMonths(6), projection.getEndDate());

        List<MonthlyFinancialState> states = projection.getStates();
        for (int i = 0; i < states.size(); i++) {
            assertEquals(AS_OF.plusMonths(i + 1), states.get(i).getDate());
        }
    }

    // ------------------------------------------------------------------
    // 17, 18: annual income/expense growth
    // ------------------------------------------------------------------

    @Test
    void annualIncomeAndExpenseGrowthAppliesAtSimulatedYearBoundaries() {
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, new BigDecimal("10000"), new BigDecimal("6000"),
                List.of(), List.of(), List.of());
        SimulationParameters p = params(26, new BigDecimal("0.07"), new BigDecimal("0.05"), new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        List<MonthlyFinancialState> states = projection.getStates();

        // Year 1 (months 1-12): unchanged.
        assertMoneyEquals("10000.00", states.get(0).getMonthlyIncome());
        assertMoneyEquals("6000.00", states.get(0).getMonthlyExpenses());
        assertMoneyEquals("10000.00", states.get(11).getMonthlyIncome());
        assertMoneyEquals("6000.00", states.get(11).getMonthlyExpenses());

        // Year 2 (months 13-24): grown once by the respective annual rate.
        assertMoneyEquals("10700.00", states.get(12).getMonthlyIncome());
        assertMoneyEquals("6300.00", states.get(12).getMonthlyExpenses());
        assertMoneyEquals("10700.00", states.get(23).getMonthlyIncome());
        assertMoneyEquals("6300.00", states.get(23).getMonthlyExpenses());

        // Year 3 (months 25-26): compounded from year 2's value, not the original.
        assertMoneyEquals("11449.00", states.get(24).getMonthlyIncome());
        assertMoneyEquals("6615.00", states.get(24).getMonthlyExpenses());
        assertMoneyEquals("11449.00", states.get(25).getMonthlyIncome());
        assertMoneyEquals("6615.00", states.get(25).getMonthlyExpenses());
    }

    // ------------------------------------------------------------------
    // 19, 20, 21: USD conversion for value and contribution
    // ------------------------------------------------------------------

    @Test
    void usdInvestmentValueIsConvertedToInrForConsolidatedTotal() {
        Investment usdInv = investment(1L, Currency.USD, new BigDecimal("10"), new BigDecimal("100"),
                new BigDecimal("0.10"), new BigDecimal("100"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(usdInv), List.of(), List.of());
        SimulationParameters p = params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        MonthlyFinancialState month1 = projection.getStates().get(0);

        BigDecimal rate = InvestmentCalculator.calculateMonthlyRate(usdInv);
        BigDecimal expectedUsdValue = InvestmentCalculator.calculateCurrentValue(usdInv)
                .multiply(BigDecimal.ONE.add(rate)).add(new BigDecimal("100.00")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedInrValue = expectedUsdValue.multiply(new BigDecimal("83")).setScale(2, RoundingMode.HALF_UP);

        assertEquals(0, expectedInrValue.compareTo(month1.getInvestmentValue()));
    }

    @Test
    void usdContributionIsConvertedToInrUsingUsdToInrRate() {
        Investment usdInv = investment(1L, Currency.USD, new BigDecimal("10"), new BigDecimal("100"),
                new BigDecimal("0.10"), new BigDecimal("100"));
        FinancialSnapshot snap = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(usdInv), List.of(), List.of());
        SimulationParameters p = params(1, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        assertMoneyEquals("8300.00", projection.getStates().get(0).getInvestmentContribution());
    }

    @Test
    void changingUsdToInrRateChangesInrTotalsButNotUnderlyingUsdCalculation() {
        Investment usdInv = investment(1L, Currency.USD, new BigDecimal("10"), new BigDecimal("100"),
                new BigDecimal("0.10"), new BigDecimal("100"));
        FinancialSnapshot snap83 = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(usdInv), List.of(), List.of());
        FinancialSnapshot snap90 = snapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of(usdInv), List.of(), List.of());

        Projection p83 = SimulationEngine.simulate(snap83, params(2, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
        Projection p90 = SimulationEngine.simulate(snap90, params(2, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("90")));

        // Different FX rate => different INR totals.
        assertTrue(p83.getStates().get(0).getInvestmentValue().compareTo(p90.getStates().get(0).getInvestmentValue()) != 0);

        // The underlying USD-denominated value, recovered by dividing back out the FX rate, is the
        // same in both runs (within a cent — each side rounds its own INR conversion independently).
        BigDecimal impliedUsd83 = p83.getStates().get(1).getInvestmentValue()
                .divide(new BigDecimal("83"), 6, RoundingMode.HALF_UP);
        BigDecimal impliedUsd90 = p90.getStates().get(1).getInvestmentValue()
                .divide(new BigDecimal("90"), 6, RoundingMode.HALF_UP);
        BigDecimal diff = impliedUsd83.subtract(impliedUsd90).abs();
        assertTrue(diff.compareTo(new BigDecimal("0.01")) < 0,
                "implied USD values diverged by more than a cent: " + diff);
    }

    // ------------------------------------------------------------------
    // 22, 23, 24: empty lists
    // ------------------------------------------------------------------

    @Test
    void emptyInvestmentListProducesZeroInvestmentValueAndContribution() {
        Loan l = loan(1L, new BigDecimal("10000"), new BigDecimal("0.05"), 5);
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(), List.of(l), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        for (MonthlyFinancialState s : projection.getStates()) {
            assertMoneyEquals("0.00", s.getInvestmentValue());
            assertMoneyEquals("0.00", s.getInvestmentContribution());
        }
    }

    @Test
    void emptyLoanListProducesZeroLoanBalanceAndPayment() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("0.05"), new BigDecimal("20"));
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(inv), List.of(), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        for (MonthlyFinancialState s : projection.getStates()) {
            assertMoneyEquals("0.00", s.getLoanBalance());
            assertMoneyEquals("0.00", s.getLoanPayment());
        }
    }

    @Test
    void emptyGoalsListSimulatesSuccessfullyAndGoalsAreIrrelevantToOutput() {
        FinancialSnapshot snapWithGoal = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(), List.of(), List.of(goal(1L)));
        FinancialSnapshot snapWithoutGoal = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(), List.of(), List.of());

        Projection withGoal = SimulationEngine.simulate(snapWithGoal, params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
        Projection withoutGoal = SimulationEngine.simulate(snapWithoutGoal, params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        assertEquals(withoutGoal, withGoal);
    }

    // ------------------------------------------------------------------
    // 25, 26, 27: isolation — nothing is mutated
    // ------------------------------------------------------------------

    @Test
    void inputSnapshotIsNotMutated() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("0.05"), new BigDecimal("20"));
        Loan l = loan(1L, new BigDecimal("10000"), new BigDecimal("0.05"), 5);
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(inv), List.of(l), List.of(goal(1L)));

        BigDecimal cashBefore = snap.getCash();
        BigDecimal incomeBefore = snap.getMonthlyIncome();
        BigDecimal expensesBefore = snap.getMonthlyExpenses();
        int investmentsSizeBefore = snap.getInvestments().size();
        int loansSizeBefore = snap.getLoans().size();
        int goalsSizeBefore = snap.getGoals().size();

        SimulationEngine.simulate(snap, params(12, new BigDecimal("0.02"), new BigDecimal("0.02"), new BigDecimal("83")));

        assertEquals(0, cashBefore.compareTo(snap.getCash()));
        assertEquals(0, incomeBefore.compareTo(snap.getMonthlyIncome()));
        assertEquals(0, expensesBefore.compareTo(snap.getMonthlyExpenses()));
        assertEquals(investmentsSizeBefore, snap.getInvestments().size());
        assertEquals(loansSizeBefore, snap.getLoans().size());
        assertEquals(goalsSizeBefore, snap.getGoals().size());
    }

    @Test
    void domainInvestmentObjectsAreNotMutated() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("10"), new BigDecimal("50"),
                new BigDecimal("0.05"), new BigDecimal("20"));
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(inv), List.of(), List.of());

        BigDecimal quantityBefore = inv.getQuantity();
        BigDecimal priceBefore = inv.getCurrentPrice();
        BigDecimal contributionBefore = inv.getMonthlyContribution();
        BigDecimal returnBefore = inv.getExpectedAnnualReturn();
        Currency currencyBefore = inv.getCurrency();

        SimulationEngine.simulate(snap, params(12, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        assertEquals(0, quantityBefore.compareTo(inv.getQuantity()));
        assertEquals(0, priceBefore.compareTo(inv.getCurrentPrice()));
        assertEquals(0, contributionBefore.compareTo(inv.getMonthlyContribution()));
        assertEquals(0, returnBefore.compareTo(inv.getExpectedAnnualReturn()));
        assertEquals(currencyBefore, inv.getCurrency());
    }

    @Test
    void domainLoanObjectsAreNotMutated() {
        Loan l = loan(1L, new BigDecimal("10000"), new BigDecimal("0.05"), 5);
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("3000"), new BigDecimal("2000"),
                List.of(), List.of(l), List.of());

        BigDecimal principalBefore = l.getPrincipal();
        BigDecimal rateBefore = l.getAnnualInterestRate();
        int tenureBefore = l.getTenureMonths();
        LocalDate startDateBefore = l.getStartDate();
        BigDecimal currentBalanceBefore = l.getCurrentBalance();
        int remainingTenureBefore = l.getRemainingTenureMonths();

        SimulationEngine.simulate(snap, params(10, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));

        assertEquals(0, principalBefore.compareTo(l.getPrincipal()));
        assertEquals(0, rateBefore.compareTo(l.getAnnualInterestRate()));
        assertEquals(tenureBefore, l.getTenureMonths());
        assertEquals(startDateBefore, l.getStartDate());
        assertEquals(0, currentBalanceBefore.compareTo(l.getCurrentBalance()));
        assertEquals(remainingTenureBefore, l.getRemainingTenureMonths());
    }

    // ------------------------------------------------------------------
    // 28: determinism
    // ------------------------------------------------------------------

    @Test
    void sameInputsProduceAnEqualProjectionEveryTime() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"),
                new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"),
                List.of(inv), List.of(l), List.of());
        SimulationParameters p = params(10, new BigDecimal("0.03"), new BigDecimal("0.02"), new BigDecimal("83.5"));

        Projection first = SimulationEngine.simulate(snap, p);
        Projection second = SimulationEngine.simulate(snap, p);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    // ------------------------------------------------------------------
    // 31: large values / precision
    // ------------------------------------------------------------------

    @Test
    void largeValuesDoNotOverflowOrLosePrecision() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("1000000"), new BigDecimal("5000"),
                new BigDecimal("0.15"), new BigDecimal("500000"));
        Loan l = loan(1L, new BigDecimal("50000000"), new BigDecimal("0.11"), 240);
        FinancialSnapshot snap = snapshot(new BigDecimal("10000000"), new BigDecimal("2000000"), new BigDecimal("800000"),
                List.of(inv), List.of(l), List.of());
        SimulationParameters p = params(24, new BigDecimal("0.06"), new BigDecimal("0.04"), new BigDecimal("83"));

        Projection projection = SimulationEngine.simulate(snap, p);
        assertEquals(24, projection.getStates().size());

        for (MonthlyFinancialState s : projection.getStates()) {
            BigDecimal expectedNetWorth = s.getCashBalance().add(s.getInvestmentValue())
                    .subtract(s.getLoanBalance()).setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedNetWorth.compareTo(s.getNetWorth()));
            assertTrue(s.getInvestmentValue().signum() > 0);
            assertTrue(s.getLoanBalance().signum() > 0);
        }
    }

    // ------------------------------------------------------------------
    // 32: multi-month carry-forward correctness
    // ------------------------------------------------------------------

    @Test
    void balancesCarryForwardCorrectlyAcrossMultipleMonths() {
        Investment inv = investment(1L, Currency.INR, new BigDecimal("100"), new BigDecimal("100"),
                new BigDecimal("0.12"), new BigDecimal("1000"));
        Loan l = loan(1L, new BigDecimal("120000"), new BigDecimal("0.12"), 12);
        AmortizationSchedule schedule = LoanCalculator.calculateAmortizationSchedule(l);

        FinancialSnapshot snap = snapshot(new BigDecimal("50000"), new BigDecimal("20000"), new BigDecimal("5000"),
                List.of(inv), List.of(l), List.of());
        Projection projection = SimulationEngine.simulate(snap, params(12, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83")));
        List<MonthlyFinancialState> states = projection.getStates();

        // Reconstruct the expected investment value chain independently and compare month by month.
        BigDecimal rate = InvestmentCalculator.calculateMonthlyRate(inv);
        BigDecimal factor = BigDecimal.ONE.add(rate);
        BigDecimal expectedInvestmentValue = InvestmentCalculator.calculateCurrentValue(inv);
        for (int i = 0; i < 12; i++) {
            expectedInvestmentValue = expectedInvestmentValue.multiply(factor)
                    .add(new BigDecimal("1000.00")).setScale(2, RoundingMode.HALF_UP);
            assertEquals(0, expectedInvestmentValue.compareTo(states.get(i).getInvestmentValue()),
                    "investment value carry-forward mismatch at month " + (i + 1));
            assertEquals(0, schedule.getPayments().get(i).getRemainingBalance().compareTo(states.get(i).getLoanBalance()),
                    "loan balance carry-forward mismatch at month " + (i + 1));
        }
    }

    // ------------------------------------------------------------------
    // Milestone 3E: scenario-aware overload compatibility
    // ------------------------------------------------------------------

    @Test
    void threeArgOverloadWithEmptyEventsProducesIdenticalProjectionToTwoArgOverload() {
        Investment inv = investment(1L, Currency.USD, new BigDecimal("5"), new BigDecimal("200"),
                new BigDecimal("0.09"), new BigDecimal("50"));
        Loan l = loan(1L, new BigDecimal("20000"), new BigDecimal("0.09"), 8);
        FinancialSnapshot snap = snapshot(new BigDecimal("5000"), new BigDecimal("8000"), new BigDecimal("4000"),
                List.of(inv), List.of(l), List.of());
        SimulationParameters p = params(10, new BigDecimal("0.03"), new BigDecimal("0.02"), new BigDecimal("83.5"));

        Projection twoArg = SimulationEngine.simulate(snap, p);
        Projection threeArgEmpty = SimulationEngine.simulate(snap, p, List.of());

        assertEquals(twoArg, threeArgEmpty);
        assertEquals(twoArg.hashCode(), threeArgEmpty.hashCode());
    }

    @Test
    void threeArgOverloadRejectsNullEvents() {
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("1500"),
                List.of(), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));

        assertThrows(NullPointerException.class, () -> SimulationEngine.simulate(snap, p, null));
    }

    @Test
    void threeArgOverloadWithNonEmptyEventsAppliesThemRatherThanRejectingThem() {
        // Milestone 3F superseded 3E's stub: a non-empty event list is now applied, not rejected.
        // (Full scenario-application coverage lives in ScenarioApplicationTest; this test only
        // guards against a regression back to the old "not yet implemented" stub behavior.)
        FinancialSnapshot snap = snapshot(new BigDecimal("1000"), new BigDecimal("2000"), new BigDecimal("1500"),
                List.of(), List.of(), List.of());
        SimulationParameters p = params(3, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("83"));
        List<ScenarioEvent> events = List.of(new IncomeChangeEvent(AS_OF.plusMonths(1), new BigDecimal("5000")));

        Projection withEvent = SimulationEngine.simulate(snap, p, events);
        Projection baseline = SimulationEngine.simulate(snap, p);

        // effectiveDate == AS_OF.plusMonths(1) is exactly the first simulated month's date, so the
        // event is already active from states.get(0) onward, for the whole 3-month projection.
        assertMoneyEquals("2000", baseline.getStates().get(0).getMonthlyIncome());
        for (MonthlyFinancialState s : withEvent.getStates()) {
            assertMoneyEquals("5000", s.getMonthlyIncome());
        }
    }
}
