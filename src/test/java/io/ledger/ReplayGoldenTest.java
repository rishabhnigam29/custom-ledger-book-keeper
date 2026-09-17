package io.ledger;

import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.Day;
import io.ledger.money.Money;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Golden replay: the full six-day output, cell for cell (requirement.md 12)")
class ReplayGoldenTest {

    private final ReplayDriver r = replayDefaults();

    @Test void acc001ClosesAt466_03() { assertEquals(aed("466.03"), r.closing(ACC1)); }

    @Test void acc002ClosesAt10_008() { assertEquals(bhd("10.008"), r.closing(ACC2)); }

    @Test void acc001JournalHas13Rows() {
        assertEquals(13, r.journal().forAccount(ACC1.id()).size());
    }

    @Test void acc002JournalHas4Rows() {
        assertEquals(4, r.journal().forAccount(ACC2.id()).size());
    }

    @Test void tenStreamEventsProduceNineRows_theEngineDerivesEightMore() {
        List<Posting> all = r.journal().rows();
        long derived = all.stream().filter(p -> p.kind() == PostingKind.FEE
                || p.kind() == PostingKind.FEE_REVERSAL
                || p.kind() == PostingKind.INTEREST_CAPITALIZATION).count();
        assertEquals(17, all.size());
        assertEquals(8, derived);                               // 3 fees + 3 reversals + 2 capitalizations
        assertEquals(9, all.size() - derived);
    }

    @Test
    @DisplayName("C1: Day 2 closing at end of Day 5, before any fee, is -370.00")
    void criterionC1() {
        // Rows with value date <= D2 existing by end of Day 5, excluding the fees booked that
        // evening: 1200.00 - 950.00 - 620.00. Holds are not rows; accruals are not rows.
        Money preFee = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.valueDate().isOnOrBefore(Day.of(2)))
                .filter(p -> p.kind() != PostingKind.FEE && p.kind() != PostingKind.FEE_REVERSAL)
                .filter(p -> p.postingDay().isOnOrBefore(Day.of(5)))
                .map(Posting::signedAmount)
                .reduce(Money.zero(ACC1.currency()), Money::plus);
        assertEquals(aed("-370.00"), preFee);
    }

    @Test
    @DisplayName("Fees: three assessed on Day 5, value-dated D2, D4, D5 (refutes C2's 'exactly one on Day 2')")
    void criterionC2IsRefutedByThreeFees() {
        List<Posting> fees = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE).toList();
        assertEquals(3, fees.size());
        assertTrue(fees.stream().allMatch(p -> p.postingDay().equals(Day.of(5))));
        assertEquals(List.of(Day.of(2), Day.of(4), Day.of(5)),
                fees.stream().map(Posting::valueDate).toList());
    }

    @Test
    @DisplayName("The D2 fee cascades: Day 3 falls from 30.00 to 5.00 (FR-29)")
    void feesCascadeIntoLaterDays() {
        Money d3WithoutFees = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.valueDate().isOnOrBefore(Day.of(3)))
                .filter(p -> p.kind() != PostingKind.FEE && p.kind() != PostingKind.FEE_REVERSAL)
                .filter(p -> p.postingDay().isOnOrBefore(Day.of(5)))
                .map(Posting::signedAmount)
                .reduce(Money.zero(ACC1.currency()), Money::plus);
        assertEquals(aed("30.00"), d3WithoutFees);       // and 5.00 once the D2 fee lands
    }

    @Test
    @DisplayName("All three fees reversed on Day 6; net fee movement is zero (FR-30)")
    void feesAreReversedOnceTheOverdraftsDisappear() {
        List<Posting> reversals = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE_REVERSAL).toList();
        assertEquals(3, reversals.size());
        assertTrue(reversals.stream().allMatch(p -> p.postingDay().equals(Day.of(6))));
        assertEquals(List.of(Day.of(2), Day.of(4), Day.of(5)),
                reversals.stream().map(Posting::valueDate).toList());
        assertEquals(aed("0.00"), r.netFees(ACC1));
        // every reversal names the fee row it refunds
        assertTrue(reversals.stream().allMatch(p -> p.reversesPosting() != null));
    }

    @Test void finalValueDatedVectorForAcc001() {
        assertEquals(aedList("250.00", "250.00", "650.00", "465.00", "465.00", "466.03"),
                r.valueDatedVector(ACC1));
    }

    @Test void interestVectorsAndTotals() {
        assertEquals(aedList("0.10", "0.10", "0.26", "0.19", "0.19", "0.19"), r.interestVector(ACC1));
        assertEquals(aed("1.03"),
                r.interestVector(ACC1).stream().reduce(Money.zero(ACC1.currency()), Money::plus));
        assertEquals(bhdList("0.000", "0.000", "0.000", "0.004", "0.004", "0.004"),
                r.interestVector(ACC2));
        assertEquals(bhd("0.008"),
                r.interestVector(ACC2).stream().reduce(Money.zero(ACC2.currency()), Money::plus));
    }

    @Test
    @DisplayName("C8: the capitalized credit is the SUM OF ROUNDED dailies, not the rounded raw sum")
    void criterionC8IsRefutedByTheOneCentGap() {
        Posting cap = r.journal().forAccount(ACC1.id()).stream()
                .filter(p -> p.kind() == PostingKind.INTEREST_CAPITALIZATION)
                .findFirst().orElseThrow();
        assertEquals(aed("1.03"), cap.signedAmount());
        assertEquals(Day.of(6), cap.valueDate());
        // Rounding the raw sum instead: 0.1000+0.1000+0.2600+0.1860+0.1860+0.1860 = 1.0180 -> 1.02.
        // Discarding that 0.01 is exactly what the non-negotiable rule forbids.
        assertNotEquals(aed("1.02"), cap.signedAmount());
    }

    @Test
    @DisplayName("E10 posts three rows sharing one event reference, summing to 10.000 (FR-45)")
    void instalmentsPostAsThreeRows() {
        List<Posting> rows = r.journal().forAccount(ACC2.id()).stream()
                .filter(p -> p.sourceEvent().value().startsWith("E10")).toList();
        assertEquals(3, rows.size());
        assertEquals(bhdList("3.334", "3.333", "3.333"), rows.stream().map(Posting::signedAmount).toList());
        assertEquals(bhd("10.000"),
                rows.stream().map(Posting::signedAmount).reduce(Money.zero(ACC2.currency()), Money::plus));
        assertTrue(rows.stream().allMatch(p -> p.valueDate().equals(Day.of(5))));
    }

    @Test void acc002IsNeverChargedAFee() {
        assertTrue(r.journal().forAccount(ACC2.id()).stream().noneMatch(p -> p.kind() == PostingKind.FEE));
    }

    @Test void replayIsDeterministic_FR7() {
        assertEquals(r.closing(ACC1), replayDefaults().closing(ACC1));
        assertEquals(r.journal().rows().size(), replayDefaults().journal().rows().size());
    }
}
