package com.finsight.simulation.calculator;

import com.finsight.common.FinancialPrecision;
import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.Investment;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes valuation, currency conversion, and forward projection for an {@link Investment}.
 *
 * <p>This class is stateless: it holds no investment data of its own, only pure functions of an
 * {@link Investment} (and, where needed, an explicitly supplied exchange rate). It depends on
 * nothing outside the JDK and {@code com.finsight.domain} — no JavaFX, no JDBC, no persistence,
 * no live market data — so it can be unit-tested in isolation. It does not modify or re-validate
 * {@link Investment}; an {@code Investment} instance is already guaranteed valid by its own
 * constructor.
 *
 * <p><b>Rounding:</b> follows the same philosophy as {@link LoanCalculator}, using the project's
 * shared {@link FinancialPrecision} policy. Rates (the monthly return derived from
 * {@link Investment#getExpectedAnnualReturn()}) are computed and kept at high precision
 * ({@link FinancialPrecision#CALCULATION_CONTEXT}) since they generally do not terminate; only
 * monetary amounts — invested/current value, gain/loss, converted INR value, and each month's
 * projected value — are rounded via {@link FinancialPrecision#roundMoney(BigDecimal)}, and only
 * once each becomes an externally-visible value.
 */
public final class InvestmentCalculator {

    private static final int MONTHS_PER_YEAR = 12;
    private static final int NTH_ROOT_MAX_ITERATIONS = 100;
    private static final BigDecimal NTH_ROOT_TOLERANCE = new BigDecimal("1E-30");

    private InvestmentCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * {@code quantity * averagePurchasePrice}, in the investment's own currency.
     */
    public static BigDecimal calculateInvestedValue(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        return FinancialPrecision.roundMoney(investment.getQuantity().multiply(investment.getAveragePurchasePrice()));
    }

    /**
     * {@code quantity * currentPrice}, in the investment's own currency.
     */
    public static BigDecimal calculateCurrentValue(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        return FinancialPrecision.roundMoney(investment.getQuantity().multiply(investment.getCurrentPrice()));
    }

    /**
     * {@code currentValue - investedValue}, in the investment's own currency. May be negative
     * (a loss) or zero.
     */
    public static BigDecimal calculateGainLoss(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        return calculateCurrentValue(investment).subtract(calculateInvestedValue(investment));
    }

    /**
     * Converts a monetary amount already expressed in the investment's own currency to INR.
     *
     * <p>For an INR-denominated investment, {@code amountInOwnCurrency} already is the INR
     * value, so it is simply rounded and returned; {@code usdToInrRate} is not required and is
     * ignored. For a USD-denominated investment, {@code usdToInrRate} must be supplied and be
     * strictly positive; the result is {@code amountInOwnCurrency * usdToInrRate}.
     *
     * <p>This does not read or alter the investment's own-currency figures — recomputing with a
     * different rate changes only the returned INR amount.
     */
    public static BigDecimal convertToInr(Investment investment, BigDecimal amountInOwnCurrency, BigDecimal usdToInrRate) {
        Objects.requireNonNull(investment, "investment must not be null");
        Objects.requireNonNull(amountInOwnCurrency, "amountInOwnCurrency must not be null");

        if (investment.getCurrency() == Currency.INR) {
            return FinancialPrecision.roundMoney(amountInOwnCurrency);
        }

        Objects.requireNonNull(usdToInrRate, "usdToInrRate must not be null for a USD-denominated investment");
        if (usdToInrRate.signum() <= 0) {
            throw new IllegalArgumentException("usdToInrRate must be strictly > 0");
        }

        return FinancialPrecision.roundMoney(amountInOwnCurrency.multiply(usdToInrRate));
    }

    /**
     * The monthly rate implied by the investment's effective annual return:
     * {@code (1 + annualRate)^(1/12) - 1}. This is a rate, not a monetary value, so it is
     * returned at full calculation precision rather than rounded to 2 decimal places. Zero
     * annual return yields exactly zero.
     *
     * <p>Delegates to {@link #calculateMonthlyRate(BigDecimal)} — see that overload for callers
     * that have an annual rate not (yet) attached to any particular {@link Investment}.
     */
    public static BigDecimal calculateMonthlyRate(Investment investment) {
        Objects.requireNonNull(investment, "investment must not be null");
        return calculateMonthlyRate(investment.getExpectedAnnualReturn());
    }

    /**
     * The monthly rate implied by an arbitrary effective annual return, using the same
     * {@code (1 + annualRate)^(1/12) - 1} conversion as {@link #calculateMonthlyRate(Investment)}.
     * Exposed directly (rather than only via an {@link Investment}) so callers with an annual rate
     * that isn't attached to any particular investment — such as a simulation applying a scenario
     * event that proposes a revised expected return — can reuse this exact conversion instead of
     * duplicating it. {@code annualRate} must be non-negative, matching
     * {@link Investment#getExpectedAnnualReturn()}'s own validation. Zero annual return yields
     * exactly zero.
     */
    public static BigDecimal calculateMonthlyRate(BigDecimal annualRate) {
        Objects.requireNonNull(annualRate, "annualRate must not be null");
        if (annualRate.signum() < 0) {
            throw new IllegalArgumentException("annualRate must be >= 0");
        }
        if (annualRate.signum() == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal onePlusAnnualRate = BigDecimal.ONE.add(annualRate);
        BigDecimal monthlyGrowthFactor = nthRoot(onePlusAnnualRate, MONTHS_PER_YEAR, FinancialPrecision.CALCULATION_CONTEXT);
        return monthlyGrowthFactor.subtract(BigDecimal.ONE);
    }

    /**
     * Advances an investment's own-currency value by exactly one simulated month: grows
     * {@code beginningValue} by {@code monthlyRate} and then adds {@code monthlyContribution} —
     * the contribution itself earns no return in the month it is added. Returns the new value
     * rounded to money scale via {@link FinancialPrecision#roundMoney(BigDecimal)}.
     *
     * <p>This is the single shared implementation of that one-month operation. {@link #projectValue}
     * calls it with a fixed rate and contribution for the whole horizon; {@code SimulationEngine}
     * calls it once per simulated month with whatever rate/contribution is currently in effect
     * (which a scenario event may have revised since the previous month). Neither caller
     * duplicates the arithmetic itself.
     *
     * <p>{@code monthlyContribution} is expected to already be at money scale — every existing
     * caller rounds it before calling — so only the final sum (growth + contribution) is rounded
     * here; this matches the exact rounding sequence both callers used before this method existed.
     * Does not read or modify any {@link Investment}; this is a pure function of the three
     * {@link BigDecimal} arguments.
     */
    public static BigDecimal compoundOneMonth(BigDecimal beginningValue, BigDecimal monthlyRate, BigDecimal monthlyContribution) {
        Objects.requireNonNull(beginningValue, "beginningValue must not be null");
        Objects.requireNonNull(monthlyRate, "monthlyRate must not be null");
        Objects.requireNonNull(monthlyContribution, "monthlyContribution must not be null");

        BigDecimal growthFactor = BigDecimal.ONE.add(monthlyRate);
        BigDecimal grown = beginningValue.multiply(growthFactor);
        return FinancialPrecision.roundMoney(grown.add(monthlyContribution));
    }

    /**
     * Projects the investment's value forward {@code months} months, starting from its current
     * value ({@link #calculateCurrentValue(Investment)}). Each month advances via
     * {@link #compoundOneMonth(BigDecimal, BigDecimal, BigDecimal)} using a fixed monthly rate
     * ({@link #calculateMonthlyRate(Investment)}) and fixed contribution for the whole horizon.
     * Values are in the investment's own currency. This method does not support a rate or
     * contribution that changes partway through the horizon — see {@code SimulationEngine} for
     * that.
     */
    public static InvestmentProjection projectValue(Investment investment, int months) {
        Objects.requireNonNull(investment, "investment must not be null");
        if (months <= 0) {
            throw new IllegalArgumentException("months must be > 0");
        }

        BigDecimal monthlyRate = calculateMonthlyRate(investment);
        BigDecimal contribution = FinancialPrecision.roundMoney(investment.getMonthlyContribution());
        BigDecimal currentValue = calculateCurrentValue(investment);

        List<BigDecimal> monthlyValues = new ArrayList<>(months);
        BigDecimal previousValue = currentValue;
        for (int i = 0; i < months; i++) {
            BigDecimal newValue = compoundOneMonth(previousValue, monthlyRate, contribution);
            monthlyValues.add(newValue);
            previousValue = newValue;
        }

        return new InvestmentProjection(currentValue, monthlyValues);
    }

    /**
     * The positive {@code n}-th root of {@code value} (which must be {@code > 0}), computed via
     * Newton's method purely with {@link BigDecimal} arithmetic (no {@code double}). Starting
     * from a guess of {@code 1} converges in a handful of iterations for the values this class
     * ever calls it with ({@code 1 + annualRate}, with {@code annualRate >= 0}, so always
     * {@code >= 1} and typically close to it).
     */
    private static BigDecimal nthRoot(BigDecimal value, int n, MathContext mc) {
        BigDecimal nBig = BigDecimal.valueOf(n);
        BigDecimal nMinusOne = BigDecimal.valueOf(n - 1L);
        BigDecimal x = BigDecimal.ONE;

        for (int i = 0; i < NTH_ROOT_MAX_ITERATIONS; i++) {
            BigDecimal xPowNMinus1 = x.pow(n - 1, mc);
            BigDecimal next = nMinusOne.multiply(x).add(value.divide(xPowNMinus1, mc)).divide(nBig, mc);
            if (next.subtract(x).abs().compareTo(NTH_ROOT_TOLERANCE) < 0) {
                return next;
            }
            x = next;
        }
        return x;
    }
}
