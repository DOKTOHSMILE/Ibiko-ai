package ai.ibiko.llm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** From the pilot's screenshot: a small model on the device picked actions nobody asked for. They are dropped now. */
public class CodeFitTest {
    private static BrainOrder code(String line) {
        return BrainOrder.leading(line + "\nok");
    }

    @Test
    public void actionsTheWordsDidNotAskForAreDropped() {
        assertFalse("plan mode for a greeting", CodeFit.fits(code("§PM"), "Hello"));
        assertFalse("a share for a question", CodeFit.fits(code("§AC share: public class JavaCode {}"), "Can you write Java codes?"));
        assertFalse("automatic commands for a question", CodeFit.fits(code("§AO"), "How are you?"));
        assertFalse("a picture for a web page", CodeFit.fits(code("§IM a sales page"), "Design for me a test website page for sales"));
        assertFalse("the question heard back is not a yes", CodeFit.fits(code("§YS"), "Say yes or no"));
    }

    @Test
    public void actionsTheWordsAskForStillRun() {
        assertTrue(CodeFit.fits(code("§AC share: hello"), "Share this on WhatsApp"));
        assertTrue(CodeFit.fits(code("§WK"), "Design for me a test website page for sales"));
        assertTrue(CodeFit.fits(code("§IM a cat"), "Draw a cat"));
        assertTrue(CodeFit.fits(code("§PM"), "Turn plan mode on"));
        assertTrue(CodeFit.fits(code("§YS"), "yes"));
        assertTrue("a look-up is always fine", CodeFit.fits(code("§SR weather Lagos"), "Hello"));
        assertTrue(CodeFit.fits(null, "anything"));
        assertTrue(CodeFit.fits(code("§SH git clone https://github.com/a/b"), "clone the private repository a/b"));
        assertTrue(CodeFit.fits(code("§ST focus on the api"), "tell the job to focus on the api"));
        assertFalse(CodeFit.fits(code("§ST focus on the api"), "What is the capital of Nigeria?"));
    }

    @Test
    public void aPoliteRequestIsTheFunctionAndAnExplanationIsNot() {
        assertTrue(CodeFit.fits(code("§SH mkdir notes"), "make a directory called notes"));
        assertTrue(CodeFit.fits(code("§RF"), "read Main.java and tell me what login does"));
        assertTrue(CodeFit.fits(code("§WK"), "add a send button to the chat"));
        assertTrue(CodeFit.fits(code("§PH a red panda"), "show me a photograph of a red panda"));
        assertFalse("a real photo is not a picture to invent", CodeFit.fits(code("§IM a red panda"), "show me a photograph of a red panda"));
        assertFalse("how do I is an explanation", CodeFit.fits(code("§SH git clone x"), "how do I clone a private repo"));
        assertFalse("they said not to", CodeFit.fits(code("§AC post on x: hi"), "don't post this"));
        assertTrue(CodeFit.fits(code("§SH git clone x"), "search for the error and then clone the repo"));
        org.junit.Assert.assertEquals(ai.ibiko.suit.FunctionCode.SH, ai.ibiko.suit.Comprehension.of("search for the error and then clone the repo").also);
    }
}
