package com.finsight.repository.jdbc;

import com.finsight.domain.transaction.Transaction;
import com.finsight.repository.RepositoryException;
import com.finsight.repository.TransactionRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC/SQLite implementation of {@link TransactionRepository}, backed by the {@code transactions}
 * table created by {@link SchemaInitializer}. Structured identically to
 * {@link AccountRepositoryJdbc} and {@link CategoryRepositoryJdbc}, with the addition of nullable
 * column handling for {@code category_id} and {@code transfer_group_id} (see
 * {@link #bindNullableLong}) — {@link Transaction} exposes both as {@link Optional}, and a plain
 * {@code PreparedStatement.setLong} cannot express SQL {@code NULL}.
 *
 * <p>Every operation uses a {@link PreparedStatement} (never string-concatenated SQL) and scopes
 * its {@code PreparedStatement}/{@code ResultSet} with try-with-resources; the underlying
 * {@link java.sql.Connection}, supplied by a {@link SqliteConnectionProvider}, is not owned by
 * this class and is never closed here. Any {@link SQLException} is wrapped as a
 * {@link RepositoryException} — this class never lets a raw {@code SQLException} escape, keeping
 * {@link TransactionRepository}'s contract free of JDBC-specific checked exceptions. This includes
 * a foreign-key violation on insert (an {@code accountId} or non-null {@code categoryId} that does
 * not refer to a persisted row): SQLite reports that as a {@code SQLException}, which is wrapped
 * the same way as any other failure.
 */
public final class TransactionRepositoryJdbc implements TransactionRepository {

    private static final String INSERT_SQL =
            "INSERT INTO transactions (account_id, amount, type, category_id, date, description, transfer_group_id) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    private static final String SELECT_BY_ID_SQL =
            "SELECT id, account_id, amount, type, category_id, date, description, transfer_group_id "
                    + "FROM transactions WHERE id = ?";
    private static final String SELECT_ALL_SQL =
            "SELECT id, account_id, amount, type, category_id, date, description, transfer_group_id "
                    + "FROM transactions ORDER BY id";
    private static final String SELECT_BY_ACCOUNT_ID_SQL =
            "SELECT id, account_id, amount, type, category_id, date, description, transfer_group_id "
                    + "FROM transactions WHERE account_id = ? ORDER BY id";
    private static final String UPDATE_TRANSFER_GROUP_ID_SQL =
            "UPDATE transactions SET transfer_group_id = ? WHERE id = ?";
    private static final String DELETE_SQL =
            "DELETE FROM transactions WHERE id = ?";

    private final SqliteConnectionProvider connectionProvider;

    public TransactionRepositoryJdbc(SqliteConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider must not be null");
    }

    /**
     * Inserts {@code transaction}'s fields as a new row — {@code transaction.getId()} is never
     * sent to the database — and returns a new {@link Transaction} carrying the id SQLite
     * generated for that row, via {@link Transaction#withId(long)}. {@code transaction} itself is
     * left unmodified. {@code amount} is stored via {@link java.math.BigDecimal#toPlainString()},
     * never as a floating-point value.
     */
    @Override
    public Transaction save(Transaction transaction) {
        Objects.requireNonNull(transaction, "transaction must not be null");
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, transaction.getAccountId());
            statement.setString(2, transaction.getAmount().toPlainString());
            statement.setString(3, transaction.getType().name());
            bindNullableLong(statement, 4, transaction.getCategoryId().orElse(null));
            statement.setString(5, transaction.getDate().toString());
            statement.setString(6, transaction.getDescription().orElse(null));
            bindNullableLong(statement, 7, transaction.getTransferGroupId().orElse(null));
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (!generatedKeys.next()) {
                    throw new RepositoryException("SQLite did not return a generated id for the new transaction");
                }
                long generatedId = generatedKeys.getLong(1);
                return transaction.withId(generatedId);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to save transaction: " + transaction, e);
        }
    }

    @Override
    public Optional<Transaction> findById(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ID_SQL)) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(TransactionMapper.fromRow(row));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find transaction by id " + id, e);
        }
    }

    @Override
    public List<Transaction> findAll() {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_ALL_SQL);
             ResultSet rows = statement.executeQuery()) {
            List<Transaction> transactions = new ArrayList<>();
            while (rows.next()) {
                transactions.add(TransactionMapper.fromRow(rows));
            }
            return List.copyOf(transactions);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find all transactions", e);
        }
    }

    /**
     * Ordered ascending by id, matching {@link #findAll()}'s stable order — the order transactions
     * for one account were originally inserted in, which is the natural, unsurprising order to show
     * them back in and requires no additional column or index beyond the primary key SQLite already
     * maintains. This performs no check that {@code accountId} refers to a persisted account: an
     * unknown id simply matches no rows, exactly like a known account with no transactions yet.
     */
    @Override
    public List<Transaction> findByAccountId(long accountId) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(SELECT_BY_ACCOUNT_ID_SQL)) {
            statement.setLong(1, accountId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Transaction> transactions = new ArrayList<>();
                while (rows.next()) {
                    transactions.add(TransactionMapper.fromRow(rows));
                }
                return List.copyOf(transactions);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to find transactions for account " + accountId, e);
        }
    }

    /**
     * Updates only the {@code transfer_group_id} column — see the interface documentation for why
     * this narrowly-scoped operation exists rather than a general update capability. Verifies
     * exactly one row was updated, since a caller of this method always expects
     * {@code transactionId} to refer to a row it (or its own transaction-boundary caller) just
     * inserted; zero rows updated would otherwise silently leave a transfer leg missing its
     * grouping value.
     */
    @Override
    public void assignTransferGroupId(long transactionId, long transferGroupId) {
        try (PreparedStatement statement =
                     connectionProvider.getConnection().prepareStatement(UPDATE_TRANSFER_GROUP_ID_SQL)) {
            statement.setLong(1, transferGroupId);
            statement.setLong(2, transactionId);
            int rowsUpdated = statement.executeUpdate();
            if (rowsUpdated != 1) {
                throw new RepositoryException(
                        "Expected to update exactly one transaction when assigning transferGroupId "
                                + transferGroupId + " to transaction " + transactionId + ", but updated " + rowsUpdated);
            }
        } catch (SQLException e) {
            throw new RepositoryException(
                    "Failed to assign transferGroupId " + transferGroupId + " to transaction " + transactionId, e);
        }
    }

    @Override
    public void delete(long id) {
        try (PreparedStatement statement = connectionProvider.getConnection().prepareStatement(DELETE_SQL)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to delete transaction " + id, e);
        }
    }

    /**
     * Binds a possibly-absent {@code Long} (a category id or transfer group id) to a
     * {@code PreparedStatement} parameter, using {@code setNull} rather than letting a boxed
     * {@code null} reach {@code setLong} (which cannot accept one). This is how
     * {@link java.util.Optional#empty()} on the domain side becomes SQL {@code NULL}.
     */
    private static void bindNullableLong(PreparedStatement statement, int parameterIndex, Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, Types.BIGINT);
        } else {
            statement.setLong(parameterIndex, value);
        }
    }
}
