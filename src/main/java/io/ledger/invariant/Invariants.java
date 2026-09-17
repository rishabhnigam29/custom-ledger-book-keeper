package io.ledger.invariant;

import io.ledger.eod.AccrualBook;
import io.ledger.hold.Hold;
import io.ledger.hold.HoldBook;
import io.ledger.journal.JournalSnapshot;
import io.ledger.journal.Posting;
import io.ledger.journal.PostingKind;
import io.ledger.model.Account;
import io.ledger.model.Day;
import io.ledger.model.PostingId;
import io.ledger.money.Money;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Checked after every cutover. A violation throws rather than producing a wrong report.
 *
 * <p>Every claim the design documents make about this engine is asserted here. An earlier version
 * claimed append-only while only comparing list sizes, which proves a list grew and nothing more.
 */
public final class Invariants {

    private Invariants() {}

    public static void assertAll(JournalSnapshot snapshot,
                                 List<Posting> rowsAtPreviousCutover,
                                 HoldBook holds,
                                 AccrualBook accruals,
                                 List<Account> accounts,
                                 Day today,
                                 int windowDays) {

        appendOnly(snapshot, rowsAtPreviousCutover);
        scalesMatchCurrencies(snapshot);
        identifiersAreUnique(snapshot);
        reversalsNameRealTargetsExactlyOnce(snapshot);

        for (Account account : accounts) {
            List<Posting> rows = snapshot.forAccount(account.id());
            noCrossCurrencyRows(rows, account);
            ledgerIsIndependentOfHolds(rows, holds);
            atMostOneFeePerAssessedDay(rows, account);
            accrualsAreNotJournalRowsBeforeTheFinalDay(rows, today, windowDays);
            capitalizationEqualsTheSumOfRoundedAccruals(rows, accruals, account, windowDays);
            instalmentLegsDifferByAtMostOneMinorUnit(rows, account);
            foldAgreesWithTheRunningTotal(snapshot, account, windowDays);
        }
    }

    /**
     * The real append-only check: every row present at the previous cutover must still be present,
     * in the same position, byte-identical. Comparing sizes only proves the list grew.
     */
    private static void appendOnly(JournalSnapshot snapshot, List<Posting> previous) {
        List<Posting> now = snapshot.rows();
        check(now.size() >= previous.size(),
                "journal shrank: " + now.size() + " < " + previous.size());
        for (int i = 0; i < previous.size(); i++) {
            check(previous.get(i).equals(now.get(i)),
                    "row " + i + " was mutated: was " + previous.get(i) + ", now " + now.get(i));
        }
    }

    private static void scalesMatchCurrencies(JournalSnapshot snapshot) {
        for (Posting p : snapshot.rows()) {
            check(p.signedAmount().amount().scale() == p.signedAmount().currency().scale(),
                    "scale drift on " + p.id() + ": " + p.signedAmount());
        }
    }

    private static void identifiersAreUnique(JournalSnapshot snapshot) {
        Set<PostingId> seen = new HashSet<>();
        for (Posting p : snapshot.rows()) {
            check(seen.add(p.id()), "duplicate posting id " + p.id());
        }
    }

    private static void reversalsNameRealTargetsExactlyOnce(JournalSnapshot snapshot) {
        Set<PostingId> ids = new HashSet<>();
        for (Posting p : snapshot.rows()) ids.add(p.id());

        List<PostingId> alreadyReversed = new ArrayList<>();
        for (Posting p : snapshot.rows()) {
            if (p.reversesPosting() == null) continue;
            check(ids.contains(p.reversesPosting()),
                    p.id() + " reverses unknown row " + p.reversesPosting());
            check(!alreadyReversed.contains(p.reversesPosting()),
                    "row " + p.reversesPosting() + " reversed twice");
            alreadyReversed.add(p.reversesPosting());
        }
    }

    private static void noCrossCurrencyRows(List<Posting> rows, Account account) {
        for (Posting p : rows) {
            check(p.signedAmount().currency() == account.currency(),
                    "currency mismatch on " + p.id() + " for " + account.id()
                            + ": " + p.signedAmount().currency() + " on a " + account.currency() + " account");
        }
    }

    /**
     * No journal row is ever produced by an authorization: holds are not money (FR-10).
     *
     * <p>An earlier version took the HoldBook and never looked at it, asserting something about
     * settlement rows instead. It would have passed even if an authorization had written a ledger
     * row, which is the one thing it is named to catch.
     */
    private static void ledgerIsIndependentOfHolds(List<Posting> rows, HoldBook holds) {
        for (Hold h : holds.placements()) {
            for (Posting p : rows) {
                check(!p.sourceEvent().equals(h.source()),
                        "authorization " + h.source() + " wrote ledger row " + p.id()
                                + "; a hold must never move the ledger");
            }
        }
        for (Posting p : rows) {
            check(p.kind() != PostingKind.SETTLEMENT || p.reversesPosting() == null,
                    "a settlement row must not claim to reverse anything: " + p.id());
        }
    }

