package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The three-way merge on the cases git handles: separate edits combine, overlapping ones conflict. */
public class LineMergeCasesTest {
    private static final String BASE = "a\nb\nc\nd\ne\nf\n";

    @Test
    public void separateEditsCombine() {
        LineMerge.Result r = LineMerge.of(BASE, "a\nB\nc\nd\ne\nf\n", "a\nb\nc\nd\nE\nf\n");
        assertTrue(r.clean());
        assertEquals("a\nB\nc\nd\nE\nf\n", r.text);
        r = LineMerge.of(BASE, "A\nb\nc\nd\ne\nf\n", "a\nb\nc\nd\ne\nf\ng\n");
        assertEquals("an edit at the start and lines added at the end", "A\nb\nc\nd\ne\nf\ng\n", r.text);
        r = LineMerge.of(BASE, "a\nb\nc\nd\ne\nf\n", "a\nb\nc\nd\ne\nf\n");
        assertEquals(BASE, r.text);
    }

    @Test
    public void theSameEditOnBothSidesIsNotAConflict() {
        LineMerge.Result r = LineMerge.of(BASE, "a\nX\nc\nd\ne\nf\n", "a\nX\nc\nd\ne\nf\n");
        assertTrue(r.clean());
        assertEquals("a\nX\nc\nd\ne\nf\n", r.text);
    }

    @Test
    public void overlappingEditsConflict() {
        LineMerge.Result r = LineMerge.of(BASE, "a\nb\nOURS\nd\ne\nf\n", "a\nb\nTHEIRS\nd\ne\nf\n");
        assertEquals(1, r.conflicts);
        assertTrue(r.text, r.text.contains("<<<<<<< yours\nOURS\n=======\nTHEIRS\n>>>>>>> theirs"));
        r = LineMerge.of(BASE, "a\nb\nd\ne\nf\n", "a\nb\nC2\nd\ne\nf\n");
        assertEquals("one deletes a line the other changed", 1, r.conflicts);
        r = LineMerge.of(BASE, "a\nb\nc\nX\nd\ne\nf\n", "a\nb\nc\nY\nd\ne\nf\n");
        assertEquals("different lines inserted at the same place", 1, r.conflicts);
    }

    @Test
    public void aDeletionAndAnEditElsewhereCombine() {
        LineMerge.Result r = LineMerge.of(BASE, "a\nc\nd\ne\nf\n", "a\nb\nc\nd\ne\nF\n");
        assertTrue(r.clean());
        assertEquals("a\nc\nd\ne\nF\n", r.text);
    }
}
