package io.ledger.eod;

import io.ledger.journal.Journal;
import io.ledger.journal.JournalSnapshot;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.money.Money;
import io.ledger.policy.FeePolicy;
import io.ledger.policy.InterestBasis;

import java.util.ArrayList;
import java.util.List;

/**
 * The cutover. Separates the EXCEPTIONAL path, restating days that late-arriving rows disturbed,
 * from the ROUTINE path, closing today. Restatement runs first so today is judged exactly once
 * against history that will not move again.
 */
public final class DayEndCycle {

    private final FeePolicy feePolicy;
    private final InterestBasis interestBasis;
    private final DayCloser dayCloser;
    private final AccrualBook accrualBook;
    private final int windowDays;
    private final Capitalizer capitalizer = new Capitalizer();

    public DayEndCycle(
            FeePolicy feePolicy,
            InterestBasis interestBasis,
            DayCloser dayCloser,
            AccrualBook accrualBook,
            int windowDays) {
        this.windowDays = windowDays;
        this.feePolicy = feePolicy;
        this.interestBasis = interestBasis;
        this.dayCloser = dayCloser;
        this.accrualBook = accrualBook;
    }

    /** @return the days restated during this cutover, for the report. */
    public List<Day> run(Day processingDay, Journal journal, List<Account> accounts) {
        List<Day> restated = new ArrayList<>();

        for (Account account : accounts) {
            // PASS 0 -- restatement. Conditional, bounded, ASCENDING. The range is derived once,
            // before anything is written; fee rows booked here carry value dates already inside
            // it, so re-deriving would return the same answer (pinned by Invariants).
            JournalSnapshot atCutover = journal.snapshot();
            var derivedBefore =
                    atCutover.earliestBackdatedValueDate(account.id(), processingDay);

            for (Day day :
                    feePolicy.restatementRange(atCutover, account, processingDay)) {
                dayCloser.close(journal, account, day, processingDay);
                restated.add(day);
            }

            // Postcondition, not luck: fee rows written above carry value dates already inside the
            // range they came from, so re-deriving must give the same lower bound. If it widened,
            // the pass would need to run again and the cycle would no longer terminate in one go.
            var derivedAfter = journal.snapshot()
                    .earliestBackdatedValueDate(account.id(), processingDay);
            if (!derivedBefore.equals(derivedAfter)) {
                throw new IllegalStateException(
                        "restatement range moved during its own pass for "
                                + account.id()
                                + " on "
                                + processingDay
                                + ": "
                                + derivedBefore
                                + " -> "
                                + derivedAfter);
            }

            // PASS 1 -- routine close of today.
            dayCloser.close(journal, account, processingDay, processingDay);

            // PASS 2 -- accrual. Memo only; no journal row before Day 6 (FR-13).
            interestBasis.onDayClose(
                    journal.snapshot(), account, processingDay, accrualBook);

            // PASS 3 -- capitalization, final day only (FR-38). Struck on the pre-capitalization
            // balance because final accruals run before capitalize, so FR-40 holds by ordering.
            if (processingDay.index() == windowDays) {
                for (Account accountToCapitalize : accounts) {
                    List<Money> dailies = interestBasis.finalAccruals(
                            journal.snapshot(),
                            accountToCapitalize,
                            processingDay,
                            accrualBook);

                    capitalizer.capitalize(
                            journal,
                            accountToCapitalize,
                            processingDay,
                            dailies);
                }
            }
        }

        return restated;
    }
}
