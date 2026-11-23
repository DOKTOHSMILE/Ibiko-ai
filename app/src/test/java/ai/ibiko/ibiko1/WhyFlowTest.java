package ai.ibiko.ibiko1;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Reasons told are understood, kept, used for "why", and followed back with "why?" until it has to ask. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WhyFlowTest {
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
    public void reasonsAreKeptAndFollowedBack() throws Exception {
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
        String a = ask(brain, chat, "The harvest in Kano was poor because the rains failed.");
        assertTrue(a, a.startsWith("Noted: The harvest in Kano was poor because the rains failed"));
        String b = ask(brain, chat, "The rains failed because the winds shifted south.");
        assertTrue(b, b.startsWith("Noted:"));
        String why = ask(brain, chat, "Why was the harvest in Kano poor?");
        assertTrue(why, why.startsWith("Because the rains failed, which came about because the winds shifted south."));
        String further = ask(brain, chat, "Why?");
        assertTrue(further, further.startsWith("That's as far back as what I've read goes"));
        String told = ask(brain, chat, "The ocean warmed up");
        assertTrue(told, told.contains("The winds shifted south because the ocean warmed up"));
        // next time, the chain goes a step further
        ask(brain, chat, "Why was the harvest in Kano poor?");
        String again = ask(brain, chat, "why?");
        assertTrue(again, again.startsWith("The winds shifted south because the ocean warmed up."));
    }
}
