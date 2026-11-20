package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** A conversation through Ibiko v1: each question leans on the one before, and how it was read is in the steps. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ScreenshotFlowTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { steps.add(step); }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { waiting = true; }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return waiting; }
        boolean waiting;
        @Override public void clearRating() { waiting = false; }
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
        @Override public void later(Runnable r, long ms) { r.run(); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }
    }

    private static String ask(Ibiko1 brain, Chat chat, String q) throws InterruptedException {
        int n = chat.said.size();
        chat.steps.clear();
        brain.answer(q);
        for (int i = 0; i < 400 && chat.said.size() == n; i++) {
            Thread.sleep(25);
        }
        return chat.said.size() > n ? chat.said.get(n) : null;
    }

    @Test(timeout = 120000)
    public void theScreenshotConversationIsSound() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
            chat.waiting = true; // an answer was marked: the next message may be its right answer
            String sing = ask(brain, chat, "Sing for me");
            assertTrue("a request is not taken as the answer to the last question: " + sing, !sing.startsWith("Got it. When you ask"));
            assertTrue(sing, sing.startsWith("I can't sing"));
            chat.waiting = true;
            String where = ask(brain, chat, "What's my current location?");
            assertTrue(where, where.startsWith("I can't see where you are"));
            assertTrue(ask(brain, chat, "Are you married?").startsWith("No, I'm not married"));
            assertTrue(ask(brain, chat, "What is my name?").startsWith("You haven't told me"));
            chat.waiting = true;
            String val = ask(brain, chat, "I'm Valentine you can call me Val");
            assertEquals("Nice to meet you, Valentine. I'll call you Val.", val);
            assertEquals("You're Valentine, and I call you Val.", ask(brain, chat, "What is my name?"));
            assertEquals("I don't know that about you yet. Tell me, and I'll remember it.", ask(brain, chat, "What is my favourite colour?"));
        } finally {
            Plug.asset = was;
        }
    }

    @Test
    public void aMarkedAnswerIsOnlyReplacedByAnAnswer() {
        assertTrue(Ibiko1.answers("Who wrote Hamlet?", "Shakespeare"));
        assertTrue(Ibiko1.answers("What is the capital of Nigeria?", "No, it's Abuja"));
        assertTrue(Ibiko1.answers("What is the capital of Nigeria?", "The capital of Nigeria is Abuja"));
        assertTrue(!Ibiko1.answers("Can you write a song?", "Sing for me"));
        assertTrue(!Ibiko1.answers("What's my current location?", "I'm Valentine you can call me Val"));
        assertTrue(!Ibiko1.answers("Who wrote Hamlet?", "What about Macbeth?"));
        assertTrue(Ibiko1.aboutThePerson("What's my current location?"));
        assertTrue(Ibiko1.aboutThePerson("Where do I work?"));
        assertTrue(!Ibiko1.aboutThePerson("What is the capital of Ghana?"));
        assertTrue(!Ibiko1.aboutThePerson("Can you tell me the time?"));
    }
}
