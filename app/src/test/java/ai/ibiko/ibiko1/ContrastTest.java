package ai.ibiko.ibiko1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Contrast;

/** Two things told apart from what is read about each: their kinds, and what sets each apart. */
public class ContrastTest {
    private static final String FROG = "A frog is an amphibian with smooth, moist skin and long back legs. Frogs live near ponds and "
            + "lay their eggs in water. Most frogs can jump very far.";
    private static final String TOAD = "A toad is an amphibian with dry, bumpy skin and short legs. Toads often live in gardens, "
            + "far from water. They walk or hop in short steps.";
    private static final String LIZARD = "A lizard is a reptile with scaly skin. Lizards are cold-blooded and lay eggs on land.";

    @Test
    public void readsTheTwoThingsAsked() {
        assertArrayEquals(new String[] {"frog", "toad"}, Contrast.pair("What is the difference between a frog and a toad?"));
        assertArrayEquals(new String[] {"frog", "toad"}, Contrast.pair("difference between frog and toad"));
        assertArrayEquals(new String[] {"alligator", "crocodile"}, Contrast.pair("Alligator vs crocodile"));
        assertArrayEquals(new String[] {"Lagos", "Abuja"}, Contrast.pair("Compare Lagos and Abuja"));
        assertArrayEquals(new String[] {"weather", "climate"}, Contrast.pair("How is weather different from climate?"));
        assertArrayEquals(new String[] {"moth", "butterfly"}, Contrast.pair("Moth or butterfly: what's the difference?"));
        assertNull(Contrast.pair("What is a frog?"));
        assertNull(Contrast.pair("Who won the 2022 World Cup?"));
    }

    @Test
    public void findsWhatKindOfThingItIs() {
        Contrast.Kind k = Contrast.kind(FROG);
        assertEquals("an amphibian", k.said);
        assertEquals("amphibian", k.head);
        assertEquals("with smooth, moist skin and long back legs", k.rest);
        assertNull(Contrast.kind("Frogs jump."));
        Contrast.Kind g = Contrast.kind("Alligator is a genus of crocodilians in the family Alligatoridae.");
        assertEquals("crocodilian", g.head);
        Contrast.Kind o = Contrast.kind("An ocean is a large body of water that covers much of the Earth.");
        assertEquals("body of water", o.head);
        assertEquals("insects", Contrast.kind("Moths are insects of the order Lepidoptera.").said);
        assertNull(Contrast.kind("Butterflies are grouped together in the suborder Rhopalocera."));
        assertNull("what a word means is not what a thing is", Contrast.kind("Python is the name given to many things."));
    }

    @Test
    public void theSameKindThenWhatSetsEachApart() {
        String told = Contrast.tell("frog", FROG, "toad", TOAD);
        assertTrue(told, told.startsWith("Both are amphibians, but they differ."));
        assertTrue(told, told.contains("• Frog: An amphibian with smooth, moist skin and long back legs."));
        assertTrue(told, told.contains("• Toad: An amphibian with dry, bumpy skin and short legs."));
        assertTrue("a detail the other lacks: " + told, told.contains("jump") || told.contains("ponds"));
    }

    @Test
    public void containerWordsAreLookedThrough() {
        String told = Contrast.tell("ocean", "An ocean is a large body of water that covers much of the Earth. There are five oceans.",
                "sea", "A sea is a body of water that is smaller than an ocean. Seas are often partly closed in by land.");
        assertTrue(told, told.startsWith("Both are bodies of water, but they differ."));
    }

    @Test
    public void differentKindsAreSaid() {
        String told = Contrast.tell("frog", FROG, "lizard", LIZARD);
        assertTrue(told, told.startsWith("Frog is an amphibian; lizard is a reptile."));
        assertNull(Contrast.tell("frog", FROG, "nothing", "Something happened once."));
    }
}
