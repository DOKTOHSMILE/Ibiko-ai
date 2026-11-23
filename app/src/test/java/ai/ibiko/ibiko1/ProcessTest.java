package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Process;

/** How a thing is made, formed, done or works, read from text and matched to a how-question. */
public class ProcessTest {
    @Test
    public void readsTheWaysThingsAreDone() {
        List<Process.Way> w = Process.read("Glass is made by melting sand at a very high heat.", null);
        assertEquals(1, w.size());
        assertEquals("Glass", w.get(0).thing);
        assertEquals("made", w.get(0).doing);
        assertEquals("by melting sand at a very high heat", w.get(0).means);
        List<Process.Way> b = Process.read("A battery works by turning chemical energy into electricity.", null);
        assertEquals("works", b.get(0).doing);
        List<Process.Way> m = Process.read("Mountains are formed when tectonic plates push against each other.", null);
        assertEquals("formed", m.get(0).doing);
        List<Process.Way> t = Process.read("To make tea, you boil water and pour it over the leaves.", null);
        assertEquals("tea", t.get(0).thing);
        assertEquals("make", t.get(0).doing);
        assertTrue(Process.read("Lagos is a large city.", null).isEmpty());
    }

    @Test
    public void readsStepsInOrder() {
        List<Process.Way> w = Process.read("Bread is made in four steps. First, mix the flour, yeast and water. Then knead the dough. "
                + "Next, let it rise for an hour. Finally, bake it in a hot oven.", "Bread");
        Process.Way steps = null;
        for (Process.Way x : w) {
            if (x.doing.equals("steps")) {
                steps = x;
            }
        }
        assertNotNull(String.valueOf(w), steps);
        assertEquals("Bread", steps.thing);
        assertEquals(4, steps.steps.size());
        assertEquals("Bread is made in four steps:\n1. Mix the flour, yeast and water.\n2. Knead the dough.\n"
                + "3. Let it rise for an hour.\n4. Bake it in a hot oven.", steps.said());
    }

    @Test
    public void aHowQuestionFindsItsWay() {
        List<Process.Way> ways = new ArrayList<Process.Way>();
        ways.addAll(Process.read("Glass is made by melting sand at a very high heat.", null));
        ways.addAll(Process.read("A battery works by turning chemical energy into electricity.", null));
        ways.addAll(Process.read("Mountains are formed when tectonic plates push against each other.", null));
        assertEquals("Glass is made by melting sand at a very high heat.", Process.how("How is glass made?", ways).said());
        assertEquals("A battery works by turning chemical energy into electricity.", Process.how("How does a battery work?", ways).said());
        assertEquals("Mountains are formed when tectonic plates push against each other.", Process.how("How are mountains formed?", ways).said());
        assertNull("made is not how it works", Process.how("How does glass work?", ways));
        assertNull("another thing", Process.how("How is paper made?", ways));
        assertNull("not a how-question", Process.how("What is glass?", ways));
    }
}
