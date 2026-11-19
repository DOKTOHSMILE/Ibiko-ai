package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

/** The encyclopedia: a title (or another name for it) found in its one small file, and the question it answers. */
public class EncyclopediaTest {
    /** Shards built the way the build tool builds them: the key's hash picks the file. */
    private static Encyclopedia with(String... rows) {
        final Map<String, StringBuilder> files = new HashMap<String, StringBuilder>();
        for (String row : rows) {
            String k = row.substring(0, row.indexOf('\t'));
            String name = Encyclopedia.DIR + String.format("%03d.tsv", Encyclopedia.shard(k));
            files.computeIfAbsent(name, x -> new StringBuilder()).append(row).append('\n');
        }
        return new Encyclopedia(path -> files.containsKey(path)
                ? new ByteArrayInputStream(files.get(path).toString().getBytes(StandardCharsets.UTF_8)) : null);
    }

    private static final Encyclopedia ENC = with(
            "volcano\tVolcano\tA volcano is an opening in the Earth's crust where molten rock comes out.",
            "nelson mandela\tNelson Mandela\tNelson Mandela was a South African anti-apartheid leader and the first black President of South Africa.",
            "united states\tUnited States\tThe United States of America is a country in North America.",
            "usa\tUSA\t=>United States",
            "lagos\tLagos\tLagos is the largest city in Nigeria.",
            "loop a\tLoop A\t=>Loop B",
            "loop b\tLoop B\t=>Loop A");

    @Test
    public void theTitleIsKeyedAsTheBuildToolKeysIt() {
        assertEquals("nelson mandela", Encyclopedia.key("Nelson  Mandela!"));
        assertEquals("são paulo", Encyclopedia.key("São Paulo"));
        // the same hash Python's build tool computes for "volcano" (Java's String.hashCode)
        assertEquals((int) ("volcano".hashCode() & 0x7fffffff) % 512, Encyclopedia.shard("volcano"));
    }

    @Test
    public void anArticleIsFoundByItsNameOrAnother() {
        assertEquals("Volcano", ENC.find("a volcano").title);
        assertEquals("Volcano", ENC.find("volcanoes").title);
        assertEquals("United States", ENC.find("USA").title);
        assertNull("a loop of other names ends", ENC.find("Loop A"));
        assertNull(ENC.find("zorblat"));
    }

    @Test
    public void questionsThatAskToExplainSomethingAreAnswered() {
        String a = ENC.answer("What is a volcano?");
        assertNotNull(a);
        assertTrue(a.startsWith("A volcano is an opening"));
        assertTrue(a.contains("(From Simple English Wikipedia, “Volcano”.)"));
        assertTrue(ENC.answer("who was Nelson Mandela").contains("anti-apartheid"));
        assertTrue(ENC.answer("Tell me about Lagos").startsWith("Lagos is the largest city"));
        assertTrue(ENC.answer("what is the United States").contains("North America"));
        assertNull("a fact about a part of it is for the facts", ENC.answer("what is the capital of Lagos"));
        assertNull("not a question to explain", ENC.answer("I like volcanoes"));
        assertNull("define is the dictionary's", ENC.answer("define volcano"));
    }
}
