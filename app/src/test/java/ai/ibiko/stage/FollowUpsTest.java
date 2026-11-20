package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** The cards under a reply that waits for an answer, and none under one that doesn't. */
public class FollowUpsTest {
    @Test
    public void choicesOfferedBecomeCards() {
        assertEquals(Arrays.asList("A shorter 120-word test", "Help debugging my wrapper prompt"),
                FollowUps.of("Here's what went wrong. Would you like a shorter 120-word test or help debugging my wrapper prompt?"));
        assertEquals(Arrays.asList("Generate the image", "Keep the description", "Simplify it"),
                FollowUps.of("Done. Do you want me to generate the image, keep the description, or simplify it?"));
    }

    @Test
    public void optionsListedUnderAQuestionBecomeCards() {
        assertEquals(Arrays.asList("Give me a shorter test", "Help debug my AI wrapper"),
                FollowUps.of("That's the diagnosis.\n\nHow would you like to proceed?\n- Give me a shorter test\n- Help debug my AI wrapper"));
    }

    @Test
    public void aYesOrNoOfferGetsYesAndNo() {
        assertEquals(Arrays.asList("Yes, please", "No, thanks"), FollowUps.of("I can save this as a file. Shall I save it?"));
        assertEquals(Arrays.asList("Yes, that's right", "No, not quite"), FollowUps.of("You were born in 1990. Is that right?"));
    }

    @Test
    public void aReplyThatDoesNotWaitGetsNone() {
        assertTrue(FollowUps.of("Abuja is the capital of Nigeria.").isEmpty());
        assertTrue(FollowUps.of("What's your name?").isEmpty()); // only the person's own words answer it
        assertTrue(FollowUps.of("Here's the code:\n```\nint x = 1;\n```").isEmpty());
    }
}
