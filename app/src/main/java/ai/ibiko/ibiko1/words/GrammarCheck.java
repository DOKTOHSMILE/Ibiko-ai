package ai.ibiko.ibiko1.words;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Syntax;

/**
 * Grammar as rules a speaker knows without thinking: a sentence is grammatical when its words can be built into a
 * sentence by the phrase structure rules ({@link Syntax}) and its parts agree (a subject and its verb in number and
 * person, "a" or "an" by the sound that follows). Ungrammatical strings ("*Deployed the engineer node the") are marked
 * and the rule they break named; what can be put right is put right. Descriptive and prescriptive rules are kept
 * apart: a double negative or a split infinitive is rule-governed speech in many varieties of English, so it is only
 * noted as informal, never called an error. Pure Java.
 */
public final class GrammarCheck {
    private GrammarCheck() {
    }

    /** One thing found: what rule it is about, what was written, what it should be (null when it can't be said). */
    public static final class Issue {
        public final String rule, found, fix;
        /** True for a style note (prescriptive): the sentence is still grammatical. */
        public final boolean style;

        Issue(String rule, String found, String fix, boolean style) {
            this.rule = rule;
            this.found = found;
            this.fix = fix;
            this.style = style;
        }

        @Override
        public String toString() {
            return rule + ": “" + found + "”" + (fix == null ? "" : " → “" + fix + "”");
        }
    }

    /** A sentence judged: whether it is grammatical, what was found, its corrected form, and its structure. */
    public static final class Judgement {
        public final String sentence, corrected;
        public final List<Issue> issues;
        public final boolean parses;
        public final Syntax.Tree tree;

        Judgement(String sentence, String corrected, List<Issue> issues, boolean parses, Syntax.Tree tree) {
            this.sentence = sentence;
            this.corrected = corrected;
            this.issues = issues;
            this.parses = parses;
            this.tree = tree;
        }

        /** Grammatical: it parses and nothing but style notes was found. */
        public boolean grammatical() {
            if (!parses) {
                return false;
            }
            for (Issue i : issues) {
                if (!i.style) {
                    return false;
                }
            }
            return true;
        }

        /** How many real errors (not style notes). */
        public int errors() {
            int n = parses ? 0 : 1;
            for (Issue i : issues) {
                n += i.style ? 0 : 1;
            }
            return n;
        }
    }

    // ------------------------------------------------------------------ a and an

    /** Words spelled with a vowel that start with a consonant sound ("a university", "a one-off", "a European"). */
    private static final Pattern YOU_SOUND = Pattern.compile("(?i)^(uni|use|usu|uti|ure|uro|eu|ewe|one|once|ufo|uk\\b|us\\b|u\\b).*");
    /** Words spelled with h that start with a vowel sound ("an hour", "an honest"). */
    private static final Pattern SILENT_H = Pattern.compile("(?i)^(hour|honest|honour|honor|heir|herb(?!al))\\w*");

    /** Whether "an" goes before a word (by its first sound). */
    public static boolean an(String word) {
        String w = word.replaceAll("^[\"'(“]+", "");
        if (w.isEmpty()) {
            return false;
        }
        if (w.matches("[A-Z]{2,}.*")) {
            return "AEFHILMNORSX".indexOf(w.charAt(0)) >= 0; // initialisms by the letter's name: an FBI agent, a CPU
        }
        if (w.matches("\\d.*")) {
            return w.matches("(8|11|18|80|800)\\b.*|8\\d*");
        }
        if (SILENT_H.matcher(w).matches()) {
            return true;
        }
        if (YOU_SOUND.matcher(w).matches()) {
            return false;
        }
        return "aeiouAEIOU".indexOf(w.charAt(0)) >= 0;
    }

    private static final Pattern ARTICLE = Pattern.compile("\\b(a|an|A|An)\\s+([\\p{L}\\d][\\p{L}\\d'-]*)");

    // ------------------------------------------------------------------ agreement

    /** Present-tense verbs as a third person singular subject takes them, and as the others take them. */
    private static final Map<String, String> TO_THIRD = new HashMap<String, String>(), TO_PLAIN = new HashMap<String, String>();
    private static final Set<String> COMMON_VERBS = new HashSet<String>(Arrays.asList(("go have do say get make know think take see come "
            + "want look use find give tell work call try ask need feel become leave put mean keep let begin seem help talk turn start "
            + "show hear play run move like live believe hold bring happen write sit stand lose pay meet include continue set learn "
            + "change lead understand watch follow stop create speak read spend grow open walk win teach offer remember love consider "
            + "appear buy wait serve die send build stay fall cut reach kill remain suggest raise pass sell require report decide pull "
            + "eat drink sleep cook dance sing swim drive fly cry laugh smile wish hope deploy compile crash fix answer").split(" ")));

