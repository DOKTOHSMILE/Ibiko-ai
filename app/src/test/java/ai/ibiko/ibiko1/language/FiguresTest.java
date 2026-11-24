package ai.ibiko.ibiko1.language;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import ai.ibiko.ibiko1.TextCode;
import ai.ibiko.ibiko1.formulas.Expressions;

/** Expression intelligence: each figure of speech found by its own classifier, and told apart from the others. */
public class FiguresTest {
    private static Figures figures;

    private static InputStream asset(String path) {
        try {
            return new FileInputStream(new File("src/main/assets/" + path));
        } catch (IOException e) {
            return null;
        }
    }

    @BeforeClass
    public static void open() {
        figures = new Figures(TextCode.open(FiguresTest::asset, "knowledge/language/figures.tsv"),
                new Expressions(TextCode.open(FiguresTest::asset, "knowledge/language/expressions.tsv")), null);
    }

    private static Figures.Figure first(String s, String before) {
        List<Figures.Figure> f = figures.find(s, before);
        return f.isEmpty() ? null : f.get(0);
    }

    private static boolean has(String s, String before, Figures.Kind k) {
        for (Figures.Figure f : figures.find(s, before)) {
            if (f.kind == k) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void idiomsAndProverbsAreNotReadWordForWord() {
        Figures.Figure f = first("It's raining cats and dogs out there.", null);
        assertEquals(Figures.Kind.IDIOM, f.kind);
        assertTrue(f.said(), f.meaning.toLowerCase().contains("rain"));
        assertEquals(Figures.Kind.PROVERB, first("Remember, actions speak louder than words.", null).kind);
        assertTrue(has("Let's break the ice with a game.", null, Figures.Kind.IDIOM));
    }

    @Test
    public void similesMetaphorsAndPersonification() {
        assertTrue(has("He is as brave as a lion.", null, Figures.Kind.SIMILE));
        assertTrue(has("She runs like the wind.", null, Figures.Kind.SIMILE));
        assertFalse("a person compared with a person", has("He is as tall as his brother.", null, Figures.Kind.SIMILE));
        assertFalse("liking, not likeness", has("I like a good challenge.", null, Figures.Kind.SIMILE));
        assertTrue(has("My boss is a dragon.", null, Figures.Kind.METAPHOR));
        assertTrue(has("Time is money.", null, Figures.Kind.METAPHOR));
        assertFalse("a lion is an animal: literal", has("The lion is an animal.", null, Figures.Kind.METAPHOR));
        assertTrue(has("The wind whispered through the trees.", null, Figures.Kind.PERSONIFICATION));
        assertFalse(has("The man whispered to his wife.", null, Figures.Kind.PERSONIFICATION));
    }

    @Test
    public void ironyAndSarcasmAreToldApart() {
        assertTrue(has("The fire station burned down last night.", null, Figures.Kind.IRONY));
        assertTrue("praise after something bad", has("Oh great, just what I needed.", "My car broke down on the way.", Figures.Kind.SARCASM));
        assertTrue(has("Oh great, my phone died again.", null, Figures.Kind.SARCASM));
        assertFalse("glad, not sarcastic", has("Oh great, see you then!", "I got the job!", Figures.Kind.SARCASM));
        assertTrue(has("Yeah right, he'll pay you back.", null, Figures.Kind.SARCASM));
        assertFalse(has("The fire station burned down.", null, Figures.Kind.SARCASM));
    }

    @Test
    public void theOtherFigures() {
        assertTrue(has("I've told you a million times.", null, Figures.Kind.HYPERBOLE));
        assertTrue(has("I'm so hungry I'm starving.", null, Figures.Kind.HYPERBOLE));
        assertTrue(has("There was a deafening silence.", null, Figures.Kind.OXYMORON));
        assertEquals("died", firstOf("His grandfather passed away last year.", Figures.Kind.EUPHEMISM).meaning);
        assertTrue(has("Peter Piper picked a peck of pickled peppers.", null, Figures.Kind.ALLITERATION));
        assertFalse(has("The cat sat on the mat.", null, Figures.Kind.ALLITERATION));
        assertTrue(has("The bees buzz and the bacon sizzles.", null, Figures.Kind.ONOMATOPOEIA));
        assertTrue(has("Less is more.", null, Figures.Kind.PARADOX));
        assertTrue(has("A bicycle can't stand on its own because it is two-tired.", null, Figures.Kind.PUN));
        assertTrue(figures.find("The meeting starts at nine in room four.", null).isEmpty());
    }

    private static Figures.Figure firstOf(String s, Figures.Kind k) {
        for (Figures.Figure f : figures.find(s, null)) {
            if (f.kind == k) {
                return f;
            }
        }
        throw new AssertionError("no " + k + " in " + figures.find(s, null));
    }
}
