package ai.ibiko.ibiko1.language;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.ibiko.ibiko1.words.Morph;

/**
 * A word's linguistic record: its lemma, every part of speech it can be, how it is built (root and affixes), its
 * meanings, its grammatical features, and, when it is read in a sentence, what it is there. A word is classified in
 * its sentence, not memorised once: "running" is a verb in "I am running", a gerund (acting as a noun) in "Running is
 * healthy", and a participle (acting as an adjective) in "running water". Pure Java.
 *
 * <pre>
 * {"word": "running", "lemma": "run", "possible_categories": ["verb", "noun", "adjective"],
 *  "morphology": {"root": "run", "suffix": "ing"}, "meanings": ["…", "…"],
 *  "grammar_features": {"verb_form": "present_participle_or_gerund"},
 *  "in_sentence": {"sentence": "Running is healthy.", "role": "gerund", "acts_as": "noun"},
 *  "context_required": true}
 * </pre>
 */
public final class WordRecord {
    public final String word, lemma;
    public final List<String> categories, meanings;
    public final Map<String, String> morphology, features;
    /** What the word is in the sentence it was read in; empty without one. */
    public final Map<String, String> inSentence;
    public final boolean contextRequired;

    private WordRecord(String word, String lemma, List<String> categories, List<String> meanings, Map<String, String> morphology,
            Map<String, String> features, Map<String, String> inSentence, boolean contextRequired) {
        this.word = word;
        this.lemma = lemma;
        this.categories = categories;
        this.meanings = meanings;
        this.morphology = morphology;
        this.features = features;
        this.inSentence = inSentence;
        this.contextRequired = contextRequired;
    }

    private static final String[][] SUFFIXES = {{"ing", "present participle or gerund"}, {"ed", "past tense or past participle"},
        {"est", "superlative"}, {"er", "comparative, or one who does it"}, {"ly", "adverb of manner"}, {"ness", "the state of being"},
        {"ment", "the act or result of"}, {"tion", "the act or result of"}, {"able", "able to be"}, {"ible", "able to be"},
        {"ful", "full of"}, {"less", "without"}, {"ize", "to make"}, {"ise", "to make"}, {"ity", "the quality of"}, {"ous", "full of"},
        {"es", "plural or third person"}, {"s", "plural or third person"}};
    private static final String[][] PREFIXES = {{"un", "not, or reversing"}, {"re", "again"}, {"dis", "not, or apart"}, {"pre", "before"},
        {"mis", "wrongly"}, {"non", "not"}, {"over", "too much"}, {"under", "too little"}, {"in", "not"}, {"im", "not"}, {"anti", "against"},
        {"inter", "between"}, {"sub", "under"}, {"super", "above"}, {"co", "together"}};

