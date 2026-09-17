package io.ledger.replay;

import io.ledger.event.LedgerEvent;
import io.ledger.json.Json;
import io.ledger.model.Account;
import io.ledger.model.AccountId;
import io.ledger.model.AuthId;
import io.ledger.model.Day;
import io.ledger.model.EventId;
import io.ledger.money.Currency;
import io.ledger.money.Money;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Reads a scenario file: policies, accounts and an event stream. See scenarios/README.md. */
public final class ScenarioLoader {

    public record Scenario(String name, LedgerSetup setup, List<Account> accounts,
                           List<LedgerEvent> events, Map<Day, List<LedgerEvent>> stream,
                           int eventCount) {}

    /** From a file on disk: any scenario you write yourself. */
    public static Scenario load(Path file) {
        try {
            return parse(Files.readString(file), file.getFileName().toString());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read scenario " + file, e);
        }
    }

    /**
     * From the classpath. The canonical stream lives here rather than in Java, so there is one
     * source of truth, and as a resource rather than a file so it travels inside the jar and loads
     * the same from a test, the CLI or any working directory.
     */
    public static Scenario loadResource(String resource) {
        try (InputStream in = ScenarioLoader.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("no such classpath resource: " + resource);
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read classpath resource " + resource, e);
        }
    }

    private static Scenario parse(String text, String fallbackName) {
        Map<String, Object> root = Json.object(Json.parse(text));
        String name = Json.strOrNull(root, "name") == null ? fallbackName : Json.str(root, "name");

        Map<String, Object> pol = root.containsKey("policies")
                ? Json.object(root.get("policies")) : Map.of();
        LedgerSetup setup = LedgerSetup.of(
                pol.containsKey("feePolicy") ? Json.str(pol, "feePolicy") : "retroactive",
                pol.containsKey("interestBasis") ? Json.str(pol, "interestBasis") : "final-value-dated",
                Json.boolOr(pol, "feeReversal", true),
                Json.intOr(pol, "windowDays", 6));

        List<Account> accounts = new ArrayList<>();
        Map<AccountId, Currency> currencyOf = new java.util.LinkedHashMap<>();
        for (Object a : Json.array(root.get("accounts"))) {
            Map<String, Object> o = Json.object(a);
            AccountId id = new AccountId(Json.str(o, "id"));
            Currency ccy = Currency.valueOf(Json.str(o, "currency"));
            String opening = Json.strOrNull(o, "opening");
            if (currencyOf.containsKey(id)) {
                throw new IllegalArgumentException("account " + id + " is declared more than once");
            }
            accounts.add(new Account(id, ccy,
                    opening == null ? Money.zero(ccy) : Money.of(opening, ccy)));
            currencyOf.put(id, ccy);
        }

        List<LedgerEvent> events = new ArrayList<>();
        Map<Day, List<LedgerEvent>> stream = new TreeMap<>();
        for (Object e : Json.array(root.get("events"))) {
            LedgerEvent event = event(Json.object(e), currencyOf, Day.of(1));
            events.add(event);
            stream.computeIfAbsent(event.postingDay(), day -> new ArrayList<>()).add(event);
        }
        return new Scenario(name, setup, accounts, List.copyOf(events), stream, events.size());
    }

    /**
     * One event, from a single JSON object. Lets the console accept a line lifted straight out of a
     * scenario file, so the same text works in a file and at the prompt.
     */
    public static LedgerEvent parseEvent(String json, List<Account> accounts, Day defaultDay) {
        String trimmed = json.trim();
        if (trimmed.endsWith(",")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        Map<AccountId, Currency> currencyOf = new java.util.LinkedHashMap<>();
        for (Account a : accounts) currencyOf.put(a.id(), a.currency());
        return event(Json.object(Json.parse(trimmed)), currencyOf, defaultDay);
    }

    private static LedgerEvent event(Map<String, Object> o, Map<AccountId, Currency> currencyOf,
                                     Day defaultDay) {
        EventId id = new EventId(Json.str(o, "id"));
        Day posting = Day.of(Json.intOr(o, "postingDay", defaultDay.index()));
        AccountId account = new AccountId(Json.str(o, "account"));
        Day valueDate = Day.of(Json.intOr(o, "valueDate", posting.index()));

        // An undeclared account is a data problem, not a type violation: the event is carried
        // through and EventProcessor records "unknown account" so the rest of the stream still runs.
        Currency ccy = currencyOf.get(account);
        if (ccy == null) ccy = currencyOf.values().stream().findFirst().orElse(Currency.AED);

        String type = Json.str(o, "type").toUpperCase().replace('-', '_');
        return switch (type) {
            case "CREDIT" -> new LedgerEvent.Credit(id, posting, account,
                    Money.of(Json.str(o, "amount"), ccy), valueDate);
            case "DEBIT" -> new LedgerEvent.Debit(id, posting, account,
                    Money.of(Json.str(o, "amount"), ccy), valueDate);
            case "INSTALMENT_CREDIT" -> new LedgerEvent.InstalmentCredit(id, posting, account,
                    Money.of(Json.str(o, "amount"), ccy), Json.intAt(o, "parts"), valueDate);
            case "AUTHORIZATION" -> new LedgerEvent.Authorization(id, posting, account,
                    new AuthId(Json.str(o, "authId")), Money.of(Json.str(o, "amount"), ccy), valueDate);
            case "SETTLEMENT" -> new LedgerEvent.Settlement(id, posting, account,
                    new AuthId(Json.str(o, "authId")), Money.of(Json.str(o, "amount"), ccy), valueDate);
            case "REVERSAL" -> new LedgerEvent.Reversal(id, posting, account,
                    new EventId(Json.str(o, "target")), valueDate);
            default -> throw new IllegalArgumentException("event " + id + " has unknown type \"" + type
                    + "\". Expected CREDIT, DEBIT, INSTALMENT_CREDIT, AUTHORIZATION, SETTLEMENT or REVERSAL.");
        };
    }

    private ScenarioLoader() {}
}