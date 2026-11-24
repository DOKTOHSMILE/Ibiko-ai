package ai.ibiko.ibiko1.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contextual intelligence: what a sentence does and means in the conversation, not just what its words say. "Can you
 * open the door?" is, by its grammar, a question about ability; said to someone, it is a request. "That's just great!"
 * is glad or sarcastic depending on what happened before. "I saw her duck" means two things until the context picks one.
 * So the reading here gives the speech act (what the person is doing by saying it), what is meant when it differs from
 * what is said, the tone and the feeling, what is implied, and the ambiguities still open. Pure Java.
 *
 * <pre>
 * ACT        request · question · statement · suggestion · offer · promise · advice · greeting · thanks · apology ·
 *            complaint · permission · exclamation
 * INDIRECT   "can/could/would/will you (please) V …" with a doing verb → a request to V (with "swim", "speak French":
 *            a question about ability) · "would you mind V-ing" · "I was wondering if you could" · "do you know what
 *            time it is" → asks for the time · "why don't we", "how about", "let's" → a suggestion · "you might want
 *            to" → advice · "is anyone sitting here" → asks to sit
 * IMPLIED    "it's cold in here" → close a window or warm the room · "I'm hungry" → would like food · "I'm tired" →
 *            would like to rest · "it's late" → time to go
 * TONE       polite (please, would you mind, kindly) · urgent (asap, now!, immediately) · angry (fed up, ridiculous,
 *            !!!, shouting in capitals) · uncertain (maybe, I think, not sure) · sarcastic (Figures) · neutral
 * FEELING    joy, sadness, anger, fear, surprise, frustration, gratitude, worry: from the words that carry them
 * AMBIGUITY  a word after "her" that is both a noun and a verb, after a seeing verb ("I saw her duck") · a phrase that
 *            can attach to the verb or the noun ("I saw the man with the telescope": formulas/Syntax)
 * </pre>
 */
public final class Pragmatics {
    private Pragmatics() {
    }

    /** What a sentence does and means in context. */
    public static final class Reading {
        public final String act;
        /** What is meant, when it differs from what is said; null when it is meant as said. */
        public final String intended;
        public final List<String> tones, feelings, implied, ambiguities;

        Reading(String act, String intended, List<String> tones, List<String> feelings, List<String> implied, List<String> ambiguities) {
            this.act = act;
            this.intended = intended;
            this.tones = tones;
            this.feelings = feelings;
            this.implied = implied;
            this.ambiguities = ambiguities;
        }

        /** In plain words, as a step or an answer. */
        public String said() {
            StringBuilder b = new StringBuilder("It's ").append(Figures.article(act)).append(' ').append(act);
            if (intended != null) {
                b.append(": ").append(intended);
            }
            b.append('.');
            if (!tones.isEmpty()) {
                b.append(" Tone: ").append(String.join(", ", tones)).append('.');
            }
            if (!feelings.isEmpty()) {
                b.append(" Feeling: ").append(String.join(", ", feelings)).append('.');
            }
            for (String i : implied) {
                b.append(" Implied: ").append(i).append('.');
            }
            for (String a : ambiguities) {
                b.append(' ').append(a);
            }
            return b.toString();
        }
    }

