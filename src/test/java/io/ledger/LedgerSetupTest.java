// LedgerSetupTest.java
package io.ledger;

import io.ledger.replay.LedgerSetup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Configuration is user input once scenarios are read from JSON, so it is validated like input.
 *
 * <p>An earlier version fell through to the defaults on an unrecognised name, so a typo ran
 * retroactive assessment and then printed "retroactive" as though confirming the request. Every
 * figure downstream was plausible and wrong, which is the worst failure this class can produce.
 */
@DisplayName("LedgerSetup validation")
class LedgerSetupTest {

    @Test void bothPolicyNamesAreAccepted() {
        assertDoesNotThrow(() -> LedgerSetup.of("retroactive", "final-value-dated", true));
        assertDoesNotThrow(() -> LedgerSetup.of("processing-day", "as-known-frozen", false));
    }

    @Test void aTypoInTheFeePolicyThrowsRatherThanDefaulting() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LedgerSetup.of("retroactve", "final-value-dated", true));
        assertTrue(e.getMessage().contains("retroactve"));
        assertTrue(e.getMessage().contains("processing-day"), "the message must name the alternatives");
    }

    @Test void aTypoInTheInterestBasisThrowsRatherThanDefaulting() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> LedgerSetup.of("retroactive", "final-value-date", true));
        assertTrue(e.getMessage().contains("as-known-frozen"));
    }

    @Test void aWindowMustHaveAtLeastOneDay() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerSetup.of("retroactive", "final-value-dated", true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> LedgerSetup.of("retroactive", "final-value-dated", true, -1));
        assertDoesNotThrow(() -> LedgerSetup.of("retroactive", "final-value-dated", true, 1));
    }

    @Test void theDefaultsAreTheCombinationTheDesignArguesFor() {
        assertEquals("retroactive / final-value-dated / reversal on / 6 days",
                LedgerSetup.defaults().describe());
    }
}