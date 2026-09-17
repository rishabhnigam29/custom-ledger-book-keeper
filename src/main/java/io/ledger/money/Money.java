package io.ledger.money;

import io.ledger.config.LedgerConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Immutable, currency-tagged amount held at exactly its currency's scale.
 *
 * <p>The compact constructor is the only gate: an ill-scaled amount cannot exist anywhere in the
 * system (FR-47), rather than being caught at the point of use. Arithmetic guards currency, so
 * FR-6 is structural rather than a rule someone has to remember.
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.scale() != currency.scale()) {
            throw new IllegalArgumentException(
                    "scale " + amount.scale() + " != " + currency + " scale " + currency.scale()
                            + " for amount " + amount.toPlainString());
        }
    }

    /** Throws if the literal carries more precision than the currency allows. FR-47. */
    public static Money of(String amount, Currency currency) {
        return new Money(
                new BigDecimal(amount).setScale(currency.scale(), RoundingMode.UNNECESSARY),
                currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO.setScale(currency.scale()), currency);
    }

    public Money plus(Money other) {
        guard(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other) {
        guard(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negated() {
        return new Money(amount.negate(), currency);
    }

    public Money abs() {
        return new Money(amount.abs(), currency);
    }

    /** FR-37. Exactly one rounding, at the account's own precision. */
    public Money multiplyAndRound(BigDecimal rate) {
        return new Money(
                amount.multiply(rate).setScale(currency.scale(), LedgerConfig.ROUNDING),
                currency);
    }

    public boolean isNegative() { return amount.signum() < 0; }

    public boolean isPositive() { return amount.signum() > 0; }

    public boolean isZero() { return amount.signum() == 0; }

    private void guard(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException(
                    "cross-currency arithmetic: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public int compareTo(Money other) {
        guard(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }

    /** Signed, for ledger rows: "+1200.00" / "-950.00". */
    public String signed() {
        return (amount.signum() >= 0 ? "+" : "") + amount.toPlainString();
    }
}
