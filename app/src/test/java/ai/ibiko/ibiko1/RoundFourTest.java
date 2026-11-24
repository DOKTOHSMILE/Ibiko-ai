package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Quick;
import ai.ibiko.ibiko1.formulas.TextOps;

/** Round 4's skills: time and money worked out, text handled exactly, the people and places in the person's life. */
public class RoundFourTest {
    @Test
    public void timeAndMoney() {
        assertTrue(Quick.reply("It is 10am now. What time will it be in 3 hours?").startsWith("It will be 1pm"));
        assertTrue(Quick.reply("It's 11pm. What time will it be in 2 hours?").startsWith("It will be 1am the next day"));
        assertTrue(Quick.reply("If I save 500 naira a day for 30 days, how much will I have?").startsWith("15,000 naira"));
        assertTrue(Quick.reply("A shirt costs 2000 naira after a 20% discount. What was the original price?").startsWith("The original price was 2,500 naira"));
        assertTrue(Quick.reply("I bought a phone for 50000 naira and sold it for 65000. How much profit did I make?").startsWith("A profit of 15,000 naira"));
        assertTrue(Quick.reply("I bought a bike for 30000 and sold it for 25000. Did I make a loss?").startsWith("A loss of 5,000"));
    }

    @Test
    public void textHandledExactly() {
        assertEquals("“Nigeria” has 7 letters: N-I-G-E-R-I-A.", TextOps.reply("How many letters are in the word Nigeria?"));
        assertEquals("“hello” backwards is “olleh”.", TextOps.reply("Reverse the word hello"));
        assertEquals("In alphabetical order: apple, banana, mango.", TextOps.reply("Sort these words alphabetically: mango, apple, banana"));
        assertEquals("The first letter of “Lagos” is L.", TextOps.reply("What is the first letter of Lagos?"));
        assertEquals("There are 4 words in “I love jollof rice”.", TextOps.reply("How many words are in \"I love jollof rice\"?"));
        assertNull("spelling, not reversing", TextOps.reply("spell necessary"));
    }

    @Test
    public void thePeopleAndPlacesInTheirLife() throws Exception {
        Person p = new Person(Files.createTempDirectory("person").toFile());
        assertEquals("Noted: your sister is Ada.", p.heard("My sister is called Ada."));
        assertEquals("Noted: your dog is Bingo.", p.heard("My dog's name is Bingo"));
        assertNull("about her, not her name", p.heard("My sister is a doctor"));
        assertEquals("Your sister is Ada.", p.asked("What is my sister's name?", null));
        assertEquals("Your dog is Bingo.", p.asked("What is my dog called?", null));
        p.heard("I live in Ghana");
        assertEquals("What is the capital of Ghana?", p.fillIn("What is the capital of the country I live in?"));
        assertEquals("Where do I live?", p.fillIn("Where do I live?"));
    }
}
