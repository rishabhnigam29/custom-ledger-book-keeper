package io.ledger;

import io.ledger.event.EventStream;
import io.ledger.replay.LedgerSetup;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A-1. The brief lists E9 ninth and E10 tenth, but E9 posts on Day 6 and E10 on Day 5. Grouping by
 * posting day is chosen. The alternative reading treats E10 as a late arrival on Day 6.
 */
@DisplayName("Replay order: posting-day grouping vs the brief's listed order")
class OrderingTest {

    @Test
    @DisplayName("under the default interest basis the two readings agree exactly")
    void orderingIsInvisibleUnderFinalValueDatedInterest() {
        ReplayDriver byDay = replayStream(LedgerSetup.defaults(), EventStream.byPostingDay());
        ReplayDriver listed = replayStream(LedgerSetup.defaults(), EventStream.inListedOrder());
        assertEquals(bhd("10.008"), byDay.closing(ACC2));
        assertEquals(bhd("10.008"), listed.closing(ACC2));
        assertEquals(aed("466.03"), byDay.closing(ACC1));
        assertEquals(aed("466.03"), listed.closing(ACC1));
    }

    @Test
    @DisplayName("under the frozen basis the listed order costs ACC-002 0.004")
    void orderingCostsFourThousandthsUnderTheFrozenBasis() {
        LedgerSetup frozen = LedgerSetup.of("retroactive", "as-known-frozen", true);
        assertEquals(bhd("10.008"), replayStream(frozen, EventStream.byPostingDay()).closing(ACC2));
        // Day 5 closes before E10 arrives, so a 0.000 accrual is frozen and only Day 6 earns.
        assertEquals(bhd("10.004"), replayStream(frozen, EventStream.inListedOrder()).closing(ACC2));
    }

    @Test void acc001IsNeverAffectedByTheOrderingChoice() {
        for (LedgerSetup s : java.util.List.of(LedgerSetup.defaults(),
                LedgerSetup.of("retroactive", "as-known-frozen", true))) {
            assertEquals(replayStream(s, EventStream.byPostingDay()).closing(ACC1),
                    replayStream(s, EventStream.inListedOrder()).closing(ACC1));
        }
    }
}
