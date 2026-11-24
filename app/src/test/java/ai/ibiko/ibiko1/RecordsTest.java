package ai.ibiko.ibiko1;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;

import org.junit.Test;

import ai.ibiko.ibiko1.formulas.Records;

/** Facts found by what they are: the records the encyclopedia names, and the world's holder for the place it is in. */
public class RecordsTest {
    private static Records records() {
        return new Records(TextCode.open(path -> {
            try {
                return new FileInputStream("src/main/assets/" + path);
            } catch (java.io.IOException e) {
                return null;
            }
        }, "knowledge/records.tsv"));
    }

    @Test
    public void readAsked() {
        assertArrayEquals(new String[]{"high", "mountain", "world"}, Records.asked("What is the highest mountain in the world?"));
        assertArrayEquals(new String[]{"high", "mountain", "world"}, Records.asked("which is the tallest mountain on earth"));
        assertArrayEquals(new String[]{"long", "river", "Africa"}, Records.asked("What is the longest river in Africa?"));
        assertArrayEquals(new String[]{"populous", "city", "Nigeria"}, Records.asked("What is the most populous city in Nigeria?"));
        assertNull("a clause isn't a thing", Records.asked("What is the highest score you can get?"));
        assertNull(Records.asked("Tell me about mountains"));
    }

    @Test
    public void foundByWhatItIs() {
        Records r = records();
        assertTrue(r.size() > 1000);
        String everest = r.answer(Records.asked("What is the highest mountain in the world?"));
        assertTrue(everest, everest.startsWith("Mount Everest. Mount Everest is the highest mountain on Earth."));
        String lagos = r.answer(Records.asked("What is the most populous city in Nigeria?"));
        assertTrue(lagos, lagos.startsWith("Lagos."));
        String kenya = r.answer(Records.asked("What is the highest mountain in Kenya?"));
        assertTrue(kenya, kenya.startsWith("Mount Kenya."));
        String pacific = r.answer(Records.asked("What is the largest ocean?"));
        assertTrue("said with no place, for a thing with one world meaning: " + pacific, pacific.startsWith("Pacific Ocean."));
        assertNull("the largest city said with no place is a country's, not the world's", r.answer(Records.asked("What is the largest city?")));
    }

    @Test
    public void theWorldsHolderForThePlaceItIsIn() {
        String nile = records().answer(Records.asked("What is the longest river in Africa?"));
        assertTrue(nile, nile.startsWith("Nile. It is the longest river on Earth") && nile.endsWith("It's in Africa, so it's the longest river in Africa too."));
    }
}
