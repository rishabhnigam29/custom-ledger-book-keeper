// FailingByDesignTest.java
package io.ledger;

import io.ledger.decision.AuthState;
import io.ledger.model.AuthId;
import io.ledger.replay.ReplayDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static io.ledger.Fixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * THE MANDATED FAILING TEST (deliverable D-6).
 *
 * <p>Excluded from {@code mvn test} by the surefire {@code excludedGroups}; run it on its own with
 * {@code mvn test -Pfailing}. It is expected to be RED, and that is the deliverable.
 */
@Tag("failing-by-design")
@DisplayName("FAILS BY DESIGN: the reversal does not re-approve Auth-B")
class FailingByDesignTest {

    @Test
    void authBIsApprovedAfterTheReversal() {
        ReplayDriver r = replayDefaults();

        // By the end of Day 6 ACC-001 holds 466.03. A 90.00 hold would sit comfortably inside it,
        // and the debit that caused the decline has been fully reversed. On the face of it,
        // nothing about Auth-B's decline survives its own cause.
        assertEquals(aed("466.03"), r.closing(ACC1));

        // This assertion FAILS. Auth-B is still DECLINED.
        //
        // -- WHAT IT REVEALS ------------------------------------------------------------------
        // Fee reversal shows this engine CAN walk history back. CounterfactualTest proves it
        // exactly: every day closing, the net fee, the interest vector and the final balance all
        // equal a replay in which E7 and E9 never existed. Money is fully retroactive here.
        //
        // Authorization decisions are not, and the asymmetry is deliberate. A decision is a fact
        // recorded at the instant it was taken, against the information available then. On Day 5,
        // after E7 and before anything corrected it, the available balance genuinely was -155.00.
        // The decline was the right answer to the question actually asked.
        //
        // So this engine is RETROACTIVE ABOUT MONEY and FINAL ABOUT DECISIONS. Making decisions
        // retroactive too would manufacture an approval for a moment at which the customer had no
        // funds, which is a worse error than the one it fixes: it would let a later correction
        // authorise spending that was not authorised at the time, and in a card system that hold
        // would have been communicated to a merchant who acted on it.
        //
        // The limitation is real, not a bug. The production fix is a RE-PRESENTMENT flow - the
        // acquirer submits the authorisation again against the corrected balance and receives a
        // fresh decision with its own timestamp - rather than a recomputation of the old one.
        //
        // Covers FR-15 (decisions are final) and FR-10 (holds are processing-time facts).
        // -------------------------------------------------------------------------------------
        assertEquals(AuthState.APPROVED_ACTIVE, r.authState(ACC1.id(), new AuthId("Auth-B")),
                "Auth-B stays declined: money is retroactive, decisions are not");
    }
}