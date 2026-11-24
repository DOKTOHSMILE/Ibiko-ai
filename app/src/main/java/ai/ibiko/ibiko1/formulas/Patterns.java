package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Seeing the rule in what's given, and carrying it on: number sequences ("2, 6, 18, 54, ?") and analogies ("hot is to
 * cold as up is to ?"). Each rule found is said with the answer, so the reasoning can be checked. Pure Java: the
 * analogies ask the dictionary through the functions they are given.
 *
 * <pre>
 * S1 steady step      the same difference each time (3, 7, 11, 15 → 19)
 * S2 times            the same ratio each time (2, 6, 18, 54 → 162)
 * S3 squares, cubes   n², n³ of counting numbers (4, 9, 16, 25 → 36)
 * S4 sum of two       each is the sum of the two before (2, 3, 5, 8, 13 → 21)
 * S5 growing step     the differences themselves go up steadily (1, 2, 4, 7, 11 → 16)
 * A1 relation         how the first pair is related, by the dictionary: opposites, young of, covers, does, part of
 * A2 carried over     the same relation from the third word, the commonest word that fits
 * </pre>
 */
public final class Patterns {
    private Patterns() {
    }

    private static final Pattern SEQUENCE = Pattern.compile("(?i)(?:what comes next|next number|next in the (?:sequence|series|pattern)|continue the (?:pattern|sequence|series)"
            + "|complete the (?:sequence|series|pattern)|what number comes next|find the next number|next term)\\b[^0-9-]*((?:-?\\d+(?:\\.\\d+)?\\s*,\\s*){3,}-?\\d+(?:\\.\\d+)?)");
    private static final Pattern BARE = Pattern.compile("^\\s*((?:-?\\d+(?:\\.\\d+)?\\s*,\\s*){3,}-?\\d+(?:\\.\\d+)?)\\s*,\\s*(?:\\?|_+|\\.\\.\\.|…)\\s*\\??\\s*$");

    /** The next number of a sequence asked about, with its rule; null when the message isn't one, or no rule fits. */
    public static String next(String message) {
        if (message == null) {
            return null;
        }
        Matcher m = SEQUENCE.matcher(message);
        String list = m.find() ? m.group(1) : null;
        if (list == null) {
            Matcher b = BARE.matcher(message);
            list = b.matches() ? b.group(1) : null;
        }
        if (list == null) {
            return null;
        }
        List<Double> x = new ArrayList<Double>();
        for (String p : list.split("\\s*,\\s*")) {
            x.add(Double.parseDouble(p.trim()));
        }
        int n = x.size();
        // S1 steady step
        double d = x.get(1) - x.get(0);
        boolean steady = true;
        for (int i = 2; i < n; i++) {
            steady &= near(x.get(i) - x.get(i - 1), d);
        }
        if (steady) {
            return show(x.get(n - 1) + d) + ": each number is " + (d >= 0 ? show(d) + " more" : show(-d) + " less") + " than the one before.";
        }
        // S2 times
        if (x.get(0) != 0) {
            double r = x.get(1) / x.get(0);
            boolean times = true;
            for (int i = 2; i < n; i++) {
                times &= x.get(i - 1) != 0 && near(x.get(i) / x.get(i - 1), r);
            }
            if (times) {
                return show(x.get(n - 1) * r) + ": each number is " + show(r) + " times the one before.";
            }
        }
        // S3 squares and cubes of counting numbers
        for (int power = 2; power <= 3; power++) {
            double root = Math.round(Math.pow(Math.abs(x.get(0)), 1.0 / power));
            boolean fits = true;
            for (int i = 0; i < n; i++) {
                fits &= near(x.get(i), Math.pow(root + i, power));
            }
            if (fits) {
                return show(Math.pow(root + n, power)) + ": they are " + (power == 2 ? "squares" : "cubes") + " (" + show(root) + "×" + show(root)
                        + (power == 3 ? "×" + show(root) : "") + " and on), so next is " + show(root + n) + (power == 2 ? "²" : "³") + ".";
            }
        }
        // S4 each the sum of the two before
        boolean sum = n >= 4;
        for (int i = 2; i < n; i++) {
            sum &= near(x.get(i), x.get(i - 1) + x.get(i - 2));
        }
        if (sum) {
            return show(x.get(n - 1) + x.get(n - 2)) + ": each number is the sum of the two before it (" + show(x.get(n - 2)) + " + " + show(x.get(n - 1)) + ").";
        }
        // S5 the steps grow steadily
        if (n >= 4) {
            double d1 = x.get(1) - x.get(0), dd = (x.get(2) - x.get(1)) - d1;
            boolean grows = true;
            for (int i = 3; i < n; i++) {
                grows &= near((x.get(i) - x.get(i - 1)) - (x.get(i - 1) - x.get(i - 2)), dd);
            }
            if (grows && !near(dd, 0)) {
                double last = x.get(n - 1) - x.get(n - 2);
                return show(x.get(n - 1) + last + dd) + ": the steps grow by " + show(dd) + " each time (" + show(last) + ", then " + show(last + dd) + ").";
            }
        }
        return "I can't see a single rule in " + list.trim() + ": the steps don't keep the same difference, ratio or pattern.";
    }

