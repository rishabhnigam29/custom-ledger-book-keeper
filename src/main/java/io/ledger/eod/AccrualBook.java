package io.ledger.eod;

import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * FR-13. Accruals are MEMO records, never journal rows, until the single Day 6 capitalization.
 *
 * <p>This is not a detail. If accruals posted daily, the Day 2 closing balance at end of Day 5
 * would be -369.80 rather than -370.00, and acceptance criterion C1 would fail for a reason that
 * has nothing to do with backdating.
 */
public final class AccrualBook {

    private record Key(AccountId account, Day day) {}

    private final Map<Key, Money> accruals = new LinkedHashMap<>();

    /** Final-value-dated basis: overwritten each evening until Day 6 settles it. */
    public void putProvisional(AccountId account, Day day, Money rounded) {
        accruals.put(new Key(account, day), rounded);
    }

    /** As-known basis: written once, never revised. */
    public void freeze(AccountId account, Day day, Money rounded) {
        accruals.putIfAbsent(new Key(account, day), rounded);
    }

    public Money get(AccountId account, Day day, Currency currency) {
        return accruals.getOrDefault(new Key(account, day), Money.zero(currency));
    }

    public List<Money> vector(AccountId account, Day lastDay, Currency currency) {
        return IntStream.rangeClosed(1, lastDay.index())
                .mapToObj(i -> get(account, Day.of(i), currency))
                .toList();
    }
}
