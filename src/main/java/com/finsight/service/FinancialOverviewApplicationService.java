package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.financialprofile.FinancialProfile;
import com.finsight.domain.investment.Investment;
import com.finsight.domain.loan.Loan;
import com.finsight.domain.transaction.Transaction;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.FinancialProfileRepository;
import com.finsight.repository.InvestmentRepository;
import com.finsight.repository.LoanRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.simulation.calculator.AccountBalanceCalculator;
import com.finsight.simulation.calculator.InvestmentCalculator;
import com.finsight.simulation.calculator.NetWorthCalculator;
import com.finsight.common.FinancialPrecision;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer read access to a concise "Financial Overview" (Milestone 7A): the user's
 * baseline monthly income/expenses/savings, plus their consolidated current position — cash,
 * investments, loans, and net worth — for a UI that needs all seven figures together without
 * touching a repository or duplicating a single financial calculation itself.
 *
 * <p><b>This is an orchestration/assembly component, not a financial calculator</b> — exactly the
 * same role {@link SnapshotBuilder} already plays for {@code SimulationEngine}. It reads
 * already-persisted state and delegates every actual calculation to the already-existing,
 * already-tested calculator that owns it:
 * <ul>
 *   <li>{@code monthlyIncome}/{@code monthlyExpenses} are read directly from the single persisted
 *       {@link FinancialProfile} — never derived from transaction history (see that class's own
 *       documentation on why), and {@code monthlySavings} is computed once,
 *       inside {@link FinancialOverviewData}'s own constructor, from those two figures;</li>
 *   <li>cash is consolidated exactly the way {@link SnapshotBuilder#build} already consolidates
 *       it for a simulation: each account's balance is computed by
 *       {@link AccountBalanceCalculator#calculateBalance(Account, List)}, in that account's own
 *       currency, then INR passes through unchanged and USD is multiplied by the caller-supplied
 *       {@code usdToInrRate} — this class does not re-derive {@code AccountBalanceCalculator}'s
 *       {@code openingBalance + income - expense} formula, only sums and converts its output;
 *       unlike {@code SnapshotBuilder}, there is no {@code asOfDate} cutoff here — every
 *       persisted transaction is included, since this is a "right now" figure, not a simulation
 *       starting point;</li>
 *   <li>investments are consolidated by calling
 *       {@link InvestmentCalculator#calculateCurrentValue(Investment)} and
 *       {@link InvestmentCalculator#convertToInr(Investment, BigDecimal, BigDecimal)} for each
 *       persisted {@link Investment} and summing the INR results — this class performs no
 *       valuation or currency-conversion arithmetic of its own;</li>
 *   <li>loans are summed directly from {@link Loan#getCurrentBalance()} — already a stored,
 *       currently-outstanding figure, not something {@code LoanCalculator} (which computes EMI
 *       and amortization schedules, a different concern) is asked to derive;</li>
 *   <li>net worth is obtained by calling
 *       {@link NetWorthCalculator#calculateNetWorth(BigDecimal, BigDecimal, BigDecimal)} directly
 *       — this class never reimplements {@code cash + investments - loans} itself.</li>
 * </ul>
 *
 * <p><b>FX rate (locked architectural rule, matching {@code SimulationApplicationService}):</b>
 * {@code usdToInrRate} is supplied by the caller to {@link #getOverview(BigDecimal)} on every
 * call — never looked up, never fetched from a live service, never derived from system time or
 * database state. This class introduces no new FX source; it is exactly the same explicit,
 * caller-supplied approach {@link SnapshotBuilder#build} already established.
 *
 * <p><b>Missing {@link FinancialProfile}:</b> if none has been recorded yet, {@link #getOverview}
 * fails clearly via {@link ApplicationValidationException} — the same exception type and the same
 * "fail rather than assume zero income/expenses" behavior {@link SnapshotBuilder#build} already
 * uses for the identical condition, not a newly invented behavior.
 *
 * <p><b>Unresolved {@code TRANSFER} balance:</b> {@link AccountBalanceCalculator} refuses (via
 * {@link UnsupportedOperationException}) to compute a balance for an account with any
 * {@code TRANSFER} transaction, since transfer direction is not yet determinable from the current
 * domain model (see that class's own documentation). {@link FinancialDataApplicationService}
 * handles this per-account by reporting that one account's balance as absent while still showing
 * every other account's balance (a partial, per-row display can tolerate one missing entry). A
 * single consolidated cash total cannot tolerate that same gap — a "current position" figure that
 * silently omits or zeroes out one unresolvable account's balance would misrepresent the user's
 * actual cash position, not merely omit a row — so this class does not catch that exception and
 * substitute a placeholder. Instead it is caught and re-thrown as {@link ApplicationValidationException}
 * (this class's own reported-failure type, matching {@link SnapshotBuilder}'s and every other
 * application service's convention of surfacing a clear, unchecked, application-level failure
 * rather than letting a calculator-level {@link UnsupportedOperationException} propagate as-is),
 * with the original failure's message included in full so nothing is silently swallowed.
 *
 * <p>This class depends only on repository interfaces and the calculators it orchestrates — no
 * JDBC type, no other application service, and no JavaFX type.
 */
public final class FinancialOverviewApplicationService {

    private final FinancialProfileRepository financialProfileRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final InvestmentRepository investmentRepository;
    private final LoanRepository loanRepository;

    public FinancialOverviewApplicationService(
            FinancialProfileRepository financialProfileRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            InvestmentRepository investmentRepository,
            LoanRepository loanRepository) {
        this.financialProfileRepository =
                Objects.requireNonNull(financialProfileRepository, "financialProfileRepository must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.transactionRepository =
                Objects.requireNonNull(transactionRepository, "transactionRepository must not be null");
        this.investmentRepository =
                Objects.requireNonNull(investmentRepository, "investmentRepository must not be null");
        this.loanRepository = Objects.requireNonNull(loanRepository, "loanRepository must not be null");
    }

    /**
     * Builds a {@link FinancialOverviewData} from whatever is currently persisted: the single
     * {@link FinancialProfile}'s monthly income/expenses, every account's balance consolidated
     * into INR, every investment's current value consolidated into INR, and every loan's current
     * balance summed — combined into a net worth via {@link NetWorthCalculator}.
     *
     * @param usdToInrRate the exchange rate applied to every USD-denominated account balance and
     *     investment value; supplied by the caller, never looked up; must be strictly positive
     * @throws NullPointerException if {@code usdToInrRate} is {@code null}
     * @throws IllegalArgumentException if {@code usdToInrRate} is zero or negative
     * @throws ApplicationValidationException if no {@link FinancialProfile} has been recorded
     *     yet, or if a consolidated cash figure cannot be safely computed because an account has
     *     an unresolved {@code TRANSFER} transaction (see this class's own documentation)
     */
    public FinancialOverviewData getOverview(BigDecimal usdToInrRate) {
        Objects.requireNonNull(usdToInrRate, "usdToInrRate must not be null");
        if (usdToInrRate.signum() <= 0) {
            throw new IllegalArgumentException("usdToInrRate must be strictly > 0");
        }

        FinancialProfile profile = financialProfileRepository.find()
                .orElseThrow(() -> new ApplicationValidationException(
                        "No FinancialProfile has been recorded yet; a baseline monthlyIncome and "
                                + "monthlyExpenses must be set before a Financial Overview can be built."));

        BigDecimal cash = consolidateCashInInr(usdToInrRate);
        BigDecimal investments = consolidateInvestmentsInInr(usdToInrRate);
        BigDecimal loans = sumLoanBalances();
        BigDecimal netWorth = NetWorthCalculator.calculateNetWorth(cash, investments, loans);

        return new FinancialOverviewData(
                profile.getMonthlyIncome(), profile.getMonthlyExpenses(), cash, investments, loans, netWorth);
    }

    /**
     * Sums every persisted account's balance, converted to INR: each balance is computed by
     * {@link AccountBalanceCalculator#calculateBalance(Account, List)} in that account's own
     * currency, then an INR balance passes through unchanged and a USD balance is multiplied by
     * {@code usdToInrRate}. See this class's own top-level documentation for why an
     * {@link UnsupportedOperationException} from the calculator (an unresolved {@code TRANSFER}
     * balance) is re-thrown here rather than worked around.
     */
    private BigDecimal consolidateCashInInr(BigDecimal usdToInrRate) {
        BigDecimal totalInInr = BigDecimal.ZERO;
        for (Account account : accountRepository.findAll()) {
            List<Transaction> transactions = transactionRepository.findByAccountId(account.getId());
            BigDecimal nativeBalance;
            try {
                nativeBalance = AccountBalanceCalculator.calculateBalance(account, transactions);
            } catch (UnsupportedOperationException e) {
                throw new ApplicationValidationException(
                        "Cannot compute a consolidated Financial Overview: account \"" + account.getName()
                                + "\" (id " + account.getId() + ") has a TRANSFER transaction whose balance "
                                + "cannot yet be determined. " + e.getMessage());
            }
            totalInInr = totalInInr.add(toInr(nativeBalance, account.getCurrency(), usdToInrRate));
        }
        return FinancialPrecision.roundMoney(totalInInr);
    }

    /** An INR balance is returned unchanged; a USD balance is multiplied by {@code usdToInrRate}. */
    private static BigDecimal toInr(BigDecimal nativeBalance, Currency currency, BigDecimal usdToInrRate) {
        return switch (currency) {
            case INR -> nativeBalance;
            case USD -> nativeBalance.multiply(usdToInrRate);
        };
    }

    /**
     * Sums every persisted investment's current value, converted to INR via
     * {@link InvestmentCalculator#calculateCurrentValue(Investment)} and
     * {@link InvestmentCalculator#convertToInr(Investment, BigDecimal, BigDecimal)} — neither of
     * which is re-derived here.
     */
    private BigDecimal consolidateInvestmentsInInr(BigDecimal usdToInrRate) {
        BigDecimal totalInInr = BigDecimal.ZERO;
        for (Investment investment : investmentRepository.findAll()) {
            BigDecimal currentValue = InvestmentCalculator.calculateCurrentValue(investment);
            totalInInr = totalInInr.add(InvestmentCalculator.convertToInr(investment, currentValue, usdToInrRate));
        }
        return FinancialPrecision.roundMoney(totalInInr);
    }

    /**
     * Sums every persisted loan's {@link Loan#getCurrentBalance()} directly — already a stored,
     * currently-outstanding figure; {@code LoanCalculator} is not invoked, since this is not an
     * EMI or amortization computation.
     */
    private BigDecimal sumLoanBalances() {
        BigDecimal totalLoanBalance = BigDecimal.ZERO;
        for (Loan loan : loanRepository.findAll()) {
            totalLoanBalance = totalLoanBalance.add(loan.getCurrentBalance());
        }
        return FinancialPrecision.roundMoney(totalLoanBalance);
    }
}
