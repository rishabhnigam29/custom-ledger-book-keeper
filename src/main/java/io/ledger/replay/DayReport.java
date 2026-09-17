package io.ledger.replay;

import io.ledger.journal.Posting;
import io.ledger.model.Day;
import io.ledger.money.Money;

import java.util.List;

public record DayReport(Day day, List<Day> restated, List<AccountView> accounts) {

    /** One account's state at the close of a day, plus what happened to it during the day. */
    public record AccountView(String accountId,
                              List<Posting> journal,
                              List<DayLine> lines,
                              Money closing,
                              Money activeHolds,
                              Money available,
                              List<String> feeActivity,
                              List<String> authStates,
                              List<String> errors) {}

    /** One value-dated day within an account's state table. */
    public record DayLine(Day day, Money closing, Money holds, Money available, Money accrual) {}
}