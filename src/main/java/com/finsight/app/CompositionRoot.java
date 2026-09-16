package com.finsight.app;

import com.finsight.repository.AccountRepository;
import com.finsight.repository.CategoryRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.GoalRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.jdbc.AccountRepositoryJdbc;
import com.finsight.repository.jdbc.CategoryRepositoryJdbc;
import com.finsight.repository.jdbc.FinancialProfileRepositoryJdbc;
import com.finsight.repository.jdbc.GoalRepositoryJdbc;
import com.finsight.repository.jdbc.InvestmentRepositoryJdbc;
import com.finsight.repository.jdbc.LoanRepositoryJdbc;
import com.finsight.repository.jdbc.SchemaInitializer;
import com.finsight.repository.jdbc.SqliteConnectionProvider;
import com.finsight.repository.jdbc.TransactionRepositoryJdbc;
import com.finsight.service.FinancialDataApplicationService;
import com.finsight.service.FinancialProfileApplicationService;
import com.finsight.service.SimulationApplicationService;
import com.finsight.service.SnapshotBuilder;
import com.finsight.service.TransactionApplicationService;

import java.util.Objects;

/**
 * The application's single composition root: the one place responsible for turning a JDBC URL
 * into a fully wired set of application services, and for owning the database connection's
 * lifecycle from startup to shutdown.
 *
 * <p>Before this class existed, the object graph below was hand-duplicated in every integration
 * test that needed a real database ({@code AccountBalanceCalculatorIntegrationTest},
 * {@code TransactionApplicationServiceIntegrationTest},
 * {@code SimulationApplicationServiceIntegrationTest}); production code had no path from a JDBC
 * URL to a working service at all. This class is that one path, used by
 * {@link FinSightApplication#init()} and equally usable, unchanged, from a test — see
 * {@code CompositionRootTest}.
 *
 * <p><b>What this class owns:</b> exactly one {@link SqliteConnectionProvider}, opened from the
 * {@code jdbcUrl} given to the constructor — never a hardcoded production path; the caller
 * decides, matching {@link SqliteConnectionProvider}'s own constructor convention. The database
 * schema is initialized exactly once, during construction, via
 * {@link SchemaInitializer#initialize(java.sql.Connection)}. If that fails, the
 * {@link com.finsight.repository.RepositoryException} it throws propagates unchanged and
 * construction fails outright, rather than leaving a partially-initialized instance around for a
 * caller to accidentally use.
 *
 * <p><b>What this class builds:</b> one instance of each JDBC repository the application services
 * actually need — {@link FinancialProfileRepositoryJdbc}, {@link AccountRepositoryJdbc},
 * {@link TransactionRepositoryJdbc}, {@link InvestmentRepositoryJdbc}, {@link LoanRepositoryJdbc},
 * and {@link GoalRepositoryJdbc} for {@link SnapshotBuilder}, plus {@link CategoryRepositoryJdbc}
 * (needed only by {@link TransactionApplicationService}, not by {@link SnapshotBuilder}) — then
 * {@link SnapshotBuilder}, {@link SimulationApplicationService}, {@link TransactionApplicationService},
 * and (Milestone 5J) {@link FinancialDataApplicationService} from those same repository instances —
 * no repository is ever constructed twice.
 *
 * <p>{@code BudgetRepositoryJdbc} is deliberately <b>not</b> constructed here: nothing in the
 * current dependency graph ({@link SnapshotBuilder} or {@link TransactionApplicationService})
 * consumes a {@code BudgetRepository}, so wiring one now would be speculative repository wiring
 * with no concrete consumer. It can be added the moment a real consumer needs it.
 *
 * <p><b>Demo data (Milestone 5E):</b> immediately after {@code financialProfileRepository} and
 * {@code accountRepository} are constructed, {@link DemoDataSeeder#seedIfEmpty} is called against
 * them. It writes a small, fixed set of sample data only if the database is genuinely empty (no
 * {@link com.finsight.domain.financialprofile.FinancialProfile} has ever been recorded) — see its
 * own documentation for exactly what it creates and why it exists. This never touches a database
 * that already has real data.
 *
 * <p>{@link SqliteConnectionProvider} itself implements
 * {@link com.finsight.repository.TransactionRunner} (see its own documentation), so it is passed
 * directly to {@link TransactionApplicationService} for that role — no separate adapter is
 * needed.
 *
 * <p><b>Financial profile editing (Milestone 5H):</b> {@link FinancialProfileApplicationService}
 * is built directly from the same {@code financialProfileRepository} instance used by
 * {@link SnapshotBuilder} — there is exactly one {@link FinancialProfileRepository} instance per
 * {@code CompositionRoot}, so a save through {@link FinancialProfileApplicationService} and a
 * read through {@link SnapshotBuilder} always see the same underlying table, never two divergent
 * copies of the data.
 *
 * <p>This class performs no business logic of its own — only construction and lifecycle. It holds
 * no static state and is not a singleton: there is no {@code getInstance()} or other global
 * accessor. {@link FinSightApplication} owns exactly one instance of it explicitly, constructing
 * it in {@code init()} and closing it in {@code stop()}.
 *
 * <p><b>Resource ownership:</b> the object that creates the {@link SqliteConnectionProvider} is
 * responsible for closing it — see {@link #close()}.
 */
