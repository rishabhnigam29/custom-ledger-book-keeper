// RetroactiveFeePolicy.java
package io.ledger.policy;

import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.Day;

import java.util.List;
import java.util.stream.IntStream;

/**
 * DEFAULT. The only reading under which the rule's parenthetical "(all entries with value_date
 * &lt;= that day)" does any work, and the only one that can place a fee on a past day at all.
 *
 * <p>The range is DERIVED at cutover from rows posted today, never registered as they post.
 */
public final class RetroactiveFeePolicy implements FeePolicy {

    @Override
    public List<Day> restatementRange(JournalSnapshot snapshot, Account account, Day processingDay) {
        return snapshot.earliestBackdatedValueDate(account.id(), processingDay)
                .map(day -> IntStream.range(day.index(), processingDay.index())
                        .mapToObj(Day::of)
                        .toList())
                .orElse(List.of());
    }

    @Override public String name() { return "retroactive"; }
}