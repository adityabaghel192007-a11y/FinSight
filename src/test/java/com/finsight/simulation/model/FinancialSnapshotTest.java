package com.finsight.simulation.model;

import com.finsight.domain.account.Currency;
import com.finsight.domain.goal.Goal;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinancialSnapshotTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 1, 1);

    private static Investment investment() {
        return new Investment(1L, 10L, "ETF", null, AssetType.ETF, Currency.INR,
                BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("0.10"), BigDecimal.ZERO);
    }

    private static Loan loan() {
        return new Loan(1L, "Home Loan", new BigDecimal("100000"), new BigDecimal("0.08"), 12, AS_OF,
                new BigDecimal("100000"), 12);
    }

    private static Goal goal() {
        return new Goal(1L, "Fund", new BigDecimal("50000"), new BigDecimal("1000"), AS_OF.plusYears(1), BigDecimal.ZERO);
    }

    private static FinancialSnapshot snapshot(List<Investment> investments, List<Loan> loans, List<Goal> goals) {
        return new FinancialSnapshot(AS_OF, new BigDecimal("5000"), new BigDecimal("80000"),
                new BigDecimal("50000"), investments, loans, goals);
    }

    @Test
    void constructsWithValidArguments() {
        FinancialSnapshot snapshot = snapshot(List.of(investment()), List.of(loan()), List.of(goal()));

        assertEquals(AS_OF, snapshot.getAsOfDate());
        assertEquals(new BigDecimal("5000"), snapshot.getCash());
        assertEquals(new BigDecimal("80000"), snapshot.getMonthlyIncome());
        assertEquals(new BigDecimal("50000"), snapshot.getMonthlyExpenses());
        assertEquals(1, snapshot.getInvestments().size());
        assertEquals(1, snapshot.getLoans().size());
        assertEquals(1, snapshot.getGoals().size());
    }

    @Test
    void allowsEmptyLists() {
        FinancialSnapshot snapshot = snapshot(List.of(), List.of(), List.of());

        assertTrue(snapshot.getInvestments().isEmpty());
        assertTrue(snapshot.getLoans().isEmpty());
        assertTrue(snapshot.getGoals().isEmpty());
    }

    @Test
    void rejectsNullAsOfDate() {
        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsNullCash() {
        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                AS_OF, null, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of()));
    }

    @Test
    void allowsNegativeCashAndItRoundTripsCorrectly() {
        // Cash is a consolidated aggregate across every account (see SnapshotBuilder); an account
        // whose recorded expenses exceed its opening balance can legitimately drive that aggregate
        // negative, so this is not rejected — see the class-level note on preserving, not clamping,
        // that value.
        FinancialSnapshot snapshot = new FinancialSnapshot(
                AS_OF, new BigDecimal("-500.00"), BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), List.of());

        assertEquals(0, new BigDecimal("-500.00").compareTo(snapshot.getCash()));
    }

    @Test
    void rejectsNegativeMonthlyIncome() {
        assertThrows(IllegalArgumentException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, new BigDecimal("-1"), BigDecimal.ZERO, List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsNegativeMonthlyExpenses() {
        assertThrows(IllegalArgumentException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-1"), List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsNullLists() {
        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), null, List.of()));
        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of(), null));
    }

    @Test
    void rejectsNullElementsInLists() {
        List<Investment> withNull = new ArrayList<>();
        withNull.add(null);

        assertThrows(NullPointerException.class, () -> new FinancialSnapshot(
                AS_OF, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, withNull, List.of(), List.of()));
    }

    @Test
    void doesNotMutateCallersListsAfterConstruction() {
        List<Investment> investments = new ArrayList<>(List.of(investment()));
        FinancialSnapshot snapshot = snapshot(investments, List.of(), List.of());

        investments.add(investment());

        assertEquals(1, snapshot.getInvestments().size());
    }

    @Test
    void returnedListsAreImmutable() {
        FinancialSnapshot snapshot = snapshot(List.of(investment()), List.of(loan()), List.of(goal()));

        assertThrows(UnsupportedOperationException.class, () -> snapshot.getInvestments().add(investment()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getLoans().add(loan()));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.getGoals().add(goal()));
    }

    @Test
    void equalityIsBasedOnCompleteState() {
        FinancialSnapshot a = snapshot(List.of(investment()), List.of(loan()), List.of(goal()));
        // Same logical values, different BigDecimal scale.
        FinancialSnapshot b = new FinancialSnapshot(AS_OF, new BigDecimal("5000.00"), new BigDecimal("80000.00"),
                new BigDecimal("50000.00"), List.of(investment()), List.of(loan()), List.of(goal()));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());

        FinancialSnapshot different = snapshot(List.of(), List.of(), List.of());
        assertNotEquals(a, different);
    }
}
