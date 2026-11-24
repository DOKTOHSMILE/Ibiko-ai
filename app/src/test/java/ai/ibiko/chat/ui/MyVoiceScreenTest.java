package ai.ibiko.chat.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** Settings › Chat › My voice: its page (res/layout/activity_my_voice.xml) shows the first sentence to read, and moves on. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h740dp-xhdpi")
public class MyVoiceScreenTest {
    @Test
    public void thePageShowsTheSentenceToRead() {
        ActivityController<MyVoiceActivity> c = Robolectric.buildActivity(MyVoiceActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper();
        MyVoiceActivity a = c.get();
        TextView number = a.findViewById(ai.ibiko.R.id.my_voice_number);
        TextView sentence = a.findViewById(ai.ibiko.R.id.my_voice_sentence);
        TextView progress = a.findViewById(ai.ibiko.R.id.my_voice_progress);
        assertNotNull(sentence);
        assertEquals("Sentence 1 of 750", number.getText().toString());
        assertEquals("Hello! How can I help you today?", sentence.getText().toString());
        assertTrue(progress.getText().toString(), progress.getText().toString().startsWith("0 of 750 recorded"));
        assertFalse("nothing to speak with until a voice is trained", a.findViewById(ai.ibiko.R.id.my_voice_use).isEnabled());
        a.findViewById(ai.ibiko.R.id.my_voice_next).performClick();
        assertEquals("Sentence 2 of 750", number.getText().toString());
        assertFalse(sentence.getText().toString().isEmpty());
        c.pause().stop().destroy();
    }
}
