package ai.ibiko.ibiko1.formulas;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.junit.Test;

/** Wisdom applies what a sentence implies. It does not hand the stored words back as the answer. */
public class WisdomTest {
    @Test
    public void aLimitIsUnderstoodAndThenApplied() {
        Understanding.Implication u = Understanding.of("I can't afford the deposit and I might leave in a year.");
        assertTrue(u.follows, u.follows.toLowerCase().contains("limit"));
        assertTrue(u.follows.toLowerCase().contains("not settled") || u.follows.toLowerCase().contains("possible"));
        assertTrue(u.line().startsWith("Implies:"));
        Wisdom.Applied w = Wisdom.of("Should I rent if I can't afford the deposit?",
                Arrays.asList("I can't afford the deposit and I might leave in a year.", "Bananas are yellow."));
        assertTrue(w.known.contains("deposit"));
        assertFalse("a sentence that shares no word of the question is not knowledge for it", w.known.contains("Bananas"));
        assertTrue(w.implies.toLowerCase().contains("limit"));
        assertTrue(w.apply.contains("not from repeating"));
        assertTrue(w.brief().contains("Wisdom 3.0"));
    }

    @Test
    public void nothingStoredStillReadsTheQuestion() {
        Wisdom.Applied w = Wisdom.of("Should I rent?", null);
        assertTrue(w.known.isEmpty());
        assertTrue(w.apply.toLowerCase().contains("question"));
    }
}
