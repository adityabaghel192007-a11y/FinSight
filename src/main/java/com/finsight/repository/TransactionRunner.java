package com.finsight.repository;

/**
 * A minimal abstraction over "run this unit of work atomically," so that code above the
 * persistence layer (currently {@code TransactionApplicationService}'s transfer workflow) can
 * require an atomic multi-write transaction boundary without depending on
 * {@code com.finsight.repository.jdbc.SqliteConnectionProvider}, or any other JDBC-specific type,
 * directly. {@code SqliteConnectionProvider} implements this interface with its existing
 * {@code executeInTransaction(Runnable)} method — nothing about the method itself is
 * JDBC-specific, so exposing it through this repository-layer interface keeps an application
 * service's dependencies limited to {@code com.finsight.repository}, exactly like
 * {@link AccountRepository}, {@link CategoryRepository}, and {@link TransactionRepository}.
 *
 * <p>Deliberately a single method with a single responsibility — not a general
 * "unit of work"/transaction-manager framework. There is no explicit begin/commit/rollback here,
 * no nested-transaction policy (that remains {@code SqliteConnectionProvider}'s own concern,
 * documented there), and no notion of multiple simultaneous transactions or connections. A caller
 * supplies the work to run; the implementation decides how "atomically" is achieved.
 */
public interface TransactionRunner {

    /**
     * Runs {@code work} as a single atomic unit: an implementation is expected to either apply
     * every write {@code work} performs, or none of them — exactly what
     * {@code SqliteConnectionProvider.executeInTransaction(Runnable)} already guarantees for the
     * one implementation of this method in this codebase. If {@code work} throws, that exception
     * propagates from this method after rollback; if {@code work} completes normally, its writes
     * are committed.
     */
    void executeInTransaction(Runnable work);
}
