package io.ledger.decision;

import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;

import java.util.ArrayList;
import java.util.List;

/** Append-only. Authorization state is derived by scanning, so no status field can drift. */
public final class DecisionLog {

    private final List<Decision> decisions = new ArrayList<>();

    public void record(Decision decision) { decisions.add(decision); }

    public List<Decision> all() { return List.copyOf(decisions); }

    public List<Decision> on(Day day) {
        return decisions.stream().filter(d -> d.day().equals(day)).toList();
    }

    /** Errors, as opposed to ordinary declines. A decline is a decision, not an error. */
    public List<Decision> errorsOn(Day day, AccountId account) {
        return on(day).stream()
                .filter(d -> d.account().equals(account))
                .filter(d -> d instanceof Decision.SettlementRejected
                        || d instanceof Decision.ReversalRejected
                        || d instanceof Decision.ProcessingError)
                .toList();
    }

    /**
     * FR-15. Derived from the record; a later backdated row never rewrites it.
     *
     * <p>Scoped by account: an auth id belongs to the account that issued it. Without the scope, a
     * settled authorization flipped back to APPROVED_ACTIVE as soon as another account reused the
     * same id, which broke decision finality for a reason that had nothing to do with the account.
     */
    public AuthState stateOf(AccountId account, AuthId id) {
        AuthState state = AuthState.UNKNOWN;
        for (Decision d : decisions) {
            if (!d.account().equals(account)) continue;
            if (d instanceof Decision.AuthApproved a && a.id().equals(id)) {
                state = AuthState.APPROVED_ACTIVE;
            } else if (d instanceof Decision.AuthDeclined x && x.id().equals(id)) {
                state = AuthState.DECLINED;
            } else if (d instanceof Decision.SettlementAccepted s && s.id().equals(id)) {
                state = AuthState.SETTLED;
            }
        }
        return state;
    }

    /** State as it stood at the close of a given day, for the per-day report. */
    public AuthState stateOfAsOf(AccountId account, AuthId id, Day day) {
        AuthState state = AuthState.UNKNOWN;
        for (Decision d : decisions) {
            if (!d.day().isOnOrBefore(day) || !d.account().equals(account)) continue;
            if (d instanceof Decision.AuthApproved a && a.id().equals(id)) {
                state = AuthState.APPROVED_ACTIVE;
            } else if (d instanceof Decision.AuthDeclined x && x.id().equals(id)) {
                state = AuthState.DECLINED;
            } else if (d instanceof Decision.SettlementAccepted s && s.id().equals(id)) {
                state = AuthState.SETTLED;
            }
        }
        return state;
    }

    public List<AuthId> knownAuthsAsOf(Day day, AccountId account) {
        List<AuthId> ids = new ArrayList<>();
        for (Decision d : decisions) {
            if (!d.day().isOnOrBefore(day) || !d.account().equals(account)) continue;
            AuthId id = null;
            if (d instanceof Decision.AuthApproved a)      id = a.id();
            else if (d instanceof Decision.AuthDeclined x) id = x.id();
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        return ids;
    }
}