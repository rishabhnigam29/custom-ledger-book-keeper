# Architecture & Trade-offs – In-Memory Account Ledger Core

*This is the brief's Deliverable 2, the Architecture & Trade-offs document. The event-by-event walkthrough it argues from is in `eventUnderstanding.md`.*

| Status | Implemented; 89 tests green, replay produces the canonical output below |
|---|---|
| Scope | In-memory ledger core replaying a fixed six-day event stream over two accounts |
| Stack | Java 17, Maven, JUnit 5, `BigDecimal`. No framework, no persistence, no I/O beyond stdout |
| Canonical output | ACC-001 closes at AED 466.03, ACC-002 at BHD 10.008 |

---

## 1. Purpose and scope

Build a ledger core that replays ten events across Day 1 to Day 6 for two accounts in different currencies, and prints per day the closing ledger balance, fee assessments, authorization states and errors.

The hard part is not the arithmetic. It is that two events are **backdated**: they are posted on one day but belong to an earlier day. Every interesting design decision follows from that.

### In scope

Value-dated balances, overdraft fee assessment and reversal, daily interest accrual and capitalization, authorization holds and settlement, event reversal, multi-currency precision, an append-only journal, and a replay harness that prints and asserts.

### Explicitly out of scope

No web layer, persistence, database or user interface. No concurrency: replay is single-threaded and deterministic by construction. No calendar, no day-count convention, no business-day rules; days are integers 1 to 6. No FX, no cross-currency movement. No partial settlement or hold expiry. Since the stream exercises neither, though both are noted as extension points in §11.

---

## 2. The central domain concept: two timelines

Every event carries two dates, and conflating them is the failure mode this design exists to prevent.

```text
POSTING DAY  — when the system saw the event
             — drives holds, authorization decisions, "what did we know then"
             — monotonic, never revised

VALUE DATE   — which day the money is deemed to have moved
             — drives balances, fees, interest
             — a day's balance is NOT frozen when that day ends
```

The rules define a day's closing balance as **all entries with `value_date ≤ that day`**. So a new row can rewrite the past:

```text
             posted D5, value-dated D2              posted D6, value-dated D2
                     |                                      |
D1    D2    D3    D4    D5    D6                 D2 <---
     <-------------------->

Day 2 closes at 250.00 on Day 2,
at -370.00 on Day 5,
and at 250.00 again on Day 6.
```

**Design consequence.** No balance is ever stored. `ledgerBalanceAsOf(valueDate)` is always a fold over the journal. A cached or incrementally-maintained balance would silently go stale the moment a backdated row arrives, and the staleness would be invisible. Recomputation is O(rows) per query, which for a six-day, seventeen-row problem is free, and the honesty is worth far more than the cycles.

---

## 3. Three kinds of record, deliberately separated

The single most load-bearing structural decision is that money, reservations and decisions live in different stores.

| Store | Holds | Moves ledger balance | Moves available balance | Append-only |
|---|---|---|---|---|
| **Journal** | Postings: credit, debit, settlement, fee, fee reversal, reversal, capitalized interest | yes | yes | yes |
| **HoldBook** | Hold placements and releases | no | yes, while active | yes |
| **DecisionLog** | Approvals, declines, rejections, errors | no | no | yes |
| **AccrualBook** | Daily interest memos | no | no | yes |

`available = ledgerBalance - sum(activeHolds)`

Two acceptance criteria probe exactly this separation, so it is not incidental structure. An approved authorization writes nothing to the journal. A rejected settlement writes nothing to the journal. An interest accrual writes nothing to the journal until the single Day 6 capitalization.

That last point is subtle and worth stating explicitly: **if accruals were journal rows, the Day 2 closing balance at end of Day 5 would be -369.80 rather than -370.00, and an acceptance criterion would fail for a reason that has nothing to do with backdating.**

### 3.1 Every hold in the stream, with its arithmetic

Two authorizations are requested and a third identifier appears only on a settlement. All three are decided by one rule: **approve if and only if `available - hold ≥ 0`**, evaluated at the instant the event is replayed.

