# REJECTED.md

Two parts. Acceptance criteria refused, with the arithmetic. Then
approaches abandoned mid-build, including one that was published in an
earlier draft of the design and later withdrawn.

Every verdict below is an executable assertion in `CriteriaVerdictTest`,
so this table is generated evidence rather than prose.

------------------------------------------------------------------------

## Part 1 --- Acceptance criteria

**Refused: C2, C7, C8. Accepted: C1, C3, C6. Accepted with a stated
caveat: C4, C5.**

### C2 --- REFUSED

> "E7 causes exactly one overdraft fee to be assessed, on Day 2."

**False under every coherent reading**, which makes it the cleanest
refusal in the set.

Under retroactive assessment the backdated debit drives three
value-dated days negative: Day 2 to -370.00, Day 4 to -155.00, Day 5 to
-155.00. Day 3 survives at 30.00 and falls to 5.00 once the Day 2 fee
cascades into it. That is **three fees totalling 75.00**, all booked on
processing Day 5.

Under processing-day assessment there is exactly one fee, but it is
value-dated **Day 5**, not Day 2, because Day 2's close has long since
run.

One reading gets the count wrong, the other gets the day wrong, and no
reading yields one fee dated Day 2.

**Corrected statement.** E7 causes three fees value-dated Days 2, 4 and
5 under retroactive assessment, or one fee value-dated Day 5 under
processing-day assessment.

### C7 --- REFUSED

> "The three BHD instalments in E10 must each be BHD 3.334."

**Arithmetically impossible.** 3 × 3.334 = 10.002, crediting the account
0.002 more than the event says. The other candidate is no better: 3 ×
3.333 = 9.999, short by 0.001. Ten divided by three does not terminate,
so **no equal three-way split exists at three decimals**.

The requirement that cannot be met is equality, so equality is what
gives. The total is the customer's money; equality was a presentation
detail.

**Corrected statement.** E10 posts as 3.334, 3.333 and 3.333, summing
exactly to 10.000.

### C8 --- REFUSED

> "If the rounded daily interest accruals do not sum to the capitalized
> total, the remainder is discarded."

**Directly contradicts a non-negotiable rule in the same brief**, which
states that the rounded daily accruals must sum exactly to the
capitalized total.

The contradiction is not merely formal. Under this design the
capitalized total is **defined** as the sum of the rounded dailies, so
no remainder can exist and the criterion describes an unreachable
branch. A remainder appears only if the total is computed the forbidden
way: the raw sum of 0.1000 + 0.1000 + 0.2600 + 0.1860 + 0.1860 + 0.1860
= 1.0180 rounds to 1.02, against 1.03 from summing the rounded values.
Discarding that 0.01 is money vanishing from a ledger.

`Capitalizer` contains no `setScale` at all, so the rule cannot be
violated by accident.

**Corrected statement.** The capitalized credit equals the exact sum of
the rounded daily accruals. If reconciliation were ever needed it would
be pushed into a daily accrual, never discarded.

### C4 --- ACCEPTED, with a caveat on its wording

> "Any settlement referencing an authorization ID not present in the
> ledger must be rejected and the funds must not leave the account."

The behaviour is right and the replay honours it: the balance is 465.00
before and after E6, no row is posted, no hold changes, one error is
recorded.

**The caveat:** authorizations are **not in the ledger**. They are
holds, which by C5's own logic never touch ledger balance. The phrase
should read "not present in the authorization register". The substance
is unaffected, which is why this is a caveat and not a refusal.

Worth adding that rejecting is a **choice, not a derivation**. Nothing
in the non-negotiable rules dictates it, and real card systems often
force-post an unmatched settlement because at the scheme the money has
already moved. See AMBIGUITIES.md A-7.

### C5 --- ACCEPTED, with a caveat that it is vacuous

> "If Auth-B is approved, its hold reduces available balance but not
> ledger balance."

Mechanically true and worth asserting as an invariant: no authorization
ever writes a ledger row.

**But Auth-B is never approved.** E8 is replayed after E7 on Day 5, when
available is -155.00 with no active holds, so -155.00 - 90.00 = -245.00
and it is declined. The antecedent is false, so the criterion tells us
nothing about this run under any policy combination. It fails on
intra-day ordering alone: had E8 preceded E7, available would have been
465.00 and it would have been approved.

------------------------------------------------------------------------

## Part 2 --- Approaches abandoned mid-build

### The C6 refusal, published and then withdrawn

**The largest single correction in this work.** An earlier draft of the
design **refused** criterion C6 and defaulted fee reversal to off, on
two arguments. Both were wrong.

The first argument was that append-only forbids "return". That confused
a **derived balance with log state**. A balance can go 100 to 50 to 100
and has plainly returned, while the log only grew. C6 claims balances
and fees return, not that the ledger returns; the refusal answered a
claim the criterion never made.

