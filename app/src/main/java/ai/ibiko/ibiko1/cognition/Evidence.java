package ai.ibiko.ibiko1.cognition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Truth;

/**
 * Engine 1, evidence and truth evaluation (IBIKO_COGNITIVE_LEARNING.md, priority 1), built on the foundation of
 * {@link Truth}: its source ranks are the reliability of each piece of evidence. What this adds: what kind of claim a
 * sentence is (a fact, an inference, an assumption, a hypothesis, a preference, or unknown), each piece of evidence with
 * an id, a source, a time and where it came from, how many of them are truly independent (three copies of one article
 * are one source), whether a time-sensitive claim is still fresh, and a verdict with a confidence that is never more
 * than the evidence allows. An assumption stays an assumption however often it is repeated. Pure Java.
 *
 * <pre>
 * E1  type         FACT, INFERENCE ("so", "therefore"), ASSUMPTION ("assume", "I think"), HYPOTHESIS ("might",
 *                  "could"), PREFERENCE ("I prefer", "I like"), UNKNOWN (a question, "no one knows", nothing claimed)
 * E2  evidence     id, source (a {@link Truth.Source}: its rank is its reliability), origin (a title, an address),
 *                  the words, when it was seen
 * E3  independent  evidence from one origin, or near the same words (most of their word pairs shared), counts once
 * E4  fresh        prices, office-holders, populations, versions, "now", "latest": time-sensitive; older than 30 days
 *                  is STALE and is marked for checking
 * E5  verdict      SUPPORTED, CONTESTED (evidence as reliable says otherwise), UNSUPPORTED (no evidence), UNKNOWN;
 *                  confidence = the most reliable support, a little more for each independent agreement, less for
 *                  staleness, at most 0.5 when contested or when the claim is an assumption or a hypothesis
 * </pre>
 */
public final class Evidence {
    private Evidence() {
    }

    public enum Type { FACT, INFERENCE, ASSUMPTION, HYPOTHESIS, PREFERENCE, UNKNOWN }

    public enum Freshness { CURRENT, TIME_SENSITIVE, STALE, TIMELESS }

    public enum Status { SUPPORTED, CONTESTED, UNSUPPORTED, UNKNOWN }

    /** One piece of evidence. */
    public static final class Item {
        public final String id, origin, text;
        public final Truth.Source source;
        public final long at;

        public Item(String id, Truth.Source source, String origin, String text, long at) {
            this.id = id;
            this.source = source == null ? Truth.Source.MODEL : source;
            this.origin = origin == null ? "" : origin.trim().toLowerCase(Locale.ROOT);
            this.text = text == null ? "" : text;
            this.at = at;
        }
    }

    /** The verdict on one claim. */
    public static final class Evaluation {
        public final String claim;
        public final Type type;
        public final List<String> evidenceIds;
        public final double confidence;
        public final List<String> conflicts;
        public final Freshness freshness;
        public final Status status;
        /** How many independent origins support it. */
        public final int independent;

        Evaluation(String claim, Type type, List<String> evidenceIds, double confidence, List<String> conflicts, Freshness freshness,
                Status status, int independent) {
            this.claim = claim;
            this.type = type;
            this.evidenceIds = evidenceIds;
            this.confidence = confidence;
            this.conflicts = conflicts;
            this.freshness = freshness;
            this.status = status;
            this.independent = independent;
        }

