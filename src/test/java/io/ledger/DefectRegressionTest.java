package io.ledger;

import io.ledger.decision.AuthState;
import io.ledger.decision.Decision;
import io.ledger.event.LedgerEvent;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;
import io.ledger.replay.LedgerSetup;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * One test per defect an external review reproduced. None of these exercised by the brief's own
 * stream, which is exactly why they survived: an unreachable path with no test is where behaviour
 * and documentation drift apart silently.
 */
@DisplayName("Regressions for defects found in review")
class DefectRegressionTest {

    private static final Account A1 = Account.of("ACC-001", Currency.AED);
    private static final Account A2 = Account.of("ACC-002", Currency.AED);
    private static Money aed(String a) { return Money.of(a, Currency.AED); }

    private static ReplayDriver run(int window, List<Account> accounts, LedgerEvent... events) {
        Map<Day, List<LedgerEvent>> stream = new TreeMap<>();
        for (LedgerEvent e : events) {
            stream.computeIfAbsent(e.postingDay(), d -> new ArrayList<>()).add(e);
        }
        ReplayDriver d = new ReplayDriver(
                LedgerSetup.of("retroactive", "final-value-dated", true, window), stream, accounts);
        d.run();
        return d;
    }

    private static LedgerEvent credit(String id, int day, Account a, String amt, int vd) {
        return new LedgerEvent.Credit(new EventId(id), Day.of(day), a.id(), aed(amt), Day.of(vd));
    }
    private static LedgerEvent debit(String id, int day, Account a, String amt, int vd) {
        return new LedgerEvent.Debit(new EventId(id), Day.of(day), a.id(), aed(amt), Day.of(vd));
    }

    @Test
    @DisplayName("a day whose fee was refunded is charged again when it closes negative again")
    void refundedDayIsNotPermanentlyImmune() {
        ReplayDriver r = run(6, List.of(A1),
                credit("A1", 1, A1, "100.00", 1),
                debit("A2", 3, A1, "200.00", 1),   // D1 -> -100.00, fee booked
                credit("A3", 4, A1, "300.00", 1),  // cured, fee refunded
                debit("A4", 5, A1, "400.00", 1));  // D1 negative AGAIN

        List<Posting> d1Fees = r.journal().forAccount(A1.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE && p.valueDate().equals(Day.of(1))).toList();
        assertEquals(2, d1Fees.size(), "Day 1 went negative twice, so it must be charged twice");
        assertEquals(1, r.journal().forAccount(A1.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE_REVERSAL && p.valueDate().equals(Day.of(1)))
                .count(), "and refunded once, leaving exactly one fee outstanding");
    }

    @Test
    @DisplayName("one account cannot settle another account's authorization")
    void settlementIsScopedToTheAccountThatPlacedTheHold() {
        ReplayDriver r = run(3, List.of(A1, A2),
                credit("B1", 1, A1, "500.00", 1),
                new LedgerEvent.Authorization(new EventId("B2"), Day.of(1), A1.id(),
                        new AuthId("Auth-A"), aed("200.00"), Day.of(1)),
                credit("B3", 1, A2, "500.00", 1),
                new LedgerEvent.Settlement(new EventId("B4"), Day.of(2), A2.id(),
                        new AuthId("Auth-A"), aed("100.00"), Day.of(2)));

        assertTrue(r.journal().forEvent(new EventId("B4")).isEmpty(), "no row may be posted");
        assertTrue(r.decisions().all().stream().anyMatch(d -> d instanceof Decision.SettlementRejected),
                "and it must be recorded as a rejection");
        // ACC-001's hold survives untouched: the stranger's settlement freed nothing.
        assertEquals(AuthState.APPROVED_ACTIVE, r.authState(A1.id(), new AuthId("Auth-A")));
    }

