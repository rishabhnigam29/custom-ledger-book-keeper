package io.ledger.eod;

import io.ledger.journal.Journal;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Money;

import java.util.List;

/** FR-38 .. FR-40. One credit per account, equal to the exact sum of the rounded dailies. */
public final class Capitalizer {

    public Money capitalize(Journal journal, Account account, Day day, List<Money> dailies) {
        Money total = dailies.stream()
                .reduce(Money.zero(account.currency()), Money::plus);

        // There is deliberately NO setScale here. The total is a sum of already-rounded values, so
        // it is exact by construction and FR-39 cannot be violated by accident. Re-rounding is the
        // mistake criterion C8 invites: it would give 1.02 against the correct 1.03.
        if (total.isZero()) return total;

        journal.append(
                account.id(),
                day,
                day,
                total,
                PostingKind.INTEREST_CAPITALIZATION,
                EventId.derived("capitalization"),
                null);

        return total;
    }
}
