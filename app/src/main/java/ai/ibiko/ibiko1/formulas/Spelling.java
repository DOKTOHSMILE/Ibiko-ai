package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "How do you spell necessary?": the word spelled out letter by letter, with what trips people up (the doubled letters,
 * the letters that sound like others). A word the dictionary doesn't have ("neccessary") is matched to the real word one or
 * two letters away, and that one is spelled, with what was different. Pure Java: the dictionary is given as a test.
 */
public final class Spelling {
    private Spelling() {
    }

    private static final Pattern ASKS = Pattern.compile("(?i)^\\s*(?:how (?:do (?:you|i|we|one)|to|does one|should i|would you|can i) spell"
            + "|(?:can you |please )?spell(?: out)?|what(?:'s| is) the (?:correct |right |proper )?spelling (?:of|for)"
            + "|is (?:it|this) (?:spelt|spelled) (?:right|correctly)[:,]?|is (?:it|this) spelled|is (?:it|this) spelt)"
            + "\\s+(?:the word\\s+)?[\"“'‘]?([\\p{L}'-]{2,30})[\"”'’]?\\s*(?:correctly|right)?\\s*[?.!]*\\s*$");

    /** The word a message asks to be spelled, or null. */
    public static String asked(String message) {
        Matcher m = message == null ? null : ASKS.matcher(message.trim());
        return m != null && m.matches() ? m.group(1).toLowerCase(Locale.ROOT).replaceAll("^['-]+|['-]+$", "") : null; // the quotes round it aren't in it
    }

    /** The word spelled, or the nearest real word when this one isn't one; null when there is none near it. */
    public static String reply(String word, Predicate<String> isWord) {
        String w = word.toLowerCase(Locale.ROOT);
        if (isWord.test(w)) {
            return spelled(w) + notes(w);
        }
        List<String> near = nearest(w, isWord);
        if (near.isEmpty()) {
            return null;
        }
        String best = near.get(0);
        return "I think you mean " + best + ": " + spelled(best) + notes(best)
                + (near.size() > 1 ? " (Or did you mean " + String.join(" or ", near.subList(1, Math.min(3, near.size()))) + "?)" : "");
    }

    private static String spelled(String w) {
        StringBuilder b = new StringBuilder();
        for (char c : w.toCharArray()) {
            b.append(b.length() == 0 ? "" : "-").append(Character.toUpperCase(c));
        }
        return w + " is spelled " + b + " (" + w.replaceAll("[^\\p{L}]", "").length() + " letters).";
    }

    /** What trips people up: the doubled letters, and the single ones next to them that people double. */
    private static String notes(String w) {
        List<String> doubled = new ArrayList<String>();
        for (int i = 0; i + 1 < w.length(); i++) {
            if (w.charAt(i) == w.charAt(i + 1) && Character.isLetter(w.charAt(i))) {
                doubled.add("two " + w.charAt(i) + "'s");
                i++;
            }
        }
        if (doubled.isEmpty()) {
            return "";
        }
        // a single letter that people often double, before the doubled one: "one c, two s's" (necessary), "one s" (dessert)
        List<String> single = new ArrayList<String>();
        for (char c : new char[]{'c', 's', 'r', 'l', 'm', 'n', 't', 'p'}) {
            int n = w.length() - w.replace(String.valueOf(c), "").length();
            if (n == 1 && !doubled.contains("two " + c + "'s")) {
                single.add("one " + c);
            }
        }
        List<String> all = new ArrayList<String>(single.subList(0, Math.min(1, single.size())));
        all.addAll(doubled);
        return " Watch for: " + String.join(", ", all) + ".";
    }

    /** Real words one letter away, then two, closest first. */
    static List<String> nearest(String w, Predicate<String> isWord) {
        Set<String> one = edits(w);
        List<String> out = new ArrayList<String>();
        for (String e : one) {
            if (isWord.test(e) && !out.contains(e)) {
                out.add(e);
            }
        }
        if (!out.isEmpty() || w.length() > 14) {
            return out;
        }
        for (String e : one) {
            for (String f : edits(e)) {
                if (isWord.test(f) && !out.contains(f)) {
                    out.add(f);
                }
            }
            if (out.size() >= 3) {
                break;
            }
        }
        return out;
    }

    private static Set<String> edits(String w) {
        Set<String> out = new LinkedHashSet<String>();
        String abc = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i <= w.length(); i++) {
            String a = w.substring(0, i), b = w.substring(i);
            if (!b.isEmpty()) {
                out.add(a + b.substring(1)); // a letter dropped (people add one: "neccessary")
            }
            if (b.length() > 1) {
                out.add(a + b.charAt(1) + b.charAt(0) + b.substring(2)); // two swapped
            }
            for (char c : abc.toCharArray()) {
                if (!b.isEmpty()) {
                    out.add(a + c + b.substring(1)); // one changed
                }
                out.add(a + c + b); // one added
            }
        }
        out.remove(w);
        return out;
    }

    // ------------------------------------------------------------------------------------------------- the future

    private static final Pattern FUTURE = Pattern.compile("(?i)^\\s*(?:who|which (?:team|country|party|side|one)|what team|what country)\\s+will\\s+"
            + "(?:win|be (?:the )?(?:next|new) \\w+|become (?:the )?(?:next|new) \\w+|lift|take)\\b.*"
            + "|^\\s*(?:what|which) will (?:the )?(?:score|result|winning numbers?|exchange rate|price) be\\b.*"
            + "|^\\s*(?:what (?:will|are) (?:the )?(?:winning )?(?:lottery|lotto) numbers)\\b.*"
            + "|^\\s*will (?:\\p{Lu}[\\p{L}]+|we|they|i|it) win\\b.*");

    /** A question about what hasn't happened yet ("who will win the next World Cup?"): said plainly, or null. */
    public static String future(String message) {
        if (message == null || !FUTURE.matcher(message.trim()).matches()) {
            return null;
        }
        return "No one can know that yet: it hasn't happened. I can tell you what has happened (past results, who's done well"
                + " lately), which is what forecasts are built on, but anyone who says they know for sure is guessing.";
    }
}
