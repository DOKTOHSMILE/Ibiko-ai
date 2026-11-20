package ai.ibiko.ibiko1.neural;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ibiko v1's word vectors (docs/IBIKO_NEURAL.md, N2): meaning as geometry. Built by tools/build_neural.py from its own
 * text (co-occurrence, positive PMI, SVD to 128 dimensions), so words used alike sit close together: "doctor" near
 * "physician" and "nurse", "river" near "lake" and "stream". Kept as one signed byte per value with a scale per row
 * (about 8 MB for 50,000 words and the tokenizer's pieces). A word outside the vocabulary gets the mean of its BPE
 * pieces' vectors, so every word has one. Pure Java.
 */
public final class Embeddings {
    public final int dims;
    private final int words;
    private final String[] names;
    private final byte[] values;
    private final float[] scales;
    private final Map<String, Integer> index;
    private final Tokenizer tokenizer;

    private Embeddings(int dims, int words, String[] names, byte[] values, float[] scales, Tokenizer tokenizer) {
        this.dims = dims;
        this.words = words;
        this.names = names;
        this.values = values;
        this.scales = scales;
        this.tokenizer = tokenizer;
        index = new HashMap<String, Integer>(names.length * 2);
        for (int i = 0; i < names.length; i++) {
            index.put(names[i], i);
        }
    }

    /** Reads vectors.ibk ("IBKN", version 1). */
    public static Embeddings read(InputStream in, Tokenizer tokenizer) throws IOException {
        DataInputStream d = new DataInputStream(new java.io.BufferedInputStream(in, 1 << 16));
        byte[] magic = new byte[4];
        d.readFully(magic);
        if (!"IBKN".equals(new String(magic, StandardCharsets.US_ASCII)) || d.readUnsignedByte() != 1) {
            throw new IOException("not Ibiko vectors");
        }
        int dims = Tokenizer.varint(d), words = Tokenizer.varint(d), pieces = Tokenizer.varint(d);
        int n = words + pieces;
        String[] names = new String[n];
        byte[] values = new byte[n * dims];
        float[] scales = new float[n];
        for (int i = 0; i < n; i++) {
            names[i] = Tokenizer.string(d);
            scales[i] = d.readFloat();
            d.readFully(values, i * dims, dims);
        }
        return new Embeddings(dims, words, names, values, scales, tokenizer);
    }

    /** How many whole words have their own vector. */
    public int vocabulary() {
        return words;
    }

    /** Whether the word has a vector of its own (not one made from its pieces). */
    public boolean knows(String word) {
        Integer i = index.get(word);
        return i != null && i < words;
    }

    /**
     * The word's place by frequency (0 the most frequent); words outside the vocabulary count as rarer than all.
     * Attention uses it to weigh rare words above common ones.
     */
    public int rank(String word) {
        Integer i = index.get(word);
        return i != null && i < words ? i : words;
    }

    /** The word's vector, length 1; made from its pieces when it isn't in the vocabulary; null if nothing is known. */
    public float[] vector(String word) {
        Integer i = index.get(word);
        if (i != null && i < words) {
            return row(i);
        }
        if (tokenizer == null) {
            return null;
        }
        float[] sum = new float[dims];
        boolean any = false;
        // the longest known word it starts with (its stem: "footballish" → "football") says the most
        for (int end = word.length() - 1; end >= 4 && end * 2 >= word.length(); end--) {
            Integer j = index.get(word.substring(0, end));
            if (j != null && j < words) {
                add(sum, row(j), 3f);
                any = true;
                break;
            }
        }
        // then its pieces, a longer piece saying more than a short one
        for (String p : tokenizer.pieces(word)) {
            Integer j = index.get("##" + p);
            if (j != null) {
                add(sum, row(j), p.replace(Tokenizer.END, "").length());
                any = true;
            }
        }
        return any ? normalise(sum) : null;
    }

    float[] row(int i) {
        float[] v = new float[dims];
        float s = scales[i];
        int at = i * dims;
        for (int k = 0; k < dims; k++) {
            v[k] = values[at + k] * s;
        }
        return normalise(v);
    }

    /** Cosine similarity of two words, from -1 to 1; 0 when either is unknown. */
    public double similarity(String a, String b) {
        float[] x = vector(a), y = vector(b);
        return x == null || y == null ? 0 : dot(x, y);
    }

    /** The k words closest in meaning to the vector, best first, skipping those given. */
    public List<String> nearest(float[] v, int k, java.util.Collection<String> skip) {
        int[] best = new int[k];
        double[] score = new double[k];
        java.util.Arrays.fill(best, -1);
        java.util.Arrays.fill(score, -2);
        for (int i = 0; i < words; i++) {
            double s = 0;
            int at = i * dims;
            for (int d = 0; d < dims; d++) {
                s += values[at + d] * v[d];
            }
            s *= scales[i] / norm(i);
            if (s > score[k - 1] && (skip == null || !skip.contains(names[i]))) {
                int p = k - 1;
                while (p > 0 && score[p - 1] < s) {
                    score[p] = score[p - 1];
                    best[p] = best[p - 1];
                    p--;
                }
                score[p] = s;
                best[p] = i;
            }
        }
        List<String> out = new ArrayList<String>();
        for (int b : best) {
            if (b >= 0) {
                out.add(names[b]);
            }
        }
        return out;
    }

    private float[] norms;

    private float norm(int i) {
        if (norms == null) {
            float[] n = new float[words];
            for (int r = 0; r < words; r++) {
                double s = 0;
                for (int d = 0; d < dims; d++) {
                    double x = values[r * dims + d] * scales[r];
                    s += x * x;
                }
                n[r] = (float) Math.max(1e-8, Math.sqrt(s));
            }
            norms = n;
        }
        return norms[i];
    }

    /** The k words closest to the word, best first. */
    public List<String> nearest(String word, int k) {
        float[] v = vector(word);
        return v == null ? new ArrayList<String>() : nearest(v, k, java.util.Collections.singleton(word));
    }

    public Tokenizer tokenizer() {
        return tokenizer;
    }

    static double dot(float[] a, float[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    static void add(float[] to, float[] v, float w) {
        for (int i = 0; i < to.length; i++) {
            to[i] += v[i] * w;
        }
    }

    static float[] normalise(float[] v) {
        double s = 0;
        for (float x : v) {
            s += x * x;
        }
        if (s <= 0) {
            return v;
        }
        float k = (float) (1 / Math.sqrt(s));
        for (int i = 0; i < v.length; i++) {
            v[i] *= k;
        }
        return v;
    }
}
