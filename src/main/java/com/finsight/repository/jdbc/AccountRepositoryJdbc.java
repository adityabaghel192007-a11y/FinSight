package com.finsight.repository.jdbc;

import com.finsight.domain.account.Account;
import com.finsight.repository.AccountRepository;
import com.finsight.repository.RepositoryException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC/SQLite implementation of {@link AccountRepository}, backed by the {@code accounts} table
 * created by {@link SchemaInitializer}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link AccountRepository}'s contract free of JDBC-specific checked exceptions.
 */
public final class AccountRepositoryJdbc implements AccountRepository {

    private static final String INSERT_SQL =
            "INSERT INTO accounts (name, type, currency, opening_balance) VALUES (?, ?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, name, type, currency, opening_balance FROM accounts WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, name, type, currency, opening_balance FROM accounts ORDER BY id";
    private static final String DELETE_SQL =
            "DELETE FROM accounts WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public AccountRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code account}'s fields as a new row — {@code account.getId()} is never sent to
     * the database — and returns a new {@link Account} carrying the id SQLite generated for that
     * row, via {@link Account#withId(long)}. {@code account} itself is left unmodified.
     */
    @Override
    public Account save(Account account) {
        Objects.requireNonNull(account, "account must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, account.getName());
            statement.setString(2, account.getType().name());
            statement.setString(3, account.getCurrency().name());
            statement.setString(4, account.getOpeningBalance().toPlainString());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new account");
                }
                long generatedId = generatedKeys.getLong(1);
                return account.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save account: " + account, e);
        }
    }

    @Override
    public Optional<Account> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(AccountMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find account by id " + id, e);
        }
    }

    @Override
    public List<Account> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Account> accounts = new ArrayList<>();
            while (rows.next()) {
                accounts.add(AccountMapper.fromRow(rows));
            }
            return List.copyOf(accounts);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all accounts", e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete account " + id, e);
        }
    }
}
