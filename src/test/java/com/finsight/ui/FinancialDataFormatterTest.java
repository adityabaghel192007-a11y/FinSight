package com.finsight.ui;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;
import com.finsight.service.AccountBalance;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pure unit tests for {@link FinancialDataFormatter#formatAccountBalance(AccountBalance)}
 * (Milestone 5K) — no JavaFX toolkit, no database, no {@code CompositionRoot}. Every input is a
 * hand-built {@link AccountBalance}, so these tests verify only this one formatting method: that a
 * present balance is formatted as plain (unsigned) money, and an absent one (the
 * transfer-affected-account case — see {@link AccountBalance}'s own documentation) falls back to
 * the "Balance unavailable" placeholder rather than throwing or fabricating a number. The
 * underlying balance calculation itself is covered elsewhere (see
 * {@code AccountBalanceCalculatorTest} and {@code FinancialDataApplicationServiceTest}), not here.
 */
class FinancialDataFormatterTest {

    private static Account account() {
        return new Account(1L, "Demo Checking Account", AccountType.BANK, Currency.INR, new BigDecimal("150000.00"));
    }

    @Test
    void formatsAPresentCurrentBalanceAsPlainMoney() {
        AccountBalance accountBalance = new AccountBalance(account(), new BigDecimal("165000.00"));

        assertEquals("165,000.00", FinancialDataFormatter.formatAccountBalance(accountBalance));
    }

    @Test
    void fallsBackToAPlaceholderWhenTheCurrentBalanceIsAbsent() {
        AccountBalance accountBalance = new AccountBalance(account(), null);

        assertEquals("Balance unavailable", FinancialDataFormatter.formatAccountBalance(accountBalance));
    }

    @Test
    void rejectsANullAccountBalance() {
        assertThrows(NullPointerException.class, () -> FinancialDataFormatter.formatAccountBalance(null));
    }
}
