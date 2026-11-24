package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.words.GrammarCheck;
import ai.ibiko.ibiko1.words.MeaningIndex;
import ai.ibiko.ibiko1.words.Meanings;
import ai.ibiko.ibiko1.words.Morph;
import ai.ibiko.ibiko1.words.Morphology;

/**
 * Talking about words, meanings and grammar, answered from the words engine: what a word means (in plain words first,
 * then its definition), what it means in a given sentence, the word for a meaning, synonyms and opposites, a sentence
 * explained word by word or said in other words, whether two sentences mean the same, a word's morphemes and tree, its
 * inflected forms and how their endings sound, and whether a sentence is grammatical. Pure Java.
 */
public final class WordTalk {
    private final Meanings meanings;
    private final Morphology morphology;
    /** Meanings by subject and the context tags ("n.root@computing"); null without them. */
    private final ai.ibiko.ibiko1.words.SenseTags tags;
    /** What the conversation is about ("computing"), so "what is root?" means the right root; null when unknown. */
    private volatile String topic;

    public WordTalk(Meanings meanings) {
        this(meanings, null);
    }

    public WordTalk(Meanings meanings, ai.ibiko.ibiko1.words.SenseTags tags) {
        this.meanings = meanings;
        this.morphology = new Morphology(meanings);
        this.tags = tags;
    }

    /** What the conversation is about now, or null. */
    public void topic(String subject) {
        topic = subject;
    }

    public ai.ibiko.ibiko1.words.SenseTags tags() {
        return tags;
    }

    private static final String Q = "[\"“”'‘’]?";
    private static final String W = Q + "([\\p{L}][\\p{L}'-]*)" + Q;
    private static final String S = Q + "(.+?)" + Q;
    private static final String END = "\\s*[?.!]*$";

