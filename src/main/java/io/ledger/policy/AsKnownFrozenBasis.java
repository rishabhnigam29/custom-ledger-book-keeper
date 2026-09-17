// AsKnownFrozenBasis.java
package io.ledger.policy;

import io.ledger.eod.AccrualBook;
import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.List;

/**
 * Each evening's figure is struck once and never revisited, even when a backdated row later
 * contradicts it. Day 2 keeps what it earned on Day 2 although its final balance differs.
 */
public final class AsKnownFrozenBasis implements InterestBasis {

    @Override
    public void onDayClose(JournalSnapshot snapshot, Account account, Day day, AccrualBook book) {
        book.freeze(account.id(), day, InterestBasis.accrue(snapshot, account, day));
    }

    @Override
    public List<Money> finalAccruals(JournalSnapshot snapshot, Account account, Day lastDay,
                                     AccrualBook book) {
        return book.vector(account.id(), lastDay, account.currency());
    }

    @Override public String name() { return "as-known-frozen"; }
}