| Auth | Event | Day | Requested | Ledger at decision | Holds before | Available before | Available if held | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---|
| Auth-A | E3 | 2 | 200.00 | 250.00 | 0.00 | 250.00 | 50.00 | **APPROVED** |
| Auth-B | E8 | 5 | 90.00 | -155.00 | 0.00 | -155.00 | -245.00 | **DECLINED** |
| Auth-Z | — | — | — | — | — | — | — | **NEVER EXISTED** — no authorization event was ever replayed for it |

### Hold state at the close of each day

| Day | Auth-A | Auth-B | Active holds | ACC-001 ledger | Available | What moved it |
|---:|---|---|---:|---:|---:|---|
| 1 | not yet requested | not yet requested | 0.00 | 250.00 | 250.00 | E1, E2 |
| 2 | **ACTIVE 200.00** | not yet requested | **200.00** | 250.00 | **50.00** | E3 approved |
| 3 | **ACTIVE 200.00** | not yet requested | **200.00** | 650.00 | **450.00** | E4 credit |
| 4 | RELEASED in full | not yet requested | 0.00 | 465.00 | 465.00 | E5 credit |
| 5 | released | DECLINED, no hold | 0.00 | -230.00 | -230.00 | E7, E8 declined, three fees |
| 6 | released | still declined | 0.00 | 465.00 | 465.00 | E9, three fee reversals |

### 3.2 Four things this table is designed to make obvious

**An approved hold moves available and nothing else.** On Day 2 the ledger reads 250.00 before and after E3. Only the available column moves, to 50.00. That is the whole content of one acceptance criterion, and here it is a column, not a claim.

**Settlement releases the hold in full, not the settled amount.** E5 settles 185.00 against a 200.00 hold. The hold is released entirely and a 185.00 debit is posted. The residual 15.00 was a reservation, never money, so there is nothing to return.

**Available can rise on a debit.** Across E5 the ledger falls 650.00 → 465.00 while available rises 450.00 → 465.00, a gain of exactly 15.00. Releasing a 200.00 reservation outweighs spending 185.00. This is the single most counter-intuitive line in the replay and the one most likely to be probed.

**Auth-B fails on ordering alone.** E7 and E8 are both Day 5 events, and E7 is replayed first. Had the order been reversed, available would have been 465.00, the hold would have left 375.00, and Auth-B would have been approved. Nothing about the amounts caused the decline; the intra-day sequence did.

### 3.3 Why the hold column is never restated

The state table in the per-event walkthrough carries a holds column beside the ledger column, and the two behave differently on purpose.

```text
backdated row arrives

        ┌───────────────┐
        │ LEDGER column │  rewritten for every affected day
        │               │  (a day's balance is whatever the rows now say)
        └───────────────┘
        │
        └── HOLDS column    untouched
                           (on that day, that hold really was there)
```

A hold is a processing-time fact about what was reserved at a moment. A balance is a value-dated fact about where money sits. Backdating changes the second and cannot change the first. At the close of Day 6 the Day 2 row still shows a 200.00 hold, even though Day 2's balance has been rewritten twice since and that is correct rather than stale.

The same reasoning is why authorization decisions are final. The decline of Auth-B is a record of what was true on Day 5, and no later row makes it untrue.

---

## 4. Component architecture

