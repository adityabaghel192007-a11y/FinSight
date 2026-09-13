package com.finsight.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SchemaInitializer} and the foreign-key enforcement
 * {@link SqliteConnectionProvider} turns on for every connection it opens.
 *
 * <p>Every test opens its own isolated {@code jdbc:sqlite::memory:} database — never a file on
 * disk, and never shared between tests — so these tests cannot depend on, or leave behind, any
 * real application database.
 */
class SchemaInitializerTest {

    private static SqliteConnectionProvider newInMemoryProvider() {
        return new SqliteConnectionProvider("jdbc:sqlite::memory:");
    }

    @Test
    void initializeCreatesAllSevenTablesWithoutError() throws SQLException {
        try (SqliteConnectionProvider provider = newInMemoryProvider()) {
            // Would throw RepositoryException if any CREATE TABLE statement failed.
            SchemaInitializer.initialize(provider.getConnection());

            for (String table : new String[] {
                    "accounts", "categories", "transactions", "investments", "loans", "goals", "budgets"
            }) {
                try (Statement statement = provider.getConnection().createStatement()) {
                    // A trivial query against each table only succeeds if it actually exists.
                    statement.execute("SELECT * FROM " + table + " LIMIT 1");
                }
            }
        }
    }

    @Test
    void initializeCanBeCalledMoreThanOnceWithoutLosingData() throws SQLException {
        try (SqliteConnectionProvider provider = newInMemoryProvider()) {
            SchemaInitializer.initialize(provider.getConnection());

            try (PreparedStatement insert = provider.getConnection().prepareStatement(
                    "INSERT INTO accounts (name, type, currency, opening_balance) VALUES (?, ?, ?, ?)")) {
                insert.setString(1, "Checking");
                insert.setString(2, "BANK");
                insert.setString(3, "INR");
                insert.setString(4, "0.00");
                insert.executeUpdate();
            }

            // CREATE TABLE IF NOT EXISTS: re-running initialization must not fail or wipe data.
            SchemaInitializer.initialize(provider.getConnection());

            try (Statement statement = provider.getConnection().createStatement();
                 ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                rows.next();
                assertEquals(1, rows.getInt(1), "re-initializing the schema must not delete existing rows");
            }
        }
    }

    @Test
    void foreignKeyEnforcementIsEnabledOnEveryConnection() throws SQLException {
        try (SqliteConnectionProvider provider = newInMemoryProvider()) {
            SchemaInitializer.initialize(provider.getConnection());

            // No account with id 9999 exists, so this insert violates transactions.account_id's
            // foreign key — it must be rejected, proving PRAGMA foreign_keys is actually ON
            // (SQLite silently allows this exact statement when foreign keys are off).
            try (PreparedStatement insert = provider.getConnection().prepareStatement(
                    "INSERT INTO transactions (account_id, amount, type, date) VALUES (?, ?, ?, ?)")) {
                insert.setLong(1, 9999L);
                insert.setString(2, "100.00");
                insert.setString(3, "EXPENSE");
                insert.setString(4, "2026-01-01");

                assertThrows(SQLException.class, insert::executeUpdate,
                        "inserting a transaction with a non-existent account_id should be rejected");
            }
        }
    }

    @Test
    void foreignKeysPragmaReadsOnForANewConnection() throws SQLException {
        try (SqliteConnectionProvider provider = newInMemoryProvider();
             Statement statement = provider.getConnection().createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1), "PRAGMA foreign_keys should read back as ON (1)");
        }
    }
}
