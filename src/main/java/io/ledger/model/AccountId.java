package io.ledger.model;

public record AccountId(String value) {
    @Override public String toString() { return value; }
}
