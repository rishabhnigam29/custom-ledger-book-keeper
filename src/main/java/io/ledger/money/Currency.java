package io.ledger.money;

import java.math.BigDecimal;

/** FR-5. Precision belongs to the currency, never to a global constant. */
public enum Currency {
    AED(2),
    BHD(3);

    private final int scale;

    Currency(int scale) { this.scale = scale; }

    public int scale() { return scale; }

    /** The smallest representable amount: 0.01 for AED, 0.001 for BHD. */
    public BigDecimal unit() { return BigDecimal.ONE.movePointLeft(scale); }
}
