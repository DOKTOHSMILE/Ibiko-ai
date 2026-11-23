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
 * Understanding why: reading cause and effect out of plain text, so a "why" question is answered from what was
 * read or told, not from a list. "Plants are green because of chlorophyll", "The drought led to a famine", "Because
 * the rains failed, the harvest was poor", "The river flooded. As a result, the town was moved." each become a link,
 * effect ← cause. A why-question is matched to the effect it asks about by its words; the cause is the answer, and a
 * link whose effect is that cause goes one step further back ("which came about because …"). Asked "why?" again, it
 * follows the chain back; where the chain ends, it says so and asks. Pure Java.
 *
 * <pre>
 * K1  reads    "X because (of) Y", "X due to / owing to / thanks to / as a result of / caused by Y"   effect X, cause Y
 * K2  reads    "Because (of) Y, X"                                                                   effect X, cause Y
 * K3  reads    "Y led to / caused / resulted in / triggered / brought about / gave rise to X"         effect X, cause Y
 * K4  reads    "Y, so X", "Y, which is why X", "Y; therefore X"                                       effect X, cause Y
 * K5  reads    "Y. As a result / Therefore / Consequently / For this reason, X"                       across two sentences
 * K6  matches  a why-question to the effect sharing the most of its words (stems), enough of them
 * K7  chains   the cause to a link whose effect it is: the reason behind the reason
 * </pre>
 */
public final class Causes {
    private Causes() {
    }

    /** One link: what happened (effect), why (cause), how it is joined ("because", "because of", "so that"), the sentence. */
    public static final class Link {
        public final String effect, cause, sentence;
        /** "because" (a reason said whole), "because of" (a thing), "so that" / "in order to" (a purpose). */
        public final String joiner;

        public Link(String effect, String cause, String joiner, String sentence) {
            this.effect = effect;
            this.cause = cause;
            this.joiner = joiner == null ? "because" : joiner;
            this.sentence = sentence == null ? "" : sentence;
        }

        /** The cause is a thing ("chlorophyll", "the drought"): said "because of" it. */
        public boolean noun() {
            return joiner.equals("because of");
        }

        /** A purpose, not a cause: what it is for. */
        public boolean purpose() {
            return joiner.equals("so that") || joiner.equals("in order to");
        }

        /** The reason as said: "because of chlorophyll", "because the rains failed", "so that light gets in". */
        public String because() {
            return joiner + " " + lowerFirst(cause);
        }

        @Override
        public String toString() {
            return "(" + effect + " ← " + joiner + " " + cause + ")";
        }
    }

    private static final String NOUN_LINK = "because of|due to|owing to|thanks to|as a result of|on account of|caused by|"
            + "brought about by|the result of|a result of|the consequence of";
    private static final Pattern K1 = Pattern.compile("(?i)^(.{3,}?),?\\s+(" + NOUN_LINK + "|because)\\s+(.{2,})$");
    private static final Pattern K2 = Pattern.compile("(?i)^(because of|because|due to|owing to|thanks to|as a result of)\\s+(.{2,}?),\\s+(.{3,})$");
    private static final Pattern K3 = Pattern.compile("(?i)^(.{2,}?)\\s+(led to|leads to|lead to|caused|causes|cause|resulted in|results in|"
            + "result in|triggered|triggers|brought about|brings about)\\s+(.{2,})$");
    private static final Pattern K4 = Pattern.compile("(?i)^(.{3,}?)(?:,|;)\\s*(?:and\\s+)?(so(?!\\s+that\\b)|which is why|that is why|that's why|"
            + "therefore|thus|hence|and as a result|as a result)\\s*,?\\s+(.{3,})$");
    private static final Pattern K5 = Pattern.compile("(?i)^(as a result|therefore|consequently|because of this|because of that|"
            + "for this reason|for that reason|this is why|that is why|that's why|this meant that|so|thus|hence)\\s*,?\\s+(.{3,})$");
    /** K5b: "This is because Y": the sentence before is what Y explains. */
    private static final Pattern K5B = Pattern.compile("(?i)^(?:this|that|it) (?:is|was|happens|happened) (?:partly |mainly |largely |mostly |probably )?"
            + "(because of|because|due to)\\s+(.{2,})$");
    /** K8: a purpose, "X so that Y", "X in order to Y". */
    private static final Pattern K8 = Pattern.compile("(?i)^(.{3,}?),?\\s+(so that|in order to|in order that)\\s+(.{3,})$");
    /** What isn't so, or only might be: "people thought it would be impossible to sink" explains nothing about sinking. */
    private static final Pattern NOT_SO = Pattern.compile("(?i)\\b(thought|believed|would|could|might|impossible|unlikely|"
            + "if|whether|claimed|supposed)\\b");

