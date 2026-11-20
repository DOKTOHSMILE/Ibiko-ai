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

/** A pasted text read by Ibiko 1, and questions about it answered from it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PassageFlowTest {
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

    static final String TEXT = "Chiamaka Okafor was born in Enugu in 1988. She studied computer science at the University of Lagos "
            + "and graduated in 2010. After working for three years at a bank, she started a company called FarmLink in 2014. "
            + "FarmLink helps small farmers sell their crops directly to markets in Lagos and Abuja. The company now works with more "
            + "than 12,000 farmers across five states. In 2021, Okafor won the Africa Prize for Engineering Innovation for the "
            + "FarmLink app. She lives in Lagos with her husband, Tunde, and their two children.";

    @Test(timeout = 120000)
    public void aPastedTextIsReadAndQuestionsAreAnsweredFromIt() throws Exception {
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
            String read = ask(brain, chat, TEXT);
            assertTrue(read, read.startsWith("I've read it"));
            assertTrue(read, read.contains("Chiamaka Okafor"));
            assertEquals("Chiamaka Okafor started FarmLink.", ask(brain, chat, "Who started FarmLink?"));
            assertTrue(chat.steps.toString(), chat.steps.toString().contains("Looking in the text you pasted"));
            assertTrue(ask(brain, chat, "When did she start it?").contains("in 2014"));
            assertEquals("Chiamaka Okafor was born in Enugu in 1988.", ask(brain, chat, "Where was she born?"));
            String farmers = ask(brain, chat, "How many farmers does the company work with?");
            assertTrue(farmers + " " + chat.steps, farmers.contains("12,000 farmers"));
            assertTrue(ask(brain, chat, "What prize did she win?").contains("Africa Prize"));
            assertTrue(ask(brain, chat, "Who is her husband?").contains("Tunde"));
            String summary = ask(brain, chat, "Summarise it");
            assertTrue(summary, summary.startsWith("It's about"));
            assertTrue(ask(brain, chat, "What are the key points?").startsWith("The key points:"));
            assertEquals("a question about the world isn't answered from the text", "The capital of Ghana is Accra.",
                    ask(brain, chat, "What is the capital of Ghana?"));
        } finally {
            Plug.asset = was;
        }
    }
}
