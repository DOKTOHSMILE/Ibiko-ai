package ai.ibiko.ibiko1.words;

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

import ai.ibiko.ibiko1.formulas.Syntax;

/**
 * Morphology, the structure inside words: a word broken into its morphemes (the smallest units of meaning) and built
 * back up one affix at a time, as a binary tree whose every node has a category. Each affix is picky about what it
 * attaches to and what it makes (its selection): negative un- takes an adjective and makes an adjective ("unhappy"),
 * -ness takes an adjective and makes a noun ("happiness"), -ize makes a verb, reversive de- takes a verb, -ation makes a
 * noun of a verb. So "unhappiness" can only be [[un- [happy]Adj]Adj -ness]N: the other order would put un- on a noun.
 * And a word that can be built two legal ways has two trees and two meanings: "unlockable" is "able to be unlocked"
 * ([[un- [lock]V]V -able]Adj) or "not able to be locked" ([un- [[lock]V -able]Adj]Adj).
 * <p>
 * The roots and their categories come from the dictionary ({@link Meanings}); the spelling changes an affix brings are
 * undone when it is taken off ("happi-ness" is happy + -ness, "centr-al" is center + -al, "decentraliz-ation" is
 * decentralize + -ation). Pure Java.
 */
public final class Morphology {
    /** An affix: its form, whether it goes before or after, what it attaches to, what it makes, what it means. */
    public static final class Affix {
        public final String form, makes, means;
        public final boolean prefix, inflection;
        public final Set<String> takes;

        Affix(String form, boolean prefix, String takes, String makes, String means, boolean inflection) {
            this.form = form;
            this.prefix = prefix;
            this.takes = new HashSet<String>(Arrays.asList(takes.split(",")));
            this.makes = makes;
            this.means = means;
            this.inflection = inflection;
        }

        /** As written in a tree: "un-" or "-ness". */
        public String shown() {
            return prefix ? form + "-" : "-" + form;
        }
    }

    /** The affixes English builds words with, each with its selection (N noun, V verb, Adj adjective, Adv adverb). */
    static final List<Affix> AFFIXES = Arrays.asList(
            // prefixes
            new Affix("un", true, "Adj", "Adj", "not", false),
            new Affix("un", true, "V", "V", "reverse", false),
            new Affix("re", true, "V", "V", "again", false),
            new Affix("dis", true, "V", "V", "reverse", false),
            new Affix("dis", true, "Adj", "Adj", "not", false),
            new Affix("de", true, "V", "V", "undo", false),
            new Affix("in", true, "Adj", "Adj", "not", false),
            new Affix("im", true, "Adj", "Adj", "not", false),
            new Affix("il", true, "Adj", "Adj", "not", false),
            new Affix("ir", true, "Adj", "Adj", "not", false),
            new Affix("non", true, "Adj,N", "same", "not", false),
            new Affix("mis", true, "V", "V", "wrongly", false),
            new Affix("pre", true, "V,N", "same", "before", false),
            new Affix("over", true, "V", "V", "too much", false),
            new Affix("under", true, "V", "V", "too little", false),
            new Affix("anti", true, "N", "Adj", "against", false),
            new Affix("co", true, "V,N", "same", "together", false),
            // suffixes that make new words
            new Affix("ness", false, "Adj", "N", "the state of being", false),
            new Affix("ity", false, "Adj", "N", "the quality of being", false),
            new Affix("ment", false, "V", "N", "the result of", false),
            new Affix("ation", false, "V", "N", "the act of", false),
            new Affix("ion", false, "V", "N", "the act of", false),
            new Affix("er", false, "V", "N", "one who", false),
            new Affix("or", false, "V", "N", "one who", false),
            new Affix("ist", false, "N,Adj", "N", "one who practises", false),
            new Affix("ism", false, "N,Adj", "N", "the belief in", false),
            new Affix("able", false, "V", "Adj", "able to be", false),
            new Affix("ible", false, "V", "Adj", "able to be", false),
            new Affix("al", false, "N", "Adj", "relating to", false),
            new Affix("ful", false, "N", "Adj", "full of", false),
            new Affix("less", false, "N", "Adj", "without", false),
            new Affix("ous", false, "N", "Adj", "full of", false),
            new Affix("ive", false, "V", "Adj", "tending to", false),
            new Affix("ic", false, "N", "Adj", "relating to", false),
            new Affix("ize", false, "N,Adj", "V", "make", false),
            new Affix("ise", false, "N,Adj", "V", "make", false),
            new Affix("ify", false, "N,Adj", "V", "make", false),
            new Affix("en", false, "Adj", "V", "make", false),
            new Affix("ly", false, "Adj", "Adv", "in a way that is", false),
            new Affix("ship", false, "N", "N", "the state of being a", false),
            new Affix("hood", false, "N", "N", "the time or state of being a", false),
            new Affix("y", false, "N", "Adj", "like or full of", false),
            // inflections: endings that don't make a new word
            new Affix("s", false, "N", "N", "plural", true),
            new Affix("es", false, "N", "N", "plural", true),
            new Affix("ed", false, "V", "V", "past", true),
            new Affix("ing", false, "V", "V", "ongoing", true),
            new Affix("er", false, "Adj", "Adj", "more", true),
            new Affix("est", false, "Adj", "Adj", "most", true));

