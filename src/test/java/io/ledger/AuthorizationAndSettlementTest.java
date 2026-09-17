package io.ledger;

import io.ledger.decision.AuthState;
import io.ledger.decision.Decision;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.money.Money;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Holds, authorization and settlement (FR-10..FR-18, C3, C4, C5)")
class AuthorizationAndSettlementTest {

    private final ReplayDriver r = replayDefaults();

    @Test
    @DisplayName("C5: an approved hold moves available and NOT the ledger")
    void approvedAuthorizationWritesNoLedgerRow() {
        Decision.AuthApproved a = r.decisions().all().stream()
                .filter(d -> d instanceof Decision.AuthApproved)
                .map(d -> (Decision.AuthApproved) d).findFirst().orElseThrow();
        assertEquals(new AuthId("Auth-A"), a.id());
        assertEquals(aed("250.00"), a.availableBefore());
        assertEquals(aed("50.00"), a.availableAfter());     // 250.00 - 200.00, still >= 0
        // no journal row anywhere carries an authorization as its source
        assertTrue(r.journal().forEvent(new io.ledger.model.EventId("E3")).isEmpty());
    }

    @Test
    @DisplayName("C5 is vacuous: Auth-B is DECLINED, so its antecedent is false")
    void authBIsDeclinedAndNeverReEvaluated() {
        Decision.AuthDeclined d = r.decisions().all().stream()
                .filter(x -> x instanceof Decision.AuthDeclined)
                .map(x -> (Decision.AuthDeclined) x).findFirst().orElseThrow();
        assertEquals(new AuthId("Auth-B"), d.id());
        assertEquals(aed("-155.00"), d.availableBefore());
        assertEquals(aed("-245.00"), d.wouldBe());          // -155.00 - 90.00 < 0
        // FR-15: still declined at the end, although the account closes at 466.03
        assertEquals(AuthState.DECLINED, r.authState(ACC1.id(), new AuthId("Auth-B")));
    }

    @Test
    @DisplayName("C3: settlement releases the hold IN FULL, not just the settled amount")
    void settlementReleasesTheWholeHold() {
        Decision.SettlementAccepted s = r.decisions().all().stream()
                .filter(x -> x instanceof Decision.SettlementAccepted)
                .map(x -> (Decision.SettlementAccepted) x).findFirst().orElseThrow();
        assertEquals(aed("185.00"), s.settled());
        assertEquals(aed("200.00"), s.holdReleased());      // the residual 15.00 was never money
        assertEquals(AuthState.SETTLED, r.authState(ACC1.id(), new AuthId("Auth-A")));
    }

    @Test
    @DisplayName("C4: an unknown authorization is rejected and the balance is byte-identical")
    void unknownAuthorizationSettlementMovesNothing() {
        Decision.SettlementRejected rej = r.decisions().all().stream()
                .filter(x -> x instanceof Decision.SettlementRejected)
                .map(x -> (Decision.SettlementRejected) x).findFirst().orElseThrow();
        assertEquals(new AuthId("Auth-Z"), rej.id());
        assertEquals(Day.of(4), rej.day());
        // E6 produced no row at all
        assertTrue(r.journal().forEvent(new io.ledger.model.EventId("E6")).isEmpty());
        // and Day 4 closes exactly where E5 left it
        assertEquals(aed("465.00"),
                r.journal().ledgerBalanceAsOf(ACC1.id(), Day.of(4), ACC1.currency())
                        .minus(feesValuedOn(Day.of(4))));
    }

    private Money feesValuedOn(Day day) {
        return r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.valueDate().equals(day))
                .filter(p -> p.kind() == PostingKind.FEE || p.kind() == PostingKind.FEE_REVERSAL)
                .map(Posting::signedAmount)
                .reduce(Money.zero(ACC1.currency()), Money::plus);
    }

    @Test
    @DisplayName("Available RISES on the settlement debit, 450.00 -> 465.00")
    void availableRisesOnADebitBecauseTheHoldOutweighsIt() {
        var reports = reportsDefaults();

        // Day 3, before the settlement: the hold is still active.
        var day3 = view(reports, 3, ACC1);
        assertEquals(aed("650.00"), day3.closing());
        assertEquals(aed("200.00"), day3.activeHolds());
        assertEquals(aed("450.00"), day3.available());

        // Day 4, after settling 185.00 against that 200.00 hold. The ledger FALLS by 185.00 while
        // available RISES by 15.00, because releasing the reservation outweighs the spend. The
        // residual 15.00 was never money, so there is nothing to give back.
        var day4 = view(reports, 4, ACC1);
        assertEquals(aed("465.00"), day4.closing());
        assertEquals(aed("0.00"), day4.activeHolds());
        assertEquals(aed("465.00"), day4.available());

        assertTrue(day4.closing().compareTo(day3.closing()) < 0, "the ledger must fall on a debit");
        assertTrue(day4.available().compareTo(day3.available()) > 0, "available must rise");
        assertEquals(aed("15.00"), day4.available().minus(day3.available()));
    }
}
