package io.ledger.model;

public record PostingId(long seq) {
    @Override public String toString() { return "#" + seq; }
}
