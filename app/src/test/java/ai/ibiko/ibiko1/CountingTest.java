package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Counting;

/** Counting stories worked through step by step; impossible ones said to be so; other messages left alone. */
public class CountingTest {
    @Test
    public void stepByStep() {
        assertEquals("2 apples are left.\n\n• Start with 3 apples\n• Eat 1: 3 − 1 = 2",
                Counting.reply("If I have 3 apples and eat one, how many are left?"));
        assertEquals("1 sweet is left.\n\n• Start with 12 sweets\n• Gave away 4: 12 − 4 = 8\n• Ate 7: 8 − 7 = 1",
                Counting.reply("Ada had 12 sweets. She gave away 4 and ate 7. How many are left?"));
        String more = Counting.reply("Tunde has 5 mangoes. He buys 3 more and finds 2. How many does he have now?");
        assertTrue(more, more.startsWith("That makes 10 mangoes.") && more.contains("Buys 3: 5 + 3 = 8") && more.contains("Finds 2: 8 + 2 = 10"));
        String groups = Counting.reply("There are 4 bags with 6 oranges each. How many oranges are there altogether?");
        assertTrue(groups, groups.startsWith("That makes 24 oranges.") && groups.contains("4 × 6 = 24"));
        String share = Counting.reply("Kemi has 14 biscuits and shares them equally among 4 friends. How many does each get?");
        assertTrue(share, share.startsWith("Each gets 3 biscuits, and 2 are left over.") && share.contains("14 ÷ 4 = 3, with 2 left over"));
        String doubled = Counting.reply("I have 6 marbles. My brother doubles them. Then I lose 4. How many do I have?");
        assertTrue(doubled, doubled.startsWith("That makes 8 marbles.") && doubled.contains("6 × 2 = 12"));
    }

    @Test
    public void impossibleOnesSaidPlainly() {
        assertTrue(Counting.reply("I have 2 pens and lose 5. How many are left?").startsWith("That can't happen as told: there are only 2 pens"));
    }

    @Test
    public void otherMessagesLeftAlone() {
        assertNull(Counting.reply("How many people live in Lagos?"));
        assertNull(Counting.reply("I have 3 apples."));
        assertNull(Counting.reply("How many days are in a leap year?"));
    }
}
