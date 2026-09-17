package io.ledger.event;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Money;

/** The five event shapes in the stream, plus the instalment variant. */
public sealed interface LedgerEvent {

    EventId id();

    Day postingDay();

    AccountId account();

    Day valueDate();

    record Credit(EventId id, Day postingDay, AccountId account, Money amount, Day valueDate)
            implements LedgerEvent {}

    record Debit(EventId id, Day postingDay, AccountId account, Money amount, Day valueDate)
            implements LedgerEvent {}

    record InstalmentCredit(
            EventId id,
            Day postingDay,
            AccountId account,
            Money total,
            int parts,
            Day valueDate)
            implements LedgerEvent {}

    record Authorization(
            EventId id,
            Day postingDay,
            AccountId account,
            AuthId authId,
            Money hold,
            Day valueDate)
            implements LedgerEvent {}

    record Settlement(
            EventId id,
            Day postingDay,
            AccountId account,
            AuthId authId,
            Money amount,
            Day valueDate)
            implements LedgerEvent {}

    /**
     * FR-19 .. FR-24.
     *
     * <p>FR-19: the target is a REQUIRED constructor argument and there is NO amount field: the
     * amount exists nowhere but on the target. That makes an unlinked or self-describing reversal
     * unconstructible rather than merely invalid, so the rule needs no runtime check.
     */
    record Reversal(
            EventId id,
            Day postingDay,
            AccountId account,
            EventId target,
            Day valueDate)
            implements LedgerEvent {}
}
