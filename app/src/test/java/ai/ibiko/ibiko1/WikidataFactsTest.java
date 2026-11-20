package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** The Wikidata facts' new questions: LGAs, states, towns, inventions, people, mountains, rivers, lakes. */
public class WikidataFactsTest {
    static final String SAMPLE = "Sapele\tstate\tDelta State\nSapele\tcountry\tNigeria\nSapele\tpopulation\t242,652\n"
            + "Delta State\tlocal government area count\t3\n"
            + "Delta State\tlocal government area\tSapele\nDelta State\tlocal government area\tUghelli North\n"
            + "Delta State\tlocal government area\tWarri South\nDelta State\tcreated\t1991\n"
            + "telephone\tinventor\tAlexander Graham Bell\ntelephone\tinvented\t1876\n"
            + "Chinua Achebe\tborn\t16 November 1930\nChinua Achebe\tdied\t21 March 2013\n"
            + "Chinua Achebe\tnationality\tNigeria\nChinua Achebe\toccupation\tnovelist\n"
            + "Mount Kilimanjaro\televation\t5,895 m\nMount Kilimanjaro\tcountry\tTanzania\n"
            + "Niger River\tlength\t4,180 km\nLake Chad\tarea\t1,350\n";

    static BuiltIn facts() {
        return new BuiltIn(new ByteArrayInputStream(SAMPLE.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void nigeria() {
        BuiltIn b = facts();
        assertEquals("Sapele is in Delta State.", b.answer("Which state is Sapele in?"));
        assertEquals("Delta State has 3 local government areas: Sapele, Ughelli North and Warri South.",
                b.answer("What are the local government areas in Delta State?"));
        assertEquals("Delta State has 3 local government areas.", b.answer("How many LGAs does Delta State have?"));
        assertEquals("Delta State was created in 1991.", b.answer("When was Delta State created?"));
        assertTrue(b.answer("What is the population of Sapele?").contains("242,652"));
    }

    @Test
    public void inventionsPeoplePlaces() {
        BuiltIn b = facts();
        assertEquals("Telephone was invented by Alexander Graham Bell in 1876.", b.answer("Who invented the telephone?"));
        assertEquals("Telephone was discovered by Alexander Graham Bell in 1876.", b.answer("Who discovered the telephone?"));
        assertEquals("Telephone was invented in 1876 by Alexander Graham Bell.", b.answer("When was the telephone invented?"));
        assertEquals("Chinua Achebe was born on 16 November 1930 and died on 21 March 2013.", b.answer("When was Chinua Achebe born?"));
        assertEquals("Chinua Achebe was a novelist from Nigeria.", b.answer("What did Chinua Achebe do?"));
        assertEquals("Chinua Achebe was a citizen of Nigeria.", b.answer("What was Chinua Achebe's nationality?"));
        assertEquals("Mount Kilimanjaro is 5,895 m high.", b.answer("How high is Mount Kilimanjaro?"));
        assertEquals("Mount Kilimanjaro is in Tanzania.", b.answer("Which country is Mount Kilimanjaro in?"));
        assertEquals("Niger River is 4,180 km long.", b.answer("How long is the Niger River?"));
        assertTrue(b.answer("How big is Lake Chad?").contains("1,350"));
        assertNull(b.answer("Tell me a joke"));
    }

    /** The shipped file itself, asked real questions. */
    @Test
    public void theShippedFacts() throws Exception {
        BuiltIn b = new BuiltIn(new java.io.FileInputStream(ai.ibiko.PlainAsset.of("knowledge/wikidata_facts.tsv")));
        System.out.println("facts: " + b.size());
        assertTrue(b.size() > 100000);
        String[] qs = {"Which state is Sapele in?", "How many local government areas does Delta State have?",
            "When was Delta State created?", "Who invented the telephone?", "When was penicillin discovered?",
            "When was Chinua Achebe born?", "What did Fela Kuti do?", "How high is Mount Kilimanjaro?",
            "How long is the Niger River?", "What is the population of Warri?", "Which country is Kumasi in?",
            "Which state is Ughelli North in?", "When was Wole Soyinka born?"};
        for (String q : qs) {
            String a = b.answer(q);
            System.out.println(q + " -> " + a);
            assertTrue(q, a != null);
        }
        assertTrue(b.answer("Which state is Sapele in?").contains("Delta State"));
        assertTrue(b.answer("Who invented the telephone?").contains("Alexander Graham Bell"));
        assertEquals("Delta State has 25 local government areas.", b.answer("How many local government areas does Delta State have?"));
        assertTrue(b.answer("What are the LGAs in Delta State?").contains("Warri South West") && !b.answer("What are the LGAs in Delta State?").contains("Ozoro"));
        assertEquals("Lagos State has 20 local government areas.", b.answer("How many LGAs are in Lagos State?"));
        // ordinary questions aren't hijacked by a person's or a city's name
        for (String q : new String[] {"How are you?", "What is love?", "Tell me a joke", "Who are you?", "What time is it?",
                "How long is a day on Mars?", "Where is my phone?", "What is the meaning of life?"}) {
            String a = b.answer(q);
            System.out.println(q + " -> " + a);
            assertNull(q + " -> " + a, a);
        }
    }
}
