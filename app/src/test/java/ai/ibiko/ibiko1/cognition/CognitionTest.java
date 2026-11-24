package ai.ibiko.ibiko1.cognition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Foresight;
import ai.ibiko.ibiko1.formulas.Truth;

/**
 * The minimum evaluation tests of IBIKO_COGNITIVE_LEARNING.md §7, one for each row of its table, and the edges of each
 * engine. Plain JVM tests: the engines are pure Java.
 */
public class CognitionTest {
    private static final long NOW = 1_800_000_000_000L, DAY = 86_400_000L;

    private static Evidence.Item ev(String id, Truth.Source s, String origin, String text, long at) {
        return new Evidence.Item(id, s, origin, text, at);
    }

    // §7 row 1: two sources directly conflict → detect it, no false certainty
    @Test
    public void twoSourcesThatConflictAreNotCertain() {
        Evidence.Evaluation e = Evidence.evaluate("The bridge is 1,200 metres long.",
                Arrays.asList(ev("ev_1", Truth.Source.SOURCE, "site-a", "The bridge is 1,200 metres long.", NOW)),
                Arrays.asList(ev("ev_2", Truth.Source.SOURCE, "site-b", "The bridge is 1,500 metres long.", NOW)), NOW);
        assertEquals(Evidence.Status.CONTESTED, e.status);
        assertTrue(e.confidence <= 0.5);
        assertEquals(Arrays.asList("ev_2"), e.conflicts);
        assertTrue(e.json(), e.json().contains("\"status\":\"CONTESTED\""));
        // and in one answer: the conflict is found, as real
        List<Consistency.Conflict> c = Consistency.within("The bridge is 1,200 metres long. The bridge is 1.5 km long.");
        assertEquals(1, c.size());
        assertTrue(c.get(0).real);
    }

    // §7 row 2: an old fact against a newer reliable one → the current evidence, timestamps kept
    @Test
    public void theNewerReliableFactStands() {
        Evidence.Item old = ev("ev_old", Truth.Source.SOURCE, "news", "The governor is A.", NOW - 400 * DAY);
        Evidence.Item fresh = ev("ev_new", Truth.Source.SOURCE, "gazette", "The governor is B.", NOW - 2 * DAY);
        assertEquals("ev_new", Consistency.resolve(old, fresh).id);
        assertEquals(NOW - 400 * DAY, old.at); // the history is kept, not rewritten
        // a more trusted source stands over a newer weaker one
        Evidence.Item built = ev("ev_ref", Truth.Source.BUILT_IN, "reference", "The capital is X.", NOW - 900 * DAY);
        Evidence.Item model = ev("ev_mod", Truth.Source.MODEL, "model", "The capital is Y.", NOW);
        assertEquals("ev_ref", Consistency.resolve(built, model).id);
        // a time-sensitive claim on old evidence is marked stale
        assertEquals(Evidence.Freshness.STALE, Evidence.freshness("The governor is A.", NOW - 400 * DAY, NOW));
        assertEquals(Evidence.Freshness.TIMELESS, Evidence.freshness("Water boils at 100 degrees at sea level.", NOW - 4000 * DAY, NOW));
        // and two statements about different times are not a real conflict
        List<Consistency.Conflict> c = Consistency.within("The population was 2 million in 1990. The population is 5 million in 2020.");
        assertEquals(1, c.size());
        assertFalse(c.get(0).real);
    }

    // §7 row 3: the user states an assumption → it stays an assumption, however often it's said
    @Test
    public void anAssumptionStaysAnAssumption() {
        String a = "Let's assume the market grows by 10% a year.";
        assertEquals(Evidence.Type.ASSUMPTION, Evidence.type(a));
        List<Evidence.Item> repeats = new ArrayList<Evidence.Item>();
        for (int i = 0; i < 5; i++) {
            repeats.add(ev("ev_" + i, Truth.Source.PILOT, "chat-" + i, a, NOW));
        }
        Evidence.Evaluation e = Evidence.evaluate(a, repeats, null, NOW);
        assertEquals(Evidence.Type.ASSUMPTION, e.type);
        assertFalse(e.status == Evidence.Status.SUPPORTED);
        assertTrue(e.confidence <= 0.5);
        assertEquals(Evidence.Type.PREFERENCE, Evidence.type("I prefer short answers."));
        assertEquals(Evidence.Type.HYPOTHESIS, Evidence.type("The outage might be caused by the storm."));
        assertEquals(Evidence.Type.INFERENCE, Evidence.type("So the shop is closed on Sundays."));
        assertEquals(Evidence.Type.UNKNOWN, Evidence.type("Who built it?"));
        assertEquals(Evidence.Type.FACT, Evidence.type("Abuja is the capital of Nigeria."));
    }

