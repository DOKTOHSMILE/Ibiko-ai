package ai.ibiko.ibiko1;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.formulas.Calculus;
import org.junit.Test;

/** Ibiko v1's calculus on polynomials: exact, with the working. */
public class CalculusTest {
    private static String r(String q) {
        String s = Calculus.reply(q);
        System.out.println("Q: " + q + "\nA: " + s + "\n");
        return s;
    }

    @Test
    public void derivatives() {
        String s = r("Differentiate 3x^4 - 2x^2 + 7");
        assertTrue(s, s.contains("d/dx = 12x³ − 4x"));
        s = r("find dy/dx if y = (x+1)(x-2)");
        assertTrue(s, s.contains("2x − 1"));
        s = r("what is the second derivative of x^5 - 3x^3");
        assertTrue(s, s.contains("20x³ − 18x"));
        s = r("derivative of t^3 with respect to t");
        assertTrue(s, s.contains("3t²"));
    }

    @Test
    public void integrals() {
        String s = r("integrate 3x^2 + 2x");
        assertTrue(s, s.contains("x³ + x² + C"));
        s = r("integrate x^2 from 0 to 3");
        assertTrue(s, s.contains("= 9."));
        s = r("integral of x from 1 to 2");
        assertTrue(s, s.contains("= 3/2 ≈ 1.5"));
        s = r("find the area under y = x^2 - 4 from 0 to 2");
        assertTrue(s, s.contains("area is 16/3") && s.contains("below the axis"));
        s = r("area under y = x^3 from -1 to 1");
        assertTrue(s, s.contains("≈ 0.5") && s.contains("cancel"));
    }

    @Test
    public void stationaryPointsAndTangents() {
        String s = r("find the stationary points of y = x^3 - 3x");
        assertTrue(s, s.contains("(−1, 2)") && s.contains("maximum") && s.contains("(1, −2)") && s.contains("minimum"));
        s = r("find the tangent to y = x^2 - 3x at x = 2");
        assertTrue(s, s.contains("y = x − 4"));
        s = r("turning points of y = x^2 + 1");
        assertTrue(s, s.contains("(0, 1)") && s.contains("minimum"));
    }

    @Test
    public void notCalculus() {
        assertNull(Calculus.reply("What is the capital of Nigeria?"));
        assertNull(Calculus.reply("I want to integrate my business with WhatsApp"));
        assertNull(Calculus.reply("Solve x + 2 = 5"));
    }
}