```text
┌─────────────────────────────────────────────────────────────┐
│ ReplayDriver                                                │
│   for day P in 1..6: process events of P → DayEndCycle(P)  │
└─────────────────────────────────────────────────────────────┘
                 │                         │
                 ▼                         ▼
        ┌────────────────┐       ┌────────────────────┐
        │ EventProcessor │       │ DayEndCycle        │
        │ one method per  │       │ restatement, then  │
        │ event type,     │       │ close              │
        │ pattern-matched │       └────────────────────┘
        └────────────────┘                 │
                                           │ at cutover, asks the
                                           │ journal for the earliest
                                           │ value date among rows
                                           │ posted today.
                                           │ Derived, never registered.
                                           ▼
                    ┌──────────────┬─────────────────┬──────────────┐
                    │ DayCloser    │ InterestAccrual │ Capitalizer  │
                    │ FeeAssessor  │ InterestBasis   │ (Day 6)      │
                    │ FeeReverser  │                 │              │
                    │ FeePolicy    │                 │              │
                    └──────────────┴─────────────────┴──────────────┘
                                   │
                                   ▼
       ┌─────────────────────────────────────────────────────────┐
       │ Journal · HoldBook · DecisionLog · AccrualBook (append-only) │
       └─────────────────────────────────────────────────────────┘
                                   │
                                   ▼
             ┌────────────────────────────────────────┐
             │ JournalSnapshot (immutable)            │
             │ ledgerBalanceAsOf(valueDate)           │
             └────────────────────────────────────────┘
                                   │
                                   ▼
                    DayReport → ReportPrinter
```

`DayCloser` is one component applied to a day, not two. The restatement pass and the routine close run the same fee assessment and fee reversal logic over different day ranges, which is why there is a single box.

### Module responsibilities

| Package | Responsibility |
|---|---|
| `money` | Currency (AED 2dp, BHD 3dp), `Money` (immutable, currency-tagged, scale-enforcing), `LargestRemainderSplitter` |
| `event` | Sealed `Event` hierarchy and the fixed stream fixture. `ReversalResolver` |
| `journal` | Posting, append-only `Journal`, immutable `JournalSnapshot` with value-dated balance queries |
| `hold` | `HoldBook` = append-only placements and releases; active holds are derived, not stored |
| `decision` | `DecisionLog`: every approval, decline, rejection and error, each carrying the numbers that justified it |
| `policy` | `FeePolicy` and `InterestBasis` strategy interfaces, two implementations each |
| `eod` | `DayCloser` (assessment + reversal), `InterestAccrual`, `AccrualBook`, `Capitalizer`, `DayEndCycle` |
| `replay` | `ReplayDriver`, `DayReport`, `ReportPrinter`, `Main` |
| `config` | `LedgerConfig`: every constant in one place |

**Why this shape and not event-sourced full recompute.** An alternative design makes everything an event in one log and derives all state by projection. It is elegant and handles backdating naturally. It was rejected because the decision records, which are the interesting part of two acceptance criteria, get buried among money movements, and because every query becomes a fold over a heterogeneous log. Separating the four stores keeps each one trivially explainable, which matters when the design must be defended verbally.

---

## 5. Data model

```text
Money                 { BigDecimal amount, Currency currency }
                      compact constructor rejects wrong scale
                      arithmetic rejects currency mismatch

Currency              AED(scale 2) | BHD(scale 3)

Posting               { PostingId id
                      , Day postingDay        // when it entered the journal
                      , Day valueDate         // which day it belongs to
                      , Money signedAmount    // sign carries direction
                      , PostingKind kind
                      , EventId sourceEvent
                      , PostingId reversesPosting // nullable
                      }

PostingKind            CREDIT | DEBIT | SETTLEMENT | FEE | FEE_REVERSAL
                      | REVERSAL | INTEREST_CAPITALIZATION

Event (sealed)         Credit | Debit | InstalmentCredit
                      | Authorization | Settlement | Reversal

Reversal               { EventId targetEvent } // REQUIRED, and no amount field
Hold                   { AuthId, Money amount, Day placedOn }
HoldRelease            { AuthId, Day releasedOn, EventId cause }

Decision (sealed)      AuthApproved | AuthDeclined | SettlementAccepted
                      | SettlementRejected | ReversalAccepted | ProcessingError

Accrual                 { AccountId, Day, Money basisBalance, Money rounded }
```

### Two modelling decisions worth defending

