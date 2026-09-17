package io.ledger.event;

import io.ledger.model.Account;
import io.ledger.model.AccountId;
import io.ledger.model.Day;
import io.ledger.replay.ScenarioLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The canonical stream, read once from {@code brief.json} on the classpath.
 *
 * <p>It used to be written out in Java here, which meant the same ten events existed twice: once
 * as code and once as the scenario file, with only a test keeping them honest. The data now lives
 * in one place and both the CLI and the fixtures go through the same loader.
 *
 * <p>A resource rather than a file on disk, so it travels inside the jar and resolves identically
 * from a test, from {@code java -cp}, and from any working directory.
 *
 * <p>Ordering, FR-8 / A-1: the brief lists E9 ninth and E10 tenth, but E9 posts on Day 6 and E10 on
 * Day 5. Those disagree exactly once. Grouping by POSTING DAY wins, because the output is required
 * per day and a day cannot close before its own events have arrived.
 */
public final class EventStream {

    private static final ScenarioLoader.Scenario CANONICAL =
            ScenarioLoader.loadResource("brief.json");

    public static final AccountId ACC1 = new AccountId("ACC-001");
    public static final AccountId ACC2 = new AccountId("ACC-002");

    private EventStream() {}

    /** The accounts the brief declares, read from the same file as the events. */
    public static List<Account> accounts() {
        return CANONICAL.accounts();
    }

    public static List<LedgerEvent> all() {
        return CANONICAL.events();
    }

    /** Grouped by posting day, stream order preserved within a day. */
    public static Map<Day, List<LedgerEvent>> byPostingDay() {
        return group(all());
    }

    /** For the counterfactual test: the same stream with the named events removed. */
    public static Map<Day, List<LedgerEvent>> without(String... eventIds) {
        Set<String> drop = Set.of(eventIds);
        return group(all().stream()
                .filter(e -> !drop.contains(e.id().value()))
                .toList());
    }

    /**
     * A-1's alternative reading: strict stream order, so E9 precedes E10.
     *
     * <p>Modelled as a LATE ARRIVAL. An event whose stated posting day is earlier than a day the
     * replay has already reached is processed on the day it actually turns up, so its effective
     * processing day is the running maximum. E10 states Day 5 but is listed after E9 on Day 6, so
     * it lands on Day 6 and Day 5 closes without it. Costs ACC-002 0.004 under the frozen interest
     * basis; identical under the default basis.
     */
    public static Map<Day, List<LedgerEvent>> inListedOrder() {
        Map<Day, List<LedgerEvent>> byDay = new TreeMap<>();
        int running = 0;

        for (LedgerEvent e : all()) {
            running = Math.max(running, e.postingDay().index());
            Day arrived = Day.of(running);
            // Restamped, not just re-bucketed: a row must not claim a posting day on which it was
            // not processed. Its value date is untouched, which is what makes it a late arrival.
            byDay.computeIfAbsent(arrived, d -> new ArrayList<>())
                    .add(withPostingDay(e, arrived));
        }

        return byDay;
    }

    /** Restamps an event onto the day it actually arrived. Its value date is untouched. */
    public static LedgerEvent withPostingDay(LedgerEvent e, Day day) {
        if (e.postingDay().equals(day)) return e;

        if (e instanceof LedgerEvent.Credit x)
            return new LedgerEvent.Credit(x.id(), day, x.account(), x.amount(), x.valueDate());

        if (e instanceof LedgerEvent.Debit x)
            return new LedgerEvent.Debit(x.id(), day, x.account(), x.amount(), x.valueDate());

        if (e instanceof LedgerEvent.InstalmentCredit x)
            return new LedgerEvent.InstalmentCredit(
                    x.id(), day, x.account(), x.total(), x.parts(), x.valueDate());

        if (e instanceof LedgerEvent.Authorization x)
            return new LedgerEvent.Authorization(
                    x.id(), day, x.account(), x.authId(), x.hold(), x.valueDate());

        if (e instanceof LedgerEvent.Settlement x)
            return new LedgerEvent.Settlement(
                    x.id(), day, x.account(), x.authId(), x.amount(), x.valueDate());

        if (e instanceof LedgerEvent.Reversal x)
            return new LedgerEvent.Reversal(
                    x.id(), day, x.account(), x.target(), x.valueDate());

        throw new IllegalStateException("unhandled event type " + e.getClass());
    }

    private static Map<Day, List<LedgerEvent>> group(List<LedgerEvent> events) {
        Map<Day, List<LedgerEvent>> byDay = new TreeMap<>();

        for (LedgerEvent e : events) {
            byDay.computeIfAbsent(e.postingDay(), d -> new ArrayList<>()).add(e);
        }

        return byDay;
    }
}
