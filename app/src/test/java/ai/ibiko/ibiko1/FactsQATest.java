package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Questions answered exactly from the shipped fact tables (the articles' information boxes). */
public class FactsQATest {
    private static final Encyclopedia ENC = new Encyclopedia(path -> {
        try {
            return new java.io.FileInputStream(new java.io.File("src/main/assets", path));
        } catch (java.io.IOException e) {
            return null;
        }
    });
    private static final FactsQA QA = new FactsQA(ENC);

    private static String said(String q) {
        FactsQA.Found f = QA.answer(q);
        return f == null ? null : f.text;
    }

    @Test
    public void anArticlesBoxIsRead() {
        assertEquals("18 July 1918", ENC.facts("Nelson Mandela").get("born"));
        assertEquals("Abuja", ENC.facts("Nigeria").get("capital"));
        assertTrue(ENC.facts("Zzzz no such article").isEmpty());
    }

    @Test
    public void peopleDatesAndPlaces() {
        assertEquals("Nelson Mandela was born on 18 July 1918.", said("When was Nelson Mandela born?"));
        assertEquals("Nelson Mandela died on 5 December 2013.", said("When did Nelson Mandela die?"));
        assertTrue(said("Where was Albert Einstein born?"), said("Where was Albert Einstein born?").startsWith("Albert Einstein was born in Ulm"));
        assertEquals("Nelson Mandela was born on 18 July 1918 and died on 5 December 2013, at the age of 95.", said("How old was Nelson Mandela?"));
        assertTrue(said("Who was Barack Obama married to?"), said("Who was Barack Obama married to?").contains("Michelle"));
    }

    @Test
    public void placesCompaniesAndWorks() {
        assertEquals("The capital of Nigeria is Abuja.", said("What is the capital of Nigeria?"));
        assertTrue(said("What is the population of Lagos?"), said("What is the population of Lagos?").startsWith("Lagos has a population of 8,048,430"));
        assertEquals("Microsoft was founded by Bill Gates and Paul Allen.", said("Who founded Microsoft?"));
        assertTrue(said("Do you know who founded Microsoft?").contains("Bill Gates"));
        assertTrue(said("Who wrote Harry Potter and the Philosopher's Stone?"), said("Who wrote Harry Potter and the Philosopher's Stone?").contains("Rowling"));
        assertTrue(said("When was Microsoft founded?").startsWith("Microsoft was founded on 4 April 1975"));
    }

    @Test
    public void nothingWhenTheBoxDoesntSay() {
        assertNull(said("What is the capital of Lagos?")); // Lagos's box has no capital
        assertNull(said("What is a volcano?"));
        assertNull(said("How are you?"));
    }
}
