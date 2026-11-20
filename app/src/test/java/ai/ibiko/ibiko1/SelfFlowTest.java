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

/** Ibiko as itself: what it is, what it can do, what the person told it about themselves. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class SelfFlowTest {
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
    public void itKnowsItselfAndThePerson() throws Exception {
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
            File brainDir = Files.createTempDirectory("ibiko1").toFile();
            Ibiko1 brain = new Ibiko1(chat, brainDir);
            String talk = ask(brain, chat, "What can we talk about?");
            assertTrue(talk, talk.startsWith("Almost anything") && !talk.toLowerCase().contains("communication"));
            String mixed = ask(brain, chat, "I'm a man, what are you?");
            assertTrue(mixed, mixed.startsWith("Got it, you're a man. I'm Ibiko, an AI"));
            assertEquals("Nice to meet you, Emeka.", ask(brain, chat, "My name is Emeka"));
            assertEquals("Got it, you live in Lagos. Good to know you like football.", ask(brain, chat, "I live in Lagos and I like football"));
            assertEquals("Here's what you've told me: your name is Emeka, you're a man, you live in Lagos and you like football.",
                    ask(brain, chat, "What do you know about me?"));
            assertTrue(ask(brain, chat, "What can you do?").contains("271,450 articles"));
            assertTrue(ask(brain, chat, "Are you conscious?").startsWith("Not the way you are"));
            assertTrue(ask(brain, chat, "Do you dream?").startsWith("No, I don't dream"));
            assertTrue(ask(brain, chat, "What do you think about Lagos?").contains("Lagos is"));
            ask(brain, chat, "Who was Nelson Mandela?");
            assertEquals("We're talking about Nelson Mandela.", ask(brain, chat, "What are we talking about?"));
            assertEquals("You asked: \u201cWhat are we talking about?\u201d", ask(brain, chat, "What did I just ask?"));
            assertTrue(ask(brain, chat, "I'm tired").startsWith("Sorry you're tired"));
            assertTrue("the world still goes to its knowledge", ask(brain, chat, "What is the capital of Kenya?").contains("Nairobi"));
            // a new brain on the same folder still knows the person
            Ibiko1 again = new Ibiko1(chat, brainDir);
            assertEquals("You're Emeka.", ask(again, chat, "What's my name?"));
        } finally {
            Plug.asset = was;
        }
    }
}
