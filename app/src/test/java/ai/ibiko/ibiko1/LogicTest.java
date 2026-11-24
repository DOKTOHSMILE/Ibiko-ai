package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Logic;

/** Reasoning from what was said: kinds, what things have, if-thens; what doesn't follow said not to. */
public class LogicTest {
    @Test
    public void kinds() {
        assertEquals("Yes: Tom is a cat, and all cats are mammals, so Tom is a mammal.",
                Logic.answer("All cats are mammals. Tom is a cat. Is Tom a mammal?", null));
        assertEquals("Yes: Tom is a cat, and all cats are mammals, and all mammals are animals, so Tom is an animal.",
                Logic.answer("All cats are mammals. All mammals are animals. Tom is a cat. Is Tom an animal?", null));
        assertEquals("No: Nemo is a fish, and no fish are mammals.", Logic.answer("No fish are mammals. Nemo is a fish. Is Nemo a mammal?", null).replace("fishes", "fish"));
        assertEquals("I can't tell from what you said: nothing you told me makes Tom a dog, or rules it out.",
                Logic.answer("All cats are mammals. Tom is a cat. Is Tom a dog?", null));
    }

    @Test
    public void whatThingsHave() {
        assertEquals("No: all birds have feathers, and the bat has none, so it can't be a bird.",
                Logic.answer("All birds have feathers. A bat has no feathers. Is a bat a bird?", null));
        assertEquals("Yes: Polly is a bird, and all birds have feathers.", Logic.answer("All birds have feathers. Polly is a bird. Does Polly have feathers?", null));
    }

    @Test
    public void ifThen() {
        assertEquals("Yes: it rained, and if it rains, the ground gets wet.",
                Logic.answer("If it rains, the ground gets wet. It rained. Is the ground wet?", null));
        assertEquals("Not necessarily: if it rains, the ground gets wet, but it doesn't work the other way round. “The ground is wet” could have another cause, so it doesn't prove that it rains.",
                Logic.answer("If it rains, the ground gets wet. The ground is wet. Did it rain?", null));
        assertEquals("Only if it rains: you haven't said that it did.", Logic.answer("If it rains, the ground gets wet. Is the ground wet?", null));
    }

    @Test
    public void questionsOfFactLeftAlone() {
        assertNull(Logic.answer("Is a whale a mammal?", null));
        assertNull(Logic.answer("Is Lagos in Nigeria?", null));
    }

    @Test
    public void keptForLaterQuestions() {
        Logic.Known said = Logic.read("All cats are mammals. Tom is a cat.");
        assertEquals("Yes: Tom is a cat, and all cats are mammals, so Tom is a mammal.", Logic.answer("Is Tom a mammal?", said));
    }
}
