package io.ledger.journal;

import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.model.PostingId;
import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Immutable read model. Reporting can only see a snapshot, so a printer can never write a row. */
public final class JournalSnapshot {

    private final List<Posting> rows;

    JournalSnapshot(List<Posting> rows) { this.rows = rows; }

    public List<Posting> rows() { return rows; }

    public List<Posting> forAccount(AccountId account) {
        return rows.stream().filter(p -> p.account().equals(account)).toList();
    }

    /**
     * FR-1. The closing ledger balance of a day: every row whose value date is at or before it.
     * Always a fold, never cached -- a backdated row would silently invalidate a cache.
     */
    public Money ledgerBalanceAsOf(AccountId account, Day valueDate, Currency currency) {
        return rows.stream()
                .filter(p -> p.account().equals(account))
                .filter(p -> p.valueDate().isOnOrBefore(valueDate))
                .map(Posting::signedAmount)
                .reduce(Money.zero(currency), Money::plus);
    }

    /** Everything posted so far regardless of value date. Feeds available balance (FR-4). */
    public Money postedBalance(AccountId account, Currency currency) {
        return rows.stream()
                .filter(p -> p.account().equals(account))
                .map(Posting::signedAmount)
                .reduce(Money.zero(currency), Money::plus);
    }

    /**
     * The restatement range's lower bound. DERIVED from the journal at cutover, never registered
     * during the day: ten backdated rows for the same past day simply produce the same minimum ten
     * times, so deduplication is structural. See HLD 6.3 and A-11.
     */
    public Optional<Day> earliestBackdatedValueDate(AccountId account, Day postingDay) {
        return rows.stream()
                .filter(p -> p.account().equals(account))
                .filter(p -> p.postingDay().equals(postingDay))
                .map(Posting::valueDate)
                .filter(vd -> vd.isBefore(postingDay))
                .min(Comparator.naturalOrder());
    }

    public boolean feeAssessedFor(AccountId account, Day valueDate) {
        return hasKindOn(account, valueDate, PostingKind.FEE);
    }

    public boolean feeReversedFor(AccountId account, Day valueDate) {
        return hasKindOn(account, valueDate, PostingKind.FEE_REVERSAL);
    }

    public int feeCount(AccountId account, Day valueDate) {
        return (int) rows.stream()
                .filter(p -> p.account().equals(account) && p.valueDate().equals(valueDate)
                        && p.kind() == PostingKind.FEE)
                .count();
    }

    public int feeReversalCount(AccountId account, Day valueDate) {
        return (int) rows.stream()
                .filter(p -> p.account().equals(account) && p.valueDate().equals(valueDate)
                        && p.kind() == PostingKind.FEE_REVERSAL)
                .count();
    }

    /** The oldest fee row for this day that no reversal has yet refunded. */
    public Optional<PostingId> unrefundedFeeRowFor(AccountId account, Day valueDate) {
        List<PostingId> refunded = rows.stream()
                .filter(p -> p.kind() == PostingKind.FEE_REVERSAL && p.reversesPosting() != null)
                .map(Posting::reversesPosting)
                .toList();

        return rows.stream()
                .filter(p -> p.account().equals(account) && p.valueDate().equals(valueDate)
                        && p.kind() == PostingKind.FEE)
                .map(Posting::id)
                .filter(id -> !refunded.contains(id))
                .findFirst();
    }

    public Optional<PostingId> feeRowFor(AccountId account, Day valueDate) {
        return rows.stream()
                .filter(p -> p.account().equals(account))
                .filter(p -> p.valueDate().equals(valueDate))
                .filter(p -> p.kind() == PostingKind.FEE)
                .map(Posting::id)
                .findFirst();
    }

    public List<Posting> forEvent(EventId event) {
        return rows.stream().filter(p -> p.sourceEvent().equals(event)).toList();
    }

    /** FR-23. True once any row of the target event has been mirrored. */
    public boolean isReversed(EventId target) {
        List<PostingId> targetRows = forEvent(target).stream().map(Posting::id).toList();
        return rows.stream()
                .filter(p -> p.reversesPosting() != null)
                .anyMatch(p -> targetRows.contains(p.reversesPosting()));
    }

    public List<Posting> postedOn(Day day) {
        return rows.stream().filter(p -> p.postingDay().equals(day)).toList();
    }

    private boolean hasKindOn(AccountId account, Day valueDate, PostingKind kind) {
        return rows.stream()
                .anyMatch(p -> p.account().equals(account)
                        && p.valueDate().equals(valueDate)
                        && p.kind() == kind);
    }
}