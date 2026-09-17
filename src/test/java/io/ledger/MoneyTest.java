// MoneyTest.java
package io.ledger;

import io.ledger.money.Currency;
import io.ledger.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Money: scale, currency and rounding (FR-5, FR-6, FR-37, FR-47)")
class MoneyTest {

    @Test void amountMustCarryItsCurrencyScale_FR5() {
        assertThrows(IllegalArgumentException.class,
                () -> new Money(new BigDecimal("1.5"), Currency.AED));      // scale 1, needs 2
        assertDoesNotThrow(() -> new Money(new BigDecimal("1.50"), Currency.AED));
        assertDoesNotThrow(() -> new Money(new BigDecimal("1.500"), Currency.BHD));
    }

    @Test void literalWithTooMuchPrecisionThrowsRatherThanRoundingSilently_FR47() {
        assertThrows(ArithmeticException.class, () -> Money.of("10.005", Currency.AED));
        assertEquals("10.00", Money.of("10", Currency.AED).toString());
    }

    @Test void crossCurrencyArithmeticIsImpossible_FR6() {
        Money aed = Money.of("1.00", Currency.AED);
        Money bhd = Money.of("1.000", Currency.BHD);
        assertThrows(IllegalArgumentException.class, () -> aed.plus(bhd));
        assertThrows(IllegalArgumentException.class, () -> aed.minus(bhd));
        assertThrows(IllegalArgumentException.class, () -> aed.compareTo(bhd));
    }

    @Test void accrualRoundsExactlyOnceAtTheAccountsPrecision_FR37() {
        // The real Day 4 figure: 465.00 x 0.0004 = 0.186000 -> 0.19
        assertEquals(Money.of("0.19", Currency.AED),
                Money.of("465.00", Currency.AED).multiplyAndRound(new BigDecimal("0.0004")));
        // BHD keeps what AED loses: 10.000 x 0.0004 = 0.0040000 -> 0.004
        assertEquals(Money.of("0.004", Currency.BHD),
                Money.of("10.000", Currency.BHD).multiplyAndRound(new BigDecimal("0.0004")));
    }

    @Test
    @DisplayName("HALF_UP vs HALF_EVEN: indistinguishable in this stream, so pinned on a constructed value")
    void roundingModeIsPinnedOnAConstructedHalf_A10() {
        // No product in the real stream lands on an exact half, so the choice is unobservable there.
        // 12.50 x 0.0004 = 0.005000 exactly: HALF_UP gives 0.01, HALF_EVEN would give 0.00.
        BigDecimal raw = new BigDecimal("12.50").multiply(new BigDecimal("0.0004"));
        assertEquals(new BigDecimal("0.01"), raw.setScale(2, RoundingMode.HALF_UP));
        assertEquals(new BigDecimal("0.00"), raw.setScale(2, RoundingMode.HALF_EVEN));
        assertEquals(Money.of("0.01", Currency.AED),
                Money.of("12.50", Currency.AED).multiplyAndRound(new BigDecimal("0.0004")));
    }
}