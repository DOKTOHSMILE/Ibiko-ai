package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Grammar, the structure of a sentence: its words grouped into nested constituents (phrases) by phrase structure rules,
 * drawn as a syntax tree. A sentence is not a flat row of words: "The engineer deployed the node on the server" is a
 * noun phrase (the engineer) and a verb phrase (deployed the node on the server), and the verb phrase holds its own
 * noun phrase and prepositional phrase. Every phrase takes its name from its head (a noun heads an NP, a verb a VP, a
 * preposition a PP), and the rules are recursive (an NP holds a PP that holds an NP: "the node on the server in the
 * rack in Lagos"). Pure Java.
 *
 * <pre>
 * S    → NP VP                         a sentence: a subject and a predicate
 * S    → S Conj S                      two joined ("I came and I saw")
 * Q    → Wh Aux NP VP | Aux NP VP      a question ("what did Edison invent", "did Edison invent it")
 * NP   → (Det) (Num) (AdjP) N (PP)     a noun phrase: its noun, what goes before it, what it is "of" or "on"
 * NP   → Pron | Name | NP PP | NP Conj NP
 * VP   → V (NP) (PP) (AdvP) | V AdjP | Aux VP | VP AdvP | Adv VP
 * PP   → P NP                          a prepositional phrase
 * AdjP → (Deg) Adj                     an adjective phrase ("very reliable"; a degree word: very, quite, too)
 * AdvP → (Deg) Adv
 * </pre>
 *
 * It is a chart parser (CYK): every span of words is tried with every rule, bottom up, so every reading the rules allow
 * is found at once. "I saw the user with the telescope" has two: the PP under the VP (the seeing was done with the
 * telescope) or inside the object NP (the user had the telescope). A word's category comes from the parts-of-speech
 * formula, with the other categories a word can also be ("saw" a verb or a noun) tried too.
 */
public final class Syntax {
    private Syntax() {
    }

    /** One node of a syntax tree: a phrase (label and children) or a word (label and the word). */
    public static final class Tree {
        public final String label, word;
        public final List<Tree> kids;
        /** The words it spans: from (inclusive) to (exclusive). */
        public final int from, to;

        Tree(String label, String word, List<Tree> kids, int from, int to) {
            this.label = label;
            this.word = word;
            this.kids = kids;
            this.from = from;
            this.to = to;
        }

        /** A word (a leaf): its category and the word. For trees made outside the parser (a word's morphemes). */
        public static Tree leaf(String label, String word) {
            return new Tree(label, word, new ArrayList<Tree>(), 0, 0);
        }

        /** A phrase (a node) over its children. */
        public static Tree node(String label, List<Tree> kids) {
            return new Tree(label, null, new ArrayList<Tree>(kids), 0, 0);
        }

        /** In brackets, as the Penn Treebank writes it: (S (NP (Det The) (N engineer)) (VP …)). */
        public String bracketed() {
            if (word != null) {
                return "(" + label + " " + word + ")";
            }
            StringBuilder b = new StringBuilder("(").append(label);
            for (Tree k : kids) {
                b.append(' ').append(k.bracketed());
            }
            return b.append(')').toString();
        }

        /** Its words, as written. */
        public String words() {
            if (word != null) {
                return word;
            }
            StringBuilder b = new StringBuilder();
            for (Tree k : kids) {
                b.append(b.length() == 0 ? "" : " ").append(k.words());
            }
            return b.toString();
        }

        /** Every phrase in it, outermost first. */
        public List<Tree> phrases() {
            List<Tree> out = new ArrayList<Tree>();
            if (word == null) {
                out.add(this);
                for (Tree k : kids) {
                    out.addAll(k.phrases());
                }
            }
            return out;
        }

        /** The node directly over a span, or null: what makes a run of words a constituent. */
        public Tree over(int a, int b) {
            if (from == a && to == b) {
                return this;
            }
            for (Tree k : kids) {
                if (k.from <= a && b <= k.to) {
                    return k.over(a, b);
                }
            }
            return null;
        }

        /** The node a node hangs from, or null. */
        public Tree parentOf(Tree child) {
            for (Tree k : kids) {
                if (k == child) {
                    return this;
                }
                Tree p = k.parentOf(child);
                if (p != null) {
                    return p;
                }
            }
            return null;
        }

        int size() {
            int n = 1;
            for (Tree k : kids) {
                n += k.size();
            }
            return n;
        }

        /** Drawn as a tree, top down, the words along the bottom of each branch. */
        public String drawn() {
            return String.join("\n", Drawing.of(this).lines).replaceAll("\\s+\\n", "\n").replaceAll("\\s+$", "");
        }
    }

    // ------------------------------------------------------------------ the rules

    /** A rule: a phrase and what it expands to. Helper phrases (in angle brackets) are folded into their parent. */
    private static final String[][] RULES = {
            {"S", "NP VP"}, {"S", "S Conj S"}, {"S", "Wh Aux NP VP"}, {"S", "Aux NP VP"}, {"S", "Wh VP"},
            {"S", "VP"}, // an order: "deploy the node"
            {"NP", "Det <N'>"}, {"NP", "<N'>"}, {"NP", "Det Num <N'>"}, {"NP", "Num <N'>"}, {"NP", "Pron"}, {"NP", "<Name>"},
            {"NP", "NP PP"}, {"NP", "NP Conj NP"}, {"NP", "Det <Name>"}, {"NP", "Wh"}, {"NP", "Num"},
            {"<N'>", "N"}, {"<N'>", "AdjP <N'>"}, {"<N'>", "N <N'>"}, {"<N'>", "<Name> <N'>"},
            {"<Name>", "Name"}, {"<Name>", "Name <Name>"},
            {"VP", "V"}, {"VP", "V NP"}, {"VP", "V NP PP"}, {"VP", "V PP"}, {"VP", "V NP NP"}, {"VP", "V AdjP"},
            {"VP", "Aux VP"}, {"VP", "Aux NP"}, {"VP", "Aux AdjP"}, {"VP", "Aux PP"}, {"VP", "VP AdvP"}, {"VP", "AdvP VP"},
            {"VP", "VP Conj VP"}, {"VP", "Aux Neg VP"}, {"VP", "Aux Neg NP"}, {"VP", "Aux Neg AdjP"},
            {"PP", "P NP"},
            {"AdjP", "Adj"}, {"AdjP", "Deg Adj"}, {"AdjP", "AdjP Conj AdjP"},
            {"AdvP", "Adv"}, {"AdvP", "Deg Adv"},
    };

    /** The rules as the chart uses them: two on the right (longer ones cut into helper pairs), or one. */
    private static final List<String[]> BINARY = new ArrayList<String[]>();
    private static final Map<String, Set<String>> UNARY = new HashMap<String, Set<String>>();
    /** Helper phrases made when cutting a long rule: folded back in when the tree is drawn. */
    private static final Set<String> HELPERS = new HashSet<String>();

    static {
        int made = 0;
        for (String[] r : RULES) {
            String[] rhs = r[1].split(" ");
            if (rhs.length == 1) {
                UNARY.computeIfAbsent(rhs[0], k -> new LinkedHashSet<String>()).add(r[0]);
                continue;
            }
            String left = r[0];
            for (int i = 0; i < rhs.length - 2; i++) {
                String next = "<" + r[0] + "#" + made++ + ">";
                HELPERS.add(next);
                BINARY.add(new String[]{left, rhs[i], next});
                left = next;
            }
            BINARY.add(new String[]{left, rhs[rhs.length - 2], rhs[rhs.length - 1]});
        }
    }

    // ------------------------------------------------------------------ the words' categories

    private static final Set<String> WH = new HashSet<String>(Arrays.asList("who", "what", "which", "where", "when", "why", "how", "whom"));
    /** Degree words: they modify an adjective or an adverb ("very reliable", "quite quickly"), never stand alone. */
    private static final Set<String> DEGREE = new HashSet<String>(Arrays.asList("very", "quite", "too", "rather", "so", "extremely",
            "really", "pretty", "fairly", "most", "more", "less", "least", "incredibly", "highly"));
    /** Words that are a noun and a verb alike ("saw", "run", "deploys"). */
    private static final Set<String> BOTH = new HashSet<String>(Arrays.asList("saw", "run", "runs", "walk", "walks", "work", "works", "play",
            "plays", "deploy", "deploys", "cook", "cooks", "love", "loves", "need", "needs", "use", "uses", "watch", "watches",
            "book", "books", "fly", "flies", "time", "times", "plan", "plans", "test", "tests", "show", "shows", "build", "builds",
            "code", "codes", "call", "calls", "help", "helps", "start", "starts", "change", "changes", "visit", "visits"));

    /** The categories a word may have in a sentence: what the parts-of-speech formula says, then the others it can be. */
    static List<Set<String>> categories(List<PartsOfSpeech.Word> words) {
        List<Set<String>> out = new ArrayList<Set<String>>();
        for (int i = 0; i < words.size(); i++) {
            PartsOfSpeech.Word w = words.get(i);
            String low = w.text.toLowerCase(Locale.ROOT);
            Set<String> c = new LinkedHashSet<String>();
            if (WH.contains(low)) {
                c.add("Wh");
            }
            if (low.matches("not|n't|never")) {
                c.add("Neg");
                out.add(c);
                continue;
            }
            switch (w.tag) {
                case NOUN: c.add("N"); break;
                case PROPER_NOUN: c.add("Name"); break;
                case PRONOUN: c.add(PartsOfSpeech.DETERMINERS.contains(low) || low.matches("my|your|his|her|its|our|their|this|that|these|those") ? "Det" : "Pron");
                    if (low.matches("this|that|these|those|her")) {
                        c.add("Pron");
                    }
                    break;
                case VERB: c.add("V"); break;
                case AUXILIARY: c.add("Aux"); c.add("V"); break;
                case ADJECTIVE: c.add("Adj"); break;
                case ADVERB: c.add(DEGREE.contains(low) ? "Deg" : "Adv"); break;
                case PREPOSITION: c.add("P"); break;
                case CONJUNCTION: c.add("Conj"); break;
                case DETERMINER: c.add("Det"); break;
                case NUMERAL: c.add("Num"); break;
                default: break;
            }
            if (BOTH.contains(low)) {
                c.add("N");
                c.add("V");
            }
            if (i == 0 && w.tag == PartsOfSpeech.Tag.PROPER_NOUN) {
                c.add("N"); // a sentence's first word is capitalised whatever it is
                c.add("V");
            }
            if (w.tag == PartsOfSpeech.Tag.ADJECTIVE && low.matches(".*(ed|ing)$")) {
                c.add("V");
            }
            boolean open = w.tag == PartsOfSpeech.Tag.NOUN || w.tag == PartsOfSpeech.Tag.ADJECTIVE || w.tag == PartsOfSpeech.Tag.VERB
                    || w.tag == PartsOfSpeech.Tag.PROPER_NOUN;
            if (open && w.text.matches("[\\p{L}'-]+")) {
                // the tagger's first guess leads; a content word may also be the other kinds ("invent" a verb, "company" a
                // noun), tried after it at a cost, so a sentence the tagger half-read still parses
                if (w.text.matches("\\p{Lu}.*")) {
                    c.add("Name");
                }
                if (!low.matches(".*(ly)$")) {
                    c.add("N");
                    c.add("V");
                }
                if (low.matches(".*(ous|ful|ive|able|ible|al|ic|less|ish|y|ed|ing|ent|ant)$")) {
                    c.add("Adj");
                }
            }
            if (c.isEmpty()) {
                c.add("N");
            }
            out.add(c);
        }
        return out;
    }

    // ------------------------------------------------------------------ the chart

    /** How a phrase over a span was made: one child phrase, or two split at a word. */
    private static final class Way {
        final String a, b;
        final int split;

        Way(String a, String b, int split) {
            this.a = a;
            this.b = b;
            this.split = split;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Way && ((Way) o).split == split && ((Way) o).a.equals(a) && java.util.Objects.equals(((Way) o).b, b);
        }

        @Override
        public int hashCode() {
            return a.hashCode() * 31 + (b == null ? 0 : b.hashCode()) + split;
        }
    }

    /** The most readings kept (a long sentence can have hundreds). */
    public static final int MOST = 8;
    /** The longest sentence it parses. */
    public static final int LONGEST = 30;

    /** Every reading of a sentence the rules allow, the simplest first; empty when none. */
    /** Contractions written out so their parts are read: "don't" is "do not", "I'm" is "I am". */
    static String expand(String sentence) {
        return (sentence == null ? "" : sentence).replaceAll("(?i)\\bcan't\\b", "can not").replaceAll("(?i)\\bwon't\\b", "will not")
                .replaceAll("(?i)\\b(\\w+)n['\u2019]t\\b", "$1 not").replaceAll("(?i)\\b(I)['\u2019]m\\b", "$1 am")
                .replaceAll("(?i)\\b(you|we|they)['\u2019]re\\b", "$1 are").replaceAll("(?i)\\b(I|you|we|they)['\u2019]ve\\b", "$1 have")
                .replaceAll("(?i)\\b(I|you|he|she|we|they)['\u2019]ll\\b", "$1 will");
    }

    public static List<Tree> parse(String sentence) {
        sentence = expand(sentence);
        List<PartsOfSpeech.Word> tagged = new ArrayList<PartsOfSpeech.Word>();
        for (PartsOfSpeech.Word w : PartsOfSpeech.tag(sentence, null)) {
            if (w.tag != PartsOfSpeech.Tag.PUNCTUATION && w.tag != PartsOfSpeech.Tag.INTERJECTION) {
                tagged.add(w);
            }
        }
        int n = tagged.size();
        if (n == 0 || n > LONGEST) {
            return new ArrayList<Tree>();
        }
        List<Set<String>> cats = categories(tagged);
        @SuppressWarnings("unchecked")
        Map<String, Set<Way>>[][] chart = new LinkedHashMap[n][n + 1];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j <= n; j++) {
                chart[i][j] = new LinkedHashMap<String, Set<Way>>();
            }
        }
        for (int i = 0; i < n; i++) {
            for (String c : cats.get(i)) {
                chart[i][i + 1].put(c, new LinkedHashSet<Way>());
            }
            unary(chart[i][i + 1]);
        }
        for (int span = 2; span <= n; span++) {
            for (int i = 0; i + span <= n; i++) {
                int j = i + span;
                Map<String, Set<Way>> cell = chart[i][j];
                for (int k = i + 1; k < j; k++) {
                    Map<String, Set<Way>> left = chart[i][k], right = chart[k][j];
                    if (left.isEmpty() || right.isEmpty()) {
                        continue;
                    }
                    for (String[] r : BINARY) {
                        if (left.containsKey(r[1]) && right.containsKey(r[2])) {
                            cell.computeIfAbsent(r[0], x -> new LinkedHashSet<Way>()).add(new Way(r[1], r[2], k));
                        }
                    }
                }
                unary(cell);
            }
        }
        List<Tree> trees = new ArrayList<Tree>();
        if (chart[0][n].containsKey("S")) {
            trees.addAll(build(chart, tagged, cats, 0, n, "S", new HashSet<String>()));
        }
        // folded, the same readings once, the simplest (fewest phrases, the words' first categories) first
        Map<String, Tree> unique = new LinkedHashMap<String, Tree>();
        for (Tree t : trees) {
            Tree f = fold(t);
            unique.putIfAbsent(f.bracketed(), f);
        }
        List<Tree> all = new ArrayList<Tree>(unique.values());
        // only the readings that read the words best (their first categories, or as near as the sentence allows): a
        // word read as something less likely isn't another meaning of the sentence
        int least = Integer.MAX_VALUE;
        for (Tree t : all) {
            least = Math.min(least, misread(t, tagged, cats));
        }
        List<Tree> out = new ArrayList<Tree>();
        for (Tree t : all) {
            if (misread(t, tagged, cats) == least) {
                out.add(t);
            }
        }
        out.sort((x, y) -> Integer.compare(cost(x, tagged, cats), cost(y, tagged, cats)));
        return out.size() > MOST ? new ArrayList<Tree>(out.subList(0, MOST)) : out;
    }

    /** How unlikely a reading is: its phrases, and each word read as other than its first category. */
    private static int cost(Tree t, List<PartsOfSpeech.Word> words, List<Set<String>> cats) {
        return t.size() + 6 * misread(t, words, cats);
    }

    /** How many words a reading takes as other than their first category (a capitalised name read as a name is not). */
    private static int misread(Tree t, List<PartsOfSpeech.Word> words, List<Set<String>> cats) {
        int n = 0;
        for (Tree p : leaves(t)) {
            String first = cats.get(p.from).iterator().next();
            boolean name = p.label.equals("Name") && p.word.matches("\\p{Lu}.*") && (first.equals("N") || first.equals("Name"));
            if (!first.equals(p.label) && !name) {
                n++;
            }
        }
        return n;
    }

    private static List<Tree> leaves(Tree t) {
        List<Tree> out = new ArrayList<Tree>();
        if (t.word != null) {
            out.add(t);
        } else {
            for (Tree k : t.kids) {
                out.addAll(leaves(k));
            }
        }
        return out;
    }

    private static void unary(Map<String, Set<Way>> cell) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String child : new ArrayList<String>(cell.keySet())) {
                for (String parent : UNARY.getOrDefault(child, new HashSet<String>())) {
                    if (cell.computeIfAbsent(parent, x -> new LinkedHashSet<Way>()).add(new Way(child, null, -1))) {
                        changed = true;
                    }
                }
            }
        }
    }

    /** The trees for a phrase over a span, from the ways it was made (at most a few hundred). */
    private static List<Tree> build(Map<String, Set<Way>>[][] chart, List<PartsOfSpeech.Word> words, List<Set<String>> cats,
            int i, int j, String label, Set<String> visiting) {
        List<Tree> out = new ArrayList<Tree>();
        String key = i + ":" + j + ":" + label;
        if (!visiting.add(key)) {
            return out; // a loop of one-child rules
        }
        if (j == i + 1 && cats.get(i).contains(label)) {
            out.add(new Tree(label, words.get(i).text, new ArrayList<Tree>(), i, j));
        }
        for (Way w : chart[i][j].getOrDefault(label, new HashSet<Way>())) {
            if (out.size() > 200) {
                break;
            }
            if (w.b == null) {
                for (Tree k : build(chart, words, cats, i, j, w.a, visiting)) {
                    out.add(new Tree(label, null, Arrays.asList(k), i, j));
                }
            } else {
                List<Tree> ls = build(chart, words, cats, i, w.split, w.a, visiting);
                List<Tree> rs = build(chart, words, cats, w.split, j, w.b, visiting);
                for (Tree l : ls) {
                    for (Tree r : rs) {
                        out.add(new Tree(label, null, Arrays.asList(l, r), i, j));
                    }
                }
            }
        }
        visiting.remove(key);
        return out;
    }

    /** Helper phrases folded into their parents, so the tree is the rules' own: (NP (Det the) (AdjP …) (N node)). */
    static Tree fold(Tree t) {
        if (t.word != null) {
            return t;
        }
        List<Tree> kids = new ArrayList<Tree>();
        for (Tree k : t.kids) {
            Tree f = fold(k);
            if (f.word == null && (HELPERS.contains(f.label) || f.label.startsWith("<"))) {
                kids.addAll(f.kids);
            } else {
                kids.add(f);
            }
        }
        return new Tree(t.label, null, kids, t.from, t.to);
    }

    // ------------------------------------------------------------------ constituents

    /** The words of a sentence (as the parser counts them). */
    public static List<String> words(String sentence) {
        List<String> out = new ArrayList<String>();
        for (PartsOfSpeech.Word w : PartsOfSpeech.tag(expand(sentence), null)) {
            if (w.tag != PartsOfSpeech.Tag.PUNCTUATION && w.tag != PartsOfSpeech.Tag.INTERJECTION) {
                out.add(w.text);
            }
        }
        return out;
    }

    /** Where a phrase stands in a sentence's words: {from, to}, or null. */
    public static int[] span(List<String> words, String phrase) {
        List<String> p = new ArrayList<String>();
        for (String w : phrase.trim().split("\\s+")) {
            if (!w.isEmpty()) {
                p.add(w.replaceAll("[,.!?;:]", "").toLowerCase(Locale.ROOT));
            }
        }
        for (int i = 0; i + p.size() <= words.size(); i++) {
            boolean same = true;
            for (int k = 0; k < p.size() && same; k++) {
                same = words.get(i + k).toLowerCase(Locale.ROOT).equals(p.get(k));
            }
            if (same && !p.isEmpty()) {
                return new int[]{i, i + p.size()};
            }
        }
        return null;
    }

    /** What a phrase is called, in words. */
    public static String name(String label) {
        switch (label) {
            case "S": return "sentence";
            case "NP": return "noun phrase";
            case "VP": return "verb phrase";
            case "PP": return "prepositional phrase";
            case "AdjP": return "adjective phrase";
            case "AdvP": return "adverb phrase";
            default: return label;
        }
    }

    /**
     * Whether a run of words is a constituent of the sentence, with the three tests that show it: substitution (a
     * pronoun or "do so" for it), movement (it moves as one, "It was … that …") and the fragment test (it answers a
     * question on its own).
     */
    public static String constituent(String sentence, String phrase) {
        List<Tree> trees = parse(sentence);
        if (trees.isEmpty()) {
            return null;
        }
        List<String> words = words(sentence);
        int[] at = span(words, phrase);
        if (at == null) {
            return "“" + phrase + "” isn't in that sentence.";
        }
        Tree t = trees.get(0), node = null;
        for (Tree each : trees) {
            node = each.over(at[0], at[1]);
            if (node != null && node.word == null) {
                t = each;
                break;
            }
        }
        if (node == null || node.word != null && at[1] - at[0] > 1) {
            return "No: “" + phrase + "” isn't a constituent of that sentence. No phrase covers exactly those words: they"
                    + " cut across phrases, so no single word could stand in for them and they don't move or answer a question as one.";
        }
        String what = node.words();
        StringBuilder b = new StringBuilder("Yes: “" + what + "” is a constituent, a " + name(node.label) + " (" + node.label + ").");
        String rest = String.join(" ", words.subList(at[1], words.size()));
        String before = String.join(" ", words.subList(0, at[0]));
        switch (node.label) {
            case "NP": {
                String pron = at[0] == 0 ? pronoun(node, true) : pronoun(node, false);
                b.append("\n• Substitution: ").append(join(before, pron, rest)).append(".");
                b.append("\n• Movement: It was ").append(low(node)).append(" that ").append(plain(before, rest)).append(".");
                b.append("\n• Fragment: Q: ").append(at[0] == 0 ? "Who or what " + rest : "What did " + before + " " + "do it to")
                        .append("? A: ").append(cap(what)).append(".");
                break;
            }
            case "PP":
                b.append("\n• Substitution: ").append(join(before, "there", rest)).append(".");
                b.append("\n• Movement: It was ").append(low(node)).append(" that ").append(plain(before, rest)).append(".");
                b.append("\n• Fragment: Q: Where? A: ").append(cap(what)).append(".");
                break;
            case "VP":
                b.append("\n• Substitution: ").append(join(before, "did so", rest)).append(".");
                b.append("\n• Fragment: Q: What did ").append(before.isEmpty() ? "they" : before).append(" do? A: ").append(cap(what)).append(".");
                break;
            default:
                b.append("\n• It is one node in the tree, so it moves and stands in as one.");
        }
        return b.toString();
    }

    private static String pronoun(Tree np, boolean subject) {
        String w = np.words().toLowerCase(Locale.ROOT);
        if (w.matches(".*\\b(engineers|users|people|servers|nodes|they|we)\\b.*") || w.endsWith("s") && !w.endsWith("ss")) {
            return subject ? "They" : "them";
        }
        if (w.matches(".*\\b(woman|girl|she|her|lady|mother|queen|architect|engineer|user|man|boy|he|him|king|father|teacher|doctor)\\b.*")) {
            return subject ? "She" : "her";
        }
        return subject ? "It" : "it";
    }

    /** A phrase as it reads inside a sentence: its first word lower case unless it is a name. */
    private static String low(Tree node) {
        String w = node.words();
        Tree first = node;
        while (first.word == null && !first.kids.isEmpty()) {
            first = first.kids.get(0);
        }
        return first.label.equals("Name") || "I".equals(first.word) ? w : Character.toLowerCase(w.charAt(0)) + w.substring(1);
    }

    /** The rest of the sentence, as it reads after "that": the first word lower case unless it is a name or "I". */
    private static String plain(String a, String b) {
        String s = ((a.isEmpty() ? "" : a + " ") + b).trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) {
            return s;
        }
        String first = s.split(" ")[0];
        boolean name = first.equals("I") || first.length() > 1 && Character.isUpperCase(first.charAt(0))
                && !first.matches("The|A|An|This|That|She|He|It|They|We|You|My|Our|Your|His|Her|Their");
        return name ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /** A noun phrase's head noun ("user" in "the user"). */
    private static String head(Tree np) {
        String last = null;
        for (Tree k : np.kids) {
            if (k.word != null && (k.label.equals("N") || k.label.equals("Name") || k.label.equals("Pron"))) {
                last = k.word;
            }
        }
        return last != null ? last : np.words();
    }

    private static String join(String a, String mid, String b) {
        String s = (a.isEmpty() ? "" : a + " ") + mid + (b.isEmpty() ? "" : " " + b);
        s = s.trim().replaceAll("\\s+", " ");
        return s.isEmpty() ? s : cap(s);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Why a sentence with two readings means two things: where the phrase that moves attaches in each. "I saw the user
     * with the telescope": under the VP it says how the seeing was done; inside the NP it says which user.
     */
    public static String ambiguity(String sentence, List<Tree> trees) {
        if (trees.size() < 2) {
            return null;
        }
        Tree a = trees.get(0), b = trees.get(1);
        for (Tree p : a.phrases()) {
            if (!p.label.equals("PP")) {
                continue;
            }
            Tree q = b.over(p.from, p.to);
            if (q == null || !q.label.equals("PP")) {
                continue;
            }
            Tree pa = a.parentOf(p), pb = b.parentOf(q);
            if (pa != null && pb != null && !pa.label.equals(pb.label)) {
                Tree vp = pa.label.equals("VP") ? pa : pb, np = pa.label.equals("VP") ? pb : pa;
                String noun = head(np.kids.isEmpty() ? np : np.kids.get(0));
                String prep = p.kids.isEmpty() ? "" : p.kids.get(0).words().toLowerCase(Locale.ROOT);
                String how = prep.matches("with|by|using|through|via") ? "how" : prep.matches("on|in|at|near|under|over|inside|beside") ? "where" : "how or where";
                String verb = Grammar.base(verbOf(vp));
                String doing = verb.endsWith("e") && !verb.endsWith("ee") ? verb.substring(0, verb.length() - 1) + "ing" : verb + "ing";
                return "It's ambiguous: the same words, two structures.\n\u2022 Reading A, \u201c" + p.words() + "\u201d attached to the verb"
                        + " phrase: it says " + how + " the " + doing + " was done.\n\u2022 Reading B, \u201c" + p.words()
                        + "\u201d attached inside the noun phrase: it says which " + noun + ".";
            }
        }
        return "It has " + trees.size() + " readings: its words can be grouped in more than one way.";
    }

    private static String verbOf(Tree vp) {
        for (Tree k : vp.kids) {
            if (k.word != null && (k.label.equals("V") || k.label.equals("Aux"))) {
                return k.word;
            }
        }
        return vp.words().split(" ")[0];
    }

    // ------------------------------------------------------------------ drawing

    /** A tree drawn as lines of text, with where its label's middle is. */
    static final class Drawing {
        final List<String> lines;
        final int width, middle;

        Drawing(List<String> lines, int width, int middle) {
            this.lines = lines;
            this.width = width;
            this.middle = middle;
        }

        static Drawing of(Tree t) {
            if (t.word != null) {
                String top = t.label, bottom = t.word;
                int w = Math.max(top.length(), bottom.length());
                List<String> ls = new ArrayList<String>();
                ls.add(center(top, w));
                ls.add(center("|", w));
                ls.add(center(bottom, w));
                return new Drawing(ls, w, w / 2);
            }
            List<Drawing> kids = new ArrayList<Drawing>();
            for (Tree k : t.kids) {
                kids.add(of(k));
            }
            int gap = 2, width = 0, height = 0;
            List<Integer> starts = new ArrayList<Integer>();
            for (Drawing d : kids) {
                starts.add(width);
                width += d.width + gap;
                height = Math.max(height, d.lines.size());
            }
            width -= gap;
            int first = starts.get(0) + kids.get(0).middle;
            int last = starts.get(kids.size() - 1) + kids.get(kids.size() - 1).middle;
            int mid = (first + last) / 2;
            width = Math.max(width, t.label.length());
            List<String> ls = new ArrayList<String>();
            StringBuilder label = new StringBuilder(spaces(width));
            int at = Math.max(0, Math.min(width - t.label.length(), mid - t.label.length() / 2));
            label.replace(at, at + t.label.length(), t.label);
            ls.add(label.toString());
            StringBuilder branch = new StringBuilder(spaces(width));
            for (int i = 0; i < kids.size(); i++) {
                int c = starts.get(i) + kids.get(i).middle;
                branch.setCharAt(c, kids.size() == 1 || c == mid ? '|' : c < mid ? '/' : '\\');
            }
            ls.add(branch.toString());
            for (int row = 0; row < height; row++) {
                StringBuilder line = new StringBuilder(spaces(width));
                for (int i = 0; i < kids.size(); i++) {
                    Drawing d = kids.get(i);
                    if (row < d.lines.size()) {
                        String s = d.lines.get(row);
                        line.replace(starts.get(i), starts.get(i) + s.length(), s);
                    }
                }
                ls.add(line.toString());
            }
            return new Drawing(ls, width, mid);
        }

        private static String spaces(int n) {
            char[] c = new char[Math.max(0, n)];
            Arrays.fill(c, ' ');
            return new String(c);
        }

        private static String center(String s, int w) {
            int left = (w - s.length()) / 2;
            return spaces(left) + s + spaces(w - s.length() - left);
        }
    }
}
