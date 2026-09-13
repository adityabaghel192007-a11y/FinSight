package com.finsight.repository.jdbc;

import com.finsight.domain.account.Currency;
import com.finsight.domain.investment.AssetType;
import com.finsight.domain.investment.Investment;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one {@code investments} row into an {@link Investment}. Kept as a small, explicit,
 * hand-written mapper rather than a generic reflection-based one, matching {@code AccountMapper},
 * {@code CategoryMapper}, and {@code TransactionMapper}:
 *
 * <ul>
 *   <li>{@code id}, {@code account_id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code name} (SQL {@code TEXT}) → {@code String}</li>
 *   <li>{@code ticker} (SQL nullable {@code TEXT}) → {@code String}, {@code null} when the column
 *       is SQL {@code NULL} — {@link Investment}'s constructor turns that {@code null} into
 *       {@link java.util.Optional#empty()} on {@link Investment#getTicker()}</li>
 *   <li>{@code asset_type} (SQL {@code TEXT}) → {@link AssetType} via {@link AssetType#valueOf}</li>
 *   <li>{@code currency} (SQL {@code TEXT}) → {@link Currency} via {@link Currency#valueOf}</li>
 *   <li>{@code quantity}, {@code average_purchase_price}, {@code current_price},
 *       {@code expected_annual_return}, {@code monthly_contribution} (SQL {@code TEXT}) →
 *       {@link BigDecimal} via its string constructor, never {@link ResultSet#getBigDecimal}, since
 *       those columns store exact decimal text rather than a JDBC-native numeric type</li>
 * </ul>
 *
 * <p>Package-private: only {@link InvestmentRepositoryJdbc} needs this mapping.
 */
final class InvestmentMapper {

    private InvestmentMapper() {
        // stateless utility class; not instantiable
    }

    static Investment fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        long accountId = row.getLong("account_id");
        String name = row.getString("name");
        String ticker = row.getString("ticker");
        AssetType assetType = AssetType.valueOf(row.getString("asset_type"));
        Currency currency = Currency.valueOf(row.getString("currency"));
        BigDecimal quantity = new BigDecimal(row.getString("quantity"));
        BigDecimal averagePurchasePrice = new BigDecimal(row.getString("average_purchase_price"));
        BigDecimal currentPrice = new BigDecimal(row.getString("current_price"));
        BigDecimal expectedAnnualReturn = new BigDecimal(row.getString("expected_annual_return"));
        BigDecimal monthlyContribution = new BigDecimal(row.getString("monthly_contribution"));

        return new Investment(
                id, accountId, name, ticker, assetType, currency, quantity,
                averagePurchasePrice, currentPrice, expectedAnnualReturn, monthlyContribution);
    }
}
