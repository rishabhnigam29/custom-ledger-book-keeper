# WORKLOG.md

Real timestamps, Asia/India working day, 2026-09-17. This log begins when work on this repository began; it does not reconstruct anything earlier.

---

## 2026-09-17

**13:31 — Brief read and pulled apart.**

Identified the two-timeline problem as the spine: posting day against value date, with two events backdated. Everything else in the exercise turned out to be a consequence of that.

**13:31–15:11 — Requirement analysis and independent replay.**

Replayed the stream by hand with exact decimal arithmetic under five interpretations, so the numbers would not depend on the implementation agreeing with itself. Established the interpretation matrix and the eight acceptance-criteria verdicts. Found three genuine ambiguities that move a number: fee timing, interest basis, fee reversal.

**15:11 — HLD.md.**

Architecture. Four append-only stores, no stored balances, the day-end cycle. Wrote the component architecture and trade-off sections.

Two corrections came out of review during this hour, both recorded in REJECTED.md:
- The C6 refusal was withdrawn. It rested on confusing a derived balance with log state, and it paired retroactive assessment with no reversal, which is incoherent. Default flipped to reversal-on; headline figure moved from 390.93 to 466.03.
- The restatement queue was deleted in favour of deriving the range from the journal at cutover. A registered queue is derived state maintained beside the journal, which this design's own first principle rejects.

**15:15 — requirement.md.**

Fifty numbered requirements, the criteria verdicts, the ambiguity register, the expected output. Numbered so every rule has a test that cites it.

**15:19 — LLD.md.**

Class-level specification with real signatures, the algorithms, the test inventory, an eighteen-step implementation order.

**15:34 — Implementation begins.**

Maven skeleton, money primitives, largest-remainder splitter.

**15:34–15:37 — Core built and green.**

Model types, journal with a single mutator, hold book, decision log, event dispatch, reversal resolver, both fee policies, both interest bases, day-end cycle, report printer, invariants.

Deviations from the LLD, all deliberate:
- **`instanceof` chains instead of pattern-matching switch.** Pattern matching for switch is still preview in Java 17; it is final in 21. Sealed interfaces and records are used as designed.
- **Decisions gained an account field.** The first cut scoped nothing by account, so ACC-002's report block showed ACC-001's authorization states and errors. Caught by reading the output.

Three things the implementation taught that the design had wrong:
1. **The reversal switch is a no-op under processing-day assessment.** Scoping reversal to the days the fee policy examines is the only consistent choice, and it means the charged day is never re-examined. An earlier hand model had reversed across all charged days regardless of policy, which silently combined processing-day assessment with retroactive reversal.
2. That falsified a claim in the HLD, that reversal erases the fee-timing decision from the final balance. It does not; the policies stay 25.00 apart. Both documents corrected.
3. A counterfactual assertion was wrong about row counts, not the code: the no-backdating replay has five rows, not four. Fixed the test, not the engine.

**15:37 — Tests green.**

62 tests pass at this point. The deliberately-failing test is tagged and excluded by default; `mvn test -Pfailing` runs it alone and it is red, as intended.

**15:37 — Documents.**

README, NUMBERS.md, AMBIGUITIES.md, REJECTED.md, this log.

**15:48 — Audited the code against the brief and against its own documents.**

Found one real bug and a set of places where the code claimed less than the documents did. All fixed; the replay numbers did not move.

- **Fee currency, a genuine bug.** `DayCloser` rescaled 25.00 into the account's own precision and would have booked **BHD 25.000** on a negative BHD account, silently asserting a 1:1 exchange rate. Three documents said it should throw. Unreachable on this stream, so nothing caught it. The fee is now defined for AED alone and resolved lazily, so a positive non-AED account is unaffected rather than throwing at every cutover. Four regression tests added.
- **The append-only invariant proved nothing.** It compared list lengths, which shows a list grew and would pass even if every row had been rewritten in place. Now compares each row against the previous cutover's snapshot, position by position.
- **Three invariants the design listed were missing entirely:** no interest row before the final day, capitalization equals the exact sum of the rounded accruals, and the restatement range is stable under its own writes. The last is now a postcondition inside the day-end cycle, where both values are in scope.
- **The printer never showed the journal,** although every design document presents the append-only log as the output. It now prints the rows per account per day, marking new and backdated ones.
- **`Account.opening` was never read.** A non-zero opening is now a real posting. Both are zero here, so no row appears.
- **One test asserted an identity that held regardless of behaviour,** while being named for available rising on a debit. Rewritten against the printed report: Day 3 available 450.00, Day 4 available 465.00, a rise of 15.00 across a 185.00 debit.
- **The listed-order variant stamped rows with a posting day they were not processed on.** Late arrivals are now restamped with the day they actually turned up; the value date is untouched, which is what makes them late.

Tests: 62 to 66, all green. `-Pfailing` still red. ACC-001 466.03, ACC-002 10.008, unchanged.

The pattern worth naming: every one of these was a place where a document asserted a property and the code did not enforce it, and the unreachable path with no test is where that drift happens silently.

**19:38 — Driving it by hand, and what that exposed.**

Added a JSON scenario loader with a hand-written parser, four scenario files, a `run.sh` that compiles with plain `javac`, and an interactive console that applies one event at a time.

