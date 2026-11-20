package ai.ibiko.ibiko1;

import java.util.Locale;

/**
 * The many ways a thing is said, brought to one: before Ibiko's formulas read a message, it is written the plain way, so
 * one rule catches them all. "hey ibiko, wat r u??", "So... what are you?", "Ibiko what ARE you", "can you tell me what
 * you are" and "what are you exactly" are all "what are you". It lowercases; spells out slang ("u", "ur", "r", "wat",
 * "im", "abt", "pls") and contractions ("what's", "you're", "i'm"); takes off greetings, fillers and the assistant's name
 * ("hey", "so", "um", "ok", "ibiko", "please", "actually", "exactly"); takes off polite wrappers ("can you tell me",
 * "i want to know", "i wonder") before a question; and puts one word for several ("chat about", "discuss" → "talk
 * about"). The person's own words are never changed where they are kept: this is only the form the rules read. Pure Java.
 */
public final class Paraphrase {
    private Paraphrase() {
    }

    private static final String[][] SLANG = {
            {"\\bu\\b", "you"}, {"\\bya\\b", "you"}, {"\\byu\\b", "you"}, {"\\bur\\b", "your"}, {"\\byr\\b", "your"},
            {"\\br\\b", "are"}, {"\\bwat\\b", "what"}, {"\\bwht\\b", "what"}, {"\\bwut\\b", "what"}, {"\\bwhats\\b", "what is"},
            {"\\bwats\\b", "what is"}, {"\\bhw\\b", "how"}, {"\\bhws\\b", "how is"}, {"\\bwho's\\b", "who is"}, {"\\bwhos\\b", "who is"},
            {"\\babt\\b", "about"}, {"\\bpls\\b", "please"}, {"\\bplz\\b", "please"}, {"\\bim\\b", "i am"}, {"\\bi'm\\b", "i am"},
            {"\\bdat\\b", "that"}, {"\\bdis\\b", "this"}, {"\\bwanna\\b", "want to"}, {"\\bgonna\\b", "going to"},
            {"\\bcuz\\b", "because"}, {"\\bcoz\\b", "because"}, {"\\bthx\\b", "thanks"}, {"\\bthnx\\b", "thanks"},
            {"\\bwhat's\\b", "what is"}, {"\\bhow's\\b", "how is"}, {"\\bwhere's\\b", "where is"}, {"\\bwhen's\\b", "when is"},
            {"\\byou're\\b", "you are"}, {"\\byoure\\b", "you are"}, {"\\bi've\\b", "i have"}, {"\\byou've\\b", "you have"},
            {"\\bit's\\b", "it is"}, {"\\bthat's\\b", "that is"}, {"\\bwhat're\\b", "what are"}, {"\\bwho're\\b", "who are"},
            {"\\bhow're\\b", "how are"}, {"\\bdunno\\b", "do not know"}, {"\\bkinda\\b", "kind of"}, {"\\bgotta\\b", "have to"},
            {"\\bn\\b", "and"}, {"\\bhv\\b", "have"}, {"\\bcan u\\b", "can you"}, {"\\bdo u\\b", "do you"}, {"\\bb4\\b", "before"},
            {"\\b2day\\b", "today"}, {"\\bppl\\b", "people"}, {"\\btmrw\\b", "tomorrow"}, {"\\bbcos\\b", "because"},
    };

