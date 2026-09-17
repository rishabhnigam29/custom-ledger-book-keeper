package io.ledger.journal;

public enum PostingKind {
    OPENING,
    CREDIT,
    DEBIT,
    SETTLEMENT,
    FEE,
    FEE_REVERSAL,
    REVERSAL,
    INTEREST_CAPITALIZATION;

    /** Rows the engine derives for itself; they are not reversible targets. FR-23. */
    public boolean isDerived() {
        // OPENING belongs here too: the engine posts it, and a
        // reversal that targeted the account's opening balance.
        return this == OPENING
                || this == FEE
                || this == FEE_REVERSAL
                || this == INTEREST_CAPITALIZATION;
    }
}
