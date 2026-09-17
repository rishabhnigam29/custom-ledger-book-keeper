package io.ledger;

import io.ledger.event.EventStream;
import io.ledger.replay.LedgerSetup;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The strongest evidence for accepting criterion C6.
 *
 * <p>The only fair test of "all balances and fees return to their pre-E7 values" is a replay in
 * which E7 and E9 never happened at all. With fee reversal on, the two runs agree to the fil.
 */
@DisplayName("C6: the reversal restores the world where the backdated debit never happened")
class CounterfactualTest {

    private final ReplayDriver full = replayDefaults();
    private final ReplayDriver without =
            replayStream(LedgerSetup.defaults(), EventStream.without("E7", "E9"));

    @Test void dayClosingsMatchCellForCell() {
        assertEquals(without.valueDatedVector(ACC1), full.valueDatedVector(ACC1));
        assertEquals(aedList("250.00", "250.00", "650.00", "465.00", "465.00", "466.03"),
                full.valueDatedVector(ACC1));
    }

    @Test void netFeesMatch()      { assertEquals(without.netFees(ACC1), full.netFees(ACC1)); }

    @Test void interestMatches()   { assertEquals(without.interestVector(ACC1), full.interestVector(ACC1)); }

    @Test void finalBalanceMatches() {
        assertEquals(without.closing(ACC1), full.closing(ACC1));
        assertEquals(aed("466.03"), full.closing(ACC1));
    }

    @Test
    @DisplayName("but the LEDGER does not return: the full run carries eight more rows")
    void theLedgerItselfGrewRatherThanReturning() {
        // E1, E2, E4, E5 settlement, capitalization
        assertEquals(5, without.journal().forAccount(ACC1.id()).size());
        // the same five plus E7, three fees, E9 and three fee reversals
        assertEquals(13, full.journal().forAccount(ACC1.id()).size());
        // A balance is a derived value and can return to a previous value. The log only grows.
        // C6 claims the former, not the latter, which is why it stands.
    }
}
