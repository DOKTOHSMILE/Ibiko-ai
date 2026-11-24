package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Knowledge one part of the app holds, used by the others: core facts, place comparisons, opposites through meaning. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ConnectedKnowledgeTest {
    @Before
    public void assets() {
        Plug.asset = p -> {
            try {
                return new FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
    }

    @Test
    public void theAppsCoreFactsAnswer() {
        CoreFacts c = Ibiko1.coreFacts();
        assertTrue(c.size() > 100);
        assertEquals("Water boils at 100 degrees Celsius at sea level.", c.answer("At what temperature does water boil?"));
        assertEquals("Water boils at 212 degrees Fahrenheit at sea level.", c.answer("What is the boiling point of water in Fahrenheit?"));
        assertNotNull(c.answer("What is DNA?"));
        assertNull("a one-key fact only for the thing alone", c.answer("Write Java code to sort a list"));
        assertNull(c.answer("Book a DNA test near me"));
    }

    @Test
    public void namedPlacesAreCompared() {
        Reasoner.Result r = new Reasoner(Ibiko1.builtIn()).answer("Which has more people, Lagos or Kano?");
        assertNotNull(r);
        assertTrue(r.text, r.text.startsWith("Lagos has more people than Kano"));
    }

    @Test
    public void codeForEvenNumbers() {
        ai.ibiko.ibiko1.formulas.Recipes.Ask a = ai.ibiko.ibiko1.formulas.Recipes.read("Write a function to check if a number is even in Python");
        assertNotNull(a);
        assertEquals("is_even", a.task);
        assertEquals("python", a.lang);
        ai.ibiko.ibiko1.formulas.Recipes.Ask b = ai.ibiko.ibiko1.formulas.Recipes.read("Write code to sort a list even though it is long in Go");
        assertTrue(b == null || !"is_even".equals(b.task));
    }
}
