package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Causes;

/** Cause and effect read from text, a why-question matched to it, and the reason behind the reason. */
public class CausesTest {
    private static Causes.Link one(String text) {
        List<Causes.Link> l = Causes.read(text, null);
        assertEquals(text + " → " + l, 1, l.size());
        return l.get(0);
    }

    @Test
    public void readsEveryWayAReasonIsSaid() {
        Causes.Link a = one("Plants are green because of chlorophyll.");
        assertEquals("Plants are green", a.effect);
        assertEquals("because of chlorophyll", a.because());
        Causes.Link b = one("The harvest was poor because the rains failed.");
        assertEquals("because the rains failed", b.because());
        Causes.Link c = one("Because the rains failed, the harvest was poor.");
        assertEquals("the harvest was poor", c.effect);
        assertEquals("the rains failed", c.cause);
        Causes.Link d = one("The long drought led to a famine.");
        assertEquals("a famine", d.effect);
        assertEquals("because of the long drought", d.because());
        Causes.Link e = one("It rained all night, so the match was cancelled.");
        assertEquals("the match was cancelled", e.effect);
        List<Causes.Link> f = Causes.read("The river flooded. As a result, the town was moved.", null);
        assertEquals(1, f.size());
        assertEquals("the town was moved", f.get(0).effect);
        assertEquals("The river flooded", f.get(0).cause);
        Causes.Link g = one("Flights were delayed due to heavy fog.");
        assertEquals("because of heavy fog", g.because());
        Causes.Link h = one("Volcanism away from plate boundaries is caused by mantle plumes.");
        assertEquals("Volcanism away from plate boundaries", h.effect);
        assertEquals("because of mantle plumes", h.because());
        List<Causes.Link> i = Causes.read("Many birds fly south in winter. This is because food is scarce in the north.", null);
        assertEquals(1, i.size());
        assertEquals("Many birds fly south in winter", i.get(0).effect);
        assertEquals("because food is scarce in the north", i.get(0).because());
        Causes.Link j = one("A leaf is thin so that sunlight can reach the chloroplasts.");
        assertEquals("A leaf is thin", j.effect);
        assertEquals("so that sunlight can reach the chloroplasts", j.because());
        assertTrue(j.purpose());
    }

    @Test
    public void notEverySinceOrQuestionIsAReason() {
        assertTrue(Causes.read("She has lived in Lagos since 1990.", null).isEmpty());
        assertTrue(Causes.read("Why did the bridge fall?", null).isEmpty());
        assertTrue(Causes.read("Lagos is a large city.", null).isEmpty());
        // what people thought would happen explains nothing about what did
        assertTrue(Causes.read("Before the Titanic sailed, many people thought it would be almost impossible for ships of this design to sink, "
                + "due to her watertight bulkheads.", null).isEmpty());
        assertTrue(Causes.read("This can be caused by many things.", null).isEmpty());
    }

    @Test
    public void aWhyQuestionFindsTheEffectItAsksAbout() {
        List<Causes.Link> links = new ArrayList<Causes.Link>();
        links.addAll(Causes.read("Plants are green because of chlorophyll.", null));
        links.addAll(Causes.read("The harvest was poor because the rains failed.", null));
        links.addAll(Causes.read("The rains failed because the winds shifted south.", null));
        Causes.Why w = Causes.why("Why are plants green?", links);
        assertNotNull(w);
        assertEquals("Because of chlorophyll.", w.said());
        Causes.Why h = Causes.why("Why was the harvest poor?", links);
        assertEquals("Because the rains failed, which came about because the winds shifted south.", h.said());
        assertNull("one shared word of several is not the thing asked", Causes.why("Why is the sky green at night?", links));
        assertNull(Causes.behind(h.last(), links));
    }

    @Test
    public void irregularPastsMatch() {
        List<Causes.Link> links = Causes.read("The Roman Empire fell because of invasions and a weak economy.", null);
        Causes.Why w = Causes.why("Why did the Roman Empire fall?", links);
        assertNotNull(w);
        assertEquals("Because of invasions and a weak economy.", w.said());
    }

    @Test
    public void aReasonGivenIsFormedIntoASentence() {
        assertEquals("The sky is blue because of scattering of light.", Causes.form("Why is the sky blue?", "scattering of light"));
        assertEquals("Birds migrate because the winters are cold.", Causes.form("Why do birds migrate?", "the winters are cold"));
        assertEquals("The bridge fell because of rust.", Causes.form("Why did the bridge fall?", "rust"));
        assertEquals("The rains failed because the winds shifted.", Causes.form("Why the rains failed?", "the winds shifted"));
        assertEquals("Nigeria is called the giant of Africa because of its size.",
                Causes.form("Why is Nigeria called the giant of Africa?", "because of its size"));
    }

    @Test
    public void readsWhatAQuestionAsksTheOutcomeOf() {
        assertEquals("the rains fail", Causes.ahead("What happens if the rains fail?"));
        assertEquals("deforestation", Causes.ahead("What are the effects of deforestation?"));
        assertEquals("smoking", Causes.ahead("What does smoking lead to?"));
        assertEquals("the river floods", Causes.ahead("What would happen when the river floods?"));
        assertNull(Causes.ahead("Why did the river flood?"));
        assertNull(Causes.ahead("What is a river?"));
    }

    @Test
    public void followsCausesForwardOneStepFurther() {
        List<Causes.Link> links = new ArrayList<Causes.Link>();
        links.addAll(Causes.read("The harvest was poor because the rains failed.", null));
        links.addAll(Causes.read("A poor harvest leads to rising food prices.", null));
        links.addAll(Causes.read("Smoking causes lung cancer.", null));
        links.addAll(Causes.read("A leaf is thin so that sunlight can reach the chloroplasts.", null));
        Causes.Then t = Causes.then("What happens if the rains fail?", links);
        assertNotNull(t);
        assertEquals("The harvest was poor, which leads to rising food prices.", t.said());
        assertEquals("Lung cancer.", Causes.then("What does smoking lead to?", links).said());
        assertNull("nothing read starts from it: no outcome is guessed", Causes.then("What happens if the sun goes out?", links));
        assertNull("a purpose is not an outcome", Causes.then("What happens if a leaf is thin?", links));
    }
}
