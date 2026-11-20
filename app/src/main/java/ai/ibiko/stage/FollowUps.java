package ai.ibiko.stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The answers a reply is waiting for, as cards to tap: a reply that ends by asking the person something they can
 * answer in a few words ("Would you like a shorter test or help with the wrapper?", "Shall I save it?", "How would you
 * like to proceed?" over a list of options). A question only the person can answer in their own words ("What's your
 * name?") gets no cards. At most four, each short. Pure Java.
 */
public final class FollowUps {
    private FollowUps() {
    }

    public static final int MOST = 4;
    private static final int LONGEST = 70;

    private static final Pattern OFFER = Pattern.compile("(?i)^(?:so,?\\s+|ok(?:ay)?,?\\s+|now,?\\s+)?(would you like(?: me)? to|would you like|"
            + "do you want(?: me)? to|do you want|want me to|shall i|should i|can i|may i|could i|would it help if i|"
            + "would you prefer(?: to)?|do you prefer|which (?:would you prefer|do you prefer|one do you want|one would you like)|"
            + "are you (?:okay|ok|happy) (?:with|for me to))\\s+(.+?)\\?$");
    private static final Pattern CHECK = Pattern.compile("(?i)^(is (?:that|this) (?:right|correct|accurate|ok|okay)|did i get (?:that|it) right|"
            + "does (?:that|this) (?:help|make sense|work for you|look right)|is that what you meant|was that helpful)\\??$");
    private static final Pattern PROCEED = Pattern.compile("(?i)^(how would you like to proceed|what would you like to do(?: next)?|"
            + "which (?:one|option)(?: would you like| do you want| should i do)?|what should i do next|where should we go from here|"
            + "pick one|choose one)\\b.*");
    private static final Pattern ITEM = Pattern.compile("^\\s*(?:[-*•]|\\d{1,2}[.)]|[a-d][.)])\\s+(.+)$");

    /** The cards for a reply: the answers it is waiting for, or none. */
    public static List<String> of(String reply) {
        List<String> out = new ArrayList<String>();
        if (reply == null) {
            return out;
        }
        String text = reply.replaceAll("(?s)```.*?```", " ").replaceAll("\\*\\*|__|`", "").trim();
        if (text.isEmpty()) {
            return out;
        }
        String[] lines = text.split("\\n");
        // options listed under a "how would you like to proceed?" line (or listed last, after a question)
        List<String> listed = new ArrayList<String>();
        int at = lines.length - 1;
        while (at >= 0 && (lines[at].trim().isEmpty() || ITEM.matcher(lines[at]).matches())) {
            Matcher m = ITEM.matcher(lines[at]);
            if (m.matches()) {
                listed.add(0, m.group(1).trim());
            }
            at--;
        }
        String lead = at >= 0 ? lastSentence(lines[at]) : "";
        if (!listed.isEmpty() && (lead.endsWith("?") || lead.endsWith(":") && PROCEED.matcher(lead).matches())) {
            for (String o : listed) {
                add(out, o.replaceAll("[.:;]+$", ""));
            }
            return out;
        }
        String last = lastSentence(text);
        if (!last.endsWith("?")) {
            return out;
        }
        Matcher offer = OFFER.matcher(last);
        if (offer.matches()) {
            String what = offer.group(2).trim();
            List<String> choices = choices(what);
            if (choices.size() >= 2) {
                for (String c : choices) {
                    add(out, c);
                }
                return out;
            }
            add(out, "Yes, please");
            add(out, "No, thanks");
            return out;
        }
        if (CHECK.matcher(last).matches()) {
            add(out, "Yes, that's right");
            add(out, "No, not quite");
        }
        return out;
    }

    /** "a shorter test, a longer one, or help with the wrapper" → the three of them. */
    static List<String> choices(String what) {
        List<String> out = new ArrayList<String>();
        String w = what.replaceAll("(?i)^(?:either|maybe)\\s+", "");
        if (!w.matches("(?is).*\\bor\\b.*")) {
            return out;
        }
        for (String p : w.split("(?i)\\s*,\\s*(?:or\\s+)?|\\s+or\\s+")) {
            String c = p.trim().replaceAll("(?i)^(?:to\\s+)", "");
            if (c.isEmpty() || c.matches("(?i)(something else|anything else|both|neither|not)")) {
                continue;
            }
            out.add(c);
        }
        return out.size() > MOST ? new ArrayList<String>() : out;
    }

    private static String lastSentence(String text) {
        String t = text.trim();
        String[] parts = t.split("(?<=[.!?])\\s+(?=[A-Z\"“])");
        return parts.length == 0 ? "" : parts[parts.length - 1].trim();
    }

    private static void add(List<String> out, String option) {
        String o = option.trim();
        if (o.isEmpty() || out.size() >= MOST) {
            return;
        }
        if (o.length() > LONGEST) {
            int cut = o.lastIndexOf(' ', LONGEST - 1);
            o = o.substring(0, cut > 20 ? cut : LONGEST - 1) + "…";
        }
        o = Character.toUpperCase(o.charAt(0)) + o.substring(1);
        for (String had : out) {
            if (had.equalsIgnoreCase(o)) {
                return;
            }
        }
        out.add(o);
    }

    /** Whether a reply waits for an answer the person can tap. */
    public static boolean waits(String reply) {
        return !of(reply).isEmpty();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
