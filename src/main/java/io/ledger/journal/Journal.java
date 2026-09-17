package io.ledger.journal;

import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.model.PostingId;
import io.ledger.money.Money;

import java.util.ArrayList;
import java.util.List;

/**
 * The append-only Ledger.
 *
 * <p>FR-3 is enforced by construction: {@link #append} is the only mutator. There is no remove,
 * set, clear or replace method, so "append-only" is a compile-time fact rather than a convention
 * someone has to honour.
 */
public final class Journal {

    private final List<Posting> rows = new ArrayList<>();
    private long seq = 0;

    public PostingId append(AccountId account,
                            Day postingDay,
                            Day valueDate,
                            Money signedAmount,
                            PostingKind kind,
                            EventId sourceEvent,
                            PostingId reversesPosting) {

        PostingId id = new PostingId(++seq);
        rows.add(new Posting(id, account, postingDay, valueDate, signedAmount, kind,
                sourceEvent, reversesPosting));

        return id;
    }

    public JournalSnapshot snapshot() {
        return new JournalSnapshot(List.copyOf(rows));
    }

    public int size() {
        return rows.size();
    }
}
