package ai.ibiko.ibiko1;

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

/** The language engine in the brain: questions about language answered, and the same reading in every brain's note. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class LanguageEngineTest {
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
        for (int i = 0; i < 600 && chat.said.size() == n; i++) {
            Thread.sleep(10);
        }
        return chat.said.size() > n ? chat.said.get(n) : null;
    }

    @Test(timeout = 300000)
    public void languageQuestionsAndTheSharedReading() throws Exception {
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
            File files = Files.createTempDirectory("lang").toFile();
            Ibiko1.warm(files);
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, new File(files, "brain"));
            String figure = ask(brain, chat, "What figure of speech is \"It's raining cats and dogs\"?");
            assertNotNull(figure);
            assertTrue(figure, figure.contains("idiom") && figure.toLowerCase().contains("rain"));
            String tense = ask(brain, chat, "What tense is \"She has been writing all morning\"?");
            assertTrue(tense, tense.contains("present perfect continuous"));
            String gerund = ask(brain, chat, "What part of speech is \"running\" in \"Running is healthy\"?");
            assertTrue(gerund, gerund.contains("gerund"));
            String related = ask(brain, chat, "How are \"happy\" and \"unhappy\" related?");
            assertTrue(related, related.contains("antonyms"));
            String metaphor = ask(brain, chat, "Is \"My boss is a dragon\" a metaphor?");
            assertTrue(metaphor, metaphor.startsWith("Yes"));

            String brief = brain.premeditate("Can you open the door? It's raining cats and dogs.");
            assertTrue(brief, brief.contains("Ibiko's reading of the language"));
            assertTrue(brief, brief.contains("idiom") && brief.contains("request"));
            assertTrue("a plain request gets no language note", !brain.premeditate("What is the capital of Ghana?").contains("reading of the language"));
            assertTrue(ai.ibiko.ibiko1.language.NounKinds.of("tiger", w -> {
                List<String> up = new ArrayList<String>();
                for (ai.ibiko.ibiko1.words.MeaningIndex.Sense s : ai.ibiko.ibiko1.words.Meanings.shared().index().senses(w)) {
                    if (s.pos.equals("noun") && !s.broader.isEmpty()) {
                        up.add(s.broader.split(",")[0].trim());
                    }
                }
                return up;
            }).equals("animal"));
        } finally {
            Plug.asset = was;
        }
    }
}