    // §7 row 4: three copies of one article → not three independent sources
    @Test
    public void threeCopiesAreOneSource() {
        String article = "Kano is the second largest city in Nigeria with a long history of trade across the Sahara.";
        List<Evidence.Item> copies = Arrays.asList(ev("a", Truth.Source.SOURCE, "blog-1", article, NOW), ev("b", Truth.Source.SOURCE, "blog-2",
                article + " Read more.", NOW), ev("c", Truth.Source.SOURCE, "blog-3", "Kano is the second largest city in Nigeria with a long history of trade across the Sahara!", NOW));
        assertEquals(1, Evidence.origins(copies).size());
        Evidence.Evaluation once = Evidence.evaluate(article, copies.subList(0, 1), null, NOW);
        Evidence.Evaluation thrice = Evidence.evaluate(article, copies, null, NOW);
        assertEquals(once.confidence, thrice.confidence, 1e-9);
        assertEquals(1, thrice.independent);
        // a different source is independent
        List<Evidence.Item> two = Arrays.asList(copies.get(0), ev("d", Truth.Source.BUILT_IN, "reference", "Kano, in the north, is Nigeria's second city.", NOW));
        assertEquals(2, Evidence.origins(two).size());
    }

    // §7 row 5: a multi-step calculation → each step checked, and the result
    @Test
    public void everyStepOfTheWorkingIsChecked() {
        assertTrue(Reasoning.checkWorking("24 − 16 repaired − 3 dropped (Musa) = 5").isEmpty());
        assertTrue(Reasoning.checkWorking("4 × 18,000 naira = 72,000 naira").isEmpty());
        assertTrue(Reasoning.checkWorking("2 + 3 × 4 = 14").isEmpty()); // × before +
        List<Reasoning.Wrong> w = Reasoning.checkWorking("40 − 12 sold + 10 bought = 36, so 36 are left.");
        assertEquals(1, w.size());
        assertEquals(38, w.get(0).is, 1e-9);
        assertTrue(Reasoning.repairWorking("40 − 12 sold + 10 bought = 36").contains("= 38"));
        assertTrue("a fraction isn't a sum to check", Reasoning.checkWorking("She sold 3/4 = 0.75 of them").isEmpty());
        assertTrue("x in a word isn't a sign", Reasoning.checkWorking("next 5 = 5").isEmpty());
    }

    // §7 row 6: correlation presented as cause → flagged
    @Test
    public void aCauseClaimedFromALinkIsFlagged() {
        List<String> o = Reasoning.overreach("People who drink coffee tend to live longer. So coffee causes a longer life.");
        assertEquals(1, o.size());
        assertTrue(Reasoning.overreach("Smoking causes cancer by damaging the DNA in lung cells.").isEmpty()); // a mechanism
        assertTrue(Reasoning.overreach("Coffee may cause sleeplessness.").isEmpty()); // hedged
        // and in the graph: a cause backed only by a correlation
        Reasoning g = new Reasoning().node("e1", Reasoning.Node.EVIDENCE, "ice cream sales and drownings went up together")
                .node("x", Reasoning.Node.STEP, "ice cream").node("y", Reasoning.Node.CONCLUSION, "drownings")
                .link("e1", Reasoning.Edge.CORRELATES, "x").link("x", Reasoning.Edge.CAUSES, "y");
        assertEquals(1, g.causalOverreach().size());
    }

