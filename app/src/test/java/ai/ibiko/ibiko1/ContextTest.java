package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A message read with what the conversation is about: pronouns, "the company", "what about…", "his wife", "more". */
public class ContextTest {
    private static final long T = 1_000_000L;

    @Test
    public void pronounsFindTheirNames() {
        Context c = new Context();
        c.about("Who was Nelson Mandela?", "Nelson Mandela", "Nelson Mandela", Context.Kind.MAN, T);
        Context.Resolved r = c.resolve("When was he born?", T + 1000);
        assertEquals("When was Nelson Mandela born?", r.text);
        assertEquals("“he” is Nelson Mandela", r.said());
        assertEquals("Where did Nelson Mandela's wife live?", c.resolve("Where did his wife live?", T + 1000).text);
        assertFalse("she isn't him", c.resolve("When was she born?", T + 1000).changed());
        assertEquals("What is Nelson Mandela's wife?".replace("What", "Who"), c.resolve("And his wife?", T + 1000).text);
    }

    @Test
    public void thingsPlacesAndTheCompany() {
        Context c = new Context();
        c.about("Who founded Microsoft?", "Microsoft", "Microsoft", Context.Kind.THING, T);
        assertEquals("When was Microsoft founded?", c.resolve("When was the company founded?", T + 1).text);
        assertEquals("Where is Microsoft based?", c.resolve("Where is it based?", T + 1).text);
        assertEquals("not a thing: the time", "What time is it?", c.resolve("What time is it?", T + 1).text);
        c.about("What is the capital of Nigeria?", "Nigeria", "Nigeria", Context.Kind.PLACE, T + 2);
        assertEquals("What is the capital of Ghana?", c.resolve("What about Ghana?", T + 3).text);
        assertEquals("What is Nigeria's population?", c.resolve("What is its population?", T + 3).text);
        assertEquals("Tell me more about Nigeria", c.resolve("Tell me more", T + 3).text);
    }

    @Test
    public void anOldSubjectIsLetGo() {
        Context c = new Context();
        c.about("Who was Nelson Mandela?", "Nelson Mandela", "Nelson Mandela", Context.Kind.MAN, T);
        assertFalse(c.resolve("When was he born?", T + Context.FRESH_MS + 1).changed());
        c.clear();
        assertFalse(c.resolve("When was he born?", T + 1).changed());
    }

    @Test
    public void aStatementIsLeftAsSaid() {
        Context c = new Context();
        c.about("Who was Nelson Mandela?", "Nelson Mandela", "Nelson Mandela", Context.Kind.MAN, T);
        assertFalse(c.resolve("He was great", T + 1).changed());
        assertTrue(c.resolve("Was he a lawyer?", T + 1).changed());
    }
}
