package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link AccountRepositoryJdbc} against a real (but isolated, in-memory)
 * SQLite database — proving the persistence pattern this milestone establishes, not just that the
 * code compiles.
 *
 * <p>Every test opens its own fresh {@code jdbc:sqlite::memory:} database via
 * {@link #newRepository()} and initializes the schema on it; nothing here ever touches a file on
 * disk or a database another test (or the real application) might be using.
 */
class AccountRepositoryJdbcTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final AccountRepositoryJdbc repository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.repository = new AccountRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    private static TestDatabase newRepository() {
        return new TestDatabase();
    }

    private static Account transientAccount(String name) {
        // id is a placeholder — save() ignores it and lets SQLite generate the real one.
        return new Account(0L, name, AccountType.BANK, Currency.INR, new BigDecimal("100.00"));
    }

    // ===================== CREATE =====================

    @Test
    void savingANewAccountGeneratesAnId() {
        try (TestDatabase db = newRepository()) {
            Account saved = db.repository.save(transientAccount("Checking"));

            assertTrue(saved.getId() > 0, "generated id should be a positive, valid row id");
        }
    }

    @Test
    void savingMultipleAccountsProducesDistinctIds() {
        try (TestDatabase db = newRepository()) {
            Account first = db.repository.save(transientAccount("Checking"));
            Account second = db.repository.save(transientAccount("Savings"));
            Account third = db.repository.save(transientAccount("Wallet"));

            Set<Long> ids = new HashSet<>(List.of(first.getId(), second.getId(), third.getId()));
            assertEquals(3, ids.size(), "every saved account should receive a distinct id");
        }
    }

    @Test
    void savingReturnsANewInstanceAndDoesNotMutateTheOriginal() {
        try (TestDatabase db = newRepository()) {
            Account original = transientAccount("Checking");

            Account saved = db.repository.save(original);

            // The original, caller-held instance is exactly as it was before saving.
            assertEquals(0L, original.getId());
            // The returned instance is a different object carrying the generated id.
            assertTrue(saved.getId() != original.getId());
            assertEquals(original.getName(), saved.getName());
            assertEquals(original.getType(), saved.getType());
            assertEquals(original.getCurrency(), saved.getCurrency());
            assertEquals(0, original.getOpeningBalance().compareTo(saved.getOpeningBalance()));
        }
    }

    // ===================== READ =====================

    @Test
    void savedAccountCanBeRetrievedByIdWithMatchingValues() {
        try (TestDatabase db = newRepository()) {
            Account toSave = new Account(0L, "Retirement Fund", AccountType.INVESTMENT, Currency.USD,
                    new BigDecimal("2500.75"));
            Account saved = db.repository.save(toSave);

            Optional<Account> retrieved = db.repository.findById(saved.getId());

            assertTrue(retrieved.isPresent());
            Account account = retrieved.get();
            assertEquals(saved.getId(), account.getId());
            assertEquals("Retirement Fund", account.getName());
            assertEquals(AccountType.INVESTMENT, account.getType());
            assertEquals(Currency.USD, account.getCurrency());
            assertEquals(0, new BigDecimal("2500.75").compareTo(account.getOpeningBalance()));
        }
    }

    @Test
    void findByIdForAMissingIdReturnsEmpty() {
        try (TestDatabase db = newRepository()) {
            Optional<Account> result = db.repository.findById(999_999L);

            assertFalse(result.isPresent());
        }
    }

    @Test
    void findAllReturnsEveryPersistedAccount() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(transientAccount("Checking"));
            db.repository.save(transientAccount("Savings"));

            List<Account> accounts = db.repository.findAll();

            assertEquals(2, accounts.size());
        }
    }

    @Test
    void findAllOnAnEmptyDatabaseReturnsAnEmptyList() {
        try (TestDatabase db = newRepository()) {
            List<Account> accounts = db.repository.findAll();

            assertTrue(accounts.isEmpty());
        }
    }

    @Test
    void accountTypeAndCurrencyEnumsRoundTripThroughSqliteCorrectly() {
        try (TestDatabase db = newRepository()) {
            for (AccountType type : AccountType.values()) {
                for (Currency currency : Currency.values()) {
                    Account saved = db.repository.save(
                            new Account(0L, type + "-" + currency, type, currency, BigDecimal.ZERO));

                    Account retrieved = db.repository.findById(saved.getId()).orElseThrow();

                    assertEquals(type, retrieved.getType(),
                            () -> "AccountType " + type + " did not round-trip correctly");
                    assertEquals(currency, retrieved.getCurrency(),
                            () -> "Currency " + currency + " did not round-trip correctly");
                }
            }
        }
    }

    // ===================== DELETE =====================

    @Test
    void deletingAnAccountRemovesItFromFindByIdAndFindAll() {
        try (TestDatabase db = newRepository()) {
            Account first = db.repository.save(transientAccount("Checking"));
            Account second = db.repository.save(transientAccount("Savings"));

            db.repository.delete(first.getId());

            assertFalse(db.repository.findById(first.getId()).isPresent());
            List<Account> remaining = db.repository.findAll();
            assertEquals(1, remaining.size());
            assertEquals(second.getId(), remaining.get(0).getId());
        }
    }

    @Test
    void deletingAMissingIdDoesNothing() {
        try (TestDatabase db = newRepository()) {
            db.repository.save(transientAccount("Checking"));

            db.repository.delete(999_999L);

            assertEquals(1, db.repository.findAll().size());
        }
    }
}