Four defects surfaced only because the thing was being *used* rather than tested:
- **The pom hard-coded `exec.mainClass`,** so every `-Dexec.mainClass` was silently ignored. Four scenario runs printed identical output and I nearly reported them as working.
- **The journal table numbered rows per account while the `reverses` reference used the global posting id,** so a correct fee reversal displayed as an off-by-one. The logic was right the whole time. An invariant now pins that each reversal refunds the fee for its own value date.
- **Plain `javac` does not copy resources,** so the manual build broke the moment the canonical stream moved to the classpath. That is the one real cost of that choice.
- **Tidying `brief.json`** so the instalment credit sat before the reversal silently destroyed ambiguity A-1. `OrderingTest` caught it; the file now carries a note not to tidy it.

**19:38 — Final pass against the brief.**

Found and fixed two validation gaps and three stale claims.

- **`LedgerSetup` fell through to the defaults on an unrecognised policy name.** Once scenarios are read from JSON that is user input, so a typo ran retroactive assessment and then printed `"retroactive"` as though confirming the request. Every figure downstream was plausible and wrong. It throws now, naming the alternatives. `windowDays` is validated too.
- Test counts were stale in three documents, the low-level design never mentioned five classes added after it was written, and this log stopped two thirds of the way through the work.

Tests 72 to 77, all green. `-Pfailing` still red. ACC-001 466.03, ACC-002 10.008, unchanged throughout.

**20:20 — An adversarial review, seven lenses, against the brief.**

Ran seven independent reviewers over the repository: an independent recomputation of every number, clause-by-clause brief compliance, the eight criteria verdicts, Java defects, test quality under mutation, document-versus-code honesty, and live-defence risk. 74 findings, of which 54 survived a refutation pass. I reproduced the ten most serious by hand before acting on any of them.

**A methodology mistake worth recording.** The first refutation pass gave each finding its own agent and refuted 61% of them. To save time I rebatched six findings per agent, and the refutation rate collapsed to 18% — the filter had become a rubber stamp, not a stronger set of findings. That is why the second round required every finding to carry a scenario file that had actually been run, with quoted output: verification then means re-running a command rather than arguing with a claim.

**20:20–21:10 — Five defects fixed, each reproduced first.**

- **A refunded day could never be charged again.** The once-per-day guard asked whether a FEE row existed, not whether a fee was outstanding; the journal is append-only, so a refunded fee's row suppressed assessment forever. This was the worst of them, because accepting C6 rests on the argument that assessment and un-assessment are the same principle in both directions — and the code only ran it one way.
- One account could settle another account's authorizations.
- **Over-settlement was accepted** although AMBIGUITIES A-14 and the architecture document both said it was rejected.
- An **instalment credit could never be reversed:** the legs carried derived ids, so the resolver reported `"unknown target"` for an event plainly in the journal.
- Events **outside the window vanished** with no error.

Two invariants were also lying. `ledgerIsIndependentOfHolds` took a `HoldBook` and never looked at it, asserting something about settlement rows instead; it would have passed even if an authorization had written a ledger row, the one thing its name promises to catch. The instalment invariant claimed a sum check it never made, and is now named for what it does.

**21:10–21:36 — A second hunt, and eight more defects behind one root cause.**

Eight hunters probed the fixed engine, each required to reproduce. 29 claims, 25 confirmed.

**The root cause of five of them: auth ids were treated as globally unique.** `HoldBook` and `DecisionLog` both keyed on the auth id alone. One account's release freed another's reservation; a settled authorization reverted to APPROVED_ACTIVE when a stranger reused the name; one settlement wiped every hold sharing an id. Everything is now keyed on `(account, auth id)`, recorded as FR-53 and ambiguity A-14b.

**That cluster included a regression I had introduced an hour earlier.** My cross-account fix checked ownership while still looking the hold up by id alone, so an account could be refused permission to settle its own authorization whenever another account had used the name first. A fix that creates a new defect in the same method is exactly what the second hunt existed to catch.

Also fixed: a negative settlement amount posted a **credit**, minting money; a negative hold raised available balance **above** the ledger balance and funded an authorization the account could not cover; a forward-dated value date aborted the entire replay on an internal invariant rather than being recorded (A-23 had claimed this worked for months); the engine's own OPENING row could be reversed away; a multi-row reversal could leave itself half-unwound in an append-only journal; duplicate event ids let one reversal mirror two events; the console could be closed past the end of the window and wedged; truncated JSON threw a raw index error; and a pasted JSON event the engine refused killed the session with a stack trace.

Tests 77 to 89. Eight new requirements, FR-53 to FR-60, for behaviour that previously had no rule behind it. The canonical answer never moved: ACC-001 466.03, ACC-002 10.008, through all thirteen.

**What I would say about this in the defence.** Every one of these eighteen defects sat on a path the brief's own stream never touches. The ten events are a happy path: no id collisions, no negative amounts, no forward dating, no second fee on a refunded day. A suite that only replays the brief proves the brief and nothing else — which is why deleting the whole fee engine left the original suite green, and why the regression tests added here are all cases the brief does not contain.

---

## Where the time actually went

Roughly a third on reading and re-deriving the numbers by hand, a third on design and its two corrections, a third on implementation. The implementation was the shortest phase, which is the expected shape for a problem whose difficulty is entirely in deciding what the rules mean.

The single most valuable hour was the one spent re-deriving the replay by hand under competing interpretations before writing any code. Every number in every document traces to that, which is why the implementation had no arithmetic surprises — only the two structural ones logged above.
