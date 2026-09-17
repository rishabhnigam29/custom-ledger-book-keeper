# AMBIGUITIES.md

Every place the brief was silent, self-contradictory, or admitted more
than one honest reading.\
Ordered by whether the resolution moves a printed number. Requirement
identifiers refer to `requirement.md`; each numeric consequence below is
produced by a test, not asserted here.

------------------------------------------------------------------------

## Part 1 --- Ambiguities that change a number

### A-1. The stream's list order contradicts its own posting-day column

The brief says the events are "replayed in this order" and lists E9
ninth, E10 tenth. It also gives E9 posting day 6 and E10 posting day 5.
Those two orderings disagree, and only for this one pair.

**Options.** Group by posting day, so E10 is replayed on Day 5. Or take
the list literally, making E10 a late arrival that turns up after Day 6
work has begun.

**Resolved:** group by posting day, preserving list order within a day.
The output is required per day, and a day cannot close before its own
events have arrived. Listed order makes the day sequence run 5, then 6,
then 5, which is not monotonic, and strips E10's stated posting day of
any consequence.

**Consequence.** Under the default interest basis, none: both readings
give ACC-002 10.008. Under the frozen basis the listed reading gives
**10.004**, because Day 5 closes before E10 arrives and a 0.000 accrual
is frozen. `OrderingTest` runs both.

**Honest counter-argument.** "Replayed in this order" is explicit, and a
late-arriving entry is exactly the phenomenon this exercise is about. E7
and E9 are late in value date; E10 would be late in posting day. On that
reading the brief is consistent and E10 is a third trap rather than an
editing accident.

### A-2. "Assessed when that day's closing balance is negative" --- as of when?

The rule defines a day's closing balance as all entries with value date
at or before it, which is a figure that changes after the day has
closed. It never says which days an assessment run examines.

**Options.** Retroactive: re-examine every value-dated day against
current knowledge. Or processing-day only: look at today's closing and
nothing else.

**Resolved:** retroactive. It is the only reading under which the rule's
parenthetical does any work, and the only one that can place a fee on a
past day at all, which is what criterion C2 is baiting. The cost is that
the customer sees three fees appear at once on Day 5 for days that
looked healthy when they happened.

**Consequence.** Retroactive books three fees totalling 75.00,
value-dated Days 2, 4 and 5. Processing-day books one fee of 25.00,
value-dated Day 5.

### A-3. Nothing says whether a fee survives the reversal of its cause

**Options.** Leave fees standing as historical facts. Or append a
compensating credit when the day they were charged for no longer closes
negative.

**Resolved:** reverse them. Criterion C6 requires it, and independently,
**retroactive assessment without retroactive un-assessment is
incoherent**: if a fee is a function of the current best knowledge of a
day's balance, the same knowledge must be allowed to withdraw it. An
earlier draft of this design refused C6 and defaulted reversal off; that
was wrong and is recorded in `REJECTED.md`.

**Consequence.** ACC-001 closes at **466.03** with reversal on,
**390.93** with it off.

**Discovered while implementing.** Reversal is scoped to the same days
the fee policy examines, because assessment and reversal are one
operation in two directions. That makes the reversal switch a **no-op
under processing-day assessment**: the charged day is never re-examined,
so there is nothing to reverse. An earlier hand model reversed across
all charged days regardless of policy, which silently combined
processing-day assessment with retroactive reversal --- the exact
asymmetry this entry rejects.

### A-4. Which version of a day's balance earns interest

Given that history is restated, a day has more than one closing balance
over its lifetime.

**Options.** Final value-dated: recompute every accrual on the settled
history at capitalization. Or as-known frozen: strike each evening's
figure once and never revise it.

**Resolved:** final value-dated, so "closing ledger balance" means the
same thing in the interest rule as in the fee rule one paragraph
earlier.

**Consequence.** 1.03 against 0.84 under the default fee-settings. Under
the frozen basis Day 2 keeps 0.10 although it ends at 250.00 after
reversal, and Day 5 keeps 0.00 although it ends at 465.00 --- both
figures contradicting their own day's final balance.

### A-5. Sum of rounded dailies, or rounded sum of raw dailies

**Resolved:** sum of rounded dailies, which the brief states as
non-negotiable.

**Consequence.** 1.03 against 1.02. The 0.01 gap is precisely what
criterion C8 proposes to discard. `Capitalizer` deliberately contains no
`setScale`, so the total is exact by construction and the rule cannot be
broken by accident.

### A-6. Which instalment carries the extra minor unit

**Resolved:** the first. Documented in `NUMBERS.md` as a deliberate
constant rather than an artefact of the loop.

**Consequence.** 3.334, 3.333, 3.333 rather than 3.333, 3.333, 3.334.
Same total either way.

### A-7. A settlement against an authorization that never existed

**Options.** Reject it. Or force-post it as an unmatched debit, which is
what card schemes often do, because at the scheme the money has already
moved.

**Resolved:** reject. It keeps the core internally consistent, at the
cost of a settlement the outside world believes happened.

**Consequence.** ACC-001 is 465.00 before and after E6. Force-posting
would take it to 285.00 and change every subsequent figure.

### A-8. Are authorization decisions re-evaluated when a backdated entry lands?

**Resolved:** no. A decision is a fact recorded against the information
available at that instant.

**Consequence.** Auth-B stays declined although the account closes at
466.03. This is the subject of the deliberately-failing test: the engine
is retroactive about money and final about decisions.

### A-9. Are interest accruals ledger rows or memos before capitalization?

