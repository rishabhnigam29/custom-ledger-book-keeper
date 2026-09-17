// FeeCurrencyTest.java
package io.ledger;

import io.ledger.config.LedgerConfig;
import io.ledger.eod.DayCloser;
import io.ledger.journal.Journal;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * FR-32. The brief denominates the overdraft fee in AED and defines none for any other currency.
 *
 * <p>These tests exist because an earlier version silently redenominated the figure: it booked
 * "FEE -25.000 BHD", inventing a 1:1 exchange rate in a system that has no FX at all. The stream
 * never exercises it, because ACC-002 is never negative, so nothing caught it.
 */
@DisplayName("Overdraft fee currency: defined for AED only, never converted")
class FeeCurrencyTest {

    @Test void aFeeIsDefinedForAed() {
        assertEquals(Money.of("25.00", Currency.AED), LedgerConfig.overdraftFeeFor(Currency.AED));
    }

    @Test
    @DisplayName("asking for a fee in a currency it is not defined for THROWS")
    void noFeeExistsForAnyOtherCurrency() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> LedgerConfig.overdraftFeeFor(Currency.BHD));
        assertTrue(e.getMessage().contains("BHD"));
        assertTrue(e.getMessage().contains("FX"));
    }

    @Test
    @DisplayName("a negative BHD account throws rather than booking BHD 25.000")
    void closingANegativeNonAedDayRefusesToInventAFee() {
        Account bhd = Account.of("ACC-002", Currency.BHD);
        Journal journal = new Journal();
        journal.append(bhd.id(), Day.of(1), Day.of(1), Money.of("-5.000", Currency.BHD),
                PostingKind.DEBIT, new EventId("X"), null);

        assertThrows(IllegalStateException.class,
                () -> new DayCloser(true).close(journal, bhd, Day.of(1), Day.of(1)));
        assertEquals(1, journal.size(), "nothing may be appended when the fee cannot be resolved");
    }

    @Test
    @DisplayName("a POSITIVE non-AED day closes normally: the fee is resolved lazily")
    void aPositiveNonAedDayIsUnaffected() {
        // ACC-002 is never negative in the real stream, so the whole six-day replay must never
        // reach the throw. If the fee were resolved eagerly at the top of close(), every cutover
        // would blow up on the BHD account.
        assertEquals(bhd("10.000"), replayDefaults().closing(ACC2));
    }
}