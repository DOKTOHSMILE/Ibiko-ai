package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Corroborate;
import ai.ibiko.ibiko1.formulas.Corroborate.Post;

/** Social media as a cross-check: independent accounts that state the answer, not reposts, rumours or questions. */
public class CorroborateTest {
    private static final String Q = "who won the 2022 World Cup?";
    private static final String A = "Argentina won the 2022 FIFA World Cup, beating France on penalties.";

    @Test
    public void theAnswersOwnWordsAreWhatAPostMustState() {
        assertEquals("[argentina]", Corroborate.answerWords(Q, A).toString());
        assertEquals("[1, october, 1960]", Corroborate.answerWords("When did Nigeria become independent?",
                "Nigeria became independent on 1 October 1960.").toString());
        assertEquals("[36]", Corroborate.answerWords("How many states does Nigeria have?", "Nigeria has 36 states.").toString());
    }

    @Test
    public void independentAccountsConfirmIt() {
        List<Post> posts = new ArrayList<Post>(Arrays.asList(
                new Post("Reddit", "ada", "Argentina won the 2022 World Cup and Messi finally got his trophy."),
                new Post("Reddit", "obi", "What a final! Argentina are 2022 World Cup champions after penalties against France."),
                new Post("X", "ngozi", "Still can't believe Argentina won the World Cup in 2022, best final ever"),
                new Post("Reddit", "tunde", "Argentina winning the 2022 World Cup in Qatar was emotional for Messi fans.")));
        Corroborate.Verdict v = Corroborate.weigh(Q, A, posts);
        assertEquals(4, v.agree);
        assertTrue(v.said(), v.confirmed() && v.said().startsWith("Social media: 4 accounts agree (Reddit, X): it holds up"));
    }

    @Test
    public void repostsRumoursQuestionsAndOtherTopicsDontCount() {
        String same = "BREAKING: Argentina won the 2022 World Cup! #Messi";
        List<Post> posts = Arrays.asList(
                new Post("X", "a1", same), new Post("X", "a2", "RT " + same), new Post("X", "a3", same + " https://t.co/x"),
                new Post("Reddit", "b", "Is it true Argentina won the 2022 World Cup?"),
                new Post("Reddit", "c", "I heard Argentina won the 2022 World Cup, allegedly."),
                new Post("Reddit", "d", "Argentina won the 1986 World Cup with Maradona."),
                new Post("Reddit", "e", "The 2022 World Cup was held in Qatar in winter."));
        Corroborate.Verdict v = Corroborate.weigh(Q, A, posts);
        assertEquals("three copies of one post are one voice; the rest don't state it", 1, v.agree);
        assertTrue(!v.confirmed());
    }

    @Test
    public void disputesAreCounted() {
        List<Post> posts = Arrays.asList(
                new Post("X", "x1", "France won the 2022 World Cup, everyone knows it."),
                new Post("X", "x2", "Argentina won the 2022 World Cup? That's fake, the final was rigged."),
                new Post("Reddit", "r1", "Argentina won the 2022 World Cup."));
        Corroborate.Verdict v = Corroborate.weigh(Q, A, posts);
        assertEquals(1, v.agree);
        assertEquals(2, v.dispute);
        assertTrue(v.said(), v.disputed() && v.said().contains("treat it with care"));
    }
}
