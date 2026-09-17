package io.ledger.replay;

import io.ledger.decision.AuthState;
import io.ledger.decision.Decision;
import io.ledger.decision.DecisionLog;
import io.ledger.eod.AccrualBook;
import io.ledger.eod.DayCloser;
import io.ledger.eod.DayEndCycle;
import io.ledger.event.LedgerEvent;
import io.ledger.hold.HoldBook;
import io.ledger.invariant.Invariants;
import io.ledger.journal.JournalSnapshot;
import io.ledger.journal.Journal;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.io.Console;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One replay in progress: the four stores, the processor and the cutover, driven one step at a time.
 *
 * <p>Extracted so the same engine serves a whole-window batch run ({@link ReplayDriver}) and an
 * interactive session ({@link Console}) without either owning a private copy of the wiring.
 */
public final class LedgerSession {

    private final LedgerSetup setup;
    private final List<Account> accounts;
    private final Map<AccountId, Account> byId = new LinkedHashMap<>();

    private final Journal journal = new Journal();
    private final HoldBook holds = new HoldBook();
    private final DecisionLog decisions = new DecisionLog();
    private final AccrualBook accruals = new AccrualBook();

    private final EventProcessor processor;
    private final DayEndCycle cycle;

    private List<Posting> rowsAtPreviousCutover = List.of();
    private Day today = Day.of(1);

    public LedgerSession(LedgerSetup setup, List<Account> accounts) {
        this.setup = setup;
        this.accounts = accounts;
        for (Account a : accounts) byId.put(a.id(), a);

        for (Account account : accounts) {
            // FR-52
            if (!account.opening().isZero()) {
                journal.append(account.id(), Day.of(1), Day.of(1), account.opening(),
                        PostingKind.OPENING, EventId.derived("opening"), null);
            }
        }
        this.processor = new EventProcessor(byId);
        this.cycle = new DayEndCycle(setup.feePolicy(), setup.interestBasis(),
                new DayCloser(setup.feeReversal()), accruals, setup.windowDays());
    }

    public Day today()               { return today; }
    public LedgerSetup setup()       { return setup; }
    public List<Account> accounts()  { return accounts; }
    public JournalSnapshot journal() { return journal.snapshot(); }
    public DecisionLog decisions()   { return decisions; }
    public AccrualBook accruals()    { return accruals; }
    public HoldBook holds()          { return holds; }

    public void apply(LedgerEvent event) { processor.process(event, journal, holds, decisions); }

    /** Runs the cutover for today, checks the invariants, and returns the day's report. */
    public DayReport closeDay() {
        List<Day> restated = cycle.run(today, journal, accounts);
        JournalSnapshot after = journal.snapshot();
        Invariants.assertAll(after, rowsAtPreviousCutover, holds, accruals, accounts, today,
                setup.windowDays());
        rowsAtPreviousCutover = after.rows();
        DayReport report = report(restated);
        today = Day.of(today.index() + 1);
        return report;
    }

    /** The current picture without closing anything: what the console prints after each event. */
    public DayReport report() { return report(List.of()); }

    private DayReport report(List<Day> restated) {
        JournalSnapshot s = journal.snapshot();
        List<DayReport.AccountView> views = new ArrayList<>();

        for (Account acc : accounts) {
            Currency c = acc.currency();
            List<DayReport.DayLine> lines = new ArrayList<>();
            for (int i = 1; i <= today.index(); i++) {
                Day d = Day.of(i);
                Money closing = s.ledgerBalanceAsOf(acc.id(), d, c);
                Money held = holds.totalAtEndOf(acc.id(), d, c);
                lines.add(new DayReport.DayLine(d, closing, held, closing.minus(held),
                        accruals.get(acc.id(), d, c)));
            }

            List<String> fees = new ArrayList<>();
            for (Posting p : s.forAccount(acc.id())) {
                if (!p.postingDay().equals(today)) continue;
                if (p.kind() == PostingKind.FEE) {
                    fees.add("FEE " + p.signedAmount().signed() + " vd " + p.valueDate());
                } else if (p.kind() == PostingKind.FEE_REVERSAL) {
                    fees.add("FEE_REVERSAL " + p.signedAmount().signed() + " vd " + p.valueDate());
                }
            }

            List<String> auths = new ArrayList<>();
            for (AuthId id : decisions.knownAuthsAsOf(today, acc.id())) {
                auths.add(id + ": " + decisions.stateOfAsOf(acc.id(), id, today));
            }

            List<String> errors = new ArrayList<>();
            for (Decision d : decisions.errorsOn(today, acc.id())) errors.add(describe(d));

            Money closing = s.ledgerBalanceAsOf(acc.id(), today, c);
            Money active = holds.activeTotal(acc.id(), c);
            views.add(new DayReport.AccountView(acc.id().value(), s.forAccount(acc.id()), lines,
                    closing, active, closing.minus(active), fees, auths, errors));
        }
        return new DayReport(today, restated, views);
    }

    // ---- queries used by the CLI and the tests ----------------------------------------------------------

    public AuthState authState(AccountId account, AuthId id) { return decisions.stateOf(account, id); }

    public Money closing(Account acc) {
        return journal.snapshot()
                .ledgerBalanceAsOf(acc.id(), Day.of(setup.windowDays()), acc.currency());
    }

    public List<Money> valueDatedVector(Account acc) {
        JournalSnapshot s = journal.snapshot();
        List<Money> v = new ArrayList<>();
        for (int i = 1; i <= setup.windowDays(); i++) {
            v.add(s.ledgerBalanceAsOf(acc.id(), Day.of(i), acc.currency()));
        }
        return v;
    }

    public List<Money> interestVector(Account acc) {
        return accruals.vector(acc.id(), Day.of(setup.windowDays()), acc.currency());
    }

    public Money netFees(Account acc) {
        return journal.snapshot().forAccount(acc.id()).stream()
                .filter(p -> p.kind() == PostingKind.FEE || p.kind() == PostingKind.FEE_REVERSAL)
                .map(Posting::signedAmount)
                .reduce(Money.zero(acc.currency()), Money::plus);
    }

    static String describe(Decision d) {
        if (d instanceof Decision.SettlementRejected r) {
            return "settlement on " + r.id() + " for " + r.attempted() + " rejected: " + r.reason()
                    + "; no funds moved";
        }
        if (d instanceof Decision.ReversalRejected r) {
            return "reversal " + r.reversal() + " of " + r.target() + " rejected: " + r.reason();
        }
        if (d instanceof Decision.ProcessingError e) {
            return e.event() + ": " + e.message();
        }
        return d.toString();
    }
}