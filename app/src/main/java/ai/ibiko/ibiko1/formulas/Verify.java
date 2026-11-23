package ai.ibiko.ibiko1.formulas;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Yes-or-no questions checked against the facts, answered yes or no first: "Is Lagos the capital of Nigeria?" is "No.
 * The capital of Nigeria is Abuja, not Lagos." — not an article about Abuja. The claim in the question is read (who,
 * which relation, what value), the fact is looked up, and the two compared; the true fact is said either way. A claim
 * it has no fact for is not answered here (null): it isn't guessed. Pure Java over a fact lookup (thing, relation).
 *
 * <pre>
 * Y1  is A the capital / largest city / currency / president / language of B     ·  is the R of B A
 * Y2  was A born in YEAR                                                          born
 * Y3  was A born in PLACE                                                         birth_place
 * Y4  did A found B · was B founded by A                                          founder
 * Y5  is A in B · is A located in B                                               location
 * Y6  is L spoken in B                                                            language
 * Y7  is A married to B                                                           spouse
 * Y8  is A taller / higher than N m                                               elevation
 * </pre>
 */
public final class Verify {
    private Verify() {
    }

    /** The answer: yes or no, and the sentence that says the fact. */
    public static final class Said {
        public final boolean yes;
        public final String text, thing, relation;

        Said(boolean yes, String text, String thing, String relation) {
            this.yes = yes;
            this.text = text;
            this.thing = thing;
            this.relation = relation;
        }
    }

    private static final String N = "((?:the\\s+)?[\\p{Lu}0-9][\\p{L}0-9.'’-]*(?:\\s+(?:(?:of|de|da|and|the)\\s+)?[\\p{Lu}0-9][\\p{L}0-9.'’-]*)*)";
    private static final String V = "(.{2,60}?)";
    private static final String ROLE = "(capital(?: city)?|largest city|biggest city|currency|president|leader|head of state|king|queen|"
            + "prime minister|official language|main language|language)";

    private static final Pattern Y1 = Pattern.compile("^(?:is|was|are|were)\\s+" + V + "\\s+(?:the\\s+)?" + ROLE + "\\s+(?:of|in)\\s+" + N + "\\s*\\??$");
    private static final Pattern Y1B = Pattern.compile("^(?:is|was)\\s+the\\s+" + ROLE + "\\s+(?:of|in)\\s+" + N + "\\s+" + V + "\\s*\\??$");
    private static final Pattern Y2 = Pattern.compile("^(?:was|is)\\s+" + N + "\\s+born\\s+(?:in|on)\\s+(.{2,40}?)\\s*\\??$");
    private static final Pattern Y4 = Pattern.compile("^did\\s+" + N + "\\s+(?:found|start|create|set up|co-?found)\\s+" + N + "\\s*\\??$");
    private static final Pattern Y4B = Pattern.compile("^(?:was|is)\\s+" + N + "\\s+(?:founded|started|created|set up|co-?founded)\\s+by\\s+" + N + "\\s*\\??$");
    private static final Pattern Y5 = Pattern.compile("^(?:is|are)\\s+" + N + "\\s+(?:located\\s+|found\\s+|situated\\s+)?in\\s+" + N + "\\s*\\??$");
    private static final Pattern Y6 = Pattern.compile("^(?:is|are)\\s+" + N + "\\s+(?:spoken|used|an official language)\\s+in\\s+" + N + "\\s*\\??$");
    private static final Pattern Y7 = Pattern.compile("^(?:is|was)\\s+" + N + "\\s+married\\s+to\\s+" + N + "\\s*\\??$");
    private static final Pattern Y8 = Pattern.compile("^(?:is|was)\\s+" + N + "\\s+(taller|higher|shorter|lower)\\s+than\\s+([0-9][0-9,.]*)\\s*(m|metres|meters|ft|feet)?\\s*\\??$");

