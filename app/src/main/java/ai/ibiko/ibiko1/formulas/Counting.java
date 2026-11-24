package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A counting story worked through the way a child is taught to: "I have 3 apples and eat one: how many are left?" The
 * quantity is followed from one event to the next (has, buys, eats, gives away, loses, shares out), each step said with
 * its sum, and the question answered in its own words. A story that would go below zero, or that asks before saying how
 * many there were, is said to be so instead of guessed at. Pure Java.
 *
 * <pre>
 * K1  start    "I have 3 apples", "Ada had 12 sweets", "a box holds 4 bags of 6 eggs"
 * K2  events   more (buys, gets, finds, is given, picks), fewer (eats, gives away, loses, sells, uses, spends),
 *              half / twice / double, shared equally among N
 * K3  answer   how many are left, in total, does each get; the steps, then the answer
 * </pre>
 */
public final class Counting {
    private Counting() {
    }

    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
        "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty"};
    private static final String N = "(\\d+|a|an|" + String.join("|", WORDS) + "|a dozen|half(?: of (?:them|it|the \\w+))?|all(?: of)?(?: them| it)?)";

    private static int value(String w) {
        String s = w.toLowerCase(Locale.ROOT).trim();
        if (s.matches("\\d+")) {
            return Integer.parseInt(s);
        }
        if (s.equals("a") || s.equals("an")) {
            return 1;
        }
        if (s.equals("a dozen")) {
            return 12;
        }
        for (int i = 0; i < WORDS.length; i++) {
            if (WORDS[i].equals(s)) {
                return i;
            }
        }
        return -1;
    }

    private static final String WHO = "(?:i|you|we|they|he|she|[A-Z][a-z]+)";
    private static final Pattern START = Pattern.compile("(?i)\\b" + WHO + " (?:has|have|had|got|own|owns|start(?:s|ed)? with|bought|picked|collected|baked|made)"
            + " (\\d+|a dozen|" + String.join("|", WORDS) + ") (?:(\\d+|" + String.join("|", WORDS) + ") )?([a-z]+(?: [a-z]+)?)?");
    private static final Pattern GROUPS = Pattern.compile("(?i)\\b(\\d+|" + String.join("|", WORDS) + ") (?:bags|boxes|packs|baskets|rows|groups|plates|crates|trays|bunches)"
            + " (?:of|with|each with|that each (?:have|hold|contain)|holding|containing) (\\d+|" + String.join("|", WORDS) + ")(?: each)? ([a-z]+)");
    private static final Pattern MORE = Pattern.compile("(?i)\\b(?:buys?|bought|gets?|got|finds?|found|receives?|received|(?:is|was|were|am|are) given|picks?|picked"
            + "|adds?|added|earns?|earned|wins?|won|makes?|made|bakes?|baked|collects?|collected) (?:another |" + "an extra )?" + N + "(?: more)?\\b");
    private static final Pattern FEWER = Pattern.compile("(?i)\\b(?:eats?|ate|gives? away|gave away|gives?|gave|loses?|lost|sells?|sold|uses?|used|spends?|spent"
            + "|drops?|dropped|breaks?|broke|takes? away|took away|throws? away|threw away|donates?|donated|shares?|shared) " + N + "\\b(?! more)");
    private static final Pattern TIMES = Pattern.compile("(?i)\\b(doubles?|doubled|triples?|tripled|twice as many|three times as many)\\b");
    private static final Pattern SHARE = Pattern.compile("(?i)\\b(?:shares?|shared|divides?|divided|splits?|split) (?:them|it|the \\w+|all of them)? ?(?:equally |evenly )?"
            + "(?:among|between|with) (\\d+|" + String.join("|", WORDS) + ")(?: (?:people|friends|children|kids|students|of them))?");
    private static final Pattern ASKED = Pattern.compile("(?i)\\bhow many\\b[^?.]*?(?:\\b(left|remain|now|in total|altogether|all together|each|does \\w+ have|do (?:i|you|we|they) have|are there)\\b|\\?)");

    /** K1–K3: the story worked through, or null when it isn't a counting story. */
    public static String reply(String message) {
        if (message == null || message.length() > 600) {
            return null;
        }
        Matcher ask = ASKED.matcher(message);
        if (!ask.find()) {
            return null;
        }
        String asked = ask.group(1) == null ? "" : ask.group(1).toLowerCase(Locale.ROOT);
        String story = message;
        List<String> steps = new ArrayList<String>();
        int total;
        String thing;
        Matcher g = GROUPS.matcher(story);
        Matcher s = START.matcher(story);
        int from;
        if (g.find()) {
            int groups = value(g.group(1)), each = value(g.group(2));
            total = groups * each;
            thing = g.group(3);
            steps.add(groups + " groups of " + each + ": " + groups + " × " + each + " = " + total);
            from = g.end();
        } else if (s.find()) {
            int first = value(s.group(1));
            if (s.group(2) != null && value(s.group(2)) > 0) {
                return null; // "has 3 5 apples": not read surely
            }
            total = first;
            thing = s.group(3) == null ? "" : s.group(3).replaceFirst(" (?:and|but|then)$", "");
            steps.add("Start with " + total + (thing.isEmpty() ? "" : " " + thing));
            from = s.end();
        } else {
            return null;
        }
        if (total < 0) {
            return null;
        }
        // the events after the start, in the order told
        String rest = story.substring(from);
        Pattern any = Pattern.compile(MORE.pattern() + "|" + FEWER.pattern().replace("(?i)", "") + "|" + TIMES.pattern().replace("(?i)", "")
                + "|" + SHARE.pattern().replace("(?i)", ""), Pattern.CASE_INSENSITIVE);
        Matcher e = any.matcher(rest);
        Integer shareAmong = null;
        int events = 0;
        while (e.find()) {
            String ev = e.group();
            Matcher x;
            if ((x = SHARE.matcher(ev)).matches()) {
                shareAmong = value(x.group(1));
                events++;
                continue;
            }
            if ((x = TIMES.matcher(ev)).matches()) {
                int by = x.group(1).toLowerCase(Locale.ROOT).matches("doubles?|doubled|twice as many") ? 2 : 3;
                steps.add((by == 2 ? "Doubled" : "Tripled") + ": " + total + " × " + by + " = " + (total * by));
                total *= by;
                events++;
                continue;
            }
            boolean more = MORE.matcher(ev).matches();
            Matcher num = Pattern.compile(N + "(?: more)?$", Pattern.CASE_INSENSITIVE).matcher(ev);
            if (!num.find()) {
                continue;
            }
            String w = num.group(1).toLowerCase(Locale.ROOT);
            int n = w.startsWith("half") ? total / 2 : w.startsWith("all") ? total : value(w);
            if (n < 0) {
                continue;
            }
            if (w.startsWith("half") && total % 2 != 0) {
                return "Half of " + total + " isn't a whole number, so the story doesn't work out evenly: did you mean a different number?";
            }
            String verb = ev.substring(0, num.start()).trim().toLowerCase(Locale.ROOT);
            if (more) {
                steps.add(cap(verb) + " " + n + ": " + total + " + " + n + " = " + (total + n));
                total += n;
            } else {
                if (n > total) {
                    return "That can't happen as told: there are only " + total + (thing.isEmpty() ? "" : " " + thing) + " by then, so " + verb + " " + n
                            + " would go below zero. Could you check the numbers?";
                }
                steps.add(cap(verb) + " " + n + ": " + total + " − " + n + " = " + (total - n));
                total -= n;
            }
            events++;
        }
        if (events == 0 && !asked.matches("in total|altogether|all together|are there") && shareAmong == null && steps.size() < 2) {
            return null; // nothing happens in it: not a story to work through
        }
        String things = thing.isEmpty() ? "" : " " + (total == 1 ? one(thing) : thing);
        String answer;
        if (shareAmong != null || asked.equals("each")) {
            int among = shareAmong == null ? 0 : shareAmong;
            if (among <= 0) {
                return null;
            }
            int each = total / among, over = total % among;
            steps.add("Shared equally among " + among + ": " + total + " ÷ " + among + " = " + each + (over > 0 ? ", with " + over + " left over" : ""));
            answer = "Each gets " + each + things + (over > 0 ? ", and " + over + " " + (over == 1 ? "is" : "are") + " left over" : "") + ".";
        } else if (asked.equals("left") || asked.equals("remain")) {
            answer = total + things + (total == 1 ? " is" : " are") + " left.";
        } else {
            answer = "That makes " + total + things + ".";
        }
        StringBuilder b = new StringBuilder(answer).append("\n\n");
        for (String step : steps) {
            b.append("• ").append(step).append('\n');
        }
        return b.toString().trim();
    }

    /** One of them: "sweets" → "sweet", "boxes" → "box", "mangoes" → "mango". */
    private static String one(String things) {
        String[] w = things.split(" ");
        String last = w[w.length - 1];
        String single = last.matches(".*(?:ch|sh|x|ss|o)es$") ? last.substring(0, last.length() - 2)
                : last.endsWith("ies") ? last.substring(0, last.length() - 3) + "y" : last.endsWith("s") && !last.endsWith("ss") ? last.substring(0, last.length() - 1) : last;
        w[w.length - 1] = single;
        return String.join(" ", w);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
