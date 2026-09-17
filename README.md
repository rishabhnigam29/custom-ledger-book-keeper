# In-Memory Account Ledger Core

Replays a fixed ten-event stream across Day 1 to Day 6 for two accounts in different currencies, and prints per day the closing ledger balance, active holds, available balance, interest accrual, fee activity, authorization states and errors.

No web layer, no persistence, no database, no user interface. Java 17, Maven, JUnit 5, `BigDecimal`.

## Type events yourself

```bash
./run.sh console
```

An interactive session. Type one event, and the ledger and every account print immediately. Days advance only when you say `eod`, so you can see exactly what the end of day adds on top of what you typed.

You can also paste **one event as JSON**, in exactly the shape `brief.json` uses, trailing comma and whitespace are tolerated:

```json
{"id": "E1", "type": "CREDIT", "postingDay": 1, "account": "ACC-001", "amount": "1200.00", "valueDate": 1}
```

Omit `postingDay` to use the current day. `help` lists the syntax. To step the exercise's own ten events **one at a time**, type `next` repeatedly: it applies exactly one event and stops, closing the day for you when the next event belongs to a later day. `brief` finishes the rest, `brief fires all ten at once.

The whole exercise, typed by hand:

```text
credit ACC-001 1200.00
debit ACC-001 950.00
eod
eod
credit ACC-001 400.00
eod
eod
debit ACC-001 620.00 vd 2    <- backdated: days 2, 4, and 5 go negative retrospectively
eod
reverse ACC-001 U4           <- fee refunded, every balance where it would have been
eod
```

## Run it by hand, without Maven

Maven is needed only for the test suite. Everything else is plain `javac` and `java`.

```bash
./run.sh console                 # interactive
./run.sh build                   # javac -> out/
./run.sh replay                  # the built-in stream
./run.sh replay processing-day as-known-frozen false
./run.sh scenario scenarios/no-backdating.json  # your own data
./run.sh scenarios               # every scenario, one summary table
```

Or without the script at all:

```bash
javac -d out $(find src/main/java -name '*.java')
cp -R src/main/resources/. out/          # brief.json is loaded from the classpath
java -cp out io.ledger.Console
java -cp out io.ledger.replay.Main --replay      # whole window at once
java -cp out io.ledger.replay.JsonReplay scenarios/no-backdating.json
```

`./run.sh scenarios` prints:

```text
SCENARIO                 ACCOUNT    CLOSING    INTEREST    NET FEES    ROWS
net-zero-correction      ACC-001    250.00     0.00       0.00        5
no-backdating             ACC-001    466.03     1.03       0.00        5
overdraft-cascade         ACC-001     84.06     0.06      -25.00        5
```

The brief's own stream is not in that list because it is not a file in `scenarios/`; run it with `./run.sh replay` or `java -cp out io.ledger.replay.JsonReplay` with no argument.

The first two rows are the arguments worth carrying into a defence. `no-backdating` deletes the backdated debit and its reversal and lands on the same `466.03`, which is why criterion C6 stands. `net-zero-correction` has two cancelling backdated rows on one day and writes **no fee at all**, which per-row restatement would not manage.

## Run it with Maven

```bash
mvn test                         # 89 tests: golden replay, units, criteria, scenarios, invariants
mvn exec:java -Dexec.args=replay # prints the six day blocks and the final balances
mvn exec:java                    # opens the interactive console (no args = console)
mvn test -Pfailing               # runs ONLY the deliberately-failing test. Expected RED.
mvn test -Dtest=PolicyMatrixTest  # prints all policy combinations
mvn package -DskipTests && java -jar target/ledger-core-1.0.0.jar  # the brief's own stream
```

`mvn exec:java` accepts three optional arguments to change the switches:

```bash
mvn exec:java -Dexec.args="processing-day as-known-frozen false"
```

## Run it on your own data

```bash
mvn -q exec:java -Dexec.mainClass=io.ledger.replay.JsonReplay  # the brief's own stream
```

The brief's own stream lives in `src/main/resources/brief.json` and runs with no argument. Three further scenarios ship in `scenarios/`; each is asserted by `ScenarioLoaderTest` so they cannot rot. `scenarios/README.md` has the format, the event types and a list of things worth trying. There is no JSON dependency: the parser is a hundred hand-written lines reading numbers as `BigDecimal`.

## The answer

| Account | Currency | Closes at | Interest | Net fees | Journal rows |
|---|---|---:|---:|---:|---:|
| ACC-001 | AED, 2 dp | **466.03** | 1.03 | 0.00 | 13 |
| ACC-002 | BHD, 3 dp | **10.008** | 0.008 | 0.00 | 4 |

## Reading the output

Each day prints one block per account. The table shows **every value-dated day so far**, not just today, and prints a backdated entry rewrites days that have already closed.

```text
Day    Closing    Holds    Available    Accrual
D1       250.00     0.00       250.00      0.10
D2      -395.00   200.00      -595.00      0.00
```

| Column | Meaning | Restated by a backdated entry |
|---|---|---|
| Closing | Sum of rows with value date at or before that day | **yes** |
| Holds | The hold active at the close of that day | **no** |
| Available | Closing minus holds | follows Closing |
| Accrual | That day's interest, a memo until day 6 | **yes** |

That asymmetry is the point of the exercise. The ledger column is value-dated and gets rewritten; the holds column is a processing-time fact and never does. At the close of Day 6 the Day 2 row still shows a 200.00 hold, because on Day 2 that hold really was there.

Watch Day 5: one backdated debit turns three already-closed days negative, three fees are booked, and one of them cascades into Day 3. Then watch Day 6: the reversal and the three fee refunds put every balance back exactly where it would have been had the debit never happened, using only new rows.

## Documents

| File | What it holds |
|---|---|
| `eventUnderstanding.md` | Event-by-event walkthrough: the immutable ledger and account state after every single event. Start here |
| `requirement.md` | Fifty numbered requirements, the eight criteria verdicts, the ambiguity register, the expected output |
| `HLD.md` | **The brief's Architecture & Trade-offs document.** The two timelines, the day-end cycle, trade-offs and rejected alternatives |
| `LLD.md` | Class-level specification, algorithms, test inventory, implementation order |
| `NUMBERS.md` | Every constant and what value |
| `AMBIGUITIES.md` | Every ambiguity found and how it was resolved |
| `REJECTED.md` | Criteria verified, and approaches abandoned mid-build |
| `WORKLOG.md` | Timestamped record of the work |

## Design in one paragraph

Money, reservations and decisions live in separate append-only stores: `Journal`, `HoldBook`, `DecisionLog`. No balance is ever stored; `LedgerBalanceAsOf` is always a fold, because a cached balance would go stale the instant a backdated row arrived and the staleness would be invisible. The day-end cycle separates **restatement**, re-closing days that late arrivals disturbed, from the **routine close** of today, and the restatement range is derived from the journal at cutover rather than registered as events post. Fee assessment and fee reversal are one component applied to one day, because they are the same principle in two directions.
