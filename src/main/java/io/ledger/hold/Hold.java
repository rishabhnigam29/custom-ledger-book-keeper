package io.ledger.hold;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Money;

public record Hold(AuthId id, AccountId account, Money amount, Day placedOn, EventId source) {
}