    /** Words that start a clause, not a thing: "because the rains failed" is a reason said whole. */
    private static final Pattern CLAUSE = Pattern.compile("(?i)\\b(is|are|was|were|has|have|had|do|does|did|can|could|will|would|"
            + "\\w+ed|\\w{3,}s)\\b");

    /** K1–K5: the links read from a text about {@code topic} (what "it" stands for), in order. */
    public static List<Link> read(String text, String topic) {
        List<Link> out = new ArrayList<Link>();
        if (text == null) {
            return out;
        }
        String[] sentences = text.trim().split("(?<=[.!?])\\s+(?=[A-Z\"“(])");
        String before = null;
        for (String raw : sentences) {
            String s = raw.trim().replaceAll("[.!]+$", "").trim();
            if (s.endsWith("?") || s.length() < 8) {
                before = null;
                continue;
            }
            Link l = link(s, before, topic, raw.trim());
            if (l != null) {
                out.add(l);
            }
            before = s;
        }
        return out;
    }

    private static Link link(String s, String before, String topic, String sentence) {
        Matcher m;
        if ((m = K5B.matcher(s)).matches() && before != null) {
            String j = m.group(1).equalsIgnoreCase("because") ? (clause(m.group(2)) ? "because" : "because of") : "because of";
            return made(before, m.group(2), j, topic, sentence);
        }
        if ((m = K5.matcher(s)).matches() && before != null) {
            return made(m.group(2), before, "because", topic, sentence);
        }
        if ((m = K2.matcher(s)).matches()) {
            boolean noun = !m.group(1).equalsIgnoreCase("because") || !clause(m.group(2));
            return made(m.group(3), m.group(2), noun ? "because of" : "because", topic, sentence);
        }
        if ((m = K8.matcher(s)).matches()) {
            String j = m.group(2).toLowerCase(Locale.US).startsWith("in order to") ? "in order to" : "so that";
            return made(m.group(1), m.group(3), j, topic, sentence);
        }
        if ((m = K1.matcher(s)).matches() && !timeSince(m.group(3))) {
            boolean noun = !m.group(2).equalsIgnoreCase("because");
            return made(m.group(1), m.group(3), noun ? "because of" : "because", topic, sentence);
        }
        if ((m = K4.matcher(s)).matches()) {
            return made(m.group(3), m.group(1), "because", topic, sentence);
        }
        if ((m = K3.matcher(s)).matches() && !m.group(1).matches("(?i).*\\b(what|who|which)$")) {
            // "The drought led to a famine": the cause is the thing that did it
            return made(m.group(3), m.group(1), "because of", topic, sentence);
        }
        return null;
    }

    /** "since 1990" is a time, not a reason. */
    private static boolean timeSince(String cause) {
        return cause.matches("(?i)^(then|\\d.*|the (\\d|\\w+ century).*)");
    }

    private static boolean clause(String s) {
        return CLAUSE.matcher(s).find() && s.trim().split("\\s+").length >= 2;
    }

