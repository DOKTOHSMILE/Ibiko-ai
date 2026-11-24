package ai.ibiko.ibiko1.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.words.Morph;

/**
 * Grammar intelligence, the form of a sentence: how it is built (simple, compound, complex, compound-complex), what it
 * does (declares, asks, orders, exclaims), its tense and aspect (the twelve: simple, continuous, perfect and perfect
 * continuous, each in the present, past and future), its voice, whether it is negated, which conditional it is, and
 * whether it reports speech directly or indirectly. Read from the words, not memorised: the same word is a verb in one
 * sentence and not in the next. Pure Java.
 *
 * <pre>
 * CLAUSES      a clause has its own verb. Split at ";", a coordinator (and, but, or, so, yet, nor) followed by a new
 *              subject, a subordinator (because, although, if, when, while, since, unless…), "that" after a saying or
 *              thinking verb, and who/which/whom/whose after a noun. A part with no verb of its own joins the one
 *              before it ("bread and butter"; "because of the rain"); "and" without a new subject joins two
 *              predicates of one clause ("I came and saw").
 * STRUCTURE    independent = opening or coordinated clauses · dependent = subordinate, relative and that-clauses
 *              1 + 0 simple · 2+ + 0 compound · 1 + 1+ complex · 2+ + 1+ compound-complex
 * FUNCTION     "?" or an auxiliary before its subject: interrogative · "What a…", "How …!": exclamatory ·
 *              a bare verb first (after please, kindly, just; or don't, let's): imperative · else declarative
 * TENSE        the verb chain of the main clause, auxiliaries then the main verb:
 *              will/shall (or be going to) → future · first word past (was, had, did, -ed) → past · else present
 *              have + participle → perfect · be + -ing → continuous · both → perfect continuous
 *              be + participle → passive voice · another modal (can, could, should…) → modal
 * CONDITIONAL  if/unless clause and main clause: present + present zero · present + will/imperative first ·
 *              past + would second · past perfect + would have third · otherwise mixed
 * SPEECH       quotation marks with a saying verb: direct · a saying verb + that/if/whether/wh- or "told X to": indirect
 * </pre>
 */
public final class SentenceForm {
    private SentenceForm() {
    }

    public enum Structure { SIMPLE, COMPOUND, COMPLEX, COMPOUND_COMPLEX, FRAGMENT }

    public enum Function { DECLARATIVE, INTERROGATIVE, IMPERATIVE, EXCLAMATORY }

    /** One clause: its words, how it was joined on, and its verb chain read. */
    public static final class Clause {
        public final String text, joinedBy, kind;
        public final List<String> chain;
        public final Tense tense;

        Clause(String text, String joinedBy, String kind, List<String> chain, Tense tense) {
            this.text = text;
            this.joinedBy = joinedBy;
            this.kind = kind;
            this.chain = chain;
            this.tense = tense;
        }

        public boolean independent() {
            return kind.equals("main") || kind.equals("coordinate");
        }

        @Override
        public String toString() {
            return kind + (joinedBy.isEmpty() ? "" : " (" + joinedBy + ")") + ": " + text + (tense == null ? "" : " · " + tense.name);
        }
    }

    /** A verb chain's tense, aspect and voice. */
    public static final class Tense {
        /** "present", "past", "future" or "modal". */
        public final String time;
        /** "simple", "continuous", "perfect" or "perfect continuous". */
        public final String aspect;
        public final boolean passive;
        /** The modal when there is one ("could"), else "". */
        public final String modal;
        /** The main verb as written ("written"), and its base when known ("write"). */
        public final String verb;
        /** Its usual name: "present perfect continuous", "simple past", "modal could (perfect)". */
        public final String name;

        Tense(String time, String aspect, boolean passive, String modal, String verb) {
            this.time = time;
            this.aspect = aspect;
            this.passive = passive;
            this.modal = modal;
            this.verb = verb;
            String n;
            if (time.equals("modal")) {
                n = "modal “" + modal + "”" + (aspect.equals("simple") ? "" : " (" + aspect + ")");
            } else if (aspect.equals("simple")) {
                n = "simple " + time;
            } else {
                n = time + " " + aspect;
            }
            this.name = n + (passive ? ", passive" : "");
        }
    }

