package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FinancialOverviewApplicationService} (Milestone 7A), using small
 * in-memory fakes of every repository it depends on — exactly the same style
 * {@code InvestmentApplicationServiceTest}/{@code FinancialDataApplicationServiceTest} already
 * establish for this project. Real-database wiring is separately proven in
 * {@code CompositionRootTest}.
 *
 * <p>These tests assert the <em>aggregation</em> this service performs (summing, converting,
 * combining via {@code NetWorthCalculator}) — never re-proving arithmetic already exhaustively
 * covered by {@code AccountBalanceCalculatorTest}, {@code InvestmentCalculatorTest}, or
 * {@code NetWorthCalculatorTest}.
 */
class FinancialOverviewApplicationServiceTest {

    // ===================== in-memory fakes =====================

    private static final class InMemoryFinancialProfileRepository implements FinancialProfileRepository {
        private FinancialProfile profile;

        @Override
        public Optional<FinancialProfile> find() {
            return Optional.ofNullable(profile);
        }

        @Override
        public FinancialProfile save(FinancialProfile profile) {
            this.profile = profile.withId(1L);
            return this.profile;
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
            return transactions.values().stream().filter(t -> t.getAccountId() == accountId).toList();
        }

        @Override
        public void assignTransferGroupId(long transactionId, long transferGroupId) {
            throw new UnsupportedOperationException("not needed by these tests");
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

    // ===================== fixture =====================

    private static final class Fixture {
        private final InMemoryFinancialProfileRepository financialProfileRepository = new InMemoryFinancialProfileRepository();
        private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
        private final InMemoryTransactionRepository transactionRepository = new InMemoryTransactionRepository();
        private final InMemoryInvestmentRepository investmentRepository = new InMemoryInvestmentRepository();
        private final InMemoryLoanRepository loanRepository = new InMemoryLoanRepository();
        private final FinancialOverviewApplicationService service = new FinancialOverviewApplicationService(
                financialProfileRepository, accountRepository, transactionRepository,
                investmentRepository, loanRepository);

        private void saveProfile(String income, String expenses) {
            financialProfileRepository.save(
                    new FinancialProfile(0L, new BigDecimal(income), new BigDecimal(expenses)));
        }

        private Account saveAccount(Currency currency, String openingBalance) {
            return accountRepository.save(
                    new Account(0L, "Test Account", AccountType.BANK, currency, new BigDecimal(openingBalance)));
        }

        private Investment saveInvestment(long accountId, Currency currency, String quantity, String currentPrice) {
            return investmentRepository.save(new Investment(
                    0L, accountId, "Test Investment", null, AssetType.OTHER, currency,
                    new BigDecimal(quantity), new BigDecimal(currentPrice), new BigDecimal(currentPrice),
                    new BigDecimal("0.10"), BigDecimal.ZERO));
        }

        private Loan saveLoan(String currentBalance) {
            return loanRepository.save(new Loan(
                    0L, "Test Loan", new BigDecimal("100000.00"), new BigDecimal("0.10"), 12,
                    LocalDate.of(2026, 1, 1), new BigDecimal(currentBalance), 6));
        }
    }

    // ===================== monthly metrics =====================

    @Test
    void computesMonthlyIncomeExpensesAndSavingsFromTheFinancialProfile() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("60000.00").compareTo(overview.getMonthlyIncome()));
        assertEquals(0, new BigDecimal("40000.00").compareTo(overview.getMonthlyExpenses()));
        assertEquals(0, new BigDecimal("20000.00").compareTo(overview.getMonthlySavings()));
    }

    @Test
    void monthlySavingsIsNegativeWhenExpensesExceedIncome() {
        Fixture fx = new Fixture();
        fx.saveProfile("30000.00", "45000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("-15000.00").compareTo(overview.getMonthlySavings()));
    }

    // ===================== cash aggregation =====================

    @Test
    void aggregatesCashAcrossMultipleInrAccountsWithNoConversion() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        fx.saveAccount(Currency.INR, "100000.00");
        fx.saveAccount(Currency.INR, "50000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("150000.00").compareTo(overview.getCash()));
    }

    @Test
    void convertsAUsdAccountBalanceUsingTheSuppliedRate() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        fx.saveAccount(Currency.INR, "100000.00");
        fx.saveAccount(Currency.USD, "1000.00");

        FinancialOverviewData overview = fx.service.getOverview(new BigDecimal("83"));

        // 100000.00 (INR, unchanged) + 1000.00 * 83 (USD, converted) = 183000.00
        assertEquals(0, new BigDecimal("183000.00").compareTo(overview.getCash()));
    }

