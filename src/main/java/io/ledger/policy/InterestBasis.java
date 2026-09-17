// InterestBasis.java
package io.ledger.policy;

import io.ledger.config.LedgerConfig;
import io.ledger.eod.AccrualBook;
import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.List;

/** A-4. Which version of a day's closing balance earns interest. */
public interface InterestBasis {

    void onDayClose(JournalSnapshot snapshot, Account account, Day day, AccrualBook book);

    List<Money> finalAccruals(JournalSnapshot snapshot, Account account, Day lastDay, AccrualBook book);

    String name();

    /** FR-34 .. FR-36. Closing LEDGER balance, positive only, exactly one rounding. */
    static Money accrue(JournalSnapshot snapshot, Account account, Day day) {
        Money closing = snapshot.ledgerBalanceAsOf(account.id(), day, account.currency());
        return closing.isPositive()
                ? closing.multiplyAndRound(LedgerConfig.DAILY_RATE)
                : Money.zero(account.currency());
    }
}