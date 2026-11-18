package ai.ibiko.intelligence.context;

import static org.junit.Assert.*;
import org.junit.Test;

public class ContextualUnderstandingEngineTest {
    @Test public void resolvesUnambiguousRecentEntityAndRemembersTurns() {
        ContextualUnderstandingEngine engine = new ContextualUnderstandingEngine();
        engine.analyze("The car is red", 1763481600000L);
        ContextualUnderstandingEngine.Result next = engine.analyze("It is low", 1763481600000L);
        assertEquals(1, next.coreferences.size());
        assertEquals("car", next.coreferences.get(0).antecedent.toLowerCase());
        assertEquals(2, next.rememberedTurns);
    }

    @Test public void intentLabelsDoNotExecuteCapabilities() {
        IntentClassifier.Result r = IntentClassifier.classify("Search for a dictionary");
        assertEquals(IntentClassifier.Kind.SEARCH, r.kind);
        assertTrue(r.actionRequested);
        // This class only labels intent; no executor/callback is exposed.
    }

    @Test public void disambiguatesBankFromNearbyMeaningCues() {
        assertEquals("financial institution", WordSenseDisambiguator.analyze("Deposit cash at the bank").get(0).label);
        assertEquals("river edge", WordSenseDisambiguator.analyze("The bank beside the river").get(0).label);
    }

    @Test public void normalisesRelativeDatesAgainstSuppliedClock() {
        assertTrue(TemporalSpatialAnalyzer.analyze("Meet me tomorrow", 1763481600000L).get(0).normalised.matches("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test public void snapshotRoundTripRestoresContext() {
        ContextualUnderstandingEngine first = new ContextualUnderstandingEngine();
        first.analyze("The car is red", 1763481600000L);
        String snapshot = first.exportSnapshot();
        ContextualUnderstandingEngine second = new ContextualUnderstandingEngine();
        assertTrue(second.restoreSnapshot(snapshot));
        ContextualUnderstandingEngine.Result r = second.analyze("It is low", 1763481600000L);
        assertEquals("car", r.coreferences.get(0).antecedent.toLowerCase());
    }


    @Test public void detectsOppositePolarityAcrossTurnsAsCandidateOnly() {
        ContextualUnderstandingEngine engine = new ContextualUnderstandingEngine();
        engine.analyze("The door is open", 1763481600000L);
        ContextualUnderstandingEngine.Result next = engine.analyze("The door is not open", 1763481600000L);
        assertFalse(next.historicalContradictions.isEmpty());
        assertTrue(next.toPromptContext().contains("historical_contradiction_candidates"));
    }

    @Test public void invalidSnapshotIsRejected() {
        ContextualUnderstandingEngine engine = new ContextualUnderstandingEngine();
        assertFalse(engine.restoreSnapshot("not-a-snapshot"));
    }
}
