package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Counts kept through a conversation, the way a person keeps them: "I have 3 apples", "I buy 4 more", "I eat 2", then
 * "How many apples do I have now?" is 5, with the working shown. One tally per thing. Pure Java.
 */
public final class Tally {
    private static final String NUM = "(\\d+|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen"
            + "|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|hundred|a dozen|dozen)";
    private static final String THING = "([a-z][a-z -]{1,30}?)";
    private static final String END = "\\s*[.!]*\\s*$";
    /** A count to start from: a number, not "a" ("I have a dream" is no count). */
    private static final String COUNT = "(\\d+|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen"
            + "|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|hundred|a dozen|one)";
    private static final Pattern HAVE = Pattern.compile("(?i)^(?:i|we)\\s+(?:have|'ve got|have got|own|keep)\\s+" + COUNT + "\\s+" + THING + END);
    private static final Pattern GAIN = Pattern.compile("(?i)^(?:then\\s+|and\\s+|now\\s+)?(?:i|we)\\s+(?:buy|bought|get|got|receive|received|pick|picked|find|found"
            + "|earn|earned|add|added|collect|collected|win|won|am given|was given|were given)\\s+(?:another\\s+)?" + NUM + "\\s+(more\\s+)?" + THING + "?(?:\\s+more)?" + END);
    private static final Pattern LOSE = Pattern.compile("(?i)^(?:then\\s+|and\\s+|now\\s+)?(?:i|we)\\s+(?:eat|ate|give away|gave away|give|gave|lose|lost|sell|sold|use|used"
            + "|spend|spent|drop|dropped|throw away|threw away|break|broke|donate|donated)\\s+" + NUM + "\\s*(?:of\\s+)?(?:my\\s+|the\\s+|them|of them)?\\s*" + THING + "?"
            + "(?:\\s+(?:to|away)\\b.*)?" + END);
    private static final Pattern ASK = Pattern.compile("(?i)^(?:so\\s+|and\\s+|now\\s+)?how many\\s+" + THING + "\\s+(?:do|have)\\s+(?:i|we)\\s+(?:have|got|get)?"
            + "\\s*(?:now|left|in total|altogether|remaining)?\\s*[?.!]*\\s*$");

    private final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
    private final Map<String, List<String>> working = new LinkedHashMap<String, List<String>>();
    private final Map<String, String> shown = new LinkedHashMap<String, String>();
    private String last;

    /** What was said, kept: the reply ("Noted: 7 apples"), or null when it isn't a count of things. */
    public synchronized String heard(String clause) {
        if (clause == null) {
            return null;
        }
        String c = clause.trim();
        Matcher m = HAVE.matcher(c);
        if (m.matches()) {
            return set(m.group(2), number(m.group(1)), false);
        }
        m = GAIN.matcher(c);
        if (m.matches()) {
            String thing = m.group(3) == null ? last : m.group(3);
            if (thing == null) {
                return null;
            }
            String k = key(thing);
            if (m.group(1).matches("(?i)an?") && !counts.containsKey(k)) {
                return null; // "I got a job": something said, not a count started
            }
            boolean more = m.group(2) != null || c.toLowerCase(Locale.ROOT).contains(" more") || counts.containsKey(k);
            return more ? change(thing, number(m.group(1))) : set(thing, number(m.group(1)), false);
        }
        m = LOSE.matcher(c);
        if (m.matches()) {
            String thing = m.group(2) == null || m.group(2).trim().matches("(?i)them|of them|it") ? last : m.group(2);
            if (thing == null || !counts.containsKey(key(thing))) {
                return null;
            }
            return change(thing, -number(m.group(1)));
        }
        return null;
    }

    /** The answer to "how many apples do I have?" from what was said; null when it isn't one, or nothing was. */
    public synchronized String asked(String question) {
        Matcher m = question == null ? null : ASK.matcher(question.trim());
        if (m == null || !m.matches()) {
            return null;
        }
        String k = key(m.group(1));
        Integer n = counts.get(k);
        if (n == null) {
            return null;
        }
        List<String> w = working.get(k);
        String name = n == 1 ? singular(shown.get(k)) : shown.get(k);
        return "You have " + n + " " + name + (w.size() > 1 ? ": " + String.join(", ", w) + " = " + n + "." : ".");
    }

    private String set(String thing, int n, boolean quiet) {
        if (n < 0) {
            return null;
        }
        String k = key(thing);
        counts.put(k, n);
        shown.put(k, plural(thing.trim()));
        working.put(k, new ArrayList<String>(java.util.Collections.singletonList(String.valueOf(n))));
        last = thing;
        return quiet ? null : "Noted: you have " + n + " " + (n == 1 ? singular(thing.trim()) : plural(thing.trim())) + ".";
    }

    private String change(String thing, int by) {
        String k = key(thing);
        if (!counts.containsKey(k)) {
            return by >= 0 ? set(thing, by, false) : null;
        }
        int n = Math.max(0, counts.get(k) + by);
        counts.put(k, n);
        working.get(k).add((by >= 0 ? "+ " : "− ") + Math.abs(by));
        last = thing;
        return "Noted: that makes " + n + " " + (n == 1 ? singular(shown.get(k)) : shown.get(k)) + ".";
    }

    static String key(String thing) {
        return singular(thing.trim().toLowerCase(Locale.ROOT).replaceFirst("^(?:more|of|my|the)\\s+", ""));
    }

    static String singular(String s) {
        if (s == null) {
            return "";
        }
        if (s.matches(".*[^s]ies$")) {
            return s.substring(0, s.length() - 3) + "y";
        }
        if (s.matches(".*(ches|shes|sses|xes)$")) {
            return s.substring(0, s.length() - 2);
        }
        return s.endsWith("s") && !s.endsWith("ss") ? s.substring(0, s.length() - 1) : s;
    }

    static String plural(String s) {
        String one = singular(s);
        if (one.matches(".*[^aeiou]y$")) {
            return one.substring(0, one.length() - 1) + "ies";
        }
        return one.matches(".*(ch|sh|ss|x)$") ? one + "es" : one + "s";
    }

    static int number(String w) {
        String x = w.toLowerCase(Locale.ROOT).trim();
        if (x.matches("\\d+")) {
            return Integer.parseInt(x);
        }
        String[] words = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
                "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
        for (int i = 0; i < words.length; i++) {
            if (words[i].equals(x)) {
                return i;
            }
        }
        switch (x) {
            case "a": case "an": return 1;
            case "thirty": return 30;
            case "forty": return 40;
            case "fifty": return 50;
            case "hundred": return 100;
            case "a dozen": case "dozen": return 12;
            default: return 0;
        }
    }
}
