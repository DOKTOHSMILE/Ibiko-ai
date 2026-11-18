package ai.ibiko.ibiko1.formulas;

import static org.junit.Assert.assertEquals;
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
        assertTrue(w.assumes.toLowerCase().contains("limit"));
        assertTrue(w.doesNotSay.toLowerCase().contains("bad"));
        assertTrue(w.undone.toLowerCase().contains("lifted"));
        assertEquals(1, w.evidenceCount);
        assertTrue(w.apply.contains("not from repeating"));
        assertTrue(w.brief().contains("ibiko1 reasoning"));
        assertFalse("Wisdom is merged into ibiko1, not a separate level", w.brief().contains("Wisdom 3.0"));
        assertTrue("the brief can be built from the reasoning module directly",
                Wisdom.brief("Should I rent?", Arrays.asList("I can't afford the deposit.")).contains("ibiko1 reasoning"));
    }

    @Test
    public void nothingStoredStillReadsTheQuestion() {
        Wisdom.Applied w = Wisdom.of("Should I rent?", null);
        assertTrue(w.known.isEmpty());
        assertTrue(w.apply.toLowerCase().contains("question"));
    }
    @Test
    public void theMostRelevantEvidenceIsAppliedBeforeWeakMatches() {
        Wisdom.Applied w = Wisdom.of("Should I rent if I cannot afford the deposit?", Arrays.asList(
                "A deposit is sometimes refundable after a long inspection process.",
                "Renting requires paying a deposit and ongoing rent.",
                "A deposit is sometimes refundable after a long inspection process."));
        assertEquals(2, w.evidenceCount);
        assertTrue(w.known.indexOf("Renting requires") < w.known.indexOf("A deposit is sometimes"));
    }

    @Test
    public void distinctiveEvidenceOutranksGenericOneWordOverlap() {
        Wisdom.Applied w = Wisdom.of("What causes engine overheating under load?", Arrays.asList(
                "Engine maintenance matters for vehicle performance.",
                "An engine can overheat when coolant circulation fails under heavy load."));
        assertTrue(w.known.indexOf("coolant circulation") < w.known.indexOf("Engine maintenance"));
    }

    @Test
    public void unrelatedEvidenceDoesNotEnterTheWisdomBrief() {
        Wisdom.Applied w = Wisdom.of("How does an engine power a vehicle?", Arrays.asList(
                "Bananas are yellow.",
                "An engine converts fuel or electrical energy into mechanical power."));
        assertEquals(1, w.evidenceCount);
        assertTrue(w.known.contains("engine converts"));
        assertFalse(w.known.contains("Bananas"));
    }

}
