package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A model switched in mid-job carries the work on, aware of it, and free to correct it. */
public class TakeoverTest {
    @Test
    public void theNewModelKnowsItTookTheWorkOver() {
        String note = Takeover.note("gpt-5", "claude-opus-5-5");
        String system = Takeover.with("You are Ibiko's coding agent.", note);
        assertTrue(system.startsWith("You are Ibiko's coding agent."));
        assertTrue(system.contains("from gpt-5 to claude-opus-5-5"));
        assertTrue("goes on from where it stands", system.contains("carry on from the next step that is not done"));
        assertTrue("no fresh start", system.contains("Do not restart"));
        assertTrue("not bound by the earlier model", system.contains("not bound by"));
        assertEquals("no switch: the instructions as they are", "Plain.", Takeover.with("Plain.", null));
    }
}