**A reversal has no amount of its own.** Look at the stream: every money event carries a figure except the reversal, which carries only its target. The amount exists nowhere but on the target. So `Reversal` takes `targetEvent` as a required constructor argument and has no amount field at all. An unlinked reversal is merely invalid, it is unconstructible. The resolver derives the amount, asserts the stated account and value date against the target, and emits **one mirrored posting per posting of the target**, each naming the row it cancels.

Pointing at the **event** rather than a posting is what makes the multi-row case work. The instalment credit produces three postings from one event; reversing it must unwind all three atomically, and a posting-level reference would allow a half-undone credit to exist.

**Signed amounts, not direction flags.** A posting carries a signed `Money`. Balance is then a plain sum with no branching on kind, which removes an entire class of sign bugs and makes every balance assertion a one-liner.

---

## 6. Processing model

### 6.1 Replay order

Events are grouped by **posting day**, and list order is preserved within a day. The brief's list order and its posting-day column disagree exactly once, on the reversal against the instalment credit, and the posting-day column wins: the output is required per day, and a day cannot close before its own events have arrived.

This is a genuine ambiguity, not a formality. Under the frozen interest basis the other reading costs ACC-002 0.004. Both readings ship as tests so the difference is demonstrated rather than claimed.

### 6.2 Per-event flow

```text
event → EventProcessor

  ├─ CREDIT / DEBIT
  │      └─ append one posting, unconditionally
  │         (debits are never balance-checked; that is what the overdraft fee exists for)
  │
  ├─ INSTALMENT CREDIT
  │      └─ split via LargestRemainderSplitter
  │         append N postings atomically, one event ref
  │
  ├─ AUTHORIZATION
  │      └─ available - hold ≥ 0 ?
  │         yes → HoldBook.place + DecisionLog.approved
  │         no  → DecisionLog.declined, NO hold, NO posting
  │
  ├─ SETTLEMENT
  │      └─ target hold active ?
  │         yes → HoldBook.release(full) + one DEBIT posting
  │         no  → DecisionLog.rejected, NOTHING else
  │
  └─ REVERSAL
         └─ ReversalResolver: validate target, then
            one mirrored posting per target posting
```

Authorization decisions are **final**. They are never re-evaluated when a backdated row later changes the balance. A decision is a fact recorded against the information available at that instant, and inventing a retrospective approval would assert that funds were available at a moment when they were not.

### 6.3 The day-end cycle

The cycle separates the **exceptional** path, restating days that late-arriving rows disturbed, from the **routine** path, closing today. Restatement runs first, so today is judged once against settled history.

```text
DayEndCycle(P)

  ├─ PASS 0. RESTATEMENT
  │      conditional: only if backdated rows posted today
  │      range = [earliestBackdatedValueDate(account, P) .. P-1],
  │      walked ASCENDING
  │      range is DERIVED from the journal, not registered during the day
  │      applies DayCloser to each day in the range
  │
  ├─ PASS 1. ROUTINE CLOSE
  │      unconditional, every day
  │      applies DayCloser to day P alone
  │
  ├─ PASS 2. InterestAccruer «InterestBasis»
  │      MEMO accruals, not postings
  │      rate × closing balance, positive balances only,
  │      rounded per day to the account's own precision
  │
  └─ PASS 3. Capitalizer (P = 6 only)
         ONE posting per account
         amount = exact sum of the rounded daily accruals,
         struck on the pre-capitalization balance so interest never accrues on itself

DayCloser(day D)
  ├─ assess «FeePolicy»
  │    D closes negative and not yet charged → append FEE vd D
  └─ reverse
       D was charged and would close non-negative
       → append FEE_REVERSAL vd D
```

**Why restatement is batched rather than triggered per event.** Several backdated rows can land on the same processing day for the same past day. Evaluating after each one assesses fees against intermediate states that never existed at any close of business. A correction that nets to zero across two rows would book a fee on the first and reverse it on the second, leaving four rows in a log that should never be cleaned up, and showing the customer money leaving and returning for a correction that should never have reached them. Batching collapses the day's corrections into one evaluation per affected day.

**The range is derived, not registered.** Nothing is queued, notified or accumulated while events process. At cutover the day-end cycle asks the journal one question:

```text
restatementRange(account, P):

    earliest = min(
        posting.valueDate
        where posting.postingDay == P
          and posting.valueDate < P
    )

    return earliest is null ? nothing : [earliest .. P-1]
```

Then backdated rows for the same past day cost nothing extra, because there is nothing to send a message to; they simply produce the same minimum ten times. Deduplication is not implemented, it is structural. The forward walk from the earliest day is what propagates the cascade.

This also closes a silent failure mode. A registration side-channel would make restatement depend on every writer remembering to call it, so adding an event type and forgetting the call would stop restatement with no test failing. The current approach cannot forget, because the evidence is the posting itself. It is also the only choice consistent with §2: this design stores no derived state, and a restatement queue would have been exactly that.

The range is evaluated **once, before pass 0 writes anything**. It is in fact stable under re-evaluation, since fee rows booked during restatement carry value dates already inside the range, but an invariant pins that rather than leaving it to luck.

**Why restatement runs before the routine close.** Fees cascade forward, so a fee restated onto Day 2 changes Day P's closing balance. Closing P first means re-examining it after the cascade arrives. Running restatement first lets P be evaluated exactly once, on history that will not move again.

**Why assessment and reversal are one component.** They are the same principle in opposite directions: a fee is a function of the current best knowledge of a day's balance. Assessing retroactively while refusing to un-assess on the very same information is one position that cannot be defended, and an earlier draft of this design held it.

**Terminology note.** In banking, *reconciliation* normally means matching two independent record sets, such as an internal ledger against a custodian statement. This pass restates the system's own history in light of late-arriving entries, so it is named restatement to avoid the collision.

### 6.4 Relationship to a production batch

This structure is the in-memory shape of how a real core banking system is organised, and the mapping is worth stating because it is the same split, not an analogy.

| Here | Production |
|---|---|
| `EventProcessor`, driven by events arriving | The online, real-time path: authorizations and postings all day |
| `DayEndCycle`, driven by the day advancing | The scheduled end-of-day or close-of-business batch, at a controlled cutover |
| Day index 1 to 6 | *business date* distinct from the wall clock, advanced by an explicit rollover step |
| The derived restatement range | Value-date adjustment processing, run ahead of the routine close |

The one place this design knowingly departs from production is covered in §12: real ledgers **lock a period once it has been reported and handed to the general ledger**, so a late entry cannot be value-dated into it. It is posted to the current open period carrying the original value date as a reference for recalculation only. This brief has no locking, and its fee rule evaluates value-date against the fee to the assessed day, so back-dating is correct here while being the first thing to change there.

---

## 7. Policy plug points

Three genuine ambiguities in the brief are modelled as switches rather than resolved by fiat. All three affect only the end-of-day pipeline; the ten events replay identically under every setting.

| Switch | Implementations | Default | Rationale for the default |
|---|---|---|---|
| **Fee timing** | `RetroactiveFeePolicy`, `ProcessingDayFeePolicy` | Retroactive | The rule's parenthetical “all entries with value_date ≤ that day” only does work under this reading. It also makes fee reversal coherent |
| **Interest basis** | `FinalValueDatedBasis`, `AsKnownFrozenBasis` | Final value-dated | “Closing ledger balance” must mean the same thing in the interest rule as in the fee rule |
| **Fee reversal** | on / off | **On** | An acceptance criterion requires it, and the replay confirms it reproduces the no-backdating world exactly |

### The outcome matrix, generated not asserted

A parameterized test prints all eight combinations, so the numbers in any document are output, not claims.

| Fee timing | Interest basis | Reversal | Fee rows | Net fees | Interest | ACC-001 |
|---|---|---|---:|---:|---:|---:|
| Retroactive | Final | off | 3 | 75.00 | 0.93 | 390.93 |
| Retroactive | As-known | off | 3 | 75.00 | 0.81 | 390.81 |
| Retroactive | Final | on | 3 | 75.00 | 1.01 | 441.01 |