    /** A word built: its category, the morpheme (for a root), the affix, and its parts. */
    public static final class Build {
        public final String category, root;
        public final Affix affix;
        public final Build base;
        /** Whether every intermediate word is one the dictionary knows (not only a possible one). */
        public final boolean attested;

        Build(String category, String root) {
            this.category = category;
            this.root = root;
            this.affix = null;
            this.base = null;
            this.attested = true;
        }

        Build(String category, Affix affix, Build base, boolean attested) {
            this.category = category;
            this.root = null;
            this.affix = affix;
            this.base = base;
            this.attested = attested;
        }

        /** Bracket notation: [[un- [happy]Adj]Adj -ness]N. */
        public String brackets() {
            if (root != null) {
                return "[" + root + "]" + category;
            }
            return affix.prefix ? "[" + affix.shown() + " " + base.brackets() + "]" + category
                    : "[" + base.brackets() + " " + affix.shown() + "]" + category;
        }

        /** As a tree to draw. */
        public Syntax.Tree tree() {
            if (root != null) {
                return Syntax.Tree.node(category, Arrays.asList(Syntax.Tree.leaf(category.equals("N") ? "→" : "→", root)))
                        .kids.get(0).word == null ? null : Syntax.Tree.leaf(category, root);
            }
            Syntax.Tree a = Syntax.Tree.leaf("affix", affix.shown()), b = base.tree();
            return Syntax.Tree.node(category, affix.prefix ? Arrays.asList(a, b) : Arrays.asList(b, a));
        }

        /** Its morphemes, in order. */
        public List<String> morphemes() {
            List<String> out = new ArrayList<String>();
            if (root != null) {
                out.add(root);
                return out;
            }
            if (affix.prefix) {
                out.add(affix.shown());
                out.addAll(base.morphemes());
            } else {
                out.addAll(base.morphemes());
                out.add(affix.shown());
            }
            return out;
        }

        /** What the word means, put together from its parts: "not able to be locked". */
        public String meaning() {
            if (root != null) {
                return root;
            }
            String inner = base.meaning();
            switch (affix.means) {
                case "not": return "not " + inner;
                case "reverse": return "to reverse " + strip(inner) + " (" + affix.form + base.surface() + ")";
                case "undo": return "to undo " + strip(inner);
                case "again": return strip(inner) + " again";
                case "able to be": return "able to be " + Morph.participle(base.surface());
                case "the state of being": return "the state of being " + inner;
                case "the quality of being": return "the quality of being " + inner;
                case "the act of": return "the act of " + Morph.ing(base.surface());
                case "the result of": return "the result of " + Morph.ing(base.surface());
                case "one who": return "one who " + Morph.third(base.surface());
                case "make": return "to make " + inner + (base.category.equals("Adj") ? "" : "-like");
                case "relating to": return "relating to " + inner;
                case "in a way that is": return "in a way that is " + inner;
                case "plural": return "more than one " + inner;
                case "past": return inner + " (in the past)";
                case "ongoing": return inner + " (going on)";
                case "more": return "more " + inner;
                case "most": return "most " + inner;
                default: return affix.means + " " + inner;
            }
        }

