package ai.ibiko.brain;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;
import org.junit.Test;

public class AdaptiveLearningEngineTest {
    @Test public void correctionSupersedesOlderTaughtFactForSameSubject() throws Exception {
        LearningJournal journal = new LearningJournal(TestPack.tempDir());
        journal.add(LearningJournal.TAUGHT_FACT, "capital of Nigeria", "Lagos");
        AdaptiveLearningEngine engine = new AdaptiveLearningEngine(journal);
        engine.recordCorrection("capital of Nigeria", "Abuja");
        List<AdaptiveLearningEngine.Evidence> got = engine.retrieve("What is the capital of Nigeria?", 5,
                System.currentTimeMillis() + 1L);
        assertEquals(1, got.size());
        assertTrue(got.get(0).sentence.toLowerCase().contains("abuja"));
        assertEquals(LearningJournal.CORRECTION, got.get(0).kind);
    }

    @Test public void unrelatedEvidenceIsNotRetrieved() throws Exception {
        LearningJournal journal = new LearningJournal(TestPack.tempDir());
        journal.add(LearningJournal.TAUGHT_FACT, "my dog", "Rex");
        assertTrue(new AdaptiveLearningEngine(journal).retrieve("What is the capital of Nigeria?", 4,
                System.currentTimeMillis()).isEmpty());
    }

    @Test public void feedbackIsLoggedButNeverRetrievedAsAFact() throws Exception {
        LearningJournal journal = new LearningJournal(TestPack.tempDir());
        AdaptiveLearningEngine engine = new AdaptiveLearningEngine(journal);
        assertNotNull(engine.recordFeedback("What is 2 + 2?", "4", false));
        assertTrue(engine.retrieve("What is 2 + 2?", 4, System.currentTimeMillis()).isEmpty());
    }

    @Test public void confidenceIsLabelledAsHeuristicNotTruth() throws Exception {
        LearningJournal journal = new LearningJournal(TestPack.tempDir());
        AdaptiveLearningEngine engine = new AdaptiveLearningEngine(journal);
        engine.recordCorrection("capital of Nigeria", "Abuja");
        String context = engine.promptContext("capital Nigeria", 3, System.currentTimeMillis());
        assertTrue(context.contains("not independently verified"));
        assertTrue(context.contains("not a probability of truth"));
    }
}