    private static Link made(String effect, String cause, String joiner, String topic, String sentence) {
        // "X is caused by Y", "X can be explained by Y": the effect is X, without its helping verb
        String e = tidy(effect).replaceFirst("(?i)\\s+(is|are|was|were|can be|may be|could be|is often|are often|is mostly|is mainly|be)$", "");
        String c = tidy(cause);
        if (topic != null && !topic.isEmpty()) {
            // "It became a desert": the thing the text is about, when "it" is plainly that thing
            String it = "^(?i:it|this)\\s+(?=(?:is|was|has|had|became|becomes|grew|lost|gained|fell|remains|remained|lies|lay)\\b)";
            e = e.replaceFirst(it, topic + " ");
            c = c.replaceFirst(it, topic + " ");
        }
        if (e.length() < 3 || c.length() < 2 || e.length() > 220 || c.length() > 220
                || c.matches("(?i)(it|this|that|they|so|many things|several things|a number of things|various things|many reasons)")
                || e.matches("(?i)(it|this|that|they|these|those|there)( \\w+)?")) {
            return null; // a reason with nothing in it, or an effect that is only a pronoun
        }
        if (NOT_SO.matcher(e).find()) {
            return null; // what people thought would happen, or what didn't: no cause of anything that did
        }
        return new Link(e, c, joiner, sentence);
    }

    private static String tidy(String s) {
        return s.trim().replaceAll("^[,;:\\s]+|[,;:.!\\s]+$", "").replaceFirst("(?i)^(and|but|that|which|then)\\s+", "")
                .replaceAll("\\s+", " ");
    }

    /** Lower case for a small word starting the clause ("The rains" → "the rains"); a name keeps its capital. */
    static String lowerFirst(String s) {
        String first = s.split("\\s+")[0];
        if (SMALL.contains(first.toLowerCase(Locale.US)) && !first.equals("I")) {
            return Character.toLowerCase(s.charAt(0)) + s.substring(1);
        }
        return s;
    }