    @Test
    void cashReflectsAccountTransactionsNotJustOpeningBalances() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        Account account = fx.saveAccount(Currency.INR, "100000.00");
        fx.transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("15000.00"), TransactionType.INCOME, null,
                LocalDate.of(2026, 4, 1), null, null));
        fx.transactionRepository.save(new Transaction(
                0L, account.getId(), new BigDecimal("5000.00"), TransactionType.EXPENSE, null,
                LocalDate.of(2026, 4, 2), null, null));

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("110000.00").compareTo(overview.getCash()));
    }

    // ===================== investment aggregation =====================

    @Test
    void aggregatesInvestmentCurrentValueAcrossMultipleInrInvestments() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        Account account = fx.saveAccount(Currency.INR, "0.00");
        fx.saveInvestment(account.getId(), Currency.INR, "10", "100.00");
        fx.saveInvestment(account.getId(), Currency.INR, "5", "200.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        // (10 * 100.00) + (5 * 200.00) = 1000.00 + 1000.00 = 2000.00
        assertEquals(0, new BigDecimal("2000.00").compareTo(overview.getInvestments()));
    }

    @Test
    void convertsAUsdInvestmentCurrentValueUsingTheSuppliedRate() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        Account account = fx.saveAccount(Currency.INR, "0.00");
        fx.saveInvestment(account.getId(), Currency.USD, "10", "100.00");

        FinancialOverviewData overview = fx.service.getOverview(new BigDecimal("83"));

        // 10 * 100.00 = 1000.00 (USD) * 83 = 83000.00
        assertEquals(0, new BigDecimal("83000.00").compareTo(overview.getInvestments()));
    }

    @Test
    void investmentsIsZeroWhenNonePersisted() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, BigDecimal.ZERO.compareTo(overview.getInvestments()));
    }

    // ===================== loan aggregation =====================

    @Test
    void loansIsZeroWhenNonePersisted() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, BigDecimal.ZERO.compareTo(overview.getLoans()));
    }

    @Test
    void sumsMultipleLoansCurrentBalances() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        fx.saveLoan("50000.00");
        fx.saveLoan("25000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("75000.00").compareTo(overview.getLoans()));
    }

    // ===================== net worth =====================

    @Test
    void netWorthIsCashPlusInvestmentsMinusLoans() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        fx.saveAccount(Currency.INR, "100000.00");
        Account investmentAccount = fx.saveAccount(Currency.INR, "0.00");
        fx.saveInvestment(investmentAccount.getId(), Currency.INR, "10", "100.00");
        fx.saveLoan("50000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        // cash = 100000.00 + 0.00 = 100000.00; investments = 1000.00; loans = 50000.00
        // netWorth = 100000.00 + 1000.00 - 50000.00 = 51000.00
        assertEquals(0, new BigDecimal("51000.00").compareTo(overview.getNetWorth()));
    }

    @Test
    void netWorthIsNegativeWhenLoansExceedCashPlusInvestments() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        fx.saveAccount(Currency.INR, "10000.00");
        fx.saveLoan("50000.00");

        FinancialOverviewData overview = fx.service.getOverview(BigDecimal.ONE);

        assertEquals(0, new BigDecimal("-40000.00").compareTo(overview.getNetWorth()));
    }

    // ===================== missing FinancialProfile =====================

    @Test
    void throwsApplicationValidationExceptionWhenNoFinancialProfileExists() {
        Fixture fx = new Fixture();

        ApplicationValidationException e = assertThrows(ApplicationValidationException.class,
                () -> fx.service.getOverview(BigDecimal.ONE));
        assertTrue(e.getMessage().contains("FinancialProfile"),
                "the failure message must explain what is missing, matching SnapshotBuilder's own convention");
    }

    // ===================== invalid FX rate =====================

    @Test
    void rejectsAZeroFxRate() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        assertThrows(IllegalArgumentException.class, () -> fx.service.getOverview(BigDecimal.ZERO));
    }

    @Test
    void rejectsANegativeFxRate() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        assertThrows(IllegalArgumentException.class, () -> fx.service.getOverview(new BigDecimal("-1")));
    }

    @Test
    void rejectsANullFxRate() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");

        assertThrows(NullPointerException.class, () -> fx.service.getOverview(null));
    }

    // ===================== unresolved TRANSFER balance =====================

    @Test
    void surfacesAnApplicationValidationExceptionWhenAnAccountHasAnUnresolvedTransferBalance() {
        Fixture fx = new Fixture();
        fx.saveProfile("60000.00", "40000.00");
        Account transferAccount = fx.saveAccount(Currency.INR, "50000.00");
        fx.transactionRepository.save(new Transaction(
                0L, transferAccount.getId(), new BigDecimal("10000.00"), TransactionType.TRANSFER, null,
                LocalDate.of(2026, 4, 1), null, 1L));

        ApplicationValidationException e = assertThrows(ApplicationValidationException.class,
                () -> fx.service.getOverview(BigDecimal.ONE),
                "a consolidated cash figure must not silently substitute a fake value for an "
                        + "account whose balance cannot be resolved");
        assertTrue(e.getMessage().contains("TRANSFER"),
                "the original AccountBalanceCalculator failure must not be silently swallowed");
    }

    // ===================== constructor validation =====================

    @Test
    void constructorRejectsNullRepositories() {
        FinancialProfileRepository financialProfileRepository = new InMemoryFinancialProfileRepository();
        AccountRepository accountRepository = new InMemoryAccountRepository();
        TransactionRepository transactionRepository = new InMemoryTransactionRepository();
        InvestmentRepository investmentRepository = new InMemoryInvestmentRepository();
        LoanRepository loanRepository = new InMemoryLoanRepository();

        assertThrows(NullPointerException.class, () -> new FinancialOverviewApplicationService(
                null, accountRepository, transactionRepository, investmentRepository, loanRepository));
        assertThrows(NullPointerException.class, () -> new FinancialOverviewApplicationService(
                financialProfileRepository, null, transactionRepository, investmentRepository, loanRepository));
        assertThrows(NullPointerException.class, () -> new FinancialOverviewApplicationService(
                financialProfileRepository, accountRepository, null, investmentRepository, loanRepository));
        assertThrows(NullPointerException.class, () -> new FinancialOverviewApplicationService(
                financialProfileRepository, accountRepository, transactionRepository, null, loanRepository));
        assertThrows(NullPointerException.class, () -> new FinancialOverviewApplicationService(
                financialProfileRepository, accountRepository, transactionRepository, investmentRepository, null));
    }
}
