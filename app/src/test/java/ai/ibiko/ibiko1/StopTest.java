package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Ibiko v1 counts as thinking until it answers, so the app shows Stop; Stop lets the answer go and nothing is said late. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class StopTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        /** Its steps wait here, as on a screen, until the test lets time pass. */
        final ArrayDeque<Runnable> waiting = new ArrayDeque<Runnable>();
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return false; }
        @Override public String sum(String message) { return null; }
        @Override public String recallTalk(String question) { return null; }
        @Override public void learnTalk(String question, String answer) { }
        @Override public void forgetTalk(String question) { }
        @Override public int talkSize() { return 0; }
        @Override public int shapes() { return 0; }
        @Override public List<String> aboutPilot() { return new ArrayList<String>(); }
        @Override public void answered(Ibiko1.How how) { }
        @Override public void note(String text) { }
        @Override public void later(Runnable r, long ms) { waiting.add(r); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }

        void passTime() {
            for (int i = 0; i < 200 && !waiting.isEmpty(); i++) {
                waiting.poll().run();
            }
        }
    }

    @Test(timeout = 120000)
    public void stopLetsTheAnswerGo() throws Exception {
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("stop").toFile());
        assertFalse(brain.isThinking());
        brain.answer("Who wrote Things Fall Apart?");
        assertTrue("thinking while its steps show", brain.isThinking());
        brain.stop();
        assertFalse("Stop ends the thinking", brain.isThinking());
        chat.passTime();
        assertEquals("nothing is said after Stop", 0, chat.said.size());

        brain.answer("Who wrote Things Fall Apart?");
        assertTrue(brain.isThinking());
        chat.passTime();
        assertEquals(1, chat.said.size());
        assertFalse("answered: Send again", brain.isThinking());
    }
}
