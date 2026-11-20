package ai.ibiko.ibiko1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    /** Shards coded the way the build tools code them (IBK1): the key's hash picks the file, words become numbers. */
    private static Encyclopedia with(String... rows) {
        // the vocabulary: every word form used, most used first
        java.util.Map<String, Integer> uses = new java.util.HashMap<String, Integer>();
        for (String row : rows) {
            String body = row.split("\t", 3)[2];
            if (!body.startsWith("=>")) {
                for (String[] t : tokens(body)) {
                    uses.merge(form(t[0])[0], 1, Integer::sum);
                }
            }
        }
        final java.util.List<String> vocab = new java.util.ArrayList<String>(uses.keySet());
        vocab.sort((a, b) -> uses.get(b) - uses.get(a) != 0 ? uses.get(b) - uses.get(a) : a.compareTo(b));
        java.io.ByteArrayOutputStream v = new java.io.ByteArrayOutputStream();
        v.write('I'); v.write('B'); v.write('K'); v.write('1'); v.write('V');
        varint(v, vocab.size());
        for (String w : vocab) {
            byte[] b = w.getBytes(StandardCharsets.UTF_8);
            varint(v, b.length);
            v.write(b, 0, b.length);
        }
        final Map<String, java.util.List<String[]>> shards = new HashMap<String, java.util.List<String[]>>();
        for (String row : rows) {
            String[] r = row.split("\t", 3);
            shards.computeIfAbsent(Encyclopedia.DIR + String.format("%03d.ibk", Encyclopedia.shard(r[0])),
                    x -> new java.util.ArrayList<String[]>()).add(r);
        }
        final Map<String, byte[]> files = new HashMap<String, byte[]>();
        files.put(Encyclopedia.DIR + "vocab.ibk", v.toByteArray());
        for (Map.Entry<String, java.util.List<String[]>> e : shards.entrySet()) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
            o.write('I'); o.write('B'); o.write('K'); o.write('1');
            varint(o, e.getValue().size());
            for (String[] r : e.getValue()) {
                bytes(o, r[0].getBytes(StandardCharsets.UTF_8));
                java.io.ByteArrayOutputStream p = new java.io.ByteArrayOutputStream();
                if (r[2].startsWith("=>")) {
                    o.write((3 << 1) | 1); // a redirect: no title of its own
                    byte[] t = r[2].substring(2).getBytes(StandardCharsets.UTF_8);
                    p.write(t, 0, t.length);
                } else {
                    // the title by rule when it is the key capitalised, else written
                    int rule = r[1].equals(Encyclopedia.capital(r[0])) ? 1 : 0;
                    o.write(rule << 1);
                    if (rule == 0) {
                        bytes(o, r[1].getBytes(StandardCharsets.UTF_8));
                    }
                    for (String[] t : tokens(r[2])) {
                        String[] fc = form(t[0]);
                        varint(p, ((vocab.indexOf(fc[0]) + 1) << 3) | (Integer.parseInt(fc[1]) << 1) | ("1".equals(t[1]) ? 1 : 0));
                    }
                }
                bytes(o, p.toByteArray());
            }
            files.put(e.getKey(), o.toByteArray());
        }
        return new Encyclopedia(path -> files.containsKey(path) ? new ByteArrayInputStream(files.get(path)) : null);
    }

    private static java.util.List<String[]> tokens(String text) {
        java.util.List<String[]> out = new java.util.ArrayList<String[]>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\p{L}\\p{N}_]+|[^\\p{L}\\p{N}_\\s]").matcher(text);
        int last = 0;
        while (m.find()) {
            out.add(new String[]{m.group(), m.start() > last ? "1" : "0"});
            last = m.end();
        }
        return out;
    }

    /** {vocabulary form, case}: 0 as is, 1 first letter capital, 2 all capitals. */
    private static String[] form(String t) {
        String low = t.toLowerCase(java.util.Locale.ROOT);
        if (t.equals(low)) {
            return new String[]{t, "0"};
        }
        if (t.equals(Character.toUpperCase(low.charAt(0)) + low.substring(1))) {
            return new String[]{low, "1"};
        }
        if (t.equals(low.toUpperCase(java.util.Locale.ROOT))) {
            return new String[]{low, "2"};
        }
        return new String[]{t, "0"};
    }

    private static void varint(java.io.ByteArrayOutputStream o, int n) {
        while (true) {
            int b = n & 0x7f;
            n >>>= 7;
            if (n != 0) {
                o.write(b | 0x80);
            } else {
                o.write(b);
                return;
            }
        }
    }

    private static void bytes(java.io.ByteArrayOutputStream o, byte[] b) {
        varint(o, b.length);
        o.write(b, 0, b.length);
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
    public void theCodesGiveTheWordsBackExactly() {
        Encyclopedia e = with("x\tX\tThe USA's GDP, in 2024, was \"large\" (very) - e.g. NASA's iPhone app.",
                "y\tY\tthe usa usa USA Usa");
        assertEquals("The USA's GDP, in 2024, was \"large\" (very) - e.g. NASA's iPhone app.", e.get("x").text);
        assertEquals("the usa usa USA Usa", e.get("y").text);
    }

    @Test
    public void questionsThatAskToExplainSomethingAreAnswered() {
        String a = ENC.answer("What is a volcano?");
        assertNotNull(a);
        assertTrue(a.startsWith("A volcano is an opening"));
        assertFalse("just the answer: no source line under it", a.contains("Simple English Wikipedia"));
        assertTrue(ENC.answer("who was Nelson Mandela").contains("anti-apartheid"));
        assertTrue(ENC.answer("Tell me about Lagos").startsWith("Lagos is the largest city"));
        assertTrue(ENC.answer("what is the United States").contains("North America"));
        assertNull("a fact about a part of it is for the facts", ENC.answer("what is the capital of Lagos"));
        assertNull("not a question to explain", ENC.answer("I like volcanoes"));
        assertNull("define is the dictionary's", ENC.answer("define volcano"));
    }
}
