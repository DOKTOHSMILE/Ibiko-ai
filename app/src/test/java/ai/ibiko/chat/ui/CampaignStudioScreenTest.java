package ai.ibiko.chat.ui;

import static org.junit.Assert.assertNotNull;

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

/** A campaign's studio conversation: its brief, its posts as feed cards with links, and the next steps as chips. */
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
        ActivityController<CampaignChatActivity> a = Robolectric.buildActivity(CampaignChatActivity.class,
                CampaignChatActivity.open(app, c.id)).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View root = a.get().getWindow().getDecorView();
        assertNotNull(byText(root, "Campaign brief"));
        assertNotNull(byText(root, "Post number 0"));
        assertNotNull("a post that went out links to itself", byText(root, "Open the post on Facebook"));
        assertNotNull(byText(root, "Coming up"));
        assertNotNull("the next step for a running campaign, one tap", byText(root, "How is it doing?"));
        // the tools over the message box, as in the chat; pausing is in the Campaign sheet
        for (String tool : new String[] {"Campaign", "Posts", "Media", "Edit"}) {
            assertNotNull(tool, byText(root, tool));
        }
        assertNotNull("the model, chosen in the message box", byText(root, "Choose a model"));
        a.pause().stop().destroy();
        CampaignRunner.delete(app, c);
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
