package io.ledger.model;

public record EventId(String value) {
    /** For rows the engine derives for itself: fees, fee reversals, capitalization. */
    public static EventId derived(String value) { return new EventId(value); }

    @Override public String toString() { return value; }
}
