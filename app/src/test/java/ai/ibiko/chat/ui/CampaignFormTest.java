package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignDraft;

/** The new campaign brief is a screen of its own: roomy boxes for long answers, and Plan it hands the brief on. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
public class CampaignFormTest {
    @Test
    public void longAnswersFitAndPlanItHandsTheBriefOn() {
        CampaignDraft.take();
        android.content.Context app = org.robolectric.RuntimeEnvironment.getApplication();
        SocialAccounts accounts = SocialWeb.accounts(app);
        SocialAccounts.Account x = accounts.add(Platform.X, "Brand", System.currentTimeMillis());
        accounts.signedIn(x.id, true);
        accounts.use(x.id);

        ActivityController<CampaignFormActivity> c = Robolectric.buildActivity(CampaignFormActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        CampaignFormActivity a = c.get();
        List<EditText> boxes = new ArrayList<EditText>();
        collect(a.getWindow().getDecorView(), boxes);
        EditText business = boxes.get(0), offer = boxes.get(1);
        assertTrue("many lines", (offer.getInputType() & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0);
        assertTrue("starts tall", offer.getMinLines() >= 3);
        assertTrue("a long answer wraps", offer.getMaxLines() > 1);

        offer.setText("Tazers, gas cylinders\nand household gadgets, delivered across Lagos");
        TextView plan = (TextView) byText(a.getWindow().getDecorView(), "Plan it");
        assertNotNull(plan);
        plan.performClick();
        ShadowLooper.idleMainLooper();
        assertNull("business or offer is enough", business.getError());
        Campaign got = CampaignDraft.take();
        assertNotNull(got);
        assertTrue(got.brief.offer.contains("\n"));
        assertEquals(1, got.accounts.size());
        assertTrue(a.isFinishing());
        c.pause().stop().destroy();
    }

    @Test
    public void anEmptyBriefKeepsTheScreenOpen() {
        CampaignDraft.take();
        ActivityController<CampaignFormActivity> c = Robolectric.buildActivity(CampaignFormActivity.class).create().start().resume().visible();
        CampaignFormActivity a = c.get();
        ((TextView) byText(a.getWindow().getDecorView(), "Plan it")).performClick();
        assertNull(CampaignDraft.take());
        assertTrue(!a.isFinishing());
        c.pause().stop().destroy();
    }

    private static void collect(View v, List<EditText> out) {
        if (v instanceof EditText) {
            out.add((EditText) v);
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                collect(((ViewGroup) v).getChildAt(i), out);
            }
        }
    }

    private static View byText(View v, String text) {
        if (v instanceof TextView && !(v instanceof EditText) && text.contentEquals(((TextView) v).getText())) {
            return v;
        }
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                View hit = byText(((ViewGroup) v).getChildAt(i), text);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
