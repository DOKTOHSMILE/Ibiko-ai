package ai.ibiko.ibiko1.formulas;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The things people say to the one they're talking with, not about the world: asking leave ("can I ask you something
 * private?") and the short bits of talk ("What?", "You!", a question
 * cut short: "What can I?"). Each is read in the conversation: "What?" right after an answer means it wasn't followed,
 * so the answer is said again more simply; a question cut short that the last one began the same way is that question.
 * Pure Java.
 */
public final class Social {
    private Social() {
    }

    private static final Pattern LEAVE = Pattern.compile("(?i)^\\s*(?:hey,?\\s+|so,?\\s+|ok(?:ay)?,?\\s+)?(?:can|may|could) i\\s+(?:please\\s+)?"
            + "(?:ask|tell|say|share|talk to|talk with|show|send|confess|run|bounce|get your)\\b.*\\?*\\s*$");
    private static final Pattern PRIVATE = Pattern.compile("(?i)\\b(private|personal|secret|confidential|sensitive|embarrassing)\\b");
    private static final Pattern HUH = Pattern.compile("(?i)^\\s*(?:what|huh|pardon|sorry|eh|come again|say that again|what do you mean|i don'?t get it)\\s*[?!.]*\\s*$");
    private static final Pattern YOU = Pattern.compile("(?i)^\\s*(?:you|yes you|hey you|you there)\\s*[!?.]*\\s*$");
    /** Nothing but question words: a question that stops before its end ("What can I?", "Can I?", "What can?"). */
    private static final Pattern CUT = Pattern.compile("(?i)^\\s*(?:(?:what|who|where|when|why|how|which|can|could|may|do|does|did|is|are|will|would|should)"
            + "(?:\\s+(?:can|could|do|does|did|is|are|will|would|should|i|you|we|they|it|he|she))*)\\s*[?!.]*\\s*$");

    /** The reply to something said to Ibiko itself, or null when it isn't one of these. */
    public static String reply(String message, String lastAnswer, String lastAsked) {
        return reply(message, lastAnswer, lastAsked, null);
    }

    /** As {@link #reply(String, String, String)}, a question cut short answered with {@code cutShort} when one is given (its kind's own reply). */
    public static String reply(String message, String lastAnswer, String lastAsked, String cutShortReply) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        if (LEAVE.matcher(m).matches()) {
            return PRIVATE.matcher(m).find()
                    ? "Of course, go ahead. I answer here on your phone; a question only goes out (to the web, or your API model if you've"
                    + " set one up) when I don't know the answer myself. You can turn those off in Settings › Data and privacy."
                    : "Of course, go ahead.";
        }
        // "what can I do with you?": Ibiko's own account of itself answers it (SelfModel), with its live counts
        if (YOU.matcher(m).matches()) {
            return "Yes, me! I'm Ibiko. What would you like to do? Ask me anything, or say \"what can you do?\" to see what I'm good at.";
        }
        if (HUH.matcher(m).matches()) {
            if (lastAnswer != null && !lastAnswer.trim().isEmpty()) {
                return null; // said again more simply: the reshaping reading does that, with the last answer
            }
            return "What would you like to know? Ask me anything.";
        }
        if (CUT.matcher(m).matches() && m.split("\\s+").length <= 4 && cutShort(m)) {
            String words = m.replaceAll("[?!.]+$", "").trim();
            if (lastAsked != null && lastAsked.toLowerCase(Locale.ROOT).startsWith(words.toLowerCase(Locale.ROOT))
                    && lastAsked.trim().length() > words.length() + 2) {
                return "That's how your last question began: “" + lastAsked.trim() + "” Did you want to ask it again, or ask something else?";
            }
            return cutShortReply != null ? cutShortReply
                    : "“" + Character.toUpperCase(words.charAt(0)) + words.substring(1) + "…” what? The question stops there: finish it and I'll answer.";
        }
        return null;
    }

    private static final java.util.Set<String> MODALS = new java.util.HashSet<String>(java.util.Arrays.asList("can", "could", "may", "might", "should",
            "would", "will", "shall", "must", "do", "does", "did"));
    private static final java.util.Set<String> AUX = new java.util.HashSet<String>(java.util.Arrays.asList("can", "could", "may", "might", "should",
            "would", "will", "shall", "must", "do", "does", "did", "is", "are", "was", "were"));

    /**
     * Whether question words stop before the question does: ending on a helping verb ("What can…", "Who is…"), or on
     * I, you, we or they right after one of can, do, should… ("What can I…", "Can I…"). A lone "Why?" is a whole
     * follow-up, and "What are you?", "Who are you?" are whole questions.
     */
    static boolean cutShort(String m) {
        String[] w = m.toLowerCase(Locale.ROOT).replaceAll("[?!.]+", "").trim().split("\\s+");
        if (w.length < 2) {
            return false;
        }
        String last = w[w.length - 1], before = w[w.length - 2];
        boolean pronoun = before.matches("i|you|we|they|he|she|it");
        // "What can you do?": "do" after "you" is the doing itself, not a helper waiting for its verb
        return AUX.contains(last) && !pronoun || last.matches("i|you|we|they") && MODALS.contains(before);
    }

    /** "What?" right after an answer: it wasn't followed. */
    public static boolean huh(String message) {
        return message != null && HUH.matcher(message.trim()).matches();
    }
}