    static {
        for (String v : COMMON_VERBS) {
            TO_THIRD.put(v, Morph.third(v));
            TO_PLAIN.put(Morph.third(v), v);
        }
    }

    private static final Pattern THIRD_SUBJECT = Pattern.compile("(?i)(?<![\\w,] that |[\\w,] which |[\\w,] who )\\b(he|she|it|everyone|everybody|"
            + "someone|somebody|nobody|my (?!children|people|men|women|parents|friends)\\w+(?<!s)|the (?!children|people|men|women|police|"
            + "data|sheep|staff|crew|team|way|time|day|one|place)\\w+(?<!s))\\s+(" + String.join("|", COMMON_VERBS) + ")\\b");
    /** Only he, she and it: what Ibiko applies to its own sentences (a noun subject can be plural in ways a rule can't see). */
    private static final Pattern THIRD_PRONOUN = Pattern.compile("(?i)\\b(he|she|it)\\s+(" + String.join("|", COMMON_VERBS) + ")\\b");
    private static final Pattern PLAIN_SUBJECT = Pattern.compile("(?i)\\b(i|you|we|they)\\s+(" + String.join("|", TO_PLAIN.keySet()) + ")\\b");
    private static final Pattern BE = Pattern.compile("(?i)(?<!\\bif |\\bas |\\bwish |\\bthough )(?:\\b(i)\\s+(is|are)\\b|\\b(he|she|it)\\s+(are|am|were)\\b|\\b(we|they|you)\\s+(is|am|was)\\b)");

    // ------------------------------------------------------------------ the rules, run

    /** The sentence judged against every rule. */
    public static Judgement judge(String sentence) {
        return judge(sentence, false);
    }

