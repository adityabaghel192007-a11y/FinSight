package com.finsight.repository.jdbc;

import com.finsight.domain.financialprofile.FinancialProfile;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one {@code financial_profile} row into a {@link FinancialProfile}. Kept as a small,
 * explicit, hand-written mapper rather than a generic reflection-based one, matching
 * {@link AccountMapper} and every sibling mapper in this package:
 *
 * <ul>
 *   <li>{@code id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code monthly_income} (SQL {@code TEXT}) → {@link BigDecimal} via its string
 *       constructor, never {@link ResultSet#getBigDecimal}, since that column stores exact
 *       decimal text rather than a JDBC-native numeric type</li>
 *   <li>{@code monthly_expenses} (SQL {@code TEXT}) → {@link BigDecimal}, same reasoning</li>
 * </ul>
 *
 * <p>Package-private: only {@link FinancialProfileRepositoryJdbc} needs this mapping.
 */
final class FinancialProfileMapper {

    private FinancialProfileMapper() {
        // stateless utility class; not instantiable
    }

    static FinancialProfile fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        BigDecimal monthlyIncome = new BigDecimal(row.getString("monthly_income"));
        BigDecimal monthlyExpenses = new BigDecimal(row.getString("monthly_expenses"));

        return new FinancialProfile(id, monthlyIncome, monthlyExpenses);
    }
}
