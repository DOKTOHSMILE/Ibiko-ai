package ai.ibiko.intelligence;

import static org.junit.Assert.*;

import ai.ibiko.ibiko1.formulas.PartsOfSpeech;
import ai.ibiko.intelligence.context.CoreferenceResolver;
import ai.ibiko.intelligence.context.IntentClassifier;
import ai.ibiko.intelligence.context.TemporalSpatialAnalyzer;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Small deterministic evaluation set for the local, rule-based comprehension components. */
public class IntelligenceEvaluationTest {
    @Test public void labelsRepresentativeUserIntents() {
        String[][] cases = {
            {"What is photosynthesis?", "QUESTION"}, {"Explain photosynthesis", "EXPLANATION"},
            {"Create a logo", "CREATION"}, {"Search for a supplier", "SEARCH"},
            {"Compare Java and Python", "COMPARISON"}, {"Translate this to Hausa", "TRANSLATION"},
            {"Continue the implementation", "CONTINUATION"}, {"Hello", "GREETING"},
            {"Thank you", "THANKS"}, {"I prefer concise answers", "FEEDBACK"},
            {"The engine is overheating", "STATEMENT"}, {"Could you explain this?", "EXPLANATION"},
            {"Please delete the file", "DELETION"}, {"What if I delete the file?", "QUESTION"},
            {"Do not delete the file", "DELETION"},
            {"Can you find the nearest clinic?", "SEARCH"}, {"Plan a 3 day trip", "PLANNING"}
        };
        for (String[] row : cases) assertEquals(row[0], row[1], IntentClassifier.classify(row[0]).kind.name());
        assertFalse("negated deletion is not an action request", IntentClassifier.classify("Do not delete the file").actionRequested);
    }

    @Test public void tagsRepresentativePartsOfSpeech() {
        String[][] cases = {{"the", "DETERMINER"}, {"quickly", "ADVERB"}, {"beautiful", "ADJECTIVE"},
            {"running", "VERB"}, {"because", "CONJUNCTION"}, {"under", "PREPOSITION"},
            {"she", "PRONOUN"}, {"will", "AUXILIARY"}, {"42", "NUMERAL"}, {"happiness", "NOUN"}};
        for (String[] row : cases) {
            List<PartsOfSpeech.Word> words = PartsOfSpeech.tag(row[0], null);
            assertFalse(row[0], words.isEmpty());
            assertEquals(row[0], row[1], words.get(0).tag.name());
        }
    }

    @Test public void resolvesOnlySupportedCoreferenceEvidence() {
        List<CoreferenceResolver.Entity> entities = Arrays.asList(
            new CoreferenceResolver.Entity("car", "thing", false, 0, .9),
            new CoreferenceResolver.Entity("people", "person", true, 4, .6));
        List<CoreferenceResolver.Resolution> results = CoreferenceResolver.resolve(Arrays.asList("it", "they", "he", null), entities);
        assertEquals(3, results.size());
        assertEquals("car", results.get(0).antecedent);
        assertEquals("people", results.get(1).antecedent);
        assertTrue("gender is not stored, so gendered pronouns must abstain", results.get(2).antecedent.isEmpty());
    }

    @Test public void extractsAbsoluteDatesButDoesNotInventDirectionForDurations() {
        long reference = 1763481600000L;
        List<TemporalSpatialAnalyzer.Cue> cues = TemporalSpatialAnalyzer.analyze(
            "Meet tomorrow and wait 2 days inside the car", reference);
        assertTrue(cues.stream().anyMatch(c -> c.expression.equals("tomorrow") && c.normalised.matches("\\d{4}-\\d{2}-\\d{2}")));
        assertTrue(cues.stream().anyMatch(c -> c.expression.equals("2 days")
            && c.normalised.equals("duration-unspecified-direction")));
        assertTrue(cues.stream().anyMatch(c -> c.kind.equals("space") && c.expression.equals("inside")));
    }
}
