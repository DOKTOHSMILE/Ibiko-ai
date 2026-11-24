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
 * Reasoning from what the person states, the way logic is taught: "All cats are mammals. Tom is a cat. Is Tom a mammal?"
 * The statements are read as rules and facts; the question is answered from them alone (not looked up), with the steps
 * said. A conclusion that doesn't follow is said not to ("The ground is wet. Did it rain?": not necessarily), and when the
 * statements don't decide it, it says so. Pure Java.
 *
 * <pre>
 * L1  kinds      "All A are B", "Every A is a B": what is an A is a B; "No A are B": what is an A isn't a B
 * L2  having     "All A have B": what is an A has B; "X has no B", "X doesn't have B"
 * L3  members    "X is a A", "X is an A"
 * L4  if-then    "If P, (then) Q." and "P." give Q; Q alone doesn't give P (affirming what follows proves nothing)
 * L5  answer     "Is X a B?", "Does X have B?", "Is Q?" / "Did P?": yes with the steps, no with the steps, or can't tell
 * </pre>
 */
public final class Logic {
    private Logic() {
    }

    /** What was stated: kinds inside kinds, kinds apart, what kinds have, who is what, who lacks what, if-thens, what happened. */
    public static final class Known {
        final List<String[]> isa = new ArrayList<String[]>();        // {A, B}: every A is a B
        final List<String[]> apart = new ArrayList<String[]>();      // {A, B}: no A is a B
        final List<String[]> has = new ArrayList<String[]>();        // {A, B}: every A has B
        final List<String[]> member = new ArrayList<String[]>();     // {X, A}: X is an A
        final List<String[]> lacks = new ArrayList<String[]>();      // {X, B}: X has no B
        final List<String[]> rules = new ArrayList<String[]>();      // {P, Q, as said}: if P then Q
        final List<String> happened = new ArrayList<String>();       // P: stated as so
        final List<String[]> does = new ArrayList<String[]>();       // {A, verb, rest, as said}: every A does it ("pay a stall fee")

        public boolean isEmpty() {
            return isa.isEmpty() && apart.isEmpty() && has.isEmpty() && member.isEmpty() && lacks.isEmpty() && rules.isEmpty() && does.isEmpty();
        }

        public void addAll(Known k) {
            isa.addAll(k.isa);
            apart.addAll(k.apart);
            has.addAll(k.has);
            member.addAll(k.member);
            lacks.addAll(k.lacks);
            rules.addAll(k.rules);
            happened.addAll(k.happened);
            does.addAll(k.does);
        }
    }

    private static final String NOUN = "([a-z][a-z-]*(?: [a-z][a-z-]*)?)";
    private static final String WHO = "((?:[Aa] |[Aa]n |[Tt]he )?[A-Za-z][\\p{L}'-]*(?: [A-Z][\\p{L}'-]*)?)";
    private static final Pattern ALL = Pattern.compile("(?i)^(?:all|every|each)\\s+" + NOUN + "\\s+(?:are|is)\\s+(?:a |an )?" + NOUN + "$");
    private static final Pattern NONE = Pattern.compile("(?i)^no\\s+" + NOUN + "\\s+(?:are|is)\\s+(?:a |an )?" + NOUN + "$");
    private static final Pattern ALL_HAVE = Pattern.compile("(?i)^(?:all|every|each)\\s+" + NOUN + "\\s+(?:have|has)\\s+(?:a |an |some )?" + NOUN + "$");
    private static final Pattern IS_A = Pattern.compile("^" + WHO + "\\s+is\\s+(?:a|an)\\s+" + NOUN + "$");
    private static final Pattern LACKS = Pattern.compile("(?i)^" + WHO + "\\s+(?:has no|does not have|doesn't have|has not got|hasn't got|lacks)\\s+(?:a |an |any )?" + NOUN + "$");
    /** "All traders in the market pay a stall fee": what every one of a kind does (any verb but be and have). */
    private static final Pattern ALL_DO = Pattern.compile("(?i)^(?:all|every|each)\\s+([a-z][a-z-]*)(?:\\s+(?:in|at|of|on|from)\\s+(?:the\\s+)?[a-z]+(?:\\s+[a-z]+)??)?\\s+"
            + "(?!are\\b|is\\b|have\\b|has\\b|were\\b|was\\b)([a-z]+)\\s+(.{2,60})$");
    // case kept: a name is its capitals ("Does Tunde pay…": "Tunde", not "Tunde pay")
    private static final Pattern ASK_DO = Pattern.compile("^\\s*(?:[Dd]oes|[Dd]o|[Dd]id|[Ww]ill|[Mm]ust|[Ss]hould)\\s+" + WHO + "\\s+([a-z]+)\\s+(.{2,60}?)\\s*\\?\\s*$");
    private static final Pattern IF = Pattern.compile("(?i)^if\\s+(.{3,80}?),\\s*(?:then\\s+)?(.{3,80})$");

