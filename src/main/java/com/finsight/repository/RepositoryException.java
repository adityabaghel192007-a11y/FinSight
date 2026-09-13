package com.finsight.repository;

/**
 * Wraps a persistence-layer failure (a JDBC/SQLite error, or any other implementation-specific
 * failure a repository encounters) as a single, unchecked exception type that callers of a
 * repository interface can catch without depending on any implementation's underlying
 * technology (e.g. {@code java.sql.SQLException}).
 *
 * <p>Deliberately a single class rather than a hierarchy of specific subtypes (a
 * "not found" exception, a "constraint violation" exception, ...) — this milestone's repository
 * surface (see {@link AccountRepository}) has no caller that needs to distinguish failure kinds
 * yet, so a richer hierarchy would be speculative. Unchecked, matching how the rest of this
 * codebase reports programmer/domain errors ({@link IllegalArgumentException},
 * {@link NullPointerException}) rather than forcing every repository method signature to declare
 * a checked exception.
 */
public final class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
