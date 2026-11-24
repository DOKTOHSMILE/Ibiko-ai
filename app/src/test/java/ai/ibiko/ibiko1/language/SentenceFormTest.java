package ai.ibiko.ibiko1.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Grammar intelligence: structure, function, the twelve tenses, voice, negation, conditionals, reported speech. */
public class SentenceFormTest {
    private static String tense(String s) {
        SentenceForm.Form f = SentenceForm.read(s);
        return f.tense == null ? null : f.tense.name;
    }

    @Test
    public void theTwelveTenses() {
        assertEquals("simple present", tense("She writes letters every week."));
        assertEquals("present continuous", tense("She is writing a letter."));
        assertEquals("present perfect", tense("She has written three letters."));
        assertEquals("present perfect continuous", tense("She has been writing all morning."));
        assertEquals("simple past", tense("She wrote a letter yesterday."));
        assertEquals("simple past", tense("They walked to school."));
        assertEquals("past continuous", tense("She was writing when I called."));
        assertEquals("past perfect", tense("She had written it before we arrived."));
        assertEquals("past perfect continuous", tense("She had been writing for hours."));
        assertEquals("simple future", tense("She will write tomorrow."));
        assertEquals("future continuous", tense("She will be writing at noon."));
        assertEquals("future perfect", tense("She will have written it by Friday."));
        assertEquals("future perfect continuous", tense("She will have been writing for a year."));
        assertEquals("simple future", tense("It is going to rain."));
        assertEquals("simple present", tense("She is a doctor."));
        assertEquals("simple past", tense("Did you eat?"));
        assertEquals("present perfect", tense("She's gone home."));
        assertEquals("simple present", tense("I don't know."));
    }

    @Test
    public void voiceAndModals() {
        assertEquals("simple past, passive", tense("The letter was written by Ada."));
        assertEquals("present perfect, passive", tense("The house has been sold."));
        assertEquals("present continuous, passive", tense("The road is being repaired."));
        assertEquals("simple future, passive", tense("It will be finished soon."));
        assertEquals("modal “can”", tense("I can swim."));
        assertEquals("modal “should” (perfect)", tense("You should have called."));
    }

    @Test
    public void structures() {
        assertEquals(SentenceForm.Structure.SIMPLE, SentenceForm.read("The dog barked.").structure);
        assertEquals(SentenceForm.Structure.SIMPLE, SentenceForm.read("I bought bread and butter.").structure);
        assertEquals(SentenceForm.Structure.SIMPLE, SentenceForm.read("I came and saw the city.").structure);
        assertEquals(SentenceForm.Structure.COMPOUND, SentenceForm.read("I wanted to go, but it was raining.").structure);
        assertEquals(SentenceForm.Structure.COMPOUND, SentenceForm.read("She sings; he dances.").structure);
        assertEquals(SentenceForm.Structure.COMPLEX, SentenceForm.read("I stayed home because it was raining.").structure);
        assertEquals(SentenceForm.Structure.COMPLEX, SentenceForm.read("The man who called is my uncle.").structure);
        assertEquals(SentenceForm.Structure.COMPLEX, SentenceForm.read("She said that she was tired.").structure);
        assertEquals(SentenceForm.Structure.COMPOUND_COMPLEX,
                SentenceForm.read("I stayed home because it was raining, and my sister went out.").structure);
        assertEquals(SentenceForm.Structure.SIMPLE, SentenceForm.read("We left because of the rain.").structure);
    }

    @Test
    public void functions() {
        assertEquals(SentenceForm.Function.DECLARATIVE, SentenceForm.read("The sun rises in the east.").function);
        assertEquals(SentenceForm.Function.INTERROGATIVE, SentenceForm.read("Where do you live?").function);
        assertEquals("wh-", SentenceForm.read("Where do you live?").question);
        assertEquals("yes/no", SentenceForm.read("Can you open the door?").question);
        assertEquals("tag", SentenceForm.read("It's cold, isn't it?").question);
        assertEquals(SentenceForm.Function.IMPERATIVE, SentenceForm.read("Open the door.").function);
        assertEquals(SentenceForm.Function.IMPERATIVE, SentenceForm.read("Please sit down.").function);
        assertEquals(SentenceForm.Function.IMPERATIVE, SentenceForm.read("Don't touch that.").function);
        assertEquals(SentenceForm.Function.IMPERATIVE, SentenceForm.read("Let's go.").function);
        assertEquals(SentenceForm.Function.EXCLAMATORY, SentenceForm.read("What a beautiful day!").function);
        assertEquals(SentenceForm.Function.EXCLAMATORY, SentenceForm.read("How kind you are!").function);
    }

    @Test
    public void negationConditionalsAndSpeech() {
        assertTrue(SentenceForm.read("I never eat meat.").negated);
        assertTrue(SentenceForm.read("She isn't here.").negated);
        assertFalse(SentenceForm.read("She is here.").negated);
        assertEquals("zero", SentenceForm.read("If you heat ice, it melts.").conditional);
        assertEquals("first", SentenceForm.read("If it rains, we will stay home.").conditional);
        assertEquals("second", SentenceForm.read("If I won the lottery, I would travel.").conditional);
        assertEquals("second", SentenceForm.read("If I were you, I would apologise.").conditional);
        assertEquals("third", SentenceForm.read("If she had studied, she would have passed.").conditional);
        assertEquals("first", SentenceForm.read("We will stay home if it rains.").conditional);
        assertNull(SentenceForm.read("We stayed home.").conditional);
        assertEquals("direct", SentenceForm.read("He said, \"I am tired.\"").speech);
        assertEquals("indirect", SentenceForm.read("He said that he was tired.").speech);
        assertEquals("indirect", SentenceForm.read("She asked whether I was coming.").speech);
        assertEquals("indirect", SentenceForm.read("She told me to wait.").speech);
    }

    @Test
    public void saidInPlainWords() {
        String s = SentenceForm.read("If she had studied, she would have passed.").said();
        assertTrue(s, s.startsWith("Complex sentence (1 independent clause, 1 dependent), declarative"));
        assertTrue(s, s.contains("third conditional"));
    }
}
