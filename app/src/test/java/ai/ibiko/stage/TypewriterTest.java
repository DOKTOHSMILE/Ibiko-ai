package ai.ibiko.stage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The live reply: no typewriter. What has arrived is on screen at once; a long report is shown in pieces. */
public class TypewriterTest {
    @Test
    public void thereIsNoTypingDelay() {
        assertTrue("redrawn at least 20 times a second", 1000 / ChatPane.LIVE_REDRAW_MS >= 20);
        assertEquals("a short reply is shown whole", "The capital of France is Paris.",
                ChatPane.liveTail("The capital of France is Paris."));
    }

    @Test
    public void aLongReportIsShownInPiecesNotOneHugeBlock() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            b.append("Finding ").append(i).append(": a paragraph about a problem in the code and how to fix it.\n\n");
        }
        String report = b.toString().trim();
        java.util.List<String> parts = ChatPane.chunks(report, ChatPane.CHUNK);
        assertTrue(parts.size() > 5);
        for (String p : parts) {
            assertTrue(p.length() <= ChatPane.CHUNK);
        }
        assertEquals(report, String.join("\n\n", parts)); // nothing lost or reordered
        // while it is written, only its end is drawn
        String tail = ChatPane.liveTail(report);
        assertTrue(tail.length() < ChatPane.LIVE_TAIL + 200);
        assertTrue(tail.startsWith("\u2026 ("));
        assertTrue(report.endsWith(tail.substring(tail.indexOf("\n\n") + 2)));
    }

    @Test
    public void stepTimesReadAsHoursMinutesAndSeconds() {
        assertEquals("45s", ChatPane.duration(45));
        assertEquals("3m 05s", ChatPane.duration(185));
        assertEquals("1h 02m", ChatPane.duration(3720));
    }
}