    /**
     * "Once per day" is about how many fees STAND for a day, not how many rows the append-only
     * journal has accumulated for it. A day may be charged, refunded and charged again, so the
     * check is that the running balance of fees minus refunds never exceeds one and never goes
     * below zero.
     */
    private static void atMostOneFeePerAssessedDay(List<Posting> rows, Account account) {
        java.util.Map<Day, Integer> outstanding = new java.util.HashMap<>();
        for (Posting p : rows) {
            if (p.kind() == PostingKind.FEE) {
                int n = outstanding.merge(p.valueDate(), 1, Integer::sum);
                check(n <= 1, "two fees outstanding for " + account.id() + " on " + p.valueDate());
            } else if (p.kind() == PostingKind.FEE_REVERSAL) {
                int n = outstanding.merge(p.valueDate(), -1, Integer::sum);
                check(n >= 0, "fee reversal with no fee outstanding on " + p.valueDate());
                // It must refund the fee for ITS OWN value date, not merely some fee. The row ids
                // are a global sequence, so an off-by-one here reads as plausible in the printout.
                Posting refunded = rows.stream()
                        .filter(q -> q.id().equals(p.reversesPosting()))
                        .findFirst().orElseThrow(() -> new IllegalStateException(
                                "INVARIANT VIOLATED: fee reversal " + p.id() + " names no known row"));
                check(refunded.kind() == PostingKind.FEE,
                        "fee reversal " + p.id() + " refunds a " + refunded.kind() + ", not a fee");
                check(refunded.valueDate().equals(p.valueDate()),
                        "fee reversal value-dated " + p.valueDate() + " refunds the fee for "
                                + refunded.valueDate());
            }
        }
    }

    /** FR-13. Accruals are memo records; the only interest row is the final capitalization. */
    private static void accrualsAreNotJournalRowsBeforeTheFinalDay(List<Posting> rows, Day today, int windowDays) {
        for (Posting p : rows) {
            if (p.kind() != PostingKind.INTEREST_CAPITALIZATION) continue;
            check(p.postingDay().index() == windowDays,
                    "interest posted on " + p.postingDay() + ", not the final day");
            check(p.valueDate().index() == windowDays,
                    "interest value-dated " + p.valueDate() + ", not the final day");
        }
        if (today.index() < windowDays) {
            check(rows.stream().noneMatch(p -> p.kind() == PostingKind.INTEREST_CAPITALIZATION),
                    "an interest row exists on " + today + ", before the final day");
        }
    }

    /** FR-39. The credit is the exact sum of the rounded dailies; no remainder is discarded. */
    private static void capitalizationEqualsTheSumOfRoundedAccruals(List<Posting> rows,
                                                                    AccrualBook accruals,
                                                                    Account account,
                                                                    int windowDays) {
        List<Posting> caps = rows.stream()
                .filter(p -> p.kind() == PostingKind.INTEREST_CAPITALIZATION).toList();
        check(caps.size() <= 1, "more than one capitalization for " + account.id());
        if (caps.isEmpty()) return;

        Money expected = accruals
                .vector(account.id(), Day.of(windowDays), account.currency())
                .stream().reduce(Money.zero(account.currency()), Money::plus);
        check(caps.get(0).signedAmount().equals(expected),
                "capitalized " + caps.get(0).signedAmount() + " but the rounded dailies sum to "
                        + expected + " for " + account.id());
    }

    /**
     * FR-44, the half of it an invariant can see. That the legs sum to the EVENT's total is
     * enforced where the total is still in scope, inside LargestRemainderSplitter, which throws
     * rather than returning a split that loses money. From the journal alone the total is not
     * recoverable, so this checks the other half: no leg differs from another by more than one
     * minor unit. Named for what it checks; the previous name claimed the sum check it never made.
     */
    private static void instalmentLegsDifferByAtMostOneMinorUnit(List<Posting> rows, Account account) {
        java.util.Map<String, List<Posting>> byEvent = new java.util.LinkedHashMap<>();
        for (Posting p : rows) {
            if (p.kind() != PostingKind.CREDIT) continue;
            byEvent.computeIfAbsent(p.sourceEvent().value(), k -> new ArrayList<>()).add(p);
        }
        for (var entry : byEvent.entrySet()) {
            List<Posting> legs = entry.getValue();
            if (legs.size() < 2) continue;
            String event = entry.getKey();
            Money max = legs.stream().map(Posting::signedAmount).max(Money::compareTo).orElseThrow();
            Money min = legs.stream().map(Posting::signedAmount).min(Money::compareTo).orElseThrow();
            check(max.minus(min).abs().compareTo(new Money(
                            account.currency().unit(), account.currency())) <= 0,
                    "instalment legs of " + event + " differ by more than one minor unit");
        }
    }

    private static void foldAgreesWithTheRunningTotal(JournalSnapshot snapshot, Account account, int windowDays) {
        Money sum = snapshot.forAccount(account.id()).stream()
                .map(Posting::signedAmount)
                .reduce(Money.zero(account.currency()), Money::plus);
        Money asOfLastDay = snapshot.ledgerBalanceAsOf(account.id(),
                Day.of(windowDays), account.currency());
        check(sum.equals(asOfLastDay),
                "balance disagrees with the value-dated fold for " + account.id()
                        + ": " + sum + " vs " + asOfLastDay);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("INVARIANT VIOLATED: " + message);
    }
}