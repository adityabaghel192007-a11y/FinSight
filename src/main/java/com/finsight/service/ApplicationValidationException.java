package com.finsight.service;

/**
 * Signals that an application-service workflow (see {@link TransactionApplicationService}) was
 * asked to do something the user's input does not allow — referencing an account or category that
 * does not exist, or using a category whose type does not match the workflow — as opposed to a
 * persistence-layer failure, which remains {@link com.finsight.repository.RepositoryException}.
 *
 * <p>Deliberately a single class rather than a hierarchy of specific subtypes (an
 * "account not found" exception, a "category type mismatch" exception, ...), mirroring
 * {@link com.finsight.repository.RepositoryException}'s own reasoning: nothing in this milestone's
 * vertical slice needs to distinguish these failure kinds programmatically, only to report a clear
 * message — {@link #getMessage()} — that a future UI layer can surface directly to the user. If a
 * caller ever needs to branch on the specific kind of validation failure, that distinction can be
 * introduced deliberately then, rather than guessed at here. Unchecked, matching how the rest of
 * this codebase reports domain/application errors ({@link IllegalArgumentException},
 * {@link NullPointerException}, {@link com.finsight.repository.RepositoryException}) rather than
 * forcing every application-service method to declare a checked exception.
 */
public final class ApplicationValidationException extends RuntimeException {

    public ApplicationValidationException(String message) {
        super(message);
    }
}