    /** Everything read from a sentence's form. */
    public static final class Form {
        public final String sentence;
        public final Structure structure;
        public final Function function;
        public final List<Clause> clauses;
        /** The main clause's tense; null when it has no verb. */
        public final Tense tense;
        public final boolean negated;
        /** "zero", "first", "second", "third", "mixed", or null when it isn't a conditional. */
        public final String conditional;
        /** "direct", "indirect", or null. */
        public final String speech;
        /** A question's kind: "yes/no", "wh-", "tag", "choice"; null when it isn't one. */
        public final String question;

        Form(String sentence, Structure structure, Function function, List<Clause> clauses, Tense tense, boolean negated,
                String conditional, String speech, String question) {
            this.sentence = sentence;
            this.structure = structure;
            this.function = function;
            this.clauses = clauses;
            this.tense = tense;
            this.negated = negated;
            this.conditional = conditional;
            this.speech = speech;
            this.question = question;
        }

        public int independent() {
            int n = 0;
            for (Clause c : clauses) {
                n += c.independent() ? 1 : 0;
            }
            return n;
        }

        public int dependent() {
            return clauses.size() - independent();
        }

        /** In plain words, as a step or an answer. */
        public String said() {
            StringBuilder b = new StringBuilder();
            b.append(cap(name(structure))).append(" sentence (").append(independent()).append(" independent clause")
                    .append(independent() == 1 ? "" : "s");
            if (dependent() > 0) {
                b.append(", ").append(dependent()).append(" dependent");
            }
            b.append("), ").append(name(function));
            if (question != null) {
                b.append(" (").append(question).append(" question)");
            }
            if (tense != null) {
                b.append("; ").append(tense.name).append(tense.passive ? "" : ", active").append(" voice");
            }
            if (negated) {
                b.append("; negative");
            }
            if (conditional != null) {
                b.append("; ").append(conditional).append(" conditional");
            }
            if (speech != null) {
                b.append("; ").append(speech).append(" speech");
            }
            return b.append('.').toString();
        }
    }

