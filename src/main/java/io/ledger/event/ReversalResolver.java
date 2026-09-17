package io.ledger.event;

import io.ledger.decision.Decision;
import io.ledger.decision.DecisionLog;
import io.ledger.journal.Journal;
import io.ledger.journal.JournalSnapshot;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;

import java.util.List;

/**
 * FR-19 .. FR-24.
 *
 * <p>Note the asymmetry between the guard clauses and the value-date check. A missing or
 * already-reversed target is something the outside world can legitimately send, so it is logged
 * and replay continues (FR-46). A stated value date that disagrees with the target is an
 * inconsistency inside our own data, so it throws (FR-47).
 */
public final class ReversalResolver {

    public void resolve(LedgerEvent.Reversal reversal, Journal journal, DecisionLog log) {
        JournalSnapshot snapshot = journal.snapshot();
        List<Posting> target = snapshot.forEvent(reversal.target());

        if (target.isEmpty()) {
            reject(log, reversal, "unknown target");
            return;
        }
        if (!allOn(target, reversal)) {
            reject(log, reversal, "account mismatch");
            return;
        }
        if (snapshot.isReversed(reversal.target())) {
            reject(log, reversal, "already reversed");
            return;
        }
        if (anyKind(target, PostingKind.REVERSAL)) {
            reject(log, reversal, "target is itself a reversal");
            return;
        }
        if (target.stream().anyMatch(p -> p.kind().isDerived())) {
            reject(log, reversal, "target is a derived row");
            return;
        }
        if (target.stream().anyMatch(
                p -> reversal.postingDay().isBefore(p.postingDay()))) {
            reject(log, reversal, "target not yet posted");
            return;
        }

        // FR-24 checked BEFORE anything is written. Validating inside the append loop left a
        // multi-row event half-unwound in an append-only journal when a later row disagreed, which
        // FR-20's wording atomically forbids and no later step could undo.
        for (Posting t : target) {
            if (!t.valueDate().equals(reversal.valueDate())) {
                throw new IllegalStateException(
                        "reversal " + reversal.id() + " states value date "
                                + reversal.valueDate()
                                + " but target row " + t.id() + " is " + t.valueDate());
            }
        }

        // FR-20: one mirror per row
        for (Posting t : target) {
            journal.append(
                    reversal.account(),
                    reversal.postingDay(),
                    t.valueDate(),
                    t.signedAmount().negated(),
                    PostingKind.REVERSAL,
                    reversal.id(),
                    t.id());
        }

        log.record(new Decision.ReversalAccepted(
                reversal.postingDay(),
                reversal.account(),
                reversal.id(),
                reversal.target(),
                target.size()));
    }

    private static boolean allOn(List<Posting> target, LedgerEvent.Reversal r) {
        return target.stream().allMatch(p -> p.account().equals(r.account()));
    }

    private static boolean anyKind(List<Posting> target, PostingKind kind) {
        return target.stream().anyMatch(p -> p.kind() == kind);
    }

    private static void reject(
            DecisionLog log, LedgerEvent.Reversal r, String reason) {
        log.record(new Decision.ReversalRejected(
                r.postingDay(), r.account(), r.id(), r.target(), reason));
    }
}
