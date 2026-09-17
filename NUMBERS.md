# NUMBERS.md --- every constant, and why that value

All of these live in `io.ledger.config.LedgerConfig` except where noted,
so this file points at code rather than restating it.

## Given by the brief

  --------------------------------------------------------------------------------
  Constant                Value                   Why not half it
  ----------------------- ----------------------- --------------------------------
  Overdraft fee           AED 25.00               Given. A flat punitive charge.
                                                  Halved to 12.50 it would still
                                                  trigger on all three days here
                                                  and change no structural
                                                  behaviour, only the arithmetic.
                                                  The interesting property is not
                                                  the magnitude but that it is
                                                  **flat**: a percentage fee would
                                                  compound with the cascade, since
                                                  a fee value-dated to Day 2
                                                  lowers Day 3, which would then
                                                  attract a larger fee, and the
                                                  "once per day" guard would be
                                                  doing heavier lifting here.

  Daily interest rate     0.0004                  Given as 0.04% per day. Stored
                                                  as a **fraction, never as
                                                  0.04%**. That factor-of-100
                                                  error is the most likely silent
                                                  bug in the exercise: it would
                                                  turn 1.03 into 103.00 and still
                                                  look plausible in a report.
                                                  Halved to 0.0002, ACC-001's
                                                  daily accruals would round to
                                                  0.05/0.05/0.13/0.09/0.09/0.09,
                                                  and ACC-002 would earn 0.002 per
                                                  day instead of 0.004.

  AED precision           2                       ISO minor unit, 100 fils to the
                                                  dirham

  BHD precision           3                       ISO minor unit, 1000 fils to the
                                                  dinar. **Halving this to 2 would
                                                  make ACC-002 earn nothing at
                                                  all**: 10.00 × 0.0004 = 0.004,
                                                  which rounds to 0.00 at two
                                                  decimals. An AED balance must
                                                  reach 12.50 before a daily
                                                  accrual survives rounding; a BHD
                                                  balance needs only 1.25.
                                                  Precision belongs to the
                                                  currency, never to a global
                                                  two-decimal rule.

  Window                  6 days                  Given. Integer day index, no
                                                  calendar, no day-count
                                                  convention

  Fee currency            AED, and no other       The brief defines one fee, in
                                                  AED. There is no BHD fee, so
                                                  asking for one throws instead of
                                                  reusing the number at a
                                                  different precision. Reusing it
                                                  would assert that one dirham
                                                  equals one dinar.

  Instalment count        3                       Given by the event. The splitter
                                                  is written for arbitrary n and
                                                  tested at 1, 3 and 7
  --------------------------------------------------------------------------------

## Chosen

  -------------------------------------------------------------------------
  Constant                Value                     Why not the alternative
  ----------------------- ------------------------- -----------------------
  Rounding mode           `HALF_UP`                 Conventional for retail
                                                    money and the reading a
                                                    customer expects. **No
                                                    value in this stream
                                                    lands on an exact
                                                    half**, so `HALF_EVEN`
                                                    is indistinguishable
                                                    here; `MoneyTest` pins
                                                    the difference on a
                                                    constructed 12.50 ×
                                                    0.0004 = 0.005000,
                                                    where HALF_UP gives
                                                    0.01 and HALF_EVEN
                                                    gives 0.00.

  Fee threshold           strictly `< 0`            Zero is not overdrawn.
                                                    An account at exactly
                                                    0.00 has not borrowed
                                                    anything, so charging
                                                    it would be charging
                                                    for the absence of a
                                                    balance.

  Approval threshold      `available - hold >= 0`   Inclusive of zero.
                                                    Spending down to
                                                    exactly zero is
                                                    permitted; the rule
                                                    prohibits going below
                                                    balance.

  Instalment remainder    to the **first**          Deterministic and
                          instalment                documented, which is
                                                    what matters.
                                                    Last-instalment
                                                    allocation is equally
                                                    defensible and common
                                                    in loan amortisation.
                                                    First was chosen
                                                    because the customer
                                                    receives their money
                                                    marginally sooner, and
                                                    because it makes the
                                                    largest-remainder
                                                    algorithm read in its
                                                    natural order.

  Split method            truncate then distribute  Rounding each share
                                                    independently and
                                                    posting a true-up row
                                                    was tried and
                                                    abandoned: it needs an
                                                    extra ledger row that
                                                    corresponds to nothing
                                                    the customer did. See
                                                    `REJECTED.md`.

  Hold release on         **full**                  The residual 15.00 on
  settlement                                        Auth-A was a
                                                    reservation, never
                                                    money. Releasing only
                                                    the settled amount
                                                    would strand 15.00 of
                                                    the customer's
                                                    available balance with
                                                    no event ever to free
                                                    it.

  Test tolerance          exact equality            Scale-aware
                                                    `BigDecimal`
                                                    comparison. A money
                                                    test with a tolerance
                                                    is not a money test.

  Opening balances        0.00 / 0.000              Given, and carried as
                                                    scale-bearing `Money`
                                                    so the currency is
                                                    explicit from the first
                                                    row
  -------------------------------------------------------------------------

## Derived, not chosen

  ------------------------------------------------------------------------
  Figure                                       Value Where it comes from
  --------------------- ---------------------------- ---------------------
  Fees assessed                                    3 Day 2, Day 4 and Day
                                                     5 all close negative
                                                     once the backdated
                                                     debit lands

  Fees reversed                                    3 All three days close
                                                     non-negative once the
                                                     reversal lands

  Net fee movement                              0.00 The two above cancel;
                                                     six permanent rows
                                                     remain

  Capitalized interest                      AED 1.03 Sum of the six
                                                     **rounded** dailies.
                                                     Rounding the raw sum
                                                     of 1.0180 would give
                                                     1.02, and discarding
                                                     that 0.01 is exactly
                                                     what the brief
                                                     forbids

  Capitalized interest                     BHD 0.008 0.004 on Day 5 plus
                                                     0.004 on Day 6

  Journal rows                                    17 9 from the ten stream
                                                     events, plus 3 fees,
                                                     3 fee reversals and 2
                                                     capitalizations
  ------------------------------------------------------------------------
