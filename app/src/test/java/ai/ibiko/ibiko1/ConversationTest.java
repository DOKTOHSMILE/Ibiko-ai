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
public class ConversationTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { steps.add(step); }
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
    public void eachQuestionLeansOnTheOneBefore() throws Exception {
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
            ask(brain, chat, "Who was Nelson Mandela?");
            assertEquals("Nelson Mandela was born on 18 July 1918.", ask(brain, chat, "When was he born?"));
            assertTrue(chat.steps.toString(), chat.steps.toString().contains("“he” is Nelson Mandela"));
            assertTrue(ask(brain, chat, "And his wife?").contains("Winnie"));
            assertEquals("The capital of Nigeria is Abuja.", ask(brain, chat, "What is the capital of Nigeria?"));
            assertEquals("The capital of Ghana is Accra.", ask(brain, chat, "What about Ghana?"));
            assertEquals("The Mona Lisa was painted by Leonardo da Vinci.", ask(brain, chat, "Who painted the Mona Lisa?"));
            assertTrue("its roles are in the steps: " + chat.steps, chat.steps.toString().contains("action “paint”"));
            String more = ask(brain, chat, "Tell me more");
            assertTrue(more, more != null && !more.contains("painted by Leonardo da Vinci."));
        } finally {
            Plug.asset = was;
        }
    }
}
