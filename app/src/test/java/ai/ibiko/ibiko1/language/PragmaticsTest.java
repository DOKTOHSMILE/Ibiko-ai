package ai.ibiko.ibiko1.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Contextual intelligence: what a sentence does and means, its tone, what it implies, and its open ambiguities. */
public class PragmaticsTest {
    @Test
    public void questionsThatAreRequests() {
        Pragmatics.Reading r = Pragmatics.read("Can you open the door?", null);
        assertEquals("request", r.act);
        assertTrue(r.intended, r.intended.startsWith("please open the door"));
        assertEquals("question", Pragmatics.read("Can you swim?", null).act);
        assertEquals("request", Pragmatics.read("Would you mind closing the window?", null).act);
        assertEquals("request", Pragmatics.read("I was wondering if you could send me the file.", null).act);
        Pragmatics.Reading t = Pragmatics.read("Do you know what time it is?", null);
        assertEquals("question", t.act);
        assertTrue(t.intended, t.intended.contains("what time it is"));
        assertEquals("suggestion", Pragmatics.read("Why don't we eat out tonight?", null).act);
        assertEquals("suggestion", Pragmatics.read("Let's go to the beach.", null).act);
        assertEquals("permission", Pragmatics.read("Is anyone sitting here?", null).act);
        assertEquals("offer", Pragmatics.read("Shall I make some tea?", null).act);
        assertEquals("greeting", Pragmatics.read("How far, bros?", null).act);
        assertEquals("statement", Pragmatics.read("The meeting is at noon.", null).act);
        assertNull(Pragmatics.read("The meeting is at noon.", null).intended);
    }

    @Test
    public void hintsToneAndFeeling() {
        Pragmatics.Reading r = Pragmatics.read("It's cold in here.", null);
        assertEquals("hint", r.act);
        assertTrue(r.implied.get(0), r.implied.get(0).contains("window closed"));
        assertTrue(Pragmatics.read("Could you please send it?", null).tones.contains("polite"));
        assertTrue(Pragmatics.read("I need this fixed ASAP!", null).tones.contains("urgent"));
        assertTrue(Pragmatics.read("This is RIDICULOUS, I am FED up!!", null).tones.contains("angry"));
        assertTrue(Pragmatics.read("Maybe it was Tuesday, I'm not sure.", null).tones.contains("uncertain"));
        assertTrue(Pragmatics.read("I'm so worried about the exam.", null).feelings.contains("worry"));
        assertTrue(Pragmatics.read("I'm fed up, the app keeps crashing.", null).act.equals("complaint"));
    }

    @Test
    public void sarcasmNeedsContext() {
        Figures f = new Figures(null, null, null);
        Pragmatics.Reading bad = Pragmatics.read("That's just great!", "My laptop crashed and I lost my work.", f);
        assertTrue(bad.tones.contains("sarcastic"));
        assertFalse(bad.feelings.contains("joy"));
        Pragmatics.Reading good = Pragmatics.read("That's just great!", "I passed the exam!", f);
        assertFalse(good.tones.contains("sarcastic"));
    }

    @Test
    public void ambiguitiesStayOpen() {
        Pragmatics.Reading r = Pragmatics.read("I saw her duck.", null);
        assertEquals(1, r.ambiguities.size());
        assertTrue(r.ambiguities.get(0), r.ambiguities.get(0).contains("belongs to her"));
        assertTrue(Pragmatics.read("I saw her car.", null).ambiguities.isEmpty());
        assertTrue(Pragmatics.read("I saw the man with the telescope.", null).ambiguities.toString().contains("ambiguous"));
    }
}