    private static final Set<String> SMALL = new HashSet<String>(Arrays.asList("the", "a", "an", "it", "this", "that", "these", "those",
            "its", "their", "his", "her", "there", "many", "most", "some", "people", "they", "he", "she", "we", "you", "of", "light",
            "water", "heat", "too", "more", "less", "lack", "high", "low", "heavy", "strong", "weak", "poor", "years", "war", "trade"));

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList("why", "what", "how", "come", "is", "are", "was", "were",
            "do", "does", "did", "the", "a", "an", "of", "to", "in", "on", "at", "for", "and", "or", "so", "it", "its", "there", "be",
            "been", "being", "has", "have", "had", "made", "makes", "make", "caused", "causes", "cause", "led", "reason", "that", "this",
            "by", "from", "with", "as", "very", "so", "such", "can", "could", "would", "will", "get", "got", "become", "became", "becomes"));

    /** A word as compared: lower case, a plain ending ("floods", "flooded", "flooding" are one). */
    static String stem(String w) {
        String s = w.toLowerCase(Locale.US);
        if (s.length() > 5 && s.endsWith("ing")) {
            s = s.substring(0, s.length() - 3);
        } else if (s.length() > 4 && s.endsWith("ied")) {
            s = s.substring(0, s.length() - 3) + "y";
        } else if (s.length() > 4 && s.endsWith("ies")) {
            s = s.substring(0, s.length() - 3) + "y";
        } else if (s.length() > 4 && s.endsWith("ed")) {
            s = s.substring(0, s.length() - 2);
        } else if (s.length() > 4 && s.endsWith("es") && s.matches(".*(ch|sh|ss|x|z|o)es$")) {
            s = s.substring(0, s.length() - 2);
        } else if (s.length() > 3 && s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us") && !s.endsWith("is")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.replaceAll("e$", "");
    }

    /** The content words of a text, as stems. */
    public static Set<String> words(String s) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : (s == null ? "" : s).toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty() && !STOP.contains(w)) {
                out.add(stem(w));
            }
        }
        Set<String> more = new HashSet<String>();
        for (String w : out) {
            String base = PAST.get(w);
            if (base != null) {
                more.add(base);
            }
        }
        out.addAll(more);
        return out;
    }

    /** Past forms that don't end in -ed, read as the verb ("fell" is "fall"). */
    private static final java.util.Map<String, String> PAST = new java.util.HashMap<String, String>();

    static {
        String[][] p = {{"fell", "fall"}, {"fallen", "fall"}, {"rose", "rise"}, {"risen", "ris"}, {"grew", "grow"}, {"grown", "grow"},
            {"began", "begin"}, {"begun", "begin"}, {"broke", "break"}, {"broken", "break"}, {"froze", "freez"}, {"frozen", "freez"},
            {"won", "win"}, {"lost", "los"}, {"left", "leav"}, {"sank", "sink"}, {"sunk", "sink"}, {"died", "di"}, {"dead", "di"},
            {"death", "di"}, {"ran", "run"}, {"went", "go"}, {"gone", "go"}, {"came", "com"}, {"shrank", "shrink"}, {"burnt", "burn"},
            {"spread", "spread"}, {"built", "build"}, {"fought", "fight"}, {"flood", "flood"}, {"collapse", "collaps"}, {"fall", "fall"}};
        for (String[] w : p) {
            PAST.put(stem(w[0]), stem(w[1]));
        }
    }

    /** K6–K7: the answer to a why-question from the links, or null when none is about what it asks. */
    public static final class Why {
        public final Link link;
        /** The reason behind the reason, or null. */
        public final Link behind;

        Why(Link link, Link behind) {
            this.link = link;
            this.behind = behind;
        }

        /** "Because of the drought, which came about because the rains failed." */
        public String said() {
            String first = Character.toUpperCase(link.because().charAt(0)) + link.because().substring(1);
            if (behind == null) {
                return first + ".";
            }
            if (behind.purpose()) {
                return first + ", " + behind.because() + ".";
            }
            return first + ", which came about " + behind.because() + ".";
        }

        /** The deepest link used: where "why?" goes on from. */
        public Link last() {
            return behind != null ? behind : link;
        }
    }

    /** K6: the link whose effect the question asks about (its words, enough of them), with K7 the one behind it. */
    public static Why why(String question, List<Link> links) {
        Link best = best(words(question), links, null);
        if (best == null) {
            return null;
        }
        return new Why(best, behind(best, links));
    }

    /** K7: the reason behind a cause: a link whose effect is that cause, or null. */
    public static Link behind(Link l, List<Link> links) {
        Set<String> c = words(l.cause);
        if (c.isEmpty()) {
            return null;
        }
        Link b = best(c, links, l);
        // the same reason back again (a loop) is no step further
        return b == null || words(b.cause).equals(words(l.effect)) || words(b.cause).equals(c) ? null : b;
    }

    private static Link best(Set<String> asked, List<Link> links, Link not) {
        if (asked.isEmpty()) {
            return null;
        }
        Link best = null;
        double bestScore = 0;
        for (Link l : links) {
            if (l == not || not != null && l.effect.equalsIgnoreCase(not.effect) && l.cause.equalsIgnoreCase(not.cause)) {
                continue;
            }
            Set<String> e = words(l.effect);
            int shared = 0;
            for (String w : asked) {
                shared += e.contains(w) ? 1 : 0;
            }
            // enough of what was asked: all of one or two words, most of more, never a single word of many
            int need = asked.size() <= 2 ? asked.size() : Math.max(2, (int) Math.ceil(asked.size() * 0.6));
            if (shared < need) {
                continue;
            }
            double score = shared - 0.01 * e.size(); // the closer effect wins: fewer words beside the ones asked
            if (best == null || score > bestScore) {
                best = l;
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * A why-question and the reason someone gave, put as one sentence to keep ("Why is the sky blue?" + "scattering
     * of light" → "The sky is blue because of scattering of light"); null when the question isn't a plain why.
     */
    public static String form(String question, String reason) {
        String q = question.trim().replaceAll("[?.!\\s]+$", "");
        Matcher m = Pattern.compile("(?i)^why\\s+(is|are|was|were|do|does|did|has|have|had|can|can't|cannot|don't|doesn't|didn't|isn't|aren't|wasn't|weren't)\\s+(.+)$").matcher(q);
        if (!m.matches()) {
            // "Why the rains failed" (asked back after a reason): the clause is the statement as it stands
            Matcher bare = Pattern.compile("(?i)^why\\s+(.{3,})$").matcher(q);
            if (!bare.matches() || !clause(bare.group(1))) {
                return null;
            }
            String st = bare.group(1).trim();
            String r = reason.trim().replaceAll("[.!\\s]+$", "");
            String joined = r.toLowerCase(Locale.US).matches("^(because|due to|owing to|thanks to|as a result of|since)\\b.*") ? lowerFirst(r)
                    : (clause(r) ? "because " : "because of ") + lowerFirst(r);
            return Character.toUpperCase(st.charAt(0)) + st.substring(1) + " " + joined + ".";
        }
        String aux = m.group(1).toLowerCase(Locale.US), rest = m.group(2).trim();
        String statement;
        boolean neg = aux.contains("n't") || aux.equals("cannot");
        String base = aux.equals("cannot") || aux.equals("can't") ? "can" : aux.replace("n't", "");
        if (base.matches("is|are|was|were|has|have|had|can")) {
            // "the sky blue": the verb goes before the word that says what it is ("called", "known" and the like first)
            String[] w = rest.split("\\s+");
            int at = w.length - 1;
            for (int i = 1; i < w.length; i++) {
                if (w[i].matches("(?i)\\w+(ed|en)|called|known|named|made|seen|considered|used")) {
                    at = i;
                    break;
                }
            }
            if (at <= 0) {
                return null;
            }
            statement = String.join(" ", Arrays.copyOfRange(w, 0, at)) + " " + base + (neg ? " not " : " ")
                    + String.join(" ", Arrays.copyOfRange(w, at, w.length));
        } else {
            // "do birds migrate" → "birds migrate"; "did the empire fall" → "the empire fell"; "does ice float" → "ice floats"
            // the verb follows the subject: past "the", "a", "my" and the name's capitalised words
            String[] w = rest.split("\\s+");
            int at = 0;
            if (at < w.length && w[at].matches("(?i)the|a|an|my|your|his|her|its|our|their|this|that|these|those|some|many|most")) {
                at++;
                at++; // the noun after it
            } else {
                at++;
            }
            while (at < w.length - 1 && Character.isUpperCase(w[at].charAt(0))) {
                at++;
            }
            if (at >= w.length) {
                return null;
            }
            String subject = String.join(" ", Arrays.copyOfRange(w, 0, at));
            String after = at + 1 < w.length ? " " + String.join(" ", Arrays.copyOfRange(w, at + 1, w.length)) : "";
            String verb = w[at];
            if (neg) {
                statement = subject + " " + base + " not " + verb + after;
            } else {
                verb = base.equals("did") ? ai.ibiko.ibiko1.words.Morph.past(verb) : base.equals("does") ? ai.ibiko.ibiko1.words.Morph.third(verb) : verb;
                statement = subject + " " + verb + after;
            }
        }
        String r = reason.trim().replaceAll("[.!\\s]+$", "");
        String low = r.toLowerCase(Locale.US);
        String joined;
        if (low.matches("^(because|due to|owing to|thanks to|as a result of|since)\\b.*")) {
            joined = lowerFirst(r);
        } else {
            joined = (clause(r) ? "because " : "because of ") + lowerFirst(r);
        }
        statement = statement.trim();
        return Character.toUpperCase(statement.charAt(0)) + statement.substring(1) + " " + joined + ".";
    }
}
