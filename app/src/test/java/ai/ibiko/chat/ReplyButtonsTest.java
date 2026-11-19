package ai.ibiko.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

/** A reply's buttons: taken out of the words, a few short choices, never half-written on screen. */
public class ReplyButtonsTest {
    @Test
    public void theButtonsLineBecomesButtons() {
        ReplyButtons.Split s = ReplyButtons.split("Your campaign is ready.\n\n[buttons: Start it | Show the posts | Not now]");
        assertEquals("Your campaign is ready.", s.text);
        assertEquals(Arrays.asList("Start it", "Show the posts", "Not now"), s.buttons);
    }

    @Test
    public void atMostFourShortChoicesOnce() {
        ReplyButtons.Split s = ReplyButtons.split("Pick one.\n[buttons: A | A | B | C | D | E | "
                + "this choice is far too long to be a button on a phone screen]");
        assertEquals(Arrays.asList("A", "B", "C", "D"), s.buttons);
    }

    @Test
    public void aReplyWithoutButtonsIsLeftAlone() {
        String plain = "Here is a list: [1] first, [2] second.";
        ReplyButtons.Split s = ReplyButtons.split(plain);
        assertEquals(plain, s.text);
        assertTrue(s.buttons.isEmpty());
    }

    @Test
    public void aButtonsLineBeingWrittenIsNotShown() {
        assertEquals("Ready to go.", ReplyButtons.writing("Ready to go.\n[buttons: Sta"));
        assertEquals("Ready to go.", ReplyButtons.writing("Ready to go."));
    }

    @Test
    public void theLayoutKeepsTheButtonsLine() {
        String laid = ai.ibiko.llm.ReplyLayout.join("Done.\n\n[buttons: Yes | No]");
        assertEquals(Arrays.asList("Yes", "No"), ReplyButtons.split(laid).buttons);
    }
}
