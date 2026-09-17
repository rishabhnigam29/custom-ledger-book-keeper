package io.ledger;

import io.ledger.decision.AuthState;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.money.Money;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The eight acceptance criteria as executable assertions. REJECTED.md is written from this output,
 * so the verdict table is generated evidence rather than prose.
 */
@DisplayName("Acceptance criteria C1..C8")
class CriteriaVerdictTest {

    private final ReplayDriver r = replayDefaults();

    @Test
    @DisplayName("C1 ACCEPT: Day 2 closing at end of Day 5, before any fee, is -370.00")
    void c1() { assertEquals(aed("-370.00"), preFeeBalanceAsOf(Day.of(2), Day.of(5))); }

    @Test
    @DisplayName("C2 REFUSE: E7 causes THREE fees, valued D2, D4 and D5, not one on Day 2")
    void c2() {
        List<Posting> fees = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE).toList();
        assertNotEquals(1, fees.size(), "C2 claims exactly one fee");
        assertEquals(3, fees.size());
        assertEquals(List.of(Day.of(2), Day.of(4), Day.of(5)),
                fees.stream().map(Posting::valueDate).toList());
    }

    @Test
    @DisplayName("C3 ACCEPT: the Day 4 settlement of Auth-A is accepted")
    void c3() {
        assertEquals(AuthState.SETTLED, r.authState(ACC1.id(), new AuthId("Auth-A")));
        assertEquals(1, r.journal().forEvent(new io.ledger.model.EventId("E5")).size());
    }

    @Test
    @DisplayName("C4 ACCEPT with caveat: unknown-auth settlement rejected; 'in the ledger' misdescribes a hold")
    void c4() {
        assertTrue(r.journal().forEvent(new io.ledger.model.EventId("E6")).isEmpty());
        // Caveat: authorizations live in the HoldBook, not the journal, so the criterion's wording
        // should read "not present in the authorization register".
    }

    @Test
    @DisplayName("C5 ACCEPT with caveat: mechanically true, but VACUOUS because Auth-B is declined")
    void c5() {
        assertEquals(AuthState.DECLINED, r.authState(ACC1.id(), new AuthId("Auth-B")));
        assertTrue(r.journal().forEvent(new io.ledger.model.EventId("E8")).isEmpty());
    }

    @Test
    @DisplayName("C6 ACCEPT: balances, fees and interest all return to their pre-E7 values")
    void c6() {
        assertEquals(aed("0.00"), r.netFees(ACC1));
        assertEquals(aedList("250.00", "250.00", "650.00", "465.00", "465.00", "466.03"),
                r.valueDatedVector(ACC1));
        // What does NOT return is the Auth-B decline, but C6 speaks of balances and fees only.
        assertEquals(AuthState.DECLINED, r.authState(ACC1.id(), new AuthId("Auth-B")));
    }

    @Test
    @DisplayName("C7 REFUSE: three instalments of 3.334 would credit 10.002")
    void c7() {
        List<Money> parts = r.journal().forAccount(ACC2.id()).stream()
                .filter(p -> p.sourceEvent().value().startsWith("E10"))
                .map(Posting::signedAmount).toList();
        assertNotEquals(List.of(bhd("3.334"), bhd("3.334"), bhd("3.334")), parts);
        assertEquals(bhdList("3.334", "3.333", "3.333"), parts);
        assertEquals(new BigDecimal("10.002"), new BigDecimal("3.334").multiply(new BigDecimal("3")));
    }

    @Test
    @DisplayName("C8 REFUSE: the capitalized credit is the sum of rounded dailies, 1.03 not 1.02")
    void c8() {
        Money capitalized = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.kind() == PostingKind.INTEREST_CAPITALIZATION)
                .map(Posting::signedAmount).findFirst().orElseThrow();
        Money sumOfRounded = r.interestVector(ACC1).stream()
                .reduce(Money.zero(ACC1.currency()), Money::plus);
        assertEquals(sumOfRounded, capitalized);
        assertEquals(aed("1.03"), capitalized);
        // The forbidden path: rounding the raw sum 1.0180 gives 1.02. Discarding that 0.01 is
        // precisely what the non-negotiable rule prohibits.
        assertNotEquals(aed("1.02"), capitalized);
    }

    private Money preFeeBalanceAsOf(Day valueDate, Day asOfPostingDay) {
        return r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.valueDate().isOnOrBefore(valueDate))
                .filter(p -> p.postingDay().isOnOrBefore(asOfPostingDay))
                .filter(p -> p.kind() != PostingKind.FEE && p.kind() != PostingKind.FEE_REVERSAL)
                .map(Posting::signedAmount)
                .reduce(Money.zero(ACC1.currency()), Money::plus);
    }
}
