package ai.ibiko.ibiko1.neural;

import java.util.ArrayList;
import java.util.List;

/**
 * One layer of self-attention over a sentence (docs/IBIKO_NEURAL.md, N3), with identity projections, so it needs no
 * training of its own: the embedding geometry already makes words that belong together attend to each other.
 *
 * <pre>
 * score(i, j) = sqrt(d) · cos(e_i, e_j) + log weight(w_j)     (j ≠ i: rare words draw more attention)
 * a(i, ·)     = softmax over j of score(i, j)
 * h_i         = normalise(e_i + MIX · Σ_j a(i, j) e_j)          the residual keeps the word itself
 * s           = Σ_i weight(w_i) · h_i                           weight = A / (A + p(w)), p by Zipf from the rank
 * sentence    = normalise(s − (s·u) u)                          u: the direction all sentences share
 * </pre>
 *
 * "bank" beside "river" moves towards water words, and beside "loan" towards money words. Pure Java.
 */
public final class Attention {
    /** How far a word's context moves it. */
    static final float MIX = 0.6f;
    /** Smooth inverse frequency: how much a common word still counts. */
    static final double A = 1e-4;

    private final Embeddings e;

    public Attention(Embeddings e) {
        this.e = e;
    }

    /** A sentence read in context: each known word with its vector before and after attention. */
    public static final class Reading {
        public final List<String> words = new ArrayList<String>();
        public final List<float[]> plain = new ArrayList<float[]>();
        public final List<float[]> contextual = new ArrayList<float[]>();
        public final List<Double> weights = new ArrayList<Double>();
        /** The whole sentence as one vector (length 1), or null when no word of it is known. */
        public float[] sentence;

        /** The word's vector in this sentence, or null when it isn't here. */
        public float[] of(String word) {
            int i = words.indexOf(word);
            return i < 0 ? null : contextual.get(i);
        }
    }

    private volatile float[] common;

    /** The common direction: the mean of the most frequent words' vectors, weighted by how often each is used. */
    float[] common() {
        float[] u = common;
        if (u == null) {
            u = new float[e.dims];
            int top = Math.min(5000, e.vocabulary());
            for (int i = 0; i < top; i++) {
                Embeddings.add(u, e.row(i), (float) (1.0 / (i + 1)));
            }
            common = u = Embeddings.normalise(u);
        }
        return u;
    }

    /** The words that only frame a sentence or a question: they weigh almost nothing. */
    static final java.util.Set<String> FRAME = new java.util.HashSet<String>(java.util.Arrays.asList(("how much many what which who "
            + "whom whose where when why did do does done will would can could shall should may might must is are was were be been "
            + "being am have has had i you he she it we they me him her us them my your his its our their this that these those "
            + "there here some any very so just also not no yes").split(" ")));

    /** How much a word says: near 0 for "the", near 1 for a rare word. */
    public double weight(String word) {
        if (FRAME.contains(word) || ai.ibiko.ibiko1.words.Words.STOP.contains(word)) {
            return 0.02;
        }
        double p = 0.1 / (e.rank(word) + 1.0);
        return A / (A + p);
    }

    public Reading read(String text) {
        return read(Tokenizer.words(text));
    }

    public Reading read(List<String> words) {
        Reading r = new Reading();
        for (String w : words) {
            float[] v = e.vector(w);
            if (v != null) {
                r.words.add(w);
                r.plain.add(v);
                r.weights.add(weight(w));
            }
        }
        int n = r.words.size();
        if (n == 0) {
            return r;
        }
        double scale = Math.sqrt(e.dims);
        double[] score = new double[n];
        float[] sum = new float[e.dims];
        for (int i = 0; i < n; i++) {
            float[] h = r.plain.get(i).clone();
            if (n > 1) {
                double top = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < n; j++) {
                    score[j] = j == i ? Double.NEGATIVE_INFINITY
                            : scale * Embeddings.dot(r.plain.get(i), r.plain.get(j)) + Math.log(r.weights.get(j) + 1e-6);
                    top = Math.max(top, score[j]);
                }
                double z = 0;
                for (int j = 0; j < n; j++) {
                    score[j] = j == i ? 0 : Math.exp(score[j] - top);
                    z += score[j];
                }
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        Embeddings.add(h, r.plain.get(j), (float) (MIX * score[j] / z));
                    }
                }
            }
            Embeddings.normalise(h);
            r.contextual.add(h);
            Embeddings.add(sum, h, r.weights.get(i).floatValue());
        }
        // the direction every sentence shares (what common words pull towards) is taken out, so what's left is what
        // this sentence says (smooth inverse frequency with the common component removed)
        float[] u = common();
        Embeddings.add(sum, u, (float) -Embeddings.dot(sum, u));
        r.sentence = Embeddings.normalise(sum);
        return r;
    }

    /** The sentence's vector, or null when none of its words is known. */
    public float[] encode(String text) {
        return read(text).sentence;
    }

    /**
     * How well two texts' words line up: each word of one finds the word of the other closest to it in context, and
     * the closeness is averaged both ways, rarer words counting more (as BERTScore does with a transformer's
     * vectors). It answers "does this sentence say what the question asks about" better than one vector per sentence.
     */
    public double alignment(String a, String b) {
        Reading x = read(a), y = read(b);
        if (x.words.isEmpty() || y.words.isEmpty()) {
            return 0;
        }
        return (covered(x, y) + covered(y, x)) / 2;
    }

    private static double covered(Reading from, Reading to) {
        double sum = 0, weights = 0;
        for (int i = 0; i < from.words.size(); i++) {
            double best = -1;
            String stem = ai.ibiko.ibiko1.words.Words.key(from.words.get(i));
            for (int j = 0; j < to.words.size(); j++) {
                // the same word in another form ("earn", "earned") is the same idea: the vectors, built from use,
                // keep inflections apart, so the stem joins them
                double d = stem.length() > 2 && stem.equals(ai.ibiko.ibiko1.words.Words.key(to.words.get(j))) ? 1
                        : Embeddings.dot(from.contextual.get(i), to.contextual.get(j));
                best = Math.max(best, d);
            }
            double w = from.weights.get(i);
            sum += w * best;
            weights += w;
        }
        return weights == 0 ? 0 : sum / weights;
    }

    /** Meaning match for a question against a sentence: the sentence vectors and the word alignment together. */
    public double match(String question, String sentence) {
        return 0.35 * similarity(question, sentence) + 0.65 * alignment(question, sentence);
    }

    /** How alike in meaning two texts are, -1 to 1 (0 when either has no known word). */
    public double similarity(String a, String b) {
        float[] x = encode(a), y = encode(b);
        return x == null || y == null ? 0 : Embeddings.dot(x, y);
    }
}