    /** Verbs that ask someone to do something when put as "can you …": doing verbs, not abilities. */
    static final Set<String> DOING = set("open", "close", "shut", "pass", "help", "send", "give", "tell", "show", "explain", "turn", "bring",
            "get", "call", "check", "fix", "write", "make", "take", "move", "lend", "remind", "find", "read", "play", "stop", "wait", "hold",
            "put", "pick", "buy", "fetch", "print", "share", "forward", "book", "order", "clean", "wash", "cook", "repeat", "say", "speak up",
            "lower", "raise", "keep", "answer", "look", "come", "go", "sign", "review", "summarise", "summarize", "translate", "list",
            "describe", "draw", "edit", "update", "delete", "save", "drop", "leave", "join", "let", "teach", "plan", "calculate", "do");
    private static final Pattern CAN_YOU = Pattern.compile("(?i)^\\s*(?:hey,?\\s+)?(?:can|could|would|will|won't)\\s+you\\s+(?:please\\s+|kindly\\s+|just\\s+)?"
            + "(\\w+)(.*?)\\s*(?:,?\\s*please)?\\s*[?!.]*\\s*$");
    private static final Pattern MIND = Pattern.compile("(?i)^\\s*(?:would|do) you mind (\\w+ing)(.*?)\\s*[?.!]*\\s*$");
    private static final Pattern WONDERING = Pattern.compile("(?i)^\\s*i (?:was|am|'m) wondering (?:if|whether) you (?:could|would|might|can) (\\w+)(.*?)\\s*[?.!]*\\s*$");
    private static final Pattern KNOW_TIME = Pattern.compile("(?i)^\\s*(?:do|would) you (?:know|happen to know) (what time it is|where .+|how .+|when .+|"
            + "who .+|what .+|which .+)\\s*[?.!]*\\s*$");
    private static final Pattern SUGGEST = Pattern.compile("(?i)^\\s*(?:why don't (?:we|you)|how about|what about|let's|let us|shall we|maybe we "
            + "(?:could|should))\\b(.*?)\\s*[?.!]*\\s*$");
    private static final Pattern ADVICE = Pattern.compile("(?i)^\\s*(?:you (?:might|may) (?:want|wish) to|you should|you ought to|if i were you|"
            + "it might be (?:a good idea|better) to|i'd (?:suggest|recommend))\\b(.*?)\\s*[?.!]*\\s*$");
    private static final Pattern OFFER = Pattern.compile("(?i)^\\s*(?:shall i|should i|can i get you|would you like (?:me to|some|a|an)|let me|"
            + "i can (?:help|do|get|send|bring)|do you want me to)\\b.*");
    private static final Pattern PROMISE = Pattern.compile("(?i)^\\s*i(?:'ll| will| promise| swear)\\b.*");
    private static final Pattern PERMISSION = Pattern.compile("(?i)^\\s*(?:can|could|may) i\\b.*\\?\\s*$|^\\s*is (?:anyone|anybody|this seat) "
            + "(?:sitting here|taken|free)\\b.*");
    private static final Pattern GREETING = Pattern.compile("(?i)^\\s*(?:hi|hello|hey|good (?:morning|afternoon|evening)|how far|how are you|"
            + "what's up|wassup|howdy)\\b.*");
    private static final Pattern THANKS = Pattern.compile("(?i)^\\s*(?:thanks|thank you|cheers|much appreciated|i appreciate)\\b.*");
    private static final Pattern APOLOGY = Pattern.compile("(?i)^\\s*(?:sorry|i'm sorry|i am sorry|my apologies|apologies|forgive me|abeg no vex)\\b.*");
    private static final Pattern COMPLAINT = Pattern.compile("(?i)\\b(?:i'm fed up|i am fed up|this is ridiculous|this is unacceptable|"
            + "not again|keeps (?:crashing|failing|breaking|happening)|still (?:not|doesn't|isn't|hasn't)|why (?:does|is) (?:it|this) always|"
            + "i'm tired of|i am tired of|sick of)\\b");

    /** The reading of a sentence, with the message before it. */
    public static Reading read(String sentence, String previous) {
        return read(sentence, previous, null);
    }

    /**
     * @param figures the figures engine (for sarcasm), or null
     */
    public static Reading read(String sentence, String previous, Figures figures) {
        String s = sentence == null ? "" : sentence.trim();
        String low = s.toLowerCase(Locale.ROOT).replace('’', '\'');
        String act = null, intended = null;
        Matcher m;
        if ((m = MIND.matcher(s)).matches()) {
            act = "request";
            intended = "please " + m.group(1).toLowerCase(Locale.ROOT).replaceAll("ing$", "").replaceAll("(.)\\1$", "$1") + m.group(2) + " (a polite request)";
        } else if ((m = CAN_YOU.matcher(s)).matches()) {
            String verb = m.group(1).toLowerCase(Locale.ROOT), rest = m.group(2).trim();
            if (DOING.contains(verb) || low.contains("please") || !rest.isEmpty() && rest.matches("(?i)^(?:me|us|him|her|them|it|this|that|the|a|an|my|your|"
                    + "some|up|down|out|off|on|in|over|back)\\b.*")) {
                act = "request";
                intended = "please " + verb + (rest.isEmpty() ? "" : " " + rest.replaceAll("(?i),?\\s*please$", "")) + " (asked as a question, meant as"
                        + " a request)";
            } else {
                act = "question";
                intended = "asks whether you are able to " + verb + (rest.isEmpty() ? "" : " " + rest);
            }
        } else if ((m = WONDERING.matcher(s)).matches()) {
            act = "request";
            intended = "please " + m.group(1) + m.group(2) + " (a very polite request)";
        } else if ((m = KNOW_TIME.matcher(s)).matches()) {
            act = "question";
            intended = "asks " + m.group(1) + " (not whether you know it)";
        } else if (PERMISSION.matcher(s).matches()) {
            act = "permission";
            intended = low.startsWith("is ") ? "asks whether they may sit there" : "asks to be allowed";
        } else if ((m = SUGGEST.matcher(s)).matches()) {
            act = "suggestion";
            intended = "suggests" + (m.group(1).isEmpty() ? " it" : m.group(1).replaceFirst("^\\s*", " "));
        } else if ((m = ADVICE.matcher(s)).matches()) {
            act = "advice";
        } else if (OFFER.matcher(s).matches()) {
            act = "offer";
        } else if (GREETING.matcher(s).matches()) {
            act = "greeting";
        } else if (THANKS.matcher(s).matches()) {
            act = "thanks";
        } else if (APOLOGY.matcher(s).matches()) {
            act = "apology";
        } else if (COMPLAINT.matcher(low).find()) {
            act = "complaint";
        }
        SentenceForm.Form form = SentenceForm.read(s);
        if (act == null) {
            switch (form.function) {
                case INTERROGATIVE: act = "question"; break;
                case IMPERATIVE: act = "request"; break;
                case EXCLAMATORY: act = "exclamation"; break;
                default: act = PROMISE.matcher(s).matches() && !low.matches("(?s).*\\bi'll (?:be|have)\\b.*") ? "promise" : "statement";
            }
        }
        List<String> implied = implied(low);
        if (act.equals("statement") && !implied.isEmpty()) {
            act = "hint";
        }
        boolean sarcastic = false;
        if (figures != null) {
            for (Figures.Figure f : figures.find(s, previous)) {
                if (f.kind == Figures.Kind.SARCASM) {
                    sarcastic = true;
                    intended = "the opposite of what it says: " + f.why;
                }
            }
        }
        return new Reading(act, intended, tones(s, low, sarcastic), feelings(low, sarcastic), implied, ambiguities(s, low));
    }

