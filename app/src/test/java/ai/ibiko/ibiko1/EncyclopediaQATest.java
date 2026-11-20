package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Questions answered from the shipped encyclopedia: the right article, the sentence that says it, or nothing. */
public class EncyclopediaQATest {
    /** The encyclopedia as the app ships it (the module's own assets). */
    private static final EncyclopediaQA QA = new EncyclopediaQA(new Encyclopedia(path -> {
        try {
            return new java.io.FileInputStream(new java.io.File("src/main/assets", path));
        } catch (java.io.IOException e) {
            return null;
        }
    }));

    private static String said(String q) {
        EncyclopediaQA.Found f = QA.answer(q);
        return f == null ? null : f.title + ": " + f.text;
    }

    @Test
    public void questionsGetTheSentenceThatAnswersThem() {
        String a = said("When did Nigeria gain independence?");
        assertNotNull(a);
        assertTrue(a, a.startsWith("Nigeria:") && a.contains("1 October 1960"));
        a = said("How many people live in Lagos?");
        assertTrue(a, a != null && a.startsWith("Lagos:") && a.contains("million"));
        a = said("When was Nelson Mandela born?");
        assertTrue(a, a != null && a.contains("18 July 1918"));
        a = said("Where is Mount Kilimanjaro?");
        assertTrue(a, a != null && a.contains("Tanzania"));
    }

    @Test
    public void unsureMeansNothingRatherThanAWrongArticle() {
        assertNull("not State, a word inside United States", said("Who was the first president of the United States?"));
        assertNull("not the Eruption page: erupt is the verb", said("Why do volcanoes erupt?"));
        assertNull("small talk", said("I am tired today"));
        assertNull("a judgement", said("should I learn python or java?"));
    }

    @Test
    public void theKindOfAnswerIsReadFromTheQuestion() {
        assertEquals(EncyclopediaQA.Wants.WHEN, EncyclopediaQA.wants("When did it end?"));
        assertEquals(EncyclopediaQA.Wants.HOW_MANY, EncyclopediaQA.wants("How tall is it?"));
        assertEquals(EncyclopediaQA.Wants.WHERE, EncyclopediaQA.wants("Where is it?"));
        assertEquals(EncyclopediaQA.Wants.WHO, EncyclopediaQA.wants("Who wrote it?"));
        assertEquals(EncyclopediaQA.Wants.WHY, EncyclopediaQA.wants("Why is the sky blue?"));
        assertTrue(EncyclopediaQA.measure("How tall is Mount Everest?").matcher("8,849 metres high").find());
        assertFalse("a year is not a height", EncyclopediaQA.measure("How tall is Mount Everest?").matcher("in 1953").find());
        assertTrue(EncyclopediaQA.verbish("invented"));
        assertFalse(EncyclopediaQA.verbish("Lagos"));
        assertEquals(EncyclopediaQA.stem("founded"), EncyclopediaQA.stem("founder"));
        assertEquals(EncyclopediaQA.stem("living"), EncyclopediaQA.stem("lives"));
    }
}
