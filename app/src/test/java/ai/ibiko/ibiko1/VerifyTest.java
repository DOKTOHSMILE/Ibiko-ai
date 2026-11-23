package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Verify;

/** Yes-or-no claims checked against the facts: yes or no first, and the true fact said. */
public class VerifyTest {
    private static final Map<String, String> FACTS = new HashMap<String, String>();

    static {
        FACTS.put("Nigeria|capital", "Abuja");
        FACTS.put("Nigeria|currency", "Nigerian naira");
        FACTS.put("Tanzania|language", "Swahili, English");
        FACTS.put("Tanzania|leader", "Samia Hassan Suluhu");
        FACTS.put("Barack Obama|birth_place", "Honolulu, Hawaii, U.S.");
        FACTS.put("Barack Obama|spouse", "Michelle Robinson");
        FACTS.put("Wole Soyinka|born", "13 July 1934");
        FACTS.put("Microsoft|founder", "Bill Gates, Paul Allen");
        FACTS.put("Eiffel Tower|location", "7th arrondissement, Paris, France");
        FACTS.put("Mount Kilimanjaro|elevation", "5,895 m");
    }

    private static Verify.Said check(String q) {
        return Verify.check(q, (thing, rel) -> FACTS.get(thing + "|" + rel));
    }

    @Test
    public void aWrongClaimIsNoWithTheTrueFact() {
        Verify.Said s = check("Is Lagos the capital of Nigeria?");
        assertFalse(s.yes);
        assertEquals("No. The capital of Nigeria is Abuja, not Lagos.", s.text);
        assertEquals("No. Barack Obama was born in Honolulu, Hawaii, U.S., not in Kenya.", check("Was Barack Obama born in Kenya?").text);
        assertEquals("No. Eiffel Tower is in 7th arrondissement, Paris, France.", check("Is the Eiffel Tower in Germany?").text);
        assertEquals("No. The currency of Nigeria is the Nigerian naira, not dollar.", check("Is the currency of Nigeria the dollar?").text);
    }

    @Test
    public void aTrueClaimIsYes() {
        assertEquals("Yes. The capital of Nigeria is Abuja.", check("Is Abuja the capital of Nigeria?").text);
        assertEquals("Yes. Wole Soyinka was born on 13 July 1934.", check("Was Wole Soyinka born in 1934?").text);
        assertEquals("Yes. Microsoft was founded by Bill Gates and Paul Allen.", check("Did Bill Gates found Microsoft?").text);
        assertTrue(check("Was Microsoft founded by Paul Allen?").yes);
        assertTrue(check("Is Swahili spoken in Tanzania?").yes);
        assertTrue("a surname and first name are enough", check("Is Samia Suluhu the president of Tanzania?").yes);
        assertTrue(check("Is Barack Obama married to Michelle Robinson?").yes);
        assertTrue(check("Is Mount Kilimanjaro taller than 5000 m?").yes);
        assertFalse(check("Is Mount Kilimanjaro taller than 6000 m?").yes);
        assertTrue("feet are turned into metres", check("Is Mount Kilimanjaro higher than 19000 feet?").yes);
    }

    @Test
    public void noFactNoAnswer() {
        assertNull("not known: not guessed", check("Is Atlantis the capital of Nigeria?") == null ? null : check("Is Atlantis in Greece?"));
        assertNull(check("Is it raining?"));
        assertNull(check("What is the capital of Nigeria?"));
        assertNull(check("Was Chinua Achebe born in 1930?"));
    }
}
