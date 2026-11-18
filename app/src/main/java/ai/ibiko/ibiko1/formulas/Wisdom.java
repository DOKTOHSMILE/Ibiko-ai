package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.ibiko.ibiko1.Knowledge;

/**
 * Wisdom is the application of knowledge and understanding. Knowledge is the sentences already kept.
 * Understanding is what each of those sentences implies. Wisdom does not repeat the sentences: it says
 * what to do with them for this question. Pure Java. No model.
 *
 * <pre>
 * W1  know      sentences already stored that share this question's content words
 * W2  understand what each of those sentences implies ({@link Understanding})
 * W3  apply     the move that follows from both, for this question, not a copy of the wording
 * </pre>
 */
public final class Wisdom {
    /** Knowledge, what it implies, and the application of the two. */
    public static final class Applied {
        public final String known, implies, apply;
        /** Explicit limits carried forward from the source sentences. */
        public final String assumes, doesNotSay, undone;
        public final int evidenceCount;

        Applied(String known, String implies, String apply, String assumes, String doesNotSay,
                String undone, int evidenceCount) {
            this.known = known;
            this.implies = implies;
            this.apply = apply;
            this.assumes = assumes;
            this.doesNotSay = doesNotSay;
            this.undone = undone;
            this.evidenceCount = evidenceCount;
        }

        /** What a Think level hands to the model before it answers. */
        public String brief() {
            StringBuilder b = new StringBuilder();
            b.append("ibiko1 reasoning: apply knowledge through what it implies. Do not copy an earlier sentence as the answer.\n");
            if (!known.isEmpty()) {
                b.append("Known: ").append(known).append('\n');
            }
            b.append("Implies: ").append(implies).append('\n');
            if (!assumes.isEmpty()) {
                b.append("Assumptions in the evidence: ").append(assumes).append('\n');
            }
            if (!doesNotSay.isEmpty()) {
                b.append("Does not establish: ").append(doesNotSay).append('\n');
            }
            if (!undone.isEmpty()) {
                b.append("What could change this: ").append(undone).append('\n');
            }
            b.append("Evidence sentences used: ").append(evidenceCount).append('\n');
            b.append("Apply: ").append(apply).append('\n');
            b.append("Reason: state the implication directly; a limit stays a limit, a possibility is not treated as settled, and missing evidence is not invented.");
            return b.toString();
        }
    }

    private Wisdom() {
    }

    /** W1–W3. {@code known} may be empty: then only what the question itself implies is applied. */
    public static Applied of(String question, List<String> known) {
        List<String> want = Knowledge.meaning(question);
        StringBuilder held = new StringBuilder();
        StringBuilder implies = new StringBuilder();
        StringBuilder assumes = new StringBuilder();
        StringBuilder silent = new StringBuilder();
        StringBuilder undone = new StringBuilder();
        int n = 0;
        // Rank evidence before applying it. The former first-three-match rule made the result depend on
        // storage order: an old, weakly related sentence could crowd out a newer, much closer one.
        List<Evidence> candidates = new ArrayList<Evidence>();
        List<String> sourceLines = known == null ? Collections.<String>emptyList() : known;
        List<String> uniqueLines = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String sentence : sourceLines) {
            if (sentence == null || sentence.trim().isEmpty()) continue;
            String clean = sentence.trim();
            String identity = clean.toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
            if (seen.add(identity)) uniqueLines.add(clean);
        }
        java.util.Map<String, Integer> documentFrequency = new java.util.HashMap<String, Integer>();
        for (String line : uniqueLines) {
            Set<String> once = new HashSet<String>(Knowledge.meaning(line));
            for (String token : once) {
                Integer count = documentFrequency.get(token);
                documentFrequency.put(token, count == null ? 1 : count + 1);
            }
        }
        int order = 0;
        for (String clean : uniqueLines) {
            List<String> meaning = Knowledge.meaning(clean);
            double relevance = relevance(want, meaning, documentFrequency, uniqueLines.size());
            if (!want.isEmpty() && relevance > 0.0) {
                candidates.add(new Evidence(clean, relevance, order));
            }
            order++;
        }
        Collections.sort(candidates, new Comparator<Evidence>() {
            @Override public int compare(Evidence a, Evidence b) {
                int score = Double.compare(b.score, a.score);
                return score != 0 ? score : Integer.compare(a.order, b.order);
            }
        });
        for (Evidence candidate : candidates) {
            Understanding.Implication u = Understanding.of(candidate.sentence);
            if (held.length() > 0) {
                held.append(' ');
            }
            held.append(u.sentence);
            if (implies.length() > 0) {
                implies.append(' ');
            }
            implies.append(u.follows);
            append(assumes, u.assumes);
            append(silent, u.silent);
            append(undone, u.undone);
            if (++n >= 3) {
                break;
            }
        }
        if (n == 0) {
            Understanding.Implication u = Understanding.of(question == null ? "" : question);
            return new Applied("", u.follows,
                    "Nothing stored mentions these details. Apply only what this question itself implies, and say so.",
                    u.assumes, u.silent, u.undone, 0);
        }
        return new Applied(held.toString().trim(), implies.toString().trim(),
                "Use those implications on this question. A limit they stated is kept. A possibility is not treated as settled."
                        + " The answer follows from that, not from repeating the stored words.",
                assumes.toString().trim(), silent.toString().trim(), undone.toString().trim(), n);
    }

    private static void append(StringBuilder target, String value) {
        if (value != null && !value.trim().isEmpty()) {
            if (target.length() > 0) target.append(" ");
            target.append(value.trim());
        }
    }

    /**
     * IDF-weighted Dice relevance: rare query terms carry more weight than words repeated across the evidence set.
     * It still rewards both query coverage and evidence precision, and remains a retrieval heuristic rather than
     * semantic proof or a truth score.
     */
    private static double relevance(List<String> want, List<String> have,
            java.util.Map<String, Integer> documentFrequency, int documentCount) {
        if (want == null || have == null || want.isEmpty() || have.isEmpty()) return 0.0;
        Set<String> q = new HashSet<String>(want);
        Set<String> h = new HashSet<String>(have);
        double qWeight = 0.0, hWeight = 0.0, sharedWeight = 0.0;
        for (String word : q) qWeight += idf(word, documentFrequency, documentCount);
        for (String word : h) hWeight += idf(word, documentFrequency, documentCount);
        for (String word : q) {
            if (h.contains(word)) sharedWeight += idf(word, documentFrequency, documentCount);
        }
        if (sharedWeight == 0.0 || qWeight + hWeight == 0.0) return 0.0;
        return (2.0 * sharedWeight) / (qWeight + hWeight);
    }

    private static double idf(String word, java.util.Map<String, Integer> df, int documents) {
        int total = Math.max(1, documents);
        Integer seen = df == null ? null : df.get(word);
        int frequency = seen == null ? 0 : seen;
        return 1.0d + Math.log((total + 1.0d) / (frequency + 1.0d));
    }

    private static final class Evidence {
        final String sentence;
        final double score;
        final int order;
        Evidence(String sentence, double score, int order) {
            this.sentence = sentence;
            this.score = score;
            this.order = order;
        }
    }

    /** The sentences worth handing to {@link #of}, dropping blanks. */
    public static List<String> lines(String... sentences) {
        List<String> out = new ArrayList<String>();
        if (sentences == null) {
            return out;
        }
        for (String s : sentences) {
            if (s != null && !s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** The ibiko1 reasoning module's own working brief, used by every chat level. */
    public static String brief(String question, List<String> known) {
        return of(question, known).brief();
    }
}
