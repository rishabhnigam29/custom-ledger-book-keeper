// ReportPrinter.java
package io.ledger.replay;

import io.ledger.journal.Posting;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.List;

/**
 * FR-49, FR-50. The holds column carries the hold active at the close of that day and is never
 * restated, while the closing and accrual columns are. That asymmetry is the output's main job.
 */
public final class ReportPrinter {

    public String render(List<DayReport> reports) {
        StringBuilder out = new StringBuilder();
        for (DayReport r : reports) out.append(render(r));
        return out.toString();
    }

    public String render(DayReport r) {
        StringBuilder b = new StringBuilder();
        b.append("=".repeat(70)).append(" DAY ").append(r.day().index()).append('\n');
        if (!r.restated().isEmpty()) {
            b.append("  restated days: ").append(r.restated()).append('\n');
        }
        for (DayReport.AccountView a : r.accounts()) {
            b.append(" ").append(a.accountId()).append('\n');
            b.append(journalTable(a, r.day()));
            b.append(String.format("    %-5s %14s %10s %14s %10s%n",
                    "Day", "Closing", "Holds", "Available", "Accrual"));
            for (DayReport.DayLine l : a.lines()) {
                b.append(String.format("    %-5s %14s %10s %14s %10s%n",
                        l.day(), l.closing(), l.holds(), l.available(), l.accrual()));
            }
            b.append("    fees   : ")
                    .append(a.feeActivity().isEmpty() ? "none" : String.join(" | ", a.feeActivity()))
                    .append('\n');
            b.append("    auth   : ")
                    .append(a.authStates().isEmpty() ? "none" : String.join(" | ", a.authStates()))
                    .append('\n');
            b.append("    errors : ")
                    .append(a.errors().isEmpty() ? "none" : String.join(" | ", a.errors()))
                    .append('\n');
        }
        return b.toString();
    }

    /**
     * The append-only log itself. Rows appended during the day just closed are marked, so a
     * backdated row is visible as one whose value date precedes its posting day.
     */
    private String journalTable(DayReport.AccountView a, Day today) {
        if (a.journal().isEmpty()) return "    (no ledger rows yet)\n";
        StringBuilder b = new StringBuilder();
        // The id column is the GLOBAL append sequence, shared across accounts, which is why one
        // account's rows have gaps. It has to be the real id: a "reverses #9" reference is
        // unresolvable if the column shows a per-account counter instead.
        b.append(String.format("    %-4s %-7s %-5s %-24s %14s %s%n",
                "id", "posted", "vd", "type", "amount", "ref"));
        Money total = null;
        for (Posting p : a.journal()) {
            total = (total == null) ? p.signedAmount() : total.plus(p.signedAmount());
            String mark = p.postingDay().equals(today) ? " <- new" : "";
            String back = p.isBackdated() ? " BACKDATED" : "";
            String ref = p.sourceEvent().value()
                    + (p.reversesPosting() == null ? "" : " -> reverses " + p.reversesPosting());
            b.append(String.format("    %-4s %-7s %-5s %-24s %14s %s%s%s%n",
                    p.id(), p.postingDay(), p.valueDate(), p.kind(), p.signedAmount().signed(),
                    ref, back, mark));
        }
        b.append(String.format("    %-4s %-7s %-5s %-24s %14s%n", "", "", "", "TOTAL", total));
        return b.toString();
    }

    public static String dayList(List<Day> days) { return days.toString(); }
}