    @Test
    @DisplayName("an account CAN settle its own authorization even when another account used the same id")
    void ownAuthorizationIsSettleableDespiteAnIdClash() {
        ReplayDriver r = run(3, List.of(A1, A2),
                credit("F1", 1, A1, "500.00", 1),
                new LedgerEvent.Authorization(new EventId("F2"), Day.of(1), A1.id(),
                        new AuthId("Auth-A"), aed("200.00"), Day.of(1)),
                credit("F3", 1, A2, "500.00", 1),
                new LedgerEvent.Authorization(new EventId("F4"), Day.of(1), A2.id(),
                        new AuthId("Auth-A"), aed("20.00"), Day.of(1)),
                new LedgerEvent.Settlement(new EventId("F5"), Day.of(2), A2.id(),
                        new AuthId("Auth-A"), aed("20.00"), Day.of(2)));

        // ACC-002 settles ITS OWN Auth-A. An earlier fix looked the hold up by id alone and refused
        // this, because ACC-001 had used the name first.
        assertEquals(1, r.journal().forEvent(new EventId("F5")).size(), "ACC-002's settlement must post");
        assertEquals(AuthState.SETTLED, r.authState(A2.id(), new AuthId("Auth-A")));
        // and ACC-001's identically-named hold is untouched
        assertEquals(AuthState.APPROVED_ACTIVE, r.authState(A1.id(), new AuthId("Auth-A")));
    }

    @Test
    @DisplayName("a negative settlement amount is rejected rather than posting a credit")
    void negativeSettlementCannotMintMoney() {
        ReplayDriver r = run(3, List.of(A1),
                credit("G1", 1, A1, "100.00", 1),
                new LedgerEvent.Authorization(new EventId("G2"), Day.of(1), A1.id(),
                        new AuthId("Auth-N"), aed("50.00"), Day.of(1)),
                new LedgerEvent.Settlement(new EventId("G3"), Day.of(2), A1.id(),
                        new AuthId("Auth-N"), aed("-1000000.00"), Day.of(2)));

        assertTrue(r.journal().forEvent(new EventId("G3")).isEmpty());
        assertTrue(r.closing(A1).compareTo(aed("200.00")) < 0, "no money may be minted");
    }

    @Test
    @DisplayName("a negative authorization hold is rejected rather than raising available above the ledger")
    void negativeHoldCannotRaiseAvailable() {
        ReplayDriver r = run(3, List.of(A1),
                credit("H1", 1, A1, "100.00", 1),
                new LedgerEvent.Authorization(new EventId("H2"), Day.of(1), A1.id(),
                        new AuthId("Auth-Neg"), aed("-50.00"), Day.of(1)),
                new LedgerEvent.Authorization(new EventId("H3"), Day.of(1), A1.id(),
                        new AuthId("Auth-Big"), aed("140.00"), Day.of(1)));

        // With the negative hold refused, available stays 100.00 and the 140.00 hold cannot pass.
        assertEquals(AuthState.DECLINED, r.authState(A1.id(), new AuthId("Auth-Big")));
    }

    @Test
    @DisplayName("an auth id already active on the account cannot be reused")
    void anActiveAuthIdCannotBeReusedOnTheSameAccount() {
        ReplayDriver r = run(3, List.of(A1),
                credit("I1", 1, A1, "100.00", 1),
                new LedgerEvent.Authorization(new EventId("I2"), Day.of(1), A1.id(),
                        new AuthId("Auth-D"), aed("10.00"), Day.of(1)),
                new LedgerEvent.Authorization(new EventId("I3"), Day.of(2), A1.id(),
                        new AuthId("Auth-D"), aed("20.00"), Day.of(2)),
                new LedgerEvent.Settlement(new EventId("I4"), Day.of(3), A1.id(),
                        new AuthId("Auth-D"), aed("5.00"), Day.of(3)));

        // WITHOUT this, one settlement released BOTH holds, freeing 30.00 of reservation for a 5.00 spend.
        assertTrue(r.decisions().all().stream().anyMatch(d -> d instanceof Decision.ProcessingError e
                && e.message().contains("already active")));
    }

