package ai.ibiko.ibiko1.neural;

import java.io.InputStream;
import java.util.List;

/**
 * Ibiko v1's neural layer in one place (docs/IBIKO_NEURAL.md): the subword tokenizer, the word vectors and the
 * attention encoder, loaded once from the app's knowledge/neural files. {@link #get()} is null when the app carries
 * none, and everything that uses it falls back to the rules and the dictionary.
 */
public final class Neural {
    public final Tokenizer tokenizer;
    public final Embeddings embeddings;
    public final Attention attention;

    public Neural(Tokenizer tokenizer, Embeddings embeddings) {
        this.tokenizer = tokenizer;
        this.embeddings = embeddings;
        attention = new Attention(embeddings);
    }

    private static volatile Neural loaded;
    private static volatile boolean tried;

    /** The layer from the app's files, loaded on first use (a few hundred ms, off the main thread); null if absent. */
    public static Neural get() {
        if (!tried) {
            synchronized (Neural.class) {
                if (!tried) {
                    loaded = load();
                    // tried again later while the app's files aren't there yet (they are plugged in at start)
                    tried = loaded != null;
                }
            }
        }
        return loaded;
    }

    /** Forgets the loaded layer, for tests that change the app's files. */
    public static synchronized void reset() {
        loaded = null;
        tried = false;
    }

    private static Neural load() {
        try (InputStream b = ai.ibiko.ibiko1.Plug.asset.apply("knowledge/neural/bpe.ibk")) {
            if (b == null) {
                return null;
            }
            Tokenizer t = Tokenizer.read(b);
            try (InputStream v = ai.ibiko.ibiko1.Plug.asset.apply("knowledge/neural/vectors.ibk")) {
                return v == null ? null : new Neural(t, Embeddings.read(v, t));
            }
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** How alike in meaning two texts are, -1 to 1. */
    public double similarity(String a, String b) {
        return attention.similarity(a, b);
    }

    /** Words close in meaning to the word, best first. */
    public List<String> near(String word, int k) {
        return embeddings.nearest(word.toLowerCase(java.util.Locale.ROOT), k);
    }
}
