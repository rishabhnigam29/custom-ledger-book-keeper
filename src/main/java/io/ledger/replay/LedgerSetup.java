// LedgerSetup.java
package io.ledger.replay;

import io.ledger.config.LedgerConfig;
import io.ledger.policy.AsKnownFrozenBasis;
import io.ledger.policy.FeePolicy;
import io.ledger.policy.FinalValueDatedBasis;
import io.ledger.policy.InterestBasis;
import io.ledger.policy.ProcessingDayFeePolicy;
import io.ledger.policy.RetroactiveFeePolicy;

/** The three switches. All three act only at cutover; the ten events replay identically under each. */
public record LedgerSetup(FeePolicy feePolicy, InterestBasis interestBasis, boolean feeReversal,
                          int windowDays) {

    public LedgerSetup {
        if (windowDays < 1) {
            // Zero used to construct happily, the driver's loop never ran, and the failure
            // surfaced later as Day.of(0) throwing from inside a balance query.
            throw new IllegalArgumentException("windowDays must be >= 1, was " + windowDays);
        }
    }

    /** Retroactive fees, final value-dated interest, fee reversal ON. ACC-001 closes at 466.03. */
    public static LedgerSetup defaults() {
        return new LedgerSetup(new RetroactiveFeePolicy(), new FinalValueDatedBasis(), true,
                LedgerConfig.WINDOW_DAYS);
    }

    public static LedgerSetup of(String fee, String interest, boolean reversal) {
        return of(fee, interest, reversal, LedgerConfig.WINDOW_DAYS);
    }

    /**
     * Names are matched exactly and an unknown one THROWS.
     *
     * <p>An earlier version fell through to the defaults, so a typo in a scenario file ran
     * retroactive assessment and then printed "retroactive" as though confirming the request. A
     * silently wrong policy is the worst failure this class can produce, because every figure
     * downstream is plausible.
     */
    public static LedgerSetup of(String fee, String interest, boolean reversal, int windowDays) {
        FeePolicy f = switch (fee) {
            case "retroactive" -> new RetroactiveFeePolicy();
            case "processing-day" -> new ProcessingDayFeePolicy();
            default -> throw new IllegalArgumentException("unknown feePolicy \"" + fee
                    + "\"; expected \"retroactive\" or \"processing-day\"");
        };
        InterestBasis i = switch (interest) {
            case "final-value-dated" -> new FinalValueDatedBasis();
            case "as-known-frozen" -> new AsKnownFrozenBasis();
            default -> throw new IllegalArgumentException("unknown interestBasis \"" + interest
                    + "\"; expected \"final-value-dated\" or \"as-known-frozen\"");
        };
        return new LedgerSetup(f, i, reversal, windowDays);
    }

    public String describe() {
        return feePolicy.name() + " / " + interestBasis.name() + " / reversal "
                + (feeReversal ? "on" : "off") + " / " + windowDays + " days";
    }
}