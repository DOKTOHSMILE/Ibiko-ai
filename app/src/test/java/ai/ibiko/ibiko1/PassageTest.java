package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A pasted text: told apart from a remark, read for what it is about, and asked about. */
public class PassageTest {
    @Test
    public void aLongTextIsReadAndARemarkIsNot() {
        assertNull(Passage.of("Lagos is a big city.", 0));
        Passage p = Passage.of(PassageFlowTest.TEXT, 0);
        assertEquals("Chiamaka Okafor", p.main());
        assertTrue(p.names.contains("FarmLink"));
        assertEquals(Context.Kind.WOMAN, p.kind());
        assertEquals(3, p.points(3).size());
        assertTrue(p.fresh(Passage.FRESH_MS));
        assertFalse(p.fresh(Passage.FRESH_MS + 1));
    }

    @Test
    public void aQuestionAfterTheTextIsKeptToAnswer() {
        Passage p = Passage.of(PassageFlowTest.TEXT + "\n\nWhen was FarmLink started?", 0);
        assertEquals("When was FarmLink started?", p.question);
        assertTrue(p.answer(p.question), p.answer(p.question).contains("2014"));
        assertTrue(Passage.asksWhole("Summarise it"));
        assertTrue(Passage.asksWhole("what is the text about?"));
        assertFalse(Passage.asksWhole("What is a volcano?"));
        assertNull("not about the text", p.answer("What is the boiling point of water?"));
    }
}
