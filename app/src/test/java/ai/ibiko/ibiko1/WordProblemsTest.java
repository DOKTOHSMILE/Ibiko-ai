package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Maths;
import ai.ibiko.ibiko1.formulas.WordProblems;
import org.junit.Test;

/** Real-world problems turned into formulas and worked out, and handed to models with the question. */
public class WordProblemsTest {
    private static String r(String q) {
        String s = WordProblems.reply(q);
        System.out.println("Q: " + q + "\nA: " + s + "\n");
        return s;
    }

    @Test
    public void money() {
        String s = r("I buy gas at ₦8,000 a cylinder and sell at ₦9,500. Rent is ₦45,000 a month. How many must I sell to break even?");
        assertTrue(s, s.contains("₦1,500") && s.contains("sell 30") && s.contains("₦45,000"));
        s = r("I buy each for 8000 and sell for 9500, rent 45000 and salary 30000. How many do I need to sell to make a profit of 75000?");
        assertTrue(s, s.contains("sell 100"));
        s = r("What is the simple interest on ₦200,000 at 15% per annum for 3 years?");
        assertTrue(s, s.contains("₦90,000") && s.contains("₦290,000"));
        s = r("₦1,000,000 invested at 10% compound interest for 2 years");
        assertTrue(s, s.contains("₦1,210,000"));
        s = r("I took a loan of $10,000 at 12% a year for 1 year. What is the monthly repayment?");
        assertTrue(s, s.contains("$888.49"));
        s = r("If I buy a phone at 100,000 and sell it at 125,000, what is my profit margin?");
        assertTrue(s, s.contains("20%") && s.contains("25%"));
    }

    @Test
    public void ratesTravelPercent() {
        String s = r("A car worth ₦5,000,000 depreciates at 20% a year. What is it worth after 3 years?");
        assertTrue(s, s.contains("₦2,560,000"));
        s = r("How long does money take to double at 8% per year?");
        assertTrue(s, s.contains("9.01") && s.contains("10 whole years"));
        s = r("A bus travels 240 km in 3 hours. What is its speed?");
        assertTrue(s, s.contains("80 km/h"));
        s = r("How long does it take to drive 150 km at 60 km/h?");
        assertTrue(s, s.contains("2 h 30 min"));
        s = r("The price went from ₦4,000 to ₦5,000. What percentage increase is that?");
        assertTrue(s, s.contains("25%"));
        s = r("Increase ₦12,000 by 15%");
        assertTrue(s, s.contains("₦13,800"));
    }

    @Test
    public void modelsGetTheExactWorking() {
        String q = "Should I expand? I buy gas at 8000 and sell at 9500, rent is 45000. How many to break even, and is it realistic?";
        String sent = Maths.forModel(q);
        assertTrue(sent, sent.startsWith(q) && sent.contains("Worked out exactly by Ibiko's maths engine") && sent.contains("sell 30"));
        assertEquals("Tell me about Lagos.", Maths.forModel("Tell me about Lagos."));
        assertTrue(Maths.answer("integrate x^2 from 0 to 3").contains("= 9."));
        assertTrue(Maths.answer("solve 2x + 3 = 11").contains("x = 4"));
        assertNull(WordProblems.reply("I love driving to Lagos with my family"));
    }
}
