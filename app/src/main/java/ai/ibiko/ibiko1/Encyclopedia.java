package ai.ibiko.ibiko1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko 1's encyclopedia, inside the app: the opening of every article of Simple English Wikipedia (CC BY-SA 4.0, see
 * {@code assets/knowledge/encyclopedia/LICENSE.txt}), in plain words, with the redirects as other names ("USA" for
 * "United States"). It answers "what is a volcano", "who was Nelson Mandela", "tell me about Lagos" offline, saying
 * where it read it. Kept in the Ibiko v1 coded form (IBK1, {@code tools/encode_encyclopedia.py}): every word a number
 * from one shared vocabulary, most-used words the smallest, so the files are a fraction of the text's size; split by
 * title into {@link #SHARDS} files, so a lookup reads one, skips the entries it doesn't want by their length, and
 * decodes only the article it found. The files last read and the vocabulary are kept. Pure Java: the files come
 * through {@link Plug#asset}.
 */
public final class Encyclopedia {
    /** How many files the articles are split into (as the build tool). */
    public static final int SHARDS = 512;
    static final String DIR = "knowledge/encyclopedia/";
    /** Shards kept in memory once read (the most recent). */
    static final int KEEP = 32;
    /** Redirects followed before giving up (a chain that loops is cut). */
    static final int HOPS = 3;

    /** One article: its title and its opening. */
    public static final class Article {
        public final String title, text;

        Article(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private final java.util.function.Function<String, InputStream> files;
    /** Shards read, as their bytes (an entry is decoded only when it is the one asked for). */
    private final LinkedHashMap<Integer, byte[]> cache = new LinkedHashMap<Integer, byte[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > KEEP;
        }
    };
    /** The vocabulary: its words' bytes end to end, and where each starts (word n is at {@code at[n]}). */
    private byte[] words;
    private int[] at;

    /** Reads its files from the app's assets ({@link Plug#asset}). */
    public Encyclopedia() {
        this(path -> Plug.asset.apply(path));
    }

    /** Reads its files with {@code files} (a test gives its own). */
    public Encyclopedia(java.util.function.Function<String, InputStream> files) {
        this.files = files;
    }

    /** A title as the files key it: lower case, letters and digits, single spaces (as the build tool). */
    public static String key(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^\\p{L}\\p{N}_]+", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    static int shard(String key) {
        return (key.hashCode() & 0x7fffffff) % SHARDS;
    }

    /** The article for {@code name} (redirects followed), or null. */
    public synchronized Article get(String name) {
        String k = key(name);
        for (int hop = 0; hop <= HOPS && !k.isEmpty(); hop++) {
            byte[] shard = load(shard(k));
            int[] found = locate(shard, k.getBytes(StandardCharsets.UTF_8));
            if (found == null) {
                return null;
            }
            if ((found[2] & 1) == 0) {
                return new Article(title(shard, found, k), decode(shard, found[3], found[4]));
            }
            k = key(new String(shard, found[3], found[4], StandardCharsets.UTF_8)); // another name for it
        }
        return null;
    }

    /** A varint at {@code p[0]} in {@code b}; {@code p[0]} moves past it. */
    static int varint(byte[] b, int[] p) {
        int n = 0, shift = 0;
        while (p[0] < b.length) {
            int x = b[p[0]++] & 0xff;
            n |= (x & 0x7f) << shift;
            if ((x & 0x80) == 0) {
                return n;
            }
            shift += 7;
        }
        return n;
    }

    /**
     * Where the entry for {@code key} is in a shard: {title start, title length, kind, payload start, payload length},
     * or null. Entries before it are skipped by their lengths, never decoded. The kind's bit 0 says a redirect; bits
     * 1-2 how the title is made (0 written in the entry, 1 the key with its first letter capital, 2 each word capital).
     */
    static int[] locate(byte[] shard, byte[] key) {
        if (shard.length < 4 || shard[0] != 'I' || shard[1] != 'B' || shard[2] != 'K' || shard[3] != '1') {
            return null;
        }
        int[] p = {4};
        int count = varint(shard, p);
        for (int e = 0; e < count && p[0] < shard.length; e++) {
            int kl = varint(shard, p), ks = p[0];
            p[0] += kl;
            int kind = shard[p[0]++] & 0xff;
            int tl = 0, ts = p[0];
            if (((kind >> 1) & 3) == 0) {
                tl = varint(shard, p);
                ts = p[0];
                p[0] += tl;
            }
            int pl = varint(shard, p), ps = p[0];
            p[0] += pl;
            if (kl == key.length && same(shard, ks, key)) {
                return new int[]{ts, tl, kind, ps, pl};
            }
        }
        return null;
    }

    /** An article's title: written in the entry, or made from its key by the entry's rule. */
    static String title(byte[] shard, int[] found, String key) {
        switch ((found[2] >> 1) & 3) {
            case 1:
                return capital(key);
            case 2:
                StringBuilder s = new StringBuilder();
                for (String w : key.split(" ")) {
                    s.append(s.length() == 0 ? "" : " ").append(capital(w));
                }
                return s.toString();
            default:
                return new String(shard, found[0], found[1], StandardCharsets.UTF_8);
        }
    }

    /** The first letter capital (one letter for one, as the build tool). */
    static String capital(String w) {
        if (w.isEmpty()) {
            return w;
        }
        int first = w.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first))) + w.substring(Character.charCount(first));
    }

    private static boolean same(byte[] b, int from, byte[] key) {
        for (int i = 0; i < key.length; i++) {
            if (b[from + i] != key[i]) {
                return false;
            }
        }
        return true;
    }

    /** An article's words back from their codes (see {@code tools/encode_encyclopedia.py}). */
    String decode(byte[] b, int from, int length) {
        vocabulary();
        StringBuilder s = new StringBuilder(length * 3);
        int[] p = {from};
        int end = from + length;
        while (p[0] < end) {
            int code = varint(b, p);
            int v = code >>> 3, kase = (code >>> 1) & 3;
            boolean space = (code & 1) != 0;
            String t;
            if (v == 0) {
                int n = varint(b, p);
                t = new String(b, p[0], n, StandardCharsets.UTF_8);
                p[0] += n;
            } else if (v - 1 < at.length - 1) {
                t = new String(words, at[v - 1], at[v] - at[v - 1], StandardCharsets.UTF_8);
                if (kase == 1) {
                    t = capital(t);
                } else if (kase == 2) {
                    t = t.toUpperCase(Locale.ROOT);
                }
            } else {
                t = ""; // a number past the vocabulary: a damaged file
            }
            if (space && s.length() > 0) {
                s.append(' ');
            }
            s.append(t);
        }
        return s.toString();
    }

    /** The shared vocabulary, read once. */
    private void vocabulary() {
        if (words != null) {
            return;
        }
        byte[] v = readAll(files.apply(DIR + "vocab.ibk"));
        if (v.length < 5 || v[0] != 'I' || v[4] != 'V') {
            words = new byte[0];
            at = new int[]{0};
            return;
        }
        int[] p = {5};
        int count = varint(v, p);
        at = new int[count + 1];
        java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream(v.length);
        for (int i = 0; i < count; i++) {
            int n = varint(v, p);
            at[i] = all.size();
            all.write(v, p[0], n);
            p[0] += n;
        }
        at[count] = all.size();
        words = all.toByteArray();
    }

    private static byte[] readAll(InputStream in) {
        if (in == null) {
            return new byte[0];
        }
        try (InputStream i = in) {
            java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream(1 << 16);
            byte[] buf = new byte[1 << 15];
            int n;
            while ((n = i.read(buf)) > 0) {
                o.write(buf, 0, n);
            }
            return o.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /**
     * The article for what a question names, trying the forms a name comes in: as written, without "a/an/the", the
     * singular of a plural, and each word capitalised the same (keys are not case-sensitive). Null when none.
     */
    public Article find(String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            return null;
        }
        for (String form : forms(subject)) {
            Article a = get(form);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    /** The ways a subject may be titled. */
    static List<String> forms(String subject) {
        List<String> out = new ArrayList<String>();
        String s = subject.trim().replaceAll("[?.!]+$", "").trim();
        add(out, s);
        String bare = s.replaceFirst("(?i)^(a|an|the)\\s+", "");
        add(out, bare);
        String low = bare.toLowerCase(Locale.ROOT);
        if (low.endsWith("ies") && low.length() > 4) {
            add(out, bare.substring(0, bare.length() - 3) + "y");
        } else if (low.matches(".*(ches|shes|sses|xes|zes|oes)$")) {
            add(out, bare.substring(0, bare.length() - 2)); // churches, volcanoes
            add(out, bare.substring(0, bare.length() - 1));
        } else if (low.endsWith("s") && !low.endsWith("ss") && low.length() > 3) {
            add(out, bare.substring(0, bare.length() - 1));
        }
        return out;
    }

    private static void add(List<String> out, String s) {
        if (!s.isEmpty() && !out.contains(s)) {
            out.add(s);
        }
    }

    private static final Pattern ASKS = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:(?:can|could) you\\s+)?(?:"
            + "what(?:'s| is| are| was| were)|who(?:'s| is| are| was| were)|where(?:'s| is| was)|"
            + "tell me (?:about|more about)|explain|describe|what do you know about|"
            + "give me (?:information|info|facts) (?:on|about)|information (?:on|about)|facts about)\\s+(.+?)[?.!]*\\s*$");

    /** What a question asks to be explained ("what is a volcano" → "a volcano"), or null when it isn't that kind. */
    public static String asked(String question) {
        if (question == null) {
            return null;
        }
        Matcher m = ASKS.matcher(question.trim());
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1).trim();
        // "what is the capital of X" and other questions about a part of a thing are for the facts, not an article
        if (s.isEmpty() || s.split("\\s+").length > 7 || s.matches("(?i).*\\b(of|in|for|between|than|from|to)\\b.*")
                && !s.matches("(?i)^(the )?(united|republic|kingdom|state|states|bank|church|house|university|city|isle|island|bay|gulf|sea|lake|river|mount|battle|war|treaty|museum|order|book|history|theory|law|age|statue|tower|cup|league|prince|king|queen|duke|museum)\\b.*")) {
            return null;
        }
        return s;
    }

    /**
     * The answer to {@code question} from the encyclopedia: the article's opening, a little shortened, and where it
     * comes from; null when the question doesn't ask to explain something it has.
     */
    public String answer(String question) {
        String s = asked(question);
        Article a = s == null ? null : find(s);
        if (a == null) {
            return null;
        }
        return a.text; // just the answer: the article it came from is named in the steps
    }

    /** Where the fact tables are ({@code tools/build_facts.py}). */
    static final String FACTS = DIR + "facts/";
    /** Fact shards read, as their bytes. */
    private final LinkedHashMap<Integer, byte[]> factCache = new LinkedHashMap<Integer, byte[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> eldest) {
            return size() > KEEP;
        }
    };
    /** The relations' names, by number ({@code facts/relations.ibk}). */
    private String[] relations;

    /**
     * The facts the article's information box gives ("born" → "18 July 1918", "capital" → "Abuja"), by relation, in
     * the box's order; empty when it has none. {@code title} is the article's own title (as {@link #get} gives it).
     */
    public synchronized LinkedHashMap<String, String> facts(String title) {
        LinkedHashMap<String, String> out = new LinkedHashMap<String, String>();
        String k = key(title);
        if (k.isEmpty()) {
            return out;
        }
        if (relations == null) {
            byte[] r = readAll(files.apply(FACTS + "relations.ibk"));
            if (r.length < 5 || r[0] != 'I' || r[4] != 'R') {
                relations = new String[0];
            } else {
                int[] p = {5};
                relations = new String[varint(r, p)];
                for (int i = 0; i < relations.length; i++) {
                    int n = varint(r, p);
                    relations[i] = new String(r, p[0], n, StandardCharsets.UTF_8);
                    p[0] += n;
                }
            }
        }
        int sh = shard(k);
        byte[] b = factCache.get(sh);
        if (b == null) {
            b = readAll(files.apply(FACTS + String.format(Locale.ROOT, "%03d.ibk", sh)));
            factCache.put(sh, b);
        }
        if (b.length < 5 || b[0] != 'I' || b[4] != 'F') {
            return out;
        }
        byte[] want = k.getBytes(StandardCharsets.UTF_8);
        int[] p = {5};
        int count = varint(b, p);
        for (int e = 0; e < count && p[0] < b.length; e++) {
            int kl = varint(b, p), ks = p[0];
            p[0] += kl;
            int n = varint(b, p);
            boolean mine = kl == want.length && same(b, ks, want);
            for (int f = 0; f < n && p[0] < b.length; f++) {
                int rel = b[p[0]++] & 0xff;
                int pl = varint(b, p), ps = p[0];
                p[0] += pl;
                if (mine && rel < relations.length) {
                    out.put(relations[rel], decode(b, ps, pl));
                }
            }
            if (mine) {
                return out;
            }
        }
        return out;
    }

    /** One shard's bytes (empty when the file isn't there). */
    private byte[] load(int n) {
        byte[] got = cache.get(n);
        if (got == null) {
            got = readAll(files.apply(DIR + String.format(Locale.ROOT, "%03d.ibk", n)));
            cache.put(n, got);
        }
        return got;
    }
}
