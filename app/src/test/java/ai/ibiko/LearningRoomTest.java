package ai.ibiko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

/**
 * The Learning chat is a chat like any other: it opens as a page of the chat screen, what is written there takes the way
 * every message takes (the API model answers it there), and Ibiko learns from each exchange once it's done.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w360dp-h740dp-xhdpi")
public class LearningRoomTest {
    private static String text(ChatBook.Section s) {
        List<String> out = new ArrayList<String>();
        for (ChatBook.Message m : s.messages) {
            out.add(m.role + ": " + m.body);
        }
        return String.join("\n", out);
    }

    @Test
    public void itIsAChatLikeAnyOtherWithItsOwnBrain() throws Exception {
        RuntimeEnvironment.getApplication().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit()
                .putBoolean(LearningRoom.ON, true).putBoolean(LearningRoom.OPEN, true).putString("brain_pick", "ibiko").apply();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(3, java.util.concurrent.TimeUnit.SECONDS);
        MainActivity a = c.get();
        ChatBook.Section s = a.chats.current();
        assertEquals("Learning", s.title);
        assertTrue(a.learningRoom.onScreen());
        assertTrue(text(s), text(s).contains("This is the Learning chat"));
        // its brain is the API model, whatever the other chats use; "Ibiko, …" is Ibiko's turn
        assertEquals("api", a.brainPick());
        assertFalse(a.learningRoom.control("Ibiko, what did you learn today?"));
        assertEquals("ibiko", a.brainPick());
        a.learningRoom.brainForTurn = null;

        // an exchange that is done is learned from, and what was kept is a note on the chat
        a.chats.addTo(s.id, "user", "text", "Where is Lake Chad?", "ibiko");
        a.chats.addTo(s.id, "assistant", "text", "Lake Chad is a lake in West Africa, shared by Chad, Cameroon, Niger and Nigeria.", "ibiko");
        for (int i = 0; i < 40 && !text(a.chats.current()).contains("Ibiko: "); i++) {
            ShadowLooper.idleMainLooper(1, java.util.concurrent.TimeUnit.SECONDS);
            Thread.sleep(50);
        }
        String all = text(a.chats.current());
        assertTrue(all, all.matches("(?s).*Ibiko: (Kept as|Set aside|Not kept).*"));

        // "stop learning" turns it off, answered in the chat as any reply is
        assertTrue(a.learningRoom.control("stop learning"));
        assertFalse(a.prefs.getBoolean(LearningRoom.ON, true));
        c.pause().stop().destroy();
    }

    @Test
    public void anotherChatKeepsItsOwnBrain() {
        RuntimeEnvironment.getApplication().getSharedPreferences("ibiko", android.content.Context.MODE_PRIVATE).edit()
                .putString("brain_pick", "ibiko").apply();
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).create().start().resume().visible();
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS);
        assertFalse(c.get().learningRoom.control("stop learning"));
        assertNull(c.get().learningRoom.brain());
        assertEquals("ibiko", c.get().brainPick());
        c.pause().stop().destroy();
    }
}
