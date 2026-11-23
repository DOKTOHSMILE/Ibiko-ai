package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Happenings;

/** The understanding formula for events: what happened, to what, and when, read from plain sentences. */
public class HappeningsTest {
    private static String read(String text, String topic) {
        return Happenings.read(text, topic).toString();
    }

    @Test
    public void aDateAndTheActionItBelongsTo() {
        assertEquals("[(Nigeria | gained independence | 1 October 1960), (Nigeria | became a republic | 1963)]",
                read("The country gained independence on 1 October 1960, and became a republic in 1963.", "Nigeria"));
        assertEquals("[(The Berlin Wall | was taken down | 9 November 1989)]",
                read("The Berlin Wall was taken down on November 9, 1989.", "Berlin Wall"));
    }

    @Test
    public void aDateThatOpensTheSentenceDatesTheClauseAfterIt() {
        List<Happenings.Happening> h = Happenings.read("On July 20, 1969, Armstrong and Aldrin became the first humans to land on the moon.",
                "Apollo 11");
        assertEquals(1, h.size());
        assertEquals("Armstrong and Aldrin", h.get(0).subject);
        assertTrue(h.get(0).action.startsWith("became the first humans to land"));
        assertEquals("20 July 1969", h.get(0).when.said);
    }

    @Test
    public void aSpanIsAStartAndAnEnd() {
        assertEquals("[(World War II | began | 1 September 1939), (World War II | ended | 2 September 1945)]",
                read("World War II, or the Second World War, was a global conflict (1 September 1939 – 2 September 1945).", "World War II"));
        String span = read("Mandela was the Secretary General of the Non-Aligned Movement from 1998 to 1999.", "Nelson Mandela");
        assertTrue(span, span.contains("began | 1998") && span.contains("ended | 1999"));
    }

    @Test
    public void numbersThatArentYearsAreLeftAlone() {
        assertEquals("[]", read("1 Ghana Cedi is equal to 1000 pesewas.", "Ghana"));
        assertEquals("[]", read("About 1200 people live there.", "Town"));
        assertEquals("[]", read("The United States has fifty states.", "United States"));
    }

    @Test
    public void datesAsPreciseAsWritten() {
        assertEquals(1930, Happenings.date("16 November 1930").y);
        assertEquals(11, Happenings.date("16 November 1930").m);
        assertEquals(16, Happenings.date("16 November 1930").d);
        assertEquals(9, Happenings.date("November 9, 1989").d);
        assertEquals(-3000, Happenings.date("3000 BC").y);
        assertEquals(29, Happenings.date("16 November 1930").yearsTo(Happenings.date("1 October 1960")));
        assertTrue(Happenings.date("1 October 1960").before(Happenings.date("6 March 1961")));
    }
}