The second was worse, because it was self-contradictory. The design had
already chosen retroactive fee assessment, which says a fee is a
function of the current best knowledge of a day's balance. At the end of
Day 6 that predicate is false for Days 2, 4 and 5, since all three close
positive. **Assessing retroactively on new information while refusing to
un-assess on the same information is incoherent.**

`CounterfactualTest` settles it: with reversal on, a replay with the
backdated debit and its reversal removed entirely agrees with the full
replay on every day closing, the net fee, the interest vector and the
final balance. 250 / 250 / 650 / 465 / 465, zero net fees, 1.03
interest, 466.03 final. C6 is true, to the fill.

This reframes the criteria list. C2, C7 and C8 are wrong under every
reading. C6 is a different animal: it is not false, it is a
**specification of behaviour the rules left open**, and accepting it
resolves the fee-reversal gap that the rules alone cannot settle.

### Reversal scoped independently of the fee policy

A hand model used while designing reversed fees across all charged days
regardless of the fee timing policy. Implementing it exposed the flaw:
that silently pairs processing-day assessment with retroactive reversal,
the exact asymmetry the section above rejects.

Corrected: assessment and reversal share one day scope, because they are
one operation in two directions. The consequence is that **the reversal
switch is a no-op under processing-day assessment** --- the charged day
is never re-examined, so there is nothing to reverse. It also falsified
a claim made in an earlier draft, that turning reversal on erases the
fee-timing decision from the final balance. It does not; the two
policies stay 25.00 apart.

### A registered restatement queue

The day-end cycle was first designed with a queue that the event
processor wrote to as backdated rows posted, holding the earliest value
date touched per account.

Abandoned for three reasons. It is **derived state maintained beside the
journal**, which this design's own first principle rejects, since a
cache goes stale invisibly. It makes restatement depend on **every
writer remembering to register**, a correctness-critical invariant
enforced by convention whose failure is silent: add an event type,
forget the call, and restatement stops with no test going red. And it
needs a lifecycle --- clearing, ordering against the cutover --- for no
gain.

Replaced by a query over rows posted today. Ten backdated rows for one
past day now cost exactly what one costs, because they produce the same
minimum ten times; deduplication is structural rather than implemented.

### Per-row restatement

Re-evaluating fees after each backdated row, rather than batching the
day's corrections. Rejected because it judges **intermediate states that
never existed at any close of business**. See AMBIGUITIES.md A-11 and
`RestatementTest`.

### Redenominating the overdraft fee into the account's currency

The first implementation resolved the fee by rescaling 25.00 to the
account's own precision, which for a BHD account produced **BHD
25.000**. That silently asserts a 1:1 exchange rate in a system built so
that cross-currency arithmetic cannot even be expressed.

It survived because ACC-002 is never negative, so the path is
unreachable on this stream and no test covered it. Found by auditing the
code against its own documents, which all three said the mismatch should
throw. Replaced by a fee defined for AED alone, resolved lazily so that
an account in another currency only throws when a fee is genuinely about
to be charged.

The general lesson is the one worth carrying: **an unreachable path with
no test is where the documentation and the code drift apart silently.**

### Asserting append-only by comparing list lengths

The first invariant suite checked that the journal had not shrunk. That
proves a list grew and nothing else; every row could have been rewritten
in place and it would still pass. Replaced by a comparison of every row
against the previous cutover's snapshot, position by position.

Three further invariants the design documents listed were simply
missing: that no interest row exists before the final day, that
capitalization equals the exact sum of the rounded accruals, and that
the derived restatement range is stable under its own writes. All three
are now enforced, the last as a postcondition inside the day-end cycle
where both values are in scope.

### Round-each-instalment-then-post-a-true-up-row

An alternative split: round each share independently, then post a
correcting row for the difference. Abandoned because the true-up row
corresponds to nothing the customer did, and an append-only ledger
should not carry rows that exist only to fix the ledger's own
arithmetic.

### A cached or incrementally-maintained running balance

The obvious optimisation, and the one that breaks first. A backdated row
invalidates every cached day at or after its value date, and nothing in
the write path knows which those are. Rejected in favour of always
folding the journal. At seventeen rows the cost is nothing; the honesty
is the whole point.

### One unified event log with everything derived by projection

A genuinely elegant alternative that handles backdating just as well.
Rejected because the decision records that two acceptance criteria turn
on get buried among money movements, and every query becomes a fold over
a heterogeneous log. With a live defence and no AI, being able to say
"holds are in the HoldBook, decisions are in the DecisionLog, money is
in the Journal" is worth more than the elegance.

### Daily interest postings

Posting each accrual as a ledger row, capitalizing nothing. Rejected
because the brief says accruals **capitalize as a single credit**, and
because it would make the Day 2 closing at end of Day 5 -369.80 rather
than -370.00, breaking criterion C1 for a reason that has nothing to do
with backdating.