    static String name(Enum<?> e) {
        return e.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static final Set<String> BE = set("be", "am", "is", "are", "was", "were", "been", "being");
    static final Set<String> HAVE = set("have", "has", "had", "having");
    static final Set<String> DO = set("do", "does", "did");
    static final Set<String> MODALS = set("will", "would", "shall", "should", "can", "could", "may", "might", "must", "ought");
    static final Set<String> PAST_AUX = set("was", "were", "had", "did");
    static final Set<String> COORDINATORS = set("and", "but", "or", "so", "yet", "nor");
    static final Set<String> SUBORDINATORS = set("because", "although", "though", "if", "unless", "when", "whenever", "while",
            "whereas", "since", "after", "before", "until", "till", "once", "whether", "wherever", "lest");
    static final Set<String> RELATIVES = set("who", "whom", "whose", "which");
    static final Set<String> SAYING = set("said", "say", "says", "told", "tell", "tells", "asked", "ask", "asks", "replied",
            "answered", "explained", "shouted", "whispered", "claimed", "announced", "added", "insisted", "promised",
            "warned", "admitted", "denied", "think", "thinks", "thought", "believe", "believes", "believed", "know", "knows",
            "knew", "hope", "hoped", "feel", "felt", "suggested", "reported", "wondered", "wonder", "realised", "realized");
    static final Set<String> SPEECH_VERBS = set("said", "says", "say", "told", "tells", "asked", "asks", "replied", "answered",
            "explained", "shouted", "whispered", "claimed", "announced", "added", "insisted", "promised", "warned",
            "admitted", "denied", "suggested", "reported", "wondered", "cried", "yelled", "exclaimed", "remarked");
    static final Set<String> SUBJECTS = set("i", "you", "he", "she", "it", "we", "they", "there", "this", "that", "these",
            "those", "someone", "somebody", "everyone", "everybody", "nobody", "no", "one", "people", "who", "what");
    static final Set<String> DETERMINERS = set("a", "an", "the", "my", "your", "his", "her", "its", "our", "their", "this",
            "that", "these", "those", "some", "any", "every", "each", "no", "all", "both", "many", "much", "few", "several");
    static final Set<String> PREPOSITIONS = set("in", "on", "at", "by", "for", "with", "about", "of", "to", "from", "into",
            "onto", "over", "under", "through", "during", "between", "among", "against", "without", "within", "near");
    static final Set<String> NEGATIONS = set("not", "never", "no", "nobody", "nothing", "none", "neither", "nowhere", "nor", "cannot");
    static final Set<String> SKIPPED = set("not", "never", "always", "just", "already", "still", "also", "really", "often",
            "sometimes", "usually", "probably", "certainly", "definitely", "ever", "even", "only", "then", "now", "soon",
            "recently", "finally", "almost", "nearly", "hardly", "rarely", "seldom", "surely", "actually", "quickly", "slowly");
    /** Verbs common enough to know without a dictionary: what a bare first word must be to be an order. */
    static final Set<String> COMMON_VERBS = set("open", "close", "shut", "come", "go", "sit", "stand", "look", "listen", "tell",
            "give", "take", "bring", "put", "get", "make", "let", "help", "stop", "start", "wait", "try", "call", "send", "show",
            "write", "read", "find", "keep", "turn", "move", "leave", "pass", "pick", "pay", "buy", "eat", "drink", "run",
            "walk", "explain", "describe", "list", "check", "remember", "forget", "think", "imagine", "consider", "use", "add",
            "remove", "fill", "clean", "wash", "cook", "play", "sing", "speak", "say", "ask", "answer", "follow", "watch",
            "hold", "carry", "throw", "catch", "draw", "save", "delete", "create", "build", "fix", "set", "be", "do", "have",
            "see", "hear", "feel", "know", "want", "need", "like", "love", "hate", "work", "live", "rain", "snow", "cry",
            "laugh", "smile", "sleep", "arrive", "die", "fall", "rise", "win", "lose", "learn", "teach", "study", "change",
            "meet", "join", "jump", "kill", "visit", "heat", "melt", "boil", "freeze", "mix", "pour", "cool", "burn", "cut", "dance", "drive", "fly", "swim", "climb", "lie", "spend", "sell", "lend",
            "borrow", "believe", "hope", "wish", "seem", "become", "appear", "happen", "grow", "stay", "finish", "begin");

    private static Set<String> set(String... w) {
        return new HashSet<String>(Arrays.asList(w));
    }

    /** The default test of whether a word can be a verb's plain form, without a dictionary. */
    public static final Predicate<String> COMMON = w -> COMMON_VERBS.contains(w) || Morph.irregularBase(w);

    public static Form read(String sentence) {
        return read(sentence, COMMON);
    }

    /**
     * The form of a sentence.
     *
     * @param verbBase whether a word can be a verb in its plain form ("open", "eat"): the dictionary on the phone,
     *                 {@link #COMMON} in tests
     */
    public static Form read(String sentence, Predicate<String> verbBase) {
        String raw = sentence == null ? "" : sentence.trim();
        List<String> t = tokens(raw);
        Predicate<String> base = verbBase == null ? COMMON : verbBase;
        List<Clause> clauses = clauses(t, base);
        Clause main = null;
        for (Clause c : clauses) {
            if (c.independent() && c.tense != null) {
                main = c;
                break;
            }
        }
        if (main == null && !clauses.isEmpty()) {
            main = clauses.get(0);
        }
        int independent = 0, dependent = 0;
        for (Clause c : clauses) {
            if (c.independent()) {
                independent++;
            } else {
                dependent++;
            }
        }
        Structure s = clauses.isEmpty() || independent == 0 && dependent == 0 ? Structure.FRAGMENT
                : independent <= 1 && dependent == 0 ? Structure.SIMPLE
                : dependent == 0 ? Structure.COMPOUND
                : independent <= 1 ? Structure.COMPLEX : Structure.COMPOUND_COMPLEX;
        if (independent == 0 && dependent > 0) {
            s = Structure.FRAGMENT; // "Because it rained."
        }
        String question = question(raw, t);
        Function f = function(raw, t, base, question);
        boolean negated = false;
        for (String w : t) {
            negated |= NEGATIONS.contains(w);
        }
        return new Form(raw, s, f, clauses, main == null ? null : main.tense, negated, conditional(clauses), speech(raw, t),
                f == Function.INTERROGATIVE ? question : null);
    }

    // ------------------------------------------------------------------------------------------------------------ words

    private static final Pattern WORD = Pattern.compile("[\\p{L}][\\p{L}'’-]*|\\d[\\d.,]*|[,;:?!.\"“”]");

    /** Lower-case words with contractions opened ("won't" → will not; "she's gone" → she has gone) and the marks kept. */
    static List<String> tokens(String s) {
        List<String> raw = new ArrayList<String>();
        Matcher m = WORD.matcher(s.replace('’', '\''));
        while (m.find()) {
            raw.add(m.group().toLowerCase(Locale.ROOT));
        }
        List<String> out = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            String w = raw.get(i);
            if (w.equals("won't")) {
                out.add("will");
                out.add("not");
            } else if (w.equals("can't") || w.equals("cannot")) {
                out.add("can");
                out.add("not");
            } else if (w.equals("shan't")) {
                out.add("shall");
                out.add("not");
            } else if (w.equals("let's")) {
                out.add("let");
                out.add("us");
            } else if (w.endsWith("n't")) {
                out.add(w.substring(0, w.length() - 3).replaceAll("^ai$", "is"));
                out.add("not");
            } else if (w.matches("\\p{L}+'(m|re|ve|ll|d|s)")) {
                int q = w.indexOf('\'');
                String head = w.substring(0, q), tail = w.substring(q + 1);
                if (tail.equals("s") && !SUBJECTS.contains(head) && !head.matches("here|where|how|what|who|when|why|that")) {
                    out.add(w); // a possessive ("John's")
                    continue;
                }
                out.add(head);
                switch (tail) {
                    case "m": out.add("am"); break;
                    case "re": out.add("are"); break;
                    case "ve": out.add("have"); break;
                    case "ll": out.add("will"); break;
                    case "d": out.add(participle(after(raw, i)) ? "had" : "would"); break;
                    default: out.add(after(raw, i).equals("been") || after(raw, i).equals("got")
                            || Morph.irregularParticiple(after(raw, i)) != null && Morph.irregularPast(after(raw, i)) == null ? "has" : "is");
                }
            } else {
                out.add(w);
            }
        }
        return out;
    }