    @Test
    @DisplayName("a settlement above its hold is rejected, as AMBIGUITIES A-14 states")
    void overSettlementIsRejected() {
        ReplayDriver r = run(3, List.of(A1),
                credit("C1", 1, A1, "1000.00", 1),
                new LedgerEvent.Authorization(new EventId("C2"), Day.of(1), A1.id(),
                        new AuthId("Auth-Q"), aed("200.00"), Day.of(1)),
                new LedgerEvent.Settlement(new EventId("C3"), Day.of(2), A1.id(),
                        new AuthId("Auth-Q"), aed("900.00"), Day.of(2)));

        assertTrue(r.journal().forEvent(new EventId("C3")).isEmpty());
        assertTrue(r.decisions().all().stream().anyMatch(d -> d instanceof Decision.SettlementRejected s
                && s.reason().contains("exceeds")));
    }

    @Test
    @DisplayName("an instalment credit is reversible: FR-45's 'one event reference' must be real")
    void instalmentCreditCanBeReversedAsOneUnit() {
        Account bhd = Account.of("ACC-002", Currency.BHD);
        Map<Day, List<LedgerEvent>> stream = new TreeMap<>();
        stream.put(Day.of(1), List.of(new LedgerEvent.InstalmentCredit(new EventId("D1"), Day.of(1),
                bhd.id(), Money.of("10.000", Currency.BHD), 3, Day.of(1))));
        stream.put(Day.of(2), List.of(new LedgerEvent.Reversal(new EventId("D2"), Day.of(2),
                bhd.id(), new EventId("D1"), Day.of(1))));
        ReplayDriver r = new ReplayDriver(
                LedgerSetup.of("retroactive", "final-value-dated", true, 3), stream, List.of(bhd));
        r.run();

        assertEquals(3, r.journal().forAccount(bhd.id()).stream()
                        .filter(p -> p.kind() == PostingKind.REVERSAL).count(),
                "all three legs must unwind, atomically");
        assertEquals(Money.zero(Currency.BHD), r.closing(bhd));
    }

    @Test
    @DisplayName("an event outside the window is reported, not silently dropped")
    void outOfWindowEventIsReported() {
        ReplayDriver r = run(3, List.of(A1),
                credit("E1", 1, A1, "500.00", 1),
                debit("E2", 9, A1, "400.00", 9));

        assertTrue(r.journal().forEvent(new EventId("E2")).isEmpty());
        assertTrue(r.decisions().all().stream().anyMatch(d -> d instanceof Decision.ProcessingError e
                && e.message().contains("outside the")), "the caller must be told it was ignored");
    }

    @Test
    @DisplayName("a value date beyond the window is recorded, not fatal to the whole replay")
    void forwardDatedBeyondWindowIsRecordedNotFatal() {
        ReplayDriver r = assertDoesNotThrow(() -> run(3, List.of(A1),
                credit("J1", 1, A1, "100.00", 1),
                credit("J2", 1, A1, "50.00", 8)));    // value date outside the 3-day window

        assertTrue(r.journal().forEvent(new EventId("J2")).isEmpty());
        assertTrue(r.decisions().all().stream().anyMatch(d -> d instanceof Decision.ProcessingError e
                && e.message().contains("value date 08")));
        // Day 2, before the final day's capitalization, so only the surviving credit is in play.
        assertEquals(aed("100.00"), r.journal()
                .ledgerBalanceAsOf(A1.id(), Day.of(2), Currency.AED));
    }

    @Test
    @DisplayName("a duplicate event id is rejected, so a reversal can never mirror two events")
    void duplicateEventIdIsRejected() {
        ReplayDriver r = run(3, List.of(A1),
                credit("K0", 1, A1, "500.00", 1),
                credit("K1", 1, A1, "100.00", 1)
                // ... rest of method truncated in image ...
        );
    }
}