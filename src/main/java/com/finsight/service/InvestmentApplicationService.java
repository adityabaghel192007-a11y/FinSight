package com.finsight.service;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.InvestmentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Application-layer orchestration for the two investment workflows this milestone introduces:
 * reading every persisted {@link Investment} ({@link #getInvestments()}), and adding a new one
 * ({@link #addInvestment}) — deliberately no {@code update} or {@code delete}, matching
 * {@link InvestmentRepository}'s own by-design omission (see its class-level documentation).
 *
 * <p>Kept as a single class rather than split across a read-only class (like
 * {@link FinancialDataApplicationService}) and a write-only class (like
 * {@link TransactionApplicationService}): that split exists for {@code Transaction}/
 * {@code Account}/{@code Category} because {@link TransactionApplicationService} already has a
 * documented, narrow, three-workflow responsibility that unrelated read methods would blur.
 * {@link Investment} has exactly one write workflow (add) and one read need (list), so one small
 * class covers both without introducing a second class for no benefit.
 *
 * <p><b>Validation performed here for {@link #addInvestment}</b> (a failure throws
 * {@link ApplicationValidationException}, with a message meant to be shown to the user as-is):
 * the given {@code accountId} must refer to a persisted {@link Account} — the one thing
 * {@link Investment}'s own constructor cannot check, since it has no repository access. This
 * mirrors exactly how {@link TransactionApplicationService#recordIncome} validates its
 * {@code accountId} before ever constructing a {@link com.finsight.domain.transaction.Transaction}.
 *
 * <p>Everything else — {@code name} non-blank, {@code quantity} strictly positive, every other
 * monetary/rate field non-negative — is exactly what {@link Investment}'s own constructor already
 * validates, so it is deliberately not re-checked here; constructing an {@link Investment} simply
 * lets that validation run once, in one place, and propagates its {@link IllegalArgumentException}
 * or {@link NullPointerException} unchanged. This class does not verify that {@code accountId}
 * refers to an {@link com.finsight.domain.account.AccountType#INVESTMENT}-typed account — nothing
 * elsewhere in the domain model requires that relationship (an {@link Investment} is validated
 * only against account <i>existence</i>, at every layer, including the database's own foreign-key
 * constraint), and introducing that additional restriction here would be inventing a business rule
 * the repository/domain layers do not themselves enforce.
 *
 * <p>This class is otherwise stateless — it holds no data of its own beyond what is supplied at
 * construction — and reads but never modifies the {@link Account} it looks up.
 */
public final class InvestmentApplicationService {

    private final InvestmentRepository investmentRepository;
    private final AccountRepository accountRepository;

    public InvestmentApplicationService(InvestmentRepository investmentRepository, AccountRepository accountRepository) {
        this.investmentRepository = Objects.requireNonNull(investmentRepository, "investmentRepository must not be null");
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
    }

    /** Every currently persisted {@link Investment}, exactly as {@link InvestmentRepository#findAll()} returns it. */
    public List<Investment> getInvestments() {
        return investmentRepository.findAll();
    }

    /**
     * Validates that {@code accountId} refers to a persisted {@link Account}, then persists a new
     * {@link Investment} against it and returns the persisted instance (carrying the id
     * {@link InvestmentRepository#save} generated for it).
     *
     * @throws ApplicationValidationException if {@code accountId} does not refer to a persisted
     *     {@link Account}
     * @throws NullPointerException if {@code name}, {@code assetType}, {@code currency},
     *     {@code quantity}, {@code averagePurchasePrice}, {@code currentPrice},
     *     {@code expectedAnnualReturn}, or {@code monthlyContribution} is {@code null} — propagated
     *     unchanged from {@link Investment}'s own constructor
     * @throws IllegalArgumentException if {@code name} is blank, {@code quantity} is not strictly
     *     positive, or any other monetary/rate field is negative — propagated unchanged from
     *     {@link Investment}'s own constructor
     * @throws com.finsight.repository.RepositoryException if persisting the investment fails
     */
    public Investment addInvestment(
            long accountId,
            String name,
            String ticker,
            AssetType assetType,
            Currency currency,
            BigDecimal quantity,
            BigDecimal averagePurchasePrice,
            BigDecimal currentPrice,
            BigDecimal expectedAnnualReturn,
            BigDecimal monthlyContribution) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ApplicationValidationException("Account not found: " + accountId));

        // account is only used to establish that accountId is valid above; the investment itself
        // is keyed by accountId, matching how every other repository/domain class in this codebase
        // stores foreign references as plain ids rather than embedded objects (see
        // TransactionApplicationService.record() for the identical pattern).
        Investment investment = new Investment(
                0L, account.getId(), name, ticker, assetType, currency, quantity,
                averagePurchasePrice, currentPrice, expectedAnnualReturn, monthlyContribution);
        return investmentRepository.save(investment);
    }
}