        private static String strip(String meaning) {
            return meaning.replaceFirst("^to ", "");
        }

        /** The word this build spells (for meanings): the root, or the base with its affix as written. */
        public String surface() {
            if (root != null) {
                return root;
            }
            String b = base.surface();
            if (affix.prefix) {
                return affix.form + b;
            }
            return Morphology.attach(b, affix.form);
        }

        int depth() {
            return root != null ? 0 : 1 + base.depth();
        }
    }

    /** Spelling when a suffix is attached: "happy" + "ness" → "happiness", "decentralize" + "ation" → "decentralization". */
    static String attach(String stem, String suffix) {
        if (suffix.equals("s") || suffix.equals("es")) {
            return Morph.plural(stem);
        }
        if (suffix.equals("ed")) {
            return Morph.past(stem);
        }
        if (suffix.equals("ing")) {
            return Morph.ing(stem);
        }
        if (stem.matches(".*[^aeiou]y$") && !suffix.startsWith("i")) {
            return stem.substring(0, stem.length() - 1) + "i" + suffix;
        }
        if (stem.endsWith("e") && "aeiouy".indexOf(suffix.charAt(0)) >= 0) {
            return stem.substring(0, stem.length() - 1) + suffix;
        }
        if (stem.endsWith("er") && suffix.equals("al")) {
            return stem.substring(0, stem.length() - 2) + "ral"; // center + al = central
        }
        if (stem.endsWith("re") && suffix.equals("al")) {
            return stem.substring(0, stem.length() - 2) + "ral";
        }
        return stem + suffix;
    }

