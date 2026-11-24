package ai.ibiko.ibiko1.words;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The dictionary turned both ways: every sense of every word (Open English WordNet, the brain's lexicon.tsv) indexed by
 * the words of its meaning, so a meaning finds its word as fast as a word finds its meaning. For each word that appears
 * in a definition, a synonym list or a broader term, the senses it appears in; for each sense, its headword and part of
 * speech; the definitions themselves stay on disk and are read only for the few senses an answer needs. Built once from
 * the lexicon (a second or two) and kept beside it as a compact binary file, so later starts load it in a moment.
 * Pure Java.
 */
public final class MeaningIndex {
    /** A sense's place in the lexicon: its number. */
    public static final class Sense {
        public final int id;
        public final String word, pos, definition, broader;
        public final List<String> synonyms, opposites;

        Sense(int id, String[] c) {
            this.id = id;
            word = c[0];
            pos = c.length > 1 ? c[1] : "";
            definition = c.length > 2 ? c[2] : "";
            synonyms = list(c.length > 3 ? c[3] : "");
            opposites = list(c.length > 4 ? c[4] : "");
            broader = c.length > 5 ? c[5] : "";
        }

        static List<String> list(String s) {
            List<String> out = new ArrayList<String>();
            for (String p : s.split(",")) {
                if (!p.trim().isEmpty()) {
                    out.add(p.trim());
                }
            }
            return out;
        }

        @Override
        public String toString() {
            return word + " (" + pos + "): " + definition;
        }
    }

    /** Bumped when the index's form changes, so an older cache is rebuilt. */
    static final int FORM = 3;

    private final File lexicon;
    /** Each sense's headword, part of speech (0 noun, 1 verb, 2 adjective, 3 adverb, 4 other) and line start. */
    String[] word;
    byte[] pos;
    long[] at;
    /** Word (as {@link Words#key}) to the senses whose definition holds it, and whose synonyms or broader term hold it. */
    final Map<String, int[]> inMeaning = new HashMap<String, int[]>();
    final Map<String, int[]> asSynonym = new HashMap<String, int[]>();
    /** Senses whose opposites list a word (so "small" finds "big", which lists it). */
    final Map<String, int[]> asOpposite = new HashMap<String, int[]>();
    /** Headword (lower case) to its senses, most common first. */
    final Map<String, int[]> heads = new HashMap<String, int[]>();
    private RandomAccessFile reader;

    private MeaningIndex(File lexicon) {
        this.lexicon = lexicon;
    }

    /**
     * The indexes open, by file: the app has one (its brain folder's), so it's kept for good. Only the last two used are
     * held, so a process that opens many folders (each test's own) doesn't keep every one: about 10 MB each, and they
     * were never let go.
     */
    private static final Map<String, MeaningIndex> OPEN = new java.util.LinkedHashMap<String, MeaningIndex>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, MeaningIndex> eldest) {
            return size() > 2;
        }
    };

    /** The index of a lexicon file, built or loaded once per file. Null when the file isn't there. */
    public static synchronized MeaningIndex of(File lexicon) {
        if (lexicon == null || !lexicon.isFile()) {
            return null;
        }
        String k = lexicon.getAbsolutePath();
        MeaningIndex m = OPEN.get(k);
        if (m == null) {
            m = new MeaningIndex(lexicon);
            File cache = new File(lexicon.getPath() + ".meanings");
            if (!m.load(cache)) {
                m.build();
                m.save(cache);
            }
            OPEN.put(k, m);
        }
        return m;
    }

    public int size() {
        return word.length;
    }

    static byte posCode(String p) {
        switch (p) {
            case "noun": return 0;
            case "verb": return 1;
            case "adjective": return 2;
            case "adverb": return 3;
            default: return 4;
        }
    }

    static final String[] POS = {"noun", "verb", "adjective", "adverb", "other"};

    public String pos(int id) {
        return POS[pos[id]];
    }

    // ------------------------------------------------------------------ building

    private void build() {
        List<String> words = new ArrayList<String>(200_000);
        ByteList p = new ByteList();
        LongList starts = new LongList();
        Map<String, IntList> def = new HashMap<String, IntList>(), syn = new HashMap<String, IntList>(), opp = new HashMap<String, IntList>();
        Map<String, IntList> head = new HashMap<String, IntList>();
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(lexicon), 1 << 16)) {
            long offset = 0;
            java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(256);
            int b;
            int id = 0;
            while (true) {
                b = in.read();
                if (b == '\n' || b < 0) {
                    if (line.size() > 0) {
                        String[] c = new String(line.toByteArray(), StandardCharsets.UTF_8).split("\t", -1);
                        words.add(c[0]);
                        p.add(posCode(c.length > 1 ? c[1] : ""));
                        starts.add(offset);
                        add(head, c[0].toLowerCase(Locale.ROOT), id);
                        Set<String> seen = new HashSet<String>();
                        for (String w : Words.content(c.length > 2 ? c[2] : "")) {
                            if (seen.add(w)) {
                                add(def, w, id);
                            }
                        }
                        seen.clear();
                        for (String s : (c.length > 3 ? c[3] : "").split(",")) {
                            addPhrase(syn, s, id, seen);
                        }
                        addPhrase(syn, c.length > 5 ? c[5] : "", id, seen);
                        seen.clear();
                        for (String s : (c.length > 4 ? c[4] : "").split(",")) {
                            addPhrase(opp, s, id, seen);
                        }
                        id++;
                    }
                    offset += line.size() + 1;
                    line.reset();
                    if (b < 0) {
                        break;
                    }
                } else {
                    line.write(b);
                }
            }
        } catch (IOException e) {
            // what was read stays usable
        }
        word = words.toArray(new String[0]);
        pos = p.toArray();
        at = starts.toArray();
        freeze(def, inMeaning);
        freeze(syn, asSynonym);
        freeze(opp, asOpposite);
        freeze(head, heads);
    }

    /** A synonym or broader term: as a whole ("airplane pilot") and each word of it. */
    private static void addPhrase(Map<String, IntList> to, String phrase, int id, Set<String> seen) {
        String p = phrase.trim().toLowerCase(Locale.ROOT);
        if (p.isEmpty()) {
            return;
        }
        if (seen.add(p)) {
            add(to, p, id);
        }
        if (p.contains(" ")) {
            for (String w : Words.content(p)) {
                if (seen.add(w)) {
                    add(to, w, id);
                }
            }
        } else {
            String k = Words.key(p);
            if (!k.equals(p) && seen.add(k)) {
                add(to, k, id);
            }
        }
    }

    private static void add(Map<String, IntList> m, String k, int id) {
        IntList l = m.get(k);
        if (l == null) {
            l = new IntList();
            m.put(k, l);
        }
        l.add(id);
    }

    private static void freeze(Map<String, IntList> from, Map<String, int[]> to) {
        for (Map.Entry<String, IntList> e : from.entrySet()) {
            to.put(e.getKey(), e.getValue().toArray());
        }
    }

    // ------------------------------------------------------------------ the cache

    private boolean load(File f) {
        if (!f.isFile() || f.lastModified() < lexicon.lastModified()) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16))) {
            if (in.readInt() != FORM || in.readLong() != lexicon.length()) {
                return false;
            }
            int n = in.readInt();
            word = new String[n];
            pos = new byte[n];
            at = new long[n];
            for (int i = 0; i < n; i++) {
                word[i] = in.readUTF();
                pos[i] = in.readByte();
                at[i] = in.readLong();
            }
            readMap(in, inMeaning);
            readMap(in, asSynonym);
            readMap(in, asOpposite);
            readMap(in, heads);
            return true;
        } catch (IOException | RuntimeException e) {
            inMeaning.clear();
            asSynonym.clear();
            asOpposite.clear();
            heads.clear();
            return false;
        }
    }

    private void save(File f) {
        File tmp = new File(f.getPath() + ".part");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16))) {
            out.writeInt(FORM);
            out.writeLong(lexicon.length());
            out.writeInt(word.length);
            for (int i = 0; i < word.length; i++) {
                out.writeUTF(word[i]);
                out.writeByte(pos[i]);
                out.writeLong(at[i]);
            }
            writeMap(out, inMeaning);
            writeMap(out, asSynonym);
            writeMap(out, asOpposite);
            writeMap(out, heads);
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (!tmp.renameTo(f)) {
            f.delete();
            tmp.renameTo(f);
        }
    }

    private static void writeMap(DataOutputStream out, Map<String, int[]> m) throws IOException {
        out.writeInt(m.size());
        for (Map.Entry<String, int[]> e : m.entrySet()) {
            out.writeUTF(e.getKey());
            int[] v = e.getValue();
            out.writeInt(v.length);
            int last = 0;
            for (int x : v) {
                writeVar(out, x - last); // ascending ids: the gaps are small
                last = x;
            }
        }
    }

    private static void readMap(DataInputStream in, Map<String, int[]> m) throws IOException {
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            String k = in.readUTF();
            int[] v = new int[in.readInt()];
            int last = 0;
            for (int j = 0; j < v.length; j++) {
                last += readVar(in);
                v[j] = last;
            }
            m.put(k, v);
        }
    }

    private static void writeVar(DataOutputStream out, int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    private static int readVar(DataInputStream in) throws IOException {
        int v = 0, shift = 0, b;
        do {
            b = in.readUnsignedByte();
            v |= (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return v;
    }

    // ------------------------------------------------------------------ reading senses

    /** A sense read from the lexicon by its number. */
    public synchronized Sense sense(int id) {
        if (id < 0 || id >= at.length) {
            return null;
        }
        try {
            if (reader == null) {
                reader = new RandomAccessFile(lexicon, "r");
            }
            reader.seek(at[id]);
            java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream(256);
            int b;
            while ((b = reader.read()) >= 0 && b != '\n') {
                line.write(b);
            }
            return new Sense(id, new String(line.toByteArray(), StandardCharsets.UTF_8).split("\t", -1));
        } catch (IOException e) {
            return null;
        }
    }

    /** Every sense of a headword as written (lower case), most common first. */
    public List<Sense> senses(String headword) {
        List<Sense> out = new ArrayList<Sense>();
        int[] h = heads.get(headword == null ? "" : headword.toLowerCase(Locale.ROOT));
        if (h == null) {
            return out;
        }
        for (int id : h) {
            Sense s = sense(id);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** Whether a word is a headword. */
    public boolean has(String headword) {
        return headword != null && heads.containsKey(headword.toLowerCase(Locale.ROOT));
    }

    /** How many senses a headword has (how common and how wide it is: a rough measure of how everyday a word is). */
    public int senseCount(String headword) {
        int[] h = heads.get(headword == null ? "" : headword.toLowerCase(Locale.ROOT));
        return h == null ? 0 : h.length;
    }

    /**
     * How everyday a word is: how often it is used across all the dictionary's definitions (a measure of how common it
     * is in plain English, counted from the 1.8 million words of the definitions themselves).
     */
    public int frequency(String word) {
        if (word == null || word.contains(" ")) {
            return 0;
        }
        return postings(inMeaning, Words.key(word)).length;
    }

    int[] postings(Map<String, int[]> m, String key) {
        int[] v = m.get(key);
        return v == null ? new int[0] : v;
    }

    /** How rare a word is among meanings (inverse document frequency): rare words tell meanings apart. */
    double idf(String key) {
        int df = postings(inMeaning, key).length + postings(asSynonym, key).length;
        return Math.log(1 + word.length / (1.0 + df));
    }

    // ------------------------------------------------------------------ growable primitive lists

    static final class IntList {
        int[] a = new int[4];
        int n;

        void add(int v) {
            if (n > 0 && a[n - 1] == v) {
                return;
            }
            if (n == a.length) {
                a = Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }

        int[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }

    static final class LongList {
        long[] a = new long[1024];
        int n;

        void add(long v) {
            if (n == a.length) {
                a = Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }

        long[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }

    static final class ByteList {
        byte[] a = new byte[1024];
        int n;

        void add(byte v) {
            if (n == a.length) {
                a = Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }

        byte[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}
