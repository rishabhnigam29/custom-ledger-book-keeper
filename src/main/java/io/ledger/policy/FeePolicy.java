// FeePolicy.java
package io.ledger.policy;

import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;

import java.util.List;

/**
 * A-2. Which past days, if any, are re-closed before today's routine close.
 *
 * <p>The two implementations are the two readings of "assessed when that day's closing ledger
 * balance is negative". Neither changes how an event is processed; both act only at cutover.
 */
public interface FeePolicy {

    /** Days to re-close before the routine close of {@code processingDay}, ascending. */
    List<Day> restatementRange(JournalSnapshot snapshot, Account account, Day processingDay);

    String name();
}