    /** Y1–Y8: the yes or no for a claim, from {@code fact}; null when it isn't a claim it reads, or no fact says. */
    public static Said check(String question, BiFunction<String, String, String> fact) {
        if (question == null || fact == null) {
            return null;
        }
        String q = question.trim().replaceAll("[’`]", "'");
        // the words of the claim in small letters, so a name is told by its capital ("the dollar" is no name)
        q = q.isEmpty() ? q : Character.toLowerCase(q.charAt(0)) + q.substring(1);
        Matcher m;
        if ((m = Y1.matcher(q)).matches()) {
            return role(m.group(3), m.group(2), m.group(1), fact);
        }
        if ((m = Y1B.matcher(q)).matches()) {
            return role(m.group(2), m.group(1), m.group(3), fact);
        }
        if ((m = Y2.matcher(q)).matches()) {
            String who = bare(m.group(1)), when = m.group(2).trim();
            if (when.matches("\\d{3,4}") || when.matches("(?i).*\\b\\d{3,4}$")) {
                String born = fact.apply(who, "born");
                if (born == null) {
                    return null;
                }
                String year = when.replaceAll(".*?(\\d{3,4})$", "$1");
                boolean yes = born.contains(year);
                return new Said(yes, (yes ? "Yes. " : "No. ") + who + " was born " + onIn(born) + (yes ? "." : ", not in " + year + "."), who, "born");
            }
            String place = fact.apply(who, "birth_place");
            if (place == null) {
                return null;
            }
            boolean yes = has(place, when);
            String shown = place.replaceAll("\\s*\\([^()]*\\)", "").trim();
            return new Said(yes, (yes ? "Yes. " : "No. ") + who + " was born in " + shown + (yes ? "." : ", not in " + bare(when) + "."), who, "birth_place");
        }
        if ((m = Y4.matcher(q)).matches()) {
            return founder(bare(m.group(2)), bare(m.group(1)), fact);
        }
        if ((m = Y4B.matcher(q)).matches()) {
            return founder(bare(m.group(1)), bare(m.group(2)), fact);
        }
        if ((m = Y6.matcher(q)).matches()) {
            String lang = bare(m.group(1)), where = bare(m.group(2));
            String langs = fact.apply(where, "language");
            if (langs == null) {
                return null;
            }
            boolean yes = has(langs, lang);
            return new Said(yes, (yes ? "Yes. " : "No. ") + "The language of " + where + ": " + clean(langs) + ".", where, "language");
        }
        if ((m = Y7.matcher(q)).matches()) {
            String who = bare(m.group(1)), to = bare(m.group(2));
            String spouse = fact.apply(who, "spouse");
            if (spouse == null) {
                return null;
            }
            boolean yes = has(spouse, to);
            return new Said(yes, (yes ? "Yes. " : "No. ") + who + "'s spouse is " + clean(spouse) + (yes ? "." : ", not " + to + "."), who, "spouse");
        }
        if ((m = Y8.matcher(q)).matches()) {
            String what = bare(m.group(1));
            String h = fact.apply(what, "elevation");
            if (h == null) {
                h = fact.apply(what, "height");
            }
            Double metres = h == null ? null : metres(h);
            if (metres == null) {
                return null;
            }
            double asked = Double.parseDouble(m.group(3).replace(",", ""));
            if (m.group(4) != null && m.group(4).toLowerCase(Locale.ROOT).startsWith("f")) {
                asked *= 0.3048;
            }
            boolean taller = m.group(2).toLowerCase(Locale.ROOT).matches("taller|higher");
            boolean yes = taller ? metres > asked : metres < asked;
            return new Said(yes, (yes ? "Yes. " : "No. ") + what + " is " + clean(h) + " high.", what, "elevation");
        }
        if ((m = Y5.matcher(q)).matches()) {
            String what = bare(m.group(1)), where = bare(m.group(2));
            String at = fact.apply(what, "location");
            if (at == null) {
                return null;
            }
            boolean yes = has(at, where);
            return new Said(yes, (yes ? "Yes. " : "No. ") + what + " is in " + clean(at) + ".", what, "location");
        }
        return null;
    }

    private static Said role(String of, String role, String claimed, BiFunction<String, String, String> fact) {
        String thing = bare(of), r = role.toLowerCase(Locale.ROOT);
        String rel = r.startsWith("capital") ? "capital" : r.matches("largest city|biggest city") ? "largest_city" : r.equals("currency") ? "currency"
                : r.contains("language") ? "language" : "leader";
        String v = fact.apply(thing, rel);
        if (v == null) {
            return null;
        }
        String claim = bare(claimed);
        boolean yes = rel.equals("language") || rel.equals("currency") ? has(v, claim) : has(first(v), claim);
        String said = (rel.equals("language") ? "The language of " + thing + ": " + clean(v)
                : "The " + (rel.equals("largest_city") ? "largest city in" : rel.equals("leader") ? r + " of" : r + " of") + " " + thing + " is "
                + (rel.equals("currency") ? "the " : "") + first(v)) + (yes ? "" : ", not " + claim);
        return new Said(yes, (yes ? "Yes. " : "No. ") + said + ".", thing, rel);
    }

    private static Said founder(String org, String who, BiFunction<String, String, String> fact) {
        String f = fact.apply(org, "founder");
        if (f == null) {
            return null;
        }
        boolean yes = has(f, who);
        return new Said(yes, (yes ? "Yes. " : "No. ") + org + " was founded by " + and(clean(f)) + ".", org, "founder");
    }

    /** The value names the claim: every word of it (in any order, "the" and case set aside), or a surname alone. */
    static boolean has(String value, String claim) {
        String v = " " + value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", " ") + " ";
        String c = claim.toLowerCase(Locale.ROOT).replaceFirst("^the\\s+", "").replaceAll("[^\\p{L}0-9]+", " ").trim();
        if (c.isEmpty()) {
            return false;
        }
        for (String w : c.split(" ")) {
            if (!v.contains(" " + w + " ")) {
                return false;
            }
        }
        return true;
    }

    static String first(String v) {
        return clean(v.replaceAll("\\s*\\([^()]*\\)", "").split("\\s*[,;|]\\s*")[0]);
    }

    private static String clean(String v) {
        return v.replaceAll("[\\[\\]\"]", "").replaceAll("\\s+", " ").trim();
    }

    private static String bare(String s) {
        return s.trim().replaceFirst("(?i)^the\\s+", "").replaceAll("[?.!,]+$", "").trim();
    }

    private static String onIn(String date) {
        return date.trim().matches("\\d{3,4}") ? "in " + date.trim() : "on " + date.trim();
    }

    private static String and(String list) {
        int last = list.lastIndexOf(", ");
        return last < 0 ? list : list.substring(0, last) + " and " + list.substring(last + 2);
    }

    private static Double metres(String h) {
        Matcher m = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(m|metres|meters|ft|feet)?").matcher(h);
        if (!m.find()) {
            return null;
        }
        double v = Double.parseDouble(m.group(1).replace(",", ""));
        return m.group(2) != null && m.group(2).startsWith("f") ? v * 0.3048 : v;
    }
}
