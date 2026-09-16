package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.CategoryRepositoryJdbc;
import com.finsight.repository.jdbc.FinancialProfileRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DemoDataSeeder}, against a real, isolated in-memory SQLite database
 * (never the real application database) — matching this project's established
 * {@code jdbc:sqlite::memory:} test convention (see e.g. {@code CompositionRootTest}).
 *
 * <p><b>Milestone 5J</b> added {@code categoryRepository} to {@link DemoDataSeeder#seedIfEmpty}'s
 * signature, originally seeding two demo categories under the same single guard as the profile and
 * account. <b>A Milestone 5J follow-up fix</b> found that this was a real bug: a database that
 * already had a {@link FinancialProfile} recorded (the normal case for anyone who had already run
 * the app) would never get the two demo categories seeded at all, leaving the "Add Transaction"
 * form's category dropdown permanently empty. {@link DemoDataSeeder#seedIfEmpty} now guards
 * category seeding independently of the profile/account guard (see that class's own
 * documentation). This required correcting {@link #doesNotOverwriteAnAlreadyExistingFinancialProfileOrCreateAnAccount()}
 * below, whose old name and assertions ({@code doesNotOverwriteAnAlreadyExistingFinancialProfileOrCreateAnAccountOrCategories})
 * had encoded the very bug being fixed (asserting categories stayed empty when a profile already
 * existed) — that is a correction of a test that asserted incorrect behavior, not a weakening of
 * a correct one; every assertion it made about the profile and account being left untouched is
 * unchanged. Two new tests were added below to cover the fix directly.
 */
class DemoDataSeederTest {

    private static final class TestDatabase implements AutoCloseable {
        private final SqliteConnectionProvider connectionProvider;
        private final FinancialProfileRepositoryJdbc financialProfileRepository;
        private final AccountRepositoryJdbc accountRepository;
        private final CategoryRepositoryJdbc categoryRepository;

        private TestDatabase() {
            this.connectionProvider = new SqliteConnectionProvider("jdbc:sqlite::memory:");
            SchemaInitializer.initialize(connectionProvider.getConnection());
            this.financialProfileRepository = new FinancialProfileRepositoryJdbc(connectionProvider);
            this.accountRepository = new AccountRepositoryJdbc(connectionProvider);
            this.categoryRepository = new CategoryRepositoryJdbc(connectionProvider);
        }

        @Override
        public void close() {
            connectionProvider.close();
        }
    }

    @Test
    void seedsTheDemoProfileAndAccountWhenDatabaseIsEmpty() {
        try (TestDatabase db = new TestDatabase()) {
            assertTrue(db.financialProfileRepository.find().isEmpty(), "precondition: no profile recorded yet");
            assertTrue(db.accountRepository.findAll().isEmpty(), "precondition: no account recorded yet");
            assertTrue(db.categoryRepository.findAll().isEmpty(), "precondition: no category recorded yet");

            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);

            FinancialProfile profile = db.financialProfileRepository.find()
                    .orElseThrow(() -> new AssertionError("seedIfEmpty must have recorded a FinancialProfile"));
            assertEquals(0, new BigDecimal("60000.00").compareTo(profile.getMonthlyIncome()));
            assertEquals(0, new BigDecimal("40000.00").compareTo(profile.getMonthlyExpenses()));

            List<Account> accounts = db.accountRepository.findAll();
            assertEquals(1, accounts.size(), "exactly one demo account must be created");
            Account account = accounts.get(0);
            assertEquals("Demo Checking Account", account.getName());
            assertEquals(AccountType.BANK, account.getType());
            assertEquals(Currency.INR, account.getCurrency());
            assertEquals(0, new BigDecimal("150000.00").compareTo(account.getOpeningBalance()));

            List<Category> categories = db.categoryRepository.findAll();
            assertEquals(2, categories.size(), "exactly two demo categories must be created (Milestone 5J)");
            assertTrue(categories.stream().anyMatch(
                            c -> c.getName().equals("Salary") && c.getType() == CategoryType.INCOME),
                    "an INCOME category named \"Salary\" must be seeded");
            assertTrue(categories.stream().anyMatch(
                            c -> c.getName().equals("Groceries") && c.getType() == CategoryType.EXPENSE),
                    "an EXPENSE category named \"Groceries\" must be seeded");
        }
    }

    /**
     * Renamed from {@code doesNotOverwriteAnAlreadyExistingFinancialProfileOrCreateAnAccountOrCategories}
     * as part of the Milestone 5J category-seeding bug fix: that name and its old
     * "categories stay empty" assertion described the bug being fixed, not correct behavior. The
     * profile/account assertions below are unchanged from that version.
     */
    @Test
    void doesNotOverwriteAnAlreadyExistingFinancialProfileOrCreateAnAccount() {
        try (TestDatabase db = new TestDatabase()) {
            FinancialProfile realProfile = db.financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal("99999.00"), new BigDecimal("11111.00")));

            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);

            FinancialProfile afterSeedAttempt = db.financialProfileRepository.find().orElseThrow();
            assertEquals(0, realProfile.getMonthlyIncome().compareTo(afterSeedAttempt.getMonthlyIncome()),
                    "an already-recorded real profile must never be overwritten by demo data");
            assertEquals(0, realProfile.getMonthlyExpenses().compareTo(afterSeedAttempt.getMonthlyExpenses()));
            assertTrue(db.accountRepository.findAll().isEmpty(),
                    "the profile/account guard is a single check on FinancialProfile presence, so "
                            + "once a profile already exists no demo account is created either, "
                            + "even though this database happens to have no accounts");
        }
    }

    /**
     * Milestone 5J bug-fix test: this is the exact scenario the fix addresses — a database with an
     * existing {@link FinancialProfile} (and, incidentally, its account) but no categories yet must
     * still get the two demo categories seeded, since category seeding is now guarded
     * independently of the profile/account guard. Before the fix, this test would have failed:
     * {@code categoryRepository.findAll()} would have stayed empty.
     */
    @Test
    void seedsMissingCategoriesEvenWhenAProfileAndAccountAlreadyExist() {
        try (TestDatabase db = new TestDatabase()) {
            FinancialProfile realProfile = db.financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal("99999.00"), new BigDecimal("11111.00")));
            Account realAccount = db.accountRepository.save(new Account(
                    0L, "My Real Account", AccountType.BANK, Currency.INR, new BigDecimal("42000.00")));
            assertTrue(db.categoryRepository.findAll().isEmpty(), "precondition: no category recorded yet");

            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);

            FinancialProfile afterSeedAttempt = db.financialProfileRepository.find().orElseThrow();
            assertEquals(0, realProfile.getMonthlyIncome().compareTo(afterSeedAttempt.getMonthlyIncome()),
                    "the existing real profile must never be overwritten by demo data");
            assertEquals(0, realProfile.getMonthlyExpenses().compareTo(afterSeedAttempt.getMonthlyExpenses()));

            List<Account> accounts = db.accountRepository.findAll();
            assertEquals(1, accounts.size(), "no demo account must be added once a real profile already exists");
            assertEquals(realAccount.getId(), accounts.get(0).getId(),
                    "the existing real account must be untouched, not replaced or duplicated");
            assertEquals("My Real Account", accounts.get(0).getName());

            List<Category> categories = db.categoryRepository.findAll();
            assertEquals(2, categories.size(),
                    "the two demo categories must still be seeded even though a profile/account "
                            + "already exist — category seeding is guarded independently, which is "
                            + "exactly the bug this fix addresses");
            assertTrue(categories.stream().anyMatch(
                    c -> c.getName().equals("Salary") && c.getType() == CategoryType.INCOME));
            assertTrue(categories.stream().anyMatch(
                    c -> c.getName().equals("Groceries") && c.getType() == CategoryType.EXPENSE));
        }
    }

    /**
     * Milestone 5J bug-fix test: pre-existing categories (regardless of whether this class or
     * something else created them) must never be duplicated, independently of whatever state the
     * profile/account guard is in. Here the profile is absent (so the profile/account guard does
     * fire), while categories already exist (so that guard must not fire) — proving the two guards
     * really are independent in both directions, not just the direction the other new test covers.
     */
    @Test
    void doesNotDuplicateCategoriesThatAlreadyExistRegardlessOfProfileState() {
        try (TestDatabase db = new TestDatabase()) {
            Category existingSalary = db.categoryRepository.save(new Category(0L, "Salary", CategoryType.INCOME));
            Category existingGroceries = db.categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE));
            assertTrue(db.financialProfileRepository.find().isEmpty(), "precondition: no profile recorded yet");

            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);

            assertTrue(db.financialProfileRepository.find().isPresent(),
                    "the profile/account guard must still fire normally since no profile existed yet");
            assertEquals(1, db.accountRepository.findAll().size());

            List<Category> categories = db.categoryRepository.findAll();
            assertEquals(2, categories.size(), "pre-existing categories must not be duplicated by the seeder");
            assertTrue(categories.contains(existingSalary));
            assertTrue(categories.contains(existingGroceries));
        }
    }

    @Test
    void isSafeToCallRepeatedlyWithoutDuplicatingTheDemoAccountOrCategories() {
        try (TestDatabase db = new TestDatabase()) {
            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);
            DemoDataSeeder.seedIfEmpty(db.financialProfileRepository, db.accountRepository, db.categoryRepository);

            assertEquals(1, db.accountRepository.findAll().size(),
                    "a second call must be a complete no-op once a profile exists from the first");
            assertEquals(2, db.categoryRepository.findAll().size(),
                    "a second call must not duplicate the two demo categories either (Milestone 5J)");
        }
    }
}
