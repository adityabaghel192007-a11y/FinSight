package com.finsight.ui;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link InvestmentFormatter} — no JavaFX toolkit, no database, no
 * {@code CompositionRoot}. Every input is a hand-built {@link Investment}/{@link Account}, matching
 * {@link FinancialDataFormatterTest}'s exact style. The underlying invested/current/gain-loss
 * calculations themselves are covered elsewhere (see {@code InvestmentCalculatorTest}), not here —
 * these tests verify only that {@link InvestmentFormatter} reads and formats those already-computed
 * figures correctly, in the investment's own currency, with no conversion.
 */
class InvestmentFormatterTest {

    private static final Account ACCOUNT = new Account(
            5L, "Brokerage", AccountType.INVESTMENT, Currency.INR, new BigDecimal("0.00"));

    private static Investment inrInvestment(String ticker) {
        return new Investment(
                1L, 5L, "Nifty Index Fund", ticker, AssetType.ETF, Currency.INR,
                new BigDecimal("100"), new BigDecimal("200.00"), new BigDecimal("250.00"),
                new BigDecimal("0.10"), new BigDecimal("5000.00"));
    }

    private static Investment usdInvestment() {
        return new Investment(
                2L, 5L, "US Total Market ETF", "VTI", AssetType.US_STOCK, Currency.USD,
                new BigDecimal("5"), new BigDecimal("200.00"), new BigDecimal("180.00"),
                new BigDecimal("0.08"), new BigDecimal("100.00"));
    }

    // ===================== complete investment formatting =====================

    @Test
    void formatsACompleteInvestmentRow() {
        InvestmentRowView row = InvestmentFormatter.toInvestmentRow(inrInvestment("NIFTYBEES"), "Brokerage");

        assertEquals("Nifty Index Fund", row.getName());
        assertEquals("NIFTYBEES", row.getTicker());
        assertEquals("ETF", row.getAssetType());
        assertEquals("INR", row.getCurrency());
        assertEquals("100", row.getQuantity());
        assertEquals("₹200.00", row.getAveragePurchasePrice());
        assertEquals("₹250.00", row.getCurrentPrice());
        assertEquals("Brokerage", row.getAccountName());
    }

    // ===================== missing ticker fallback =====================

    @Test
    void fallsBackToAPlaceholderWhenTheTickerIsAbsent() {
        InvestmentRowView row = InvestmentFormatter.toInvestmentRow(inrInvestment(null), "Brokerage");

        assertEquals("—", row.getTicker());
    }

    // ===================== INR formatting =====================

    @Test
    void formatsInrValuesWithARupeePrefixAndNoConversion() {
        InvestmentRowView row = InvestmentFormatter.toInvestmentRow(inrInvestment("NIFTYBEES"), "Brokerage");

        // invested = 100 * 200.00 = 20000.00 ; current = 100 * 250.00 = 25000.00 ; gain = 5000.00
        assertEquals("₹20,000.00", row.getInvestedValue());
        assertEquals("₹25,000.00", row.getCurrentValue());
        assertEquals("+₹5,000.00", row.getGainLoss());
    }

    // ===================== USD formatting =====================

    @Test
    void formatsUsdValuesWithADollarPrefixAndNoConversionToInr() {
        InvestmentRowView row = InvestmentFormatter.toInvestmentRow(usdInvestment(), "Brokerage");

        assertEquals("USD", row.getCurrency());
        assertEquals("$200.00", row.getAveragePurchasePrice());
        assertEquals("$180.00", row.getCurrentPrice());
        // invested = 5 * 200.00 = 1000.00 ; current = 5 * 180.00 = 900.00 ; loss = -100.00
        assertEquals("$1,000.00", row.getInvestedValue());
        assertEquals("$900.00", row.getCurrentValue());
        assertEquals("-$100.00", row.getGainLoss());
    }

    // ===================== invested/current/gain-loss values (via InvestmentCalculator) =====================

    @Test
    void gainLossIsZeroWhenCurrentPriceEqualsAveragePurchasePrice() {
        Investment breakEven = new Investment(
                3L, 5L, "Flat Fund", null, AssetType.MUTUAL_FUND, Currency.INR,
                new BigDecimal("10"), new BigDecimal("100.00"), new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);

        InvestmentRowView row = InvestmentFormatter.toInvestmentRow(breakEven, "Brokerage");

        assertEquals("₹0.00", row.getGainLoss());
    }

    // ===================== account-name resolution =====================

    @Test
    void toInvestmentRowsResolvesAccountNameFromTheProvidedAccountList() {
        List<InvestmentRowView> rows = InvestmentFormatter.toInvestmentRows(
                List.of(inrInvestment("NIFTYBEES")), List.of(ACCOUNT));

        assertEquals(1, rows.size());
        assertEquals("Brokerage", rows.get(0).getAccountName());
    }

    @Test
    void toInvestmentRowsFallsBackToUnknownAccountWhenNoMatchExists() {
        List<InvestmentRowView> rows = InvestmentFormatter.toInvestmentRows(
                List.of(inrInvestment("NIFTYBEES")), List.of());

        assertEquals("Unknown account", rows.get(0).getAccountName());
    }

    @Test
    void toInvestmentRowsReturnsEmptyListForNoInvestments() {
        List<InvestmentRowView> rows = InvestmentFormatter.toInvestmentRows(List.of(), List.of(ACCOUNT));

        assertEquals(0, rows.size());
    }

    // ===================== option label for the Decision Lab dropdown =====================

    @Test
    void formatsAUserFriendlyOptionLabelWithNoRawId() {
        String label = InvestmentFormatter.formatInvestmentOptionLabel(inrInvestment("NIFTYBEES"));

        assertEquals("Nifty Index Fund (ETF)", label);
    }

    // ===================== null rejection =====================

    @Test
    void rejectsANullInvestment() {
        assertThrows(NullPointerException.class, () -> InvestmentFormatter.toInvestmentRow(null, "Brokerage"));
        assertThrows(NullPointerException.class, () -> InvestmentFormatter.formatInvestmentOptionLabel(null));
    }
}
