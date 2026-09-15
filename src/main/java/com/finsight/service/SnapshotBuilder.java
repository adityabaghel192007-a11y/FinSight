package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.goal.Goal;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.domain.transaction.Transaction;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.GoalRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.simulation.calculator.AccountBalanceCalculator;
import com.finsight.simulation.model.FinancialSnapshot;
import com.finsight.common.FinancialPrecision;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Assembles a {@link FinancialSnapshot} from FinSight's persisted data: accounts and their
 * transactions, the user's {@link FinancialProfile}, investments, loans, and goals.
 *
 * <p><b>This is an orchestration/assembly component, not a financial calculator.</b> It reads
 * already-persisted state and already-existing calculators, and combines their results into a
 * {@link FinancialSnapshot}; it does not itself compute anything {@code AccountBalanceCalculator}
 * doesn't already compute. In particular, {@code SnapshotBuilder} deliberately does <b>not</b>:
 * <ul>
 *   <li>calculate investment growth ({@code InvestmentCalculator}'s job),</li>
 *   <li>calculate EMI ({@code LoanCalculator}'s job),</li>
 *   <li>project goals ({@code GoalCalculator}'s job),</li>
 *   <li>apply scenario events or income/expense growth, or calculate projections at all
 *       ({@code SimulationEngine}'s job),</li>
 *   <li>fetch live prices or FX rates — {@code usdToInrRate} is supplied by the caller, never
 *       looked up, and</li>
 *   <li>access JDBC, SQL, or any {@code SqliteConnectionProvider}/{@code Connection} directly — it
 *       depends only on the repository interfaces in {@link com.finsight.repository} and on
 *       domain/calculation classes, exactly like {@link TransactionApplicationService}.</li>
 * </ul>
 *
 * <p><b>{@code asOfDate} is always caller-supplied.</b> This class never calls
 * {@link LocalDate#now()} (or reads system time, randomness, or any external service in any other
 * way) — the snapshot's point-in-time is whatever the caller says it is.
 *
 * <p><b>{@code monthlyIncome}/{@code monthlyExpenses} come only from {@link FinancialProfile}.</b>
 * They are never derived from {@link Transaction} history — see
 * {@link FinancialProfileRepository} and {@link FinancialProfile}'s own documentation for why: a
 * transaction is a dated individual record with no recurring-transaction model, so "monthly" is
 * not a fact that can be computed from it without inventing an unrequested recurrence semantic.
 * If no {@link FinancialProfile} has been recorded yet, {@link #build} fails clearly via
 * {@link ApplicationValidationException} rather than silently assuming zero income/expenses.
 *
 * <p><b>{@link FinancialSnapshot#getCash()} is a consolidated INR value</b>, computed as follows
 * for each persisted {@link Account}:
 * <ol>
 *   <li>every transaction {@link TransactionRepository#findByAccountId} returns for it is
 *       filtered down to only those dated on or before {@code asOfDate} ({@code date <= asOfDate}
 *       is included, {@code date > asOfDate} is excluded) — {@code SnapshotBuilder} owns this
 *       point-in-time filtering itself, since {@code asOfDate} is its own parameter, not
 *       {@link AccountBalanceCalculator}'s;</li>
 *   <li>its balance is then calculated in its own native currency via
 *       {@link AccountBalanceCalculator#calculateBalance(Account, List)}, using exactly that
 *       filtered list;</li>
 *   <li>an {@link Currency#INR INR} balance is added to the running total directly;</li>
 *   <li>a {@link Currency#USD USD} balance is multiplied by the caller-supplied
 *       {@code usdToInrRate} before being added;</li>
 *   <li>any other currency fails fast via {@link ApplicationValidationException} rather than
 *       being silently treated as INR — see {@link #toInr} for why this branch is currently
 *       unreachable in practice.</li>
 * </ol>
 * The running total is rounded to money precision via {@link FinancialPrecision#roundMoney} once,
 * at the end — matching every calculator in this project. <b>A negative result is preserved, not
 * clamped to zero</b>: an account whose recorded expenses exceed its opening balance can
 * legitimately drive the aggregate negative, and that is a real state worth surfacing, not an
 * error to hide.
 *
 * <p>Inherited limitation: {@link AccountBalanceCalculator#calculateBalance(Account, List)} itself
 * refuses (via {@link UnsupportedOperationException}) to compute a balance for an account with any
 * {@code TRANSFER} transaction, since transfer direction is not yet determinable from the domain
 * model (see that class's own documentation). {@code SnapshotBuilder} does not work around this —
 * it is an existing, deliberate limitation of the calculator this class orchestrates, not
 * something introduced here.
 *
 * <p>{@link Account}, {@link Transaction}, {@link Investment}, {@link Loan}, {@link Goal}, and
 * {@link FinancialProfile} instances are only ever read here, never mutated — this class calls no
 * {@code withId} or persistence-write method on any of them.
 *
 * <p>Investments, loans, and goals are carried into the snapshot exactly as persisted —
 * {@link InvestmentRepository#findAll()}, {@link LoanRepository#findAll()}, and
 * {@link GoalRepository#findAll()} results are passed straight through, with no currency
 * conversion or recalculation of any kind.
 */
public final class SnapshotBuilder {

    private final FinancialProfileRepository financialProfileRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InvestmentRepository investmentRepository;
    private final LoanRepository loanRepository;
    private final GoalRepository goalRepository;

    public SnapshotBuilder(
            FinancialProfileRepository financialProfileRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InvestmentRepository investmentRepository,
            LoanRepository loanRepository,
            GoalRepository goalRepository) {
        this.financialProfileRepository =
                Objects.requireNonNull(financialProfileRepository, "financialProfileRepository must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.transactionRepository =
                Objects.requireNonNull(transactionRepository, "transactionRepository must not be null");
        this.investmentRepository =
                Objects.requireNonNull(investmentRepository, "investmentRepository must not be null");
        this.loanRepository = Objects.requireNonNull(loanRepository, "loanRepository must not be null");
        this.goalRepository = Objects.requireNonNull(goalRepository, "goalRepository must not be null");
    }

    /**
     * Builds a {@link FinancialSnapshot} as of {@code asOfDate}, consolidating every persisted
     * account's balance into INR using {@code usdToInrRate} (see the class-level documentation for
     * the exact algorithm), reading {@code monthlyIncome}/{@code monthlyExpenses} from the single
     * persisted {@link FinancialProfile}, and carrying every persisted investment, loan, and goal
     * through unchanged.
     *
     * @param asOfDate the snapshot's point-in-time, exactly as supplied — never computed from
     *     system time here; also the cutoff for which transactions are included when
     *     consolidating cash (a transaction dated after {@code asOfDate} is excluded)
     * @param usdToInrRate the exchange rate applied to every USD-denominated account balance;
     *     supplied by the caller, never looked up; must be strictly positive
     * @throws NullPointerException if {@code asOfDate} or {@code usdToInrRate} is {@code null}
     * @throws IllegalArgumentException if {@code usdToInrRate} is zero or negative
     * @throws ApplicationValidationException if no {@link FinancialProfile} has been recorded yet,
     *     or if any account's currency is neither {@code INR} nor {@code USD}
     * @throws UnsupportedOperationException if any account has a {@code TRANSFER} transaction —
     *     see the class-level documentation
     */
    public FinancialSnapshot build(LocalDate asOfDate, BigDecimal usdToInrRate) {
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        Objects.requireNonNull(usdToInrRate, "usdToInrRate must not be null");
        if (usdToInrRate.signum() <= 0) {
            throw new IllegalArgumentException("usdToInrRate must be strictly > 0");
        }

        FinancialProfile profile = financialProfileRepository.find()
                .orElseThrow(() -> new ApplicationValidationException(
                        "No FinancialProfile has been recorded yet; a baseline monthlyIncome and "
                                + "monthlyExpenses must be set before a FinancialSnapshot can be built."));

        BigDecimal cash = consolidateCashInInr(asOfDate, usdToInrRate);

        List<Investment> investments = investmentRepository.findAll();
        List<Loan> loans = loanRepository.findAll();
        List<Goal> goals = goalRepository.findAll();

        return new FinancialSnapshot(
                asOfDate, cash, profile.getMonthlyIncome(), profile.getMonthlyExpenses(),
                investments, loans, goals);
    }

    /**
     * Sums every persisted account's balance, converted to INR (see the class-level
     * documentation), rounding the total once at the end via
     * {@link FinancialPrecision#roundMoney}. Each account's own balance is computed by
     * {@link AccountBalanceCalculator}, never re-derived here — this method's only added
     * responsibility beyond fetching and converting is filtering each account's transactions down
     * to {@code date <= asOfDate} first, via {@link #upToAsOfDate}, since {@code asOfDate} is
     * {@code SnapshotBuilder}'s own parameter and {@link AccountBalanceCalculator} has no
     * knowledge of it.
     */
    private BigDecimal consolidateCashInInr(LocalDate asOfDate, BigDecimal usdToInrRate) {
        BigDecimal totalInInr = BigDecimal.ZERO;
        for (Account account : accountRepository.findAll()) {
            List<Transaction> transactions = transactionRepository.findByAccountId(account.getId());
            List<Transaction> transactionsUpToAsOfDate = upToAsOfDate(transactions, asOfDate);
            BigDecimal nativeBalance = AccountBalanceCalculator.calculateBalance(account, transactionsUpToAsOfDate);
            totalInInr = totalInInr.add(toInr(nativeBalance, account.getCurrency(), usdToInrRate));
        }
        return FinancialPrecision.roundMoney(totalInInr);
    }

    /**
     * Returns only the transactions dated on or before {@code asOfDate} ({@code date <= asOfDate}
     * is included, {@code date > asOfDate} is excluded), preserving their original relative order.
     * A transaction dated after {@code asOfDate} has not happened yet as of this snapshot's
     * point-in-time, so it must not affect a balance computed as of that date.
     */
    private static List<Transaction> upToAsOfDate(List<Transaction> transactions, LocalDate asOfDate) {
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (!transaction.getDate().isAfter(asOfDate)) {
                result.add(transaction);
            }
        }
        return result;
    }

    /**
     * Converts a single account's native-currency balance to INR: an INR balance is returned
     * unchanged, a USD balance is multiplied by {@code usdToInrRate}.
     *
     * <p>The {@code default} branch below is a deliberate fail-fast guard, required so that a
     * currency this method doesn't know how to consolidate is never silently summed in as if it
     * were INR. As of this milestone, {@link Currency} is a closed enum with exactly two
     * constants ({@code INR} and {@code USD}), so this branch is <b>currently unreachable</b> —
     * there is no way to construct an {@link Account} whose currency reaches it. It exists purely
     * to protect against a future third {@link Currency} constant being added without a
     * corresponding update here; it is intentionally not exercised by a unit test, since no real
     * value can trigger it today.
     */
    private static BigDecimal toInr(BigDecimal nativeBalance, Currency currency, BigDecimal usdToInrRate) {
        switch (currency) {
            case INR:
                return nativeBalance;
            case USD:
                return nativeBalance.multiply(usdToInrRate);
            default:
                throw new ApplicationValidationException(
                        "Unsupported currency for cash consolidation: " + currency
                                + ". SnapshotBuilder only knows how to consolidate INR and USD balances into INR.");
        }
    }
}
