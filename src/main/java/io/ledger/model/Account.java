package io.ledger.model;

import io.ledger.money.Currency;
import io.ledger.money.Money;

public record Account(AccountId id, Currency currency, Money opening) {

    public Account {
        if (!opening.currency().equals(currency)) {
            throw new IllegalArgumentException("opening balance currency mismatch for " + id);
        }
    }

    public static Account of(String id, Currency currency) {
        return new Account(new AccountId(id), currency, Money.zero(currency));
    }
}
