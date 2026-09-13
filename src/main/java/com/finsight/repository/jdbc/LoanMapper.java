package com.finsight.repository.jdbc;

import com.finsight.domain.loan.Loan;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Converts one {@code loans} row into a {@link Loan}. Kept as a small, explicit, hand-written
 * mapper rather than a generic reflection-based one, matching {@code AccountMapper},
 * {@code CategoryMapper}, {@code TransactionMapper}, and {@code InvestmentMapper}:
 *
 * <ul>
 *   <li>{@code id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code name} (SQL {@code TEXT}) → {@code String}</li>
 *   <li>{@code principal}, {@code annual_interest_rate}, {@code current_balance} (SQL
 *       {@code TEXT}) → {@link BigDecimal} via its string constructor, never
 *       {@link ResultSet#getBigDecimal}, since those columns store exact decimal text rather than
 *       a JDBC-native numeric type</li>
 *   <li>{@code tenure_months}, {@code remaining_tenure_months} (SQL {@code INTEGER}) →
 *       {@code int}</li>
 *   <li>{@code start_date} (SQL {@code TEXT}, ISO {@code YYYY-MM-DD}) → {@link LocalDate} via
 *       {@link LocalDate#parse(CharSequence)}</li>
 * </ul>
 *
 * <p>{@code current_balance} and {@code remaining_tenure_months} are read back exactly as stored,
 * independently of {@code principal} and {@code tenure_months} — this mapper never derives one
 * pair of fields from the other, matching {@link Loan}'s own original-terms-versus-current-state
 * distinction.
 *
 * <p>Package-private: only {@link LoanRepositoryJdbc} needs this mapping.
 */
final class LoanMapper {

    private LoanMapper() {
        // stateless utility class; not instantiable
    }

    static Loan fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        String name = row.getString("name");
        BigDecimal principal = new BigDecimal(row.getString("principal"));
        BigDecimal annualInterestRate = new BigDecimal(row.getString("annual_interest_rate"));
        int tenureMonths = row.getInt("tenure_months");
        LocalDate startDate = LocalDate.parse(row.getString("start_date"));
        BigDecimal currentBalance = new BigDecimal(row.getString("current_balance"));
        int remainingTenureMonths = row.getInt("remaining_tenure_months");

        return new Loan(
                id, name, principal, annualInterestRate, tenureMonths, startDate,
                currentBalance, remainingTenureMonths);
    }
}
