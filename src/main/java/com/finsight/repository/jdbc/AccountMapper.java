package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.domain.account.AccountType;
import com.finsight.domain.account.Currency;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Converts one {@code accounts} row into an {@link Account}. Kept as a small, explicit,
 * hand-written mapper rather than a generic reflection-based one, so the SQL-to-domain
 * conversion for each column stays easy to read and debug:
 *
 * <ul>
 *   <li>{@code id} (SQL {@code INTEGER}) → {@code long}</li>
 *   <li>{@code name} (SQL {@code TEXT}) → {@code String}</li>
 *   <li>{@code type} (SQL {@code TEXT}) → {@link AccountType} via {@link AccountType#valueOf}</li>
 *   <li>{@code currency} (SQL {@code TEXT}) → {@link Currency} via {@link Currency#valueOf}</li>
 *   <li>{@code opening_balance} (SQL {@code TEXT}) → {@link BigDecimal} via its string
 *       constructor, never {@link ResultSet#getBigDecimal}, since that column stores exact
 *       decimal text rather than a JDBC-native numeric type</li>
 * </ul>
 *
 * <p>Package-private: only {@link AccountRepositoryJdbc} needs this mapping.
 */
final class AccountMapper {

    private AccountMapper() {
        // stateless utility class; not instantiable
    }

    static Account fromRow(ResultSet row) throws SQLException {
        long id = row.getLong("id");
        String name = row.getString("name");
        AccountType type = AccountType.valueOf(row.getString("type"));
        Currency currency = Currency.valueOf(row.getString("currency"));
        BigDecimal openingBalance = new BigDecimal(row.getString("opening_balance"));

        return new Account(id, name, type, currency, openingBalance);
    }
}