    /**
     * The sentence judged; {@code careful} keeps to the rules that can't misfire on a well-written sentence (a or an,
     * "I am", "they are", "he goes" with a pronoun, the same word twice): what Ibiko checks its own replies with.
     */
    public static Judgement judge(String sentence, boolean careful) {
        String s = sentence == null ? "" : sentence.trim();
        List<Issue> issues = new ArrayList<Issue>();
        String fixed = s;
        // a / an by sound
        Matcher m = ARTICLE.matcher(fixed);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            String art = m.group(1), next = m.group(2);
            boolean wantAn = an(next);
            boolean isAn = art.equalsIgnoreCase("an");
            String repl = m.group(0);
            if (wantAn != isAn) {
                String right = (wantAn ? "an" : "a");
                if (Character.isUpperCase(art.charAt(0))) {
                    right = Character.toUpperCase(right.charAt(0)) + right.substring(1);
                }
                repl = right + " " + next;
                issues.add(new Issue("a or an goes by the sound that follows", m.group(0), repl, false));
            }
            m.appendReplacement(b, Matcher.quoteReplacement(repl));
        }
        m.appendTail(b);
        fixed = b.toString();
        // be: I am, he is, they are
        m = BE.matcher(fixed);
        b = new StringBuffer();
        while (m.find()) {
            String subj = m.group(1) != null ? m.group(1) : m.group(3) != null ? m.group(3) : m.group(5);
            String verb = m.group(2) != null ? m.group(2) : m.group(4) != null ? m.group(4) : m.group(6);
            String low = subj.toLowerCase(Locale.ROOT);
            boolean past = verb.matches("(?i)was|were");
            String right = low.equals("i") ? (past ? "was" : "am") : low.matches("he|she|it") ? (past ? "was" : "is") : (past ? "were" : "are");
            String repl = subj + " " + right;
            issues.add(new Issue("the verb agrees with its subject", m.group(0), repl, false));
            m.appendReplacement(b, Matcher.quoteReplacement(repl));
        }
        m.appendTail(b);
        fixed = b.toString();
        // he goes, they go
        m = (careful ? THIRD_PRONOUN : THIRD_SUBJECT).matcher(fixed);
        b = new StringBuffer();
        while (m.find()) {
            String subj = m.group(1), verb = m.group(2);
            String before = fixed.substring(0, m.start()).toLowerCase(Locale.ROOT);
            // "can he go", "did she go", "to go", "will it work": the plain form after a helping verb is right
            if (before.matches("(?s).*\\b(can|could|will|would|shall|should|may|might|must|did|does|do|to|let|make|help|why not)\\s*$")
                    || subj.toLowerCase(Locale.ROOT).startsWith("the ") && subj.toLowerCase(Locale.ROOT).matches("the (way|time|day|one|place)")) {
                m.appendReplacement(b, Matcher.quoteReplacement(m.group(0)));
                continue;
            }
            String repl = subj + " " + TO_THIRD.get(verb.toLowerCase(Locale.ROOT));
            issues.add(new Issue("a third person singular subject takes -s", m.group(0), repl, false));
            m.appendReplacement(b, Matcher.quoteReplacement(repl));
        }
        m.appendTail(b);
        fixed = b.toString();
        m = PLAIN_SUBJECT.matcher(fixed);
        b = new StringBuffer();
        while (m.find()) {
            String repl = m.group(1) + " " + TO_PLAIN.get(m.group(2).toLowerCase(Locale.ROOT));
            issues.add(new Issue("only he, she and it take the -s", m.group(0), repl, false));
            m.appendReplacement(b, Matcher.quoteReplacement(repl));
        }
        m.appendTail(b);
        fixed = b.toString();
        // the same word twice ("the the")
        Matcher twice = Pattern.compile("(?i)\\b(\\w+)\\s+\\1\\b").matcher(fixed);
        if (twice.find() && !twice.group(1).matches("(?i)that|had|is|very|so|bye|no|yes|ha|small|sharp|well|go|now|knock|tut|bla|blah|hush|chop|quick|dum|bye|again")) {
            issues.add(new Issue("the same word twice", twice.group(0), twice.group(1), false));
            fixed = fixed.substring(0, twice.start()) + twice.group(1) + fixed.substring(twice.end());
        }
        if (careful) {
            return new Judgement(s, fixed, issues, true, null);
        }
        // capitals and the end of a sentence
        if (!fixed.isEmpty() && Character.isLowerCase(fixed.charAt(0)) && fixed.matches("[a-z].*")) {
            issues.add(new Issue("a sentence starts with a capital", fixed.split("\\s")[0], cap(fixed.split("\\s")[0]), true));
            fixed = cap(fixed);
        }
        if (fixed.matches("(?s).*\\bi\\b.*")) {
            issues.add(new Issue("“I” is always a capital", "i", "I", false));
            fixed = fixed.replaceAll("\\bi\\b", "I");
        }
        if (!fixed.isEmpty() && fixed.matches("(?s).*[\\p{L}\\d)]$") && fixed.split("\\s+").length > 2) {
            boolean question = fixed.matches("(?i)^(who|what|when|where|why|how|which|is|are|was|were|do|does|did|can|could|will|would|should|have|has)\\b.*");
            issues.add(new Issue("a sentence ends with a full stop", "", question ? "?" : ".", true));
            fixed = fixed + (question ? "?" : ".");
        }
        // descriptive vs prescriptive: noted, not wrong
        if (fixed.matches("(?i).*\\b(don't|doesn't|didn't|can't|won't|ain't|never)\\s+(\\w+\\s+)?(no|nothing|nobody|nowhere|none)\\b.*")) {
            issues.add(new Issue("a double negative: grammatical in many varieties of English (it stresses the no), but formal"
                    + " writing uses one negative", "", null, true));
        }
        if (fixed.matches("(?i).*\\bto\\s+\\w+ly\\s+\\w+.*")) {
            issues.add(new Issue("a split infinitive (“to boldly go”): grammatical English; some style guides prefer the adverb"
                    + " outside", "", null, true));
        }
        String parseable = fixed.replaceAll("[\"“”]", "");
        List<Syntax.Tree> trees = Syntax.parse(parseable);
        boolean parses = !trees.isEmpty() || parseable.split("\\s+").length > Syntax.LONGEST;
        return new Judgement(s, fixed, issues, parses, trees.isEmpty() ? null : trees.get(0));
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** The judgement said in words. */
    public static String explain(Judgement j) {
        StringBuilder b = new StringBuilder();
        List<Issue> errors = new ArrayList<Issue>(), notes = new ArrayList<Issue>();
        for (Issue i : j.issues) {
            (i.style ? notes : errors).add(i);
        }
        if (j.grammatical()) {
            b.append("Yes, it's grammatical: its words build into a sentence by the phrase structure rules, and its parts agree.");
        } else if (!errors.isEmpty()) {
            b.append("Not quite. Here's what breaks a rule:");
            for (Issue i : errors) {
                b.append("\n• ").append(cap(i.rule)).append(": ").append(i.toString().substring(i.rule.length() + 2));
            }
            b.append("\n\nCorrected: ").append(j.corrected);
        } else {
            b.append("No: *").append(j.sentence.replaceAll("[.!?]+$", ""))
                    .append(" is ungrammatical. Its words can't be built into a sentence by the phrase structure rules (S → NP VP):"
                            + " the order puts parts of phrases where no rule allows them.");
        }
        for (Issue n : notes) {
            if (n.fix == null) {
                b.append("\n• Note: ").append(n.rule).append('.');
            }
        }
        if (j.tree != null && j.grammatical()) {
            b.append("\n\n```\n").append(j.tree.drawn()).append("\n```");
        }
        return b.toString();
    }
}
