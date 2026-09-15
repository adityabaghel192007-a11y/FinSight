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
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.GoalRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.simulation.model.FinancialSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SnapshotBuilder}, using small in-memory fakes of every repository
 * interface it depends on ({@link FinancialProfileRepository}, {@link AccountRepository},
 * {@link TransactionRepository}, {@link InvestmentRepository}, {@link LoanRepository},
 * {@link GoalRepository}) rather than a real database or a mocking framework — matching the
 * pattern already established by {@link TransactionApplicationServiceTest}. Every test in this
 * class builds a snapshot using only these fakes, which by construction proves
 * {@link SnapshotBuilder} depends solely on the repository interfaces (it is never given, and
 * could not compile against, a JDBC type).
 *
 * <p>One required scenario is deliberately not exercised here: {@code SnapshotBuilder}'s
 * unsupported-currency guard (see {@code SnapshotBuilder.toInr}). {@link Currency} is currently a
 * closed enum with exactly two constants ({@code INR}, {@code USD}), so there is no way to
 * construct an {@link Account} whose currency reaches that branch without fabricating an invalid
 * enum value via reflection — which this project's testing approach deliberately avoids. The
 * guard exists as documented, currently-unreachable, defensive code; see its Javadoc.
 */
class SnapshotBuilderTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 4, 1);

    // ===================== in-memory fakes =====================

    private static final class InMemoryFinancialProfileRepository implements FinancialProfileRepository {
        private FinancialProfile profile;
        private long nextId = 1;

        @Override
        public Optional<FinancialProfile> find() {
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
        private final InMemoryFinancialProfileRepository financialProfileRepository = new InMemoryFinancialProfileRepository();
        private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        private final InMemoryInvestmentRepository investmentRepository = new InMemoryInvestmentRepository();
        private final InMemoryLoanRepository loanRepository = new InMemoryLoanRepository();
        private final InMemoryGoalRepository goalRepository = new InMemoryGoalRepository();

        private final SnapshotBuilder builder = new SnapshotBuilder(
                financialProfileRepository, accountRepository, transactionRepository,
                investmentRepository, loanRepository, goalRepository);

        private void profile(String monthlyIncome, String monthlyExpenses) {
            financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal(monthlyIncome), new BigDecimal(monthlyExpenses)));
        }

        private Account account(Currency currency, String openingBalance) {
            return accountRepository.save(
                    new Account(0L, currency + " account", AccountType.BANK, currency, new BigDecimal(openingBalance)));
        }

        private void expense(Account account, String amount) {
            expense(account, amount, AS_OF);
        }

        private void expense(Account account, String amount, LocalDate date) {
            transactionRepository.save(new Transaction(
                    0L, account.getId(), new BigDecimal(amount), TransactionType.EXPENSE, null, date, null, null));
        }
    }

    private static Investment investment(long accountId) {
        return new Investment(0L, accountId, "ETF", null, AssetType.ETF, Currency.INR,
                BigDecimal.TEN, new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("0.10"), BigDecimal.ZERO);
    }

    private static Loan loan() {
        return new Loan(0L, "Home Loan", new BigDecimal("100000"), new BigDecimal("0.08"), 12, AS_OF,
                new BigDecimal("100000"), 12);
    }

    private static Goal goal() {
        return new Goal(0L, "Fund", new BigDecimal("50000"), new BigDecimal("1000"), AS_OF.plusYears(1), BigDecimal.ZERO);
    }

    // ===================== FinancialProfile =====================

    @Test
    void correctFinancialProfileValuesAreTransferred() {
        Fixture fixture = new Fixture();
        fixture.profile("5000.00", "3200.00");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, new BigDecimal("5000.00").compareTo(snapshot.getMonthlyIncome()));
        assertEquals(0, new BigDecimal("3200.00").compareTo(snapshot.getMonthlyExpenses()));
    }

    @Test
    void missingFinancialProfileFailsClearly() {
        Fixture fixture = new Fixture();
        // No profile saved.

        assertThrows(ApplicationValidationException.class, () -> fixture.builder.build(AS_OF, BigDecimal.ONE));
    }

    // ===================== cash consolidation =====================

    @Test
    void inrCashAggregationSumsInrAccountsDirectly() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        fixture.account(Currency.INR, "1000.00");
        fixture.account(Currency.INR, "500.00");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, new BigDecimal("1500.00").compareTo(snapshot.getCash()));
    }

    @Test
    void usdCashIsConvertedUsingTheSuppliedRate() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        fixture.account(Currency.USD, "100.00");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, new BigDecimal("83"));

        assertEquals(0, new BigDecimal("8300.00").compareTo(snapshot.getCash()));
    }

    @Test
    void suppliedFxRateIsActuallyUsedNotIgnored() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        fixture.account(Currency.USD, "100.00");

        FinancialSnapshot withLowRate = fixture.builder.build(AS_OF, new BigDecimal("80"));
        FinancialSnapshot withHighRate = fixture.builder.build(AS_OF, new BigDecimal("90"));

        assertEquals(0, new BigDecimal("8000.00").compareTo(withLowRate.getCash()));
        assertEquals(0, new BigDecimal("9000.00").compareTo(withHighRate.getCash()));
    }

    @Test
    void mixedInrAndUsdAccountsAreConsolidatedTogether() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        fixture.account(Currency.INR, "1000.00");
        fixture.account(Currency.USD, "100.00");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, new BigDecimal("83"));

        // 1000.00 (INR) + 100.00 * 83 (USD->INR) = 1000.00 + 8300.00 = 9300.00
        assertEquals(0, new BigDecimal("9300.00").compareTo(snapshot.getCash()));
    }

    @Test
    void negativeCashIsPreservedNotClampedToZero() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "0.00");
        fixture.expense(account, "500.00");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, new BigDecimal("-500.00").compareTo(snapshot.getCash()));
    }

    @Test
    void noAccountsProduceZeroCash() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, BigDecimal.ZERO.compareTo(snapshot.getCash()));
    }

    // ===================== point-in-time transaction filtering =====================

    @Test
    void transactionDatedBeforeAsOfDateIsIncluded() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "0.00");
        fixture.expense(account, "100.00", AS_OF.minusDays(1));

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, new BigDecimal("-100.00").compareTo(snapshot.getCash()));
    }

    @Test
    void transactionDatedExactlyOnAsOfDateIsIncluded() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "0.00");
        fixture.expense(account, "100.00", AS_OF);

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(0, new BigDecimal("-100.00").compareTo(snapshot.getCash()));
    }

    @Test
    void transactionDatedAfterAsOfDateIsExcluded() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "1000.00");
        fixture.expense(account, "100.00", AS_OF.plusDays(1));

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        // The future-dated expense must not affect a balance computed as of AS_OF.
        assertEquals(0, new BigDecimal("1000.00").compareTo(snapshot.getCash()));
    }

    @Test
    void mixedBeforeOnAndAfterTransactionsProduceTheCorrectBalance() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "1000.00");
        fixture.expense(account, "100.00", AS_OF.minusDays(1)); // included
        fixture.expense(account, "50.00", AS_OF);               // included
        fixture.expense(account, "9999.00", AS_OF.plusDays(1)); // excluded

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        // 1000 - 100 - 50 = 850.00; the future-dated 9999 expense must not apply.
        assertEquals(0, new BigDecimal("850.00").compareTo(snapshot.getCash()));
    }

    @Test
    void dateFilteringAppliesIndependentlyToInrAndUsdAccountsBeforeConversion() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account inrAccount = fixture.account(Currency.INR, "1000.00");
        fixture.expense(inrAccount, "500.00", AS_OF.plusDays(1)); // excluded
        Account usdAccount = fixture.account(Currency.USD, "100.00");
        fixture.expense(usdAccount, "40.00", AS_OF.plusDays(1)); // excluded

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, new BigDecimal("83"));

        // Both future-dated expenses are excluded, so each account's balance is unchanged by them:
        // 1000.00 (INR) + 100.00 * 83 (USD->INR) = 9300.00. If filtering were applied after
        // conversion, or not at all, this would come out to 500.00 + 60.00*83 = 5480.00 instead.
        assertEquals(0, new BigDecimal("9300.00").compareTo(snapshot.getCash()));
    }

    // ===================== usdToInrRate validation =====================

    @Test
    void zeroFxRateIsRejected() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(IllegalArgumentException.class, () -> fixture.builder.build(AS_OF, BigDecimal.ZERO));
    }

    @Test
    void negativeFxRateIsRejected() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(IllegalArgumentException.class, () -> fixture.builder.build(AS_OF, new BigDecimal("-1")));
    }

    // ===================== asOfDate =====================

    @Test
    void explicitAsOfDateIsPreservedExactly() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        LocalDate explicitDate = LocalDate.of(2030, 11, 17);

        FinancialSnapshot snapshot = fixture.builder.build(explicitDate, BigDecimal.ONE);

        assertEquals(explicitDate, snapshot.getAsOfDate());
    }

    // ===================== investments / loans / goals passthrough =====================

    @Test
    void investmentsLoansAndGoalsAreCarriedIntoTheSnapshot() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");
        Account account = fixture.account(Currency.INR, "0.00");
        Investment savedInvestment = fixture.investmentRepository.save(investment(account.getId()));
        Loan savedLoan = fixture.loanRepository.save(loan());
        Goal savedGoal = fixture.goalRepository.save(goal());

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertEquals(1, snapshot.getInvestments().size());
        assertEquals(savedInvestment.getId(), snapshot.getInvestments().get(0).getId());
        assertEquals(1, snapshot.getLoans().size());
        assertEquals(savedLoan.getId(), snapshot.getLoans().get(0).getId());
        assertEquals(1, snapshot.getGoals().size());
        assertEquals(savedGoal.getId(), snapshot.getGoals().get(0).getId());
    }

    @Test
    void noInvestmentsLoansOrGoalsProduceEmptyLists() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        FinancialSnapshot snapshot = fixture.builder.build(AS_OF, BigDecimal.ONE);

        assertTrue(snapshot.getInvestments().isEmpty());
        assertTrue(snapshot.getLoans().isEmpty());
        assertTrue(snapshot.getGoals().isEmpty());
    }

    // ===================== null arguments =====================

    @Test
    void rejectsNullAsOfDate() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(NullPointerException.class, () -> fixture.builder.build(null, BigDecimal.ONE));
    }

    @Test
    void rejectsNullUsdToInrRate() {
        Fixture fixture = new Fixture();
        fixture.profile("0", "0");

        assertThrows(NullPointerException.class, () -> fixture.builder.build(AS_OF, null));
    }
}