    // -------------------------------------------------------------------------------------------------- implied

    private static final String[][] HINTS = {
        {"\\b(?:it's|it is|it's getting|so) (?:cold|freezing|chilly) (?:in here|here)\\b", "they'd like a window closed or the room warmed"},
        {"\\b(?:it's|it is|it's getting|so) (?:hot|warm|stuffy) (?:in here|here)\\b", "they'd like a window opened, a fan or the AC on"},
        {"\\bi'm (?:so |really |very )?(?:hungry|starving)\\b|\\bi am (?:so |really |very )?hungry\\b", "they'd like something to eat"},
        {"\\bi'm (?:so |really |very )?thirsty\\b|\\bi am (?:so |really |very )?thirsty\\b", "they'd like something to drink"},
        {"\\bi'm (?:so |really |very )?(?:tired|exhausted|sleepy)\\b|\\bi am (?:so |really |very )?(?:tired|exhausted|sleepy)\\b", "they'd like to rest"},
        {"\\b(?:it's|it is) (?:getting )?late\\b", "it may be time to go or to stop"},
        {"\\bthe (?:door|window) is (?:still )?open\\b", "they may want it closed"},
        {"\\bmy (?:phone|battery) is (?:almost |nearly )?(?:dead|flat|low)\\b", "they need to charge it, or may lose contact"},
        {"\\bi (?:don't|do not) have (?:any )?(?:money|cash|change)\\b", "they may be asking for help to pay"},
        {"\\bi(?:'ve| have) never (?:been|tried|seen) \\w+", "they may like to"},
        {"\\bthere's no (?:light|power|nepa)\\b|\\bnepa (?:has )?(?:take|taken|carry|carried) light\\b", "the power is out: the generator or a charge may be needed"},
    };

    static List<String> implied(String low) {
        List<String> out = new ArrayList<String>();
        for (String[] h : HINTS) {
            if (Pattern.compile(h[0]).matcher(low).find()) {
                out.add(h[1]);
            }
        }
        return out;
    }

    // --------------------------------------------------------------------------------------------- tone, feeling

    private static final Pattern POLITE = Pattern.compile("\\b(?:please|kindly|would you mind|if you don't mind|if it's not too much trouble|"
            + "i was wondering|could you possibly|abeg|thank you)\\b");
    private static final Pattern URGENT = Pattern.compile("\\b(?:asap|urgent|urgently|immediately|right now|right away|quickly|hurry|emergency|"
            + "sharp sharp)\\b|now!");
    private static final Pattern ANGRY = Pattern.compile("\\b(?:fed up|ridiculous|unacceptable|stupid|nonsense|rubbish|useless|hate|furious|"
            + "how dare|shut up|what the hell)\\b|!!+");
    private static final Pattern UNSURE = Pattern.compile("\\b(?:maybe|perhaps|i think|i guess|not sure|i'm not sure|i suppose|probably|might be|"
            + "kind of|sort of|i don't know)\\b");

