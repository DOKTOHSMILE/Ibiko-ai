package ai.ibiko.ibiko1.formulas;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The things people say to the one they're talking with, not about the world: asking leave ("can I ask you something
 * private?"), asking what it can do ("what can I do with you?"), and the short bits of talk ("What?", "You!", a question
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
    private static final Pattern CAN_DO = Pattern.compile("(?i)^\\s*(?:so,?\\s+)?(?:what (?:can|could|do|should) (?:i|we) (?:do|use you for|ask)(?: with you| here| you)?"
            + "|what (?:can|do|could) you do(?: for me)?|what are you (?:able to do|good at|for)|how (?:can|could|do) you help(?: me)?"
            + "|what (?:things |kind of things )?can you help (?:me )?with|what do you know how to do|what are your (?:skills|abilities|features)"
            + "|help|help me|what can you do for me)\\s*[?.!]*\\s*$");
    private static final Pattern HUH = Pattern.compile("(?i)^\\s*(?:what|huh|pardon|sorry|eh|come again|say that again|what do you mean|i don'?t get it)\\s*[?!.]*\\s*$");
    private static final Pattern YOU = Pattern.compile("(?i)^\\s*(?:you|yes you|hey you|you there)\\s*[!?.]*\\s*$");
    /** Nothing but question words: a question that stops before its end ("What can I?", "Can I?", "What can?"). */
    private static final Pattern CUT = Pattern.compile("(?i)^\\s*(?:(?:what|who|where|when|why|how|which|can|could|may|do|does|did|is|are|will|would|should)"
            + "(?:\\s+(?:can|could|do|does|did|is|are|will|would|should|i|you|we|they|it|he|she))*)\\s*[?!.]*\\s*$");

    /** What Ibiko can do, each with a thing to try. */
    public static String abilities() {
        return "Quite a lot, on this phone and offline:\n\n"
                + "• Answer questions from my encyclopedia: \"What is the capital of Kenya?\", \"Who was Nelson Mandela?\", \"What's the highest mountain in Africa?\"\n"
                + "• Work things out step by step: sums, percentages, \"I have 12 sweets and give away 4…\", \"Ada is taller than Obi…\", \"All cats are mammals…\"\n"
                + "• Words: meanings, spelling, opposites, and say my last answer shorter, simpler or as points\n"
                + "• Code: write it in many languages, explain code you paste, and tell you what an error means and how to fix it\n"
                + "• Make things: a website for your business, a small app (a calculator, a quiz, a to-do list), and change them when you ask\n"
                + "• Remember what you tell me about you, and what we talked about\n\n"
                + "And through the menus: pictures, voice, files, social media posts and campaigns. When I don't know something, I'll look it up"
                + " (online) or ask your API model, check the answer, and keep it.";
    }

    /** The reply to something said to Ibiko itself, or null when it isn't one of these. */
    public static String reply(String message, String lastAnswer, String lastAsked) {
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
        if (CAN_DO.matcher(m).matches()) {
            return abilities();
        }
        if (YOU.matcher(m).matches()) {
            return "Yes, me! I'm Ibiko. What would you like to do? Ask me anything, or say \"what can you do?\" to see what I'm good at.";
        }
        if (HUH.matcher(m).matches()) {
            if (lastAnswer != null && !lastAnswer.trim().isEmpty()) {
                return null; // said again more simply: the reshaping reading does that, with the last answer
            }
            return "What would you like to know? Ask me anything.";
        }
        if (CUT.matcher(m).matches() && m.split("\\s+").length <= 4) {
            String words = m.replaceAll("[?!.]+$", "").trim();
            if (lastAsked != null && lastAsked.toLowerCase(Locale.ROOT).startsWith(words.toLowerCase(Locale.ROOT))
                    && lastAsked.trim().length() > words.length() + 2) {
                return "That's how your last question began: “" + lastAsked.trim() + "” Did you want to ask it again, or ask something else?";
            }
            return "“" + Character.toUpperCase(words.charAt(0)) + words.substring(1) + "…” what? The question stops there: finish it and I'll answer.";
        }
        return null;
    }

    /** "What?" right after an answer: it wasn't followed. */
    public static boolean huh(String message) {
        return message != null && HUH.matcher(message.trim()).matches();
    }
}
