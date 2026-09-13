package com.finsight.simulation.calculator;

import com.finsight.common.FinancialPrecision;
import com.finsight.domain.loan.Loan;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Computes the monthly EMI and full amortization schedule for a {@link Loan}, from its
 * <b>current</b> outstanding state, using the standard reducing-balance formula:
 *
 * <pre>
 *     EMI = B * r * (1+r)^n / ((1+r)^n - 1)
 * </pre>
 *
 * <p>where {@code B} is {@link Loan#getCurrentBalance()} (the outstanding principal as of the
 * snapshot, <em>not</em> the original {@link Loan#getPrincipal()}), {@code r} is the monthly
 * interest rate ({@code annualInterestRate / 12}), and {@code n} is
 * {@link Loan#getRemainingTenureMonths()} (the months left to pay it off, <em>not</em> the
 * original {@link Loan#getTenureMonths()}). Computing from the current state rather than the
 * original terms is what lets a simulation start a loan from wherever it actually stands as of a
 * snapshot, rather than always restarting its full original amortization. For a zero-interest
 * loan the formula is undefined (0/0), so that case is handled explicitly as
 * {@code EMI = currentBalance / remainingTenureMonths}. For an already-fully-paid loan
 * ({@link Loan#getRemainingTenureMonths()} {@code == 0}, which {@link Loan}'s own validation
 * guarantees only occurs together with {@link Loan#getCurrentBalance()} {@code == 0}), the EMI is
 * {@code 0} and the schedule is empty — there is nothing left to amortize.
 *
 * <p>This class is stateless: it holds no loan data of its own, only pure functions of a
 * {@link Loan}. It depends on nothing outside the JDK and {@code com.finsight.domain.loan} — no
 * JavaFX, no JDBC, no persistence — so it can be unit-tested in isolation. It does not modify or
 * re-validate {@link Loan}; a {@code Loan} instance is already guaranteed valid by its own
 * constructor.
 *
 * <p><b>Rounding:</b> uses the project's shared {@link FinancialPrecision} policy. The monthly
 * rate and {@code (1+r)^n} are computed at high precision
 * ({@link FinancialPrecision#CALCULATION_CONTEXT}, since these divisions frequently do not
 * terminate) so that rounding error does not compound over long tenures. Every value this class
 * returns to a caller — the EMI, and each period's interest/principal/remaining balance in the
 * schedule — is rounded via {@link FinancialPrecision#roundMoney(BigDecimal)} before being handed
 * back.
 */
public final class LoanCalculator {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private LoanCalculator() {
        // stateless utility class; not instantiable
    }

    /**
     * Computes the fixed monthly payment that fully amortizes the loan's current outstanding
     * balance ({@link Loan#getCurrentBalance()}) over its remaining tenure
     * ({@link Loan#getRemainingTenureMonths()}), rounded to 2 decimal places. Returns {@code 0}
     * for an already-fully-paid loan ({@code remainingTenureMonths == 0}).
     */
    public static BigDecimal calculateMonthlyPayment(Loan loan) {
        Objects.requireNonNull(loan, "loan must not be null");

        int remainingMonths = loan.getRemainingTenureMonths();
        if (remainingMonths == 0) {
            return FinancialPrecision.roundMoney(BigDecimal.ZERO);
        }

        BigDecimal balance = loan.getCurrentBalance();
        BigDecimal monthlyRate = monthlyRate(loan);

        if (monthlyRate.signum() == 0) {
            return FinancialPrecision.roundMoney(balance.divide(BigDecimal.valueOf(remainingMonths), FinancialPrecision.CALCULATION_CONTEXT));
        }

        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(remainingMonths, FinancialPrecision.CALCULATION_CONTEXT);

        BigDecimal numerator = balance.multiply(monthlyRate).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);

        BigDecimal emi = numerator.divide(denominator, FinancialPrecision.CALCULATION_CONTEXT);
        return FinancialPrecision.roundMoney(emi);
    }

    /**
     * Builds the complete, month-by-month amortization schedule for paying off the loan's
     * current outstanding balance ({@link Loan#getCurrentBalance()}) over its remaining tenure
     * ({@link Loan#getRemainingTenureMonths()}). The schedule always contains exactly
     * {@link Loan#getRemainingTenureMonths()} payments (empty for an already-fully-paid loan),
     * and the final payment's remaining balance is exactly {@code 0.00}.
     */
    public static AmortizationSchedule calculateAmortizationSchedule(Loan loan) {
        Objects.requireNonNull(loan, "loan must not be null");

        BigDecimal monthlyPayment = calculateMonthlyPayment(loan);
        int remainingMonths = loan.getRemainingTenureMonths();
        if (remainingMonths == 0) {
            return new AmortizationSchedule(monthlyPayment, List.of());
        }

        BigDecimal monthlyRate = monthlyRate(loan);

        List<LoanPayment> payments = new ArrayList<>(remainingMonths);
        BigDecimal beginningBalance = FinancialPrecision.roundMoney(loan.getCurrentBalance());

        for (int paymentNumber = 1; paymentNumber <= remainingMonths; paymentNumber++) {
            BigDecimal interest = FinancialPrecision.roundMoney(beginningBalance.multiply(monthlyRate));

            boolean isLastPayment = paymentNumber == remainingMonths;
            BigDecimal principal;
            BigDecimal remainingBalance;

            if (!isLastPayment) {
                principal = monthlyPayment.subtract(interest);
                remainingBalance = beginningBalance.subtract(principal);
            } else {
                // Final payment: pay off exactly what remains, rather than trusting the fixed
                // EMI to land on zero after accumulated rounding.
                principal = beginningBalance;
                remainingBalance = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            }

            if (!isLastPayment && remainingBalance.signum() <= 0) {
                // Rounding drift closed the loan out early. Treat this period as the payoff
                // instead, so the balance never goes negative and principal still sums exactly
                // to the original balance.
                principal = beginningBalance;
                remainingBalance = FinancialPrecision.roundMoney(BigDecimal.ZERO);
            }

            BigDecimal payment = isLastPayment || remainingBalance.signum() == 0
                    ? principal.add(interest)
                    : monthlyPayment;

            payments.add(new LoanPayment(paymentNumber, payment, interest, principal, remainingBalance));
            beginningBalance = remainingBalance;
        }

        return new AmortizationSchedule(monthlyPayment, payments);
    }

    private static BigDecimal monthlyRate(Loan loan) {
        BigDecimal annualRate = loan.getAnnualInterestRate();
        if (annualRate.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return annualRate.divide(MONTHS_PER_YEAR, FinancialPrecision.CALCULATION_CONTEXT);
    }
}
