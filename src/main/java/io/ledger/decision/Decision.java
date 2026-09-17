package io.ledger.decision;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Money;

/**
 * Approvals, declines, rejections and errors. None of these is money, so none touches the journal.
 *
 * <p>Every variant carries the arithmetic that produced it (FR-48), so the printer renders
 * "Auth-B DECLINED: available -155.00 - 90.00 = -245.00" from stored fields rather than
 * recomputing and risking a different answer than the one actually used.
 */
public sealed interface Decision {

    Day day();

    AccountId account();

    record AuthApproved(
            Day day,
            AccountId account,
            AuthId id,
            Money hold,
            Money availableBefore,
            Money availableAfter
    ) implements Decision {}

    record AuthDeclined(
            Day day,
            AccountId account,
            AuthId id,
            Money hold,
            Money availableBefore,
            Money wouldBe
    ) implements Decision {}

    record SettlementAccepted(
            Day day,
            AccountId account,
            AuthId id,
            Money settled,
            Money holdReleased
    ) implements Decision {}

    record SettlementRejected(
            Day day,
            AccountId account,
            AuthId id,
            Money attempted,
            String reason
    ) implements Decision {}

    record ReversalAccepted(
            Day day,
            AccountId account,
            EventId reversal,
            EventId target,
            int rowsMirrored
    ) implements Decision {}

    record ReversalRejected(
            Day day,
            AccountId account,
            EventId reversal,
            EventId target,
            String reason
    ) implements Decision {}

    record ProcessingError(
            Day day,
            AccountId account,
            EventId event,
            String message
    ) implements Decision {}
}