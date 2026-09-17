package io.ledger.replay;

import io.ledger.decision.Decision;
import io.ledger.decision.DecisionLog;
import io.ledger.event.LedgerEvent;
import io.ledger.event.ReversalResolver;
import io.ledger.hold.Hold;
import io.ledger.hold.HoldBook;
import io.ledger.journal.Journal;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.AccountId;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.LargestRemainderSplitter;
import io.ledger.money.Money;

import java.util.List;
import java.util.Map;

/**
 * FR-9 .. FR-17. Dispatch by event shape.
 *
 * <p>Java 17 has sealed interfaces and records but pattern-matching switch is still preview there,
 * so this is an instanceof chain rather than a switch over the sealed type.
 */
public final class EventProcessor {

    private final Map<AccountId, Account> accounts;
    private final ReversalResolver reversalResolver = new ReversalResolver();
    /** An event id must name one event; a reversal that found two mirrored both of them. */
    private final java.util.Set<String> seenEventIds = new java.util.HashSet<>();

    public EventProcessor(Map<AccountId, Account> accounts) { this.accounts = accounts; }

    public void process(LedgerEvent event, Journal journal, HoldBook holds, DecisionLog log) {
        Account account = accounts.get(event.account());
        if (account == null) {
            log.record(new Decision.ProcessingError(event.postingDay(), event.account(), event.id(),
                    "unknown account " + event.account()));
            return;
        }
        if (!seenEventIds.add(event.id().value())) {
            log.record(new Decision.ProcessingError(event.postingDay(), event.account(), event.id(),
                    "duplicate event id " + event.id() + "; ids must name one event"));
            return;
        }
        Currency currency = account.currency();

        if (event instanceof LedgerEvent.Credit e) {
            journal.append(e.account(), e.postingDay(), e.valueDate(), e.amount(),
                    PostingKind.CREDIT, e.id(), null);

        } else if (event instanceof LedgerEvent.Debit e) {
            // FR-9. Debits post unconditionally; there is no balance check. That is exactly why
            // the overdraft fee exists as a separate rule.
            journal.append(e.account(), e.postingDay(), e.valueDate(), e.amount().negated(),
                    PostingKind.DEBIT, e.id(), null);

        } else if (event instanceof LedgerEvent.InstalmentCredit e) {
            // FR-45. Three rows, one event reference, appended atomically.
            // FR-45: three rows, ONE event reference. Stamping each leg with a derived id
            // (E10.1, E10.2, E10.3) meant forEvent("E10") found nothing, so an instalment credit
            // could never be reversed -- and the rejection reason claimed the target was unknown.
            List<Money> parts = LargestRemainderSplitter.split(e.total(), e.parts());
            for (Money part : parts) {
                journal.append(e.account(), e.postingDay(), e.valueDate(), part,
                        PostingKind.CREDIT, e.id(), null);
            }

        } else if (event instanceof LedgerEvent.Authorization e) {
            // A hold is a reservation and can only reduce available balance. A negative hold raised
            // available ABOVE the ledger balance and let the account authorize money it did not have.
            if (e.hold().isPositive()) {
                log.record(new Decision.AuthDeclined(e.postingDay(), e.account(), e.authId(),
                        e.hold(), Money.zero(currency), Money.zero(currency)));
                log.record(new Decision.ProcessingError(e.postingDay(), e.account(), e.id(),
                        "authorization hold must be positive, was " + e.hold()));
                return;
            }
            // Reusing an id that is still active on this account would let one settlement free both.
            if (holds.isActive(e.account(), e.authId())) {
                log.record(new Decision.ProcessingError(e.postingDay(), e.account(), e.id(),
                        "authorization " + e.authId() + " is already active on " + e.account()));
                return;
            }
            // FR-14. Available uses postedBalance, everything that has actually arrived, rather
            // than the value-dated balance: a hold is a processing-time question. Identical here,
            // since nothing in the stream is forward-dated. Recorded in AMBIGUITIES.md.
            Money available = journal.snapshot().postedBalance(e.account(), currency)
                    .minus(holds.activeTotal(e.account(), currency));
            Money after = available.minus(e.hold());
            if (!after.isNegative()) {                                           // exactly zero approves
                holds.place(new Hold(e.authId(), e.account(), e.hold(), e.postingDay(), e.id()));
                log.record(new Decision.AuthApproved(e.postingDay(), e.account(), e.authId(), e.hold(),
                        available, after));
            } else {
                log.record(new Decision.AuthDeclined(e.postingDay(), e.account(), e.authId(), e.hold(),
                        available, after));                                      // FR-11: nothing else
            }

        } else if (event instanceof LedgerEvent.Settlement e) {
            if (!holds.isActive(e.account(), e.authId())) {                                   // FR-17
                log.record(new Decision.SettlementRejected(e.postingDay(), e.account(), e.authId(), e.amount(),
                        holds.everPlaced(e.account(), e.authId())
                                ? "authorization already settled"
                                : "no authorization " + e.authId() + " exists"));
                return;                                                             // no row, no hold change
            }
            Hold hold = holds.find(e.account(), e.authId()).orElseThrow();
            // Ownership is now guaranteed by the lookup itself, which is keyed on (account, id).
            // An earlier fix checked it here while still looking the hold up by id alone, which
            // blocked an account from settling its own authorization whenever another account had
            // used the same name first.
            assert hold.account().equals(e.account());
            // A settled amount is money leaving the account. A negative one posted a CREDIT and
            // minted money out of an authorization.
            if (e.amount().isNegative()) {
                log.record(new Decision.SettlementRejected(e.postingDay(), e.account(), e.authId(),
                        e.amount(), "settlement amount must not be negative, was " + e.amount()));
                return;
            }
            // A-14: settling above the hold is rejected, not force-posted. Two documents said so
            // and the code did not check it.
            if (e.amount().compareTo(hold.amount()) > 0) {
                log.record(new Decision.SettlementRejected(e.postingDay(), e.account(), e.authId(),
                        e.amount(), "settlement exceeds the " + hold.amount() + " hold"));
                return;
            }
            Money released = hold.amount();
            holds.release(e.account(), e.authId(), e.postingDay(), e.id());                   // FR-16: in full
            journal.append(e.account(), e.postingDay(), e.valueDate(), e.amount().negated(),
                    PostingKind.SETTLEMENT, e.id(), null);
            log.record(new Decision.SettlementAccepted(e.postingDay(), e.account(), e.authId(), e.amount(),
                    released));

        } else if (event instanceof LedgerEvent.Reversal e) {
            reversalResolver.resolve(e, journal, log);

        } else {
            throw new IllegalStateException("unhandled event type " + event.getClass());
        }
    }
}