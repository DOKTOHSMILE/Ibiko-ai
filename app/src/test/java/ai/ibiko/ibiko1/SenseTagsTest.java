package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.words.MeaningIndex;
import ai.ibiko.ibiko1.words.Meanings;
import ai.ibiko.ibiko1.words.SenseTags;

/** Context tags: a word with meanings in several subjects is read in the subject its sentence (or the talk) is about. */
public class SenseTagsTest {
    private static final Meanings M = new Meanings(MeaningIndex.of(ai.ibiko.PlainAsset.of("knowledge/lexicon.tsv")));
    private static final SenseTags T;

    static {
        try {
            T = new SenseTags(M, new FileInputStream(ai.ibiko.PlainAsset.of("knowledge/senses_extra.tsv")));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String subject(String word, String sentence) {
        return T.subjectOf(T.meant(word, sentence, null, null));
    }

    @Test
    public void theWordsAroundPickTheSubject() {
        assertEquals("botany", subject("root", "The root of the tree goes deep into the soil."));
        assertEquals("computing", subject("root", "You need root access on the Linux server to install it."));
        assertEquals("maths", subject("root", "The square root of 16 is 4."));
        assertEquals("finance", subject("bank", "He deposited money at the bank."));
        assertEquals("geography", subject("bank", "We fished from the bank of the river."));
        assertEquals("computing", subject("mouse", "Click the left mouse button."));
        assertEquals("animals", subject("mouse", "The cat caught a mouse."));
        assertEquals("computing", subject("bug", "There is a bug in my code."));
        assertEquals("animals", subject("python", "The python swallowed a goat at the zoo."));
        assertEquals("computing", subject("python", "I write my scripts in python."));
        assertEquals("medicine", subject("virus", "The doctor said the virus causes fever."));
        assertEquals("computing", subject("virus", "My laptop has a virus."));
    }

    @Test
    public void theConversationPicksItWhenTheSentenceDoesnt() {
        assertEquals("computing", T.subjectOf(T.meant("root", "What is root?", "computing", null)));
        assertEquals("maths", T.subjectOf(T.meant("root", "What is root?", "maths", null)));
        // with nothing to go on, the most common meaning
        assertEquals("botany", T.subjectOf(T.meant("root", "What is root?", null, null)));
    }

    @Test
    public void aSentenceGetsItsCode() {
        String code = T.code("You need root access on the Linux server.", null);
        assertTrue(code, code.startsWith("[computing]"));
        assertTrue(code, code.contains("n.root@computing"));
        assertTrue(code, code.contains("p.you"));
        assertTrue(T.code("The root of the tree goes deep into the soil.", null).contains("n.root@botany"));
        assertTrue(T.ambiguous("root"));
        assertTrue(!T.ambiguous("soil"));
        assertNull(SenseTags.subject("hello there"));
        assertEquals("computing", SenseTags.subject("my linux server keeps crashing"));
    }

    @Test
    public void wordTalkAnswersInTheSubject() {
        WordTalk talk = new WordTalk(M, T);
        String rootIt = talk.inSubject("What is root in computing?");
        assertTrue(rootIt, rootIt.startsWith("In computing, “root” means the administrator account"));
        assertTrue(rootIt, rootIt.contains("for plants"));
        assertNull("no subject given or known: the usual answer", talk.inSubject("What is root?"));
        talk.topic("maths");
        String maths = talk.inSubject("What is a root?");
        assertTrue(maths, maths.startsWith("In maths, “root” means a number that"));
        assertNull("one meaning only: nothing to choose", talk.inSubject("What is soil?"));
        String code = talk.reply("Tag this sentence: There is a bug in my code");
        assertTrue(code, code.contains("n.bug@computing"));
        String also = talk.reply("What does mouse mean?");
        assertTrue(also, also.contains("pointing device") || also.contains("pointer"));
    }
}
