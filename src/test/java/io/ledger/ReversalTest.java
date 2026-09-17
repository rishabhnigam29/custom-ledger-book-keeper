package io.ledger;

import io.ledger.decision.Decision;
import io.ledger.decision.DecisionLog;
import io.ledger.event.LedgerEvent;
import io.ledger.event.ReversalResolver;
import io.ledger.journal.Journal;
import io.ledger.journal.PostingKind;
import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Reversal: required target, event-level, validated (FR-19..FR-24)")
class ReversalTest {

    private final AccountId acc = new AccountId("ACC-001");
    private final Journal journal = new Journal();
    private final DecisionLog log = new DecisionLog();
    private final ReversalResolver resolver = new ReversalResolver();

    private LedgerEvent.Reversal reversalOf(String target, Day valueDate) {
        return new LedgerEvent.Reversal(new EventId("R"), Day.of(6), acc, new EventId(target), valueDate);
    }

    private void seedDebit() {
        journal.append(acc, Day.of(5), Day.of(2), Money.of("-620.00", Currency.AED),
                PostingKind.DEBIT, new EventId("E7"), null);
    }

    @Test void mirrorsTheTargetAndLeavesItInPlace() {
        seedDebit();
        resolver.resolve(reversalOf("E7", Day.of(2)), journal, log);
        assertEquals(2, journal.size());                             // original kept
        var mirror = journal.snapshot().rows().get(1);
        assertEquals(Money.of("620.00", Currency.AED), mirror.signedAmount());
        assertEquals(Day.of(2), mirror.valueDate());                 // FR-21: inherits
        assertNotNull(mirror.reversesPosting());                     // names the row it cancels
    }

    @Test void unknownTargetIsRejectedAndWritesNothing() {
        resolver.resolve(reversalOf("E99", Day.of(2)), journal, log);
        assertEquals(0, journal.size());
        assertTrue(lastRejectionReason().contains("unknown target"));
    }

    @Test void aTargetIsNeverReversedTwice() {
        seedDebit();
        resolver.resolve(reversalOf("E7", Day.of(2)), journal, log);
        resolver.resolve(reversalOf("E7", Day.of(2)), journal, log);
        assertEquals(2, journal.size());
        assertTrue(lastRejectionReason().contains("already reversed"));
    }

    @Test void derivedRowsAreNotReversibleTargets() {
        journal.append(acc, Day.of(5), Day.of(2), Money.of("-25.00", Currency.AED),
                PostingKind.FEE, new EventId("Fee-D2"), null);
        resolver.resolve(reversalOf("Fee-D2", Day.of(2)), journal, log);
        assertEquals(1, journal.size());
        assertTrue(lastRejectionReason().contains("derived"));
    }

    @Test void anotherAccountsRowIsNotReversible() {
        journal.append(new AccountId("ACC-002"), Day.of(5), Day.of(2),
                Money.of("-1.000", Currency.BHD), PostingKind.DEBIT, new EventId("X"), null);
        resolver.resolve(reversalOf("X", Day.of(2)), journal, log);
        assertEquals(1, journal.size());
        assertTrue(lastRejectionReason().contains("account mismatch"));
    }

    @Test
    @DisplayName("a stated value date disagreeing with the target THROWS, it is our own inconsistency")
    void valueDateMismatchThrowsRatherThanBeingLogged() {
        seedDebit();
        assertThrows(IllegalStateException.class,
                () -> resolver.resolve(reversalOf("E7", Day.of(6)), journal, log));
    }

    private String lastRejectionReason() {
        var all = log.all();
        return ((Decision.ReversalRejected) all.get(all.size() - 1)).reason();
    }
}
