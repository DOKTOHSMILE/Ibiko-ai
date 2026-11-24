package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Spelling;

/** Spelling a word out, the nearest real word for a misspelled one, the future said plainly, bare phrases as questions. */
public class SpellingTest {
    private static final Set<String> WORDS = new HashSet<String>(Arrays.asList("necessary", "dessert", "desert", "receive", "separate", "cat"));

    @Test
    public void spelledOut() {
        assertEquals("necessary", Spelling.asked("How do you spell necessary?"));
        assertEquals("receive", Spelling.asked("what's the correct spelling of 'receive'"));
        assertNull(Spelling.asked("How do you do?"));
        assertEquals("necessary is spelled N-E-C-E-S-S-A-R-Y (9 letters). Watch for: one c, two s's.", Spelling.reply("necessary", WORDS::contains));
    }

    @Test
    public void theNearestRealWord() {
        String r = Spelling.reply("neccessary", WORDS::contains);
        assertTrue(r, r.startsWith("I think you mean necessary: necessary is spelled N-E-C-E-S-S-A-R-Y"));
        assertTrue(Spelling.reply("recieve", WORDS::contains).startsWith("I think you mean receive"));
        assertNull("nothing near it", Spelling.reply("zxqwv", WORDS::contains));
    }

    @Test
    public void theFutureSaidPlainly() {
        assertTrue(Spelling.future("Who will win the next World Cup?").startsWith("No one can know that yet"));
        assertTrue(Spelling.future("Will Nigeria win the AFCON?") != null);
        assertNull("the past is a question of fact", Spelling.future("Who won the last World Cup?"));
        assertNull(Spelling.future("Who will help me with my homework?"));
    }

    @Test
    public void barePhrasesAsQuestions() {
        assertEquals("What is the capital of france?", Ibiko1.asQuestion("capital of france"));
        assertEquals("What is the population of Kano?", Ibiko1.asQuestion("population of Kano"));
        assertEquals("not a bare phrase", "The capital of France is lovely.", Ibiko1.asQuestion("The capital of France is lovely."));
        assertEquals("I love the capital of France", Ibiko1.asQuestion("I love the capital of France"));
    }

    @Test
    public void whatWasAskedFirst() {
        assertEquals(Conversation.Ask.Kind.FIRST, Conversation.read("What did I ask you first?").kind);
        assertEquals(Conversation.Ask.Kind.FIRST, Conversation.read("what was my first question").kind);
        assertEquals(Conversation.Ask.Kind.LAST, Conversation.read("What did I ask before that?").kind);
    }

    @Test
    public void questionsTypedTheWayPeopleChat() {
        assertEquals("what is the capitl of ghana?", Ibiko1.asked("whats the capitl of ghana"));
        assertEquals("who is mandela?", Ibiko1.asked("who's mandela"));
        assertEquals("what is photosynthesis?", Ibiko1.asked("wat is photosynthesis"));
        assertEquals("is 97 a prime number?", Ibiko1.asked("is 97 a prime number"));
        assertEquals("said, not asked", "My name is Ada.", Ibiko1.asked("My name is Ada."));
        assertEquals("a story ending in a question is left as typed", "I have 3 apples. how many are left",
                Ibiko1.asked("I have 3 apples. how many are left"));
        assertEquals("Y is a letter", "Y is a letter", Ibiko1.asked("Y is a letter"));
    }
}
