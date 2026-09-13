package com.finsight.domain.transaction;

/**
 * What a {@link Transaction}'s amount represents. Amounts are always stored positive; the type
 * determines their meaning (see {@link Transaction}).
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
