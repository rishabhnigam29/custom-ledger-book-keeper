// FinalValueDatedBasis.java
package io.ledger.policy;

import io.ledger.eod.AccrualBook;
import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.List;
import java.util.stream.IntStream;

/**
 * DEFAULT. Every day's accrual is recomputed on the settled history at capitalization, so
 * "closing ledger balance" means the same thing in the interest rule as in the fee rule.
 *
 * <p>Each evening writes a PROVISIONAL figure so the per-day report can show the restatement
 * happening; only the Day 6 recomputation is paid.
 */
public final class FinalValueDatedBasis implements InterestBasis {

    @Override
    public void onDayClose(JournalSnapshot snapshot, Account account, Day day, AccrualBook book) {
        for (int i = 1; i <= day.index(); i++) {
            Day d = Day.of(i);
            book.putProvisional(account.id(), d, InterestBasis.accrue(snapshot, account, d));
        }
    }

    @Override
    public List<Money> finalAccruals(JournalSnapshot snapshot, Account account, Day lastDay,
                                     AccrualBook book) {
        return IntStream.rangeClosed(1, lastDay.index())
                .mapToObj(i -> InterestBasis.accrue(snapshot, account, Day.of(i)))
                .toList();
    }

    @Override public String name() { return "final-value-dated"; }
}