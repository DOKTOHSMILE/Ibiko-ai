package ai.ibiko.ibiko1.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Semantic intelligence (how words relate) and the word record (a word classified in its sentence). */
public class WordsEngineTest {
    /** A small dictionary and concept net, in the shapes the real ones give. */
    static final Relations.Source SOURCE = new Relations.Source() {
        final Map<String, List<String[]>> senses = new HashMap<String, List<String[]>>();
        {
            sense("happy", "adjective", "enjoying or showing joy", "", "glad,joyful,content", "unhappy,sad");
            sense("joyful", "adjective", "full of joy", "", "happy,glad", "");
            sense("hot", "adjective", "having a high temperature", "", "warm", "cold");
            sense("dog", "noun", "a domesticated carnivore kept as a pet", "canine,domestic animal", "", "");
            sense("bank", "noun", "sloping land beside a river", "slope", "", "");
            sense("bank", "noun", "a financial institution that accepts deposits", "financial institution", "", "");
            sense("head", "noun", "the upper part of the body", "body part", "", "");
            sense("head", "noun", "the person in charge of a group", "leader", "", "");
            sense("run", "verb", "move fast on foot", "", "", "");
            sense("run", "noun", "the act of running", "", "", "");
            sense("happiness", "noun", "the state of being happy", "", "", "unhappiness");
            sense("lock", "verb", "fasten with a lock", "", "", "unlock");
        }

        void sense(String w, String... s) {
            senses.computeIfAbsent(w, k -> new ArrayList<String[]>()).add(new String[]{s[0], s[1], s[2], s[3], s[4]});
        }

        @Override
        public List<String[]> senses(String word) {
            return senses.getOrDefault(word, new ArrayList<String[]>());
        }

        @Override
        public List<String> linked(String word, String relation) {
            if (word.equals("car") && relation.equals("HasPart")) {
                return Arrays.asList("wheel", "engine", "door");
            }
            if (word.equals("wheel") && relation.equals("PartOf")) {
                return Arrays.asList("car", "bicycle");
            }
            return new ArrayList<String>();
        }

        @Override
        public List<String> linkedTo(String word, String relation) {
            if (word.equals("animal") && relation.equals("IsA")) {
                return Arrays.asList("dog", "cat", "horse");
            }
            return new ArrayList<String>();
        }
    };

    @Test
    public void howWordsRelate() {
        Relations r = new Relations(SOURCE, null);
        Map<String, List<String>> happy = r.of("happy");
        assertTrue(happy.get("synonyms").contains("joyful"));
        assertTrue(happy.get("antonyms").contains("sad"));
        assertTrue(r.of("dog").get("hypernyms").contains("canine"));
        assertTrue(r.of("animal").get("hyponyms").contains("dog"));
        assertTrue(r.of("car").get("meronyms").contains("wheel"));
        assertTrue(r.of("wheel").get("holonyms").contains("car"));
        assertTrue(r.of("their").get("homophones").contains("there"));
        assertTrue(r.of("lead").containsKey("homograph"));
        assertTrue(r.of("bank").containsKey("homonym"));
        assertTrue(r.of("head").containsKey("polysemy") || r.of("head").containsKey("homonym"));
        assertTrue(r.of("make").get("collocations").contains("make a decision"));
        assertTrue(r.of("sanction").get("contronym").contains("to approve"));
        assertTrue(r.between("happy", "joyful").contains("synonyms"));
        assertTrue(r.between("hot", "cold").contains("antonyms"));
        assertTrue(r.between("wheel", "car"), r.between("wheel", "car").contains("meronym of “car”"));
        assertTrue(r.between("animal", "dog").contains("hypernym"));
        assertNull(r.between("happy", "wheel"));
    }

    @Test
    public void aWordIsClassifiedInItsSentence() {
        WordRecord r = WordRecord.of("running", null, SOURCE);
        assertEquals("run", r.lemma);
        assertTrue(r.categories.containsAll(Arrays.asList("verb", "noun", "adjective")));
        assertEquals("ing", r.morphology.get("suffix"));
        assertEquals("run", r.morphology.get("root"));
        assertEquals("present_participle_or_gerund", r.features.get("verb_form"));
        assertTrue(r.contextRequired);
        assertEquals("verb", WordRecord.of("running", "I am running.", SOURCE).inSentence.get("role"));
        assertEquals("gerund", WordRecord.of("running", "Running is healthy.", SOURCE).inSentence.get("role"));
        assertEquals("participle", WordRecord.of("running", "Running water is clean.", SOURCE).inSentence.get("role"));
        String json = r.json();
        assertTrue(json, json.contains("\"lemma\":\"run\"") && json.contains("\"possible_categories\""));
        WordRecord u = WordRecord.of("unhappiness", null, SOURCE);
        assertEquals("un", u.morphology.get("prefix"));
        assertEquals("ness", u.morphology.get("suffix"));
        assertEquals("written", WordRecord.of("written", null, SOURCE).word);
        assertEquals("write", WordRecord.of("written", null, SOURCE).lemma);
        assertEquals("past_participle", WordRecord.of("written", null, SOURCE).features.get("verb_form"));
        assertTrue(WordRecord.of("running", "Running is healthy.", SOURCE).said().contains("gerund, acting as a noun"));
    }
}
