package io.ledger.money;

import io.ledger.config.LedgerConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * FR-42 ... FR-44. Splits a total into n parts that sum EXACTLY to the total.
 *
 * <p>Three equal parts of BHD 10.000 do not exist at three decimals: 3 x 3.333 = 9.999 and
 * 3 x 3.334 = 10.002. Something must give, and it is equality rather than the total, because the
 * total is the customer's money. Refutes acceptance criterion C7.
 *
 * <p>{@code RoundingMode.DOWN} truncates toward zero, which is what makes the negative case fall
 * out with no branch: -10.000 yields base -3.333, remainder -0.001, one extra unit of -0.001.
 */
public final class LargestRemainderSplitter {

    private LargestRemainderSplitter() {}

    public static List<Money> split(Money total, int parts) {
        if (parts < 1) throw new IllegalArgumentException("parts must be >= 1, was " + parts);

        Currency currency = total.currency();
        BigDecimal unit = currency.unit();

        BigDecimal base = total.amount()
                .divide(BigDecimal.valueOf(parts), currency.scale(), RoundingMode.DOWN);
        BigDecimal remainder = total.amount().subtract(base.multiply(BigDecimal.valueOf(parts)));
        int extras = remainder.divide(unit, 0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal step = unit.multiply(BigDecimal.valueOf(Integer.signum(extras)));

        List<Money> out = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            boolean carriesExtra = LedgerConfig.REMAINDER_TO_FIRST
                    ? i < Math.abs(extras)
                    : i >= parts - Math.abs(extras);
            out.add(new Money(carriesExtra ? base.add(step) : base, currency));
        }

        Money check = out.stream().reduce(Money.zero(currency), Money::plus);
        if (!check.equals(total)) {
            throw new IllegalStateException("split lost money: " + check + " != " + total);
        }
        return out;
    }
}
