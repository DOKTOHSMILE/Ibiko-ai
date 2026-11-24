package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignBoard;
import ai.ibiko.social.campaign.CampaignRunner;

/** The campaigns dashboard: running, coming up and past apart, each campaign a card that opens to its posts. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
public class CampaignsDashboardTest {
    private static Campaign campaign(String id, String name, Campaign.Status status, long first, int posted, int planned) {
        Campaign c = new Campaign();
        c.id = id;
        c.name = name;
        c.status = status;
        for (int i = 0; i < posted + planned; i++) {
            Campaign.Item it = new Campaign.Item();
            it.id = id + "-" + i;
            it.network = "facebook";
            it.account = "gone";
            it.text = "Post " + i;
            it.at = first + i * 3_600_000L;
            it.state = i < posted ? Campaign.State.POSTED : Campaign.State.PLANNED;
            if (i == 0 && posted > 0) {
                it.url = "https://m.facebook.com/story.php?story_fbid=1&id=2";
                it.checked = 1;
            }
            c.items.add(it);
        }
        return c;
    }

    @Test
    public void campaignsSitWhereTheyBelong() {
        long now = 1_800_000_000_000L;
        Campaign running = campaign("r", "Gas week", Campaign.Status.ACTIVE, now - 7_200_000, 2, 3);
        Campaign later = campaign("u", "December sale", Campaign.Status.ACTIVE, now + 86_400_000, 0, 3);
        Campaign draft = campaign("d", "New stock", Campaign.Status.DRAFT, now + 3_600_000, 0, 2);
        Campaign done = campaign("p", "November", Campaign.Status.DONE, now - 900_000_000, 4, 0);
        assertEquals(CampaignBoard.Bucket.RUNNING, CampaignBoard.bucket(running, false, now));
        assertEquals("approved, first post ahead", CampaignBoard.Bucket.UPCOMING, CampaignBoard.bucket(later, false, now));
        assertEquals("Scheduled", CampaignBoard.state(later, false, now));
        assertEquals(CampaignBoard.Bucket.UPCOMING, CampaignBoard.bucket(draft, false, now));
        assertEquals("Ready to review", CampaignBoard.state(draft, false, now));
        assertEquals("Planning", CampaignBoard.state(draft, true, now));
        assertEquals(CampaignBoard.Bucket.PAST, CampaignBoard.bucket(done, false, now));
        assertEquals(2, CampaignBoard.posted(running));
        assertEquals(running.items.get(2), CampaignBoard.next(running));
        assertEquals(Collections.singletonList("Facebook"), CampaignBoard.networks(running));
    }

    @Test
    public void theDashboardShowsTheCardsAndOpensOne() throws Exception {
        android.content.Context app = RuntimeEnvironment.getApplication();
        long now = System.currentTimeMillis();
        CampaignRunner.store(app).put(campaign("r1", "Gas week", Campaign.Status.ACTIVE, now - 7_200_000, 2, 3));
        CampaignRunner.store(app).put(campaign("p1", "November", Campaign.Status.DONE, now - 900_000_000, 4, 0));
        ActivityController<CampaignsActivity> c = Robolectric.buildActivity(CampaignsActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View root = c.get().getWindow().getDecorView();
        assertNotNull(byText(root, "Gas week"));
        assertNull("past campaigns wait on their own tab", byText(root, "November"));
        assertNotNull(byText(root, "2 of 5 posted"));
        byText(root, "Past · 1").performClick();
        ShadowLooper.idleMainLooper();
        root = c.get().getWindow().getDecorView();
        assertNotNull(byText(root, "November"));
        byText(root, "Running · 1").performClick();
        ShadowLooper.idleMainLooper();
        root = c.get().getWindow().getDecorView();
        View card = (View) byText(root, "Gas week").getParent().getParent();
        card.performClick();
        android.content.Intent next = Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals("a card opens the campaign's conversation on the chat screen", ai.ibiko.MainActivity.class.getName(),
                next.getComponent().getClassName());
        assertEquals("r1", next.getStringExtra(ai.ibiko.CampaignRoom.EXTRA));
        c.pause().stop().destroy();

        android.content.Intent all = new android.content.Intent(app, CampaignActivity.class).putExtra(CampaignActivity.EXTRA_ID, "r1");
        ActivityController<CampaignActivity> d = Robolectric.buildActivity(CampaignActivity.class, all).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View page = d.get().getWindow().getDecorView();
        assertNotNull(byText(page, "Pause"));
        // the posts are a grid of pictures; a tap opens one full size, with its state and its link
        View tile = byDescription(page, "Posted ✓");
        assertNotNull("the posted post is in the grid", tile);
        tile.performClick();
        ShadowLooper.idleMainLooper();
        View post = org.robolectric.shadows.ShadowDialog.getLatestDialog().getWindow().getDecorView();
        assertNotNull("a checked post links to itself", byText(post, "Open the post"));
        assertNotNull(byTextStart(post, "Posted ✓"));
        d.pause().stop().destroy();
        CampaignRunner.store(app).remove("r1");
        CampaignRunner.store(app).remove("p1");
    }

    private static View byDescription(View v, String part) {
        if (v.getContentDescription() != null && v.getContentDescription().toString().contains(part)) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = byDescription(g.getChildAt(i), part);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    /** A text that contains {@code part} (the viewer says when, where and the state on one line). */
    private static TextView byTextStart(View v, String part) {
        if (v instanceof TextView && ((TextView) v).getText().toString().contains(part)) {
            return (TextView) v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView f = byTextStart(g.getChildAt(i), part);
                if (f != null) {
                    return f;
                }
            }
        }
        return null;
    }

    private static TextView byText(View v, String text) {
        if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) {
            return (TextView) v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                TextView hit = byText(((ViewGroup) v).getChildAt(i), text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