    /** The stems a word may be once a suffix is taken off, undoing the spelling the suffix brought. */
    static List<String> stems(String word, String suffix) {
        List<String> out = new ArrayList<String>();
        if (!word.endsWith(suffix) || word.length() - suffix.length() < 2) {
            return out;
        }
        String s = word.substring(0, word.length() - suffix.length());
        out.add(s);
        if (s.endsWith("r") && suffix.equals("al")) {
            out.add(s.substring(0, s.length() - 1) + "er"); // centr-al: center (before "centre")
        }
        out.add(s + "e");
        if (s.endsWith("i")) {
            out.add(s.substring(0, s.length() - 1) + "y");
        }
        if (s.length() > 2 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2)) {
            out.add(s.substring(0, s.length() - 1));
        }
        if (s.endsWith("r") && suffix.equals("al")) {
            out.add(s.substring(0, s.length() - 1) + "er"); // centr-al: center
            out.add(s.substring(0, s.length() - 1) + "re"); // centre
        }
        if (suffix.equals("ation") || suffix.equals("ion")) {
            out.add(s + "e");
            out.add(s + "ate");
        }
        return out;
    }

    private final Meanings meanings;
    /** The categories the dictionary gives a word (N, V, Adj, Adv), kept as they are looked up. */
    private final Map<String, Set<String>> cats = new HashMap<String, Set<String>>();

    public Morphology(Meanings meanings) {
        this.meanings = meanings;
    }

    /** The categories a word has in the dictionary (as written, not its base): empty when it isn't a word. */
    public Set<String> categories(String w) {
        Set<String> c = cats.get(w);
        if (c != null) {
            return c;
        }
        c = new LinkedHashSet<String>();
        if (meanings != null) {
            for (MeaningIndex.Sense s : meanings.index().senses(w)) {
                switch (s.pos) {
                    case "noun": c.add("N"); break;
                    case "verb": c.add("V"); break;
                    case "adjective": c.add("Adj"); break;
                    case "adverb": c.add("Adv"); break;
                    default: break;
                }
            }
        }
        cats.put(w, c);
        return c;
    }

    /**
     * Every legal way to build a word, the best first: each affix taken off in turn (its spelling undone), down to a root
     * the dictionary knows, every step checked against the affix's selection (what it attaches to). A step that would
     * make an illegal intermediate (an affix on the wrong category) is never taken. Builds whose every intermediate
     * word is in the dictionary come first, then the ones with fewer steps.
     */
    public List<Build> builds(String word) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        Map<String, Build> unique = new LinkedHashMap<String, Build>();
        for (Build b : build(w, 0)) {
            unique.putIfAbsent(b.brackets(), b);
        }
        List<Build> all = new ArrayList<Build>(unique.values());
        // the whole word's own category, when the dictionary knows it, decides between readings that make different ones
        Set<String> own = categories(w);
        all.removeIf(b -> b.root == null && !own.isEmpty() && !own.contains(b.category) && !b.affix.inflection);
        all.sort((a, b) -> a.attested != b.attested ? (a.attested ? -1 : 1) : Integer.compare(b.depth(), a.depth()));
        // a word that is itself a root: its own entry is one reading only when nothing builds it
        List<Build> built = new ArrayList<Build>();
        for (Build b : all) {
            if (b.root == null) {
                built.add(b);
            }
        }
        return built.isEmpty() ? all : built;
    }

    private List<Build> build(String w, int depth) {
        List<Build> out = new ArrayList<Build>();
        if (depth > 6 || w.length() < 2) {
            return out;
        }
        for (String c : categories(w)) {
            out.add(new Build(c, w));
        }
        for (Affix a : AFFIXES) {
            List<String> bases = new ArrayList<String>();
            if (a.prefix) {
                if (w.startsWith(a.form) && w.length() - a.form.length() >= 3) {
                    bases.add(w.substring(a.form.length()));
                }
            } else {
                bases.addAll(stems(w, a.form));
            }
            for (String b : new LinkedHashSet<String>(bases)) {
                if (b.length() < 3) {
                    continue;
                }
                for (Build inner : build(b, depth + 1)) {
                    if (!a.takes.contains(inner.category)) {
                        continue; // selection: this affix doesn't attach to that category
                    }
                    if (!a.prefix && !attach(inner.surface(), a.form).equals(w)) {
                        continue; // the spelling must come back to the word
                    }
                    if (inner.affix != null && inner.affix.inflection) {
                        continue; // inflection sits outside derivation: nothing attaches after it
                    }
                    String makes = a.makes.equals("same") ? inner.category : a.makes;
                    boolean attested = inner.attested && (depth == 0 || !categories(w).isEmpty());
                    out.add(new Build(makes, a, inner, attested));
                }
            }
        }
        return out;
    }

    /** Builds that differ in meaning (the same morphemes in different trees): a word with structural ambiguity. */
    public List<Build> readings(String word) {
        List<Build> all = builds(word);
        if (all.isEmpty() || all.get(0).root != null) {
            return all;
        }
        List<String> morphemes = all.get(0).morphemes();
        List<Build> same = new ArrayList<Build>();
        for (Build b : all) {
            if (b.morphemes().equals(morphemes) && same.size() < 3) {
                same.add(b);
            }
        }
        return same;
    }

    // ------------------------------------------------------------------ how a word was formed

    private static final Map<String, String> BLENDS = new HashMap<String, String>(), CLIPPED = new HashMap<String, String>(),
            BACK = new HashMap<String, String>();

    static {
        String[] b = {"brunch", "breakfast + lunch", "smog", "smoke + fog", "motel", "motor + hotel", "podcast", "iPod + broadcast",
                "tokenomics", "token + economics", "emoticon", "emotion + icon", "blog", "web + log", "netizen", "net + citizen",
                "infomercial", "information + commercial", "spork", "spoon + fork", "workaholic", "work + alcoholic", "malware",
                "malicious + software", "webinar", "web + seminar", "vlog", "video + blog", "glamping", "glamorous + camping"};
        for (int i = 0; i + 1 < b.length; i += 2) {
            BLENDS.put(b[i], b[i + 1]);
        }
        String[] c = {"app", "application", "exam", "examination", "lab", "laboratory", "phone", "telephone", "flu", "influenza",
                "gym", "gymnasium", "ad", "advertisement", "bus", "omnibus", "fridge", "refrigerator", "math", "mathematics", "maths",
                "mathematics", "photo", "photograph", "info", "information", "demo", "demonstration", "memo", "memorandum", "pro",
                "professional", "vet", "veterinarian", "uni", "university", "plane", "aeroplane", "net", "internet", "bike", "bicycle"};
        for (int i = 0; i + 1 < c.length; i += 2) {
            CLIPPED.put(c[i], c[i + 1]);
        }
        String[] k = {"edit", "editor", "burgle", "burglar", "televise", "television", "donate", "donation", "babysit", "babysitter",
                "enthuse", "enthusiasm", "diagnose", "diagnosis", "laze", "lazy", "beg", "beggar", "opine", "opinion", "orientate",
                "orientation", "liaise", "liaison", "sculpt", "sculptor", "peddle", "peddler", "swindle", "swindler", "resurrect",
                "resurrection"};
        for (int i = 0; i + 1 < k.length; i += 2) {
            BACK.put(k[i], k[i + 1]);
        }
    }

    /**
     * How a word was formed: compounding (two words joined, the right one its head), conversion (the same word as noun
     * and verb), reduplication (a part said twice), blending, clipping, an acronym or initialism, back-formation, or
     * affixation (derivation and inflection, with its tree).
     */
    public String formation(String word) {
        String raw = word.trim(), w = raw.toLowerCase(Locale.ROOT);
        if (raw.matches("[A-Z]{2,6}")) {
            return raw + " is an " + (raw.matches(".*[AEIOU].*") && raw.length() > 3 ? "acronym, said as a word" : "initialism, spelled out letter by letter")
                    + ": made from the first letters of a phrase.";
        }
        if (BLENDS.containsKey(w)) {
            return w + " is a blend: parts of " + BLENDS.get(w) + " merged into one word.";
        }
        if (CLIPPED.containsKey(w)) {
            return w + " is a clipping: " + CLIPPED.get(w) + " shortened, with the same meaning.";
        }
        if (BACK.containsKey(w)) {
            return w + " is a back-formation: made by taking what looked like an affix off " + BACK.get(w) + ".";
        }
        String[] dash = w.split("-");
        if (dash.length == 2 && dash[0].equals(dash[1]) || w.length() >= 4 && w.length() % 2 == 0
                && w.substring(0, w.length() / 2).equals(w.substring(w.length() / 2))) {
            String part = dash.length == 2 ? dash[0] : w.substring(0, w.length() / 2);
            return w + " is reduplication: “" + part + "” said twice, which can mean more of it, or done bit by bit"
                    + (part.equals("small") ? " (Nigerian Pidgin “small-small”: gradually)" : part.equals("sharp") ? " (“sharp-sharp”: very quickly)" : "")
                    + ".";
        }
        // a compound: two words the dictionary knows, the right-hand one its head
        for (int i = 3; i <= w.length() - 3; i++) {
            String left = w.substring(0, i), right = w.substring(i);
            if (!categories(left).isEmpty() && !categories(right).isEmpty() && left.length() >= 3 && right.length() >= 3
                    && builds(w).stream().noneMatch(b -> b.root == null && b.affix != null)) {
                Set<String> head = categories(right);
                return w + " is a compound: " + left + " + " + right + ". The right-hand word is its head, so a " + w + " is a kind of "
                        + right + (head.contains("N") ? " (a noun)" : "") + ".";
            }
        }
        List<Build> bs = builds(w);
        if (!bs.isEmpty() && bs.get(0).root == null) {
            Build b = bs.get(0);
            return w + " is " + (b.affix.inflection ? "inflected" : "derived") + ": " + String.join(" + ", b.morphemes()) + ", "
                    + b.brackets() + ".";
        }
        Set<String> c = categories(w);
        if (c.contains("N") && c.contains("V")) {
            return w + " is used as a noun and as a verb: conversion (zero derivation) lets the same word change category with"
                    + " no affix (“a " + w + "”, “to " + w + "”).";
        }
        return c.isEmpty() ? null : w + " is a single morpheme: a root with nothing to take off.";
    }
}
