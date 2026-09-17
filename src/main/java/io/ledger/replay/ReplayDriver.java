// ReplayDriver.java
package io.ledger.replay;

import io.ledger.decision.AuthState;
import io.ledger.decision.DecisionLog;
import io.ledger.event.LedgerEvent;
import io.ledger.journal.JournalSnapshot;
import io.ledger.model.Account;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The only loop in the system: for each day, process that day's events, then run the cutover. */
public final class ReplayDriver {

    private final Map<Day, List<LedgerEvent>> stream;
    private final LedgerSession session;

    public ReplayDriver(LedgerSetup setup, Map<Day, List<LedgerEvent>> stream,
                        List<Account> accounts) {
        this.stream = stream;
        this.session = new LedgerSession(setup, accounts);
    }

    public List<DayReport> run() {
        // An event grouped under a day beyond the window was never looked up, never processed and
        // never reported: the run still printed a clean report and a summary. Silence about an
        // event the caller supplied is the worst possible handling, so it is recorded as an error.
        int window = session.setup().windowDays();
        java.util.Set<String> outOfWindow = new java.util.HashSet<>();
        for (Map.Entry<Day, List<LedgerEvent>> e : stream.entrySet()) {
            for (LedgerEvent ev : e.getValue()) {
                String why = null;
                if (ev.postingDay().index() > window) {
                    why = "posting day " + ev.postingDay();
                } else if (ev.valueDate().index() > window) {
                    // A forward-dated row beyond the window never enters any day's closing balance,
                    // so the running total and the value-dated fold disagree and the invariant
                    // aborted the whole replay. That is a data condition (FR-46), not a type
                    // violation, so it is recorded and the run continues without the event.
                    why = "value date " + ev.valueDate();
                }
                if (why == null) continue;
                outOfWindow.add(ev.id().value());
                session.decisions().record(new io.ledger.decision.Decision.ProcessingError(
                        Day.of(Math.min(ev.postingDay().index(), window)), ev.account(), ev.id(),
                        why + " is outside the " + window + "-day window; event not processed"));
            }
        }

        List<DayReport> reports = new ArrayList<>();
        for (int i = 1; i <= session.setup().windowDays(); i++) {
            for (LedgerEvent e : stream.getOrDefault(Day.of(i), List.of())) {
                if (outOfWindow.contains(e.id().value())) continue;
                session.apply(e);
            }
            reports.add(session.closeDay());
        }
        return reports;
    }

    public LedgerSession session()                  { return session; }
    public JournalSnapshot journal()                { return session.journal(); }
    public DecisionLog decisions()                  { return session.decisions(); }
    public AuthState authState(io.ledger.model.AccountId a, AuthId id) { return session.authState(a, id); }
    public Money closing(Account acc)               { return session.closing(acc); }
    public List<Money> valueDatedVector(Account a)  { return session.valueDatedVector(a); }
    public List<Money> interestVector(Account a)    { return session.interestVector(a); }
    public Money netFees(Account acc)               { return session.netFees(acc); }
}