package io.ledger.hold;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;

public record HoldRelease(
        AccountId account,
        AuthId id,
        Day releasedOn,
        EventId cause) {
}