    // §7 row 7: key information missing → a focused question or the limit said
    @Test
    public void missingInformationIsNamed() {
        Metacognition.Plan p = Metacognition.plan("Which laptop should I buy?");
        assertFalse(p.missing.isEmpty());
        assertTrue(p.brief(), p.brief().contains("Missing: what matters most to them"));
        assertTrue(Metacognition.plan("How much will it cost?").missing.size() > 0);
        assertTrue(Metacognition.plan("Fix my code").missing.contains("the code itself"));
        assertTrue(Metacognition.plan("What is the capital of Ghana?").missing.isEmpty());
    }

    // §7 row 8: the user corrects an answer → the correction is kept, the wrong item superseded with its history
    @Test
    public void aCorrectionSupersedesTheOldRecord() throws Exception {
        File f = new File(Files.createTempDirectory("learn").toFile(), "learning.jsonl");
        LearningRecords r = new LearningRecords(f);
        LearningRecords.Record wrong = r.learn(LearningRecords.Category.fact, "capital of anambra", "The capital of Anambra State is Onitsha.", "api_model",
                "learning-chat", 0.7, NOW, false);
        assertEquals(LearningRecords.Verification.unverified, wrong.status);
        LearningRecords.Record fixed = r.learn(LearningRecords.Category.correction, "capital of anambra", "The capital of Anambra State is Awka.",
                "explicit_user_correction", "conversation", 0.95, NOW + 1000, false);
        assertEquals(LearningRecords.Verification.user_confirmed, fixed.status);
        assertEquals(wrong.id, fixed.supersedes);
        assertEquals(LearningRecords.Verification.superseded, r.find(wrong.id).status);
        assertEquals(Arrays.asList(fixed.id, wrong.id), Arrays.asList(r.history(fixed.id).get(0).id, r.history(fixed.id).get(1).id));
        assertEquals(1, r.active(NOW + 2000).size());
        // it is kept on disk, in the specification's form, and read back
        LearningRecords again = new LearningRecords(f);
        assertEquals(2, again.all().size());
        String line = Files.readAllLines(f.toPath()).get(1);
        assertTrue(line, line.contains("\"verification_status\":\"user_confirmed\"") && line.contains("\"supersedes\":\"" + wrong.id + "\""));
        // a user's factual claim alone is never verified world knowledge; a second independent source corroborates it
        LearningRecords.Record said = r.learn(LearningRecords.Category.fact, null, "The market opens at 6am.", "user_assertion", "chat-1", 0.9, NOW, false);
        assertEquals(LearningRecords.Verification.unverified, said.status);
        assertTrue(said.confidence <= 0.6);
        r.learn(LearningRecords.Category.fact, null, "The market opens at 6am.", "research", "site-2", 0.7, NOW, false);
        assertEquals(LearningRecords.Verification.corroborated, r.find(said.id).status);
        // a preference is the person's, not the world's
        LearningRecords.Record pref = r.learn(LearningRecords.Category.preference, null, "I prefer short answers.", "user_statement", "chat", 0.9, NOW, false);
        assertEquals("user", pref.scope);
        // secrets are not stored unless asked
        assertNull(r.learn(LearningRecords.Category.fact, null, "My password is hunter2", "user_assertion", "chat", 0.9, NOW, false));
        assertNull(r.learn(LearningRecords.Category.fact, null, "My card is 4111 1111 1111 1111", "user_assertion", "chat", 0.9, NOW, false));
        // forgotten on request
        assertTrue(r.forget(pref.id));
        assertNull(r.find(pref.id));
    }