    private static final Pattern TAGS = Pattern.compile("(?is)^(?:(?:sense|context)[- ]?tag|tag(?: the words (?:of|in))?|"
            + "(?:show|give) me the (?:context |sense )?(?:tags|code) (?:of|for))\\s+(?:this|the)?\\s*(?:sentence|text)?\\s*[:\\-]\\s*" + "(.+?)"
            + "\\s*$");
    private static final Pattern NUMBERS = Pattern.compile("(?is)^(?:read|explain|what are|what kind of numbers are)\\s+(?:the\\s+)?numbers?"
            + "\\s+(?:in|of)\\s*(?:this|these|the)?\\s*(?:sentence|text|message)?\\s*[:\\-]\\s*(.+?)\\s*$");
    private static final Pattern WHAT_IS = Pattern.compile("(?is)^(?:what(?:'s| is| are)|define|meaning of|what does)\\s+(?:an?\\s+|the\\s+)?" + W
            + "(?:\\s+mean)?(?:\\s+(?:in|for|when talking about)\\s+([\\p{L} ]+?))?" + END);
    private static final Pattern IN_CONTEXT = Pattern.compile("(?is)^(?:what does|what is the meaning of|what's the meaning of|meaning of)\\s+" + W
            + "\\s+(?:mean\\s+)?(?:in|here in|in the sentence|in this sentence)\\s*[:\\-]?\\s*" + S + END);
    private static final Pattern MEANS = Pattern.compile("(?is)^(?:what does\\s+" + W + "\\s+mean|what is the meaning of\\s+" + W
            + "|what's the meaning of\\s+" + W + "|meaning of\\s+" + W + "|define\\s+" + W + "|definition of\\s+" + W + ")" + END);
    private static final Pattern WORD_FOR = Pattern.compile("(?is)^(?:what(?:'s| is) (?:a|the) word (?:for|that means|meaning)|"
            + "(?:give me |tell me )?(?:a|the|one) word (?:for|that means|meaning)|what do (?:you|we|people) call|what(?:'s| is) it called when|"
            + "what(?:'s| is) (?:a|the) term for|is there a word for)\\s+" + S + END);
    private static final Pattern SYNONYMS = Pattern.compile("(?is)^(?:(?:give me |list |what are (?:the |some )?)?synonyms? (?:of|for)|"
            + "(?:what(?:'s| is) )?another word for|(?:what(?:'s| is) )?a synonym (?:of|for)|other words for|words like)\\s+" + W + END);
    /** "Is run a verb or a noun?", "is quickly an adverb?", "what part of speech is beautiful?" */
    private static final String POS_WORDS = "verb|noun|adjective|adverb|pronoun|preposition|conjunction";
    private static final Pattern PART_OF_SPEECH = Pattern.compile("(?i)^\\s*(?:is (?:the word )?[\"“']?([a-z][a-z'-]*)[\"”']? (?:a|an) (" + POS_WORDS + ")(?: or (?:a|an) ("
            + POS_WORDS + "))?|what part of speech is (?:the word )?[\"“']?([a-z][a-z'-]*)[\"”']?|what (?:kind|type) of word is [\"“']?([a-z][a-z'-]*)[\"”']?)\\s*[?.!]*\\s*$");
    private static final Pattern OPPOSITES = Pattern.compile("(?is)^(?:(?:give me |list |what are (?:the |some )?)?antonyms? (?:of|for)|"
            + "(?:what(?:'s| is) )?the opposite of|opposite of|what(?:'s| is) (?:an|the) antonym (?:of|for))\\s+" + W + END);
    private static final Pattern EXPLAIN = Pattern.compile("(?is)^(?:explain|break down|analy[sz]e the meaning of|what does)\\s+(?:this|the)?\\s*"
            + "(?:sentence|statement|line)?\\s*[:\\-]?\\s*" + S + "(?:\\s+mean)?" + END);
    private static final Pattern OTHER_WORDS = Pattern.compile("(?is)^(?:say|put|write)\\s+" + S + "\\s+(?:in other words|another way|differently)"
            + END + "|^(?:paraphrase|rephrase|reword|say it another way|in other words)\\s*[:\\-]\\s*" + S + END);
    private static final Pattern SAME = Pattern.compile("(?is)^(?:do|does|are)\\s+" + S + "\\s+and\\s+" + S
            + "\\s+(?:mean|say) the same(?: thing)?" + END + "|^(?:is|does)\\s+" + S + "\\s+(?:mean the same as|the same as|same as)\\s+" + S + END
            + "|^compare\\s+" + S + "\\s+(?:and|with|to)\\s+" + S + END);
    private static final Pattern MORPH = Pattern.compile("(?is)^(?:break down the word|break down|morphemes (?:of|in)|what are the morphemes (?:of|in)|"
            + "morphology of|word structure of|draw the (?:word|morphological|morphology) tree (?:of|for)|how is|what is the structure of the word)\\s+" + W
            + "(?:\\s+(?:formed|built|made))?" + END);
    private static final Pattern FORM = Pattern.compile("(?is)^(?:what(?:'s| is) )?the\\s+(plural|past tense|past participle|participle|-ing form|ing form|"
            + "present participle|comparative|superlative|third person)(?: form)? of\\s+" + W + END);
    private static final Pattern SOUND = Pattern.compile("(?is)^how (?:is|do you say|do you pronounce)(?: the)?\\s+(plural|past|-s|-ed|s|ed|ending)"
            + "(?: ending)?(?: of| in| on)?\\s+" + W + "(?:\\s+pronounced)?" + END);
    private static final Pattern GRAMMAR = Pattern.compile("(?is)^(?:is this (?:sentence )?(?:grammatical|correct|right|good english)|"
            + "check (?:the )?grammar(?: of| in)?|correct (?:this|the grammar of|the grammar in)?(?: sentence)?|is it grammatical|grammar check)\\s*[:\\-]?\\s*" + S + END
            + "|^is\\s+" + S + "\\s+(?:grammatical|correct english|good english|grammatically correct)" + END);

