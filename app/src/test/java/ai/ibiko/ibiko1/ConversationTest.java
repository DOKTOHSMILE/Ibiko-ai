package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The conversation kept turn by turn, and talked about: recap, recall, going back, the first question. */
public class ConversationTest {
    @Test
    public void readsQuestionsAboutTheConversation() {
        assertEquals(Conversation.Ask.Kind.RECAP, Conversation.read("What have we talked about so far?").kind);
        assertEquals(Conversation.Ask.Kind.RECAP, Conversation.read("Summarize our conversation").kind);
        assertEquals(Conversation.Ask.Kind.RECAP, Conversation.read("recap").kind);
        Conversation.Ask r = Conversation.read("What did you say about Nigeria earlier?");
        assertEquals(Conversation.Ask.Kind.RECALL, r.kind);
        assertEquals("Nigeria", r.subject);
        Conversation.Ask back = Conversation.read("Ok, let's go back to the Ghana");
        assertEquals(Conversation.Ask.Kind.RETURN, back.kind);
        assertEquals("Ghana", back.subject);
        assertEquals(Conversation.Ask.Kind.FIRST, Conversation.read("What was the first thing I asked?").kind);
        assertNull(Conversation.read("What is the capital of Nigeria?"));
        assertNull("going to bed is not a subject", Conversation.read("back to bed"));
    }

    @Test
    public void recapsRecallsAndGoesBack() {
        Conversation c = new Conversation();
        assertNull(c.recap());
        c.add("Tell me about Nigeria", "Nigeria", "Nigeria is a country in West Africa.");
        c.add("What is the capital of Nigeria?", "Nigeria", "The capital of Nigeria is Abuja.");
        c.add("Who was Nelson Mandela?", "Nelson Mandela", "Nelson Mandela was a South African leader.");
        c.add("What have we talked about?", "Nelson Mandela", "So far …");
        String recap = c.recap();
        assertTrue(recap, recap.contains("• Nigeria (2 questions, last: “What is the capital of Nigeria?”)"));
        assertTrue(recap, recap.contains("• Nelson Mandela (1 question: “Who was Nelson Mandela?”)"));
        assertTrue("a question about the talk isn't a subject of it: " + recap, !recap.contains("What have we talked about"));
        String said = c.recall("nigeria");
        assertTrue(said, said.startsWith("You asked “What is the capital of Nigeria?”, and I said: The capital of Nigeria is Abuja."));
        assertTrue(said, said.contains("Before that, about “Tell me about Nigeria”"));
        assertNull(c.recall("Ghana"));
        assertEquals("Nigeria", c.named("nigeria"));
        assertEquals("Tell me about Nigeria", c.question(true));
        assertEquals("Who was Nelson Mandela?", c.question(false));
    }
}