        /** The record as the specification shows it. */
        public String json() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("claim", claim);
            m.put("type", type.name());
            m.put("evidence_ids", new ArrayList<Object>(evidenceIds));
            m.put("confidence", Math.round(confidence * 100) / 100.0);
            m.put("conflicts", new ArrayList<Object>(conflicts));
            m.put("freshness", freshness.name());
            m.put("status", status.name());
            return ai.ibiko.ibiko1.Json.write(m);
        }
    }

    // ------------------------------------------------------------------ E1 type

    private static final Pattern PREFERENCE = Pattern.compile("(?i)\\b(?:i|we)\\s+(?:really\\s+)?(?:prefer|like|love|hate|dislike|enjoy|want|would rather|'d rather)\\b"
            + "|\\bmy\\s+(?:favou?rite|preference)\\b|\\b(?:i|we)\\s+(?:don't|do not)\\s+(?:like|want)\\b");
    private static final Pattern ASSUMPTION = Pattern.compile("(?i)\\b(?:assum(?:e|ing|ption)|suppos(?:e|ing)|let's say|let us say|say that|presumably|"
            + "i think|i believe|i guess|i reckon|i suspect|i expect|taking it that|if we take)\\b");
    private static final Pattern HYPOTHESIS = Pattern.compile("(?i)\\b(?:might|may|could|perhaps|possibly|maybe|it is possible|it's possible|"
            + "hypothes[ie]s|one explanation|likely because|would explain)\\b");
    private static final Pattern INFERENCE = Pattern.compile("(?i)(?:^|\\b)(?:so|therefore|thus|hence|consequently|which means|this means|that means|"
            + "it follows|must be|must have|as a result)\\b");
    private static final Pattern UNKNOWNS = Pattern.compile("(?i)\\b(?:unknown|not known|no one knows|nobody knows|unclear|uncertain|can't tell|cannot tell|"
            + "isn't known|is not known)\\b");

    /** E1: what kind of claim a sentence makes. */
    public static Type type(String sentence) {
        String s = sentence == null ? "" : sentence.trim();
        if (s.isEmpty() || s.endsWith("?") || UNKNOWNS.matcher(s).find()) {
            return Type.UNKNOWN;
        }
        if (PREFERENCE.matcher(s).find()) {
            return Type.PREFERENCE;
        }
        if (ASSUMPTION.matcher(s).find()) {
            return Type.ASSUMPTION;
        }
        if (HYPOTHESIS.matcher(s).find()) {
            return Type.HYPOTHESIS;
        }
        if (INFERENCE.matcher(s).find()) {
            return Type.INFERENCE;
        }
        return s.split("\\s+").length >= 3 ? Type.FACT : Type.UNKNOWN;
    }

    // ------------------------------------------------------------------ E2 reliability

    /** A source's rank as a reliability between 0 and 1. */
    public static double reliability(Truth.Source s) {
        switch (s) {
            case PILOT: return 1.0;
            case CONFIRMED: return 0.95;
            case BUILT_IN: return 0.9;
            case SOURCE: return 0.75;
            case LEARNED: return 0.55;
            default: return 0.4;
        }
    }

    // ------------------------------------------------------------------ E3 independent

    /** E3: the evidence grouped by origin: one group per truly independent source. */
    public static List<List<Item>> origins(List<Item> items) {
        List<List<Item>> groups = new ArrayList<List<Item>>();
        for (Item it : items) {
            List<Item> home = null;
            for (List<Item> g : groups) {
                for (Item other : g) {
                    if (!it.origin.isEmpty() && it.origin.equals(other.origin) || nearSame(it.text, other.text)) {
                        home = g;
                        break;
                    }
                }
                if (home != null) {
                    break;
                }
            }
            if (home == null) {
                home = new ArrayList<Item>();
                groups.add(home);
            }
            home.add(it);
        }
        return groups;
    }

    /** Two texts that are mostly the same words in the same order (a copy, a repost, a lightly edited copy). */
    public static boolean nearSame(String a, String b) {
        Set<String> x = pairs(a), y = pairs(b);
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        Set<String> both = new HashSet<String>(x);
        both.retainAll(y);
        Set<String> any = new HashSet<String>(x);
        any.addAll(y);
        return both.size() >= 0.7 * any.size();
    }

    private static Set<String> pairs(String t) {
        String[] w = (t == null ? "" : t).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").trim().split("\\s+");
        Set<String> out = new HashSet<String>();
        for (int i = 0; i + 1 < w.length; i++) {
            out.add(w[i] + " " + w[i + 1]);
        }
        return out;
    }

    // ------------------------------------------------------------------ E4 fresh

    private static final Pattern TIME_SENSITIVE = Pattern.compile("(?i)\\b(?:price|prices|costs?|rate|exchange rate|current(?:ly)?|now|today|latest|newest|"
            + "this (?:year|month|week)|president|governor|prime minister|minister|ceo|chairman|head of|leader|population|version|release|"
            + "weather|score|ranking|record|holder|champion|owner|salary|inflation|unemployment)\\b");
    static final long FRESH_MS = 30L * 24 * 3600 * 1000;

    /** E4: whether a claim changes over time, and whether the newest evidence for it is still fresh. */
    public static Freshness freshness(String claim, long newest, long now) {
        if (claim == null || !TIME_SENSITIVE.matcher(claim).find()) {
            return Freshness.TIMELESS;
        }
        if (newest <= 0) {
            return Freshness.TIME_SENSITIVE;
        }
        return now - newest <= FRESH_MS ? Freshness.CURRENT : Freshness.STALE;
    }

    // ------------------------------------------------------------------ E5 verdict

    /**
     * E5: a claim weighed against the evidence for it and against it. Confidence never exceeds what the most reliable
     * support allows; repeats from one origin add nothing; an assumption or hypothesis is never made a supported fact.
     */
    public static Evaluation evaluate(String claim, List<Item> support, List<Item> against, long now) {
        Type type = type(claim);
        List<Item> sup = support == null ? new ArrayList<Item>() : support;
        List<Item> con = against == null ? new ArrayList<Item>() : against;
        List<String> ids = new ArrayList<String>();
        long newest = 0;
        double best = 0;
        for (Item it : sup) {
            ids.add(it.id);
            newest = Math.max(newest, it.at);
            best = Math.max(best, reliability(it.source));
        }
        double bestAgainst = 0;
        List<String> conflicts = new ArrayList<String>();
        for (Item it : con) {
            conflicts.add(it.id);
            bestAgainst = Math.max(bestAgainst, reliability(it.source));
        }
        int independent = origins(sup).size();
        Freshness fresh = freshness(claim, newest, now);
        Status status;
        double c;
        if (type == Type.UNKNOWN && sup.isEmpty()) {
            status = Status.UNKNOWN;
            c = 0;
        } else if (sup.isEmpty()) {
            status = Status.UNSUPPORTED;
            c = con.isEmpty() ? 0.2 : 0.05;
        } else if (!con.isEmpty() && bestAgainst >= best) {
            status = Status.CONTESTED;
            c = Math.min(0.5, best * 0.5);
        } else {
            status = Status.SUPPORTED;
            c = Math.min(0.98, best + 0.04 * (independent - 1));
            if (!con.isEmpty()) {
                c = Math.min(c, 0.75); // a weaker source disagrees: noted, not ignored
            }
        }
        if (fresh == Freshness.STALE) {
            c *= 0.8;
        }
        if (type == Type.ASSUMPTION || type == Type.HYPOTHESIS) {
            c = Math.min(c, 0.5); // however often it is said, it stays what it is
            if (status == Status.SUPPORTED) {
                status = Status.UNSUPPORTED;
            }
        }
        return new Evaluation(claim, type, ids, c, conflicts, fresh, status, independent);
    }
}
