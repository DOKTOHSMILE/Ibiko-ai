package ai.ibiko.ibiko1.neural;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko v1's subword tokenizer (docs/IBIKO_NEURAL.md, N1): byte-pair encoding learned from its own encyclopedia and
 * dictionary by tools/build_neural.py. A word starts as its characters plus the end mark {@code </w>}, and the
 * lowest-ranked adjacent pair is merged until none is left, so any word, even one never seen, misspelled or made up,
 * comes out as known pieces ("unlockable" → "un", "lock", "able</w>"). Pure Java.
 */
public final class Tokenizer {
    public static final String END = "</w>";
    /** A word as the trainer read it: letters and digits, with inner apostrophes and hyphens. */
    static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:['’-][\\p{L}\\p{N}]+)*");

    private final Map<String, Integer> ranks = new HashMap<String, Integer>();
    private final Map<String, List<String>> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, List<String>>(4096, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) {
                    return size() > 20000;
                }
            });
    private final int merges;

    private Tokenizer(int merges) {
        this.merges = merges;
    }

    /** Reads bpe.ibk ("IBKB", version 1, the merges in order). */
    public static Tokenizer read(InputStream in) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (!"IBKB".equals(new String(magic, StandardCharsets.US_ASCII)) || d.readUnsignedByte() != 1) {
            throw new IOException("not an Ibiko tokenizer");
        }
        int n = varint(d);
        Tokenizer t = new Tokenizer(n);
        for (int i = 0; i < n; i++) {
            String a = string(d), b = string(d);
            t.ranks.put(a + '\u0000' + b, i);
        }
        return t;
    }

    public int merges() {
        return merges;
    }

    /** The text's words, lower-cased, as the trainer split them. */
    public static List<String> words(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT).replace('’', '\''));
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    /** A word's pieces; the last ends with {@code </w>}. */
    public List<String> pieces(String word) {
        String w = word.toLowerCase(Locale.ROOT);
        List<String> hit = cache.get(w);
        if (hit != null) {
            return hit;
        }
        List<String> s = new ArrayList<String>();
        int[] cps = w.codePoints().toArray();
        for (int cp : cps) {
            s.add(new String(Character.toChars(cp)));
        }
        s.add(END);
        while (s.size() > 1) {
            int best = Integer.MAX_VALUE, at = -1;
            for (int k = 0; k + 1 < s.size(); k++) {
                Integer r = ranks.get(s.get(k) + '\u0000' + s.get(k + 1));
                if (r != null && r < best) {
                    best = r;
                    at = k;
                }
            }
            if (at < 0) {
                break;
            }
            s.set(at, s.get(at) + s.get(at + 1));
            s.remove(at + 1);
        }
        List<String> out = Collections.unmodifiableList(s);
        cache.put(w, out);
        return out;
    }

    /** All the text's pieces in order: the subword tokens a language model would see. */
    public List<String> tokens(String text) {
        List<String> out = new ArrayList<String>();
        for (String w : words(text)) {
            out.addAll(pieces(w));
        }
        return out;
    }

    static int varint(DataInputStream d) throws IOException {
        int n = 0, shift = 0;
        while (true) {
            int b = d.readUnsignedByte();
            n |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return n;
            }
            shift += 7;
        }
    }

    static String string(DataInputStream d) throws IOException {
        byte[] b = new byte[varint(d)];
        d.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }
}
