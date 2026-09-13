package com.finsight.repository.jdbc;

import com.finsight.domain.goal.Goal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Converts one {@code goals} row into a {@link Goal}. Kept as a small, explicit, hand-written
 * mapper rather than a generic reflection-based one, matching {@code AccountMapper},
 * {@code CategoryMapper}, {@code TransactionMapper}, {@code InvestmentMapper}, and
 * {@code LoanMapper}:
 *
 * <ul>
 *   <li>{@code id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code name} (SQL {@code TEXT}) → {@code String}</li>
 *   <li>{@code target_amount}, {@code current_amount}, {@code monthly_contribution} (SQL
 *       {@code TEXT}) → {@link BigDecimal} via its string constructor, never
 *       {@link ResultSet#getBigDecimal}, since those columns store exact decimal text rather than
 *       a JDBC-native numeric type</li>
 *   <li>{@code target_date} (SQL {@code TEXT}, ISO {@code YYYY-MM-DD}) → {@link LocalDate} via
 *       {@link LocalDate#parse(CharSequence)}</li>
 * </ul>
 *
 * <p>Every field here is the goal's actual stored state; nothing calculated by
 * {@code GoalCalculator} (remaining amount, months remaining, required monthly contribution) is
 * read or written by this mapper.
 *
 * <p>Package-private: only {@link GoalRepositoryJdbc} needs this mapping.
 */
final class GoalMapper {

    private GoalMapper() {
        // stateless utility class; not instantiable
    }

    static Goal fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        String name = row.getString("name");
        BigDecimal targetAmount = new BigDecimal(row.getString("target_amount"));
        BigDecimal currentAmount = new BigDecimal(row.getString("current_amount"));
        LocalDate targetDate = LocalDate.parse(row.getString("target_date"));
        BigDecimal monthlyContribution = new BigDecimal(row.getString("monthly_contribution"));

        return new Goal(id, name, targetAmount, currentAmount, targetDate, monthlyContribution);
    }
}
