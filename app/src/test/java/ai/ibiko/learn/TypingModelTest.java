package ai.ibiko.learn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

/** The keyboard's formula: P(word | typed) ∝ P(typed | word) · P(word | the word before, the person). */
public class TypingModelTest {
    @Before
    public void open() {
        WordBook.open(ShorthandTest.lexicon());
        if (!WordRank.ready()) {
            WordRank.load(ShorthandTest.lexicon());
        }
    }

    private static File file() throws Exception {
        return new File(Files.createTempDirectory("typing").toFile(), "typing.tsv");
    }

    @Test
    public void itCountsTheirWordsAndWhichWordFollowsWhich() throws Exception {
        File f = file();
        TypingModel m = new TypingModel(f);
        m.learn("The wired headphones broke. My wired mouse works.");
        assertEquals(2, m.uses("wired"));
        assertEquals(1, m.follows("my", "wired"));
        assertEquals("a sentence end breaks the pair", 0, m.follows("broke", "my"));
        assertEquals("kept on the device", 2, new TypingModel(f).uses("wired"));
        assertEquals("wired", TypingModel.wordBefore("my wired wierd", 9));
        assertNull(TypingModel.wordBefore("wierd", 0));
    }

    @Test
    public void theirSignalsMakeAFixLikelierAndAnUndoneFixIsNeverMadeAgain() throws Exception {
        TypingModel m = new TypingModel(file());
        assertEquals(1.0, m.factor("teh", "the", null), 1e-9);
        m.learn("on the road, in the house, by the door");
        assertTrue("used and after the word before", m.factor("teh", "the", "on") < m.factor("teh", "the", "zebra"));
        m.keptFixes(Collections.singletonList(new String[]{"recieve", "receive"}));
        assertEquals(1, m.accepted("recieve", "receive"));
        m.undid(Collections.singletonList(new String[]{"recieve", "receive"}));
        assertTrue(m.rejected("recieve", "receive"));
        assertTrue(Double.isInfinite(m.factor("recieve", "receive", null)));
        assertTrue(m.undone("recieve"));
        assertEquals("an undone word is theirs: not changed into any word", "I recieve mail",
                TypoFix.fix("I recieve mail", TypoFix.DEVICE, Collections.<String>emptySet(), m).text);
        m.keptFixes(Collections.singletonList(new String[]{"recieve", "receive"}));
        assertFalse("keeping it later doesn't undo the undo", !m.rejected("recieve", "receive"));
    }

    @Test
    public void theWordBeforeSettlesACloseCall() throws Exception {
        String typed = "the wierd thing";
        String alone = TypoFix.fix(typed, TypoFix.DEVICE, Collections.<String>emptySet()).text;
        TypingModel m = new TypingModel(file());
        for (int i = 0; i < 20; i++) {
            m.learn("that was a weird thing to say. the weird part is over");
        }
        String taught = TypoFix.fix(typed, TypoFix.DEVICE, Collections.<String>emptySet(), m).text;
        assertEquals("with their typing it is read as they mean it (alone it was: " + alone + ")", "the weird thing", taught);
    }
}
