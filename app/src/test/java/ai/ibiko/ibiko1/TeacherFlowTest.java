package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * What Ibiko v1 doesn't know, asked of the pilot's API model: the answer checked, kept, and next time answered from
 * memory without asking (or paying for) the model again.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class TeacherFlowTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        final AtomicInteger asked = new AtomicInteger();
        volatile String lastQuestion;
        String teacherSays = "The national bird of Zorbania is the blue heron. It was chosen in 1952.";

        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { steps.add(step); }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return true; }
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
        @Override public boolean teacherReady() { return true; }
        @Override public String teacherName() { return "Claude"; }

        @Override
        public String askTeacher(String system, String question) {
            asked.incrementAndGet();
            lastQuestion = question;
            return teacherSays;
        }

        String next(int index) throws InterruptedException {
            for (int i = 0; i < 400 && said.size() <= index; i++) {
                Thread.sleep(25);
            }
            return said.size() > index ? said.get(index) : null;
        }
    }

    @Test(timeout = 60000)
    public void askedOnceThenKnown() throws Exception {
        Plug.web = Plug.NONE;
        File dir = Files.createTempDirectory("ibiko1").toFile();
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, dir);
        brain.answer("What is the national bird of Zorbania?");
        String first = chat.next(0);
        assertNotNull(first);
        assertTrue("the API model's answer: " + first, first.contains("blue heron"));
        assertEquals(1, chat.asked.get());
        boolean said = false;
        for (String s : new ArrayList<String>(chat.steps)) {
            said |= s.contains("asking Claude") || s.contains("From Claude");
        }
        assertTrue("where it came from is in the thinking: " + chat.steps, said);

        // the same question again, in a new conversation: from memory, the model not asked again
        Chat later = new Chat();
        Ibiko1 again = new Ibiko1(later, dir);
        again.answer("What is the national bird of Zorbania?");
        String second = later.next(0);
        assertNotNull(second);
        assertTrue("from memory: " + second, second.contains("blue heron"));
        assertEquals("no tokens spent the second time", 0, later.asked.get());
    }

    @Test(timeout = 60000)
    public void askedWithTheConversation() throws Exception {
        Plug.web = Plug.NONE;
        Store.reset();
        Chat chat = new Chat();
        Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("ibiko1").toFile());
        brain.answer("What is the national bird of Zorbania?");
        assertNotNull(chat.next(0));
        chat.teacherSays = "Zorbania's national flower is the white lily.";
        brain.answer("And what is its national flower?");
        String reply = chat.next(1);
        assertNotNull(reply);
        String q = chat.lastQuestion;
        assertEquals("asked twice: " + reply + " " + chat.steps, 2, chat.asked.get());
        assertNotNull(q);
        // the API model is asked in the conversation, not with the words alone
        assertTrue(q, q.contains("The conversation so far"));
        assertTrue(q, q.contains("national bird of Zorbania"));
        assertTrue(q, q.contains("Question: "));
    }
}
