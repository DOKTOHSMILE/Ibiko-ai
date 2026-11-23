package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Measures;

/** How big, long, high or many a thing is, read from text, kept, and compared. */
public class MeasuresTest {
    private static String read(String text, String topic) {
        return Measures.read(text, topic).toString();
    }

    @Test
    public void measuresAreReadFromSentences() {
        assertEquals("[(Lagos | population | 15,000,000)]", read("Lagos has about 15 million people.", null));
        assertEquals("[(Kano | population | 4,348,000)]", read("The population of Kano is 4,348,000.", null));
        assertEquals("[(The Niger River | length | 4,180 km)]", read("The Niger River is 4,180 km long.", null));
        assertEquals("[(Kilimanjaro | elevation | 5,895 m)]", read("Kilimanjaro rises 5,895 metres above sea level.", null));
        assertEquals("[(Nigeria | area | 923,768 square km)]", read("It covers 923,768 square kilometres.", "Nigeria"));
        assertEquals("[(Ibadan | population | 3,500,000)]", read("About 3.5 million people live in Ibadan.", null));
    }

    @Test
    public void aYearOrACodeIsNotAMeasure() {
        assertEquals("[]", read("Lagos has 1960 as its founding year.", null));
        assertEquals("[]", read("Lagos has PIN 1960.", null));
    }

    @Test
    public void whatItReadsIsComparedLikeWhatItShippedWith() throws Exception {
        java.io.File dir = java.nio.file.Files.createTempDirectory("facts").toFile();
        Facts memory = new Facts(new java.io.File(dir, "facts.tsv"));
        // two towns the built-in facts don't have: only what it was told
        memory.learn("Oghara has about 120,000 people. Ozoro has about 95,000 people.", null, 2, 1L);
        assertEquals("120,000", memory.measure("Oghara", "population"));
        Composer c = new Composer(new BuiltIn(), null, null, null).measures(memory::measure);
        Reasoner.Result r = c.answer("Which is bigger, Oghara or Ozoro?");
        assertTrue(String.valueOf(r == null ? null : r.text), r != null && r.text.startsWith("Oghara is bigger"));
        assertTrue(r.steps.toString(), r.steps.toString().contains("read and remembered"));
    }
}
