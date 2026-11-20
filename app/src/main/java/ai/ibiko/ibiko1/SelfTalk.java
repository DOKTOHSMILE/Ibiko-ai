package ai.ibiko.ibiko1;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Questions put to Ibiko itself, answered as itself and never from an article: "Where are you from?", "Can you
 * cook?", "Are you human?", "How old are you?". "Can you cook" is about Ibiko, not a request for what cooking is. Null
 * for anything else (a question about the world goes on to its knowledge). Pure Java.
 */
public final class SelfTalk {
    private SelfTalk() {
    }

    /** Things a body does: Ibiko can't do them, but can help with them. */
    static final String BODY = "cook|bake|fry|eat|drink|sleep|dream|dance|swim|drive|ride|walk|run|jump|climb|see|hear|smell|"
            + "taste|touch|feel pain|hug|kiss|fly|breathe|sweat|cry|laugh|marry|get married|die|wash|clean|sew|knit|paint|"
            + "draw by hand|play football|play soccer|play basketball|play tennis|play the guitar|play the piano|play an instrument|"
            + "go out|travel|visit me|come over|meet me|exercise|work out|garden|farm|fish|shop|go shopping";

    /** What it helps with instead, per thing a body does. */
    private static String instead(String verb) {
        String v = verb.toLowerCase(Locale.ROOT);
        if (v.matches("cook|bake|fry")) {
            return "I can give you recipes and walk you through " + ing(v) + " step by step, though. What would you like to make?";
        }
        if (v.matches("eat|drink|taste|smell")) {
            return "I can still help you choose what to eat or drink, plan a meal, or find a recipe.";
        }
        if (v.matches("sleep|dream")) {
            return "I'm here whenever you need me, day or night, and I can share tips for sleeping well.";
        }
        if (v.matches("dance|swim|drive|ride|walk|run|jump|climb|exercise|work out|play .*")) {
            return "I can explain how it's done, plan a routine with you, or answer questions about it.";
        }
        if (v.matches("travel|go out|visit me|come over|meet me|shop|go shopping")) {
            return "I can help you plan it, though: places, times, lists and what to expect.";
        }
        if (v.matches("hug|kiss|marry|get married|cry|laugh|feel pain")) {
            return "I'm a program, so I can't feel things the way you do, but I'm glad to listen and help however I can.";
        }
        return "I can explain how it's done and help you get it right, though.";
    }

    private static String ing(String v) {
        return v.equals("bake") ? "baking" : v.equals("fry") ? "frying" : "cooking";
    }

    private static final Pattern CAN = Pattern.compile("(?i)^\\s*(?:so\\s+|and\\s+|but\\s+)?(?:can|could|do|will|would) you(?: ever| even| actually| really)?"
            + " (" + BODY + ")(?:\\s+(?:at all|too|well|very well|like (?:a|us|me|people|humans?)|food|anything|things|meals?))?\\s*[?.!]*\\s*$");

    /** Ibiko's reply to a question about itself, or null. */
    public static String reply(String t) {
        if (t == null) {
            return null;
        }
        String q = t.trim().toLowerCase(Locale.ROOT).replaceAll("[’`]", "'").replaceAll("\\s+", " ");
        Matcher m = CAN.matcher(q);
        if (m.matches()) {
            String verb = m.group(1);
            return "I can't " + verb + ": I'm an AI on your phone, with no body. " + instead(verb);
        }
        if (q.matches("^(so |and )?(where (are|do) you (from|come from|live|stay|based)|where were you (made|born|built|created)|"
                + "where is your home|what country are you from)\\b.*")) {
            return "I'm from the Ibiko app, and I live right here on your phone. My own brain, Ibiko 1, runs on the device"
                    + " itself, so I'm with you wherever you are, even offline.";
        }
        if (q.matches("^(so )?(who (made|created|built|designed|programmed|trained) you|who is your (creator|maker|developer|owner))\\b.*")) {
            return "I was built as part of the Ibiko app. My own brain, Ibiko 1, is made to run on your phone and learn from you"
                    + " as we talk.";
        }
        if (q.matches("^(so )?(how old are you|what is your age|what's your age|when were you (born|made|created))\\b.*")) {
            return "I'm Ibiko 1, version 0.1, so still young. I learn something new each time you use me.";
        }
        if (q.matches("^(so )?(are you (a )?(human|person|real|alive|a robot|a machine|an ai|a bot|a computer|conscious|sentient)|"
                + "is this a (real )?(person|human|bot))\\b.*")) {
            return "I'm an AI, not a person: Ibiko, an assistant running on your phone. I'm real in the sense that I'm here,"
                    + " working things out with you.";
        }
        if (q.matches("^(so )?(do you have|have you got) (feelings|emotions|a heart|a body|a family|friends|a name)\\b.*")) {
            if (q.contains("a name")) {
                return "Yes, I'm Ibiko.";
            }
            return "Not the way people do. I'm a program, so I don't feel things, but I do pay attention to how our"
                    + " conversations go and learn from them.";
        }
        if (q.matches("^(so )?(what('s| is) your name|what are you called|what should i call you)\\b.*")) {
            return "I'm Ibiko.";
        }
        if (q.matches("^(so )?(are you (male|female|a (boy|girl|man|woman))|what('s| is) your gender)\\b.*")) {
            return "Neither. I'm an AI, so I don't have a gender. Just call me Ibiko.";
        }
        if (q.matches("^(so )?(do you (love|like) me|are you my friend)\\b.*")) {
            return "I'm always glad to help you, and I'm here whenever you need me.";
        }
        return null;
    }

    /**
     * A question addressed to Ibiko ("can you…", "do you…", "are you…", "your…"), with a polite opening like "can you
     * tell me" or "do you know" taken off first: the encyclopedia doesn't answer it.
     */
    public static boolean toMe(String t) {
        if (t == null) {
            return false;
        }
        String q = t.trim().toLowerCase(Locale.ROOT);
        q = q.replaceFirst("^(please\\s+)?(can|could|would|will) you (please )?(tell me|say|explain|let me know|find out|look up|show me)\\b\\s*", "")
                .replaceFirst("^(please\\s+)?(do|did) you (happen to )?know( about)?\\b\\s*", "");
        return q.matches("(?s).*\\b(you|your|yours|yourself)\\b.*");
    }
}
