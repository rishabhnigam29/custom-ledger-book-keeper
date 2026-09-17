package io.ledger.config;

import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every constant in one place, so NUMBERS.md points at code rather than restating values.
 *
 * <p>Holds no {@code Money} constants: {@code Money} reads {@link #ROUNDING} during its own static
 * initialisation, so a {@code Money}-typed field here would create a class-initialisation cycle.
 * The fee is exposed as a method instead, resolved at call time.
 */
public final class LedgerConfig {

    private LedgerConfig() {}

    /** FR-25. Overdraft fee, denominated in the account's own currency. */
    public static final BigDecimal OVERDRAFT_FEE_AMOUNT = new BigDecimal("25.00");

    /** FR-33. 0.04% per day as a fraction. NOT 0.04 -- the factor-of-100 trap. */
    public static final BigDecimal DAILY_RATE = new BigDecimal("0.0004");

    /** A-10. HALF_UP; no value in this stream lands on an exact half. */
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** The replay window, Day 1 .. Day 6 inclusive. */
    public static final int WINDOW_DAYS = 6;

    /** FR-43. Extra minor units go to the FIRST instalments. A-6. */
    public static final boolean REMAINDER_TO_FIRST = true;

    /**
     * FR-32. The brief denominates the overdraft fee in AED and says nothing about any other
     * currency, so no fee is defined for one.
     *
     * <p>Asking for a fee on a non-AED account THROWS rather than redenominating the figure.
     * Reusing "25.00" as BHD 25.000 would silently invent a 1:1 exchange rate, and a fee
     * assessment is the worst possible place to introduce FX into a system that has none. This is
     * a method rather than a constant so that it is resolved at call time and only when a fee is
     * actually about to be charged.
     */
    public static Money overdraftFeeFor(Currency currency) {
        if (currency != Currency.AED) {
            throw new IllegalStateException(
                    "no overdraft fee is defined for " + currency
                            + "; the brief specifies AED 25.00 only. Converting would invent an FX "
                            + "rate, which this system deliberately does not do.");
        }
        return new Money(OVERDRAFT_FEE_AMOUNT, Currency.AED);
    }
}