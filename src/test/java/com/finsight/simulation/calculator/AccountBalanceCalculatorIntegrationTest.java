package com.finsight.simulation.calculator;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import com.finsight.repository.jdbc.TransactionRepositoryJdbc;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A small integration-level check that {@link AccountBalanceCalculator} works correctly on the
 * output of a real repository call — {@code TransactionRepositoryJdbc.findByAccountId(long)} —
 * against a real (isolated, in-memory) SQLite database, in the same style as the
 * {@code *RepositoryJdbcTest} classes under {@code com.finsight.repository.jdbc}.
 *
 * <p>This test exists only to prove the wiring: repository result feeds directly into the
 * calculator with no adapter needed. The calculation logic itself is exhaustively covered, without
 * any database, by {@link AccountBalanceCalculatorTest} — {@link AccountBalanceCalculator} remains
 * JDBC-independent; this test merely exercises it from real repository output.
 */
class AccountBalanceCalculatorIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 1, 15);

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final AccountRepositoryJdbc accountRepository;
        private final TransactionRepositoryJdbc transactionRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.transactionRepository = new TransactionRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    @Test
    void calculatesBalanceFromTransactionsRetrievedThroughTheRealRepository() {
        try (TestDatabase db = new TestDatabase()) {
            Account account = db.accountRepository.save(
                    new Account(0L, "Checking", AccountType.BANK, Currency.INR, new BigDecimal("1000.00")));
            long accountId = account.getId();

            // A second account's transactions must never leak into the first account's balance.
            Account otherAccount = db.accountRepository.save(
                    new Account(0L, "Savings", AccountType.BANK, Currency.INR, new BigDecimal("5000.00")));

            db.transactionRepository.save(new Transaction(
                    0L, accountId, new BigDecimal("300.00"), TransactionType.INCOME, null, DATE, null, null));
            db.transactionRepository.save(new Transaction(
                    0L, accountId, new BigDecimal("120.00"), TransactionType.EXPENSE, null, DATE, null, null));
            db.transactionRepository.save(new Transaction(
                    0L, otherAccount.getId(), new BigDecimal("999.00"), TransactionType.INCOME, null, DATE, null, null));

            List<Transaction> transactions = db.transactionRepository.findByAccountId(accountId);
            BigDecimal balance = AccountBalanceCalculator.calculateBalance(account, transactions);

            // 1000 + 300 - 120 = 1180, unaffected by the other account's transaction.
            assertEquals(0, new BigDecimal("1180.00").compareTo(balance));
        }
    }
}
