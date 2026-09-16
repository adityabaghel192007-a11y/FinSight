package com.finsight.app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decides where FinSight's file-backed SQLite database lives on disk. This is a single, explicit
 * decision point rather than a general-purpose configuration framework: FinSight is a single-user
 * desktop application with exactly one database file, so nothing more elaborate than "pick one
 * fixed, non-source, non-cwd-dependent path and make sure it exists" is needed here.
 *
 * <p><b>The convention.</b> A dedicated {@code .finsight} directory directly under the current
 * user's home directory ({@link System#getProperty(String) System.getProperty("user.home")}),
 * holding a single file, {@code finsight.db} — e.g. {@code ~/.finsight/finsight.db} on macOS and
 * Linux. This is not a decision invented for this milestone: it is the same convention already
 * used, on a real developer machine, by other per-user tools that keep their application data
 * next to the home directory rather than beside a project's source tree or inside its current
 * working directory (for example {@code ~/.m2}, {@code ~/.npm}, {@code ~/.config}). {@code
 * user.home} resolves to a writable, per-user directory on every platform the JDK/JavaFX
 * toolchain here targets, so no OS-specific branching (XDG on Linux, {@code AppData} on Windows,
 * {@code Application Support} on macOS) is needed to make this correct — that additional
 * complexity is deliberately left out of scope for this milestone.
 *
 * <p>{@link com.finsight.repository.jdbc.SqliteConnectionProvider} opens whatever JDBC URL it is
 * given and does not create missing parent directories itself (nor does the SQLite JDBC driver),
 * so {@link #jdbcUrl()} creates the {@code .finsight} directory first, if this is the first run,
 * before returning the URL that points into it — the caller never has to create anything by hand.
 */
public final class DatabaseLocation {

    private static final String DIRECTORY_NAME = ".finsight";
    private static final String FILE_NAME = "finsight.db";

    private DatabaseLocation() {
        // stateless utility class; not instantiable
    }

    /**
     * The JDBC URL for FinSight's real, file-backed application database, under the current
     * user's home directory. Ensures the containing directory exists before returning.
     *
     * @throws UncheckedIOException if the directory does not already exist and cannot be created
     */
    public static String jdbcUrl() {
        return jdbcUrl(Path.of(System.getProperty("user.home")));
    }

    /**
     * Same as {@link #jdbcUrl()}, but resolved under the given home directory instead of the
     * real {@code user.home} system property. Package-private: exists solely so tests can verify
     * this class's directory-creation and URL-construction behavior against a temporary directory,
     * without ever touching the current user's real home directory or real application database.
     *
     * @throws NullPointerException if {@code homeDirectory} is {@code null}
     * @throws UncheckedIOException if the {@code .finsight} directory does not already exist
     *     under {@code homeDirectory} and cannot be created
     */
    static String jdbcUrl(Path homeDirectory) {
        Path directory = homeDirectory.resolve(DIRECTORY_NAME);
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Failed to create FinSight application data directory: " + directory, e);
        }
        return "jdbc:sqlite:" + directory.resolve(FILE_NAME);
    }
}
