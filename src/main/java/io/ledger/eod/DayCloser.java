package io.ledger.eod;

import io.ledger.config.LedgerConfig;
import io.ledger.journal.Journal;
import io.ledger.journal.JournalSnapshot;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Money;

/**
 * FR-25 .. FR-32. One component applied to ONE day. Both the restatement, pass and the routine
 * close call it; only the day differs because they are the same principle in two directions:
 * a fee is a function of the current best knowledge of a day's balance.
 */
public final class DayCloser {

    private final boolean reversalEnabled;

    public DayCloser(boolean reversalEnabled) {
        this.reversalEnabled = reversalEnabled;
    }

    public void close(Journal journal, Account account, Day day, Day postingDay) {
        // Taken per day. NOT hoisted outside the caller's loop. That is what makes the cascade
        // work: a fee written for day 0 must be visible when day 0+1 is evaluated (FR-29, FR-31).
        JournalSnapshot snapshot = journal.snapshot();
        Money closing = snapshot.ledgerBalanceAsOf(account.id(), day, account.currency());

        // "Once per day" means at most one fee OUTSTANDING for a day, not one fee ever recorded
        // for it. The journal is append-only, so a refunded fee's row survives forever; keying the
        // guard on the row's existence made a day that had once been charged and refunded immune
        // to assessment for the rest of the window, however negative it later closed. That also
        // broke the symmetry the C6 acceptance rests on: the engine un-assessed on new information
        // but would not re-assess on it.
        int fees = snapshot.feeCount(account.id(), day);
        int refunds = snapshot.feeReversalCount(account.id(), day);
        boolean feeOutstanding = fees > refunds;

        if (closing.isNegative() && !feeOutstanding) { // FR-26: strictly negative
            // Resolved here rather than at the top of the method, so an account in a currency the
            // fee is not defined for only throws when a fee is genuinely about to be charged.
            Money fee = LedgerConfig.overdraftFeeFor(account.currency()); // FR-32
            journal.append(
                    account.id(),
                    postingDay,
                    day,
                    fee.negated(),
                    PostingKind.FEE,
                    EventId.derived("fee-" + day + "#" + (fees + 1)),
                    null);
            return; // never assess and reverse together
        }

        if (reversalEnabled && feeOutstanding) {
            Money fee = LedgerConfig.overdraftFeeFor(account.currency()); // FR-30
            if (!closing.plus(fee).isNegative()) {
                journal.append(
                        account.id(),
                        postingDay,
                        day,
                        fee,
                        PostingKind.FEE_REVERSAL,
                        EventId.derived("rev-" + day + "#" + fees),
                        snapshot.unrefundedFeeRowFor(account.id(), day).orElseThrow());
            }
        }
    }
}
