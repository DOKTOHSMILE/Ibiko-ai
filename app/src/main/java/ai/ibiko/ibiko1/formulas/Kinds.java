package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reasoning over kinds: "is a frog an animal?" is not looked up, it is worked out by going up what each thing is: a
 * frog is an amphibian, an amphibian is a vertebrate, a vertebrate is an animal; so yes, and the steps are the answer's
 * working. What each thing is comes from what is read about it ({@link Contrast#kind}), one step at a time, a few steps
 * at most. A chain that reaches the kind asked is a yes; one that doesn't is not a no (what isn't read isn't false),
 * so nothing is said and the question goes on. Pure Java.
 *
 * <pre>
 * Q1  asks    "is a X a/an Y", "is X a kind of Y", "are Xs Ys", "is X an example of Y"
 * Q2  up      X's kind, that kind's kind, … (at most 4 steps, never the same twice)
 * Q3  yes     a kind on the way that is Y (its head word, singular or plural)
 * </pre>
 */
public final class Kinds {
    private Kinds() {
    }

    private static final Pattern IS_A = Pattern.compile("(?i)^(?:is|are|was|were)\\s+((?:a\\s+|an\\s+|the\\s+)?.{2,40}?)\\s+"
            + "(?:a kind of|a type of|a sort of|a form of|an example of|a member of the|a|an|one of the)\\s+(.{2,40}?)[?.!\\s]*$");
    private static final Pattern ARE = Pattern.compile("(?i)^(?:are|were)\\s+([\\p{L}-]{3,})\\s+([\\p{L}-]{3,})[?.!\\s]*$");

    /** Q1: the thing and the kind asked about, or null when the question isn't "is X a Y?". */
    public static String[] asked(String question) {
        String q = question == null ? "" : question.trim();
        Matcher m = IS_A.matcher(q);
        if (m.matches()) {
            return new String[] {m.group(1).trim(), m.group(2).trim()};
        }
        m = ARE.matcher(q);
        if (m.matches()) {
            return new String[] {m.group(1), m.group(2)};
        }
        return null;
    }

    /** One step up: what a thing is, as said ("an amphibian"), and the word to go on from ("amphibian"). */
    public static final class Step {
        public final String thing, said, head;
        /** The thing as the text named it ("Gold", "A frog", "Dogs"); null when it said "it" or "they". */
        public final String named;

        Step(String thing, String said, String head) {
            this(thing, said, head, null);
        }

        Step(String thing, String said, String head, String sentence) {
            this.thing = thing;
            this.said = said;
            this.head = head;
            String n = null;
            if (sentence != null) {
                Matcher m = Pattern.compile("^(.{2,60}?)\\s+(?:\\([^)]*\\)\\s+)?(?:is|are|was|were)\\b").matcher(sentence.trim());
                if (m.find() && !m.group(1).matches("(?i)(it|they|these|this|both|that)")) {
                    n = m.group(1).replaceAll("\\s*\\([^)]*\\)", "").trim();
                }
            }
            this.named = n;
        }
    }

    /** Q2–Q3: the steps from the thing up to the kind asked, or null when what it reads doesn't get there. */
    public static List<Step> chain(String thing, String kind, Function<String, List<Contrast.Kind>> whatIs) {
        String goal = Causes.stem(last(kind));
        return search(thing, k -> Causes.stem(last(k.head)).equals(goal) || saysKind(k.said, kind), whatIs);
    }

    /**
     * Kinds that can't overlap: what is one of them is none of the others. A "no" is said only from these: a chain from
     * the thing that reaches another member of the kind's own group ("a bat is a mammal": not a bird). Anything else not
     * reached stays unsaid (what isn't read isn't false).
     */
    private static final String[][] APART = {
        {"mammal", "bird", "fish", "reptile", "amphibian", "insect", "arachnid", "crustacean", "mollusc"},
        {"animal", "plant", "fungus", "bacterium", "virus"},
        {"planet", "star", "moon", "comet", "asteroid", "galaxy"},
        {"country", "city", "continent", "river", "mountain", "ocean", "lake", "island", "desert"},
        {"fruit", "vegetable", "grain", "nut"},
        {"metal", "nonmetal", "gas", "liquid"},
        {"tree", "flower", "grass", "shrub"}};

    private static String[] groupOf(String word) {
        String w = Causes.stem(last(word));
        for (String[] g : APART) {
            for (String m : g) {
                if (Causes.stem(m).equals(w)) {
                    return g;
                }
            }
        }
        return null;
    }

    /** Q4: the steps from the thing to a kind that rules out the one asked ("a bat is a mammal"), or null: not a sure no. */
    public static List<Step> not(String thing, String kind, Function<String, List<Contrast.Kind>> whatIs) {
        final String[] group = groupOf(kind);
        if (group == null) {
            return null;
        }
        final String asked = Causes.stem(last(kind));
        return search(thing, k -> {
            String h = Causes.stem(last(k.head));
            if (h.equals(asked)) {
                return false;
            }
            for (String m : group) {
                if (Causes.stem(m).equals(h)) {
                    return true;
                }
            }
            return false;
        }, whatIs);
    }

    /** "No. A bat is a mammal, and mammals aren't birds." */
    public static String saidNo(List<Step> steps, String kind) {
        Step last = steps.get(steps.size() - 1);
        String yes = said(steps).replaceFirst("^Yes\\. ", "").replaceAll("\\.$", "");
        String other = singular(last.head.trim()), asked = singular(kind.replaceFirst("(?i)^(a|an|the)\\s+", "").trim());
        return "No. " + yes + ", and " + Contrast.plural(other) + " aren't " + Contrast.plural(asked) + ".";
    }

    private static List<Step> search(String thing, java.util.function.Predicate<Contrast.Kind> found, Function<String, List<Contrast.Kind>> whatIs) {
        Map<String, Step> cameFrom = new HashMap<String, Step>();
        Set<String> seen = new HashSet<String>();
        LinkedList<String[]> queue = new LinkedList<String[]>(); // {name, depth}
        queue.add(new String[] {thing, "0"});
        seen.add(Causes.stem(last(thing)));
        while (!queue.isEmpty()) {
            String[] at = queue.removeFirst();
            int depth = Integer.parseInt(at[1]);
            if (depth >= 4) {
                continue;
            }
            List<Contrast.Kind> ks;
            try {
                ks = whatIs.apply(at[0]);
            } catch (RuntimeException e) {
                ks = null;
            }
            if (ks == null) {
                continue;
            }
            for (Contrast.Kind k : ks) {
                Step step = new Step(at[0], k.said, k.head, k.sentence);
                if (found.test(k)) {
                    List<Step> out = new ArrayList<Step>();
                    out.add(0, step);
                    String back = at[0];
                    while (cameFrom.containsKey(back)) {
                        Step before = cameFrom.get(back);
                        out.add(0, before);
                        back = before.thing;
                    }
                    return out;
                }
            }
            for (Contrast.Kind k : ks) {
                String head = Causes.stem(last(k.head));
                if (seen.add(head)) {
                    String next = singular(k.head);
                    cameFrom.put(next, new Step(at[0], k.said, k.head, k.sentence));
                    queue.add(new String[] {next, String.valueOf(depth + 1)});
                }
            }
        }
        return null;
    }

    /** "a large reptile" says reptile; "a type of mammal" says mammal. */
    private static boolean saysKind(String said, String kind) {
        Set<String> w = new HashSet<String>();
        for (String x : said.toLowerCase(Locale.US).split("[^\\p{L}]+")) {
            w.add(Causes.stem(x));
        }
        return w.contains(Causes.stem(last(kind)));
    }

    /** "vertebrates" → "vertebrate", "bodies of water" → "body of water": a kind named as one of it. */
    static String singular(String w) {
        String s = w.trim();
        int of = s.indexOf(" of ");
        if (of > 0) {
            return singular(s.substring(0, of)) + s.substring(of);
        }
        if (s.matches("(?i).*[^aeiou]ies$")) {
            return s.substring(0, s.length() - 3) + "y";
        }
        if (s.matches("(?i).*(ches|shes|sses|xes|zes)$")) {
            return s.substring(0, s.length() - 2);
        }
        if (s.matches("(?i).*[^su]s$") && s.length() > 3) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String last(String s) {
        String[] w = s.trim().toLowerCase(Locale.US).split("\\s+");
        return w[w.length - 1];
    }

    /** "Yes. A frog is an amphibian; an amphibian is a vertebrate; a vertebrate is an animal." */
    public static String said(List<Step> steps) {
        StringBuilder b = new StringBuilder("Yes. ");
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            // "amphibians", "a group of animals", "a class of insects": said of them all ("mammals are a group of …")
            boolean plural = !s.said.matches("(?i)^(a|an|the)\\b.*")
                    || s.said.matches("(?i)^(a|an|the)\\s+(\\w+\\s+)?(group|family|class|order|kind|type|set|collection|genus|species|superorder|phylum)\\s+of\\b.*");
            String bare = s.thing.replaceFirst("(?i)^(a|an|the)\\s+", "");
            String who = s.named != null && plural == s.named.matches("(?i)^(?!a |an |the ).*s$")
                    ? (i == 0 || Character.isUpperCase(bare.charAt(0)) ? s.named : lowerStart(s.named)) // as the text named it
                    : plural ? (Character.isUpperCase(bare.charAt(0)) ? bare : Contrast.plural(bare))
                    : i == 0 ? s.thing // as it was asked: "gold", "a frog", "the sun"
                    : named(s.thing);
            String line = who + (plural ? " are " : " is ") + s.said;
            b.append(i == 0 ? Character.toUpperCase(line.charAt(0)) + line.substring(1) : line).append(i + 1 < steps.size() ? "; " : ".");
        }
        return b.toString();
    }

    private static String lowerStart(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static String named(String thing) {
        String t = thing.trim();
        if (t.matches("(?i)^(a|an|the)\\s.*") || Character.isUpperCase(t.charAt(0))) {
            return t;
        }
        return (t.matches("(?i)^[aeiou].*") ? "an " : "a ") + t;
    }
}
