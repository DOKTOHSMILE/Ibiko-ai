package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Expressions;
import ai.ibiko.ibiko1.formulas.Spelling;
import ai.ibiko.ibiko1.words.Phonetic;

/** Typos put right by sound, sayings explained, and the words the dictionary lacked. */
public class WordsAndSayingsTest {
    private static java.io.InputStream asset(String p) {
        return TextCode.open(x -> {
            try {
                return new FileInputStream(new File("src/main/assets/" + x));
            } catch (java.io.IOException e) {
                return null;
            }
        }, p);
    }

    @Test
    public void wordsThatSoundAlikeShareAKey() {
        assertEquals(Phonetic.key("phone"), Phonetic.key("fone"));
        assertEquals(Phonetic.key("knowledge"), Phonetic.key("nolij"));
        assertEquals(Phonetic.key("school"), Phonetic.key("skool"));
        assertEquals(Phonetic.key("believe"), Phonetic.key("beleive"));
        assertEquals(Phonetic.key("capital"), Phonetic.key("captal"));
        assertEquals(Phonetic.key("night"), Phonetic.key("nite"));
        assertTrue(!Phonetic.key("cat").equals(Phonetic.key("dog")));
        assertEquals(1, Phonetic.distance("spidr", "spider", 3));
        assertEquals(2, Phonetic.distance("fone", "phone", 3));
    }

    @Test
    public void aTypoFarInLettersIsFoundByItsSound() {
        Map<String, List<String>> bySound = new HashMap<String, List<String>>();
        bySound.put(Phonetic.key("phone"), Arrays.asList("phone"));
        bySound.put(Phonetic.key("knowledge"), Arrays.asList("knowledge"));
        Spelling.Fixed f = Spelling.fix("how do i charge my fone?", w -> w.equals("charge"), w -> w.equals("phone") || w.equals("knowledge"),
                w -> 4, w -> bySound.getOrDefault(Phonetic.key(w), Arrays.<String>asList()));
        assertNotNull(f);
        assertEquals("how do i charge my phone?", f.text);
        Spelling.Fixed g = Spelling.fix("what is nolij?", w -> false, w -> w.equals("knowledge"), w -> 4,
                w -> bySound.getOrDefault(Phonetic.key(w), Arrays.<String>asList()));
        assertEquals("what is knowledge?", g.text);
    }

    @Test
    public void sayingsAreExplained() {
        Expressions e = new Expressions(asset("knowledge/language/expressions.tsv"));
        assertTrue(e.size() > 200);
        String riddle = e.explain("Me myself and I\nJust the 3 of us\n🤔 What does this implicate?", null);
        assertNotNull(riddle);
        assertTrue(riddle, riddle.contains("myself alone"));
        assertTrue(riddle, riddle.contains("it's only you"));
        String luck = e.explain("Break a leg!", null);
        assertNotNull(luck);
        assertTrue(luck, luck.toLowerCase().contains("luck"));
        assertNotNull("after it was said", e.explain("What does that mean?", "No wahala, I'll do it."));
        assertNull("a saying's words in a plain question aren't asked about", e.explain("How far is Lagos from Abuja?", null));
    }

    @Test
    public void wordsTheDictionaryLackedAreThere() throws Exception {
        java.io.InputStream in = asset("knowledge/lexicon_more.tsv");
        assertNotNull(in);
        String all = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        for (String w : new String[]{"they", "went", "wahala", "japa", "selfie", "garri"}) {
            assertTrue(w, all.contains("\n" + w + "\t") || all.startsWith(w + "\t"));
        }
    }
}
