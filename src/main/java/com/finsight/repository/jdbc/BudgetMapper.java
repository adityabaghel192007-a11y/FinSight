package com.finsight.repository.jdbc;

import com.finsight.domain.budget.Budget;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;

/**
 * Converts one {@code budgets} row into a {@link Budget}. Kept as a small, explicit, hand-written
 * mapper rather than a generic reflection-based one, matching {@code AccountMapper},
 * {@code CategoryMapper}, {@code TransactionMapper}, {@code InvestmentMapper}, {@code LoanMapper},
 * and {@code GoalMapper}:
 *
 * <ul>
 *   <li>{@code id}, {@code category_id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code month} (SQL {@code TEXT}, {@code YYYY-MM}) → {@link YearMonth} via
 *       {@link YearMonth#parse(CharSequence)}, which accepts exactly the
 *       {@code YearMonth.toString()} representation used when writing — no separate
 *       {@code LocalDate} conversion is involved</li>
 *   <li>{@code amount} (SQL {@code TEXT}) → {@link BigDecimal} via its string constructor, never
 *       {@link ResultSet#getBigDecimal}, since that column stores exact decimal text rather than a
 *       JDBC-native numeric type</li>
 * </ul>
 *
 * <p>Package-private: only {@link BudgetRepositoryJdbc} needs this mapping.
 */
final class BudgetMapper {

    private BudgetMapper() {
        // stateless utility class; not instantiable
    }

    static Budget fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        long categoryId = row.getLong("category_id");
        YearMonth month = YearMonth.parse(row.getString("month"));
        BigDecimal amount = new BigDecimal(row.getString("amount"));

        return new Budget(id, categoryId, month, amount);
    }
}
