package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

/** Answers put together from facts: ages at events, who came first, the years between, comparisons, the most in a place. */
public class ComposerTest {
    private static Composer composer;

    static synchronized Composer c() throws Exception {
        if (composer == null) {
            BuiltIn b = new BuiltIn(new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/world_facts.tsv")),
                    new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/country_more.tsv")),
                    new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/wikidata_facts.tsv")));
            final Encyclopedia e = new Encyclopedia(p -> {
                try {
                    return new FileInputStream(new java.io.File("src/main/assets/" + p));
                } catch (java.io.IOException x) {
                    return null;
                }
            });
            new ai.ibiko.ibiko1.words.Meanings(ai.ibiko.ibiko1.words.MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")));
            composer = new Composer(b, e::find); // events are read from the encyclopedia, not listed
        }
        return composer;
    }

    private static String a(String q) throws Exception {
        Reasoner.Result r = c().answer(q);
        System.out.println("Q " + q + "\n  -> " + (r == null ? null : r.text) + (r == null ? "" : "\n     " + r.steps));
        return r == null ? null : r.text;
    }

    @Test
    public void anAgeAtAnEventIsWorkedOutFromWhatItReads() throws Exception {
        // Achebe's birth from the facts; Nigeria's independence read from its encyclopedia article ("The country gained
        // independence on 1 October 1960"): no list of events anywhere
        assertEquals("Chinua Achebe was 29 when Nigeria became independent: born on 16 November 1930, and Nigeria became"
                + " independent on 1 October 1960.", a("How old was Chinua Achebe when Nigeria became independent?"));
        assertTrue(a("How old was Achebe when Nigeria gained independence?").startsWith("Chinua Achebe was 29"));
        assertTrue(a("How old was Wole Soyinka when World War II ended?").startsWith("Wole Soyinka was 11 when World War II ended"));
        assertTrue(a("How old was Nelson Mandela when he became President of South Africa?")
                .startsWith("Nelson Mandela was 75 when he became President of South Africa"));
        assertTrue(a("At what age did Chinua Achebe die?").startsWith("Chinua Achebe was 82 at death"));
        assertTrue(a("How old was Chinua Achebe when he died?").startsWith("Chinua Achebe was 82 at death"));
        assertNull("its article gives no date: nothing is made up", a("How old was Chinua Achebe when the Nigerian Civil War began?"));
    }

    @Test
    public void whoCameFirstAndTheYearsBetween() throws Exception {
        assertTrue(a("Who is older, Chinua Achebe or Wole Soyinka?").startsWith("Chinua Achebe is older"));
        assertTrue(a("Who is younger, Achebe or Soyinka?").startsWith("Wole Soyinka is younger"));
        assertTrue(a("Which came first, the telephone or the light bulb?").startsWith("The telephone came first"));
        assertEquals("20 years: the first Moon landing on 20 July 1969, and the fall of the Berlin Wall on 9 November 1989.",
                a("How many years between the first Moon landing and the fall of the Berlin Wall?"));
        assertTrue(a("How many years after World War II began did Nigeria become independent?").startsWith("21 years"));
        assertEquals("Yes: Chinua Achebe was 58 when the Berlin Wall fell (on 9 November 1989).",
                a("Was Chinua Achebe alive when the Berlin Wall fell?"));
        assertTrue(a("Was Wole Soyinka born when World War II began?").startsWith("Yes: Wole Soyinka was 5"));
        assertTrue(a("Was Nelson Mandela alive when Nigeria became independent?").startsWith("Yes"));
    }

    @Test
    public void itRemembersWhatItReadsAndWhatItIsTold() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("facts").toFile();
        final Facts memory = new Facts(new java.io.File(dir, "facts.tsv"));
        long now = System.currentTimeMillis();
        // told by the pilot (sure): Ghana's article gives no date, so only memory can answer
        memory.learn("Ghana became independent on 6 March 1957.", null, 2, now);
        assertTrue(memory.happenings("Ghana").toString(), memory.happenings("Ghana").size() == 1);
        BuiltIn b = new BuiltIn(new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/wikidata_facts.tsv")));
        final Encyclopedia e = new Encyclopedia(p -> {
            try {
                return new FileInputStream(new java.io.File("src/main/assets/" + p));
            } catch (java.io.IOException x) {
                return null;
            }
        });
        Composer c = new Composer(b, e::find, memory::happenings, a -> memory.learn(a.text, a.title, 2, System.currentTimeMillis()));
        Reasoner.Result ghana = c.answer("How old was Chinua Achebe when Ghana became independent?");
        assertTrue(String.valueOf(ghana == null ? null : ghana.text), ghana != null && ghana.text.startsWith("Chinua Achebe was 26 when Ghana became independent"));
        assertTrue(ghana.steps.toString(), ghana.steps.toString().contains("remembered"));
        // read in the encyclopedia for an answer: kept in memory, so the next time it is remembered, not read again
        assertTrue(memory.happenings("Nigeria").isEmpty());
        Reasoner.Result nigeria = c.answer("How old was Chinua Achebe when Nigeria became independent?");
        assertTrue(nigeria.text, nigeria.text.startsWith("Chinua Achebe was 29"));
        assertTrue(nigeria.steps.toString().contains("read just now"));
        assertTrue("what it read is in memory: " + memory.happenings("Nigeria"), !memory.happenings("Nigeria").isEmpty());
        Reasoner.Result again = new Composer(b, null, memory::happenings, null).answer("How old was Achebe when Nigeria gained independence?");
        assertTrue(String.valueOf(again == null ? null : again.text), again != null && again.text.startsWith("Chinua Achebe was 29"));
        // a model's word alone (seen once) isn't reasoned with; said again by another source, it is
        memory.learn("Togo became independent on 27 April 1960.", null, 1, now);
        assertTrue(memory.happenings("Togo").isEmpty());
        memory.learn("Togo became independent on 27 April 1960.", null, 1, now);
        assertTrue(!memory.happenings("Togo").isEmpty());
    }

    @Test
    public void thingsAreComparedAndRanked() throws Exception {
        assertTrue(a("Which is longer, the Niger or the Nile?").startsWith("Nile is longer: Nile 6,650 km, Niger River 4,180 km"));
        assertTrue(a("Which is higher, Mount Kilimanjaro or Mount Kenya?").startsWith("Mount Kilimanjaro is higher"));
        assertTrue(a("Which is bigger, Lagos or Kano?").startsWith("Lagos is bigger"));
        assertTrue(a("What is the longest river in Nigeria?").contains("is Niger River (4,180 km)"));
        assertTrue(a("What is the highest mountain in Tanzania?").contains("is Mount Kilimanjaro (5,895 m)"));
        assertTrue(a("What is the biggest city in Nigeria?").contains("is Lagos"));
    }

    @Test
    public void aChainReadsThePersonFirst() throws Exception {
        assertTrue(a("When was the inventor of the telephone born?").contains("Alexander Graham Bell was born on 3 March 1847"));
        assertTrue(a("How old was the inventor of the telephone when he invented it?").contains("was about 29 when the telephone was invented"));
    }

    @Test
    public void whatItCantPutTogetherIsLeftToTheRest() throws Exception {
        assertNull(a("What is the capital of Ghana?"));
        assertNull(a("How old is the universe?"));
        assertNull("two countries: the reasoner over countries", a("Which is bigger, Nigeria or Ghana?"));
        assertNull("no date for the radio: nothing made up", a("Which came first, the telephone or the radio?"));
        assertNull(a("How long is the Niger River?"));
        assertEquals(1930, Composer.date("16 November 1930").y);
        assertEquals(11, Composer.date("16 November 1930").m);
        assertEquals(-3000, Composer.date("3000 BC").y);
        assertNull(Composer.date("12"));
    }
}
