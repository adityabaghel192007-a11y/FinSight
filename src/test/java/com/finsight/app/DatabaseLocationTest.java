package com.finsight.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseLocation}. Every test uses the package-private
 * {@link DatabaseLocation#jdbcUrl(Path)} overload against a manually created, manually deleted
 * temporary directory (matching this codebase's existing {@code Files.createTempFile}/{@code
 * Files.createTempDirectory} convention, e.g. {@code CompositionRootTest}) — never {@link
 * DatabaseLocation#jdbcUrl()} itself, which always resolves under the real current user's home
 * directory. This is deliberate: these tests must never create, read, or touch the real {@code
 * ~/.finsight} directory or the user's real application database.
 */
class DatabaseLocationTest {

    @Test
    void createsTheFinsightDirectoryUnderTheGivenHomeDirectoryWhenItDoesNotExist() throws IOException {
        Path homeDirectory = Files.createTempDirectory("finsight-database-location-test-home");
        try {
            Path expectedDirectory = homeDirectory.resolve(".finsight");
            assertTrue(Files.notExists(expectedDirectory), "precondition: directory must not already exist");

            DatabaseLocation.jdbcUrl(homeDirectory);

            assertTrue(Files.isDirectory(expectedDirectory),
                    "jdbcUrl(...) must create the .finsight directory when it is missing");
        } finally {
            deleteRecursively(homeDirectory);
        }
    }

    @Test
    void returnsTheExpectedJdbcUrlPointingAtFinsightDbInsideTheFinsightDirectory() throws IOException {
        Path homeDirectory = Files.createTempDirectory("finsight-database-location-test-home");
        try {
            String jdbcUrl = DatabaseLocation.jdbcUrl(homeDirectory);

            String expected = "jdbc:sqlite:" + homeDirectory.resolve(".finsight").resolve("finsight.db");
            assertEquals(expected, jdbcUrl);
        } finally {
            deleteRecursively(homeDirectory);
        }
    }

    @Test
    void isSafeToCallRepeatedlyAgainstAnAlreadyExistingDirectory() throws IOException {
        Path homeDirectory = Files.createTempDirectory("finsight-database-location-test-home");
        try {
            String first = DatabaseLocation.jdbcUrl(homeDirectory);
            String second = DatabaseLocation.jdbcUrl(homeDirectory);

            assertEquals(first, second,
                    "calling jdbcUrl(...) again against an already-initialized home directory "
                            + "must not fail and must return the same URL, since "
                            + "Files.createDirectories(...) is itself idempotent");
        } finally {
            deleteRecursively(homeDirectory);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }
    }
}
