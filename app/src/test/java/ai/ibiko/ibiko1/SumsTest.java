package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A sum taken as one only when the message is about it; roots worked out exactly. */
public class SumsTest {
    @Test
    public void onlyWhenTheMessageIsAboutTheSum() {
        assertTrue(Ibiko1.aboutTheSum("What is 248 + 917?"));
        assertTrue(Ibiko1.aboutTheSum("calculate 37 times 43 please"));
        assertTrue(Ibiko1.aboutTheSum("What is 15% of 2400?"));
        assertTrue(Ibiko1.aboutTheSum("2+2"));
        assertFalse(Ibiko1.aboutTheSum("Add a question: What is 2+2? 3, 4, 5. Answer: 4"));
        assertFalse(Ibiko1.aboutTheSum("I had 2000 naira and spent 750. How much do I have left?"));
        assertFalse(Ibiko1.aboutTheSum("Is Lagos older than Abuja?"));
    }

    @Test
    public void roots() {
        assertEquals("√169 = 13", Ibiko1.root("What is the square root of 169?"));
        assertEquals("∛27 = 3", Ibiko1.root("cube root of 27"));
        assertEquals("√2 = 1.414214 (to six places)", Ibiko1.root("square root of 2"));
        assertNull(Ibiko1.root("What is a root vegetable?"));
    }
}
