package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.QuestionTypes;
import ai.ibiko.ibiko1.formulas.SearchPhrase;

/** A question read with the running conversation: searched with it, and asked of models with it. */
public class UnderstandingTest {
    private static java.io.InputStream file(String p) {
        try {
            return TextCode.open(x -> {
                try {
                    return new FileInputStream(new File("src/main/assets/" + x));
                } catch (java.io.IOException e) {
                    return null;
                }
            }, p);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final SearchPhrase RULES = new SearchPhrase(file("knowledge/language/search_phrasing.tsv"));
    private static final QuestionTypes KINDS = new QuestionTypes(file("knowledge/language/question_types.tsv"));

    private static List<Conversation.Turn> talk(String asked, String said) {
        Conversation c = new Conversation();
        c.add(asked, null, said);
        return c.recent(3);
    }

    @Test
    public void aQuestionLeaningOnTheTalkIsSearchedWithItsSubject() {
        Understanding u = new Understanding("What's the population?", "What's the population?", KINDS.of("What's the population?"),
                null, "Nigeria", talk("Tell me about Nigeria", "Nigeria is a country in West Africa."));
        assertEquals("Nigeria population", u.searches(RULES, 2026).get(0));
    }

    @Test
    public void aQuestionNamingItsSubjectIsSearchedAsItIs() {
        Understanding u = new Understanding("Who wrote Things Fall Apart?", "Who wrote Things Fall Apart?", null, null, "Nigeria",
                Collections.<Conversation.Turn>emptyList());
        assertEquals("Things Fall Apart author", u.searches(RULES, 2026).get(0));
    }

    @Test
    public void aLoneWordIsSearchedWithWhatTheTalkIsAbout() {
        Understanding u = new Understanding("What is root?", "What is root?", null, "computing", null, Collections.<Conversation.Turn>emptyList());
        assertEquals("root computing", u.searches(RULES, 2026).get(0));
    }

    @Test
    public void newsOfNowIsSearchedWithThisYear() {
        String q = "What is happening in Ghana?";
        Understanding u = new Understanding(q, q, KINDS.of(q), null, null, Collections.<Conversation.Turn>emptyList());
        assertEquals("Ghana latest news 2026", u.searches(RULES, 2026).get(0));
    }

    @Test
    public void modelsAreAskedWithTheConversation() {
        Understanding u = new Understanding("When did he die?", "When did Nelson Mandela die?", KINDS.of("When did he die?"), null,
                "Nelson Mandela", talk("Who was Nelson Mandela?", "Nelson Mandela was South Africa's first Black president."));
        String asked = u.forModel(u.asked);
        assertTrue(asked, asked.contains("Person: Who was Nelson Mandela?"));
        assertTrue(asked, asked.contains("it is about Nelson Mandela"));
        assertTrue(asked, asked.contains("The person typed: When did he die?"));
        assertTrue(asked, asked.endsWith("Question: When did Nelson Mandela die?"));
        assertTrue(u.step(), u.step().contains("we're on Nelson Mandela"));
    }

    @Test
    public void nothingAddedWithoutATalk() {
        Understanding u = new Understanding("Hi", "Hi", null, null, null, Collections.<Conversation.Turn>emptyList());
        assertNull(u.step());
        assertEquals("Question: Hi", u.forModel("Hi"));
        assertEquals(Arrays.asList("Hi"), u.searches(RULES, 2026));
    }
}
