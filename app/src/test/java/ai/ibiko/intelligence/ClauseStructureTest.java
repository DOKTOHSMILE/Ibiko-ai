package ai.ibiko.intelligence;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClauseStructureTest {
    @Test public void extractsCandidateSvo() {
        UnderstandingEngine.Result r=new UnderstandingEngine().analyze("Ibiko builds knowledge",null);
        assertFalse(r.frames.isEmpty());
        assertEquals("Ibiko",r.frames.get(0).subject);
        assertEquals("builds",r.frames.get(0).predicate);
        assertEquals("knowledge",r.frames.get(0).object);
    }
    @Test public void lexicalVerbWinsOverAuxiliaryAndNegationIsPreserved() {
        UnderstandingEngine.Result r=new UnderstandingEngine().analyze("Ibiko does not build weapons",null);
        assertFalse(r.frames.isEmpty());
        assertEquals("build",r.frames.get(0).predicate);
        assertTrue(r.frames.get(0).negated);
        assertTrue(r.toPromptContext().contains("candidate_frames="));
    }
    @Test public void emptyInputHasNoFrames() {
        assertTrue(new UnderstandingEngine().analyze("",null).frames.isEmpty());
    }
}
