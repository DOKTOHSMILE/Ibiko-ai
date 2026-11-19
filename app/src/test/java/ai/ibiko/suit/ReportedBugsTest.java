package ai.ibiko.suit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import ai.ibiko.llm.BrainOrder;
import org.junit.Test;

/** The bugs seen on the phone: each kept from coming back. */
public class ReportedBugsTest {
    @Test
    public void aCodeAtTheEndOfASentenceIsRunAndNotShown() {
        String reply = "Let me find the zip file on your device. §FZ";
        FunctionCode.Call c = FunctionCode.parse(reply);
        assertNotNull(c);
        assertEquals(FunctionCode.FZ, c.code);
        assertEquals("Let me find the zip file on your device.", FunctionCode.hidden(reply).trim());
        assertEquals(FunctionCode.SR, FunctionCode.parse("Looking it up. §SR weather in Lagos").code);
        assertNull("a section sign in prose is not a code", FunctionCode.parse("See § 12 of the contract."));
        assertNull("nor a word that starts with two capitals", FunctionCode.parse("Read it. §SRC is a file"));
    }

    @Test
    public void aClaimMadeBeforeTheActionRanIsDropped() {
        assertEquals("", BrainOrder.noClaim("Thread posted to your X account.", BrainOrder.Kind.SOCIAL));
        assertEquals("I'll post it now.", BrainOrder.noClaim("I'll post it now.", BrainOrder.Kind.SOCIAL));
        assertEquals("Here is the thread. Ready when you are?",
                BrainOrder.noClaim("Here is the thread. I've posted it. Ready when you are?", BrainOrder.Kind.STAGE));
        assertEquals("a plain answer is left alone", "It was posted yesterday.",
                BrainOrder.noClaim("It was posted yesterday.", BrainOrder.Kind.SAY));
    }

    @Test
    public void anAuditIsWorkAndAStallIsSeen() {
        assertEquals(FunctionCode.WK, Comprehension.of("Audit the entire project codebase").want);
        assertEquals(FunctionCode.WK, Comprehension.of("Start inspecting the files for broken functions read line by line").want);
        assertTrue(BrainOrder.stalls("I'm sure I haven't read through any files in your My-Store-Manager repository this session."
                + " If you want, just tell me to go ahead."));
        assertTrue(BrainOrder.stalls("Please ask me to read the project files in the workspace now."));
        assertFalse(BrainOrder.stalls("The build failed on line 12 because the import is missing."));
    }
}