    /** The word after i, past "not", "never", "just" and "already". */
    private static String after(List<String> raw, int i) {
        for (int j = i + 1; j < raw.size(); j++) {
            if (!SKIPPED.contains(raw.get(j))) {
                return raw.get(j);
            }
        }
        return "";
    }

    static boolean participle(String w) {
        return w.length() > 3 && w.endsWith("ed") || Morph.irregularParticiple(w) != null;
    }

    static boolean past(String w) {
        return w.length() > 3 && w.endsWith("ed") || Morph.irregularPast(w) != null || w.equals("was") || w.equals("were");
    }

    static boolean aux(String w) {
        return BE.contains(w) || HAVE.contains(w) || DO.contains(w) || MODALS.contains(w);
    }

    private static boolean mark(String w) {
        return w.matches("[,;:?!.\"“”]");
    }

    /** Whether it can be a finite verb here: its plain form, its -s form, its past. */
    static boolean finite(String w, String before, Predicate<String> base) {
        if (DETERMINERS.contains(before) && !before.equals("that") || PREPOSITIONS.contains(before) || mark(w)) {
            return false;
        }
        if (aux(w) || past(w) && !w.endsWith("ing")) {
            return !DETERMINERS.contains(before) || before.equals("that");
        }
        if (base.test(w)) {
            return true;
        }
        if (w.endsWith("ies") && base.test(w.substring(0, w.length() - 3) + "y")) {
            return true;
        }
        if (w.endsWith("es") && base.test(w.substring(0, w.length() - 2))) {
            return true;
        }
        return w.endsWith("s") && w.length() > 3 && base.test(w.substring(0, w.length() - 1));
    }

    // ------------------------------------------------------------------------------------------------------- clauses

