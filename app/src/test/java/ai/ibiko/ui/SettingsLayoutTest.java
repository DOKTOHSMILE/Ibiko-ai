package ai.ibiko.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.chat.Tuning;
import ai.ibiko.chat.ui.SettingsActivity;

/** Settings: every section closed until tapped, and no slider in the scrolling list for a finger to move. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SettingsLayoutTest {
    private static int seekBars(View v) {
        int n = v instanceof android.widget.SeekBar ? 1 : 0;
        if (v instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) {
                n += seekBars(((ViewGroup) v).getChildAt(i));
            }
        }
        return n;
    }

    @Test
    public void sectionsAreClosedAndAdvancedIsGone() {
        ActivityController<SettingsActivity> c = Robolectric.buildActivity(SettingsActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        View root = c.get().getWindow().getDecorView();
        java.util.ArrayList<View> found = new java.util.ArrayList<View>();
        root.findViewsWithText(found, "Advanced", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertTrue("no Advanced section: its numbers are fixed in the app", found.isEmpty());
        root.findViewsWithText(found, "Appearance", View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertFalse(found.isEmpty());
        assertEquals("the list builds no section's controls", 0, seekBars(root));
        found.get(0).performClick();
        android.content.Intent page = org.robolectric.Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals("a section opens on its own page", "Appearance", page.getStringExtra(SettingsActivity.EXTRA_SECTION));
        c.pause().stop().destroy();
    }

    @Test
    public void aNumberTheOldSlidersSavedHasNoEffectAndIsClearedOnce() {
        SharedPreferences p = org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("t", 0);
        p.edit().putInt(Tuning.CHAT_TOKENS.key, 64).putInt(Tuning.AGENT_STEPS.key, 100).apply();
        assertEquals("a saved number is not read", Tuning.CHAT_TOKENS.def, Tuning.CHAT_TOKENS.get(p));
        assertEquals(Tuning.AGENT_STEPS.def, Tuning.AGENT_STEPS.get(p));
        assertTrue(Tuning.resetOnce(p));
        assertFalse("and it is deleted", p.contains(Tuning.CHAT_TOKENS.key));
        assertFalse("only once", Tuning.resetOnce(p));
    }
}
