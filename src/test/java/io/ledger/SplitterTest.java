package io.ledger;

import io.ledger.money.Currency;
import io.ledger.money.LargestRemainderSplitter;
import io.ledger.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Instalment split: total preserved, equality sacrificed (FR-42..FR-44, refutes C7)")
class SplitterTest {

    private static Money bhd(String a) { return Money.of(a, Currency.BHD); }

    @Test void everySplitSumsExactlyAndSpreadsAtMostOneMinorUnit() {
        record Case(String total, int parts) {}
        for (Case c : List.of(new Case("10.000", 3), new Case("9.999", 3), new Case("0.002", 3),
                new Case("1.000", 1), new Case("-10.000", 3), new Case("10.000", 7))) {
            Money total = bhd(c.total());
            List<Money> parts = LargestRemainderSplitter.split(total, c.parts());
            assertEquals(c.parts(), parts.size(), c.total());
            assertEquals(total, parts.stream().reduce(Money.zero(Currency.BHD), Money::plus), c.total());
            Money max = parts.stream().max(Money::compareTo).orElseThrow();
            Money min = parts.stream().min(Money::compareTo).orElseThrow();
            assertTrue(max.minus(min).abs().compareTo(bhd("0.001")) <= 0, "spread on " + c.total());
        }
    }

    @Test void negativeTotalsAreSignSymmetric() {
        assertEquals(List.of(bhd("-3.334"), bhd("-3.333"), bhd("-3.333")),
                LargestRemainderSplitter.split(bhd("-10.000"), 3));
    }

    @Test void aDivisibleTotalSplitsGenuinelyEqually() {
        assertEquals(List.of(bhd("3.333"), bhd("3.333"), bhd("3.333")),
                LargestRemainderSplitter.split(bhd("9.999"), 3));
    }

    @Test void rejectsNonPositivePartCount() {
        assertThrows(IllegalArgumentException.class,
                () -> LargestRemainderSplitter.split(bhd("10.000"), 0));
    }

    @Test void theRealCase_threeInstalmentsOf10_000() {
        assertEquals(List.of(bhd("3.334"), bhd("3.333"), bhd("3.333")),
                LargestRemainderSplitter.split(bhd("10.000"), 3));
    }

    @Test
    @DisplayName("C7 is arithmetically impossible: 3 x 3.334 = 10.002")
    void criterionC7OverCreditsBy0_002() {
        BigDecimal three334 = new BigDecimal("3.334").multiply(new BigDecimal("3"));
        assertEquals(new BigDecimal("10.002"), three334);      // over by 0.002
        assertEquals(new BigDecimal("9.999"),
                new BigDecimal("3.333").multiply(new BigDecimal("3")));      // under by 0.001
        // No equal three-way split at 3 decimals reaches 10.000, so equality must give.
    }
}