    // ------------------------------------------------------------------ analogies

    private static final Pattern ANALOGY = Pattern.compile("(?i)^\\s*([a-z]+)\\s+is\\s+to\\s+([a-z]+)\\s+as\\s+([a-z]+)\\s+is\\s+to\\s+(?:what|\\?|_+|which word)\\s*\\??\\s*$");

    /**
     * What the dictionary says, given to the analogies: a word's opposites, its first meanings, and the words whose
     * meaning holds a phrase.
     */
    public interface Words {
        List<String> opposites(String word);

        List<String> meanings(String word);

        /** Common words (nouns, verbs) whose meaning holds every given word, the commonest first. */
        List<String> meaning(String... holding);
    }

    /** The answer to "A is to B as C is to ?", with the relation it carries over; null when the message isn't one. */
    public static String analogy(String message, Words w) {
        Matcher m = message == null || w == null ? null : ANALOGY.matcher(message);
        if (m == null || !m.matches()) {
            return null;
        }
        String a = m.group(1).toLowerCase(Locale.ROOT), b = m.group(2).toLowerCase(Locale.ROOT), c = m.group(3).toLowerCase(Locale.ROOT);
        // A1 opposites ("hot" and "cold")
        if (w.opposites(a).contains(b) || w.opposites(b).contains(a)) {
            List<String> o = w.opposites(c);
            if (!o.isEmpty()) {
                return o.get(0) + ": " + a + " and " + b + " are opposites, and the opposite of " + c + " is " + o.get(0) + ".";
            }
        }
        // young of ("puppy" is a young dog)
        for (String def : w.meanings(a)) {
            if (def.matches("(?i).*\\byoung\\b.*\\b" + Pattern.quote(b) + "s?\\b.*")) {
                for (String cd : w.meanings(c)) {
                    Matcher y = Pattern.compile("(?i)\\byoung (?:of (?:a|an|the) )?(?:domestic |wild )?([a-z]+)").matcher(cd);
                    if (y.find()) {
                        return y.group(1) + ": a " + a + " is a young " + b + ", and a " + c + " is a young " + y.group(1) + ".";
                    }
                }
            }
        }
        // covers ("a glove covers the hand"): the word whose meaning covers C
        for (String def : w.meanings(b)) {
            if (def.matches("(?i).*\\b(cover|covering|worn on)\\b.*\\b" + Pattern.quote(a) + "s?\\b.*")) {
                for (String x : w.meaning("cover", c)) {
                    if (!x.equals(c) && !x.equals(b)) {
                        return x + ": a " + b + " covers the " + a + ", and a " + x + " covers the " + c + ".";
                    }
                }
                for (String x : w.meaning("worn on", c)) {
                    if (!x.equals(c) && !x.equals(b)) {
                        return x + ": a " + b + " is worn on the " + a + ", and a " + x + " is worn on the " + c + ".";
                    }
                }
            }
        }
        // does ("a bird flies"): B is what A does; what C does, from its meaning
        boolean does = false;
        for (String def : w.meanings(a)) {
            does |= def.matches("(?i).*\\b" + Pattern.quote(b) + "(?:s|ing|es)?\\b.*");
        }
        if (does) {
            for (String def : w.meanings(c)) {
                Matcher v = Pattern.compile("(?i)\\b(swim|fly|run|walk|crawl|read|sing|write|cut|cook|drive|bark|purr|grow|shine|burn|play|listen|hear|see|eat|drink)"
                        + "(?:s|ming|ing|es)?\\b").matcher(def);
                if (v.find()) {
                    return v.group(1).toLowerCase(Locale.ROOT) + ": " + b + " is what a " + a + " does, and a " + c + " can " + v.group(1).toLowerCase(Locale.ROOT) + ".";
                }
            }
        }
        // opposites, and C has none listed ("summer"): its meaning with a word turned round ("the warmest season" →
        // "coldest" season), and the word that means that ("winter")
        if (w.opposites(a).contains(b) || w.opposites(b).contains(a)) {
            for (String def : w.meanings(c)) {
                String[] words = def.toLowerCase(Locale.ROOT).replaceAll("[^a-z ]", " ").trim().split("\\s+");
                String head = null;
                for (int i = words.length - 1; i >= 0 && head == null; i--) {
                    if (words[i].length() > 3 && w.opposites(words[i]).isEmpty() && !words[i].matches("year|time|part|kind|thing|state|quality")) {
                        head = words[i];
                    }
                }
                for (String t : words) {
                    for (String o : w.opposites(t)) {
                        for (String x : head == null ? w.meaning(o) : w.meaning(o, head)) {
                            if (!x.equals(c) && !x.equals(a) && !x.equals(b) && !x.contains(" ")) {
                                return x + ": " + a + " and " + b + " are opposites; a " + c + " is " + t + ", so its opposite is " + o + ": " + x + ".";
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9 * Math.max(1, Math.abs(a) + Math.abs(b));
    }

    private static String show(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) {
            return String.format(Locale.US, "%,d", (long) Math.rint(v));
        }
        return String.format(Locale.US, "%.4f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static List<String> list(Function<String, List<String>> f, String w) {
        List<String> l = f == null ? null : f.apply(w);
        return l == null ? new ArrayList<String>() : l;
    }
}