    // the neural layer (docs/IBIKO_NEURAL.md): meaning as geometry
    private static final Pattern SIMILAR = Pattern.compile("(?is)^(?:how (?:similar|alike|close|related) (?:are|is)|how much alike are|"
            + "(?:what(?:'s| is) the )?similarity (?:of|between))\\s+" + S + "\\s+(?:and|to|with)\\s+" + S + END);
    private static final Pattern CLOSE = Pattern.compile("(?is)^(?:(?:give me |list |show me |what are )?(?:some )?(?:words|terms) "
            + "(?:close to|related to|similar to|near|associated with|that go with)|what words go with|what goes with|"
            + "what(?:'s| is) (?:close|related) to)\\s+" + W + END);
    private static final Pattern ODD = Pattern.compile("(?is)^(?:(?:which|what)(?:'s| is)? (?:the )?odd one out|odd one out|"
            + "which (?:one|word) does(?:n't| not) belong|which (?:one|word) is different)\\s*(?:in|of|among|from)?\\s*[:\\-]?\\s*(.+?)" + END);

    private static String first(Matcher m) {
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                return m.group(g).trim();
            }
        }
        return null;
    }

    private static List<String> all(Matcher m) {
        List<String> out = new ArrayList<String>();
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                out.add(m.group(g).trim());
            }
        }
        return out;
    }

    /** Whether a message asks about words, meanings or grammar (before the dictionary is opened to answer it). */
    public static boolean asks(String message) {
        if (message == null) {
            return false;
        }
        String m = message.trim();
        for (Pattern p : new Pattern[]{NUMBERS, PART_OF_SPEECH, TAGS, IN_CONTEXT, MEANS, WORD_FOR, SYNONYMS, OPPOSITES, SAME, OTHER_WORDS, GRAMMAR, FORM, SOUND, MORPH, SIMILAR, CLOSE, ODD}) {
            if (p.matcher(m).matches()) {
                return true;
            }
        }
        Matcher x = EXPLAIN.matcher(m);
        return x.matches() && first(x).split("\\s+").length >= 3;
    }

    /** What parts of speech a word can be, from its senses in the dictionary, with an example meaning of each. */
    private String partOfSpeech(Matcher x) {
        String word = x.group(1) != null ? x.group(1) : x.group(4) != null ? x.group(4) : x.group(5);
        java.util.LinkedHashMap<String, String> kinds = new java.util.LinkedHashMap<String, String>();
        for (ai.ibiko.ibiko1.words.MeaningIndex.Sense s : meanings.senses(word)) {
            String pos = s.pos.toLowerCase(java.util.Locale.ROOT).trim();
            if (!pos.isEmpty() && !kinds.containsKey(pos)) {
                kinds.put(pos, s.definition);
            }
        }
        if (kinds.isEmpty()) {
            return "I don't have “" + word + "” in my dictionary, so I can't say what kind of word it is.";
        }
        List<String> said = new ArrayList<String>();
        for (java.util.Map.Entry<String, String> e : kinds.entrySet()) {
            said.add("a " + e.getKey() + " (" + e.getValue().split(";")[0].trim() + ")");
        }
        String asked1 = x.group(2) == null ? null : x.group(2).toLowerCase(java.util.Locale.ROOT);
        String asked2 = x.group(3) == null ? null : x.group(3).toLowerCase(java.util.Locale.ROOT);
        String lead;
        if (asked1 != null && asked2 != null && kinds.containsKey(asked1) && kinds.containsKey(asked2)) {
            lead = "Both: “" + word + "” can be a " + asked1 + " or a " + asked2 + ", depending on how it's used.";
        } else if (asked1 != null && asked2 != null) {
            String yes = kinds.containsKey(asked1) ? asked1 : kinds.containsKey(asked2) ? asked2 : null;
            lead = yes == null ? "Neither: “" + word + "” is " + String.join(" or ", firstWords(kinds)) + "."
                    : "A " + yes + ": “" + word + "” isn't used as a " + (yes.equals(asked1) ? asked2 : asked1) + ".";
        } else if (asked1 != null) {
            lead = kinds.containsKey(asked1) ? "Yes, “" + word + "” is a " + asked1 + (kinds.size() > 1 ? ", and it can also be other kinds of word." : ".")
                    : "No, “" + word + "” isn't a " + asked1 + ".";
        } else {
            lead = "“" + word + "” is " + String.join(" or ", firstWords(kinds)) + ".";
        }
        return lead + "\n" + "It can be " + String.join("; ", said) + ".";
    }

    private static List<String> firstWords(java.util.Map<String, String> kinds) {
        List<String> out = new ArrayList<String>();
        for (String k : kinds.keySet()) {
            out.add("a " + k);
        }
        return out;
    }

    /** The reply to a question about words, meanings or grammar; null when it isn't one. */
    public String reply(String message) {
        Matcher num = message == null ? null : NUMBERS.matcher(message.trim());
        if (num != null && num.matches()) {
            // no dictionary needed: each number read for what it is (a year, a phone number, a price, a PIN…)
            String read = ai.ibiko.ibiko1.formulas.NumberSense.explain(num.group(1));
            return read.isEmpty() ? "I don't see a number in that." : "What each number is, from its shape and the words around it:\n" + read;
        }
        if (message == null || meanings == null) {
            return null;
        }
        String m = message.trim();
        Matcher x;
        if (tags != null && (x = TAGS.matcher(m)).matches()) {
            return code(x.group(1));
        }
        if ((x = IN_CONTEXT.matcher(m)).matches()) {
            return inContext(x.group(1), x.group(2));
        }
        if ((x = MEANS.matcher(m)).matches()) {
            return means(first(x));
        }
        if ((x = WORD_FOR.matcher(m)).matches()) {
            return wordFor(first(x));
        }
        if ((x = SYNONYMS.matcher(m)).matches()) {
            List<String> syn = meanings.synonyms(first(x), null);
            return syn.isEmpty() ? "I don't have synonyms for “" + first(x) + "” in my dictionary."
                    : "Words with the same meaning as “" + first(x) + "”: " + String.join(", ", syn) + ".";
        }
        if ((x = PART_OF_SPEECH.matcher(m)).matches()) {
            return partOfSpeech(x);
        }
        if ((x = OPPOSITES.matcher(m)).matches()) {
            List<String> opp = meanings.opposites(first(x));
            if (opp.isEmpty()) {
                // none listed for the word itself: worked out from what it means ("ancient" is very old: new, modern)
                ai.ibiko.ibiko1.words.Meanings.Reasoned r = meanings.oppositesByMeaning(first(x));
                if (r != null) {
                    return "The opposite of “" + first(x) + "”: " + String.join(", ", r.opposites) + ". (“" + first(x) + (r.via.startsWith("like") ? "” is " : "” means ")
                            + r.via + ", and those are the opposites of “" + r.through + "”.)";
                }
            }
            return opp.isEmpty() ? "My dictionary doesn't give an opposite for “" + first(x) + "”."
                    : "The opposite of “" + first(x) + "”: " + String.join(", ", opp) + ".";
        }
        if ((x = SAME.matcher(m)).matches()) {
            List<String> two = all(x);
            return two.size() < 2 ? null : compare(two.get(0), two.get(1));
        }
        if ((x = SIMILAR.matcher(m)).matches()) {
            List<String> two = all(x);
            return two.size() < 2 ? null : similar(two.get(0), two.get(1));
        }
        if ((x = CLOSE.matcher(m)).matches()) {
            return close(first(x));
        }
        if ((x = ODD.matcher(m)).matches()) {
            return odd(first(x));
        }
        if ((x = OTHER_WORDS.matcher(m)).matches()) {
            Meanings.Reading r = meanings.read(first(x));
            return r.inOtherWords.equalsIgnoreCase(first(x)) ? "I couldn't find plainer words for that: it's already about as plain as it gets."
                    : "In other words: " + r.inOtherWords;
        }
        if ((x = GRAMMAR.matcher(m)).matches()) {
            return GrammarCheck.explain(GrammarCheck.judge(first(x)));
        }
        if ((x = FORM.matcher(m)).matches()) {
            return form(x.group(1).toLowerCase(Locale.ROOT), x.group(2));
        }
        if ((x = SOUND.matcher(m)).matches()) {
            return sound(x.group(1).toLowerCase(Locale.ROOT), x.group(2));
        }
        if ((x = MORPH.matcher(m)).matches()) {
            // "how is garri made?" asks how the thing is made, not how the word is: only a question that says "word"
            // (or a word it can break down) is about the word
            boolean aboutWord = !m.matches("(?is)^how is\\b.*") || m.matches("(?is).*\\bword\\b.*");
            String w = first(x);
            if (!aboutWord && morphology.readings(w).isEmpty() && morphology.formation(w) == null) {
                return null;
            }
            return morphology(w);
        }
        if ((x = EXPLAIN.matcher(m)).matches() && first(x).split("\\s+").length >= 3) {
            return explain(first(x));
        }
        return null;
    }

    // ------------------------------------------------------------------ answers

    /**
     * "What is root?" while talking about Linux: the word's meaning in the subject at hand, said first, with its other
     * meanings after. Null unless the word means different things in different subjects and the question (or the
     * conversation) says which subject: otherwise the encyclopedia and the dictionary answer as before.
     */
    public String inSubject(String message) {
        if (tags == null || message == null) {
            return null;
        }
        Matcher x = WHAT_IS.matcher(message.trim());
        if (!x.matches()) {
            return null;
        }
        String word = first(x);
        String said = x.group(x.groupCount());
        if (word == null || !tags.ambiguous(word)) {
            return null;
        }
        String about = said == null ? null : ai.ibiko.ibiko1.words.SenseTags.subject(said);
        if (about == null) {
            about = topic;
        }
        if (about == null) {
            return null;
        }
        MeaningIndex.Sense s = tags.meant(word, said == null ? "" : said, about, null);
        if (s == null || !about.equals(tags.subjectOf(s))) {
            return null;
        }
        return subjectAnswer(word, s, about);
    }

    private String subjectAnswer(String word, MeaningIndex.Sense s, String about) {
        StringBuilder b = new StringBuilder(cap(ai.ibiko.ibiko1.words.SenseTags.in(about))).append(", “").append(word.toLowerCase(Locale.ROOT))
                .append("” means ").append(s.definition).append('.');
        List<String> others = new ArrayList<String>();
        java.util.Set<String> said = new java.util.HashSet<String>();
        said.add(about);
        for (MeaningIndex.Sense o : tags.senses(word)) {
            String sub = tags.subjectOf(o);
            if (sub != null && said.add(sub) && others.size() < 3) {
                others.add(ai.ibiko.ibiko1.words.SenseTags.in(sub) + ", " + o.definition);
            }
        }
        if (!others.isEmpty()) {
            b.append("\n\nIt means other things elsewhere: ").append(String.join("; ", others)).append('.');
        }
        return b.toString();
    }

    /** A sentence's context code, explained. */
    private String code(String sentence) {
        String code = tags.code(sentence, topic);
        StringBuilder b = new StringBuilder("Context code: ").append(code);
        List<String> notes = new ArrayList<String>();
        for (ai.ibiko.ibiko1.words.SenseTags.Tag t : tags.tag(sentence, topic)) {
            if (t.subject != null && t.sense != null && notes.size() < 4) {
                notes.add("“" + t.word + "” " + ai.ibiko.ibiko1.words.SenseTags.in(t.subject) + ": " + t.sense.definition);
            }
        }
        if (!notes.isEmpty()) {
            b.append("\n\nThe words with more than one meaning, read in context:\n• ").append(String.join("\n• ", notes));
        }
        b.append("\n\nKey: n noun, m name, p pronoun, v verb, b helping verb, a adjective, r adverb, i preposition, c conjunction, "
                + "d determiner, u number; @ marks the subject a word means here.");
        return b.toString();
    }

    private String means(String word) {
        if (tags != null && topic != null && tags.ambiguous(word)) {
            MeaningIndex.Sense s = tags.meant(word, "", topic, null);
            if (s != null && topic.equals(tags.subjectOf(s))) {
                return subjectAnswer(word, s, topic); // the meaning in what we're talking about, first
            }
        }
        List<MeaningIndex.Sense> senses = meanings.senses(word);
        if (senses.isEmpty()) {
            return null; // the older dictionary path (and learning a new word) takes it
        }
        MeaningIndex.Sense top = senses.get(0);
        String plain = meanings.plain(top, word);
        StringBuilder b = new StringBuilder();
        b.append(cap(word)).append(" means ").append(plain != null && !plain.equalsIgnoreCase(top.definition) ? "“" + plain + "”: " : "")
                .append(top.definition).append('.');
        int shown = 1;
        for (int i = 1; i < senses.size() && shown < 3; i++) {
            MeaningIndex.Sense s = senses.get(i);
            if (!s.pos.equals(top.pos) || i == 1) {
                b.append("\nAlso (").append(s.pos).append("): ").append(s.definition).append('.');
                shown++;
            }
        }
        if (tags != null) {
            // meanings in other subjects the dictionary hasn't got: "root" in computing and maths
            int more = 0;
            for (MeaningIndex.Sense s : tags.senses(word)) {
                String sub = tags.subjectOf(s);
                if (s.id < 0 && sub != null && more < 2) {
                    b.append("\nAlso, ").append(ai.ibiko.ibiko1.words.SenseTags.in(sub)).append(": ").append(s.definition).append('.');
                    more++;
                }
            }
        }
        return b.toString();
    }

    private String inContext(String word, String sentence) {
        MeaningIndex.Sense s = tags != null ? tags.meant(word, sentence, topic, null) : meanings.meant(word, sentence, null);
        if (s == null) {
            return "I don't have “" + word + "” in my dictionary.";
        }
        String plain = s.id < 0 ? null : meanings.plain(s, word);
        int count = tags != null ? tags.senses(word).size() : meanings.senses(word).size();
        return "Here, “" + word + "” means " + (plain != null && !plain.equalsIgnoreCase(s.definition) ? "“" + plain + "”: " : "")
                + s.definition + "." + (count > 1 ? " (It has " + count + " meanings; the words around it pick this one.)" : "");
    }

    private String wordFor(String description) {
        List<Meanings.Found> found = meanings.wordsFor(description, 5);
        if (found.isEmpty()) {
            return "I couldn't find a word for that. Try describing it another way.";
        }
        Meanings.Found best = found.get(0);
        StringBuilder b = new StringBuilder("The word is “" + best.word + "”" + (best.definition == null ? "." : ": " + best.definition + "."));
        if (found.size() > 1) {
            List<String> others = new ArrayList<String>();
            for (int i = 1; i < found.size(); i++) {
                others.add(found.get(i).word);
            }
            b.append("\nClose ones: ").append(String.join(", ", others)).append('.');
        }
        return b.toString();
    }

    private String compare(String a, String b) {
        Meanings.Comparison c = meanings.compare(a, b);
        switch (c.verdict) {
            case SAME:
                return "Yes, they say the same thing in different words" + (c.matched.isEmpty() ? "." : ": " + String.join(", ", c.matched) + ".");
            case OPPOSITE:
                return "No, they say opposite things: " + String.join(", ", c.opposed) + ".";
            case RELATED:
                return "They're related but not the same" + (c.matched.isEmpty() ? "." : ": they share " + String.join(", ", c.matched) + ".");
            default:
                return "No, they say different things.";
        }
    }

    // ------------------------------------------------------------------ the neural layer

    /** How alike two words, or two sentences, are in meaning: the cosine of their vectors, said in words. */
    String similar(String a, String b) {
        ai.ibiko.ibiko1.neural.Neural n = ai.ibiko.ibiko1.neural.Neural.get();
        if (n == null) {
            return compare(a, b);
        }
        boolean words = !a.trim().contains(" ") && !b.trim().contains(" ");
        String x = a.trim().toLowerCase(Locale.ROOT), y = b.trim().toLowerCase(Locale.ROOT);
        double s = words ? n.embeddings.similarity(x, y) : n.similarity(a, b);
        int pct = (int) Math.round(Math.max(0, s) * 100);
        String how = s >= 0.75 ? "very close in meaning" : s >= 0.55 ? "closely related" : s >= 0.35 ? "somewhat related"
                : s >= 0.2 ? "only loosely related" : "not really related";
        StringBuilder out = new StringBuilder("“" + a.trim() + "” and “" + b.trim() + "” are " + how + " (" + pct + "% alike).");
        if (words) {
            List<String> opp = meanings.opposites(x);
            if (opp.contains(y)) {
                out.append(" They're opposites, though: words used in the same places, saying opposite things.");
            } else {
                List<String> na = n.near(x, 5);
                if (!na.isEmpty()) {
                    out.append("\nClose to “").append(x).append("”: ").append(String.join(", ", na)).append('.');
                }
            }
        }
        return out.toString();
    }

    /** The words that sit closest to a word: those used in the same places in what Ibiko has read. */
    String close(String word) {
        ai.ibiko.ibiko1.neural.Neural n = ai.ibiko.ibiko1.neural.Neural.get();
        if (n == null) {
            return null;
        }
        String w = word.toLowerCase(Locale.ROOT);
        List<String> near = n.near(w, 10);
        if (near.isEmpty()) {
            return null;
        }
        return "Words close to “" + w + "”" + (n.embeddings.knows(w) ? "" : " (I built its meaning from its parts)")
                + ": " + String.join(", ", near) + ".";
    }

    /** The odd one out of a list of words: the one farthest from the others' centre. */
    String odd(String list) {
        ai.ibiko.ibiko1.neural.Neural n = ai.ibiko.ibiko1.neural.Neural.get();
        if (n == null) {
            return null;
        }
        List<String> ws = new ArrayList<String>();
        for (String p : list.split("\\s*(?:,|;|/|\\bor\\b|\\band\\b)\\s*|\\s+")) {
            String w = p.trim().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}'-]", "");
            if (!w.isEmpty() && !ws.contains(w)) {
                ws.add(w);
            }
        }
        if (ws.size() < 3) {
            return "Give me at least three words, and I'll tell you which one doesn't belong.";
        }
        String odd = null;
        double worst = 2;
        for (String w : ws) {
            double sum = 0;
            for (String o : ws) {
                if (!o.equals(w)) {
                    sum += n.embeddings.similarity(w, o);
                }
            }
            double mean = sum / (ws.size() - 1);
            if (mean < worst) {
                worst = mean;
                odd = w;
            }
        }
        List<String> rest = new ArrayList<String>(ws);
        rest.remove(odd);
        return "The odd one out is “" + odd + "”: " + String.join(", ", rest) + " go together, and “" + odd + "” is used in other places.";
    }

    private String explain(String sentence) {
        Meanings.Reading r = meanings.read(sentence);
        if (r.words.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("Here's how I read it, word by word:");
        for (Meanings.Word w : r.words) {
            b.append("\n• ").append(w.written).append(" (").append(w.tag).append(")");
            if (w.plain != null && !w.plain.equalsIgnoreCase(w.base) && w.plain.split("\\s+").length <= 3) {
                b.append(": “").append(w.plain).append('”');
            }
            if (w.sense != null) {
                b.append(" — ").append(w.sense.definition);
            }
        }
        if (r.subject != null && r.verb != null) {
            b.append("\n\nWho does what: ").append(r.subject).append(" → ").append(r.verb)
                    .append(r.object == null ? "" : " → " + r.object).append('.');
        }
        if (!r.inOtherWords.equalsIgnoreCase(sentence)) {
            b.append("\nIn other words: ").append(r.inOtherWords);
        }
        return b.toString();
    }

    private String morphology(String word) {
        List<Morphology.Build> builds = morphology.readings(word);
        if (builds.isEmpty()) {
            String how = morphology.formation(word);
            return how == null ? "I don't know “" + word + "” well enough to break it down." : cap(how);
        }
        Morphology.Build b = builds.get(0);
        if (b.root != null) {
            String how = morphology.formation(word);
            return cap(how == null ? word + " is a single morpheme: a root with nothing to take off." : how);
        }
        StringBuilder out = new StringBuilder();
        List<String> parts = b.morphemes();
        out.append(cap(word)).append(" has ").append(parts.size()).append(" morphemes: ").append(String.join(" + ", parts)).append('.');
        if (builds.size() > 1) {
            out.append(" It can be built two ways, so it has two meanings:");
            for (int i = 0; i < builds.size(); i++) {
                Morphology.Build r = builds.get(i);
                out.append("\n\n").append(i + 1).append(". “").append(cap(r.meaning())).append("”: ").append(r.brackets())
                        .append("\n```\n").append(r.tree().drawn()).append("\n```");
            }
            return out.toString();
        }
        out.append(" Built one affix at a time, each attaching to the category it takes:\n```\n").append(b.tree().drawn()).append("\n```\n")
                .append(b.brackets()).append("\nIt means: ").append(b.meaning()).append('.');
        return out.toString();
    }

    private String form(String which, String word) {
        String w = word.toLowerCase(Locale.ROOT);
        switch (which) {
            case "plural":
                String p = Morph.plural(w);
                return "The plural of “" + w + "” is “" + p + "”" + (p.endsWith("s") ? ", its -s said " + Morph.pluralSound(w) : "") + ".";
            case "past tense":
                String past = Morph.past(w);
                return "The past tense of “" + w + "” is “" + past + "”" + (past.endsWith("ed") ? ", its -ed said " + Morph.edSound(w) : "") + ".";
            case "past participle":
            case "participle":
                return "The past participle of “" + w + "” is “" + Morph.participle(w) + "”.";
            case "-ing form":
            case "ing form":
            case "present participle":
                return "The -ing form of “" + w + "” is “" + Morph.ing(w) + "”.";
            case "comparative":
                return "The comparative of “" + w + "” is “" + Morph.comparative(w) + "”.";
            case "superlative":
                return "The superlative of “" + w + "” is “" + Morph.superlative(w) + "”.";
            default:
                return "With he, she or it: “" + Morph.third(w) + "”.";
        }
    }

    private String sound(String which, String word) {
        String w = word.toLowerCase(Locale.ROOT);
        if (which.contains("ed") || which.equals("past")) {
            String verb = w.endsWith("ed") && w.length() > 4 ? w.replaceAll("ed$", "") : w;
            if (!Morph.past(verb).equals(w) && Morph.past(verb + "e").equals(w)) {
                verb = verb + "e";
            }
            return "The -ed of \u201c" + Morph.past(verb) + "\u201d sounds " + Morph.edSound(verb)
                    + ": the past tense is one morpheme with three sounds, /\u026ad/ after t or d, /t/ after a voiceless sound, /d/ after a"
                    + " voiced one.";
        }
        String base = w.endsWith("s") && Morph.plural(w.substring(0, w.length() - 1)).equals(w) ? w.substring(0, w.length() - 1)
                : w.endsWith("es") ? w.substring(0, w.length() - 2) : w;
        return "The -s of “" + Morph.plural(base) + "” sounds " + Morph.pluralSound(base)
                + ": the plural is one morpheme with three sounds, /ɪz/ after s, z, sh, ch or j sounds, /s/ after a voiceless"
                + " sound (cats), /z/ after a voiced one (dogs).";
    }

    private static String cap(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