    /** "If all birds have feathers and a penguin is a bird, does a penguin have feathers?" asked in one sentence. */
    private static final Pattern IF_ASK = Pattern.compile("(?i)^\\s*(?:if|given that|suppose|supposing|assuming)\\s+(.{6,200}?),\\s*(?:then\\s+)?"
            + "((?:is|are|was|were|does|do|did|can|could|will|would|has|have|must)\\b.{3,120}\\?)\\s*$");

    /**
     * The premises of a one-sentence question said first, as statements ("All birds have feathers. A penguin is a bird.
     * Does a penguin have feathers?"), when each is one Ibiko reasons from; the text as it was otherwise.
     */
    static String premisesFirst(String text) {
        Matcher m = text == null ? null : IF_ASK.matcher(text.trim());
        if (m == null || !m.matches()) {
            return text;
        }
        List<String> said = new ArrayList<String>();
        for (String p : m.group(1).split("(?i),?\\s+and\\s+|;\\s*")) {
            String c = p.trim();
            if (!(ALL.matcher(c).matches() || ALL_HAVE.matcher(c).matches() || NONE.matcher(c).matches() || IS_A.matcher(c).matches()
                    || LACKS.matcher(c).matches())) {
                return text; // "if it rains and …": an if-then, read as one
            }
            said.add(Character.toUpperCase(c.charAt(0)) + c.substring(1) + ".");
        }
        String q = m.group(2).trim();
        return String.join(" ", said) + " " + Character.toUpperCase(q.charAt(0)) + q.substring(1);
    }

    /** L1–L4: what a text states (its questions are left out). */
    public static Known read(String text) {
        Known k = new Known();
        if (text == null) {
            return k;
        }
        text = premisesFirst(text);
        for (String raw : text.split("(?<=[.!?])\\s+|\\n+")) {
            String s = raw.trim();
            if (s.endsWith("?") || s.isEmpty()) {
                continue;
            }
            s = s.replaceAll("[.!]+$", "").trim();
            Matcher m;
            if ((m = ALL_HAVE.matcher(s)).matches()) {
                k.has.add(new String[]{kind(m.group(1)), said(m.group(2))}); // what they have, as said ("feathers")
            } else if ((m = ALL_DO.matcher(s)).matches()) {
                k.does.add(new String[]{kind(m.group(1)), m.group(2).toLowerCase(Locale.ROOT), m.group(3).toLowerCase(Locale.ROOT).trim(), s});
            } else if ((m = ALL.matcher(s)).matches()) {
                k.isa.add(new String[]{kind(m.group(1)), kind(m.group(2))});
            } else if ((m = NONE.matcher(s)).matches()) {
                k.apart.add(new String[]{kind(m.group(1)), kind(m.group(2))});
            } else if ((m = LACKS.matcher(s)).matches()) {
                k.lacks.add(new String[]{who(m.group(1)), said(m.group(2))});
            } else if ((m = IF.matcher(s)).matches()) {
                k.rules.add(new String[]{m.group(1).trim(), m.group(2).trim(), s});
            } else if ((m = IS_A.matcher(s)).matches()) {
                k.member.add(new String[]{who(m.group(1)), kind(m.group(2))});
            } else if (s.split("\\s+").length <= 8) {
                k.happened.add(s); // "It rained": something stated as so, for the if-thens
            }
        }
        return k;
    }

    private static String kind(String w) {
        return Records.singular(w.trim().toLowerCase(Locale.ROOT));
    }

    private static String said(String w) {
        return w.trim().toLowerCase(Locale.ROOT);
    }

    /** The same thing had, however it's said ("feathers", "feather"). */
    private static boolean same(String a, String b) {
        return kind(a).equals(kind(b));
    }

