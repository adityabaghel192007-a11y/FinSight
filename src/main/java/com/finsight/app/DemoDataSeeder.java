package com.finsight.app;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.category.Category;
import com.finsight.domain.category.CategoryType;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.FinancialProfileRepository;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Seeds the smallest possible amount of deterministic sample data so a freshly created FinSight
 * database can actually run a simulation, instead of failing immediately with "no FinancialProfile
 * recorded yet" (see {@code SnapshotBuilder#build}).
 *
 * <p><b>Why this exists (Milestone 5E finding):</b> before this class, {@link CompositionRoot}
 * would compose a fully-working {@code SimulationApplicationService} even against a brand new,
 * empty, file-backed database (5D's {@code ~/.finsight/finsight.db}), but the very first call to
 * {@code runScenario} against it would always fail — a {@link FinancialProfile} is the one thing
 * {@code SnapshotBuilder} refuses to proceed without, and nothing in the codebase before this
 * milestone ever created one outside of a test. There is no data-entry UI yet (that is explicitly
 * out of scope for this milestone), so without this class the first end-to-end simulation
 * workflow would have nothing to demonstrate.
 *
 * <p><b>What this seeds — exactly this, and nothing else:</b>
 * <ul>
 *   <li>one {@link FinancialProfile}: monthly income {@code 60,000.00}, monthly expenses
 *       {@code 40,000.00};</li>
 *   <li>one {@link Account}: {@code "Demo Checking Account"}, {@link AccountType#BANK},
 *       {@link Currency#INR}, opening balance {@code 150,000.00};</li>
 *   <li><b>(Milestone 5J)</b> two {@link Category Categories}: {@code "Salary"}
 *       ({@link CategoryType#INCOME}) and {@code "Groceries"} ({@link CategoryType#EXPENSE}).</li>
 * </ul>
 * No investments, loans, goals, or transactions are created. These rows are enough for
 * {@code SnapshotBuilder}/{@code SimulationEngine} to produce a real, meaningful projection (a
 * cash balance that grows every month by income minus expenses), and — since Milestone 5J — for
 * the "Add Transaction" workflow to have at least one {@code INCOME} and one {@code EXPENSE}
 * category to actually select and record against. Without these two categories, a freshly created
 * database would have {@link CategoryRepository#findAll()} return empty, and every call to
 * {@code TransactionApplicationService.recordIncome}/{@code recordExpense} would fail validation
 * with "Category not found" no matter what the user entered — this class does not otherwise change
 * how those methods validate a category, it only ensures at least one valid one of each type
 * exists to validate against. This class still does not take on the job of a general-purpose
 * sample-data generator: no other category, no transaction, and nothing else is seeded.
 *
 * <p><b>When this runs, and why it is safe to call unconditionally:</b> {@link #seedIfEmpty} checks
 * two independent guards, each only ever writing its own data once: the profile/account are
 * written only if {@link FinancialProfileRepository#find()} is empty, and the two demo categories
 * are written only if {@link CategoryRepository#findAll()} is empty (see
 * {@link #seedDemoCategoriesIfMissing}) — <b>not</b> the same single guard, so a database that
 * already has a profile but no categories still gets the categories seeded (see that method's own
 * documentation for why this was changed). Either way, this class never overwrites, deletes, or
 * otherwise touches data that already exists. {@link CompositionRoot} calls this once, during
 * construction, immediately after building the repositories it needs.
 *
 * <p>This is demo/bootstrap data, not a financial fixture — it deliberately lives in
 * {@code com.finsight.app} next to {@link CompositionRoot} and {@link DatabaseLocation}, not in
 * {@code com.finsight.domain} or {@code com.finsight.service}, to keep it clearly separated from
 * production domain/application logic.
 *
 * <p><b>Category seeding is independently guarded (Milestone 5J fix).</b> An earlier version of
 * this class nested the two demo categories inside the same {@code if (profile is absent) return;}
 * check as the profile and account, which meant a database that already had a
 * {@link FinancialProfile} recorded — including the common case of an existing installation
 * upgraded to a version of the app with categories for the first time — would never get the two
 * categories seeded at all: {@link CategoryRepository#findAll()} would stay permanently empty,
 * and the "Add Transaction" workflow would have nothing to select in its category dropdown, with
 * every {@code recordIncome}/{@code recordExpense} call then failing "Category not found" no
 * matter what the user entered. {@link #seedIfEmpty} now checks the profile/account guard and the
 * category guard <b>separately</b>: whichever one is already satisfied is left alone, and whichever
 * one is not gets seeded, independently of the other. See {@link #seedDemoCategoriesIfMissing} for
 * exactly how the category guard works.
 */
final class DemoDataSeeder {

    private DemoDataSeeder() {
        // stateless utility class; not instantiable
    }

    /**
     * Seeds whichever of the fixed demo data this database is still missing: the
     * {@link FinancialProfile} and {@link Account} (together, guarded by whether a profile has
     * ever been recorded — see {@link FinancialProfileRepository#find()}), and, independently, the
     * two demo {@link Category Categories} (guarded by whether any category has ever been recorded
     * — see {@link #seedDemoCategoriesIfMissing}). Each guard is checked and acted on separately, so
     * either kind of seed data can be added on its own without touching or duplicating the other,
     * and neither ever overwrites, deletes, or otherwise touches data that already exists.
     */
    static void seedIfEmpty(
            FinancialProfileRepository financialProfileRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository) {
        Objects.requireNonNull(financialProfileRepository, "financialProfileRepository must not be null");
        Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        Objects.requireNonNull(categoryRepository, "categoryRepository must not be null");

        if (financialProfileRepository.find().isEmpty()) {
            financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal("60000.00"), new BigDecimal("40000.00")));
            accountRepository.save(new Account(
                    0L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00")));
        }

        seedDemoCategoriesIfMissing(categoryRepository);
    }

    /**
     * Seeds the two fixed demo categories ({@code "Salary"}/{@link CategoryType#INCOME} and
     * {@code "Groceries"}/{@link CategoryType#EXPENSE}) if and only if
     * {@link CategoryRepository#findAll()} currently returns empty — i.e. only on a database that
     * has never had any category recorded, exactly mirroring how the profile/account guard above
     * treats "no profile recorded yet" as its own signal for "seeding has not happened here yet".
     *
     * <p>{@link CategoryRepository} exposes no by-name lookup (only {@code findById}, {@code
     * findAll}, {@code save}, {@code delete} — see its own documentation), and this class does not
     * add one merely to check for these two specific rows: doing so would mean introducing a
     * category-management capability this milestone has no other use for. "Any category already
     * exists" is the smallest check {@link CategoryRepository}'s existing API supports, and — since
     * this is demo/bootstrap data seeded nowhere else in the codebase — it is a reliable proxy for
     * "these two demo categories have already been seeded here", exactly as
     * {@code financialProfileRepository.find().isPresent()} is already trusted as a proxy for "the
     * profile/account have already been seeded here" above. If a real category-management workflow
     * is ever introduced, this guard should be revisited then, not before.
     */
    private static void seedDemoCategoriesIfMissing(CategoryRepository categoryRepository) {
        if (categoryRepository.findAll().isEmpty()) {
            categoryRepository.save(new Category(0L, "Salary", CategoryType.INCOME));
            categoryRepository.save(new Category(0L, "Groceries", CategoryType.EXPENSE));
        }
    }
}
