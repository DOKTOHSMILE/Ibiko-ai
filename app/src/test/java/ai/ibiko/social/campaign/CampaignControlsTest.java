package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The campaign's own controls: now, skip, retry, move, copy, rename, share. */
public class CampaignControlsTest {
    private static final long HOUR = 3_600_000L, NOW = 1_800_000_000_000L;

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.id = "c1";
        c.name = "Launch";
        c.status = Campaign.Status.ACTIVE;
        c.startAt = NOW;
        String[] states = {"POSTED", "FAILED", "PLANNED", "PLANNED", "FAILED"};
        for (int i = 0; i < states.length; i++) {
            Campaign.Item it = new Campaign.Item();
            it.id = "c1-" + i;
            it.network = "facebook";
            it.account = "fb1";
            it.text = "Post number " + i + " about affordable apps";
            it.at = NOW + i * HOUR;
            it.state = Campaign.State.valueOf(states[i]);
            c.items.add(it);
        }
        return c;
    }

    @Test
    public void theNextPostGoesNowOrIsSkipped() {
        Campaign c = campaign();
        String said = CampaignControls.postNextNow(c, NOW + 10);
        assertTrue(said, said.startsWith("The next post (Facebook: “Post number 2"));
        assertEquals(NOW + 10, c.items.get(2).at);
        String skipped = CampaignControls.skipNext(c);
        assertTrue(skipped, skipped.startsWith("Skipped the next post"));
        assertEquals(Campaign.State.SKIPPED, c.items.get(2).state);
        assertEquals("the one after is next now", "c1-3", CampaignControls.next(c).id);
    }

    @Test
    public void failedPostsGoBackInLineAQuarterHourApart() {
        Campaign c = campaign();
        assertEquals(2, CampaignControls.failed(c));
        assertEquals(2, CampaignControls.retryFailed(c, NOW));
        assertEquals(Campaign.State.PLANNED, c.items.get(1).state);
        assertEquals(NOW + 15 * 60_000L, c.items.get(1).at);
        assertEquals(NOW + 30 * 60_000L, c.items.get(4).at);
        assertEquals(0, CampaignControls.failed(c));
    }

    @Test
    public void theScheduleMovesButNeverIntoThePast() {
        Campaign c = campaign();
        assertEquals("only posts still to go", 2, CampaignControls.shift(c, 24 * HOUR, NOW));
        assertEquals(NOW + 26 * HOUR, c.items.get(2).at);
        assertEquals("a posted one stays", NOW, c.items.get(0).at);
        CampaignControls.shift(c, -100 * HOUR, NOW);
        assertTrue("never before now", c.items.get(2).at >= NOW);
    }

    @Test
    public void aCopyRunsAgainFresh() {
        Campaign c = campaign();
        c.brief.business = "Capital oracle projects";
        c.accounts.add("fb1");
        Campaign copy = CampaignControls.duplicate(c, NOW + 5);
        assertEquals("Launch (again)", copy.name);
        assertEquals("c" + (NOW + 5), copy.id);
        assertTrue(copy.items.isEmpty());
        assertEquals(Campaign.Status.DRAFT, copy.status);
        assertEquals("Capital oracle projects", copy.brief.business);
        assertEquals("fb1", copy.accounts.get(0));
        assertEquals("a copy of a copy isn't “(again) (again)”", "Launch (again)", CampaignControls.duplicate(copy, NOW + 6).name);
        assertEquals("the original keeps its posts", 5, c.items.size());
    }

    @Test
    public void namesAndTheTextToShare() {
        assertEquals("Spring sale", CampaignControls.name("  Spring   sale "));
        assertNull(CampaignControls.name("   "));
        String text = CampaignControls.export(campaign());
        assertTrue(text, text.startsWith("Launch\n"));
        assertTrue(text, text.contains("Facebook · posted\nPost number 0 about affordable apps"));
        assertTrue(text, text.contains("· failed"));
    }

    @Test
    public void nothingToDoIsSaid() {
        Campaign c = new Campaign();
        assertNull(CampaignControls.postNextNow(c, NOW));
        assertNull(CampaignControls.skipNext(c));
        assertEquals(0, CampaignControls.retryFailed(c, NOW));
    }
}
