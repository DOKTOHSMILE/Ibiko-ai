package ai.ibiko.ibiko1;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Algebra;
import org.junit.Test;

/** Ibiko v1's algebra: exact answers with the working, offline. */
public class AlgebraTest {
    private static String r(String q) {
        String s = Algebra.reply(q);
        System.out.println("Q: " + q + "\nA: " + s + "\n");
        return s;
    }

    @Test
    public void linearAndQuadratic() {
        String s = r("Solve 2x + 3 = 11");
        assertTrue(s, s.endsWith("x = 4.\nCheck: putting x = 4 back in gives 0. ✓") || s.contains("So x = 4."));
        s = r("solve x^2 - 5x + 6 = 0");
        assertTrue(s, s.contains("x = 2, or x = 3"));
        s = r("Solve 2x² − 5x − 3 = 0");
        assertTrue(s, s.contains("x = −1/2, or x = 3") && s.contains("Discriminant"));
        s = r("solve 2x^2 - 5x + 1 = 0");
        assertTrue(s, s.contains("(5 − √17)/4") && s.contains("(5 + √17)/4"));
        s = r("solve x^2 + 2x + 5 = 0");
        assertTrue(s, s.contains("negative") && s.contains("x = −1 ± 2i"));
        s = r("solve 3(x - 1) = 2(x + 4)");
        assertTrue(s, s.contains("x = 11"));
    }

    @Test
    public void cubicsInequalitiesSystems() {
        String s = r("solve x^3 - 6x^2 + 11x - 6 = 0");
        assertTrue(s, s.contains("x = 1") && s.contains("x = 2") && s.contains("x = 3"));
        s = r("solve x^3 - 2 = 0");
        assertTrue(s, s.contains("1.2599"));
        s = r("solve x^2 - 5x + 6 > 0");
        assertTrue(s, s.contains("(−∞, 2) ∪ (3, ∞)"));
        s = r("solve x^2 - 4 <= 0");
        assertTrue(s, s.contains("[−2, 2]"));
        s = r("Solve x + y = 10 and x - y = 2");
        assertTrue(s, s.contains("x = 6, y = 4"));
        s = r("solve 2a + b - c = 3, a - b + 2c = 7, 3a + 2b + c = 10");
        assertTrue(s, s != null && s.contains("a = ") && s.contains("c = "));
        s = r("factorise x^2 - 5x + 6");
        assertTrue(s, s.contains("(x − 2)(x − 3)"));
        s = r("expand (x + 2)(x - 3)");
        assertTrue(s, s.contains("x² − x − 6"));
    }

    /** The screenshot's problem: the discriminant as a polynomial in m, then equal and distinct real roots. */
    @Test
    public void aQuadraticWithAParameter() {
        String q = "Given the quadratic equation (2m-3)x^2-2(m^2-4m+1)x+(m^2-5m+6)=0 where m is a real parameter, find the values of m";
        String s = r(q + " for which the roots are equal.");
        assertTrue(s, s.contains("m⁴ − 40m³") || s.contains("4m⁴"));
        assertTrue(s, s.contains("3.3233") && s.contains("5.1916"));
        s = r(q + " for which the equation has two distinct real roots.");
        assertTrue(s, s.contains("(−∞, 3.3233)") && s.contains("(5.1916, ∞)") && s.contains("3/2"));
    }

    @Test
    public void notAlgebraIsLeftAlone() {
        assertNull(Algebra.reply("What is 12 times 7?"));
        assertNull(Algebra.reply("Who founded Microsoft?"));
        assertNull(Algebra.reply("I paid 500 = a lot of money for this"));
        assertNull(Algebra.reply("Find the capital of Nigeria"));
    }

    /** The screenshot's whole problem, pasted as it was (LaTeX and all): every part worked out. */
    @Test
    public void aProblemInParts() {
        String q = "Advanced Mathematics Challenge: Complex Quadratic Equations\n\nQuestion:\n\nGiven the quadratic equation\n\n\\[\n"
                + "(2m-3)x^2-2(m^2-4m+1)x+(m^2-5m+6)=0\n\\]\n\nwhere \\(m\\) is a real parameter and \\(x\\) is the unknown variable.\n\n"
                + "Answer all parts:\n\n(a) Determine all real values of \\(m\\) for which the equation has two distinct real roots.\n\n"
                + "(b) Find all values of \\(m\\) for which the roots are equal.\n\n"
                + "(c) If the roots are \\(\\alpha\\) and \\(\\beta\\), determine all values of \\(m\\) satisfying:\n\n\\[\n"
                + "\\frac{\\alpha^2}{\\beta}+\\frac{\\beta^2}{\\alpha}=7\n\\]\n\nprovided that \\(\\alpha\\beta\\neq 0\\).\n\n"
                + "(d) Without solving the quadratic equation directly, find the values of \\(m\\) for which:\n\n\\[\n\\alpha^3+\\beta^3=19\n\\]\n\n"
                + "(e) Determine the values of \\(m\\) for which both roots are negative.\n\n"
                + "(f) Find the values of \\(m\\) for which:\n\n\\[\n\\frac{1}{\\alpha^2}+\\frac{1}{\\beta^2}=5\n\\]\n\n"
                + "(g) Find the range of values of \\(m\\) for which the equation has two positive roots, and the difference between the roots is greater than 3.\n\n"
                + "Final Challenge\n\nFind all possible values of \\(m\\) such that the equation has two distinct real roots satisfying:\n\n\\[\n"
                + "\\boxed{\\alpha^2+\\beta^2+\\frac{1}{\\alpha\\beta}=13}\n\\]\n\nInstruction: Show all workings.";
        String s = Algebra.parts(q);
        System.out.println(s);
        assertTrue(s, s != null && s.contains("(a)") && s.contains("(5.1916, ∞)"));
        assertTrue(s, s.contains("3.3233") && s.contains("5.1916"));
        assertTrue(s, s.contains("(c)") && s.contains("5.7768") && s.contains("0.5151"));
        assertTrue(s, s.contains("(d)") && s.contains("0.7615") && s.contains("6.2882"));
        assertTrue(s, s.contains("(e)") && s.contains("(3/2, 2)") && s.contains("(3, 3.3233)"));
        assertTrue(s, s.contains("(f)") && s.contains("= 1"));
        assertTrue(s, s.contains("(g)") && s.contains("(7.04"));
        assertTrue(s, s.contains("Final challenge") && s.contains("1.3447") && s.contains("0.914") && s.contains("3.2268") && s.contains("7.0113"));
    }

    /** Maths in words, inside a sentence, with "x3" read both ways. */
    @Test
    public void mathsInWords() {
        String s = r("what is this 2 plus x3 equals 9\nFind x");
        assertTrue(s, s.contains("two ways") && s.contains("Solve 2 + 3x = 9") && s.contains("x = 7/3"));
        assertTrue(s, s.contains("\u221b7") && s.contains("1.9129"));
        s = r("Find x if 5 times x minus 4 equals 11");
        assertTrue(s, s.contains("x = 3"));
        s = r("x squared minus 9 equals 0, what is x?");
        assertTrue(s, s.contains("x = \u22123, or x = 3"));
        assertNull(Algebra.reply("My son is 2 years older and the roots are equal in this plant"));
    }
}
