package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** An API model's answer checked by Ibiko before it is shown or kept: against, backed, or new. */
public class TeacherCheckTest {
    @Test
    public void againstAFactItHoldsSure() throws Exception {
        Facts facts = new Facts(new File(Files.createTempDirectory("t").toFile(), "facts.tsv"));
        facts.learn("Nelson Mandela was born on 18 July 1918.", "Nelson Mandela", 2, 0);
        TeacherCheck.Result r = TeacherCheck.check("Nelson Mandela was born on 18 July 1920. He was a lawyer.", "Nelson Mandela", facts, null, null);
        assertEquals(TeacherCheck.Verdict.CONFLICT, r.verdict);
        assertTrue(r.because, r.because.contains("1918"));
    }

    @Test
    public void againstWhatThePilotTaught() throws Exception {
        Knowledge k = new Knowledge(new File(Files.createTempDirectory("t").toFile(), "k.tsv"));
        k.confirm("What is a zorblat?", "A zorblat is a wedding dance from Ibadan.");
        TeacherCheck.Result r = TeacherCheck.check("A zorblat is a software library for spreadsheets.", "zorblat", null, k, null);
        assertEquals(TeacherCheck.Verdict.CONFLICT, r.verdict);
    }

    @Test
    public void backedByItsOwnReading() {
        String article = "The Nile is a major north-flowing river in northeastern Africa. It flows into the Mediterranean Sea."
                + " The Nile is the longest river in Africa, about 6,650 km long.";
        TeacherCheck.Result r = TeacherCheck.check("The Nile is the longest river in Africa. It flows north into the Mediterranean Sea.",
                "Nile", null, null, article);
        assertEquals(TeacherCheck.Verdict.CONFIRMED, r.verdict);
        assertEquals("my encyclopedia's article on Nile", r.because);
    }

    @Test
    public void newWhenNothingBacksOrGoesAgainstIt() {
        TeacherCheck.Result r = TeacherCheck.check("The national bird of Zorbania is the blue heron. It was chosen in 1952.", "Zorbania",
                null, null, "Zorbania is a small country with mountains and lakes.");
        assertEquals(TeacherCheck.Verdict.NEW, r.verdict);
    }
}
