package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Critical;

import org.junit.Test;

/** Critical reasoning: a request is questioned before it is answered, and an answer is never surer than its ground. */
public class CriticalTest {
    @Test
    public void eachKindOfQuestionIsKnown() {
        assertEquals(Critical.Kind.CAUSE, Critical.kind("why is the sky blue"));
        assertEquals(Critical.Kind.JUDGEMENT, Critical.kind("which is better, renting or buying"));
        assertEquals(Critical.Kind.PREDICTION, Critical.kind("will the naira get stronger next year"));
        assertEquals(Critical.Kind.COMPARISON, Critical.kind("is gold heavier than silver"));
        assertEquals(Critical.Kind.HOWTO, Critical.kind("how do i reset my phone"));
        assertEquals(Critical.Kind.FACT, Critical.kind("who invented the telephone"));
    }

    @Test
    public void whatTheQuestionTakesForGrantedIsFound() {
        Critical.Reading r = Critical.read("Why do all vaccines cause autism?");
        assertTrue("the premise", r.has("K2"));
        assertTrue(r.premise, r.premise.contains("vaccines cause autism"));
        assertTrue("the absolute", r.has("K3"));
        assertTrue("the cause", r.has("K5"));
        assertTrue(Critical.read("Experts say coffee is bad, is it?").has("K6"));
        assertTrue(Critical.read("Will Bitcoin hit 200k next year?").has("K8"));
        assertTrue(Critical.read("Should I learn Python or Java?").has("K4"));
        assertFalse("a plain fact has nothing to question", Critical.read("What is the capital of Ghana?").findings.size() > 0);
    }

    @Test
    public void everyBrainGetsTheChecksForThisRequest() {
        String brief = Critical.brief(Critical.read("Why do all vaccines cause autism?"));
        assertTrue(brief, brief.contains("Check that premise"));
        assertTrue(brief.contains("correlation"));
        assertTrue(brief.contains("how sure"));
    }

    @Test
    public void sureNeverOutrunsTheGround() {
        assertTrue(Critical.sure(Critical.Ground.CONFIRMED).startsWith("Certain"));
        assertTrue(Critical.sure(Critical.Ground.ONE_SOURCE).startsWith("Likely"));
        assertTrue(Critical.sure(Critical.Ground.UNCHECKED).startsWith("Not checked"));
    }

    @Test
    public void aDecisionGetsTheWayToDecideNotAnInventedVerdict() {
        assertTrue(Critical.decision("Should I rent or buy a house?"));
        assertTrue(Critical.decision("Which is better, Lagos or Abuja?"));
        assertFalse(Critical.decision("What is the best way to boil an egg"));
        String d = Critical.deliberate("Which is better, Lagos or Abuja?");
        assertTrue(d, d.contains("Lagos") && d.contains("Abuja"));
        assertTrue(d.contains("both, neither or a third option"));
        assertTrue(d.contains("best case for the option you lean against"));
        assertTrue("no invented winner", d.contains("Lean: none"));
    }

    @Test
    public void aChoiceIsWeighedFromWhatWasSaidNotFromATemplate() {
        assertTrue(Critical.decision("I'm torn between staying and leaving"));
        assertFalse(Critical.decision("What is the best way to boil an egg"));
        Critical.Verdict open = Critical.decide("Which is better, Lagos or Abuja?");
        assertEquals("place", open.domain);
        assertEquals(null, open.lean);
        Critical.Verdict housed = Critical.decide("Should I rent or buy a house? I might leave in a year and I have a small deposit.");
        assertEquals("rent", housed.lean);
        assertTrue(housed.because, housed.because.toLowerCase().contains("money") || housed.because.toLowerCase().contains("short"));
        assertTrue(housed.confidence >= 40 && housed.confidence <= 78);
        Critical.Verdict broke = Critical.decide("I prefer to buy, but I can't afford the deposit. Should I rent or buy a house?");
        assertEquals("rent", broke.lean);
        assertTrue(broke.because.toLowerCase(), broke.because.toLowerCase().contains("money"));
    }
}