    private static String who(String w) {
        return w.trim().replaceFirst("(?i)^(a|an|the)\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static final Pattern ASK_IS = Pattern.compile("(?i)^\\s*(?:is|was)\\s+" + WHO + "\\s+(?:a|an)\\s+" + NOUN + "\\s*\\?\\s*$");
    private static final Pattern ASK_HAS = Pattern.compile("(?i)^\\s*(?:does|did)\\s+" + WHO + "\\s+have\\s+(?:a |an |any )?" + NOUN + "\\s*\\?\\s*$");
    private static final Pattern ASK_ANY = Pattern.compile("(?i)^\\s*(?:is|are|was|were|does|do|did|will|has|have)\\s+(.{3,80}?)\\s*\\?\\s*$");

    /** L5: the answer to the text's last question from what was stated (here and before), or null: not a question of logic. */
    public static String answer(String text, Known before) {
        if (text == null) {
            return null;
        }
        text = premisesFirst(text);
        String[] parts = text.trim().split("(?<=[.!?])\\s+");
        String q = parts[parts.length - 1].trim();
        if (!q.endsWith("?")) {
            return null;
        }
        Known k = new Known();
        if (before != null) {
            k.addAll(before);
        }
        Known here = read(text);
        k.addAll(here);
        if (here.isEmpty() && (before == null || before.isEmpty())) {
            return null; // nothing stated to reason from: a question of fact, for the rest of Ibiko
        }
        Matcher m;
        if ((m = ASK_IS.matcher(q)).matches()) {
            return isA(k, who(m.group(1)), kind(m.group(2)), m.group(1).trim(), m.group(2).trim());
        }
        if ((m = ASK_HAS.matcher(q)).matches()) {
            return hasIt(k, who(m.group(1)), kind(m.group(2)), m.group(1).trim(), m.group(2).trim());
        }
        if ((m = ASK_DO.matcher(q)).matches() && !k.does.isEmpty()) {
            String done = doesIt(k, who(m.group(1)), m.group(2).toLowerCase(Locale.ROOT), m.group(3).toLowerCase(Locale.ROOT), m.group(1).trim());
            if (done != null) {
                return done;
            }
        }
        if ((m = ASK_ANY.matcher(q)).matches() && !k.rules.isEmpty()) {
            return ifThen(k, m.group(1));
        }
        return null;
    }

    /** "Does Tunde pay a stall fee?": yes when he is one of a kind that all do it, with how that follows. */
    private static String doesIt(Known k, String x, String verb, String rest, String xSaid) {
        for (String[] kd : kinds(k, x)) {
            for (String[] d : k.does) {
                if (d[0].equals(kd[0]) && sameVerb(d[1], verb) && overlap(d[2], rest)) {
                    return "Yes: " + kd[1] + ", and " + d[3].replaceAll("[.!]+$", "").replaceFirst("^(\\p{Lu})", "$1").toLowerCase(Locale.ROOT)
                            .replaceFirst("^", "") + ".";
                }
            }
        }
        return null;
    }

    private static boolean sameVerb(String a, String b) {
        String x = a.replaceAll("(?:es|s)$", ""), y = b.replaceAll("(?:es|s)$", "");
        return x.equals(y);
    }

    /** Whether two phrases say the same thing: most of the shorter one's words in the other ("a stall fee", "stall fees"). */
    private static boolean overlap(String a, String b) {
        java.util.Set<String> wa = new java.util.HashSet<String>(), wb = new java.util.HashSet<String>();
        for (String w : a.replaceAll("[^a-z ]", " ").split("\\s+")) {
            if (w.length() > 2 && !w.matches("the|and|for|any|all")) {
                wa.add(w.replaceAll("s$", ""));
            }
        }
        for (String w : b.replaceAll("[^a-z ]", " ").split("\\s+")) {
            if (w.length() > 2 && !w.matches("the|and|for|any|all")) {
                wb.add(w.replaceAll("s$", ""));
            }
        }
        if (wa.isEmpty() || wb.isEmpty()) {
            return false;
        }
        java.util.Set<String> both = new java.util.HashSet<String>(wa);
        both.retainAll(wb);
        return both.size() * 2 >= Math.min(wa.size(), wb.size()) + (Math.min(wa.size(), wb.size()) > 1 ? 1 : 0);
    }

    /** The kinds X belongs to, through "all A are B", with how each was reached. */
    private static List<String[]> kinds(Known k, String x) {
        List<String[]> out = new ArrayList<String[]>(); // {kind, how}
        for (String[] mm : k.member) {
            if (mm[0].equals(x)) {
                out.add(new String[]{mm[1], cap(x) + " is " + article(mm[1])});
            }
        }
        for (int i = 0; i < out.size() && i < 12; i++) {
            for (String[] r : k.isa) {
                if (r[0].equals(out.get(i)[0])) {
                    boolean seen = false;
                    for (String[] o : out) {
                        seen |= o[0].equals(r[1]);
                    }
                    if (!seen) {
                        out.add(new String[]{r[1], out.get(i)[1] + ", and all " + plural(r[0]) + " are " + plural(r[1])});
                    }
                }
            }
        }
        return out;
    }

    private static String isA(Known k, String x, String b, String xSaid, String bSaid) {
        for (String[] c : kinds(k, x)) {
            if (c[0].equals(b)) {
                return "Yes: " + c[1] + ", so " + xSaid.replaceFirst("(?i)^(a|an|the) ", "the ") + " is " + article(b) + ".";
            }
            for (String[] a : k.apart) {
                if (a[0].equals(c[0]) && a[1].equals(b) || a[1].equals(c[0]) && a[0].equals(b)) {
                    return "No: " + c[1] + ", and no " + plural(a[0]) + " are " + plural(a[1]) + ".";
                }
            }
        }
        // what every B has, and X lacks: X isn't a B
        for (String[] h : k.has) {
            if (!h[0].equals(b)) {
                continue;
            }
            for (String[] l : k.lacks) {
                if (l[0].equals(x) && same(l[1], h[1])) {
                    return "No: all " + plural(b) + " have " + h[1] + ", and " + xSaid.replaceFirst("(?i)^(a|an|the) ", "the ") + " has none, so it can't be "
                            + article(b) + ".";
                }
            }
        }
        return "I can't tell from what you said: nothing you told me makes " + xSaid + " " + article(b) + ", or rules it out.";
    }

    private static String hasIt(Known k, String x, String b, String xSaid, String bSaid) {
        for (String[] l : k.lacks) {
            if (l[0].equals(x) && same(l[1], b)) {
                return "No: you said " + xSaid + " has no " + bSaid + ".";
            }
        }
        for (String[] c : kinds(k, x)) {
            for (String[] h : k.has) {
                if (h[0].equals(c[0]) && same(h[1], b)) {
                    return "Yes: " + c[1] + ", and all " + plural(h[0]) + " have " + h[1] + ".";
                }
            }
        }
        return "I can't tell from what you said.";
    }

    private static final Set<String> SMALL = new HashSet<String>(Arrays.asList("the", "a", "an", "it", "is", "are", "was", "were", "be", "been",
            "get", "gets", "got", "will", "does", "do", "did", "has", "have", "had", "then", "there", "this", "that", "of", "to", "in"));

    /** The content words of a clause, with their endings off ("rained", "rains", "raining": rain). */
    static Set<String> gist(String clause) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : clause.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")) {
            if (w.length() > 1 && !SMALL.contains(w)) {
                out.add(w.replaceFirst("(?:ing|ed|es|s)$", "").replaceFirst("(.)\\1$", "$1"));
            }
        }
        return out;
    }

