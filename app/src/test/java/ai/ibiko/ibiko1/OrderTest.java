package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Order;

/** Who is more, reasoned from what was said: the chain followed, contradictions caught, gaps said instead of guessed. */
public class OrderTest {
    private static String ask(String text) {
        List<Order.Fact> facts = Order.read(text);
        Order.Question q = Order.asked(text, facts);
        return q == null ? null : Order.answer(q, facts);
    }

    @Test
    public void theChainFollowed() {
        assertEquals("Ada is the tallest: Ada is taller than Obi, and Obi is taller than Emeka.",
                ask("Ada is taller than Obi. Obi is taller than Emeka. Who is the tallest?"));
        assertEquals("Emeka is the shortest: Ada is taller than Obi, and Obi is taller than Emeka.",
                ask("Ada is taller than Obi. Obi is taller than Emeka. Who is the shortest?"));
        assertEquals("Yes: Ada is taller than Obi, and Obi is taller than Emeka.",
                ask("Ada is taller than Obi. Obi is taller than Emeka. Is Ada taller than Emeka?"));
        assertEquals("No: it's the other way round. Ada is taller than Obi, and Obi is taller than Emeka.",
                ask("Ada is taller than Obi. Obi is taller than Emeka. Is Emeka taller than Ada?"));
        assertEquals("the other word for the same scale", "Yes: Ada is taller than Obi, and Obi is taller than Emeka.",
                ask("Ada is taller than Obi. Obi is taller than Emeka. Is Emeka shorter than Ada?"));
        assertEquals("From oldest to youngest: Tunde, Kemi, Bisi.", ask("Kemi is younger than Tunde. Bisi is younger than Kemi. Put them in order."));
    }

    @Test
    public void otherWaysOfSayingIt() {
        assertEquals("Chidi is the youngest.", ask("Ngozi is older than Chidi. Who is the youngest?"));
        assertEquals("Musa is the most careful.", ask("Musa is more careful than Sani. Who is the most careful?"));
        assertEquals("Yes: Joy is faster than Ann.", ask("Ann is not as fast as Joy. Is Joy faster than Ann?"));
        assertEquals("Bayo is the richest.", ask("Bayo is much richer than Femi. Who is the richest?"));
        assertEquals("the second comparison is the first one's subject's", "Bisi is the oldest: Bisi is older than Ada, and Ada is older than Obi.",
                ask("Ada is older than Obi but younger than Bisi. Who is the oldest?"));
    }

    @Test
    public void contradictionsAndGaps() {
        assertTrue(ask("Ada is taller than Obi. Obi is taller than Ada. Who is the tallest?").startsWith("That can't all be true:"));
        assertEquals("I can't tell from what you said: it's Ada or Bola, and nothing says which of them is taller.",
                ask("Ada is taller than Obi. Bola is taller than Obi. Who is the tallest?"));
        assertEquals("You haven't told me anything about Zainab and tall, so I can't say.",
                ask("Ada is taller than Obi. Is Zainab taller than Obi?"));
        assertNull("no comparison said", ask("Who is the tallest man in the world?"));
        List<Order.Fact> before = Order.read("Ada is taller than Obi. Obi is taller than Emeka.");
        assertNull("a question of its own after a puzzle", Order.asked("Who is the tallest man in the world?", before));
        assertEquals("Ada is the tallest of them: the people compared", "tall", Order.asked("Who is the tallest of them?", before).scale);
        assertNull("a question isn't a statement", ask("Is Ada taller than Obi?"));
    }
}