    private static List<Clause> clauses(List<String> t, Predicate<String> base) {
        List<int[]> spans = new ArrayList<int[]>();
        List<String> joins = new ArrayList<String>(), kinds = new ArrayList<String>();
        int start = 0;
        String join = "", kind = "main";
        boolean quoted = false;
        for (int i = 0; i < t.size(); i++) {
            String w = t.get(i), prev = i > 0 ? t.get(i - 1) : "", next = i + 1 < t.size() ? t.get(i + 1) : "";
            if (w.matches("[\"“”]")) {
                quoted = !quoted;
            }
            String newKind = null;
            if (w.equals(";")) {
                newKind = "coordinate";
            } else if (i > 0 && COORDINATORS.contains(w) && !prev.equals("either") && subjectAhead(t, i + 1, base)) {
                newKind = "coordinate";
            } else if (SUBORDINATORS.contains(w) && !next.equals("of") && !(w.equals("since") || w.equals("after")
                    || w.equals("before") || w.equals("until")) || (w.equals("since") || w.equals("after") || w.equals("before")
                    || w.equals("until")) && subjectAhead(t, i + 1, base)) {
                newKind = "subordinate";
            } else if (w.equals("that") && SAYING.contains(prev)) {
                newKind = "that-clause";
            } else if (RELATIVES.contains(w) && i > 0 && !mark(prev) && !aux(prev) && !SAYING.contains(prev) && !COORDINATORS.contains(prev)) {
                newKind = "relative";
            } else if (w.equals(",") && i > 0 && kind.equals("subordinate") && start == 0 && subjectAhead(t, i + 1, base)) {
                newKind = "main"; // "If it rains, we stay in"
            }
            if (newKind != null && i > start) {
                spans.add(new int[]{start, i});
                joins.add(join);
                kinds.add(kind);
                start = w.matches("[;,]") ? i + 1 : i;
                join = w.matches("[;,]") ? (w.equals(";") ? ";" : "") : w;
                kind = newKind;
            } else if (newKind != null && i == start) {
                join = w.matches("[;,]") ? "" : w;
                kind = newKind;
                start = w.matches("[;,]") ? i + 1 : i;
            }
        }
        if (start < t.size()) {
            spans.add(new int[]{start, t.size()});
            joins.add(join);
            kinds.add(kind);
        }
        // a part with no verb of its own joins the one before it
        List<Clause> out = new ArrayList<Clause>();
        for (int k = 0; k < spans.size(); k++) {
            List<String> words = t.subList(spans.get(k)[0], spans.get(k)[1]);
            List<String> chain = chain(words, base);
            String text = String.join(" ", words).replaceAll(" ([,;:?!.])", "$1").trim();
            if (chain.isEmpty() && !out.isEmpty()) {
                Clause last = out.remove(out.size() - 1);
                out.add(new Clause((last.text + " " + text).trim(), last.joinedBy, last.kind, last.chain, last.tense));
                continue;
            }
            if (chain.isEmpty() && text.replaceAll("[,;:?!.\"“” ]", "").isEmpty()) {
                continue;
            }
            out.add(new Clause(text, joins.get(k), kinds.get(k), chain, chain.isEmpty() ? null : tense(chain)));
        }
        return out;
    }

    /** Whether a new subject and its verb start here ("and she left"), not a second noun or predicate ("and butter", "and saw"). */
    private static boolean subjectAhead(List<String> t, int i, Predicate<String> base) {
        if (i >= t.size()) {
            return false;
        }
        String w = t.get(i);
        if (aux(w) || mark(w)) {
            return false;
        }
        if (!SUBJECTS.contains(w) && !DETERMINERS.contains(w) && finite(w, "", base) && !w.endsWith("s")) {
            return false; // a verb straight after: the same subject
        }
        for (int j = i + 1; j < Math.min(t.size(), i + 6); j++) {
            String x = t.get(j);
            if (mark(x) || COORDINATORS.contains(x)) {
                return false;
            }
            if (aux(x) || finite(x, t.get(j - 1), base) && !DETERMINERS.contains(t.get(j - 1))) {
                return true;
            }
        }
        return false;
    }

