package ai.ibiko.ibiko1;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * An error it doesn't know by heart: its kind said, a fix looked up on the web (not confirmed), "that fixed it" keeps
 * it, and the same error later (other names, other paths) is answered from what it learned, offline.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ErrorLookupFlowTest {
    static final List<String> searched = Collections.synchronizedList(new ArrayList<String>());

    private static final Plug.Web WEB = new Plug.Web() {
        @Override
        public String get(String url) {
            return null;
        }

        @Override
        public String search(String question) {
            searched.add(question);
            if (!question.contains("UnicodeDecodeError")) {
                return null;
            }
            return "Web results for " + question + "\n[1] How to fix UnicodeDecodeError — https://example.org/q/1\n"
                    + "From [1]:\nI get UnicodeDecodeError when I read my CSV file in Python.\n"
                    + "The file isn't UTF-8, so the codec can't decode the byte. Pass the file's real encoding when you open it,"
                    + " for example latin-1, or use errors='replace' to keep going.\n"
                    + "```\nwith open(path, encoding='latin-1') as f:\n    text = f.read()\n```\n";
        }
    };

    @After
    public void noWeb() {
        Plug.web = Plug.NONE;
    }

    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        boolean online = true;

        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return online; }
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

        String next(int index) throws InterruptedException {
            for (int i = 0; i < 200 && said.size() <= index; i++) {
                Thread.sleep(25);
            }
            return said.size() > index ? said.get(index) : null;
        }
    }

    @Test(timeout = 60000)
    public void lookedUpThenLearnedOnceItWorks() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = WEB;
        try {
            File dir = Files.createTempDirectory("ibiko1").toFile();
            Store.reset();
            Chat chat = new Chat();
            Ibiko1 brain = new Ibiko1(chat, dir);
            brain.answer("Traceback (most recent call last):\n  File \"/home/ada/report.py\", line 9, in <module>\n"
                    + "UnicodeDecodeError: 'utf-8' codec can't decode byte 0xe9 in position 41: invalid continuation byte");
            String found = chat.next(0);
            assertNotNull(found);
            assertTrue(found, found.startsWith("That error isn't one I know by heart: UnicodeDecodeError:"));
            assertTrue("found on the web, said as not confirmed: " + found, found.contains("What I found on the web (not confirmed yet):")
                    && found.contains("Pass the file's real encoding") && found.contains("encoding='latin-1'"));
            assertTrue("the search leaves out the person's own path: " + searched, searched.get(0).startsWith("python UnicodeDecodeError")
                    && !searched.get(0).contains("/home/ada"));

            brain.answer("That fixed it, thanks!");
            String kept = chat.next(1);
            assertTrue(kept, kept.startsWith("Good. I'll keep that fix"));

            // later, offline, the same error with other names and numbers: from what it learned
            chat.online = false;
            Plug.web = Plug.NONE;
            Ibiko1 later = new Ibiko1(chat, dir);
            later.answer("UnicodeDecodeError: 'ascii' codec can't decode byte 0x92 in position 7: invalid continuation byte");
            String again = chat.next(2);
            assertTrue("learned, offline: " + again, again.contains("The fix (it worked for you before):") && again.contains("encoding='latin-1'"));
        } finally {
            Plug.asset = was;
        }
    }
}
