package ai.ibiko.ibiko1.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Ibiko Language Intelligence Engine, in one place (docs/IBIKO_LINGUISTIC_INTELLIGENCE.md). A message is read at
 * three levels: recognition (each word and what it is), interpretation (the sentence's form, its figures of speech),
 * comprehension (what it does and means in the conversation: the act, the intent, the tone, what is implied, what is
 * still ambiguous). The same reading goes to every brain: Ibiko v1 uses it to answer, and the API and on-device models
 * get it as a note with the request (Ibiko1.premeditate), so they all read "it's raining cats and dogs" as heavy rain
 * and "can you open the door?" as a request. It also answers questions about language ("what tense is …", "is that
 * sarcastic?", "what figure of speech is …", "how are wheel and car related?"). Pure Java.
 */
public final class Language {
    private final Figures figures;
    private final Relations relations;
    private final Relations.Source source;
    private final Predicate<String> verbBase;

    public Language(Figures figures, Relations relations, Relations.Source source, Predicate<String> verbBase) {
        this.figures = figures == null ? new Figures(null, null, null) : figures;
        this.relations = relations == null ? new Relations(source, null) : relations;
        this.source = source;
        this.verbBase = verbBase == null ? SentenceForm.COMMON : verbBase;
    }

    public Figures figures() {
        return figures;
    }

    public Relations relations() {
        return relations;
    }

    /** A message read at the three levels. */
    public static final class Reading {
        /** Level 1, recognition: each word with its part of speech. */
        public final String words;
        /** Level 2, interpretation: the sentence's form and its figures of speech. */
        public final SentenceForm.Form form;
        public final List<Figures.Figure> figures;
        /** Level 3, comprehension: the act, intent, tone, implications and open ambiguities. */
        public final Pragmatics.Reading meaning;

        Reading(String words, SentenceForm.Form form, List<Figures.Figure> figures, Pragmatics.Reading meaning) {
            this.words = words;
            this.form = form;
            this.figures = figures;
            this.meaning = meaning;
        }

        /** Whether anything is worth telling a brain: it isn't plain and literal. */
        public boolean notable() {
            return !figures.isEmpty() || meaning.intended != null || !meaning.implied.isEmpty() || !meaning.ambiguities.isEmpty()
                    || meaning.tones.contains("sarcastic") || meaning.tones.contains("angry") || meaning.tones.contains("urgent");
        }

        /** The thinking steps: what reading the message found, one line each. */
        public List<String> steps() {
            List<String> out = new ArrayList<String>();
            for (Figures.Figure f : figures) {
                out.add("That's " + Figures.article(f.name()) + " " + f.name() + ": “" + f.words + "”"
                        + (f.meaning.isEmpty() ? "" : " means " + Figures.lowerFirst(f.meaning.replaceAll("[.]$", ""))) + ", not word for word");
            }
            if (meaning.intended != null) {
                out.add("Read as " + Figures.article(meaning.act) + " " + meaning.act + ": " + meaning.intended);
            }
            for (String i : meaning.implied) {
                out.add("Implied: " + i);
            }
            if (!meaning.ambiguities.isEmpty()) {
                out.add("It can mean two things: using the conversation to choose");
            }
            return out;
        }

        /** The note a brain gets with the request; empty when the message is plain and literal. */
        public String brief() {
            if (!notable()) {
                return "";
            }
            StringBuilder b = new StringBuilder("\nIbiko's reading of the language (take the meaning, not the literal words):");
            for (Figures.Figure f : figures) {
                b.append("\n- ").append(f.said());
            }
            if (meaning.intended != null) {
                b.append("\n- It is ").append(Figures.article(meaning.act)).append(' ').append(meaning.act).append(": ").append(meaning.intended).append('.');
            }
            for (String i : meaning.implied) {
                b.append("\n- Implied: ").append(i).append('.');
            }
            if (!meaning.tones.isEmpty()) {
                b.append("\n- Tone: ").append(String.join(", ", meaning.tones)).append('.');
            }
            for (String a : meaning.ambiguities) {
                b.append("\n- ").append(a);
            }
            return b.toString();
        }
    }

    /** A message read at the three levels, with the message before it. */
    public Reading read(String sentence, String previous) {
        String s = sentence == null ? "" : sentence;
        StringBuilder words = new StringBuilder();
        for (ai.ibiko.ibiko1.formulas.PartsOfSpeech.Word w : ai.ibiko.ibiko1.formulas.PartsOfSpeech.tag(s.length() > 400 ? s.substring(0, 400) : s, null)) {
            if (w.tag != ai.ibiko.ibiko1.formulas.PartsOfSpeech.Tag.PUNCTUATION) {
                words.append(words.length() == 0 ? "" : " ").append(w);
            }
        }
        SentenceForm.Form form = SentenceForm.read(s, verbBase);
        List<Figures.Figure> found = s.length() > 600 ? new ArrayList<Figures.Figure>() : figures.find(s, previous);
        return new Reading(words.toString(), form, found, Pragmatics.read(s, previous, figures));
    }

    /** The note every brain gets with a request: what the language means when it isn't literal; "" when it is. */
    public String brief(String request, String previous) {
        if (request == null || request.length() > 600 || request.contains("```")) {
            return "";
        }
        try {
            return read(request, previous).brief();
        } catch (RuntimeException e) {
            return "";
        }
    }

    // ---------------------------------------------------------------------------------------------- questions

    private static final String Q = "[\"“'‘]?(.+?)[\"”'’]?\\s*[?.!]*\\s*$";
    private static final Pattern FIGURE = Pattern.compile("(?i)^\\s*(?:what|which|identify|name|find)(?: is| are)?(?: the)? figures? of speech"
            + "(?: is| are| used| in| does)*(?: this| that| the sentence| in)?\\s*:?\\s*" + Q);
    private static final Pattern IS_FIGURE = Pattern.compile("(?i)^\\s*is\\s+[\"“'‘](.+?)[\"”'’]\\s+(?:an?\\s+)?(idiom|proverb|metaphor|simile|personification|"
            + "hyperbole|irony|ironic|sarcasm|sarcastic|oxymoron|euphemism|alliteration|onomatopoeia|paradox|pun)\\s*\\??\\s*$");
    private static final Pattern SARCASTIC = Pattern.compile("(?i)^\\s*(?:is|was) (?:this|that|it|he|she|they) (?:being )?(?:sarcastic|sarcasm|serious|ironic)\\b.*");
    private static final Pattern TENSE = Pattern.compile("(?i)^\\s*(?:what|which) tense (?:is|is used in|does)(?: the sentence| this)?\\s*:?\\s*" + Q);
    private static final Pattern TENSE_OF = Pattern.compile("(?i)^\\s*(?:what is|what's|identify|name) the tense (?:of|in)(?: the sentence)?\\s*:?\\s*" + Q);
    private static final Pattern VOICE = Pattern.compile("(?i)^\\s*is\\s+[\"“'‘]?(.+?)[\"”'’]?\\s+(?:in )?(?:the )?(?:active|passive)(?: voice)?(?: or (?:active|passive)(?: voice)?)?\\s*\\??\\s*$");
    private static final Pattern KIND = Pattern.compile("(?i)^\\s*(?:what|which) (?:kind|type|sort) of sentence is(?: this)?\\s*:?\\s*" + Q);
    private static final Pattern ANALYSE = Pattern.compile("(?i)^\\s*(?:analy[sz]e|break down|explain the grammar of|read)(?: the)?(?: sentence| grammar of)?\\s*:?\\s*[\"“'‘](.+?)[\"”'’]\\s*[?.!]*\\s*$");
    private static final Pattern WORD_IN = Pattern.compile("(?i)^\\s*(?:what|which) (?:part of speech|word class|grammatical category) is [\"“'‘]?([\\p{L}'-]+)[\"”'’]?"
            + "(?: in\\s*:?\\s*[\"“'‘]?(.+?)[\"”'’]?)?\\s*[?.!]*\\s*$");
    private static final Pattern RECORD = Pattern.compile("(?i)^\\s*(?:classify|identify|give me the (?:linguistic )?record (?:of|for)|word record (?:of|for)|"
            + "tell me everything about the word|analy[sz]e the word) [\"“'‘]?([\\p{L}'-]+)[\"”'’]?(?: in\\s*:?\\s*[\"“'‘]?(.+?)[\"”'’]?)?\\s*[?.!]*\\s*$");
    private static final Pattern BETWEEN = Pattern.compile("(?i)^\\s*(?:what(?:'s| is) the relationship between|how (?:are|is)) [\"“'‘]?([\\p{L}'-]+)[\"”'’]? "
            + "(?:and|to) [\"“'‘]?([\\p{L}'-]+)[\"”'’]?(?: related)?\\s*\\??\\s*$");
    private static final Pattern RELATION_OF = Pattern.compile("(?i)^\\s*(?:what are |list |give me |name )?(?:the |some )?(homophones|homographs|hyponyms|hypernyms|"
            + "meronyms|holonyms|collocations|parts|kinds|types) (?:of|for) [\"“'‘]?([\\p{L}'-]+)[\"”'’]?\\s*\\??\\s*$");
    private static final Pattern IS_RELATION = Pattern.compile("(?i)^\\s*is [\"“'‘]?([\\p{L}'-]+)[\"”'’]? an? (contronym|homograph|homonym)\\s*\\??\\s*$");
    private static final Pattern MEANT = Pattern.compile("(?i)^\\s*(?:what (?:do|does|did) (?:they|he|she|you|i|someone|people) (?:really )?mean (?:by|when (?:they|he|she|you) say)|"
            + "what is meant by|is (?:this|it) a request|what is the intent of|what does it really mean when someone says)\\s*:?\\s*" + Q);
    private static final Pattern AMBIGUOUS = Pattern.compile("(?i)^\\s*(?:is|why is) [\"“'‘](.+?)[\"”'’] ambiguous\\s*\\??\\s*$");
    private static final Pattern LEVELS = Pattern.compile("(?i)^\\s*(?:read|understand|interpret) (?:this|the message)(?: for me)?\\s*:\\s*" + Q);

    private static final Pattern[] ALL = {FIGURE, IS_FIGURE, SARCASTIC, TENSE, TENSE_OF, VOICE, KIND, ANALYSE, WORD_IN, RECORD, BETWEEN,
        RELATION_OF, IS_RELATION, MEANT, AMBIGUOUS, LEVELS};

    /** Whether a message asks about language this engine answers. */
    public static boolean asks(String message) {
        if (message == null || message.length() > 500) {
            return false;
        }
        for (Pattern p : ALL) {
            if (p.matcher(message).matches()) {
                return true;
            }
        }
        return false;
    }

    /** The answer to a question about language; null when it isn't one, or nothing can be said. */
    public String reply(String message, String previous) {
        if (!asks(message)) {
            return null;
        }
        Matcher m;
        if ((m = IS_FIGURE.matcher(message)).matches()) {
            String asked = m.group(2).toLowerCase(Locale.ROOT).replace("ironic", "irony").replace("sarcastic", "sarcasm");
            List<Figures.Figure> found = figures.find(m.group(1), previous);
            for (Figures.Figure f : found) {
                if (f.name().equals(asked)) {
                    return "Yes. " + f.said();
                }
            }
            return found.isEmpty() ? "No: I read “" + m.group(1) + "” literally; I don't find " + Figures.article(asked) + " " + asked + " in it."
                    : "Not " + Figures.article(asked) + " " + asked + ". " + found.get(0).said();
        }
        if ((m = FIGURE.matcher(message)).matches()) {
            List<Figures.Figure> found = figures.find(m.group(1), previous);
            if (found.isEmpty()) {
                return "I don't find a figure of speech in “" + m.group(1) + "”: it reads literally.";
            }
            StringBuilder b = new StringBuilder();
            for (Figures.Figure f : found) {
                b.append(b.length() == 0 ? "" : "\n").append(found.size() > 1 ? "• " : "").append(f.said());
            }
            return b.toString();
        }
        if (SARCASTIC.matcher(message).matches()) {
            if (previous == null || previous.trim().isEmpty()) {
                return null;
            }
            Pragmatics.Reading r = Pragmatics.read(previous, null, figures);
            if (r.tones.contains("sarcastic")) {
                return "Yes, it reads as sarcastic: " + r.intended + ".";
            }
            List<Figures.Figure> f = figures.find(previous, null);
            for (Figures.Figure x : f) {
                if (x.kind == Figures.Kind.IRONY) {
                    return "Not sarcasm, but irony: " + x.why + ".";
                }
            }
            return "I don't read it as sarcastic: nothing bad is being praised, and it has none of sarcasm's set phrases. It reads as meant.";
        }
        if ((m = TENSE.matcher(message)).matches() || (m = TENSE_OF.matcher(message)).matches()) {
            SentenceForm.Form f = SentenceForm.read(m.group(1), verbBase);
            if (f.tense == null) {
                return "“" + m.group(1) + "” has no verb of its own, so it has no tense.";
            }
            return "“" + m.group(1) + "” is in the " + f.tense.name + (f.tense.passive ? "" : " (active voice)") + explainTense(f.tense) + ".";
        }
        if ((m = VOICE.matcher(message)).matches()) {
            SentenceForm.Form f = SentenceForm.read(m.group(1), verbBase);
            if (f.tense == null) {
                return null;
            }
            return f.tense.passive ? "Passive: the subject receives the action (a form of “be” + the past participle “" + f.tense.verb + "”)."
                    : "Active: the subject does the action.";
        }
        if ((m = KIND.matcher(message)).matches() || (m = ANALYSE.matcher(message)).matches()) {
            return analysis(m.group(1), previous);
        }
        if ((m = WORD_IN.matcher(message)).matches() || (m = RECORD.matcher(message)).matches()) {
            WordRecord r = WordRecord.of(m.group(1), m.group(2), source);
            return r.said();
        }
        if ((m = BETWEEN.matcher(message)).matches()) {
            String b = relations.between(m.group(1), m.group(2));
            return b != null ? b : "I don't know a relation between “" + m.group(1) + "” and “" + m.group(2) + "”.";
        }
        if ((m = RELATION_OF.matcher(message)).matches()) {
            String kind = m.group(1).toLowerCase(Locale.ROOT), w = m.group(2).toLowerCase(Locale.ROOT);
            String key = kind.equals("parts") ? "meronyms" : kind.equals("kinds") || kind.equals("types") ? "hyponyms"
                    : kind.equals("homographs") ? "homograph" : kind;
            List<String> l = relations.of(w).get(key);
            if (l == null || l.isEmpty()) {
                return kind.equals("parts") || kind.equals("kinds") || kind.equals("types") ? null : "I don't know any " + kind + " of “" + w + "”.";
            }
            return Character.toUpperCase(kind.charAt(0)) + kind.substring(1) + " of “" + w + "”: " + String.join(", ", l) + ".";
        }
        if ((m = IS_RELATION.matcher(message)).matches()) {
            String w = m.group(1).toLowerCase(Locale.ROOT), kind = m.group(2).toLowerCase(Locale.ROOT);
            Map<String, List<String>> r = relations.of(w);
            List<String> l = r.get(kind.equals("homonym") ? "homonym" : kind);
            return l == null ? "Not that I know: I have no record of “" + w + "” as " + Figures.article(kind) + " " + kind + "."
                    : "Yes: “" + w + "” is " + Figures.article(kind) + " " + kind + " (" + String.join("; ", l) + ").";
        }
        if ((m = MEANT.matcher(message)).matches()) {
            Pragmatics.Reading r = Pragmatics.read(m.group(1), previous, figures);
            List<Figures.Figure> f = figures.find(m.group(1), previous);
            StringBuilder b = new StringBuilder();
            for (Figures.Figure x : f) {
                b.append(x.said()).append(' ');
            }
            return (b + r.said()).trim();
        }
        if ((m = AMBIGUOUS.matcher(message)).matches()) {
            Pragmatics.Reading r = Pragmatics.read(m.group(1), previous, figures);
            return r.ambiguities.isEmpty() ? "I find only one reading of “" + m.group(1) + "”." : String.join("\n", r.ambiguities);
        }
        if ((m = LEVELS.matcher(message)).matches()) {
            return analysis(m.group(1), previous);
        }
        return null;
    }

    /** Everything about a sentence, level by level. */
    public String analysis(String sentence, String previous) {
        Reading r = read(sentence, previous);
        StringBuilder b = new StringBuilder("“" + sentence.trim() + "”\n");
        b.append("• Words: ").append(r.words).append('\n');
        b.append("• Form: ").append(r.form.said()).append('\n');
        if (r.form.clauses.size() > 1) {
            for (SentenceForm.Clause c : r.form.clauses) {
                b.append("   – ").append(c).append('\n');
            }
        }
        for (Figures.Figure f : r.figures) {
            b.append("• Figure of speech: ").append(f.said()).append('\n');
        }
        b.append("• In context: ").append(r.meaning.said());
        return b.toString().trim();
    }

    private static String explainTense(SentenceForm.Tense t) {
        switch (t.time + " " + t.aspect) {
            case "present simple": return ": a habit, a fact, or now";
            case "present continuous": return ": happening now, still going on";
            case "present perfect": return ": done at some time before now, mattering now";
            case "present perfect continuous": return ": begun before now and still going on";
            case "past simple": return ": done and finished in the past";
            case "past continuous": return ": going on at a moment in the past";
            case "past perfect": return ": done before another past moment";
            case "past perfect continuous": return ": going on up to a moment in the past";
            case "future simple": return ": will happen";
            case "future continuous": return ": will be going on at a moment in the future";
            case "future perfect": return ": will be done by a moment in the future";
            case "future perfect continuous": return ": will have been going on up to a moment in the future";
            default: return "";
        }
    }
}
