package ai.ibiko.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** LaTeX maths in a reply shown as readable maths; code and money left alone. */
public class MathTextTest {
    @Test
    public void theScreenshotsMathsReadsPlainly() {
        assertEquals("The equation is quadratic only when m≠3/2.",
                MathText.readable("The equation is quadratic only when \\(m\\neq\\frac32\\)."));
        assertEquals("D=4(m⁴-10m³+31m²-35m+19)", MathText.readable("\\(D=4\\bigl(m^4-10m^3+31m^2-35m+19\\bigr)\\)"));
        assertEquals("m∈(-∞, 3.32)∪(5.19,∞), m≠3/2",
                MathText.readable("\\(m\\in(-\\infty,\\,3.32)\\cup(5.19,\\infty),\\;m\\neq\\frac32\\)"));
        assertEquals("α²/β+β²/α=7", MathText.readable("\\(\\frac{\\alpha^2}{\\beta}+\\frac{\\beta^2}{\\alpha}=7\\)"));
        assertEquals("S=(2(m²-4m+1))/(2m-3)", MathText.readable("\\(\\displaystyle S=\\frac{2(m^2-4m+1)}{2m-3}\\)"));
        assertEquals("√D/|a|>3", MathText.readable("\\(\\sqrt{D}/|a|>3\\)"));
        String boxed = MathText.readable("Final:\n\\[\n\\boxed{\\alpha^2+\\beta^2+\\frac{1}{\\alpha\\beta}=13}\n\\]\nDone.");
        assertEquals("Final:\nα²+β²+1/(αβ)=13\nDone.", boxed);
    }

    @Test
    public void codeAndMoneyAreLeftAlone() {
        String code = "Use `\\(x\\)` or:\n```\nprint(\"\\\\alpha\")\n```";
        assertEquals(code, MathText.readable(code));
        assertEquals("It costs $5 and $10.", MathText.readable("It costs $5 and $10."));
        assertEquals("x₁+x₂", MathText.readable("$x_1+x_2$"));
        assertFalse(MathText.readable("\\(a^{10}\\)").contains("^"));
        assertTrue(MathText.readable("\\(e^{i\\pi}\\)").contains("e^(iπ)") || MathText.readable("\\(e^{i\\pi}\\)").contains("eⁱ"));
    }
}
