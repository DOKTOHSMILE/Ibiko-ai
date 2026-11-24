package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Reshape;

/** The last answer said another way: what's asked, repeated sentences dropped, and each way of saying it. */
public class ReshapeTest {
    private static Reshape reshape() {
        return new Reshape(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/language/plain_words.tsv"));
    }

    private static final String ANSWER = "The heart is a muscle that pumps blood through the body. It pumps blood through the body using regular"
            + " beats. Blood carries oxygen to the organs (the brain, the kidneys and the liver); it also removes waste. Approximately 5 litres"
            + " of blood circulate in an adult.";

    @Test
    public void whatsAskedOfTheLastAnswer() {
        assertEquals(Reshape.Ask.SHORTER, Reshape.asked("Shorter please"));
        assertEquals(Reshape.Ask.SHORTER, Reshape.asked("tl;dr"));
        assertEquals(Reshape.Ask.ONE, Reshape.asked("Can you say it in one sentence?"));
        assertEquals(Reshape.Ask.POINTS, Reshape.asked("as bullet points"));
        assertEquals(Reshape.Ask.POINTS, Reshape.asked("List it out"));
        assertEquals(Reshape.Ask.SIMPLER, Reshape.asked("Explain it like I'm five"));
        assertEquals(Reshape.Ask.SIMPLER, Reshape.asked("I don't understand"));
        assertEquals(Reshape.Ask.EXAMPLE, Reshape.asked("Give me an example"));
        assertEquals(Reshape.Ask.EXAMPLE, Reshape.asked("like what?"));
        assertNull("a question of its own", Reshape.asked("Who is shorter, Ada or Obi?"));
        assertNull(Reshape.asked("Give me an example of a mammal"));
        assertNull(Reshape.asked("Make a list of countries in Africa"));
    }

    @Test
    public void saidAgainEachWay() {
        Reshape r = reshape();
        assertEquals("what it said twice is said once", 3, Reshape.sentences(ANSWER).size());
        assertEquals("The heart is a muscle that pumps blood through the body.", r.again(ANSWER, Reshape.Ask.SHORTER));
        String points = r.again(ANSWER, Reshape.Ask.POINTS);
        assertEquals("• The heart is a muscle that pumps blood through the body.\n• Blood carries oxygen to the organs (the brain, the kidneys and the liver); it also removes waste.\n"
                + "• Approximately 5 litres of blood circulate in an adult.", points);
        String simpler = r.again(ANSWER, Reshape.Ask.SIMPLER);
        assertEquals("The heart is a muscle that pumps blood through the body. Blood carries oxygen to the organs. It also removes waste.", simpler);
        assertEquals("plain words", "About 5 litres of blood circulate in an adult.", r.again("Approximately 5 litres of blood circulate in an adult.", Reshape.Ask.SIMPLER));
        assertNull("code isn't reshaped", r.again("Here it is:\n```python\nprint(1)\n```", Reshape.Ask.SHORTER));
        String one = r.again(ANSWER, Reshape.Ask.ONE);
        assertTrue(one, one.equals("The heart is a muscle that pumps blood through the body.") || one.startsWith("Blood carries oxygen"));
    }

    @Test
    public void anExampleFromWhatItRead() {
        String text = "A mammal is an animal that feeds its young on milk. Most mammals give birth to live young. Some, such as the platypus"
                + " and the echidna, lay eggs instead. Mammals have hair or fur.";
        assertEquals("Some, such as the platypus and the echidna, lay eggs instead.", Reshape.example(text, "A mammal is an animal that feeds its young on milk."));
        assertNull("not one already said", Reshape.example(text, "Some, such as the platypus and the echidna, lay eggs instead."));
        assertNull(Reshape.example("Mammals have hair.", null));
    }
}
