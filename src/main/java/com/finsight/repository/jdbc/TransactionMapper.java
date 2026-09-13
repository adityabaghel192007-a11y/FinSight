package com.finsight.repository.jdbc;

import com.finsight.domain.transaction.Transaction;
import com.finsight.domain.transaction.TransactionType;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Converts one {@code transactions} row into a {@link Transaction}. Kept as a small, explicit,
 * hand-written mapper rather than a generic reflection-based one, matching {@code AccountMapper}
 * and {@link CategoryMapper}:
 *
 * <ul>
 *   <li>{@code id}, {@code account_id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code amount} (SQL {@code TEXT}) → {@link BigDecimal} via its string constructor, never
 *       {@link ResultSet#getBigDecimal}, since that column stores exact decimal text rather than
 *       a JDBC-native numeric type</li>
 *   <li>{@code type} (SQL {@code TEXT}) → {@link TransactionType} via
 *       {@link TransactionType#valueOf}</li>
 *   <li>{@code category_id} (SQL nullable {@code INTEGER}) → {@code Long}, {@code null} when the
 *       column is SQL {@code NULL} (an uncategorized transaction) — {@link Transaction}'s
 *       constructor turns that {@code null} into {@link java.util.Optional#empty()} on
 *       {@link Transaction#getCategoryId()}</li>
 *   <li>{@code date} (SQL {@code TEXT}, ISO {@code YYYY-MM-DD}) → {@link LocalDate} via
 *       {@link LocalDate#parse(CharSequence)}</li>
 *   <li>{@code description} (SQL nullable {@code TEXT}) → {@code String}, {@code null} when the
 *       column is SQL {@code NULL}</li>
 *   <li>{@code transfer_group_id} (SQL nullable {@code INTEGER}) → {@code Long}, {@code null} when
 *       the column is SQL {@code NULL} (a non-transfer transaction)</li>
 * </ul>
 *
 * <p>Package-private: only {@link TransactionRepositoryJdbc} needs this mapping.
 */
final class TransactionMapper {

    private TransactionMapper() {
        // stateless utility class; not instantiable
    }

    static Transaction fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        long accountId = row.getLong("account_id");
        BigDecimal amount = new BigDecimal(row.getString("amount"));
        TransactionType type = TransactionType.valueOf(row.getString("type"));
        Long categoryId = nullableLong(row, "category_id");
        LocalDate date = LocalDate.parse(row.getString("date"));
        String description = row.getString("description");
        Long transferGroupId = nullableLong(row, "transfer_group_id");

        return new Transaction(id, accountId, amount, type, categoryId, date, description, transferGroupId);
    }

    /**
     * Reads a nullable {@code INTEGER} column as a boxed {@code Long}, returning {@code null}
     * (rather than {@code 0}) when the column is SQL {@code NULL} — {@link ResultSet#getLong}
     * alone cannot distinguish a stored {@code 0} from {@code NULL}, so {@link ResultSet#wasNull()}
     * is checked immediately afterward.
     */
    private static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }
}