**Resolved:** memos. The brief says accruals "capitalize as a single
credit at end of Day 6", which is only meaningful if they were not
posted already.

**Consequence.** Decisive for criterion C1. If accruals posted daily,
the Day 2 closing at end of Day 5 would be **-369.80**, not **-370.00**,
and C1 would fail for a reason unrelated to backdating.

### A-11. Several backdated rows landing on one processing day

**Options.** Re-evaluate after each backdated row. Or batch the day's
corrections and restate once per affected day, before the routine close.

**Resolved:** batch, and run restatement before the routine close so
today is judged exactly once against history that will not move again.

**Consequence.** None in this stream; both agree. The argument is about
the permanent record. Per-row evaluation judges **intermediate states
that never existed at any close of business**: a correction netting to
zero across two rows would book a fee on the first and reverse it on the
second, leaving four rows in an append-only log that can never be
cleaned up, and showing the customer money leaving and returning for a
correction that should never have reached them. `RestatementTest`
constructs that case and asserts nothing is written.

### A-12. A reversal's value date: inherit from the target, or use its own?

**Resolved:** assert they agree. The brief makes both Day 2, so the two
candidate rules coincide. A disagreement throws rather than silently
preferring one.

**Consequence.** If a reversal used its own posting day as its value
date, Day 2 would stay at **-370.00** permanently and only Day 6 would
recover.

------------------------------------------------------------------------

## Part 2 --- Ambiguities that change no number here but had to be decided

### A-10. Rounding mode

`HALF_UP` chosen. No product in this stream lands on an exact half-cent,
so `HALF_EVEN` is indistinguishable. Pinned in `MoneyTest` against a
constructed value where they differ, so the choice is tested rather than
merely stated.

### A-13. Which balance feeds the available-balance check

Available could mean everything posted so far, or the value-dated
balance as of today. They are identical in this stream because nothing
is forward-dated.

**Resolved:** everything posted. A hold is a processing-time question
about money that has actually arrived, not about which day it is
attributed to.

### A-14. Settlement above the hold amount

Not exercised by the brief: Auth-A settles 185.00 against a 200.00 hold.
**Rejected** rather than force-posted, consistent with A-7.

**Documented before it was true.** This entry and the architecture
document both described the rejection while `EventProcessor` performed
no such check, so a settlement of 900.00 against a 200.00 hold was
accepted silently. The check now exists and is tested. Recorded here
because a document asserting a property the code does not enforce is the
failure mode this project is graded on.

### A-14b. Is an auth id unique to an account, or to the world?

The brief names authorizations Auth-A, Auth-B and Auth-Z without saying
whose namespace they live in. The stream never collides, so the question
is invisible in it.

**Resolved:** an auth id belongs to the account that issued it, and
every lookup is keyed on the pair. The alternative, a global namespace,
produces indefensible behaviour the moment two accounts pick the same
name: one account's settlement frees another's reservation, a settled
authorization reverts to active, and an account is refused permission to
settle its own holds.

**Consequence.** None on the brief's stream. Four separate defects on
any stream with a collision.

### A-15. Hold expiry

Not exercised and not implemented. Auth-B is never settled, so the
window ends with a declined authorization and no orphaned hold. A
production system needs an expiry sweep in the day-end cycle, ahead of
the routine close.

### A-16. The fee currency for a non-AED account

The brief denominates the fee in AED while one account is BHD. ACC-002
never goes negative, so the question never arises numerically.

**Resolved:** the fee is **defined for AED only**, and asking for one in
another currency is not supported. There is no FX in this system, and a
fee assessment is the worst possible place to introduce one.

**Caught in review, not by a test.** The first implementation rescaled
25.00 to the account's own precision and booked **BHD 25.000**, silently
asserting a 1:1 rate. Nothing failed, because the account is never
negative. `FeeCurrencyTest` now covers it, including the lazy resolution
that keeps a positive non-AED account from throwing at every cutover.

### A-17. Is a declined authorization an error?

**Resolved:** no. A decline is a decision with a reason and stored
arithmetic. An error is a malformed or unprocessable event. The report
separates them, so Day 5 shows an authorization state rather than an
error line.

### A-18. What trace does a rejected event leave?

**Resolved:** a `DecisionLog` entry carrying the attempted amount and
the reason, and no journal row at all. The journal records money; the
decision log records judgements.

### A-19. Reversing a multi-row event

Not exercised, since the reversed event produced one row. **Resolved:**
a reversal references an **event**, not a row, and emits one mirrored
row per row of the target. Reversing the instalment credit would
therefore unwind all three legs atomically. A row-level reference would
allow a half-undone credit to exist.

### A-20. What "Day N" means on a printed line

**Resolved:** the report prints the value-dated closing of **every day
so far**, not only today, because that is the only way a restatement is
visible. The holds column is deliberately not restated, and the contrast
between the two columns is the output's main teaching job.

### A-21. Day 6 ordering between the fee pass, the accrual and capitalization

**Resolved:** events, then fee assessment, then fee reversal, then
accrual, then capitalization. The Day 6 accrual is therefore struck on
the pre-capitalization balance, so interest never accrues on itself.
Enforced by ordering rather than by a flag.

### A-22. Zero-amount derived rows

**Resolved:** suppressed. An account whose accruals sum to zero gets no
capitalization row rather than a row for 0.00, because a zero-value
posting asserts that something happened when nothing did.

### A-23. Forward-dated entries

Not exercised. The value-dated fold handles them naturally: a row valued
after today simply does not count toward today's closing and starts
counting when its day arrives. Untested, and flagged as such rather than
claimed.
