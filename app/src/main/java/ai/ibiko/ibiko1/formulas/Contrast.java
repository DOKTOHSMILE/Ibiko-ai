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
                if (m.start(1) == 0 && !a.contains(" ") && Character.isLowerCase(b.charAt(0))) {
                    a = a.toLowerCase(Locale.US); // "Moth or butterfly": the capital is the sentence's, not a name's
                }
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
        /** Said of them all, without "a": "Moths are insects". */
        public boolean all;

        Kind(String said, String head, String rest, String sentence) {
            this.said = said;
            this.head = head;
            this.rest = rest;
            this.sentence = sentence;
        }
    }

    private static final Pattern IS_A = Pattern.compile("(?i)^(.{2,80}?)\\s+(?:\\([^)]*\\)\\s+)?(?:is|are|was|were)\\s+(?:also\\s+)?"
            + "(?:(a|an|the|one of the|any of the|a kind of|a type of|a group of|a family of|a member of the)\\s+)?(.+)$");
    private static final Pattern KIND_END = Pattern.compile("(?i)\\s(that|which|who|whose|with|of|in|from|for|by|used|found|living|made|"
            + "where|when|known|called|native|between|on|at|to|and|but|given)\\b|"
            + ",(?=\\s*(?:the|a|an|and|or|which|who|that|it|its|located|known|also|but|in|on|with|one|usually|often|mostly)\\b|\\s*\\p{Lu})|;|\\(");

    /** D2: the kind from the first sentence that says what the thing is; null when none does. */
    public static Kind kind(String text) {
        for (String s : sentences(text, 4)) {
            Kind k = read(s);
            if (k == NAME_ONLY) {
                return null; // what a word means, not what a thing is: it can't be told apart this way
            }
            if (k != null) {
                return k;
            }
        }
        return null;
    }

    /** A word's meaning, not a thing's kind ("Python is the name given to …"). */
    private static final Kind NAME_ONLY = new Kind("", "", "", "");

    /**
     * Every kind the opening of a text gives its thing: "Mammals are in the class Mammalia. They are a group of vertebrate
     * animals." gives vertebrate animals; "Frogs are amphibians and vertebrates" gives both. Only sentences about the
     * thing itself (its name or "it", "they") count.
     */
    public static List<Kind> kinds(String text, String title) {
        List<Kind> out = new ArrayList<Kind>();
        Set<String> name = words(title);
        for (String s : sentences(text, 5)) {
            Matcher subj = Pattern.compile("^(.{2,80}?)\\s+(?:\\([^)]*\\)\\s+)?(?:is|are|was|were)\\b").matcher(s);
            if (!subj.find()) {
                continue;
            }
            String who = subj.group(1);
            Set<String> w = words(who);
            w.retainAll(name);
            if (!who.matches("(?i)(it|they|these|this|both)") && w.isEmpty()) {
                continue;
            }
            Kind k = read(s);
            if (k == NAME_ONLY) {
                return new ArrayList<Kind>();
            }
            if (k == null) {
                continue;
            }
            out.add(k);
            // "amphibians and vertebrates of the order Anura": the second is a kind too
            Matcher and = Pattern.compile("(?i)^and\\s+(.+)$").matcher(k.rest);
            if (and.matches()) {
                Kind more = read(who + (k.all ? " are " : " is ") + and.group(1));
                if (more != null && more != NAME_ONLY) {
                    out.add(more);
                }
            }
        }
        return out;
    }

    /** D2 for one sentence: the kind it says its subject is; null when it says none. */
    static Kind read(String s) {
        {
            Matcher m = IS_A.matcher(s.replaceAll("[.!]+$", ""));
            if (!m.matches()) {
                return null;
            }
            String det = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.US);
            if (det.isEmpty() && !s.matches("(?is).*?\\s(are|were)\\s.*")) {
                return null; // "Frogs are amphibians" says a kind without "a"; "Lagos is big" doesn't
            }
            String pred = m.group(3).trim();
            Matcher end = KIND_END.matcher(pred);
            int at = end.find() ? end.start() : pred.length();
            String kind = pred.substring(0, at).trim();
            String rest = pred.substring(at).replaceFirst("^[,;\\s]+", "").trim();
            if (kind.isEmpty() || kind.split(" ").length > 6 || det.isEmpty() && kind.split(" ")[0].matches("(?i)\\w+(ed|ing|ly)|not|very|so|too|also|still|in|on|at|of|from|by|for|with|part|one|all|some|usually|often|mostly|found|called|known")) {
                return null;
            }
            String[] w = kind.split("\\s+");
            String head = w[w.length - 1].toLowerCase(Locale.US);
            if (kind.toLowerCase(Locale.US).matches("(.*\\s)?(name|names|word|term|title|surname|nickname|abbreviation|acronym)(\\s.*)?")) {
                return NAME_ONLY;
            }
            // "a genus of crocodilians", "a body of water": the kind is what the container word is of
            Matcher of = Pattern.compile("(?i)^of\\s+((?:the\\s+)?[\\p{L}-]+(?:\\s+[\\p{L}-]+){0,2}?)(?=\\s+(?:that|which|who|with|in|from|for|by|"
                    + "used|found|living|where|known|called|and|or)\\b|[,;(]|$)").matcher(rest);
            if (head.matches("genus|genera|species|family|families|group|groups|kind|kinds|type|types|sort|variety|breed|member|members|order|"
                    + "class|subfamily|branch|body|bodies|part|piece|area|unit|form|collection|set") && of.find()) {
                String target = of.group(1).replaceFirst("(?i)^the\\s+", "");
                rest = rest.substring(of.end()).replaceFirst("^[,;\\s]+", "").trim();
                kind = kind + " of " + target;
                if (head.matches("body|bodies|part|piece|area|unit|form|collection|set")) {
                    head = kind.toLowerCase(Locale.US).replaceFirst("^(\\w+\\s+)*?(?=" + head + "\\b)", ""); // "body of water", whole
                } else {
                    String[] t = target.split("\\s+");
                    head = Causes.stem(t[t.length - 1]); // "crocodilians" → the kind they are
                }
            }
            // "the second largest city" keeps its "the"; "the day-to-day change" reads as "day-to-day change"
            boolean most = kind.matches("(?i)^(\\w+\\s+)?(\\w+est|most|largest|biggest|first|only|main|oldest|second|third)\\b.*");
            String said = (det.matches("a|an") ? det + " " : det.equals("the") ? (most ? "the " : "") : det.isEmpty() ? "" : det + " ") + kind;
            Kind k = new Kind(said.trim(), head, rest, s);
            k.all = det.isEmpty();
            return k;
        }
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
        String sameHead = Causes.stem(a.head).equals(Causes.stem(b.head)) ? (a.head.contains(" ") ? a.head : plainNoun(a.head, a.said)) : null;
        if (sameHead != null) {
            out.append("Both are ").append(plural(sameHead)).append(", but they differ.");
        } else {
            out.append(is(nameA, a, true)).append("; ").append(is(nameB, b, false)).append(".");
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
        if (w.contains(" of ")) {
            int at = w.indexOf(" of ");
            return plural(w.substring(0, at)) + w.substring(at); // "bodies of water"
        }
        if (w.matches("(?i).*(s|sh|ch|x|z)$")) {
            return w + "es";
        }
        if (w.matches("(?i).*um$")) {
            return w.substring(0, w.length() - 2) + "a"; // bacterium, bacteria
        }
        if (w.matches("(?i).*[^aeiou]y$")) {
            return w.substring(0, w.length() - 1) + "ies";
        }
        return w + "s";
    }

    /** The head as written in the kind ("crocodilian" from "a genus of crocodilians" is said "crocodilian"). */
    private static String plainNoun(String head, String said) {
        for (String w : said.toLowerCase(Locale.US).split("\\s+")) {
            if (Causes.stem(w).equals(Causes.stem(head))) {
                return w.endsWith("s") && !w.endsWith("ss") && w.length() > 3 ? w.substring(0, w.length() - 1) : w;
            }
        }
        return head;
    }

    /** "Frog is an amphibian", "moths are insects": a kind said without "a" is said of them all. */
    private static String is(String name, Kind k, boolean first) {
        boolean all = k.all;
        String who = all && !name.matches(".*\\p{Lu}.*") ? plural(name) : name;
        String s = who + (all && !who.equals(name) ? " are " : " is ") + k.said;
        return first ? cap(s) : s;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