    /**
     * The record of a word, read in a sentence when one is given.
     *
     * @param source the dictionary, or null for what the word's form alone shows
     */
    public static WordRecord of(String word, String sentence, Relations.Source source) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        String lemma = lemma(w, source);
        Set<String> cats = new LinkedHashSet<String>();
        List<String> meanings = new ArrayList<String>();
        for (String look : new String[]{w, lemma}) {
            for (String[] s : senses(source, look)) {
                if (s.length > 1 && meanings.size() < 5 && !meanings.contains(s[1])) {
                    meanings.add(s[1]);
                }
                if (s.length > 0 && !s[0].isEmpty()) {
                    cats.add(s[0]);
                }
            }
        }
        if (w.endsWith("ing") && !lemma.equals(w)) {
            cats.add("verb");
            cats.add("noun");
            cats.add("adjective");
        } else if (w.endsWith("ed") && !lemma.equals(w) || Morph.irregularParticiple(w) != null) {
            cats.add("verb");
            cats.add("adjective");
        }
        if (cats.isEmpty()) {
            cats.add(guess(w));
        }
        Map<String, String> morph = morphology(w, lemma, source);
        Map<String, String> features = features(w, lemma, cats);
        Map<String, String> in = sentence == null ? new LinkedHashMap<String, String>() : inSentence(w, sentence);
        boolean context = cats.size() > 1 || meanings.size() > 1;
        return new WordRecord(w, lemma, new ArrayList<String>(cats), meanings, morph, features, in, context);
    }

    static String lemma(String w, Relations.Source source) {
        String irr = Morph.irregularPast(w);
        if (irr == null) {
            irr = Morph.irregularParticiple(w);
        }
        if (irr != null) {
            return irr;
        }
        List<String> tries = new ArrayList<String>();
        if (w.endsWith("ing") && w.length() > 5) {
            String stem = w.substring(0, w.length() - 3);
            if (stem.length() > 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                tries.add(stem.substring(0, stem.length() - 1)); // running → run
            }
            tries.add(stem + "e"); // making → make
            tries.add(stem);
        } else if (w.endsWith("ied") || w.endsWith("ies")) {
            tries.add(w.substring(0, w.length() - 3) + "y");
        } else if (w.endsWith("ed") && w.length() > 4) {
            String stem = w.substring(0, w.length() - 2);
            if (stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)) {
                tries.add(stem.substring(0, stem.length() - 1));
            }
            tries.add(stem + "e");
            tries.add(stem);
        } else if (w.endsWith("est") && w.length() > 5) {
            tries.add(w.substring(0, w.length() - 3));
            tries.add(w.substring(0, w.length() - 3) + "e");
        } else if (w.endsWith("es") && w.length() > 4) {
            tries.add(w.substring(0, w.length() - 2));
            tries.add(w.substring(0, w.length() - 1));
        } else if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            tries.add(w.substring(0, w.length() - 1));
        }
        for (String t : tries) {
            if (source != null && !senses(source, t).isEmpty() || source == null && SentenceForm.COMMON.test(t)) {
                return t;
            }
        }
        return tries.isEmpty() || source != null ? w : tries.get(0);
    }

    private static List<String[]> senses(Relations.Source source, String w) {
        try {
            return source == null ? new ArrayList<String[]>() : source.senses(w);
        } catch (RuntimeException e) {
            return new ArrayList<String[]>();
        }
    }

    private static String guess(String w) {
        if (w.endsWith("ly")) {
            return "adverb";
        }
        if (w.matches(".*(tion|ness|ment|ity|ship|ism|ance|ence)$")) {
            return "noun";
        }
        if (w.matches(".*(ous|ful|ive|able|ible|al|ic|less)$")) {
            return "adjective";
        }
        if (w.matches(".*(ize|ise|ify|ate)$")) {
            return "verb";
        }
        return "noun";
    }

    private static Map<String, String> morphology(String w, String lemma, Relations.Source source) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        String root = lemma;
        if (!lemma.equals(w)) {
            for (String[] s : SUFFIXES) {
                if (w.endsWith(s[0])) {
                    m.put("suffix", s[0]);
                    m.put("suffix_means", s[1]);
                    break;
                }
            }
            if (Morph.irregularPast(w) != null || Morph.irregularParticiple(w) != null) {
                m.put("change", "irregular form of “" + lemma + "”");
                m.remove("suffix");
                m.remove("suffix_means");
            }
        } else {
            for (String[] s : SUFFIXES) {
                if (w.endsWith(s[0]) && w.length() > s[0].length() + 3 && !s[0].matches("s|es|er|ed|ing")) {
                    String stem = w.substring(0, w.length() - s[0].length());
                    if (stem.endsWith("i")) {
                        stem = stem.substring(0, stem.length() - 1) + "y"; // happi-ness is happy + -ness
                    } else if (source != null && senses(source, stem).isEmpty() && !senses(source, stem + "e").isEmpty()) {
                        stem = stem + "e"; // us-able is use + -able
                    }
                    m.put("suffix", s[0]);
                    m.put("suffix_means", s[1]);
                    root = stem;
                    break;
                }
            }
        }
        for (String[] p : PREFIXES) {
            if (root.startsWith(p[0]) && root.length() > p[0].length() + 3) {
                String rest = root.substring(p[0].length());
                if (source != null && !senses(source, rest).isEmpty() || source == null && SentenceForm.COMMON.test(rest)) {
                    m.put("prefix", p[0]);
                    m.put("prefix_means", p[1]);
                    root = rest;
                    break;
                }
            }
        }
        m.put("root", root);
        return m;
    }

    private static Map<String, String> features(String w, String lemma, Set<String> cats) {
        Map<String, String> f = new LinkedHashMap<String, String>();
        if (w.endsWith("ing") && !lemma.equals(w)) {
            f.put("verb_form", "present_participle_or_gerund");
            f.put("aspect", "progressive_when_used_as_part_of_a_verb_phrase");
        } else if (Morph.irregularPast(w) != null && Morph.irregularParticiple(w) == null) {
            f.put("verb_form", "past_tense");
            f.put("verb_type", "irregular");
        } else if (Morph.irregularParticiple(w) != null && Morph.irregularPast(w) == null) {
            f.put("verb_form", "past_participle");
            f.put("verb_type", "irregular");
        } else if (Morph.irregularParticiple(w) != null) {
            f.put("verb_form", "past_tense_or_past_participle");
            f.put("verb_type", "irregular");
        } else if (w.endsWith("ed") && !lemma.equals(w)) {
            f.put("verb_form", "past_tense_or_past_participle");
            f.put("verb_type", "regular");
        } else if (w.endsWith("est") && !lemma.equals(w)) {
            f.put("degree", "superlative");
        } else if (w.endsWith("s") && !lemma.equals(w)) {
            f.put("number_or_person", cats.contains("verb") ? "plural noun, or third person singular verb" : "plural");
        } else if (Morph.irregularBase(w)) {
            f.put("verb_type", "irregular (" + w + ", " + Morph.past(w) + ", " + Morph.participle(w) + ")");
        }
        return f;
    }

    /** What the word is in its sentence: verb, gerund (a noun), participle (an adjective) or its part of speech. */
    static Map<String, String> inSentence(String w, String sentence) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        List<String> t = SentenceForm.tokens(sentence);
        int at = t.indexOf(w);
        if (at < 0) {
            return m;
        }
        m.put("sentence", sentence.trim());
        String prev = at > 0 ? t.get(at - 1) : "", next = at + 1 < t.size() ? t.get(at + 1) : "";
        if (w.endsWith("ing") && w.length() > 4) {
            if (SentenceForm.BE.contains(prev) || SentenceForm.SKIPPED.contains(prev) && at > 1 && SentenceForm.BE.contains(t.get(at - 2))) {
                m.put("role", "verb");
                m.put("acts_as", "verb (progressive)");
            } else if (next.matches("[a-z]+") && !SentenceForm.aux(next) && !SentenceForm.PREPOSITIONS.contains(next)
                    && !SentenceForm.DETERMINERS.contains(next) && !next.matches("and|or|but|is|are|was|were|me|him|her|them|us|it")
                    && (prev.isEmpty() || SentenceForm.DETERMINERS.contains(prev) || SentenceForm.PREPOSITIONS.contains(prev))) {
                m.put("role", "participle");
                m.put("acts_as", "adjective (describes “" + next + "”)");
            } else {
                m.put("role", "gerund");
                m.put("acts_as", "noun");
            }
            return m;
        }
        List<ai.ibiko.ibiko1.formulas.PartsOfSpeech.Word> tags = ai.ibiko.ibiko1.formulas.PartsOfSpeech.tag(sentence, null);
        for (ai.ibiko.ibiko1.formulas.PartsOfSpeech.Word x : tags) {
            if (x.text.toLowerCase(Locale.ROOT).equals(w)) {
                m.put("role", x.tag.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                break;
            }
        }
        return m;
    }

    /** The record as JSON (the shape the spec gives, docs/IBIKO_LINGUISTIC_INTELLIGENCE.md). */
    public String json() {
        Map<String, Object> o = new LinkedHashMap<String, Object>();
        o.put("word", word);
        o.put("lemma", lemma);
        o.put("possible_categories", categories);
        o.put("morphology", morphology);
        o.put("meanings", meanings);
        o.put("grammar_features", features);
        if (!inSentence.isEmpty()) {
            o.put("in_sentence", inSentence);
        }
        o.put("context_required", contextRequired);
        return ai.ibiko.ibiko1.Json.write(o);
    }

    /** In plain words. */
    public String said() {
        StringBuilder b = new StringBuilder("“" + word + "”");
        if (!lemma.equals(word)) {
            b.append(" is a form of “").append(lemma).append("”");
        }
        b.append(". It can be ").append(join(categories)).append('.');
        if (morphology.containsKey("prefix") || morphology.containsKey("suffix") || morphology.containsKey("change")) {
            b.append(" Built from: ");
            List<String> parts = new ArrayList<String>();
            if (morphology.containsKey("prefix")) {
                parts.add(morphology.get("prefix") + "- (" + morphology.get("prefix_means") + ")");
            }
            parts.add("root “" + morphology.get("root") + "”");
            if (morphology.containsKey("suffix")) {
                parts.add("-" + morphology.get("suffix") + " (" + morphology.get("suffix_means") + ")");
            }
            if (morphology.containsKey("change")) {
                parts.add(morphology.get("change"));
            }
            b.append(String.join(" + ", parts)).append('.');
        }
        if (!inSentence.isEmpty() && inSentence.containsKey("role")) {
            b.append(" Here it is ").append(Figures.article(inSentence.get("role"))).append(' ').append(inSentence.get("role"));
            if (inSentence.containsKey("acts_as") && !inSentence.get("acts_as").equals(inSentence.get("role"))) {
                b.append(", acting as ").append(Figures.article(inSentence.get("acts_as"))).append(' ').append(inSentence.get("acts_as"));
            }
            b.append('.');
        }
        if (!meanings.isEmpty()) {
            b.append(" Meanings: ").append(String.join("; ", meanings.subList(0, Math.min(3, meanings.size())))).append('.');
        }
        if (contextRequired) {
            b.append(" Which one is meant depends on the sentence.");
        }
        return b.toString();
    }

    private static String join(List<String> l) {
        if (l.size() <= 1) {
            return l.isEmpty() ? "" : Figures.article(l.get(0)) + " " + l.get(0);
        }
        return String.join(", ", l.subList(0, l.size() - 1)) + " or " + l.get(l.size() - 1);
    }
}
