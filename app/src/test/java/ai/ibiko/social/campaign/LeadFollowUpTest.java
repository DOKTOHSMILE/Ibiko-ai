package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/** Follow-ups: a pitch not moved on in two days is due a reminder; one that bought or said no never is. */
public class LeadFollowUpTest {
    @Test
    public void aContactedLeadIsDueAfterTwoDaysAndAClosedOneNever() throws Exception {
        Lead l = new Lead();
        l.id = "l1";
        l.url = "https://x.com/someone/status/1";
        l.kind = Lead.Kind.LEAD;
        l.state = Lead.State.SENT;
        long now = 1_800_000_000_000L;
        l.moveTo(Lead.Stage.CONTACTED, now);
        assertFalse(l.dueFollowUp(now + Lead.FOLLOW_UP_MS - 1));
        assertTrue(l.dueFollowUp(now + Lead.FOLLOW_UP_MS));
        l.moveTo(Lead.Stage.BOUGHT, now);
        assertFalse(l.dueFollowUp(now + 10 * Lead.FOLLOW_UP_MS));
        assertEquals("Bought", l.stageShown());
        l.moveTo(Lead.Stage.TALKING, now);
        l.note = "asked for blue";
        File dir = Files.createTempDirectory("leads").toFile();
        LeadStore store = new LeadStore(dir);
        store.put(l);
        Lead back = new LeadStore(dir).get("l1");
        assertEquals(Lead.Stage.TALKING, back.stage);
        assertEquals("asked for blue", back.note);
        assertTrue(back.dueFollowUp(now + Lead.FOLLOW_UP_MS));
    }
}
