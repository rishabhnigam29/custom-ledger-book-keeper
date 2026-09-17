package io.ledger.model;

/**
 * A day index in the replay window, 1..6.
 *
 * <p>A wrapper rather than a bare int so a value date can never be passed where a posting day is
 * expected. Confusing the two is the bug this whole system exists to prevent, and the type system
 * is the cheapest place to stop it.
 */
public record Day(int index) implements Comparable<Day> {

    public Day {
        if (index < 1) throw new IllegalArgumentException("day index must be >= 1, was " + index);
    }

    public static Day of(int index) { return new Day(index); }

    public boolean isOnOrBefore(Day other) { return index <= other.index; }

    public boolean isBefore(Day other) { return index < other.index; }

    @Override public int compareTo(Day other) { return Integer.compare(index, other.index); }

    @Override public String toString() { return "D" + index; }
}