public final class CompositionRoot implements AutoCloseable {

    private final SqliteConnectionProvider connectionProvider;
    private final SimulationApplicationService simulationApplicationService;
    private final TransactionApplicationService transactionApplicationService;
    private final FinancialProfileApplicationService financialProfileApplicationService;
    private final FinancialDataApplicationService financialDataApplicationService;

    /**
     * Opens a connection to {@code jdbcUrl}, initializes the database schema on it, and
     * constructs every repository and application service the current codebase needs from it.
     *
     * @param jdbcUrl the JDBC URL to open — e.g. {@code "jdbc:sqlite:/path/to/finsight.db"} for a
     *     real file, or {@code "jdbc:sqlite::memory:"} for an isolated in-memory database; never
     *     hardcoded here
     * @throws NullPointerException if {@code jdbcUrl} is {@code null}
     * @throws com.finsight.repository.RepositoryException if the connection cannot be opened, or
     *     if schema initialization fails — construction fails outright rather than leaving a
     *     partially-initialized instance behind
     */
    public CompositionRoot(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");

        this.connectionProvider = new SqliteConnectionProvider(jdbcUrl);
        SchemaInitializer.initialize(connectionProvider.getConnection());

        FinancialProfileRepository financialProfileRepository = new FinancialProfileRepositoryJdbc(connectionProvider);
        AccountRepository accountRepository = new AccountRepositoryJdbc(connectionProvider);
        TransactionRepository transactionRepository = new TransactionRepositoryJdbc(connectionProvider);
        InvestmentRepository investmentRepository = new InvestmentRepositoryJdbc(connectionProvider);
        LoanRepository loanRepository = new LoanRepositoryJdbc(connectionProvider);
        GoalRepository goalRepository = new GoalRepositoryJdbc(connectionProvider);
        CategoryRepository categoryRepository = new CategoryRepositoryJdbc(connectionProvider);

        DemoDataSeeder.seedIfEmpty(financialProfileRepository, accountRepository, categoryRepository);

        SnapshotBuilder snapshotBuilder = new SnapshotBuilder(
                financialProfileRepository, accountRepository, transactionRepository,
                investmentRepository, loanRepository, goalRepository);
        this.simulationApplicationService = new SimulationApplicationService(snapshotBuilder);

        this.transactionApplicationService = new TransactionApplicationService(
                accountRepository, categoryRepository, transactionRepository, connectionProvider);

        this.financialProfileApplicationService = new FinancialProfileApplicationService(financialProfileRepository);

        this.financialDataApplicationService =
                new FinancialDataApplicationService(accountRepository, categoryRepository, transactionRepository);
    }

    /**
     * The application service for building financial snapshots and running baseline/scenario
     * simulations against them. Backed by the repositories and {@link SnapshotBuilder} this
     * composition root constructed.
     */
    public SimulationApplicationService getSimulationApplicationService() {
        return simulationApplicationService;
    }

    /**
     * The application service for recording income, expense, and transfer transactions. Backed
     * by the repositories and {@link com.finsight.repository.TransactionRunner} this composition
     * root constructed.
     */
    public TransactionApplicationService getTransactionApplicationService() {
        return transactionApplicationService;
    }

    /**
     * The application service for reading and updating the single persisted
     * {@link com.finsight.domain.financialprofile.FinancialProfile} (Milestone 5H). Backed by the
     * same {@link FinancialProfileRepository} instance {@link SnapshotBuilder} reads from.
     */
    public FinancialProfileApplicationService getFinancialProfileApplicationService() {
        return financialProfileApplicationService;
    }

    /**
     * The application service for read-only access to persisted accounts, categories, and
     * transactions (Milestone 5J) — for a UI that needs to display them without a repository
     * accessor. Backed by the same {@link AccountRepository}/{@link CategoryRepository}/
     * {@link TransactionRepository} instances {@link TransactionApplicationService} (and, for
     * accounts/transactions, {@link SnapshotBuilder}) already use, so a read through this service
     * always sees the same data a write through {@link #getTransactionApplicationService()} just
     * persisted.
     */
    public FinancialDataApplicationService getFinancialDataApplicationService() {
        return financialDataApplicationService;
    }

    /**
     * Closes the single {@link SqliteConnectionProvider} this composition root owns. The object
     * that created the database connection is responsible for closing it.
     */
    @Override
    public void close() {
        connectionProvider.close();
    }
}
