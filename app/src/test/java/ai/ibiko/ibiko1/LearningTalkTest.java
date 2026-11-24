package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/** The learning conversation with the API model: when it starts, what it asks, what it keeps, and stopping it. */
public class LearningTalkTest {
    @Test
    public void knowsWhenItIsAskedToLearn() {
        assertEquals(10, LearningTalk.asked("Start a learning conversation with my API model"));
        assertEquals(10, LearningTalk.asked("enter into a learning conversation with my API model and start learning random knowledge"));
        assertEquals(10, LearningTalk.asked("Then finally make ibiko enter into a learning conversation with my API model make ibiko starts learning random knowledge"));
        assertEquals(20, LearningTalk.asked("Go and learn 20 new things"));
        assertEquals(5, LearningTalk.asked("learn 5 random facts from my API model"));
        assertEquals(LearningTalk.MOST, LearningTalk.asked("learn 99 things"));
        assertEquals(0, LearningTalk.asked("What is learning?"));
        assertEquals(0, LearningTalk.asked("I want to learn French"));
        assertTrue(LearningTalk.stops("stop learning"));
        assertTrue(LearningTalk.stops("Please end the learning conversation"));
        assertFalse(LearningTalk.stops("I stopped learning piano"));
    }

    @Test
    public void asksWhatItDoesNotKnowChecksAndKeeps() throws Exception {
        final List<String> shown = Collections.synchronizedList(new ArrayList<String>());
        final List<String> kept = Collections.synchronizedList(new ArrayList<String>());
        final List<String> questions = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch done = new CountDownLatch(1);
        LearningTalk talk = new LearningTalk(new LearningTalk.Brain() {
            @Override public String ask(String q) {
                questions.add(q);
                return q.contains("Zaria") ? "Zaria is a city in Kaduna State, home to Ahmadu Bello University." : "It is a thing worth knowing.";
            }
            @Override public String teacher() { return "Claude"; }
            @Override public String checkAndKeep(String q, String a, String s) {
                kept.add(a);
                return a.contains("Zaria") ? "Kept as known: it agrees with what I hold." : "Kept as unsure: it's new to me.";
            }
            @Override public List<String> open() { return new ArrayList<String>(Arrays.asList("Where is Zaria?")); }
            @Override public String randomSubject(Random r) { return "Subject " + r.nextInt(100000); }
            @Override public boolean knows(String name) { return name.equals("Kaduna State"); }
        }, text -> {
            shown.add(text);
            if (text.startsWith("Learning conversation done")) {
                done.countDown();
            }
        });
        talk.start(3);
        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertEquals("the question it couldn't answer comes first", "Where is Zaria?", questions.get(0));
        assertTrue("then what the answer made it curious about: " + questions, questions.get(1).contains("Ahmadu Bello University"));
        assertEquals(3, kept.size());
        assertTrue(shown.get(0).startsWith("Ibiko → Claude: Where is Zaria?\nClaude: Zaria is"));
        assertTrue(shown.get(0).contains("\nIbiko: Kept as known"));
        String last = shown.get(shown.size() - 1);
        assertTrue(last, last.contains("3 answers learned from Claude. 1 agreed"));
        assertFalse(talk.going());
    }

    @Test
    public void stopsWhenTold() throws Exception {
        final List<String> shown = Collections.synchronizedList(new ArrayList<String>());
        final CountDownLatch asking = new CountDownLatch(1), release = new CountDownLatch(1);
        LearningTalk talk = new LearningTalk(new LearningTalk.Brain() {
            @Override public String ask(String q) {
                asking.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "An answer.";
            }
            @Override public String teacher() { return "Claude"; }
            @Override public String checkAndKeep(String q, String a, String s) { return "Kept as unsure."; }
            @Override public List<String> open() { return new ArrayList<String>(); }
            @Override public String randomSubject(Random r) { return "Subject " + r.nextInt(100000); }
            @Override public boolean knows(String name) { return true; }
        }, shown::add);
        talk.start(10);
        assertTrue(asking.await(5, TimeUnit.SECONDS));
        talk.stop();
        release.countDown();
        Thread.sleep(300);
        assertFalse(talk.going());
        assertTrue("nothing kept or shown after stopping: " + shown, shown.isEmpty());
    }
}
