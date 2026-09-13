package com.finsight.simulation.calculator;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AccountBalanceCalculator}, using plain domain objects only — no database,
 * matching the class's own claim of being independently testable without JDBC. A small
 * repository-backed integration test lives separately in
 * {@link AccountBalanceCalculatorIntegrationTest}.
 */
class AccountBalanceCalculatorTest {

    private static final long ACCOUNT_ID = 1L;
    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    private static Account account(BigDecimal openingBalance) {
        return new Account(ACCOUNT_ID, "Checking", AccountType.BANK, Currency.INR, openingBalance);
    }

    private static Transaction transaction(long id, long accountId, BigDecimal amount, TransactionType type) {
        return new Transaction(id, accountId, amount, type, null, DATE, null, null);
    }

    // ===================== 1. openingBalanceOnly =====================

    @Test
    void openingBalanceOnlyWhenThereAreNoTransactions() {
        Account account = account(new BigDecimal("1000.00"));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, List.of());

        assertEquals(0, new BigDecimal("1000.00").compareTo(balance));
    }

    // ===================== 2. incomeIncreasesBalance =====================

    @Test
    void incomeIncreasesBalance() {
        Account account = account(new BigDecimal("1000.00"));
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("250.00"), TransactionType.INCOME));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("1250.00").compareTo(balance));
    }

    // ===================== 3. expenseDecreasesBalance =====================

    @Test
    void expenseDecreasesBalance() {
        Account account = account(new BigDecimal("1000.00"));
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("300.00"), TransactionType.EXPENSE));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("700.00").compareTo(balance));
    }

    // ===================== 4. multipleIncomeAndExpenseTransactions =====================

    @Test
    void multipleIncomeAndExpenseTransactionsAreAllApplied() {
        Account account = account(new BigDecimal("500.00"));
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("1000.00"), TransactionType.INCOME),
                transaction(2L, ACCOUNT_ID, new BigDecimal("200.00"), TransactionType.EXPENSE),
                transaction(3L, ACCOUNT_ID, new BigDecimal("50.00"), TransactionType.EXPENSE),
                transaction(4L, ACCOUNT_ID, new BigDecimal("75.50"), TransactionType.INCOME));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        // 500 + 1000 - 200 - 50 + 75.50 = 1325.50
        assertEquals(0, new BigDecimal("1325.50").compareTo(balance));
    }

    // ===================== 5. zeroOpeningBalance =====================

    @Test
    void zeroOpeningBalanceWorksCorrectly() {
        Account account = account(BigDecimal.ZERO);
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("100.00"), TransactionType.INCOME),
                transaction(2L, ACCOUNT_ID, new BigDecimal("40.00"), TransactionType.EXPENSE));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("60.00").compareTo(balance));
    }

    // ===================== 6. negativeResultIsAllowed =====================

    @Test
    void expensesCanDriveTheBalanceNegative() {
        Account account = account(new BigDecimal("50.00"));
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("200.00"), TransactionType.EXPENSE));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("-150.00").compareTo(balance));
    }

    // ===================== 7. multipleAccountsAreNotMixed =====================

    @Test
    void aTransactionBelongingToAnotherAccountIsRejected() {
        Account account = account(new BigDecimal("1000.00"));
        long otherAccountId = ACCOUNT_ID + 1;
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("100.00"), TransactionType.INCOME),
                transaction(2L, otherAccountId, new BigDecimal("500.00"), TransactionType.INCOME));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AccountBalanceCalculator.calculateBalance(account, transactions),
                "a transaction belonging to a different account must not silently be folded in");

        assertTrue(exception.getMessage().contains(String.valueOf(otherAccountId)));
    }

    // ===================== 8. transferBehaviorIsExplicit =====================

    @Test
    void aTransferTransactionIsExplicitlyUnsupportedRatherThanGuessedAt() {
        Account account = account(new BigDecimal("1000.00"));
        List<Transaction> transactions = List.of(
                new Transaction(1L, ACCOUNT_ID, new BigDecimal("200.00"), TransactionType.TRANSFER,
                        null, DATE, null, 99L));

        assertThrows(UnsupportedOperationException.class,
                () -> AccountBalanceCalculator.calculateBalance(account, transactions),
                "TRANSFER direction cannot be determined from the current model, so this must fail "
                        + "loudly rather than silently including or excluding it");
    }

    // ===================== 9. BigDecimalPrecision =====================

    @Test
    void monetaryPrecisionAndFinalScaleFollowFinancialPrecisionConventions() {
        Account account = account(new BigDecimal("0.00"));
        List<Transaction> transactions = List.of(
                transaction(1L, ACCOUNT_ID, new BigDecimal("0.01"), TransactionType.INCOME),
                transaction(2L, ACCOUNT_ID, new BigDecimal("0.02"), TransactionType.INCOME),
                transaction(3L, ACCOUNT_ID, new BigDecimal("100000.10"), TransactionType.INCOME));

        BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("100000.13").compareTo(balance));
        assertEquals(2, balance.scale(), "the result must be rounded to FinancialPrecision.MONEY_SCALE");
    }

    // ===================== 10. nullInputs =====================

    @Test
    void nullAccountIsRejected() {
        assertThrows(NullPointerException.class,
                () -> AccountBalanceCalculator.calculateBalance(null, List.of()));
    }

    @Test
    void nullTransactionListIsRejected() {
        Account account = account(BigDecimal.ZERO);

        assertThrows(NullPointerException.class,
                () -> AccountBalanceCalculator.calculateBalance(account, null));
    }

    @Test
    void nullElementWithinTheTransactionListIsRejected() {
        Account account = account(BigDecimal.ZERO);
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(transaction(1L, ACCOUNT_ID, new BigDecimal("10.00"), TransactionType.INCOME));
        transactions.add(null);

        assertThrows(NullPointerException.class,
                () -> AccountBalanceCalculator.calculateBalance(account, transactions));
    }

    // ===================== 11. noMutation =====================

    @Test
    void neitherTheAccountNorItsTransactionsAreMutated() {
        Account account = account(new BigDecimal("1000.00"));
        Transaction income = transaction(1L, ACCOUNT_ID, new BigDecimal("100.00"), TransactionType.INCOME);
        Transaction expense = transaction(2L, ACCOUNT_ID, new BigDecimal("30.00"), TransactionType.EXPENSE);
        List<Transaction> transactions = List.of(income, expense);

        AccountBalanceCalculator.calculateBalance(account, transactions);

        assertEquals(0, new BigDecimal("1000.00").compareTo(account.getOpeningBalance()));
        assertEquals(0, new BigDecimal("100.00").compareTo(income.getAmount()));
        assertEquals(TransactionType.INCOME, income.getType());
        assertEquals(0, new BigDecimal("30.00").compareTo(expense.getAmount()));
        assertEquals(TransactionType.EXPENSE, expense.getType());
    }
}
