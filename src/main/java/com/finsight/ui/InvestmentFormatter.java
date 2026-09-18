package com.finsight.ui;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.Investment;
import com.finsight.simulation.calculator.InvestmentCalculator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Turns persisted {@link Investment} data — read via
 * {@code com.finsight.service.InvestmentApplicationService}, exactly as persisted — into display
 * strings/{@link InvestmentRowView}s a JavaFX control can show directly, exactly the same kind of
 * presentation-only job {@link FinancialDataFormatter} already does for accounts/transactions.
 *
 * <p>This class performs no financial calculation of its own beyond invoking the existing,
 * already-tested {@link InvestmentCalculator} for invested value, current value, and gain/loss —
 * the same "read an already-computed or trivially-derivable figure, then format it" boundary every
 * other formatter in this package holds to. In particular, this class never converts a USD-
 * denominated investment's figures to INR: {@link InvestmentCalculator#convertToInr} requires an
 * explicit exchange rate that only exists inside a simulation run
 * ({@code SimulationParameters.getUsdToInrRate()} — see {@code SimulationApplicationService}'s own
 * "locked architectural rule" documentation), and the Investments section is a per-item list, not a
 * portfolio total, so there is nothing here that legitimately needs converting. Every monetary
 * figure is instead formatted in the investment's own currency, with a currency-appropriate prefix
 * (₹ for {@link Currency#INR}, $ for {@link Currency#USD}) — no new exchange rate is introduced,
 * and no cross-currency total is computed.
 *
 * <p>Its only other jobs are formatting a {@link BigDecimal} quantity as plain decimal text, and
 * resolving an {@link Investment}'s {@code accountId} to the matching {@link Account#getName()}
 * from an already-fetched list — a lookup, not a calculation, exactly matching
 * {@link FinancialDataFormatter#toTransactionRows}'s existing account-name resolution.
 */
public final class InvestmentFormatter {

    private InvestmentFormatter() {
        // stateless utility class; not instantiable
    }

    /**
     * Converts every element of {@code investments} into an {@link InvestmentRowView}, resolving
     * each investment's {@code accountId} against {@code accounts} once via a lookup map built up
     * front, rather than scanning the list per investment. An {@code accountId} with no match in
     * {@code accounts} displays as {@code "Unknown account"} — not expected in practice, since the
     * foreign key is enforced at the database level, but this method never throws for display
     * purposes, matching {@link FinancialDataFormatter#toTransactionRows}'s identical fallback.
     */
    public static List<InvestmentRowView> toInvestmentRows(List<Investment> investments, List<Account> accounts) {
        Objects.requireNonNull(investments, "investments must not be null");
        Objects.requireNonNull(accounts, "accounts must not be null");

        Map<Long, String> accountNamesById = new HashMap<>();
        for (Account account : accounts) {
            accountNamesById.put(account.getId(), account.getName());
        }

        List<InvestmentRowView> rows = new ArrayList<>(investments.size());
        for (Investment investment : investments) {
            String accountName = accountNamesById.getOrDefault(investment.getAccountId(), "Unknown account");
            rows.add(toInvestmentRow(investment, accountName));
        }
        return rows;
    }

    /**
     * Converts a single {@link Investment} into an {@link InvestmentRowView}, given the already-
     * resolved name of the account it belongs to. Exposed separately from
     * {@link #toInvestmentRows} so a caller that already knows the owning account (e.g. immediately
     * after adding a new investment against a specific, already-selected {@link Account}) is not
     * forced to re-fetch and re-resolve the entire account list just to format one row.
     */
    public static InvestmentRowView toInvestmentRow(Investment investment, String accountName) {
        Objects.requireNonNull(investment, "investment must not be null");
        Objects.requireNonNull(accountName, "accountName must not be null");

        BigDecimal investedValue = InvestmentCalculator.calculateInvestedValue(investment);
        BigDecimal currentValue = InvestmentCalculator.calculateCurrentValue(investment);
        BigDecimal gainLoss = InvestmentCalculator.calculateGainLoss(investment);

        return new InvestmentRowView(
                investment.getName(),
                investment.getTicker().orElse("—"),
                investment.getAssetType().toString(),
                investment.getCurrency().toString(),
                formatQuantity(investment.getQuantity()),
                formatMoney(investment.getAveragePurchasePrice(), investment.getCurrency()),
                formatMoney(investment.getCurrentPrice(), investment.getCurrency()),
                formatMoney(investedValue, investment.getCurrency()),
                formatMoney(currentValue, investment.getCurrency()),
                formatSignedMoney(gainLoss, investment.getCurrency()),
                accountName);
    }

    /**
     * A short, user-friendly label for {@code investment} suitable for a {@code ComboBox<Investment>}
     * option — e.g. {@code "Nifty Index Fund (ETF)"} — never the investment's database id. Used by
     * the Decision Lab's investment-targeting scenario controls to let a user pick an investment by
     * name rather than by typing a raw id.
     */
    public static String formatInvestmentOptionLabel(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        return investment.getName() + " (" + investment.getAssetType() + ")";
    }

    private static String formatQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private static String formatMoney(BigDecimal amount, Currency currency) {
        String prefix = currency == Currency.USD ? "$" : "₹";
        return prefix + String.format("%,.2f", amount);
    }

    private static String formatSignedMoney(BigDecimal amount, Currency currency) {
        String prefix = currency == Currency.USD ? "$" : "₹";
        String sign = amount.signum() > 0 ? "+" : (amount.signum() < 0 ? "-" : "");
        return sign + prefix + String.format("%,.2f", amount.abs());
    }
}
