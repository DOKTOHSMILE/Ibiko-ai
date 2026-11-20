package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A reply to what it asked back about is formed into one proper sentence before it is kept. */
public class CuriosityTest {

    private static Curiosity.Reading read(String q, String reply) {
        return Curiosity.read(Curiosity.open(q, 0L), reply);
    }

    private static String kept(String q, String reply) {
        return read(q, reply).statement;
    }

    @Test
    public void aReplyIsFormedIntoASentence() {
        assertEquals("The capital of Ghana is Accra.", kept("What is the capital of Ghana?", "accra"));
        assertEquals("The capital of Ghana is Accra.", kept("What is the capital of Ghana?", "It's Accra"));
        assertEquals("Aliko Dangote founded Dangote Group.", kept("Who founded Dangote Group?", "some guy called Aliko Dangote I think"));
        assertEquals("Chinua Achebe was born in 1930.", kept("When was Chinua Achebe born?", "1930"));
        assertEquals("Chinua Achebe was born in Ogidi.", kept("Where was Chinua Achebe born?", "Ogidi"));
        assertEquals("Chinua Achebe was born in 1930.", kept("When was Chinua Achebe born?", "He was born in 1930"));
        assertEquals("Ikeja is in Lagos.", kept("Where is Ikeja?", "it's in Lagos"));
        assertEquals("Ngozi is from Ogwashi-Ukwu.", kept("Where is Ngozi from?", "Ogwashi-Ukwu"));
        assertEquals("Nigeria has 36 states.", kept("How many states does Nigeria have?", "36"));
        assertEquals("There are 774 local government areas in Nigeria.",
                kept("How many local government areas are there in Nigeria?", "774"));
        assertEquals("Ngozi is an economist.", kept("What does Ngozi do?", "she's an economist"));
        assertEquals("Things Fall Apart was written by Chinua Achebe.", kept("Who was Things Fall Apart written by?", "Chinua Achebe"));
        assertEquals("Wole Soyinka wrote Death and the King's Horseman.",
                kept("Who wrote Death and the King's Horseman?", "um, Wole Soyinka"));
    }

    @Test
    public void whatIsAboutThePersonIsSaidBackAsYours() {
        assertEquals("Your favourite colour is blue.", kept("What is my favourite colour?", "blue"));
        assertEquals("You live in Lagos.", kept("Where do I live?", "Lagos"));
        assertEquals("Your mum's name is Ada.", kept("What is my mum's name?", "Ada"));
    }

    @Test
    public void aWholeSentenceIsKeptAsSaidTidied() {
        assertEquals("Accra is the capital of Ghana.", kept("What is the capital of Ghana?", "accra is the capital of ghana"));
    }

    @Test
    public void hedgesMakeItCheckFirst() {
        assertTrue(read("Who founded Dangote Group?", "I think Aliko Dangote").unsure);
        assertTrue(read("What is the capital of Ghana?", "maybe Accra").unsure);
        assertTrue(!read("What is the capital of Ghana?", "Accra").unsure);
    }

    @Test
    public void notAnAnswerIsNotKept() {
        Curiosity.Reading r = read("Who founded Dangote Group?", "I don't know");
        assertTrue(r.declined);
        assertNull(r.statement);
        assertTrue(read("What is the capital of Ghana?", "no idea, sorry").declined);
        Curiosity.Gap g = Curiosity.open("Who founded Dangote Group?", 0L);
        assertTrue(Curiosity.movesOn(g, "What is the capital of Togo?"));
        assertTrue(Curiosity.movesOn(g, "write me a poem"));
        assertTrue(!Curiosity.movesOn(g, "Aliko Dangote"));
        assertNull(Curiosity.open("Sing for me", 0L));
    }

    @Test
    public void yesNoAndACorrection() {
        assertTrue(Curiosity.yes("Yes, that's right"));
        assertTrue(Curiosity.no("No, it's Accra"));
        assertEquals("Accra", Curiosity.instead("No, it's Accra"));
        assertNull(Curiosity.instead("no"));
    }
}
