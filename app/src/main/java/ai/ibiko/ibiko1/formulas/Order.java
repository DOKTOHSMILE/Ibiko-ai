package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reasoning about order from what the person says: "Ada is taller than Obi. Obi is taller than Emeka. Who is the
 * tallest?" Each comparison is read as one thing above another on a scale (height, age, size, speed…); "shorter" is the
 * same scale read the other way. The answer follows the chain, and says it ("Ada is taller than Obi, and Obi is taller
 * than Emeka"); when the statements contradict each other it says so, and when they don't decide it, it says what's
 * missing instead of guessing. Pure Java.
 *
 * <pre>
 * O1  read     "X is taller than Y", "X is not as tall as Y", "X is more careful than Y": X above (or below) Y on a scale
 * O2  chain    everything above or below each one, through the others
 * O3  answer   the most or least ("the tallest", "the youngest"), one against another ("is Ada taller than Emeka?"),
 *              all in order; a contradiction, or what can't be told
 * </pre>
 */
public final class Order {
    /** One scale: its "more" and "less" words, and the superlatives of each. */
    private static final String[][] SCALES = {
        // scale, more, less, most, least
        {"tall", "taller", "shorter", "tallest", "shortest"},
        {"old", "older", "younger", "oldest", "youngest"},
        {"big", "bigger|larger", "smaller", "biggest|largest", "smallest"},
        {"heavy", "heavier", "lighter", "heaviest", "lightest"},
        {"fast", "faster|quicker", "slower", "fastest|quickest", "slowest"},
        {"rich", "richer|wealthier", "poorer", "richest|wealthiest", "poorest"},
        {"strong", "stronger", "weaker", "strongest", "weakest"},
        {"long", "longer", "shorter", "longest", "shortest"},
        {"high", "higher", "lower", "highest", "lowest"},
        {"smart", "smarter|cleverer", "dumber", "smartest|cleverest", "dumbest"},
        {"cheap", "cheaper", "dearer|pricier", "cheapest", "dearest|priciest"},
        {"hot", "hotter|warmer", "colder|cooler", "hottest|warmest", "coldest|coolest"}};

    /** A scale's words for a comparative or superlative: the scale, and +1 (more) or -1 (less). */
    private static String[] scaleOf(String word, boolean superlative, Set<String> known) {
        String w = word.toLowerCase(Locale.ROOT);
        String[] found = null;
        for (String[] s : SCALES) {
            int more = superlative ? 3 : 1, less = superlative ? 4 : 2;
            if (w.matches(s[more])) {
                found = new String[]{s[0], "1"};
            } else if (w.matches(s[less])) {
                found = new String[]{s[0], "-1"};
            }
            if (found != null && (known == null || known.isEmpty() || known.contains(found[0]))) {
                return found; // "shorter" is height when height was talked about, length when length was
            }
        }
        return found;
    }

    /** One statement: who is above whom on which scale, and the words it was said in. */
    public static final class Fact {
        public final String scale, above, below, said;

        Fact(String scale, String above, String below, String said) {
            this.scale = scale;
            this.above = above;
            this.below = below;
            this.said = said;
        }
    }

    private static final String NAME = "((?:the )?[A-Z][\\p{L}'-]*(?: [A-Z][\\p{L}'-]*)?|[a-z]+)";
    private static final Pattern THAN = Pattern.compile("\\b" + NAME + " (?:is|was|are|were|'s) (?:a (?:bit|little|lot) |much |far |slightly )?"
            + "(?:(more|less) (\\p{L}+)|(\\p{L}+er)) than " + NAME + "\\b");
    private static final Pattern ALSO = Pattern.compile("\\b(?:but|and|yet|while)\\s+(?:is\\s+|also\\s+|is also\\s+)?(?:a (?:bit|little|lot) |much |far |slightly )?"
            + "(?:(more|less) (\\p{L}+)|(\\p{L}+er)) than " + NAME + "\\b");
    private static final Pattern NOT_AS = Pattern.compile("\\b" + NAME + " (?:is|was|are|were) not as (\\p{L}+) as " + NAME + "\\b");

    /** O1: the comparisons a text states (not the ones it asks about). */
    public static List<Fact> read(String text) {
        List<Fact> out = new ArrayList<Fact>();
        if (text == null) {
            return out;
        }
        Set<String> scales = new LinkedHashSet<String>();
        for (String sentence : text.split("(?<=[.!;])\\s+|\\n+")) {
            String s = sentence.trim();
            if (s.endsWith("?") || s.matches("(?i)^(?:is|are|was|were|who|which|what)\\b.*")) {
                continue; // a question, not a statement
            }
            Matcher m = THAN.matcher(s);
            String subject = null; // the one the sentence is about, for "… but younger than Bisi"
            while (m.find()) {
                String a = name(m.group(1)), b = name(m.group(5));
                subject = a;
                String[] sc;
                if (m.group(2) != null) {
                    sc = new String[]{m.group(3).toLowerCase(Locale.ROOT), m.group(2).equalsIgnoreCase("more") ? "1" : "-1"};
                } else {
                    sc = scaleOf(m.group(4), false, scales);
                }
                if (sc != null && !a.equalsIgnoreCase(b)) {
                    scales.add(sc[0]);
                    out.add(sc[1].equals("1") ? new Fact(sc[0], a, b, s) : new Fact(sc[0], b, a, s));
                }
            }
            // "Ada is older than Obi but younger than Bisi": the second comparison is Ada's too
            Matcher e = subject == null ? null : ALSO.matcher(s);
            while (e != null && e.find()) {
                String b = name(e.group(4));
                String[] sc = e.group(1) != null ? new String[]{e.group(2).toLowerCase(Locale.ROOT), e.group(1).equalsIgnoreCase("more") ? "1" : "-1"}
                        : scaleOf(e.group(3), false, scales);
                if (sc != null && !subject.equalsIgnoreCase(b)) {
                    scales.add(sc[0]);
                    out.add(sc[1].equals("1") ? new Fact(sc[0], subject, b, s) : new Fact(sc[0], b, subject, s));
                }
            }
            Matcher n = NOT_AS.matcher(s);
            while (n.find()) {
                String adj = n.group(2).toLowerCase(Locale.ROOT);
                String scale = null;
                for (String[] sc : SCALES) {
                    if (sc[0].equals(adj)) {
                        scale = sc[0];
                    }
                }
                if (scale == null) {
                    scale = adj;
                }
                scales.add(scale);
                out.add(new Fact(scale, name(n.group(3)), name(n.group(1)), s)); // "not as tall as": the other is taller
            }
        }
        return out;
    }

    private static String name(String s) {
        return s.trim().replaceFirst("^(?i)the ", "the ");
    }

    /** What's asked: the most, the least, one against another, or the whole order. */
    public static final class Question {
        public final String scale;
        public final int direction; // +1 the most, -1 the least
        public final String a, b; // one against another: is a above b?
        public final boolean all;

        Question(String scale, int direction, String a, String b, boolean all) {
            this.scale = scale;
            this.direction = direction;
            this.a = a;
            this.b = b;
            this.all = all;
        }
    }

    private static final Pattern MOST = Pattern.compile("(?i)\\b(?:who|which(?: one)?|what)(?: of them| of the \\w+)? (?:is|was|are|'s) (?:the )?(?:(most|least) (\\p{L}+)|(\\p{L}+est))\\b");
    private static final Pattern ONE = Pattern.compile("\\b(?:[Ii]s|[Ww]as|[Aa]re|[Ww]ere) " + NAME + " (?:(more|less) (\\p{L}+)|(\\p{L}+er)) than " + NAME + "\\s*\\?");
    private static final Pattern ALL = Pattern.compile("(?i)\\b(?:put|list|rank|sort|arrange) (?:them|these|everyone|all of them|the \\w+) (?:in order|from|by)"
            + "|\\b(?:what|which) (?:is|'s) the order\\b|\\bin order of (\\p{L}+)");

    /** O3: the question the text asks about order, or null. */
    public static Question asked(String text, List<Fact> facts) {
        if (text == null) {
            return null;
        }
        Set<String> scales = new LinkedHashSet<String>();
        for (Fact f : facts) {
            scales.add(f.scale);
        }
        Matcher m = MOST.matcher(text);
        // "who is the tallest man in the world?": a question of its own, not about the people compared before
        // (unless the comparisons are in this same message)
        boolean ownSubject = m.find() && text.substring(m.end()).matches("(?is)\\s+(?!of (?:them|us|the|those|these|all)|one\\b|among|person\\b|here\\b)\\p{L}.*")
                && read(text).isEmpty()
                // "the oldest of Amina, Tunde and Ngozi": the people compared, named
                && !text.matches("(?s).*\\b(?:of|among|between)\\s+[A-Z][\\p{L}'-]+(?:,\\s*[A-Z][\\p{L}'-]+)*,?\\s+(?:and|or)\\s+[A-Z][\\p{L}'-]+.*");
        m.reset();
        if (!ownSubject && m.find()) {
            if (m.group(1) != null) {
                return new Question(m.group(2).toLowerCase(Locale.ROOT), m.group(1).equalsIgnoreCase("most") ? 1 : -1, null, null, false);
            }
            String[] sc = scaleOf(m.group(3), true, scales);
            if (sc != null) {
                return new Question(sc[0], Integer.parseInt(sc[1]), null, null, false);
            }
        }
        Matcher o = ONE.matcher(text);
        if (o.find()) {
            String a = name(o.group(1)), b = name(o.group(5));
            if (o.group(2) != null) {
                boolean more = o.group(2).equalsIgnoreCase("more");
                String scale = o.group(3).toLowerCase(Locale.ROOT);
                return more ? new Question(scale, 1, a, b, false) : new Question(scale, 1, b, a, false);
            }
            String[] sc = scaleOf(o.group(4), false, scales);
            if (sc != null) {
                return sc[1].equals("1") ? new Question(sc[0], 1, a, b, false) : new Question(sc[0], 1, b, a, false);
            }
        }
        if (ALL.matcher(text).find() && !scales.isEmpty()) {
            return new Question(scales.iterator().next(), 1, null, null, true);
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------------- O2–O3

    /** O2–O3: the answer, reasoned from the facts on the question's scale; null when there are none on it. */
    public static String answer(Question q, List<Fact> all) {
        List<Fact> facts = new ArrayList<Fact>();
        for (Fact f : all) {
            if (f.scale.equals(q.scale)) {
                facts.add(f);
            }
        }
        if (facts.isEmpty()) {
            return null;
        }
        Map<String, String> names = new LinkedHashMap<String, String>(); // lower case → as said
        Map<String, Set<String>> below = new HashMap<String, Set<String>>(); // directly below each
        for (Fact f : facts) {
            names.putIfAbsent(f.above.toLowerCase(Locale.ROOT), f.above);
            names.putIfAbsent(f.below.toLowerCase(Locale.ROOT), f.below);
            below.computeIfAbsent(f.above.toLowerCase(Locale.ROOT), k -> new LinkedHashSet<String>()).add(f.below.toLowerCase(Locale.ROOT));
        }
        // a contradiction: something above itself
        for (String n : names.keySet()) {
            List<String> loop = path(below, n, n, new LinkedHashSet<String>());
            if (loop != null) {
                return "That can't all be true: " + chain(loop, names, q.scale) + ". One of those must be wrong: which one should I drop?";
            }
        }
        if (q.a != null) {
            String a = q.a.toLowerCase(Locale.ROOT), b = q.b.toLowerCase(Locale.ROOT);
            if (!names.containsKey(a) || !names.containsKey(b)) {
                String missing = !names.containsKey(a) ? q.a : q.b;
                return "You haven't told me anything about " + missing + " and " + q.scale + ", so I can't say.";
            }
            List<String> up = path(below, a, b, new LinkedHashSet<String>());
            if (up != null) {
                return "Yes: " + chain(up, names, q.scale) + ".";
            }
            List<String> down = path(below, b, a, new LinkedHashSet<String>());
            if (down != null) {
                return "No: it's the other way round. " + capital(chain(down, names, q.scale)) + ".";
            }
            return "I can't tell from what you said: nothing links " + names.get(a) + " and " + names.get(b) + " on " + q.scale + ".";
        }
        // everyone, ordered: the ones above all the rest first
        Map<String, Set<String>> reach = new HashMap<String, Set<String>>();
        for (String n : names.keySet()) {
            reach.put(n, reachable(below, n));
        }
        if (q.all) {
            List<String> order = new ArrayList<String>(names.keySet());
            order.sort((x, y) -> reach.get(y).size() - reach.get(x).size());
            for (int i = 0; i + 1 < order.size(); i++) {
                if (!reach.get(order.get(i)).contains(order.get(i + 1))) {
                    return "I can't put them all in one order: nothing you said links " + names.get(order.get(i)) + " and " + names.get(order.get(i + 1)) + ".";
                }
            }
            List<String> said = new ArrayList<String>();
            for (String n : order) {
                said.add(names.get(n));
            }
            return "From " + superlative(q.scale, 1) + " to " + superlative(q.scale, -1) + ": " + String.join(", ", said) + ".";
        }
        // the most (or the least): the one above (or below) every other
        List<String> winners = new ArrayList<String>();
        for (String n : names.keySet()) {
            boolean beatsAll = true;
            for (String other : names.keySet()) {
                if (!other.equals(n) && !(q.direction > 0 ? reach.get(n).contains(other) : reach.get(other).contains(n))) {
                    beatsAll = false;
                    break;
                }
            }
            if (beatsAll) {
                winners.add(n);
            }
        }
        String word = superlative(q.scale, q.direction);
        if (winners.size() == 1) {
            String w = winners.get(0);
            String end = q.direction > 0 ? lowest(names, reach) : highestOf(names, reach);
            List<String> line = end == null ? null : q.direction > 0 ? path(below, w, end, new LinkedHashSet<String>()) : path(below, end, w, new LinkedHashSet<String>());
            return names.get(w) + " is the " + word + (line != null && line.size() > 2 ? ": " + chain(line, names, q.scale) + "." : ".");
        }
        List<String> candidates = new ArrayList<String>();
        for (String n : names.keySet()) {
            boolean top = true;
            for (String other : names.keySet()) {
                if (q.direction > 0 ? reach.get(other).contains(n) : reach.get(n).contains(other)) {
                    top = false;
                }
            }
            if (top) {
                candidates.add(names.get(n));
            }
        }
        return "I can't tell from what you said: it's " + String.join(" or ", candidates) + ", and nothing says which of them is " + comparative(q.scale, q.direction) + ".";
    }

    private static String lowest(Map<String, String> names, Map<String, Set<String>> reach) {
        for (String n : names.keySet()) {
            if (reach.get(n).isEmpty()) {
                return n;
            }
        }
        return null;
    }

    private static String highestOf(Map<String, String> names, Map<String, Set<String>> reach) {
        String best = null;
        for (String n : names.keySet()) {
            if (best == null || reach.get(n).size() > reach.get(best).size()) {
                best = n;
            }
        }
        return best;
    }

    private static Set<String> reachable(Map<String, Set<String>> below, String from) {
        Set<String> seen = new LinkedHashSet<String>();
        List<String> todo = new ArrayList<String>(below.getOrDefault(from, new LinkedHashSet<String>()));
        while (!todo.isEmpty()) {
            String n = todo.remove(0);
            if (seen.add(n)) {
                todo.addAll(below.getOrDefault(n, new LinkedHashSet<String>()));
            }
        }
        return seen;
    }

    /** A way down from one to another (both included), or null. */
    private static List<String> path(Map<String, Set<String>> below, String from, String to, Set<String> seen) {
        for (String next : below.getOrDefault(from, new LinkedHashSet<String>())) {
            if (next.equals(to)) {
                return new ArrayList<String>(Arrays.asList(from, to));
            }
            if (seen.add(next)) {
                List<String> rest = path(below, next, to, seen);
                if (rest != null) {
                    rest.add(0, from);
                    return rest;
                }
            }
        }
        return null;
    }

    private static String chain(List<String> line, Map<String, String> names, String scale) {
        List<String> parts = new ArrayList<String>();
        for (int i = 0; i + 1 < line.size(); i++) {
            parts.add(names.get(line.get(i)) + " is " + comparative(scale, 1) + " than " + names.get(line.get(i + 1)));
        }
        return parts.size() == 1 ? parts.get(0) : String.join(", ", parts.subList(0, parts.size() - 1)) + ", and " + parts.get(parts.size() - 1);
    }

    private static String comparative(String scale, int direction) {
        for (String[] s : SCALES) {
            if (s[0].equals(scale)) {
                return (direction > 0 ? s[1] : s[2]).split("\\|")[0];
            }
        }
        return (direction > 0 ? "more " : "less ") + scale;
    }

    private static String superlative(String scale, int direction) {
        for (String[] s : SCALES) {
            if (s[0].equals(scale)) {
                return (direction > 0 ? s[3] : s[4]).split("\\|")[0];
            }
        }
        return (direction > 0 ? "most " : "least ") + scale;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
