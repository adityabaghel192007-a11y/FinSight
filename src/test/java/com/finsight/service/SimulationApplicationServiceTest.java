package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.goal.Goal;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.domain.transaction.Transaction;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.GoalRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.simulation.comparison.ScenarioComparison;
import com.finsight.simulation.comparison.ScenarioComparisonCalculator;
import com.finsight.simulation.engine.SimulationEngine;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.simulation.model.Projection;
import com.finsight.simulation.model.SimulationParameters;
import com.finsight.simulation.scenario.IncomeChangeEvent;
import com.finsight.simulation.scenario.InvestmentContributionChangeEvent;
import com.finsight.simulation.scenario.Scenario;
import com.finsight.simulation.scenario.ScenarioEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for {@link SimulationApplicationService} and {@link SimulationResult}.
 *
 * <p>{@link SnapshotBuilder} is a concrete, {@code final} class, not an interface, so it cannot be
 * faked or mocked itself (this project's convention is small in-memory fakes, never Mockito — see
 * {@link SnapshotBuilderTest}). Instead, exactly as {@link SnapshotBuilderTest} does, a real
 * {@link SnapshotBuilder} is constructed here, wired to small in-memory fakes of the six repository
 * interfaces it depends on. This proves {@link SimulationApplicationService} depends solely on
 * {@link SnapshotBuilder} — it is never given, and could not compile against, a JDBC type.
 *
 * <p>{@code SimulationEngine} and {@code ScenarioComparisonCalculator} are themselves stateless
 * static-method utility classes, deliberately not injected into
 * {@link SimulationApplicationService} (see its own class-level Javadoc), so they cannot be
 * intercepted or spied on either. Two requirements from this milestone's test list are therefore
 * verified indirectly rather than by capturing object identity directly:
 * <ul>
 *   <li><b>"baseline and scenario use the same {@code FinancialSnapshot} instance"</b> — verified
 *       by counting {@link FinancialProfileRepository#find()} calls, which {@code
 *       SnapshotBuilder.build} invokes exactly once per call: see
 *       {@link #exactlyOneFinancialSnapshotIsBuiltAndReusedForBothRuns()}. If
 *       {@code runScenario} built a second snapshot for the scenario run, this count would be 2,
 *       not 1 — and since only one snapshot is ever built, both runs necessarily share it.</li>
 *   <li><b>"baseline and scenario use the same {@code SimulationParameters} instance"</b> —
 *       {@code SimulationParameters} is itself concrete and {@code final} with no observable side
 *       effect on its getters, so there is no seam to intercept a "different" instance even if one
 *       were used. This is instead verified end-to-end in
 *       {@link #correctlyProducesTheExactContractedResult()}, which independently recomputes the
 *       expected baseline/scenario/comparison by calling the exact same static operations, by
 *       hand, with the one {@code parameters} instance the test holds — the results only match if
 *       the service used that same instance for both calls.</li>
 * </ul>
 * This mirrors how {@link SnapshotBuilderTest} itself documents the one requirement it cannot
 * directly exercise (the unreachable unsupported-currency guard) rather than fabricating a
 * workaround such as reflection.
 */
class SimulationApplicationServiceTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 1);

    // ===================== in-memory fakes (same pattern as SnapshotBuilderTest) =====================

    private static final class CountingFinancialProfileRepository implements FinancialProfileRepository {
        private FinancialProfile profile;
        private long nextId = 1;
        private int findCallCount = 0;

        @Override
        public Optional<FinancialProfile> find() {
            findCallCount++;
            return Optional.ofNullable(profile);
        }

        @Override
        public FinancialProfile save(FinancialProfile profile) {
            long id = (this.profile == null) ? nextId++ : this.profile.getId();
            FinancialProfile saved = profile.withId(id);
            this.profile = saved;
            return saved;
        }
    }

    private static final class InMemoryAccountRepository implements AccountRepository {
        private final Map<Long, Account> accounts = new HashMap<>();
        private long nextId = 1;

        @Override
        public Account save(Account account) {
            long id = nextId++;
            Account saved = account.withId(id);
            accounts.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Account> findById(long id) {
            return Optional.ofNullable(accounts.get(id));
        }

        @Override
        public List<Account> findAll() {
            return List.copyOf(accounts.values());
        }

        @Override
        public void delete(long id) {
            accounts.remove(id);
        }
    }

    private static final class InMemoryTransactionRepository implements TransactionRepository {
        private final Map<Long, Transaction> transactions = new HashMap<>();
        private long nextId = 1;

        @Override
        public Transaction save(Transaction transaction) {
            long id = nextId++;
            Transaction saved = transaction.withId(id);
            transactions.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Transaction> findById(long id) {
            return Optional.ofNullable(transactions.get(id));
        }

        @Override
        public List<Transaction> findAll() {
            return List.copyOf(transactions.values());
        }

        @Override
        public List<Transaction> findByAccountId(long accountId) {
            List<Transaction> result = new ArrayList<>();
            for (Transaction transaction : transactions.values()) {
                if (transaction.getAccountId() == accountId) {
                    result.add(transaction);
                }
            }
            return List.copyOf(result);
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            Transaction existing = transactions.get(transactionId);
            transactions.put(transactionId, new Transaction(
                    existing.getId(), existing.getAccountId(), existing.getAmount(), existing.getType(),
                    existing.getCategoryId().orElse(null), existing.getDate(),
                    existing.getDescription().orElse(null), transferGroupId));
        }

        @Override
        public void delete(long id) {
            transactions.remove(id);
        }
    }

    private static final class InMemoryInvestmentRepository implements InvestmentRepository {
        private final Map<Long, Investment> investments = new HashMap<>();
        private long nextId = 1;

        @Override
        public Investment save(Investment investment) {
            long id = nextId++;
            Investment saved = investment.withId(id);
            investments.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Investment> findById(long id) {
            return Optional.ofNullable(investments.get(id));
        }

        @Override
        public List<Investment> findAll() {
            return List.copyOf(investments.values());
        }

        @Override
        public void delete(long id) {
            investments.remove(id);
        }
    }

    private static final class InMemoryLoanRepository implements LoanRepository {
        private final Map<Long, Loan> loans = new HashMap<>();
        private long nextId = 1;

        @Override
        public Loan save(Loan loan) {
            long id = nextId++;
            Loan saved = loan.withId(id);
            loans.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Loan> findById(long id) {
            return Optional.ofNullable(loans.get(id));
        }

        @Override
        public List<Loan> findAll() {
            return List.copyOf(loans.values());
        }

        @Override
        public void delete(long id) {
            loans.remove(id);
        }
    }

    private static final class InMemoryGoalRepository implements GoalRepository {
        private final Map<Long, Goal> goals = new HashMap<>();
        private long nextId = 1;

        @Override
        public Goal save(Goal goal) {
            long id = nextId++;
            Goal saved = goal.withId(id);
            goals.put(id, saved);
            return saved;
        }

        @Override
        public Optional<Goal> findById(long id) {
            return Optional.ofNullable(goals.get(id));
        }

        @Override
        public List<Goal> findAll() {
            return List.copyOf(goals.values());
        }

        @Override
        public void delete(long id) {
            goals.remove(id);
        }
    }

    // ===================== test fixture =====================

    private static final class Fixture {
        private final CountingFinancialProfileRepository financialProfileRepository = new CountingFinancialProfileRepository();
        private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        private final InMemoryInvestmentRepository investmentRepository = new InMemoryInvestmentRepository();
        private final InMemoryLoanRepository loanRepository = new InMemoryLoanRepository();
        private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();

        private final SnapshotBuilder snapshotBuilder = new SnapshotBuilder(
                financialProfileRepository, accountRepository, transactionRepository,
                investmentRepository, loanRepository, goalRepository);

        private final SimulationApplicationService service = new SimulationApplicationService(snapshotBuilder);

        private void profile(String monthlyIncome, String monthlyExpenses) {
            financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal(monthlyIncome), new BigDecimal(monthlyExpenses)));
        }

        private Account account(Currency currency, String openingBalance) {
            return accountRepository.save(
                    new Account(0L, currency + " account", AccountType.BANK, currency, new BigDecimal(openingBalance)));
        }

        private Investment investment(long accountId) {
            return investmentRepository.save(new Investment(0L, accountId, "ETF", null, AssetType.ETF, Currency.INR,
                    BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("0.10"), BigDecimal.ZERO));
        }
    }

    private static SimulationParameters parameters(int projectionMonths, BigDecimal usdToInrRate) {
        return new SimulationParameters(projectionMonths, BigDecimal.ZERO, BigDecimal.ZERO, usdToInrRate);
    }

    private static Scenario scenario(ScenarioEvent... events) {
        return new Scenario(1L, "Test scenario", null, AS_OF, List.of(events));
    }

    // ===================== 1. asOfDate reaches the snapshot =====================

    @Test
    void runScenarioBuildsTheSnapshotUsingTheSuppliedAsOfDate() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        LocalDate explicitDate = LocalDate.of(2030, 6, 15);

        SimulationResult result = fixture.service.runScenario(explicitDate, parameters(1, BigDecimal.ONE), scenario());

        assertEquals(explicitDate, result.getBaselineProjection().getStartDate());
        assertEquals(explicitDate, result.getScenarioProjection().getStartDate());
    }

    // ===================== 2. FX rate reaches SnapshotBuilder from parameters =====================

    @Test
    void fxRatePassedToSnapshotBuilderComesFromParameters() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        fixture.account(Currency.USD, "100.00");

        SimulationResult result = fixture.service.runScenario(AS_OF, parameters(1, new BigDecimal("83")), scenario());

        // No income/expenses/contributions/payments, so month 1's cash balance is exactly the
        // snapshot's consolidated cash: 100.00 USD * 83 = 8300.00 INR. This is only true if the
        // 83 rate given to `parameters` actually reached SnapshotBuilder's cash consolidation.
        BigDecimal month1Cash = result.getBaselineProjection().getStates().get(0).getCashBalance();
        assertEquals(0, new BigDecimal("8300.00").compareTo(month1Cash));
    }

    // ===================== 3/4. baseline and scenario projections are both produced =====================

    @Test
    void baselineProjectionIsProduced() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");

        SimulationResult result = fixture.service.runScenario(AS_OF, parameters(3, BigDecimal.ONE), scenario());

        assertEquals(3, result.getBaselineProjection().getStates().size());
    }

    @Test
    void scenarioProjectionIsProducedUsingTheScenariosEvents() {
        Fixture fixture = new Fixture();
        fixture.profile("1000.00", "0");
        Scenario scenarioWithIncomeChange = scenario(new IncomeChangeEvent(AS_OF, new BigDecimal("5000.00")));

        SimulationResult result = fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenarioWithIncomeChange);

        BigDecimal baselineMonth1Income = result.getBaselineProjection().getStates().get(0).getMonthlyIncome();
        BigDecimal scenarioMonth1Income = result.getScenarioProjection().getStates().get(0).getMonthlyIncome();
        assertEquals(0, new BigDecimal("1000.00").compareTo(baselineMonth1Income));
        assertEquals(0, new BigDecimal("5000.00").compareTo(scenarioMonth1Income));
    }

    // ===================== 5. exactly one snapshot is built =====================

    @Test
    void exactlyOneFinancialSnapshotIsBuiltAndReusedForBothRuns() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");

        fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenario());

        assertEquals(1, fixture.financialProfileRepository.findCallCount,
                "SnapshotBuilder.build() must be called exactly once per runScenario() call, "
                        + "so its baseline and scenario runs necessarily share one FinancialSnapshot instance");
    }

    // ===================== 6. the same SimulationParameters instance drives both runs =====================

    @Test
    void baselineAndScenarioAreDrivenByTheSameParametersInstance() {
        Fixture fixture = new Fixture();
        fixture.profile("1000.00", "500.00");
        SimulationParameters distinctiveParameters = new SimulationParameters(
                13, new BigDecimal("0.10"), new BigDecimal("0.05"), BigDecimal.ONE);

        SimulationResult result = fixture.service.runScenario(AS_OF, distinctiveParameters, scenario());

        // Both runs must reflect this one instance's projectionMonths and its year-13 growth
        // boundary identically (see class-level Javadoc for why full reference-identity capture
        // isn't directly observable, and correctlyProducesTheExactContractedResult() below for the
        // end-to-end cross-check).
        assertEquals(13, result.getBaselineProjection().getStates().size());
        assertEquals(13, result.getScenarioProjection().getStates().size());
        BigDecimal baselineMonth13Income = result.getBaselineProjection().getStates().get(12).getMonthlyIncome();
        BigDecimal scenarioMonth13Income = result.getScenarioProjection().getStates().get(12).getMonthlyIncome();
        // 1000.00 * 1.10 = 1100.00 once the 13th month's annual growth is applied.
        assertEquals(0, new BigDecimal("1100.00").compareTo(baselineMonth13Income));
        assertEquals(0, new BigDecimal("1100.00").compareTo(scenarioMonth13Income));
    }

    // ===================== 7/8. exact contract: result matches independently-recomputed values =====================

    @Test
    void correctlyProducesTheExactContractedResult() {
        Fixture fixture = new Fixture();
        fixture.profile("2000.00", "800.00");
        Account account = fixture.account(Currency.INR, "5000.00");
        Investment investment = fixture.investment(account.getId());
        SimulationParameters testParameters = parameters(6, BigDecimal.ONE);
        Scenario testScenario = scenario(new InvestmentContributionChangeEvent(
                AS_OF.plusMonths(2), investment.getId(), new BigDecimal("500.00")));

        SimulationResult result = fixture.service.runScenario(AS_OF, testParameters, testScenario);

        // Recompute independently: build a second, separately-constructed snapshot from the same
        // (unchanged) fixture data, then call the exact same stateless operations by hand.
        FinancialSnapshot independentSnapshot = fixture.snapshotBuilder.build(AS_OF, testParameters.getUsdToInrRate());
        Projection expectedBaseline = SimulationEngine.simulate(independentSnapshot, testParameters);
        Projection expectedScenario = SimulationEngine.simulate(independentSnapshot, testParameters, testScenario.getEvents());
        ScenarioComparison expectedComparison = ScenarioComparisonCalculator.compare(expectedBaseline, expectedScenario);

        assertEquals(expectedBaseline, result.getBaselineProjection());
        assertEquals(expectedScenario, result.getScenarioProjection());
        assertEquals(expectedComparison, result.getComparison());

        // 8: the comparison is derived from exactly this baseline/scenario pair, not some other one.
        assertEquals(ScenarioComparisonCalculator.compare(result.getBaselineProjection(), result.getScenarioProjection()),
                result.getComparison());
    }

    // ===================== 9. missing FinancialProfile propagates unchanged =====================

    @Test
    void missingFinancialProfileValidationPropagatesUnchanged() {
        Fixture fixture = new Fixture();
        // No profile saved.

        assertThrows(ApplicationValidationException.class,
                () -> fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenario()));
    }

    // ===================== 10. IllegalArgumentException from a lower layer propagates unchanged =====================

    /**
     * {@code SimulationParameters} itself already rejects a zero/negative {@code usdToInrRate} at
     * construction time (see its own validation), so a genuinely-constructed
     * {@code SimulationParameters} can never carry an invalid rate into {@code
     * SnapshotBuilder.build} through this service — that specific guard is unreachable here,
     * exactly like {@code SnapshotBuilder}'s own documented-unreachable unsupported-currency
     * branch (see {@link SnapshotBuilderTest}'s class-level Javadoc). The reachable, equivalent
     * proof that an {@link IllegalArgumentException} from a lower layer propagates unchanged
     * (rather than being wrapped or swallowed) is a conflicting pair of scenario events, which
     * {@code SimulationEngine} itself rejects the same way.
     */
    @Test
    void lowerLayerIllegalArgumentExceptionPropagatesUnchanged() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");
        Scenario conflictingScenario = scenario(
                new IncomeChangeEvent(AS_OF.plusMonths(1), new BigDecimal("1100.00")),
                new IncomeChangeEvent(AS_OF.plusMonths(1), new BigDecimal("1200.00")));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.runScenario(AS_OF, parameters(2, BigDecimal.ONE), conflictingScenario));
    }

    // ===================== 11. unknown investment id propagates unchanged =====================

    @Test
    void unknownInvestmentIdInScenarioEventPropagatesUnchanged() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");
        Scenario scenarioTargetingUnknownInvestment = scenario(
                new InvestmentContributionChangeEvent(AS_OF, 999L, new BigDecimal("100.00")));

        assertThrows(IllegalArgumentException.class,
                () -> fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenarioTargetingUnknownInvestment));
    }

    // ===================== 12. null arguments rejected =====================

    @Test
    void rejectsNullAsOfDate() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(NullPointerException.class,
                () -> fixture.service.runScenario(null, parameters(1, BigDecimal.ONE), scenario()));
    }

    @Test
    void rejectsNullParameters() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(NullPointerException.class,
                () -> fixture.service.runScenario(AS_OF, null, scenario()));
    }

    @Test
    void rejectsNullScenario() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(NullPointerException.class,
                () -> fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), null));
    }

    // ===================== 13. determinism =====================

    @Test
    void repeatedExecutionWithIdenticalInputsIsDeterministic() {
        Fixture fixture = new Fixture();
        fixture.profile("1500.00", "600.00");
        fixture.account(Currency.INR, "2000.00");
        SimulationParameters testParameters = parameters(4, BigDecimal.ONE);
        Scenario testScenario = scenario(new IncomeChangeEvent(AS_OF.plusMonths(1), new BigDecimal("1600.00")));

        SimulationResult first = fixture.service.runScenario(AS_OF, testParameters, testScenario);
        SimulationResult second = fixture.service.runScenario(AS_OF, testParameters, testScenario);

        assertEquals(first, second);
    }

    // ===================== SimulationResult itself (section 10 of the design) =====================

    @Test
    void simulationResultRejectsNullValues() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");
        SimulationResult valid = fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenario());
        Projection baseline = valid.getBaselineProjection();
        Projection scenarioProjection = valid.getScenarioProjection();
        ScenarioComparison comparison = valid.getComparison();

        assertThrows(NullPointerException.class, () -> new SimulationResult(null, scenarioProjection, comparison));
        assertThrows(NullPointerException.class, () -> new SimulationResult(baseline, null, comparison));
        assertThrows(NullPointerException.class, () -> new SimulationResult(baseline, scenarioProjection, null));
    }

    @Test
    void simulationResultEqualityIsValueBased() {
        Fixture fixture = new Fixture();
        fixture.profile("1000", "0");
        SimulationResult first = fixture.service.runScenario(AS_OF, parameters(1, BigDecimal.ONE), scenario());
        SimulationResult second = new SimulationResult(
                first.getBaselineProjection(), first.getScenarioProjection(), first.getComparison());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