    private static boolean covers(Set<String> claim, Set<String> asked) {
        return !claim.isEmpty() && asked.containsAll(claim);
    }

    /** L4: "Is the ground wet?" from "If it rains, the ground gets wet" and "It rained"; and back the other way, not. */
    private static String ifThen(Known k, String asked) {
        Set<String> q = gist(asked);
        for (String[] r : k.rules) {
            Set<String> p = gist(r[0]), c = gist(r[1]);
            if (covers(c, q) || covers(q, c)) {
                for (String h : k.happened) {
                    if (covers(p, gist(h)) || covers(gist(h), p)) {
                        return "Yes: " + lower(h) + ", and " + lower(r[2]) + ".";
                    }
                }
                return "Only if " + r[0].trim() + ": you haven't said that it did.";
            }
            if (covers(p, q) || covers(q, p)) {
                for (String h : k.happened) {
                    if (covers(c, gist(h)) || covers(gist(h), c)) {
                        return "Not necessarily: " + lower(r[2]) + ", but it doesn't work the other way round. “" + h.trim().replaceAll("[.!]+$", "")
                                + "” could have another cause, so it doesn't prove that " + r[0].trim() + ".";
                    }
                }
            }
        }
        return null;
    }

    private static String lower(String s) {
        String t = s.trim().replaceAll("[.!]+$", "");
        return t.length() > 1 && Character.isUpperCase(t.charAt(0)) && !Character.isUpperCase(t.charAt(1)) && !t.matches("^I\\b.*")
                ? Character.toLowerCase(t.charAt(0)) + t.substring(1) : t;
    }

    private static String article(String kind) {
        return ("aeiou".indexOf(kind.charAt(0)) >= 0 ? "an " : "a ") + kind;
    }

    private static String plural(String kind) {
        return Contrast.plural(kind);
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
