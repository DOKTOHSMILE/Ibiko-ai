package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Readings;
import ai.ibiko.ibiko1.formulas.Readings.Kind;
import ai.ibiko.ibiko1.formulas.Recipes;
import ai.ibiko.ibiko1.formulas.Sites;

/** The thinking playground: readings from the words as well as the phrasing, and asking when two can't be told apart. */
public class ReadingsTest {
    @Test
    public void tooCloseToCallIsAskedNotGuessed() {
        List<Readings.Reading> r = Readings.weigh("Make a contact form", Arrays.asList(Kind.SITE, Kind.CODING), new Readings.Context());
        assertTrue(Readings.unsure(r));
        assertEquals("Do you mean a website, or code to write? Say which, and I'll go on from there.", Readings.ask(r));
        // the conversation tips it: no question
        List<Readings.Reading> after = Readings.weigh("Make a contact form", Arrays.asList(Kind.SITE, Kind.CODING),
                new Readings.Context().said("web"));
        assertFalse(Readings.unsure(after));
        assertEquals(Kind.SITE, after.get(0).kind);
        // code in the message is never asked about
        assertFalse(Readings.unsure(Readings.weigh("x", Arrays.asList(Kind.CODE, Kind.WORD), null)));
    }

    @Test
    public void theAnswerPicksTheReading() {
        List<Kind> asked = Arrays.asList(Kind.SITE, Kind.CODING);
        assertEquals(Kind.SITE, Readings.choose("the first", asked));
        assertEquals(Kind.CODING, Readings.choose("The second one", asked));
        assertEquals(Kind.CODING, Readings.choose("2", asked));
        assertEquals(Kind.SITE, Readings.choose("I mean a website", asked));
        assertEquals(Kind.CODING, Readings.choose("code please", asked));
        assertNull("both named: still not told apart", Readings.choose("a website with code", asked));
        assertNull("a new message, not an answer", Readings.choose("What is the capital of Ghana and who leads it today in the region", asked));
    }

    @Test
    public void looseReadingsStartLowerAndSaySo() {
        List<Readings.Reading> r = Readings.weigh("Sort a list", Collections.singletonList(Kind.CODING), new Readings.Context(),
                new HashSet<Kind>(Collections.singletonList(Kind.CODING)));
        assertEquals(0.8, r.get(0).score, 0.001);
        assertTrue(r.get(0).why, r.get(0).why.startsWith("from its words, not the usual way of asking"));
    }

    @Test
    public void codeToWriteByItsWords() {
        assertEquals("sort_list", Recipes.loose("Sort a list").task);
        assertEquals("go", Recipes.loose("Reverse a string in Go").lang);
        assertNotNull(Recipes.loose("Parse JSON"));
        assertNotNull(Recipes.loose("Remove duplicates from a list"));
        assertNull("data: a thing to do", Recipes.loose("Sort these numbers: 5 3 1"));
        assertNull("data: a thing to do", Recipes.loose("Reverse the word hello"));
        assertNull("mentioned, not asked for", Recipes.loose("The prime number"));
        assertNull(Recipes.loose("Prime minister of Nigeria"));
        assertNull(Recipes.loose("Make the class quiet"));
        assertNull(Recipes.loose("Count the words in my essay"));
    }

    @Test
    public void aWebsiteByItsWords() {
        assertEquals(Sites.Ask.Is.BUILD, Sites.loose("Write html for a bakery website").is);
        assertEquals(Sites.Ask.Is.BUILD, Sites.loose("I need a landing page for my shop").is);
        assertEquals(Sites.Ask.Is.MADE_OF, Sites.loose("What is a website?").is);
        assertNull(Sites.loose("I visited their website yesterday"));
    }
}
