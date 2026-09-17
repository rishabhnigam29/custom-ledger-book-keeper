package io.ledger.journal;

import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.model.PostingId;
import io.ledger.money.Money;

/**
 * One immutable money movement.
 *
 * @param postingDay    when the row entered the journal
 * @param valueDate     which day the money is deemed to have moved; drives balances
 * @param signedAmount  sign carries direction, so balance is a plain sum with no branching
 * @param reversesPosting the row this cancels, or null. FR-21.
 */
public record Posting(PostingId id,
                      AccountId account,
                      Day postingDay,
                      Day valueDate,
                      Money signedAmount,
                      PostingKind kind,
                      EventId sourceEvent,
                      PostingId reversesPosting) {

    public boolean isBackdated() {
        return valueDate.isBefore(postingDay);
    }
}
