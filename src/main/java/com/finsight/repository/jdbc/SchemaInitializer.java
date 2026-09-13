package com.finsight.repository.jdbc;

import com.finsight.repository.RepositoryException;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Creates the complete set of tables FinSight's domain model will eventually be persisted to.
 *
 * <p>Only {@code accounts} has a repository built on it so far (see {@link AccountRepositoryJdbc}).
 * The other tables ({@code categories}, {@code transactions}, {@code investments}, {@code loans},
 * {@code goals}, {@code budgets}) are created here too because their column mapping to the
 * current domain model is already settled, but nothing in this milestone reads or writes them —
 * that is future work. {@code Scenario}/{@code ScenarioEvent} are intentionally not included: their
 * persistence design has not been decided yet.
 *
 * <p><b>Conventions applied uniformly:</b>
 * <ul>
 *   <li>{@code id INTEGER PRIMARY KEY} — SQLite's rowid-aliased primary key, which already
 *       auto-generates on insert; {@code AUTOINCREMENT} is deliberately omitted since nothing
 *       here needs its (slightly slower) guarantee that ids are never reused.</li>
 *   <li>Every {@code BigDecimal} column (money amounts, rates) is {@code TEXT}, never
 *       {@code REAL} — floating-point cannot represent decimal currency values exactly.</li>
 *   <li>{@code LocalDate} columns are {@code TEXT} in ISO {@code YYYY-MM-DD} form;
 *       {@code YearMonth} columns (only {@code budgets.month}) are {@code TEXT} in
 *       {@code YYYY-MM} form. Both are exactly what {@link java.time.LocalDate#toString()} and
 *       {@link java.time.YearMonth#toString()} already produce.</li>
 *   <li>Every enum column is {@code TEXT} holding {@code enum.name()}, never the ordinal, so the
 *       stored value survives the enum's declared constants being reordered later.</li>
 *   <li>A domain field exposed as {@link java.util.Optional} (e.g. {@code Transaction}'s
 *       {@code categoryId}, {@code description}, {@code transferGroupId}) is a nullable column;
 *       every other column is {@code NOT NULL}.</li>
 *   <li>Foreign keys are declared exactly where the domain model has one:
 *       {@code transactions.account_id -> accounts.id}, the nullable
 *       {@code transactions.category_id -> categories.id}, {@code investments.account_id ->
 *       accounts.id}, and {@code budgets.category_id -> categories.id}. {@code transferGroupId}
 *       is grouping metadata between transaction rows, not a reference to another table, so it is
 *       a plain nullable column with no foreign key. No cascade behavior is declared; nothing in
 *       the domain model calls for one yet.</li>
 * </ul>
 *
 * <p>Enforcing the foreign keys declared here is a per-connection setting, not a schema property
 * — see {@link SqliteConnectionProvider}, which turns it on for every connection it opens.
 */
public final class SchemaInitializer {

    private static final String CREATE_ACCOUNTS = """
            CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                currency TEXT NOT NULL,
                opening_balance TEXT NOT NULL
            )
            """;

    private static final String CREATE_CATEGORIES = """
            CREATE TABLE IF NOT EXISTS categories (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL
            )
            """;

    private static final String CREATE_TRANSACTIONS = """
            CREATE TABLE IF NOT EXISTS transactions (
                id INTEGER PRIMARY KEY,
                account_id INTEGER NOT NULL,
                amount TEXT NOT NULL,
                type TEXT NOT NULL,
                category_id INTEGER,
                date TEXT NOT NULL,
                description TEXT,
                transfer_group_id INTEGER,
                FOREIGN KEY (account_id) REFERENCES accounts (id),
                FOREIGN KEY (category_id) REFERENCES categories (id)
            )
            """;

    private static final String CREATE_INVESTMENTS = """
            CREATE TABLE IF NOT EXISTS investments (
                id INTEGER PRIMARY KEY,
                account_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                ticker TEXT,
                asset_type TEXT NOT NULL,
                currency TEXT NOT NULL,
                quantity TEXT NOT NULL,
                average_purchase_price TEXT NOT NULL,
                current_price TEXT NOT NULL,
                expected_annual_return TEXT NOT NULL,
                monthly_contribution TEXT NOT NULL,
                FOREIGN KEY (account_id) REFERENCES accounts (id)
            )
            """;

    // Loan is INR-only as of this milestone, so there is deliberately no currency column here.
    private static final String CREATE_LOANS = """
            CREATE TABLE IF NOT EXISTS loans (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                principal TEXT NOT NULL,
                annual_interest_rate TEXT NOT NULL,
                tenure_months INTEGER NOT NULL,
                start_date TEXT NOT NULL,
                current_balance TEXT NOT NULL,
                remaining_tenure_months INTEGER NOT NULL
            )
            """;

    private static final String CREATE_GOALS = """
            CREATE TABLE IF NOT EXISTS goals (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                target_amount TEXT NOT NULL,
                current_amount TEXT NOT NULL,
                target_date TEXT NOT NULL,
                monthly_contribution TEXT NOT NULL
            )
            """;

    private static final String CREATE_BUDGETS = """
            CREATE TABLE IF NOT EXISTS budgets (
                id INTEGER PRIMARY KEY,
                category_id INTEGER NOT NULL,
                month TEXT NOT NULL,
                amount TEXT NOT NULL,
                FOREIGN KEY (category_id) REFERENCES categories (id)
            )
            """;

    // At most one row is ever inserted here — see FinancialProfileRepository for how that
    // singleton contract is enforced at the repository layer. No CHECK/unique-index-based
    // enforcement is added at the schema level; the repository already guarantees it by
    // construction, and adding a second enforcement mechanism for the same invariant is not
    // needed for this milestone.
    private static final String CREATE_FINANCIAL_PROFILE = """
            CREATE TABLE IF NOT EXISTS financial_profile (
                id INTEGER PRIMARY KEY,
                monthly_income TEXT NOT NULL,
                monthly_expenses TEXT NOT NULL
            )
            """;

    private SchemaInitializer() {
        // stateless utility class; not instantiable
    }

    /**
     * Creates every table (if it does not already exist) on {@code connection}. Safe to call
     * more than once against the same database — every statement is {@code CREATE TABLE IF NOT
     * EXISTS} — so callers do not need to track whether initialization already ran.
     *
     * @throws RepositoryException if any statement fails
     */
    public static void initialize(Connection connection) {
        Objects.requireNonNull(connection, "connection must not be null");
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_ACCOUNTS);
            statement.execute(CREATE_CATEGORIES);
            statement.execute(CREATE_TRANSACTIONS);
            statement.execute(CREATE_INVESTMENTS);
            statement.execute(CREATE_LOANS);
            statement.execute(CREATE_GOALS);
            statement.execute(CREATE_BUDGETS);
            statement.execute(CREATE_FINANCIAL_PROFILE);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to initialize database schema", e);
        }
    }
}
