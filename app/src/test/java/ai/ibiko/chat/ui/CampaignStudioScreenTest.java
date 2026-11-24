package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignChat;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.CampaignStudio;

/** A campaign's studio conversation, a page of the one chat screen: its brief, the plan, and its posts with their links. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
public class CampaignStudioScreenTest {
    @Test
    public void theConversationShowsTheBriefThePostsAndTheNextSteps() throws Exception {
        android.content.Context app = RuntimeEnvironment.getApplication();
        long now = System.currentTimeMillis();
        Campaign c = new Campaign();
        c.id = "studio1";
        c.name = "Gas week";
        c.status = Campaign.Status.ACTIVE;
        c.createdAt = now - 86_400_000L;
        for (int i = 0; i < 3; i++) {
            Campaign.Item it = new Campaign.Item();
            it.id = "studio1-" + i;
            it.account = "gone";
            it.network = "facebook";
            it.text = "Post number " + i;
            it.at = now + (i - 1) * 3_600_000L;
            it.state = i == 0 ? Campaign.State.POSTED : Campaign.State.PLANNED;
            if (i == 0) {
                it.checked = 1;
                it.url = "https://m.facebook.com/story.php?story_fbid=1&id=2";
            }
            c.items.add(it);
        }
        CampaignRunner.store(app).put(c);
        CampaignChat chat = CampaignStudio.chat(app, c.id);
        chat.add(CampaignChat.YOU, "brief", "New social media campaign\n\nBusiness: Valineto3 Enterprises", now - 86_000_000L);
        chat.add(CampaignChat.STUDIO, "plan", "Your campaign is planned: 3 posts.", now - 85_000_000L);
        // the conversation opens as a page of the one chat screen: no screen of its own
        ActivityController<ai.ibiko.MainActivity> a = Robolectric.buildActivity(ai.ibiko.MainActivity.class,
                ai.ibiko.CampaignRoom.intent(app, c.id)).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        ai.ibiko.chat.ChatBook.Section page = a.get().chatsForTest().current();
        assertEquals("Gas week", page.title);
        String all = text(page);
        assertNotNull(all, all.contains("Campaign brief") ? all : null);
        assertNotNull(all, all.contains("The plan is ready") ? all : null);
        // what the owner sends takes the same way as any message: it shows as theirs, and the reply comes back in the chat
        a.get().answerForTest("Show the posts");
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS);
        all = text(a.get().chatsForTest().current());
        assertNotNull(all, all.contains("user: Show the posts") ? all : null);
        assertNotNull(all, all.contains("Post number 0") ? all : null);
        assertNotNull("a post that went out links to itself: " + all, all.contains("Open the post on Facebook") ? all : null);
        assertNotNull(all, all.contains("Coming up") ? all : null);
        a.pause().stop().destroy();
        CampaignRunner.delete(app, c);
    }

    private static String text(ai.ibiko.chat.ChatBook.Section s) {
        StringBuilder b = new StringBuilder();
        for (ai.ibiko.chat.ChatBook.Message m : s.messages) {
            b.append(m.role).append(": ").append(m.body).append('\n');
        }
        return b.toString();
    }

    private static View byDescription(View v, String what) {
        if (what.contentEquals(String.valueOf(v.getContentDescription()))) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View hit = byDescription(((ViewGroup) v).getChildAt(i), what);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** The labels of every chip in the row over the message box (TextViews inside horizontal scrollers). */
    private static void chipLabels(View v, java.util.List<String> out) {
        if (v instanceof android.widget.HorizontalScrollView) {
            collect(v, out);
            return;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                chipLabels(((ViewGroup) v).getChildAt(i), out);
            }
        }
    }

    private static void collect(View v, java.util.List<String> out) {
        if (v instanceof TextView && ((TextView) v).getText().length() > 0) {
            out.add(((TextView) v).getText().toString());
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                collect(((ViewGroup) v).getChildAt(i), out);
            }
        }
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
