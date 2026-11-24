package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Logic;
import ai.ibiko.ibiko1.formulas.Quick;
import ai.ibiko.ibiko1.formulas.Spelling;
import ai.ibiko.ibiko1.formulas.Tally;

/** Round 3's skills: counts kept through a talk, word problems, typos put right, one-sentence syllogisms. */
public class RoundThreeTest {
    @Test
    public void countsAreKeptThroughTheTalk() {
        Tally t = new Tally();
        assertEquals("Noted: you have 3 apples.", t.heard("I have 3 apples."));
        assertEquals("Noted: that makes 7 apples.", t.heard("I buy 4 more apples."));
        assertEquals("Noted: that makes 5 apples.", t.heard("I ate 2 of them"));
        assertEquals("You have 5 apples: 3, + 4, − 2 = 5.", t.asked("How many apples do I have now?"));
        assertNull("nothing said about pears", t.asked("How many pears do I have?"));
        assertNull("not a count", t.heard("I have a dream"));
        assertEquals("Noted: you have 1 dog.", t.heard("I have one dog"));
    }

    @Test
    public void wordProblems() {
        assertTrue(Quick.reply("Share 90 naira equally among 3 children. How much does each get?").startsWith("Each gets 30 naira"));
        assertTrue(Quick.reply("I paid 1000 naira for something that costs 650. How much change do I get?").startsWith("Your change is 350"));
        assertTrue(Quick.reply("What is the area of a rectangle 5 m long and 3 m wide?").startsWith("The area is 15 square metres"));
        assertEquals("Saturday comes after Friday.", Quick.reply("What day comes after Friday?"));
        assertEquals("Sunday comes before Monday.", Quick.reply("Which day is before Monday?"));
    }

    @Test
    public void typosInQuestionsArePutRight() {
        Set<String> words = new HashSet<String>(Arrays.asList("capital", "cantal", "nigeria", "spider", "leg", "spade"));
        Map<String, Integer> senses = new HashMap<String, Integer>();
        senses.put("capital", 9);
        senses.put("cantal", 1);
        senses.put("spider", 3);
        Spelling.Fixed f = Spelling.fix("what is the captal of nigeria?", w -> words.contains(w) || w.equals("legs"), words::contains,
                w -> senses.getOrDefault(w, 1));
        assertNotNull(f);
        assertEquals("what is the capital of nigeria?", f.text);
        assertEquals(Arrays.asList("“captal” as “capital”"), f.changes);
        Spelling.Fixed g = Spelling.fix("how many legs does a spidr have?", w -> words.contains(w) || w.equals("legs"), words::contains,
                w -> senses.getOrDefault(w, 1));
        assertEquals("how many legs does a spider have?", g.text);
        java.util.Map<String, Integer> tie = new java.util.HashMap<String, Integer>();
        tie.put("author", 3);
        tie.put("tutor", 3);
        tie.put("actor", 2);
        Spelling.Fixed h = Spelling.fix("who is the autor of things fall apart?", w -> !w.equals("autor"), tie::containsKey,
                w -> tie.getOrDefault(w, 1));
        assertNotNull("a letter left out wins a tie", h);
        assertEquals("who is the author of things fall apart?", h.text);
        assertNull("small words aren't typos", Spelling.fix("what currency do they use?", w -> false, w -> w.equals("thew"), w -> 5));
        assertNull("names in capitals are left alone", Spelling.fix("who is Chinedu?", w -> false, w -> true, w -> 5));
    }

    @Test
    public void aSyllogismInOneSentence() {
        String a = Logic.answer("If all birds have feathers and a penguin is a bird, does a penguin have feathers?", null);
        assertNotNull(a);
        assertTrue(a, a.startsWith("Yes"));
    }
}
