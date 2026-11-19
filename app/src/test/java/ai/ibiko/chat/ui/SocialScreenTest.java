package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

/** The social accounts screen: it opens on a narrow phone, its targets are whole, and auto-post asks first. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w320dp-h640dp-xhdpi")
public class SocialScreenTest {
    @Test
    public void wordsOfferedFromTheSocialScreenReachTheChat() {
        ShareActivity.take(); // nothing left from another test
        ShareActivity.offerText("Write a Facebook post about ");
        ShareActivity.Shared got = ShareActivity.take();
        assertNotNull(got);
        assertEquals("Write a Facebook post about ", got.text);
        assertNull(ShareActivity.take()); // taken once
    }

    @Test
    public void opensWholeAndAutoPostAsksFirst() {
        ActivityController<SocialActivity> c = Robolectric.buildActivity(SocialActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        SocialActivity a = c.get();
        float d = a.getResources().getDisplayMetrics().density;
        android.view.View close = find(a.getWindow().getDecorView(), "Close");
        assertNotNull(close);
        assertEquals(48f, close.getHeight() / d, 0.5f);
        android.widget.Switch auto = (android.widget.Switch) find(a.getWindow().getDecorView(), "Auto-post on your behalf");
        assertNotNull(auto);
        auto.setChecked(true);
        ShadowLooper.idleMainLooper();
        android.content.SharedPreferences prefs = a.getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE);
        assertFalse("not on until confirmed", prefs.getBoolean("social_auto", false));
        assertNotNull(ShadowDialog.getLatestDialog());
        c.pause().stop().destroy();
    }

    private static android.view.View find(android.view.View v, String description) {
        if (description.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription())) {
            return v;
        }
        if (v instanceof android.view.ViewGroup) {
            android.view.ViewGroup g = (android.view.ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.view.View hit = find(g.getChildAt(i), description);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }
}
