package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.SearchPhrase;

/** Questions phrased as search words for the most results, from the shipped rules. */
public class SearchPhraseTest {
    private static SearchPhrase rules() throws Exception {
        return new SearchPhrase(TextCode.open(p -> {
            try {
                return new FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/language/search_phrasing.tsv"));
    }

    @Test
    public void questionsBecomeSearchWords() throws Exception {
        SearchPhrase s = rules();
        assertTrue(s.size() > 100);
        assertEquals("Things Fall Apart author", s.words("Can you please tell me who wrote Things Fall Apart?", 2026));
        assertEquals("Nelson Mandela date of birth", s.words("When was Nelson Mandela born?", 2026));
        assertEquals("Nigeria population", s.words("what is the population of Nigeria", 2026));
        assertEquals("ubiquitous meaning", s.words("What does ubiquitous mean?", 2026));
        assertEquals("how to make jollof rice", s.words("hey Ibiko, how do I make jollof rice?", 2026));
        assertEquals("Eiffel Tower height", s.words("How tall is the Eiffel Tower?", 2026));
        assertEquals("Lagos to Abuja distance", s.words("How far is Lagos from Abuja?", 2026));
        assertEquals("python vs java", s.words("What's the difference between python and java?", 2026));
        assertEquals("malaria symptoms", s.words("What are the symptoms of malaria?", 2026));
        assertEquals("why sky blue", s.words("Why is the sky blue?", 2026));
        assertEquals("photosynthesis", s.words("I want to know what is photosynthesis", 2026));
    }

    @Test
    public void nowBringsThisYear() throws Exception {
        SearchPhrase s = rules();
        assertEquals("Nigeria president 2026", s.words("Who is the president of Nigeria now?", 2026));
        assertEquals("Ghana latest news 2026", s.words("What's happening in Ghana today?", 2026));
        assertEquals("a year given is kept, not added", "current price bitcoin 2025", s.words("current price of bitcoin 2025", 2026));
    }

    @Test
    public void noShapeKeepsTheWordsThatMatter() throws Exception {
        SearchPhrase s = rules();
        assertEquals("cheap flights Lagos London", s.words("Are there cheap flights from Lagos to London?", 2026));
        assertEquals("\"to be or not to be\" speech", s.words("tell me about the \"to be or not to be\" speech", 2026));
        assertEquals("phones without headphone jack", s.words("phones without a headphone jack", 2026));
    }

    @Test
    public void broaderWhenNothingIsFound() throws Exception {
        SearchPhrase s = rules();
        List<String> t = s.tries("Who wrote Things Fall Apart?", 2026);
        assertEquals("Things Fall Apart author", t.get(0));
        assertEquals("Things Fall Apart", t.get(1));
        assertEquals("Who wrote Things Fall Apart", t.get(2));
    }
}
