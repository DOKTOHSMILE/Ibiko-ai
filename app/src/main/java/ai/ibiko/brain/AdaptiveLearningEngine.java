package ai.ibiko.brain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Conservative, on-device learning over the user's existing LearningJournal.
 * It does not train or modify model weights. Explicit user corrections outrank
 * older taught facts for the same key; retrieved evidence is ranked by lexical
 * relevance, source type and age, with its provenance kept visible.
 */
public final class AdaptiveLearningEngine {
    private static final long HALF_LIFE_MS = 180L * 24L * 60L * 60L * 1000L;
    private static final Set<String> STOP;
    static {
        Set<String> s = new HashSet<String>();
        Collections.addAll(s, "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "of", "in", "on", "at", "to", "for", "from", "by", "with", "and", "or", "but",
                "what", "who", "where", "when", "why", "how", "which", "do", "does", "did", "can",
                "could", "would", "should", "i", "you", "we", "they", "it", "this", "that", "me", "my");
        STOP = Collections.unmodifiableSet(s);
    }

    public static final class Evidence {
        public final String entryId, kind, sentence;
        public final double relevance, confidence;
        public final long createdAt;
        Evidence(String id, String kind, String sentence, double relevance, double confidence, long createdAt) {
            this.entryId = id; this.kind = kind; this.sentence = sentence;
            this.relevance = relevance; this.confidence = confidence; this.createdAt = createdAt;
        }
        public String promptLine() {
            String origin = LearningJournal.CORRECTION.equals(kind) ? "explicit user correction" : "user-taught memory";
            return "[" + origin + "; derived confidence " + percent(confidence) + "; relevance "
                    + percent(relevance) + "] " + sentence;
        }
    }

    private final LearningJournal journal;
    public AdaptiveLearningEngine(LearningJournal journal) {
        if (journal == null) throw new IllegalArgumentException("journal is required");
        this.journal = journal;
    }

    /** Stores a correction explicitly supplied by the user; never inferred from model output. */
    public LearningJournal.Entry recordCorrection(String subject, String value) {
        if (subject == null || subject.trim().isEmpty() || value == null || value.trim().isEmpty()) return null;
        return journal.add(LearningJournal.CORRECTION, subject.trim(), value.trim());
    }

    /** Records answer feedback for later evaluation; feedback is not promoted to factual knowledge. */
    public LearningJournal.Entry recordFeedback(String question, String answer, boolean helpful) {
        if (question == null || question.trim().isEmpty()) return null;
        return journal.add(LearningJournal.FEEDBACK, question.trim(),
                (helpful ? "HELPFUL: " : "NOT_HELPFUL: ") + (answer == null ? "" : answer.trim()));
    }

    /** Best matching, non-superseded evidence. No evidence is returned for a zero-overlap query. */
    public synchronized List<Evidence> retrieve(String query, int max, long now) {
        if (max <= 0 || query == null || query.trim().isEmpty()) return Collections.emptyList();
        Set<String> wanted = tokens(query);
        if (wanted.isEmpty()) return Collections.emptyList();
        List<LearningJournal.Entry> all = journal.all();

        // The newest explicit correction is authoritative over earlier entries for exactly the same subject key.
        Map<String, Integer> latestCorrection = new HashMap<String, Integer>();
        for (int i = 0; i < all.size(); i++) {
            LearningJournal.Entry e = all.get(i);
            if (LearningJournal.CORRECTION.equals(e.kind)) latestCorrection.put(LearningJournal.normalizeKey(e.text), i);
        }

        List<Evidence> found = new ArrayList<Evidence>();
        for (int i = 0; i < all.size(); i++) {
            LearningJournal.Entry e = all.get(i);
            if (!LearningJournal.TAUGHT_FACT.equals(e.kind) && !LearningJournal.CORRECTION.equals(e.kind)) continue;
            String key = LearningJournal.normalizeKey(e.text);
            Integer correctionIndex = latestCorrection.get(key);
            if (correctionIndex != null && i < correctionIndex.intValue()) continue;

            Set<String> terms = tokens(e.text + " " + e.answer);
            int shared = 0;
            for (String term : terms) if (wanted.contains(term)) shared++;
            if (shared == 0) continue;
            double relevance = (double) shared / Math.max(1, wanted.size());
            double age = Math.max(0L, now - e.createdAt);
            double freshness = 0.85d + 0.15d * Math.exp(-Math.log(2d) * age / HALF_LIFE_MS);
            double sourceWeight = LearningJournal.CORRECTION.equals(e.kind) ? 0.98d : 0.86d;
            double confidence = clamp(sourceWeight * freshness, 0.50d, 0.99d);
            found.add(new Evidence(e.id, e.kind, LearningJournal.sentence(e.text, e.answer),
                    clamp(relevance, 0d, 1d), confidence, e.createdAt));
        }
        found.sort(new Comparator<Evidence>() {
            @Override public int compare(Evidence a, Evidence b) {
                int c = Double.compare(b.relevance * b.confidence, a.relevance * a.confidence);
                if (c != 0) return c;
                return Long.compare(b.createdAt, a.createdAt);
            }
        });
        if (found.size() > max) return new ArrayList<Evidence>(found.subList(0, max));
        return found;
    }

    public List<String> sentences(String query, int max, long now) {
        List<String> out = new ArrayList<String>();
        for (Evidence e : retrieve(query, max, now)) out.add(e.sentence);
        return out;
    }

    /** Evidence-aware prompt context. It explicitly forbids treating derived confidence as proof. */
    public String promptContext(String query, int max, long now) {
        List<Evidence> evidence = retrieve(query, max, now);
        if (evidence.isEmpty()) return "Adaptive memory: no matching user-taught evidence was found.";
        StringBuilder b = new StringBuilder("Adaptive memory evidence (user-provided; not independently verified):\n");
        for (Evidence e : evidence) b.append("- ").append(e.promptLine()).append('\n');
        b.append("Use corrections over older memories for the same subject. Confidence is a retrieval heuristic, not a probability of truth. Ask for clarification when evidence conflicts or is incomplete.");
        return b.toString();
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<String>();
        if (text == null) return out;
        for (String raw : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}' ]", " ").split("\\s+")) {
            if (raw.length() > 1 && !STOP.contains(raw)) out.add(raw);
        }
        return out;
    }
    private static double clamp(double x, double lo, double hi) { return Math.max(lo, Math.min(hi, x)); }
    private static String percent(double x) { return Integer.toString((int) Math.round(x * 100d)) + "%"; }
}