    // §7 row 9: repeated review finds no defect → it stops, no endless loop
    @Test
    public void aCleanAnswerStopsAtOnce() {
        Metacognition.Plan p = Metacognition.plan("Explain step by step how a bill becomes law in Nigeria and compare it with Ghana.");
        assertEquals(Metacognition.Complexity.COMPLEX, p.complexity);
        assertEquals(3, p.passes);
        String clean = "A bill is read three times in each chamber of the National Assembly, then sent to the President to sign. Ghana has one chamber,"
                + " so a bill there is read in Parliament alone before assent.";
        Metacognition.Outcome o = Metacognition.think(p, clean, Collections.<String>emptyList(), Calibration.Label.HIGH, d -> {
            throw new AssertionError("no brain is asked again for a clean answer");
        });
        assertEquals(clean, o.text);
        assertEquals(2, o.passes); // the plan, then one review that found nothing: stopped
        assertTrue(o.left.isEmpty());
        // a defect that can't be put right ends the passes too (diminishing returns): it is said, not looped on
        Metacognition.Outcome stuck = Metacognition.think(p, "Bananas are yellow and grow in bunches on large plants in warm countries everywhere.",
                Collections.<String>emptyList(), Calibration.Label.HIGH, null);
        assertTrue(stuck.passes <= 4);
        assertFalse(stuck.left.isEmpty());
    }

