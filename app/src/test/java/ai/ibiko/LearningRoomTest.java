package ai.ibiko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import ai.ibiko.chat.ChatBook;

/** Settings › Learning chat turned on: a chat titled Learning opens, and what the pilot writes there is taken there. */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h740dp-xhdpi")
public class LearningRoomTest {
    private static List<String> bodies(ChatBook.Section s) {
        List<String> out = new ArrayList<String>();
        for (ChatBook.Message m : s.messages) {
            out.add(m.role + ": " + m.body);
        }
        return out;
    }

    @Test
    public void turningItOnOpensTheLearningChatAndThePilotCanJoin() {
        RuntimeEnvironment.getApplication().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(LearningRoom.ON, true).putBoolean(LearningRoom.OPEN, true).apply();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS);
        MainActivity a = c.get();
        ChatBook.Section s = a.chats.current();
        assertEquals("Learning", s.title);
        assertTrue(a.learningRoom.onScreen());
        String all = String.join("\n", bodies(s));
        assertTrue(all, all.contains("This is my learning chat"));
        // no API model here: it says what it needs instead of asking
        assertTrue(all, all.contains("I need an API model"));

        // the pilot joins: the message is taken by the Learning chat, shown as theirs
        assertTrue(a.learningRoom.join("What is the longest river in Africa?"));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        all = String.join("\n", bodies(a.chats.current()));
        assertTrue(all, all.contains("user: What is the longest river in Africa?"));
        assertTrue(all, all.contains("I can't reach"));

        // "stop learning" turns it off
        assertTrue(a.learningRoom.join("stop learning"));
        ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(a.prefs.getBoolean(LearningRoom.ON, true));
        c.pause().stop().destroy();
    }

    @Test
    public void anotherChatIsNotTaken() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(c.get().learningRoom.join("Hello"));
        c.pause().stop().destroy();
    }
}
