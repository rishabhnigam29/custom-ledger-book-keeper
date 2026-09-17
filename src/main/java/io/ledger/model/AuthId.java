package io.ledger.model;

public record AuthId(String value) {
    @Override public String toString() { return value; }
}
