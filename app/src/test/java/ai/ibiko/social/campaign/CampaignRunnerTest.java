package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/** The runner only acts on a running campaign's due posts, and says why a post couldn't go out. */
@RunWith(RobolectricTestRunner.class)
public class CampaignRunnerTest {
    private static Campaign campaign(String id, Campaign.Status status, long... at) {
        Campaign c = new Campaign();
        c.id = id;
        c.name = "Cakes";
        c.status = status;
        c.accounts.add("gone");
        for (int i = 0; i < at.length; i++) {
            Campaign.Item it = new Campaign.Item();
            it.id = id + "-" + i;
            it.account = "gone"; // an account that isn't on this phone
            it.network = "facebook";
            it.text = "Fresh cakes";
            it.at = at[i];
            c.items.add(it);
        }
        return c;
    }

    @Test
    public void onlyARunningCampaignsDuePostsAreTouched() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        long now = 1_800_000_000_000L;
        CampaignStore store = CampaignRunner.store(ctx);
        store.put(campaign("run", Campaign.Status.ACTIVE, now - 1000, now + 3_600_000));
        store.put(campaign("held", Campaign.Status.PAUSED, now - 1000));
        CampaignRunner.run(ctx, now);
        Campaign run = CampaignRunner.store(ctx).get("run");
        assertEquals(Campaign.State.FAILED, run.items.get(0).state);
        assertTrue(run.items.get(0).result, run.items.get(0).result.contains("removed"));
        assertEquals("not due yet", Campaign.State.PLANNED, run.items.get(1).state);
        assertEquals(Campaign.Status.ACTIVE, run.status);
        assertEquals("a paused campaign posts nothing", Campaign.State.PLANNED, CampaignRunner.store(ctx).get("held").items.get(0).state);
        CampaignRunner.run(ctx, now + 3_700_000);
        assertEquals("nothing left to post", Campaign.Status.DONE, CampaignRunner.store(ctx).get("run").status);
    }
}