    /** One word for several: the way the rules expect it. */
    private static final String[][] ONE_WORD = {
            {"\\b(chat|converse|have a chat|have a conversation|discuss|yarn|gist)\\s+(about|on)\\b", "talk about"},
            {"\\bwhat (should|shall|could|might) we\\b", "what can we"},
            {"\\bwhat can we (discuss|chat|converse|yarn|gist)$", "what can we talk about"},
            {"\\bwhat do you want to (talk|chat) about\\b", "what can we talk about"},
            {"\\bwhat (topics|things|subjects) can (we|i) (talk|chat|ask you) about\\b", "what can we talk about"},
            {"\\bwhat (are|is) you\\b", "what are you"},
            {"\\bwho (are|is) you\\b", "who are you"},
            {"\\bwhat are your (skills|abilities|capabilities|powers|features)\\b", "what can you do"},
            {"\\bwhat are you (able to do|capable of|good at)\\b", "what can you do"},
            {"\\bwhat (else )?(can|could) you (help (me )?with|do for me)\\b", "what can you do"},
            {"\\bhow are you (doing|keeping|holding up|today|feeling)\\b", "how are you"},
            {"\\bhow (have|has) (you|things) been\\b", "how are you"},
            {"\\b(how far|how body|how you dey|wetin dey happen|sup|what's up|whats up|what is up|wassup|what is good|how is everything|how is life)\\b", "how are you"},
            {"\\bwhat do you know of me\\b", "what do you know about me"},
            {"\\bwhat have you (learned|learnt|got|kept) about me\\b", "what do you know about me"},
            {"\\b(do you|can you) (recall|recollect) my name\\b", "what is my name"},
    };

    /** Greetings, fillers and the assistant's own name, at the start or the end: not part of what is asked. */
    private static final String LEAD = "^(?:(?:hey|hi|hello|yo|ok|okay|alright|so|well|um+|uh+|hmm+|and|but|then|oh|ah|now|listen|"
            + "look|please|kindly|ibiko|dear ibiko|my friend|friend|bro|sis|boss|abeg)\\b[\\s,.!:;-]*)+";
    private static final String TAIL = "(?:[\\s,]+(?:please|ibiko|my friend|friend|bro|abeg|then|now|exactly|actually|really|again|o|oh))+$";
    private static final String WRAP = "^(?:(?:can|could|would|will) you (?:please )?(?:tell me|let me know|say)|tell me|let me know|"
            + "i (?:want|would like|wanna|'d like|need) to know|i wonder|i'm wondering|i am wondering|i was wondering|"
            + "do you mind telling me|any idea|you know)\\s+(?=(?:what|who|how|where|when|why|which)\\b)";

    /** A message the plain way, for the rules to read. */
    public static String normalize(String message) {
        if (message == null) {
            return "";
        }
        String t = message.toLowerCase(Locale.ROOT).replaceAll("[‘’`´]", "'").replaceAll("[“”]", "\"")
                .replaceAll("[^\\p{L}\\p{N}'\"?!.,;:\\s/-]", " ") // emoji and symbols
                .replaceAll("\\s+", " ").trim();
        for (String[] s : SLANG) {
            t = t.replaceAll(s[0], s[1]);
        }
        // "what is ibiko" asks about the name itself: kept before the name is taken off as a way of addressing it
        t = t.replaceAll("\\b(what|who) (is|are) (ibiko|ibiko 1)\\b(?!')", "who are you");
        String before;
        do {
            before = t;
            t = t.replaceAll("[?!.,;:\\s]+$", "").trim();
            t = t.replaceAll(LEAD, "").trim();
            t = t.replaceAll(TAIL, "").trim();
            t = t.replaceAll(WRAP, "").trim();
        } while (!t.equals(before));
        t = t.replaceAll("\\b(actually|exactly|really|honestly|seriously|just|even)\\s+(?=(?:are|is|do|can|what|who|how)\\b)", "");
        t = t.replaceAll("\\b(are|is) (actually|exactly|really|honestly|even)\\b", "$1");
        for (String[] s : ONE_WORD) {
            t = t.replaceAll(s[0], s[1]);
        }
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    /** The normalized form as a question the rules read: with its question mark when it was one. */
    public static String question(String message) {
        String n = normalize(message);
        return message != null && message.trim().matches("(?s).*\\?[\\s!?.]*$") || EncyclopediaQA.question(n) ? n + "?" : n;
    }
}
