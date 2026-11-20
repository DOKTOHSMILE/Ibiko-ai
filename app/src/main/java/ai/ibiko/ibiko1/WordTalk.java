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

    public WordTalk(Meanings meanings) {
        this.meanings = meanings;
        this.morphology = new Morphology(meanings);
    }

    private static final String Q = "[\"“”'‘’]?";
    private static final String W = Q + "([\\p{L}][\\p{L}'-]*)" + Q;
    private static final String S = Q + "(.+?)" + Q;
    private static final String END = "\\s*[?.!]*$";

    private static final Pattern IN_CONTEXT = Pattern.compile("(?is)^(?:what does|what is the meaning of|what's the meaning of|meaning of)\\s+" + W
            + "\\s+(?:mean\\s+)?(?:in|here in|in the sentence|in this sentence)\\s*[:\\-]?\\s*" + S + END);
    private static final Pattern MEANS = Pattern.compile("(?is)^(?:what does\\s+" + W + "\\s+mean|what is the meaning of\\s+" + W
            + "|what's the meaning of\\s+" + W + "|meaning of\\s+" + W + "|define\\s+" + W + "|definition of\\s+" + W + ")" + END);
    private static final Pattern WORD_FOR = Pattern.compile("(?is)^(?:what(?:'s| is) (?:a|the) word (?:for|that means|meaning)|"
            + "(?:give me |tell me )?(?:a|the|one) word (?:for|that means|meaning)|what do (?:you|we|people) call|what(?:'s| is) it called when|"
            + "what(?:'s| is) (?:a|the) term for|is there a word for)\\s+" + S + END);
    private static final Pattern SYNONYMS = Pattern.compile("(?is)^(?:(?:give me |list |what are (?:the |some )?)?synonyms? (?:of|for)|"
            + "(?:what(?:'s| is) )?another word for|(?:what(?:'s| is) )?a synonym (?:of|for)|other words for|words like)\\s+" + W + END);
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
        for (Pattern p : new Pattern[]{IN_CONTEXT, MEANS, WORD_FOR, SYNONYMS, OPPOSITES, SAME, OTHER_WORDS, GRAMMAR, FORM, SOUND, MORPH, SIMILAR, CLOSE, ODD}) {
            if (p.matcher(m).matches()) {
                return true;
            }
        }
        Matcher x = EXPLAIN.matcher(m);
        return x.matches() && first(x).split("\\s+").length >= 3;
    }

    /** The reply to a question about words, meanings or grammar; null when it isn't one. */
    public String reply(String message) {
        if (message == null || meanings == null) {
            return null;
        }
        String m = message.trim();
        Matcher x;
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
        if ((x = OPPOSITES.matcher(m)).matches()) {
            List<String> opp = meanings.opposites(first(x));
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
            return morphology(first(x));
        }
        if ((x = EXPLAIN.matcher(m)).matches() && first(x).split("\\s+").length >= 3) {
            return explain(first(x));
        }
        return null;
    }

    // ------------------------------------------------------------------ answers

    private String means(String word) {
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
        return b.toString();
    }

    private String inContext(String word, String sentence) {
        MeaningIndex.Sense s = meanings.meant(word, sentence, null);
        if (s == null) {
            return "I don't have “" + word + "” in my dictionary.";
        }
        String plain = meanings.plain(s, word);
        int count = meanings.senses(word).size();
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
