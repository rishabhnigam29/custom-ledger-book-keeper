// ProcessingDayFeePolicy.java
package io.ledger.policy;

import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;

import java.util.List;

/** Today only. Closed accounting days are never re-opened, whatever arrives afterwards. */
public final class ProcessingDayFeePolicy implements FeePolicy {

    @Override
    public List<Day> restatementRange(JournalSnapshot snapshot, Account account, Day processingDay) {
        return List.of();
    }

    @Override public String name() { return "processing-day"; }
}