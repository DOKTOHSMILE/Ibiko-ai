package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Quick;
import ai.ibiko.ibiko1.formulas.Spelling;

/** Everyday sums in words, worked out with the working shown. */
public class QuickTest {
    @Test
    public void partsAndDiscounts() {
        assertEquals("Half of 250 is 125 (250 ÷ 2).", Quick.reply("What is half of 250?"));
        assertEquals("Double of 45 is 90 (45 × 2).", Quick.reply("double 45"));
        assertEquals("4,000: 20% of 5,000 is 1,000, and 5,000 − 1,000 = 4,000.", Quick.reply("What is 20% off 5000?"));
        assertEquals("₦8,500: 15% of ₦10,000 is ₦1,500, and ₦10,000 − ₦1,500 = ₦8,500.", Quick.reply("15% off ₦10000"));
    }

    @Test
    public void primes() {
        assertTrue(Quick.reply("Is 97 a prime number?").startsWith("Yes: 97 is prime."));
        assertEquals("No: 91 = 7 × 13, so it can be divided by 7.", Quick.reply("is 91 prime"));
        assertTrue(Quick.reply("Is 1 prime?").startsWith("No:"));
    }

    @Test
    public void daysAndPrices() {
        assertEquals("Thursday. 10 days is 1 week and 3 days, so it's 3 days after Monday.", Quick.reply("What day is 10 days after Monday?"));
        assertEquals("Saturday. 2 days is 0 weeks and 2 days, so it's 2 days before Monday.", Quick.reply("what day was it 2 days before monday?".replace("was it", "is")));
        assertEquals("7,500 naira: 3 × 2,500 naira = 7,500 naira.", Quick.reply("A shirt costs 2500 naira. How much do 3 shirts cost?"));
        assertNull(Quick.reply("What is half of the world?"));
    }

    @Test
    public void aForecastOffline() {
        assertTrue(Spelling.weather("What will the weather be tomorrow?").startsWith("I can't know that without a forecast"));
        assertTrue(Spelling.weather("Will it rain tomorrow?") != null);
        assertNull(Spelling.weather("What is weather?"));
    }
}