    private static List<String> tones(String s, String low, boolean sarcastic) {
        List<String> out = new ArrayList<String>();
        if (sarcastic) {
            out.add("sarcastic");
        }
        if (POLITE.matcher(low).find()) {
            out.add("polite");
        }
        if (URGENT.matcher(low).find()) {
            out.add("urgent");
        }
        int caps = 0;
        for (String w : s.split("\\s+")) {
            caps += w.length() > 2 && w.equals(w.toUpperCase(Locale.ROOT)) && w.matches(".*\\p{Lu}.*") ? 1 : 0;
        }
        if (ANGRY.matcher(low).find() || caps >= 2) {
            out.add("angry");
        }
        if (UNSURE.matcher(low).find()) {
            out.add("uncertain");
        }
        return out;
    }

    private static final String[][] FEELINGS = {
        {"joy", "\\b(?:happy|glad|delighted|excited|thrilled|great news|yay|wonderful|amazing|love it|so good)\\b"},
        {"sadness", "\\b(?:sad|unhappy|depressed|heartbroken|miss (?:him|her|them|you)|lonely|crying|cried|grieving)\\b"},
        {"anger", "\\b(?:angry|furious|mad at|annoyed|fed up|hate|livid|pissed)\\b"},
        {"fear", "\\b(?:afraid|scared|frightened|terrified|nervous|anxious)\\b"},
        {"worry", "\\b(?:worried|worry|concerned|uneasy)\\b"},
        {"surprise", "\\b(?:wow|surprised|shocked|can't believe|cannot believe|no way|unbelievable)\\b"},
        {"frustration", "\\b(?:frustrated|frustrating|again\\?|not again|keeps (?:crashing|failing)|still not|why won't)\\b"},
        {"gratitude", "\\b(?:thanks|thank you|grateful|appreciate)\\b"},
    };

    private static List<String> feelings(String low, boolean sarcastic) {
        List<String> out = new ArrayList<String>();
        for (String[] f : FEELINGS) {
            if (sarcastic && (f[0].equals("joy") || f[0].equals("gratitude"))) {
                continue; // praise said sarcastically isn't joy
            }
            if (Pattern.compile(f[1]).matcher(low).find()) {
                out.add(f[0]);
            }
        }
        if (sarcastic && !out.contains("frustration")) {
            out.add("frustration");
        }
        return out;
    }

    // ---------------------------------------------------------------------------------------------------- ambiguity

    /** Words that are as often a noun as a verb: after "her" they can be either. */
    static final Set<String> NOUN_AND_VERB = set("duck", "fly", "wave", "ring", "park", "fish", "watch", "rock", "bow", "bark", "train", "run",
            "swing", "dance", "cry", "smile", "jump", "kick", "cook", "drink", "dress", "dive", "skate", "fall", "turn", "move", "walk",
            "bat", "star", "slip", "rest");
    private static final Pattern SAW_HER = Pattern.compile("(?i)\\b(saw|see|watched|watch|heard|hear|felt|noticed|made|make|let|helped) her (\\w+)\\s*[.!?]*$");

    static List<String> ambiguities(String s, String low) {
        List<String> out = new ArrayList<String>();
        Matcher m = SAW_HER.matcher(s.trim());
        if (m.find() && NOUN_AND_VERB.contains(m.group(2).toLowerCase(Locale.ROOT))) {
            String w = m.group(2).toLowerCase(Locale.ROOT), v = m.group(1).toLowerCase(Locale.ROOT);
            out.add("It's ambiguous: “her " + w + "” is either the " + w + " that belongs to her (a noun), or her doing it (“" + w
                    + "” a verb: I " + v + " her " + (w.endsWith("e") ? w.substring(0, w.length() - 1) : w) + "ing). What comes before decides.");
        }
        try {
            List<ai.ibiko.ibiko1.formulas.Syntax.Tree> trees = ai.ibiko.ibiko1.formulas.Syntax.parse(s);
            if (trees.size() >= 2 && low.matches("(?s).*\\b(?:with|in|on|near|by|under)\\b.*")) {
                String why = ai.ibiko.ibiko1.formulas.Syntax.ambiguity(s, trees);
                if (why != null && why.startsWith("It's ambiguous")) {
                    out.add(why.replace("\n", " "));
                }
            }
        } catch (RuntimeException ignored) {
            // too long or too odd to parse: no structural reading
        }
        return out;
    }

    private static Set<String> set(String... w) {
        return new HashSet<String>(Arrays.asList(w));
    }
}
