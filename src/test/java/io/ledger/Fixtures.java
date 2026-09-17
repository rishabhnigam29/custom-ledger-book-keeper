// Fixtures.java
package io.ledger;

import io.ledger.event.EventStream;
import io.ledger.model.Account;
import io.ledger.money.Currency;
import io.ledger.money.Money;
import io.ledger.replay.LedgerSetup;
import io.ledger.replay.ReplayDriver;

import java.util.List;

final class Fixtures {

    static final List<Account> ACCOUNTS = EventStream.accounts();
    static final Account ACC1 = ACCOUNTS.get(0);
    static final Account ACC2 = ACCOUNTS.get(1);

    private Fixtures() {}

    static ReplayDriver replayDefaults() {
        ReplayDriver d = new ReplayDriver(LedgerSetup.defaults(), EventStream.byPostingDay(), ACCOUNTS);
        d.run();
        return d;
    }

    static java.util.List<io.ledger.replay.DayReport> reportsDefaults() {
        return new ReplayDriver(LedgerSetup.defaults(), EventStream.byPostingDay(), ACCOUNTS).run();
    }

    static io.ledger.replay.DayReport.AccountView view(
            java.util.List<io.ledger.replay.DayReport> reports, int day, Account account) {
        return reports.get(day - 1).accounts().stream()
                .filter(v -> v.accountId().equals(account.id().value()))
                .findFirst().orElseThrow();
    }

    static ReplayDriver replayStream(LedgerSetup setup,
                                     java.util.Map<io.ledger.model.Day,
                                             java.util.List<io.ledger.event.LedgerEvent>> stream) {
        ReplayDriver d = new ReplayDriver(setup, stream, ACCOUNTS);
        d.run();
        return d;
    }

    static ReplayDriver replay(LedgerSetup setup) {
        ReplayDriver d = new ReplayDriver(setup, EventStream.byPostingDay(), ACCOUNTS);
        d.run();
        return d;
    }

    static Money aed(String a) { return Money.of(a, Currency.AED); }
    static Money bhd(String a) { return Money.of(a, Currency.BHD); }

    static List<Money> aedList(String... amounts) {
        return List.of(amounts).stream().map(Fixtures::aed).toList();
    }

    static List<Money> bhdList(String... amounts) {
        return List.of(amounts).stream().map(Fixtures::bhd).toList();
    }
}