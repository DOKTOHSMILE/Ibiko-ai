package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.QuestionTypes;

/** The kind of a question by how it starts, the most particular opening first; an opening alone answered from its kind. */
public class QuestionTypesTest {
    private static QuestionTypes kinds() {
        return new QuestionTypes(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/language/question_types.tsv"));
    }

    private static String type(String q) {
        QuestionTypes.Kind k = kinds().of(q);
        return k == null ? null : k.row.type;
    }

    @Test
    public void theKindByHowItStarts() {
        assertTrue(kinds().size() >= 40);
        assertEquals("REASON", type("Why is the sky blue?"));
        assertEquals("why_did", kinds().of("Why did the Roman Empire fall?").row.id);
        assertEquals("REASON", type("How come it's cold?"));
        assertEquals("PAST_EVENT", type("What happened in 1960?"));
        assertEquals("CURRENT_EVENT", type("What is happening in Lagos?"));
        assertEquals("CURRENT_EVENT", type("what's going on"));
        assertEquals("FUTURE_EVENT", type("What will happen if prices rise?"));
        assertEquals("CONSEQUENCE", type("What if the sun disappeared?"));
        assertEquals("SELF", type("What am I doing wrong?"));
        assertEquals("SELF", type("Who am I?"));
        assertEquals("IBIKO", type("What are you?"));
        assertEquals("PAST", type("What was the Biafran War?"));
        assertEquals("DEFINITION", type("What is a volcano?"));
        assertEquals("FACT", type("What is the capital of Ghana?"));
        assertEquals("MEANING", type("What does ubiquitous mean?"));
        assertEquals("PERMISSION", type("Can I ask you something?"));
        assertEquals("REQUEST", type("Can you help me?"));
        assertEquals("ADVICE", type("Should I learn Python?"));
        assertEquals("COUNT", type("How many legs does a spider have?"));
        assertEquals("PROCESS", type("How do I bake bread?"));
        assertEquals("MECHANISM", type("How does a fridge work?"));
        assertEquals("YES_NO", type("Is Lagos in Nigeria?"));
        assertEquals("WELLBEING", type("How are you?"));
        assertNull(type("Lagos is big."));
    }

    @Test
    public void anOpeningAlone() {
        QuestionTypes k = kinds();
        assertTrue(k.of("What was?").openingOnly);
        assertFalse(k.of("What was the war?").openingOnly);
        assertFalse("the word fills the opening's slot", k.of("What does \"ubiquitous\" mean?").openingOnly);
        assertEquals("‘What was…’ asks about something in the past: what was what? About Nigeria, which we were on?",
                QuestionTypes.alone(k.of("What was?"), "Nigeria"));
        assertEquals("Why what? You're curious about a reason: tell me what you'd like explained.", QuestionTypes.alone(k.of("Why?"), null));
        assertTrue(QuestionTypes.alone(k.of("What happened?"), "Nigeria").contains("About Nigeria, which we were on?"));
        assertNull("a whole question by itself", QuestionTypes.alone(k.of("Who am I?"), null));
        assertNull(QuestionTypes.alone(k.of("How are you?"), null));
        assertNull("not only its opening", QuestionTypes.alone(k.of("Why is the sky blue?"), null));
    }
}
