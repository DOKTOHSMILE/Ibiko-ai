package ai.ibiko.intelligence;

import static org.junit.Assert.*;
import org.junit.Test;

public class SemanticIntelligenceTest {
    @Test public void extractsLexicalAndTaxonomicCandidates() {
        SemanticIntelligence.Result r = new SemanticIntelligence().analyze("A car has a wheel and an engine", null);
        String context = r.toPromptContext();
        assertTrue(context.contains("hypernym_candidate"));
        assertTrue(context.contains("part_of_candidate"));
        assertTrue(context.contains("function_candidate"));
    }

    @Test public void flagsOppositeExplicitPolarityAsCandidateContradiction() {
        SemanticIntelligence.Result r = new SemanticIntelligence().analyze("Ibiko does not build weapons", "Ibiko build weapons");
        assertEquals(SemanticIntelligence.Inference.CONTRADICTION_CANDIDATE, r.inference);
    }

    @Test public void doesNotInventInferenceWhenEvidenceIsInsufficient() {
        SemanticIntelligence.Result r = new SemanticIntelligence().analyze("The sky is blue", "A phone is expensive");
        assertEquals(SemanticIntelligence.Inference.UNKNOWN, r.inference);
    }

    @Test public void understandingContextIncludesSemanticEvidence() {
        String context = new UnderstandingEngine().analyze("The car has a wheel", null).toPromptContext();
        assertTrue(context.contains("SEMANTIC EVIDENCE"));
    }
}
