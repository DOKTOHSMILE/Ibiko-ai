package ai.ibiko.ops;

import static org.junit.Assert.*;

import org.junit.Test;

import ai.ibiko.ops.pipe.BrainInterpretationEngine;
import ai.ibiko.ops.pipe.SemanticTypes;

/** Regression tests for the existing single-assistant interpretation and safety bridge. */
public class BrainInterpretationEngineTest {
    @Test public void nullDecompositionRequestsClarificationInsteadOfCrashing() {
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(null, null);
        assertEquals("insufficient", result.status);
        assertTrue(result.clarification);
    }

    @Test public void nullActionIsAValidUnclassifiedReading() {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = "What is semantic reasoning?";
        d.action = null;
        d.question = true;
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(d, null);
        assertEquals("answer", result.intent);
        assertEquals("answer", result.operation);
    }

    @Test public void modelCannotTurnAnExplicitNoDeleteIntoDelete() {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = "Do not delete the project folder.";
        d.action = "chat";
        d.negations.add("delete");
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(d, ignored -> {
            SemanticTypes.Interpretation proposed = new SemanticTypes.Interpretation();
            proposed.operation = "delete";
            proposed.target = "project folder";
            return proposed;
        });
        assertEquals("invalid", result.status);
        assertEquals("reject", result.intent);
        assertEquals("none", result.operation);
    }

    @Test public void modelCannotOverrideLowTranscriptConfidence() {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = "delete folder";
        d.action = "delete";
        d.transcriptConfidence = 0.2;
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(d, ignored -> {
            SemanticTypes.Interpretation proposed = new SemanticTypes.Interpretation();
            proposed.status = "understood";
            proposed.operation = "delete";
            return proposed;
        });
        assertEquals("insufficient", result.status);
        assertTrue(result.clarification);
    }

    @Test public void modelCannotEraseExplicitPreserveConstraints() {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = "Keep the source files and summarize the folder.";
        d.action = "explain";
        d.preserve.add("source files");
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(d, ignored -> {
            SemanticTypes.Interpretation proposed = new SemanticTypes.Interpretation();
            proposed.status = "understood";
            proposed.operation = "explain";
            return proposed;
        });
        assertTrue(result.constraints.contains("source files"));
    }

    @Test public void askingWhatWouldHappenDoesNotAuthorizeDeletion() {
        SemanticTypes.Decomposition d = new SemanticTypes.Decomposition();
        d.original = "What would happen if I deleted this file?";
        d.action = "chat";
        d.question = true;
        SemanticTypes.Interpretation result = new BrainInterpretationEngine().interpret(d, ignored -> {
            SemanticTypes.Interpretation proposed = new SemanticTypes.Interpretation();
            proposed.operation = "delete";
            return proposed;
        });
        assertEquals("invalid", result.status);
    }
}
