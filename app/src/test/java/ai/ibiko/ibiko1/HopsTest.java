package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Hops;

/** Questions inside questions: the inner ones answered from the facts first, then the one left. */
public class HopsTest {
    private static final Map<String, String> FACTS = new HashMap<String, String>();

    static {
        FACTS.put("Mount Kilimanjaro|location", "Kilimanjaro Region, north east Tanzania");
        FACTS.put("Tanzania|capital", "Dodoma (de jure), Dar es Salaam (de facto)");
        FACTS.put("Tanzania|leader", "Samia Hassan Suluhu");
        FACTS.put("Wole Soyinka|birth_place", "Abeokuta, Nigeria Protectorate (now Ogun State, Nigeria)");
        FACTS.put("Barack Obama|spouse", "Michelle Robinson");
        FACTS.put("Barack Obama|birth_place", "Honolulu, Hawaii, U.S.");
        FACTS.put("Microsoft|founder", "Bill Gates, Paul Allen");
        FACTS.put("Eiffel Tower|location", "7th arrondissement, Paris, France");
    }

    private static String fact(String thing, String relation) {
        return FACTS.get(thing + "|" + relation);
    }

    @Test
    public void twoStepsBecomeOneQuestion() {
        Hops.Done d = Hops.resolve("What is the capital of the country where Mount Kilimanjaro is?", HopsTest::fact);
        assertEquals("What is the capital of Tanzania?", d.text);
        assertEquals("Working out the country Mount Kilimanjaro is in: Tanzania", d.steps.get(0));
        Hops.Done three = Hops.resolve("How old is the president of the country where Mount Kilimanjaro is?", HopsTest::fact);
        assertEquals("How old is Samia Hassan Suluhu?", three.text);
        assertEquals(2, three.steps.size());
    }

    @Test
    public void eachKindOfStep() {
        assertEquals("When was Bill Gates born?", Hops.resolve("When was the founder of Microsoft born?", HopsTest::fact).text);
        assertEquals("Where was Michelle Robinson born?", Hops.resolve("Where was Barack Obama's wife born?", HopsTest::fact).text);
        assertEquals("what it is now, not the old name", "What is the population of Nigeria?",
                Hops.resolve("What is the population of the country where Wole Soyinka was born?", HopsTest::fact).text);
        assertEquals("Tell me about Abeokuta", Hops.resolve("Tell me about the town where Wole Soyinka was born", HopsTest::fact).text);
        assertEquals("What language is spoken in France?", Hops.resolve("What language is spoken in the country where the Eiffel Tower is?", HopsTest::fact).text);
        assertEquals("Who is the president of the United States?",
                Hops.resolve("Who is the president of the country where Barack Obama was born?", HopsTest::fact).text);
    }

    @Test
    public void theQuestionItselfIsLeftAsItIs() {
        assertNull("the whole question is the phrase: answered as it is", Hops.resolve("What is the capital of Tanzania?", HopsTest::fact));
        assertNull(Hops.resolve("Who is the president of Tanzania?", HopsTest::fact));
        assertNull("not known: no step is made up", Hops.resolve("What is the capital of the country where Atlantis is?", HopsTest::fact));
        assertNull(Hops.resolve("How are you?", HopsTest::fact));
    }

    @Test
    public void theStepsAreSaid() {
        Hops.Done d = Hops.resolve("When was the founder of Microsoft born?", HopsTest::fact);
        assertTrue(d.steps.get(0), d.steps.get(0).equals("Working out the founder of Microsoft: Bill Gates"));
    }
}
