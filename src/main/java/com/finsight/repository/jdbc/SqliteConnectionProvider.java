package com.finsight.repository.jdbc;

import com.finsight.repository.RepositoryException;
import com.finsight.repository.TransactionRunner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Owns a single JDBC {@link Connection} to a SQLite database, opened from a caller-supplied JDBC
 * URL rather than any location hardcoded here — the application layer decides where the real
 * database file lives (or that a test should use an isolated in-memory database); this class
 * only knows how to open and configure whatever URL it is given.
 *
 * <p>SQLite enforces foreign keys per-connection, not as a persistent database setting, so every
 * connection this class opens has {@code PRAGMA foreign_keys = ON} applied immediately — a
 * repository built on top of this class never has to remember to do that itself.
 *
 * <p>Deliberately a single long-lived connection rather than a pool: SQLite is an embedded,
 * single-file database accessed by one desktop application process, not a multi-client server
 * that needs connection pooling. Repositories obtain the same {@link Connection} from
 * {@link #getConnection()} for every operation and are responsible for scoping their own
 * {@code PreparedStatement}/{@code ResultSet} usage with try-with-resources; this class owns the
 * {@code Connection} itself and closes it via {@link #close()}.
 *
 * <p><b>Atomic multi-write transactions.</b> {@link #executeInTransaction(Runnable)} lets a caller
 * run several JDBC repository operations — each still built on {@link #getConnection()} exactly as
 * it is outside a transaction — as a single all-or-nothing unit, without that caller ever handling
 * a {@link Connection} or {@code SQLException} itself:
 * <ul>
 *   <li>{@code work} is expected to call one or more repositories built on this same provider
 *       instance (they all share the one {@link Connection}, so no repository code needs to change
 *       to participate in a transaction).</li>
 *   <li>If {@code work} returns normally, the transaction commits.</li>
 *   <li>If {@code work} throws any {@link RuntimeException} (including a {@link RepositoryException}
 *       from a failed repository call), every write {@code work} made is rolled back and the same
 *       exception is rethrown unchanged — this class does not swallow or re-wrap a failure that
 *       already has a meaningful type.</li>
 *   <li>Auto-commit is always restored to its normal (enabled) state before
 *       {@link #executeInTransaction(Runnable)} returns or throws, so the connection is exactly as
 *       usable afterward — by this transaction mechanism or by ordinary, non-transactional
 *       repository calls — as it was before.</li>
 *   <li>Nesting is rejected explicitly: calling {@link #executeInTransaction(Runnable)} again from
 *       within {@code work} throws {@link IllegalStateException} rather than silently starting a
 *       second, broken transaction on the same connection (SQLite/JDBC has no notion of nested
 *       transactions on a single connection). That failure is itself just a {@link RuntimeException}
 *       thrown from {@code work}, so it rolls the outer transaction back like any other failure.</li>
 * </ul>
 * This class is not thread-safe, consistently with the rest of this provider (see above) — it
 * assumes the single desktop-application thread that already owns this connection.
 *
 * <p>Implements {@link TransactionRunner} with exactly {@link #executeInTransaction(Runnable)}
 * below, so an application-service class needing this transaction boundary can depend on that
 * small repository-layer interface instead of on this JDBC-specific class.
 */
public final class SqliteConnectionProvider implements AutoCloseable, TransactionRunner {

    private final Connection connection;

    /**
     * {@code true} while a call to {@link #executeInTransaction(Runnable)} is in progress on this
     * connection. Used only to detect and reject a nested call; not a general-purpose "is this
     * connection mid-transaction" query, so it is not exposed.
     */
    private boolean inTransaction = false;

    /**
     * Opens a connection to {@code jdbcUrl} (e.g. {@code "jdbc:sqlite:/path/to/finsight.db"} for
     * a real file, or {@code "jdbc:sqlite::memory:"} for an isolated in-memory database) and
     * enables foreign key enforcement on it.
     *
     * @throws RepositoryException if the connection cannot be opened or configured
     */
    public SqliteConnectionProvider(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        try {
            this.connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        } catch (SQLException e) {
            throw new RepositoryException("Failed to open SQLite connection for " + jdbcUrl, e);
        }
    }

    /**
     * The single connection this provider owns. The same instance is returned on every call;
     * callers must not close it themselves — see {@link #close()}.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Runs {@code work} as a single atomic transaction against this connection: every write a
     * repository built on this provider performs while {@code work} runs is either all committed
     * (if {@code work} returns normally) or all rolled back (if {@code work} throws). See the
     * class-level documentation for the full contract, including how failure and nested calls are
     * handled.
     *
     * <p>{@code work} is a plain {@link Runnable} — not a checked-exception-throwing functional
     * interface — because every repository in this codebase already wraps {@link SQLException} as
     * an unchecked {@link RepositoryException} before it leaves a repository method; {@code work}
     * therefore never needs to declare a checked exception of its own.
     *
     * @throws NullPointerException if {@code work} is {@code null}
     * @throws IllegalStateException if this method is called again, directly or indirectly, from
     *     within an already-running {@code work} on this same connection
     * @throws RepositoryException if the transaction cannot be started, committed, or (on failure)
     *     rolled back, or if auto-commit cannot be restored afterward
     * @throws RuntimeException whatever {@code work} itself throws, rethrown unchanged after the
     *     transaction has been rolled back
     */
    @Override
    public void executeInTransaction(Runnable work) {
        Objects.requireNonNull(work, "work must not be null");
        if (inTransaction) {
            throw new IllegalStateException(
                    "executeInTransaction(...) was called while a transaction was already in "
                            + "progress on this connection; nested transactions are not supported");
        }

        inTransaction = true;
        try {
            beginTransaction();
            runAndCommit(work);
        } finally {
            restoreAutoCommit();
            inTransaction = false;
        }
    }

    private void beginTransaction() {
        try {
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to begin transaction", e);
        }
    }

    /**
     * Runs {@code work} and commits on success. On failure — whether {@code work} throws a
     * {@link RuntimeException} or the commit itself throws a {@link SQLException} — rolls back
     * every write {@code work} made, then propagates the original failure: {@code work}'s own
     * {@link RuntimeException} is rethrown unchanged (it already has a meaningful type, most often
     * a {@link RepositoryException} from a failed repository call), while a {@link SQLException}
     * from {@code commit()} itself is wrapped as a {@link RepositoryException}.
     */
    private void runAndCommit(Runnable work) {
        try {
            work.run();
            connection.commit();
        } catch (RuntimeException failure) {
            rollbackAfter(failure);
            throw failure;
        } catch (SQLException commitFailure) {
            rollbackAfter(commitFailure);
            throw new RepositoryException("Failed to commit transaction", commitFailure);
        }
    }

    /**
     * Rolls back the current transaction after {@code cause} has already been decided as the
     * failure to report. If the rollback itself fails, that rollback failure is reported instead
     * (wrapped as a {@link RepositoryException}, with {@code cause} attached via
     * {@link Throwable#addSuppressed(Throwable)}) since it means the transaction's outcome is no
     * longer certain — a stronger problem than the original failure.
     */
    private void rollbackAfter(Exception cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            RepositoryException wrapped =
                    new RepositoryException("Failed to roll back transaction after a failure", rollbackFailure);
            wrapped.addSuppressed(cause);
            throw wrapped;
        }
    }

    private void restoreAutoCommit() {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RepositoryException("Failed to restore auto-commit after transaction", e);
        }
    }

    /**
     * Closes the underlying connection. Safe to call once this provider (and any repository
     * built on it) is no longer needed.
     */
    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RepositoryException("Failed to close SQLite connection", e);
        }
    }
}
