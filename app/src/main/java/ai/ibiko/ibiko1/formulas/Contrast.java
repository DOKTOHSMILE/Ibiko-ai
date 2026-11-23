package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Telling two things apart from what is read about each: "what's the difference between a frog and a toad?" is
 * worked out, not looked up. Each text is read for what kind of thing it is (its class: "an amphibian", "a
 * reptile") and what is said of it; the classes are compared (the same kind, or different kinds), then what each
 * text says that the other doesn't: the details that set it apart. Pure Java.
 *
 * <pre>
 * D1  asks     "difference between X and Y", "X vs Y", "compare X and/with Y", "how is X different from Y",
 *              "is X the same as Y", "X or Y: what's the difference"
 * D2  kind     the first "X is a/an … KIND (that|which|with|of|in|…)" sentence: the kind and the rest said of it
 * D3  apart    the sentences of each text with the most words the other text lacks: what sets it apart
 * D4  says     both of a kind, or each its own kind; then each one's own details
 * </pre>
 */
public final class Contrast {
    private Contrast() {
    }

    /** D1: the two things a question asks to tell apart, or null when it doesn't. */
    public static String[] pair(String question) {
        String q = question == null ? "" : question.trim().replaceAll("[?.!\\s]+$", "");
        String[] forms = {
            "(?i)^(?:what(?:'s| is| are)|tell me|explain|whats)?\\s*(?:the\\s+)?(?:main\\s+|key\\s+)?differences?\\s+between\\s+(.+?)\\s+and\\s+(.+)$",
            "(?i)^(?:what(?:'s| is)\\s+)?(?:the\\s+)?differences?\\s+(?:of|in)\\s+(.+?)\\s+(?:and|vs\\.?|versus)\\s+(.+)$",
            "(?i)^(?:compare|contrast)\\s+(.+?)\\s+(?:and|with|to|vs\\.?|versus)\\s+(.+)$",
            "(?i)^how (?:is|are|does|do)\\s+(.+?)\\s+(?:different from|differ from|unlike|compare (?:to|with))\\s+(.+)$",
            "(?i)^(?:is|are)\\s+(.+?)\\s+(?:the same as|different from|a kind of|a type of)\\s+(.+)$",
            "(?i)^(.+?)\\s+(?:vs\\.?|versus)\\s+(.+?)(?:[,:]?\\s*what(?:'s| is) the difference)?$",
            "(?i)^(.+?)\\s+or\\s+(.+?)[,:]\\s*what(?:'s| is) the difference$"};
        for (String f : forms) {
            Matcher m = Pattern.compile(f).matcher(q);
            if (m.matches()) {
                String a = bare(m.group(1)), b = bare(m.group(2));
                if (!a.isEmpty() && !b.isEmpty() && !a.equalsIgnoreCase(b) && a.split(" ").length <= 6 && b.split(" ").length <= 6) {
                    return new String[] {a, b};
                }
            }
        }
        return null;
    }

    private static String bare(String s) {
        return s.trim().replaceFirst("(?i)^(a|an|the)\\s+", "").replaceAll("[,;:]+$", "").trim();
    }

    /** D2: what kind of thing a text says it is, and the rest of that sentence. */
    public static final class Kind {
        /** The kind as said ("an amphibian", "a large reptile"), its head word ("amphibian"), the rest ("with long legs"). */
        public final String said, head, rest, sentence;

        Kind(String said, String head, String rest, String sentence) {
            this.said = said;
            this.head = head;
            this.rest = rest;
            this.sentence = sentence;
        }
    }

    private static final Pattern IS_A = Pattern.compile("(?i)^(.{2,80}?)\\s+(?:\\([^)]*\\)\\s+)?(?:is|are|was|were)\\s+(?:also\\s+)?"
            + "(a|an|the|one of the|any of the|a kind of|a type of|a group of|a family of|a member of the)\\s+(.+)$");
    private static final Pattern KIND_END = Pattern.compile("(?i)\\s(that|which|who|whose|with|of|in|from|for|by|used|found|living|made|"
            + "where|when|known|called|native|between|on|at|to|and|or|but)\\b|,|;|\\(");

    /** D2: the kind from the first sentence that says what the thing is; null when none does. */
    public static Kind kind(String text) {
        for (String s : sentences(text, 4)) {
            Matcher m = IS_A.matcher(s.replaceAll("[.!]+$", ""));
            if (!m.matches()) {
                continue;
            }
            String det = m.group(2).toLowerCase(Locale.US);
            String pred = m.group(3).trim();
            Matcher end = KIND_END.matcher(pred);
            int at = end.find() ? end.start() : pred.length();
            String kind = pred.substring(0, at).trim();
            String rest = pred.substring(at).replaceFirst("^[,;\\s]+", "").trim();
            if (kind.isEmpty() || kind.split(" ").length > 5) {
                continue;
            }
            String[] w = kind.split("\\s+");
            String head = w[w.length - 1].toLowerCase(Locale.US);
            String said = (det.matches("a|an") ? det + " " : det.startsWith("the") ? "" : det + " ") + kind;
            return new Kind(said.trim(), head, rest, s);
        }
        return null;
    }

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList("the", "a", "an", "of", "to", "in", "on", "at", "for", "and",
            "or", "but", "is", "are", "was", "were", "be", "been", "it", "its", "they", "their", "them", "this", "that", "these", "those",
            "with", "by", "from", "as", "which", "who", "has", "have", "had", "can", "may", "also", "other", "some", "many", "most",
            "more", "very", "such", "than", "into", "about", "there", "not", "all", "one", "two", "usually", "often", "called", "known",
            "kind", "type", "group", "family", "member", "people", "used", "like", "only", "same", "different", "both"));

    static List<String> sentences(String text, int most) {
        List<String> out = new ArrayList<String>();
        for (String s : (text == null ? "" : text.trim()).split("(?<=[.!?])\\s+(?=[A-Z\"“(])")) {
            if (s.trim().length() > 10) {
                out.add(s.trim());
            }
            if (out.size() >= most) {
                break;
            }
        }
        return out;
    }

    static Set<String> words(String s) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : (s == null ? "" : s).toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                out.add(Causes.stem(w));
            }
        }
        return out;
    }

    /** D3: the sentence of {@code text} that most says what {@code other} doesn't (not the one already used), or null. */
    static String apart(String text, String other, String title, String otherTitle, Set<String> used) {
        Set<String> theirs = words(other);
        theirs.addAll(words(otherTitle));
        Set<String> own = words(title);
        String best = null;
        double bestScore = 0;
        for (String s : sentences(text, 6)) {
            if (used.contains(s)) {
                continue;
            }
            Set<String> w = words(s);
            w.removeAll(own);
            if (w.size() < 2) {
                continue;
            }
            int distinct = 0;
            for (String x : w) {
                distinct += theirs.contains(x) ? 0 : 1;
            }
            // most of its words the other text lacks, and not a long ramble
            double score = distinct / (double) w.size() * Math.min(1.0, 25.0 / s.split("\\s+").length) + 0.05 * Math.min(distinct, 6);
            if (distinct >= 2 && score > bestScore) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    /** The answer: kinds compared, then what sets each apart; null when either text doesn't say what it is. */
    public static String tell(String nameA, String textA, String nameB, String textB) {
        Kind a = kind(textA), b = kind(textB);
        if (a == null || b == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        String sameHead = Causes.stem(a.head).equals(Causes.stem(b.head)) ? a.head : null;
        if (sameHead != null) {
            out.append("Both are ").append(plural(sameHead)).append(", but they differ.");
        } else {
            out.append(cap(nameA)).append(" is ").append(a.said).append("; ").append(nameB).append(" is ").append(b.said).append(".");
        }
        Set<String> used = new HashSet<String>();
        out.append("\n\n• ").append(cap(nameA)).append(": ").append(detail(a, textA, textB, nameA, nameB, used));
        out.append("\n• ").append(cap(nameB)).append(": ").append(detail(b, textB, textA, nameB, nameA, used));
        return out.toString();
    }

    private static String detail(Kind k, String text, String other, String name, String otherName, Set<String> used) {
        List<String> parts = new ArrayList<String>();
        if (!k.rest.isEmpty()) {
            parts.add(k.said + " " + k.rest.replaceAll("[.!]+$", "") + ".");
        } else {
            parts.add(k.said + ".");
        }
        used.add(k.sentence);
        String own = apart(text, other, name, otherName, used);
        if (own != null) {
            used.add(own);
            parts.add(own.endsWith(".") || own.endsWith("!") ? own : own + ".");
        }
        String first = parts.get(0);
        parts.set(0, Character.toUpperCase(first.charAt(0)) + first.substring(1));
        return String.join(" ", parts);
    }

    static String plural(String w) {
        if (w.matches("(?i).*(s|sh|ch|x|z)$")) {
            return w + "es";
        }
        if (w.matches("(?i).*[^aeiou]y$")) {
            return w.substring(0, w.length() - 1) + "ies";
        }
        return w + "s";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