    // §7 row 10: the reviewer has only a stylistic point → no needless regeneration
    @Test
    public void styleAloneDoesNotRedo() {
        Metacognition.Plan p = Metacognition.plan("hi");
        assertEquals(Metacognition.Complexity.SIMPLE, p.complexity);
        StringBuilder longReply = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            longReply.append("Hello there, it's good to hear from you today. ");
        }
        List<Metacognition.Defect> d = Metacognition.review(p, longReply.toString(), null, Calibration.Label.HIGH);
        assertEquals(1, d.size());
        assertEquals(Metacognition.Kind.LENGTH, d.get(0).kind);
        assertFalse(d.get(0).material());
        Metacognition.Plan normal = Metacognition.plan("Tell me about the history of Lagos and why it grew.");
        Metacognition.Outcome o = Metacognition.think(normal, longReply.toString().replace("Hello there", "Lagos grew"), null, Calibration.Label.HIGH,
                x -> {
                    throw new AssertionError("style is never a reason to ask again");
                });
        assertTrue(o.text.startsWith("Lagos grew"));
    }

    // §7 row 11: a low-confidence answer → the uncertainty is said clearly
    @Test
    public void lowConfidenceIsSaid() {
        Calibration.Confidence weak = new Calibration.Confidence(0.3, 0.9, 0.4, 1.0);
        assertEquals(Calibration.Label.LOW, weak.label());
        assertEquals("evidence", weak.weakest());
        assertEquals("I'm not sure, but ", Calibration.words(weak.label()));
        // one strong dimension doesn't hide a weak one
        assertTrue(new Calibration.Confidence(1, 1, 1, 0.2).overall() < 0.55);
        assertEquals(Calibration.Label.UNVERIFIED, new Calibration.Confidence(0, 1, 1, 1).label());
        assertEquals(Calibration.Label.HIGH, new Calibration.Confidence(0.95, 0.95, 0.9, 1).label());
        // sure words on weak support are softened in Ibiko's own answer, and said in a note under a model's
        Metacognition.Plan p = Metacognition.plan("Will the naira rise next year?");
        Metacognition.Outcome o = Metacognition.think(p, "The naira will definitely rise next year.", null, Calibration.Label.LOW, null);
        assertTrue(o.text, o.text.contains("probably"));
        Cognition.Review r = Cognition.review("Will the naira rise next year?", "The naira will definitely rise next year.", null, null, 0.4, null, false);
        assertNotNull(r.note);
        assertTrue(r.note, r.note.contains("likely, not certain"));
    }

    // §7 row 12: a high-impact recommendation → risks and an alternative are weighed
    @Test
    public void aWeightyActionIsWeighed() {
        Metacognition.Plan p = Metacognition.plan("Should I invest my life savings in bitcoin?");
        assertEquals(Metacognition.Risk.HIGH, p.risk);
        assertEquals(4, p.passes);
        assertTrue(p.brief().contains("High stakes"));
        Foresight.Consequences c = Foresight.weigh("Should I quit my job to start a business?");
        assertNotNull(c);
        assertTrue(c.irreversible);
        assertFalse(c.alternatives.isEmpty());
        assertFalse(c.risks.isEmpty());
        assertTrue(c.said(), c.said().contains("This rests on"));
        assertNull(Foresight.weigh("Should I wear the blue shirt?")); // not of consequence
        assertTrue(Cognition.plan("Should I invest my life savings in bitcoin?").contains("Weigh before advising"));
    }

    // the calibration learns from outcomes: a label that's wrong too often is lowered
    @Test
    public void calibrationLearnsFromOutcomes() throws Exception {
        File f = new File(Files.createTempDirectory("cal").toFile(), "calibration.tsv");
        Calibration cal = new Calibration(f);
        Calibration.Confidence high = new Calibration.Confidence(0.95, 0.95, 0.9, 1);
        assertEquals(Calibration.Label.HIGH, cal.calibrated(high));
        for (int i = 0; i < 12; i++) {
            cal.outcome(Calibration.Label.HIGH, i % 2 == 0); // right only half the time
        }
        assertEquals(Calibration.Label.MODERATE, cal.calibrated(high));
        assertEquals(Calibration.Label.MODERATE, new Calibration(f).calibrated(high)); // kept on disk
    }

    // the reasoning graph keeps a conclusion's grounds, and names the assumption it hangs on
    @Test
    public void aConclusionKeepsItsGrounds() {
        Reasoning g = new Reasoning()
                .node("q", Reasoning.Node.QUESTION, "Will the shop make a profit this month?")
                .node("e1", Reasoning.Node.EVIDENCE, "sales were 400,000 naira last month")
                .node("a1", Reasoning.Node.ASSUMPTION, "sales stay the same")
                .node("s1", Reasoning.Node.STEP, "income about 400,000 naira")
                .node("e2", Reasoning.Node.EVIDENCE, "costs are 300,000 naira")
                .node("c", Reasoning.Node.CONCLUSION, "a profit of about 100,000 naira")
                .link("e1", Reasoning.Edge.SUPPORTS, "s1").link("s1", Reasoning.Edge.DEPENDS_ON, "a1")
                .link("s1", Reasoning.Edge.SUPPORTS, "c").link("e2", Reasoning.Edge.SUPPORTS, "c");
        assertTrue(g.grounds("c").containsAll(Arrays.asList("s1", "e1", "e2", "a1")));
        assertEquals(Arrays.asList("a1"), g.pivotal("c"));
        assertTrue(g.follows("c"));
        g.node("e3", Reasoning.Node.EVIDENCE, "a new tax doubles costs").link("e3", Reasoning.Edge.CONTRADICTS, "e2");
        assertFalse(g.follows("c"));
    }

    // consistency: the same amount in other units is no conflict; a denial is
    @Test
    public void unitsAndDenials() {
        assertTrue(Consistency.within("The road is 5 km long. The road is 5,000 m long.").isEmpty());
        List<Consistency.Conflict> c = Consistency.within("The museum is open on Sundays. The museum is not open on Sundays.");
        assertEquals(1, c.size());
        assertEquals(Consistency.Kind.NEGATION, c.get(0).kind);
        List<Consistency.Conflict> d = Consistency.against("Chinua Achebe was born in 1931.", Arrays.asList("Chinua Achebe was born in 1930."));
        assertEquals(1, d.size());
        assertEquals(Consistency.Kind.DATE, d.get(0).kind);
        assertTrue(d.get(0).real);
    }

    // a model's reply with a wrong step gets a note under it; a clean one gets none
    @Test
    public void aModelsReplyIsReviewed() {
        Cognition.Review bad = Cognition.review("How much did Zainab pay?", "Zainab paid 4 × 18,000 naira = 70,000 naira.", null, null, 0.4, null, false);
        assertNotNull(bad.note);
        assertTrue(bad.note, bad.note.contains("comes to 72,000, not 70,000"));
        Cognition.Review good = Cognition.review("How much did Zainab pay?", "Zainab paid 4 × 18,000 naira = 72,000 naira.", null, null, 0.4, null, false);
        assertNull(good.note);
        // Ibiko's own answer is put right before it is said
        Cognition.Review mine = Cognition.review("How many cables are left?", "40 − 12 sold + 10 bought = 36.", null, null, 0.9, null, true);
        assertTrue(mine.text, mine.text.contains("= 38"));
        assertTrue(Cognition.log().size() > 0);
    }
}
