package io.ledger.hold;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Holds are reservations, not money. They move available balance and never the ledger (FR-10).
 *
 * <p>Both lists are append-only and "active" is derived, which is what makes FR-18 true without
 * effort: a release is a new fact, not an edit, so the record of what was reserved on a given day
 * survives every later backdated row.
 */
public final class HoldBook {

    private final List<Hold> placements = new ArrayList<>();
    private final List<HoldRelease> releases = new ArrayList<>();

    public void place(Hold hold) {
        placements.add(hold);
    }

    /**
     * Every lookup is keyed on (account, auth id), never on the auth id alone.
     *
     * <p>An auth id is unique to the account that issued it, not to the world. Keying on the id
     * alone let one account's release free another's hold, and let a settlement find a stranger's
     * hold, and let an account be refused permission to settle its own authorization because some
     * other account had used the same name first.
     */
    public void release(AccountId account, AuthId id, Day day, EventId cause) {
        releases.add(new HoldRelease(account, id, day, cause));
    }

    public boolean isActive(AccountId account, AuthId id) {
        return placements.stream().anyMatch(
                h -> matches(h.account(), h.id(), account, id))
                && releases.stream().noneMatch(
                r -> matches(r.account(), r.id(), account, id));
    }

    public boolean everPlaced(AccountId account, AuthId id) {
        return placements.stream().anyMatch(
                h -> matches(h.account(), h.id(), account, id));
    }

    public Optional<Hold> find(AccountId account, AuthId id) {
        return placements.stream()
                .filter(h -> matches(h.account(), h.id(), account, id))
                .findFirst();
    }

    /** An auth id already active on this account must not be reused: one settlement would free both. */
    public boolean isActiveIdOn(AccountId account, AuthId id) {
        return isActive(account, id);
    }

    private static boolean matches(
            AccountId ha, AuthId hi, AccountId account, AuthId id) {
        return ha.equals(account) && hi.equals(id);
    }

    public Money activeTotal(AccountId account, Currency currency) {
        return placements.stream()
                .filter(h -> h.account().equals(account) && isActive(account, h.id()))
                .map(Hold::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    /** The hold total as it stood at the close of a past day. Never restated. FR-18. */
    public Money totalAtEndOf(AccountId account, Day day, Currency currency) {
        return placements.stream()
                .filter(h -> h.account().equals(account))
                .filter(h -> h.placedOn().isOnOrBefore(day))
                .filter(h -> releases.stream()
                        .noneMatch(r -> r.account().equals(h.account())
                                && r.id().equals(h.id())
                                && r.releasedOn().isOnOrBefore(day)))
                .map(Hold::amount)
                .reduce(Money.zero(currency), Money::plus);
    }

    public List<Hold> placements() {
        return List.copyOf(placements);
    }
}
