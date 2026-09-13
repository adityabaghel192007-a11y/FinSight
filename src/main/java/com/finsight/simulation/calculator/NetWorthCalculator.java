package com.finsight.simulation.calculator;

import com.finsight.common.FinancialPrecision;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Computes net worth for the MVP: {@code cash + investments - loans}.
 *
 * <p>This class is stateless and depends on nothing beyond the JDK — no domain classes, no
 * JavaFX, no JDBC, no persistence — so it can be unit-tested in isolation. Unlike the other
 * calculators in this package it does not read a domain object; the three components are
 * supplied directly, since at this stage nothing computes "total cash across accounts" or
 * "total loan balance" yet (that aggregation belongs to a later, service-level milestone).
 *
 * <p><b>Rounding:</b> addition and subtraction of {@link BigDecimal} values lose no precision,
 * so there is nothing to round until the result is produced; the final net worth is rounded via
 * {@link FinancialPrecision#roundMoney(BigDecimal)}, consistent with every other calculator in
 * this package.
 */
public final class NetWorthCalculator {

    private NetWorthCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * {@code cash + investments - loans}, rounded to 2 decimal places. May be negative when
     * loans exceed cash plus investments.
     */
    public static BigDecimal calculateNetWorth(BigDecimal cash, BigDecimal investments, BigDecimal loans) {
        Objects.requireNonNull(cash, "cash must not be null");
        Objects.requireNonNull(investments, "investments must not be null");
        Objects.requireNonNull(loans, "loans must not be null");

        BigDecimal netWorth = cash.add(investments).subtract(loans);
        return FinancialPrecision.roundMoney(netWorth);
    }
}
