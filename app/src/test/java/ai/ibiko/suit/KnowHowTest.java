package ai.ibiko.suit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.ibiko1.Facts;
import ai.ibiko.ibiko1.Knowledge;
import ai.ibiko.ibiko1.Methods;
import ai.ibiko.ibiko1.formulas.Shapes;
import ai.ibiko.ibiko1.formulas.TruthLedger;
import org.junit.Test;

/** The phone's own model is told how to run every function the chat allows, and Ibiko 1's stores are larger. */
public class KnowHowTest {
    @Test
    public void everyAllowedCodeHasItsHowToAndATurnedOffCodeDoesNot() {
        ChatTools t = ChatTools.defaults("ibiko");
        String how = FunctionCode.knowHow("", t.allowedCodes());
        String lean = FunctionCode.lean("", t.allowedCodes());
        assertTrue(how.contains("first line"));
        assertTrue(how.contains("wait for the user's yes"));
        for (FunctionCode c : t.allowedCodes()) {
            if (c == FunctionCode.TL) {
                assertFalse(how.contains(c.code()));
                continue;
            }
            assertTrue(c.code(), how.contains(c.code()));
            assertTrue(c.code(), how.contains("Write " + c.code()));
        }
        assertTrue(how.contains("Write \u00A7SR weather in Lagos"));
        assertFalse("the short list stays short", lean.contains("weather in Lagos"));
        String withTool = FunctionCode.knowHow("\n- github.repos <owner>: list", t.allowedCodes());
        assertTrue(withTool.contains(FunctionCode.TL.code()));
        assertTrue(withTool.contains("github.repos"));
        String one = FunctionCode.knowHow("", java.util.EnumSet.of(FunctionCode.SR));
        assertTrue(one.contains(FunctionCode.SR.code()));
        assertFalse(one.contains(FunctionCode.SH.code()));
        assertTrue(how.length() + " against " + lean.length(), how.length() > lean.length());
    }

    @Test
    public void theStoresHoldFourTimesAsMuch() {
        org.junit.Assert.assertEquals(20000, Knowledge.MOST);
        org.junit.Assert.assertEquals(80000, Facts.MOST);
        org.junit.Assert.assertEquals(1600, Methods.MOST);
        org.junit.Assert.assertEquals(12000, TruthLedger.MOST);
        org.junit.Assert.assertEquals(800, Shapes.MOST);
        org.junit.Assert.assertEquals(16000, Shapes.CHARS);
    }
}
