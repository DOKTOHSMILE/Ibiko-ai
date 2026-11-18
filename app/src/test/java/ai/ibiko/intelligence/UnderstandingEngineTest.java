package ai.ibiko.intelligence;

import static org.junit.Assert.*;
import org.junit.Test;

public class UnderstandingEngineTest {
    @Test public void detectsIntentNegationAndReasoningDomainsWithoutExecutingAnything() {
        UnderstandingEngine.Result r = new UnderstandingEngine().analyze("Please do not move the object because it is behind the box tomorrow", "motorcycle");
        assertEquals("request", r.intent);
        assertTrue(r.negated);
        assertTrue(r.causal);
        assertTrue(r.spatial);
        assertTrue(r.temporal);
        assertEquals("motorcycle", r.resolvedReference);
        assertTrue(r.toPromptContext().contains("heuristic"));
    }
    @Test public void labelsBasicGrammarAndNumbers() {
        UnderstandingEngine.Result r = new UnderstandingEngine().analyze("The small engine moves quickly", null);
        assertEquals(5, r.tokens.size());
        assertEquals(UnderstandingEngine.PartOfSpeech.DETERMINER, r.tokens.get(0).pos);
        assertEquals(UnderstandingEngine.PartOfSpeech.ADJECTIVE, r.tokens.get(1).pos);
        assertEquals(UnderstandingEngine.PartOfSpeech.NOUN, r.tokens.get(2).pos);
        assertEquals(UnderstandingEngine.PartOfSpeech.VERB, r.tokens.get(3).pos);
        assertEquals(UnderstandingEngine.PartOfSpeech.ADVERB, r.tokens.get(4).pos);
    }
    @Test public void identifiesStrongNigerianLanguageCues() {
        assertEquals("yo", new UnderstandingEngine().analyze("Ẹ káàbọ̀", null).languageHint);
        assertEquals("ha", new UnderstandingEngine().analyze("Sannu, yaya lafiya?", null).languageHint);
        assertEquals("ig", new UnderstandingEngine().analyze("Kedu, onye ọma", null).languageHint);
        assertEquals("pcm", new UnderstandingEngine().analyze("Wetin you dey do?", null).languageHint);
        assertEquals("undetermined", new UnderstandingEngine().analyze("Unfamiliar sample", null).languageHint);
    }
}
