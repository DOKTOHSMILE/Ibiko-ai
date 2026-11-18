package ai.ibiko.intelligence.importer;

import static org.junit.Assert.*;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class DelimitedDatasetReaderTest {
    @Test public void readsQuotedSeparatorsAndEscapedQuotes() throws Exception {
        final List<String> seen = new ArrayList<String>();
        long count = DelimitedDatasetReader.read(new StringReader("language\tlemma\tdefinition\n Yoruba\tile\t\"house, \\\"home\\\"\"\n"), '\t',
                (header, row, number) -> seen.add(row.get(2)));
        assertEquals(1, count);
        assertEquals("house, \"home\"", seen.get(0));
    }
    @Test public void readsCsvWithNewlineInsideQuotedField() throws Exception {
        final List<String> seen = new ArrayList<String>();
        DelimitedDatasetReader.read(new StringReader("lang,word,meaning\nen,bank,\"financial institution\nand river edge\"\n"), ',',
                (header, row, number) -> seen.add(row.get(2)));
        assertEquals("financial institution\nand river edge", seen.get(0));
    }
    @Test public void rejectsUnclosedQuote() throws Exception {
        try {
            DelimitedDatasetReader.read(new StringReader("lang,word\nen,\"broken\n"), ',', (h, r, n) -> {});
            fail("expected malformed input to be rejected");
        } catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("Unclosed")); }
    }
}