    /** The verb chain of a clause: its auxiliaries, then the main verb ("has been written"). Empty when it has none. */
    static List<String> chain(List<String> words, Predicate<String> base) {
        List<String> chain = new ArrayList<String>();
        int gap = 0;
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i), prev = i > 0 ? words.get(i - 1) : "";
            if (mark(w)) {
                if (!chain.isEmpty()) {
                    break;
                }
                continue;
            }
            if (SKIPPED.contains(w) || w.equals("not")) {
                continue;
            }
            if (aux(w) && !(DETERMINERS.contains(prev) && !prev.equals("that")) && (chain.isEmpty() || aux(chain.get(chain.size() - 1)))) {
                if (w.equals("going") || chain.size() > 4) {
                    break;
                }
                chain.add(w);
                gap = 0;
                continue;
            }
            if (!chain.isEmpty()) {
                String last = chain.get(chain.size() - 1);
                boolean verb = w.endsWith("ing") || participle(w) || base.test(w) || finite(w, prev, base);
                if (verb && (DO.contains(last) || MODALS.contains(last) || BE.contains(last) || HAVE.contains(last))) {
                    if (w.equals("going") && i + 2 < words.size() && words.get(i + 1).equals("to")) {
                        chain.add("going to");
                        chain.add(words.get(i + 2));
                        break;
                    }
                    if (w.equals("to")) {
                        break;
                    }
                    if (!(BE.contains(last) && !w.endsWith("ing") && !participle(w)) || gap > 0 && base.test(w)) {
                        chain.add(w);
                        break;
                    }
                    if (BE.contains(last) && (w.endsWith("ing") || participle(w))) {
                        chain.add(w);
                        break;
                    }
                }
                if (++gap > 3) {
                    break; // a subject between ("did your brother eat"), then no verb
                }
                continue;
            }
            if (!w.endsWith("ing") && finite(w, prev, base) && !SUBJECTS.contains(w) && !RELATIVES.contains(w)
                    && !(prev.isEmpty() && !past(w) && words.size() > 1 && !base.test(w))) {
                chain.add(w);
                break;
            }
        }
        return chain;
    }

    /** The tense, aspect and voice of a verb chain. */
    static Tense tense(List<String> chain) {
        String first = chain.get(0), main = chain.get(chain.size() - 1);
        String modal = "";
        boolean future = false;
        for (String w : chain) {
            if (w.equals("will") || w.equals("shall") || w.equals("going to")) {
                future = true;
            } else if (MODALS.contains(w) && modal.isEmpty()) {
                modal = w;
            }
        }
        boolean perfect = false, progressive = false, passive = false;
        for (int i = 0; i + 1 < chain.size(); i++) {
            String a = chain.get(i), b = chain.get(i + 1);
            if (HAVE.contains(a) && (participle(b) || b.equals("been"))) {
                perfect = true;
            }
            if (BE.contains(a) && b.endsWith("ing") && !b.equals("being")) {
                progressive = true;
            }
            if (a.equals("being") && participle(b) || BE.contains(a) && !a.equals("being") && participle(b) && i + 1 == chain.size() - 1) {
                passive = true;
            }
            if (a.equals("being")) {
                progressive = true;
            }
        }
        String time;
        if (future && modal.isEmpty() || first.equals("will") || first.equals("shall") || chain.contains("going to")) {
            time = "future";
            if (chain.contains("going to") && PAST_AUX.contains(first)) {
                time = "past"; // "was going to"
            }
        } else if (!modal.isEmpty()) {
            time = "modal";
        } else if (PAST_AUX.contains(first) || chain.size() == 1 && past(first) && !first.endsWith("ing")) {
            time = "past";
        } else {
            time = "present";
        }
        String aspect = perfect && progressive ? "perfect continuous" : perfect ? "perfect" : progressive ? "continuous" : "simple";
        return new Tense(time, aspect, passive, modal, main);
    }

    // ---------------------------------------------------------------------------------------------- function, etc.

    private static String question(String raw, List<String> t) {
        if (t.isEmpty()) {
            return null;
        }
        String first = t.get(0);
        if (raw.matches("(?s).*,\\s*(?:\\w+n't|is|are|was|were|do|does|did|will|would|can|could|have|has|had|shall|should)(?:\\s+not)?\\s+\\w+\\s*\\?\\s*$")) {
            return "tag";
        }
        if (first.matches("what|who|whom|whose|which|where|when|why|how")) {
            return "wh-";
        }
        if (raw.matches("(?is).*\\bor\\b[^,?]+\\?\\s*$") && aux(first)) {
            return "choice";
        }
        if (aux(first)) {
            return "yes/no";
        }
        return raw.endsWith("?") ? "yes/no" : null;
    }

    private static Function function(String raw, List<String> t, Predicate<String> base, String question) {
        if (t.isEmpty()) {
            return Function.DECLARATIVE;
        }
        String first = t.get(0), second = t.size() > 1 ? t.get(1) : "";
        if (raw.matches("(?is)^\\s*(?:what an?\\b|how\\s+\\w+(?:\\s+\\w+)?\\s+(?:is|are|was|were|it|this|that|you|he|she|they|we)\\b).*!\\s*$")
                || raw.matches("(?is)^\\s*what an?\\b[^?]*$") && !raw.endsWith("?") && !aux(second)) {
            return Function.EXCLAMATORY;
        }
        if (raw.endsWith("?")) {
            return Function.INTERROGATIVE;
        }
        if (aux(first) && !first.equals("do") && t.size() > 2 && (SUBJECTS.contains(second) || DETERMINERS.contains(second))
                && !raw.endsWith("!") && !raw.endsWith(".")) {
            return Function.INTERROGATIVE;
        }
        if ("what who where when why how which".contains(first) && t.size() > 2 && (aux(second) || first.equals("who"))
                && !raw.endsWith(".") && !raw.endsWith("!") && question != null) {
            return Function.INTERROGATIVE;
        }
        int i = 0;
        while (i < t.size() && t.get(i).matches("please|kindly|just|now|so|ok|okay|,")) {
            i++;
        }
        String v = i < t.size() ? t.get(i) : "", after = i + 1 < t.size() ? t.get(i + 1) : "";
        boolean order = v.equals("let") && after.equals("us")
                || v.equals("do") && after.equals("not")
                || v.equals("never") && base.test(after)
                || base.test(v) && !aux(v) && !SUBJECTS.contains(v) && !v.endsWith("s") && !past(v)
                && !after.matches("is|are|was|were|has|have|had|will|would|can|could|should|must|does|did");
        if (order) {
            return Function.IMPERATIVE;
        }
        if (raw.endsWith("!") && !raw.matches("(?is).*\\b(?:is|are|was|were)\\b.*") ) {
            return Function.EXCLAMATORY;
        }
        if (raw.endsWith("!") && raw.matches("(?is)^\\s*(?:how|what|so|such|wow|oh)\\b.*")) {
            return Function.EXCLAMATORY;
        }
        return Function.DECLARATIVE;
    }

    private static String conditional(List<Clause> clauses) {
        Clause cond = null, main = null;
        for (Clause c : clauses) {
            if (c.kind.equals("subordinate") && (c.joinedBy.equals("if") || c.joinedBy.equals("unless")) && cond == null) {
                cond = c;
            } else if (c.independent() && main == null) {
                main = c;
            }
        }
        if (cond == null || main == null || cond.tense == null || main.tense == null) {
            return null;
        }
        Tense a = cond.tense, b = main.tense;
        boolean wouldHave = b.time.equals("modal") && b.aspect.contains("perfect") && b.modal.matches("would|could|might|should");
        boolean would = b.time.equals("modal") && b.modal.matches("would|could|might") && !b.aspect.contains("perfect");
        if (a.time.equals("past") && a.aspect.contains("perfect")) {
            return wouldHave ? "third" : would ? "mixed" : "mixed";
        }
        if (a.time.equals("past") || cond.chain.contains("were")) {
            return would ? "second" : wouldHave ? "mixed" : "mixed";
        }
        if (a.time.equals("present")) {
            if (b.time.equals("future") || b.time.equals("modal") && b.modal.matches("can|may|might|must|should")) {
                return "first";
            }
            if (b.time.equals("present")) {
                return "zero";
            }
        }
        return "mixed";
    }

    private static String speech(String raw, List<String> t) {
        boolean quotes = raw.matches("(?s).*[\"“”].+[\"“”].*");
        boolean saying = false;
        for (String w : t) {
            saying |= SPEECH_VERBS.contains(w);
        }
        if (quotes && saying) {
            return "direct";
        }
        if (!quotes && saying && raw.matches("(?is).*\\b(?:said|says|told|tells|asked|asks|replied|answered|explained|claimed|announced|"
                + "insisted|promised|warned|admitted|denied|suggested|reported|wondered)\\b(?:\\s+\\w+)?\\s+(?:that|if|whether|what|why|"
                + "where|when|how|who|to)\\b.*")) {
            return "indirect";
        }
        return null;
    }
}
