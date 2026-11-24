package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.ibiko1.cognition.LearningRecords;

/** Memory › What Ibiko learned: each record with its status and source; filters; confirm, correct, forget. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h740dp-xhdpi")
public class LearnedScreenTest {
    @Test
    public void recordsAreShownConfirmedCorrectedAndForgotten() {
        File brain = new File(RuntimeEnvironment.getApplication().getFilesDir(), "brain");
        LearningRecords r = ai.ibiko.ibiko1.Store.learningRecords(brain);
        long now = System.currentTimeMillis();
        LearningRecords.Record onitsha = r.learn(LearningRecords.Category.fact, "capital of anambra", "The capital of Anambra State is Onitsha.",
                "api_model", "Claude", 0.6, now, false);
        r.learn(LearningRecords.Category.preference, null, "I prefer short answers.", "user_statement", "chat", 0.9, now, false);
        LearningRecords.Record lagos = r.learn(LearningRecords.Category.fact, null, "Lagos is the largest city in Nigeria.", "api_model", "Claude", 0.6,
                now, false);

        ActivityController<LearnedActivity> c = Robolectric.buildActivity(LearnedActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        LearnedActivity a = c.get();
        LinearLayout list = a.findViewById(ai.ibiko.R.id.learned_list);
        TextView summary = a.findViewById(ai.ibiko.R.id.learned_summary);
        assertEquals(3, list.getChildCount());
        assertTrue(summary.getText().toString(), summary.getText().toString().startsWith("1 known · 2 unsure · 0 disputed"));
        View first = list.getChildAt(0); // newest first
        assertEquals("Unsure", ((TextView) first.findViewById(ai.ibiko.R.id.record_status)).getText().toString());
        assertTrue(((TextView) first.findViewById(ai.ibiko.R.id.record_kind)).getText().toString().contains("the API model (Claude)"));

        // the filters: only the person's own
        a.findViewById(ai.ibiko.R.id.learned_yours).performClick();
        assertEquals(1, list.getChildCount());
        assertEquals("I prefer short answers.", ((TextView) list.getChildAt(0).findViewById(ai.ibiko.R.id.record_content)).getText().toString());
        a.findViewById(ai.ibiko.R.id.learned_all).performClick();

        // confirm Lagos: it becomes the person's own confirmation
        list.getChildAt(0).findViewById(ai.ibiko.R.id.record_confirm).performClick();
        assertEquals(LearningRecords.Verification.user_confirmed, r.find(lagos.id).status);

        // correct Onitsha: the correction replaces it, the old one kept and shown under Replaced
        LearningRecords.Record fixed = r.correct(onitsha.id, "The capital of Anambra State is Awka.", now + 1000);
        assertEquals(LearningRecords.Verification.superseded, r.find(onitsha.id).status);
        assertEquals(onitsha.id, fixed.supersedes);
        a.findViewById(ai.ibiko.R.id.learned_replaced).performClick();
        assertEquals(1, list.getChildCount());
        assertTrue(((TextView) list.getChildAt(0).findViewById(ai.ibiko.R.id.record_why)).getText().toString().contains("replaced by “The capital of Anambra State is Awka.”"));

        // forget, through its confirmation dialog
        a.findViewById(ai.ibiko.R.id.learned_yours).performClick();
        list.getChildAt(0).findViewById(ai.ibiko.R.id.record_forget).performClick();
        android.app.AlertDialog d = (android.app.AlertDialog) org.robolectric.shadows.ShadowDialog.getLatestDialog();
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        ShadowLooper.idleMainLooper();
        assertTrue("forgotten for good", r.active(now + 2000).stream().noneMatch(x -> x.content.equals("I prefer short answers.")));
        assertEquals("only the empty-filter line is left", 1, list.getChildCount());
        assertEquals("Nothing here.", ((TextView) list.getChildAt(0)).getText().toString());
        c.pause().stop().destroy();
    }
}
