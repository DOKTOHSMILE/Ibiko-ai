package ai.ibiko.ibiko1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

/** The phone screenshot: "Good" was noted as a fact; "What do you know of coca-cola?" got the learning report. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ScreenshotTwoTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
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
        @Override public void later(Runnable r, long ms) { r.run(); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }
    }

    private static String ask(Ibiko1 brain, Chat chat, String q) throws InterruptedException {
        int n = chat.said.size();
        brain.answer(q);
        for (int i = 0; i < 400 && chat.said.size() == n; i++) {
            Thread.sleep(10);
        }
        return chat.said.size() > n ? chat.said.get(n) : null;
    }

    @Test(timeout = 120000)
    public void theConversationFromThePhone() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        try {
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("shot2").toFile());
            ask(brain, chat, "Hello there Ibiko");
            String good = ask(brain, chat, "Good");
            assertNotNull(good);
            assertFalse("not a fact to note: " + good, good.startsWith("Noted"));
            assertTrue(good, good.startsWith("Glad"));
            String report = ask(brain, chat, "What have you learned so far?");
            assertTrue(report, report.contains("confirm"));
            String coke = ask(brain, chat, "What do you know of coca-cola?");
            assertNotNull(coke);
            assertFalse("about Coca-Cola, not its memory: " + coke, coke.startsWith("Answers you confirmed") || coke.startsWith("No answers confirmed"));
            assertTrue(coke, coke.toLowerCase().contains("coca-cola"));
        } finally {
            Plug.asset = was;
        }
    }

    /** The demo run on the build machine: a saying, then a word; a question asked back, then talk about something else. */
    @Test(timeout = 300000)
    public void theDemoConversation() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        try {
            Store.reset();
            Chat chat = new Chat();
            File files = Files.createTempDirectory("demo").toFile();
            Ibiko1.warm(files); // as the app does at start: the dictionary open
            Ibiko1 brain = new Ibiko1(chat, files);
            String riddle = ask(brain, chat, "Me myself and I\nJust the 3 of us\n🤔 What does this implicate?");
            assertTrue(riddle, riddle.contains("only you"));
            String wahala = ask(brain, chat, "What does wahala mean?");
            assertNotNull(wahala);
            assertFalse("its own word, not the saying again: " + wahala, wahala.contains("myself"));
            assertTrue(wahala, wahala.toLowerCase().contains("trouble"));
            String capital = ask(brain, chat, "wat is the captal of nigeria");
            assertTrue(capital, capital.contains("Abuja"));
            ask(brain, chat, "Who invented the zorbanian flute?"); // unknown: asked back
            String apples = ask(brain, chat, "I have 3 apples.");
            assertFalse("not taken as the answer to what it asked: " + apples, apples.contains("fills a gap") || apples.contains("kept it as"));
            ask(brain, chat, "I buy 4 more apples.");
            String count = ask(brain, chat, "How many apples do I have now?");
            assertTrue(count, count.startsWith("You have 7 apples"));
        } finally {
            Plug.asset = was;
        }
    }
}
