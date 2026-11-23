package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The conversation so far, kept turn by turn so it can be talked about: what was asked, what it was about, and what
 * was said back. "What have we talked about?", "what did you say about Nigeria?", "go back to Ghana", "what was the
 * first thing I asked?" are answered from it, the way a person remembers a talk rather than only its last line. Pure
 * Java; one per chat.
 *
 * <pre>
 * V1  recap     the subjects in the order they came up, with what was asked about each
 * V2  recall    what was said about a subject (or a word), the latest first
 * V3  return    "go back to X": X becomes the subject again, with what was said about it last
 * V4  first     the first or last question asked
 * </pre>
 */
public final class Conversation {
    /** The most turns kept: older ones go first. */
    public static final int MOST = 200;

    /** One turn: what was asked (as understood), what it was about (or null), what was said. */
    public static final class Turn {
        public final String asked, about, said;

        Turn(String asked, String about, String said) {
            this.asked = asked == null ? "" : asked.trim();
            this.about = about;
            this.said = said == null ? "" : said.trim();
        }
    }

    private final List<Turn> turns = new ArrayList<Turn>();

    /** Keeps a turn. */
    public synchronized void add(String asked, String about, String said) {
        if (said == null || said.trim().isEmpty()) {
            return;
        }
        turns.add(new Turn(asked, about, said));
        while (turns.size() > MOST) {
            turns.remove(0);
        }
    }

    public synchronized int size() {
        return turns.size();
    }

    public synchronized void clear() {
        turns.clear();
    }

    /** What a message asks about the conversation itself, read; null when it isn't about the conversation. */
    public static final class Ask {
        public enum Kind { RECAP, RECALL, RETURN, FIRST, LAST }

        public final Kind kind;
        /** The subject asked about (recall, return); null for the others. */
        public final String subject;

        Ask(Kind kind, String subject) {
            this.kind = kind;
            this.subject = subject;
        }
    }

    private static final Pattern RECAP = Pattern.compile("(?i)^\\s*(?:so,?\\s+)?(?:what (?:have|did) we (?:talk(?:ed)? about|discuss(?:ed)?|cover(?:ed)?)"
            + "(?: so far| today| in this chat)?|(?:sum(?:marise|marize|up)|recap)(?: (?:our|this|the) (?:chat|conversation|talk|discussion))?"
            + "(?: so far)?|what topics (?:have we|did we) (?:talk(?:ed)? about|cover(?:ed)?)|give me a (?:recap|summary)(?: of (?:our|this|the) "
            + "(?:chat|conversation))?)\\s*[?.!]*\\s*$");
    private static final Pattern RECALL = Pattern.compile("(?i)^\\s*(?:what did you (?:say|tell me|write|answer)|what was (?:it|that) you said|"
            + "remind me what you said|what did we say)\\s+(?:about|on|regarding)\\s+(.{2,60}?)(?:\\s+(?:earlier|before|again|just now))?\\s*[?.!]*\\s*$");
    private static final Pattern RETURN = Pattern.compile("(?i)^\\s*(?:(?:ok(?:ay)?|so|now|anyway),?\\s+)?(?:(?:let'?s |can we |could we )?go back to|back to|"
            + "returning to|let'?s talk about|let'?s get back to|getting back to)\\s+(.{2,60}?)(?:\\s+(?:again|for a (?:moment|second|bit)))?\\s*[?.!]*\\s*$");
    private static final Pattern FIRST = Pattern.compile("(?i)^\\s*what (?:was|is) the (first|last|previous) (?:thing|question) (?:i|we) (?:asked|said|talked about)"
            + "(?: you)?\\s*[?.!]*\\s*$|^\\s*what did i (first) ask(?: you)?\\s*[?.!]*\\s*$");

    /** V1–V4: what a message asks about the conversation, or null. */
    public static Ask read(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        Matcher x;
        if (RECAP.matcher(m).matches()) {
            return new Ask(Ask.Kind.RECAP, null);
        }
        if ((x = RECALL.matcher(m)).matches()) {
            return new Ask(Ask.Kind.RECALL, bare(x.group(1)));
        }
        if ((x = RETURN.matcher(m)).matches() && !x.group(1).matches("(?i)(work|sleep|bed|you|me|it|that|this|the start|the beginning)")) {
            return new Ask(Ask.Kind.RETURN, bare(x.group(1)));
        }
        if ((x = FIRST.matcher(m)).matches()) {
            String which = x.group(1) != null ? x.group(1) : x.group(2);
            return new Ask("first".equalsIgnoreCase(which) ? Ask.Kind.FIRST : Ask.Kind.LAST, null);
        }
        return null;
    }

    private static String bare(String s) {
        return s.trim().replaceFirst("(?i)^(the|a|an)\\s+", "").replaceAll("[,;:.!?]+$", "").trim();
    }

    /** V1: the subjects in the order they came up, and how much was asked about each; null when nothing was. */
    public synchronized String recap() {
        Map<String, List<String>> by = new LinkedHashMap<String, List<String>>();
        List<String> loose = new ArrayList<String>();
        for (Turn t : turns) {
            if (t.asked.isEmpty() || Conversation.read(t.asked) != null) {
                continue;
            }
            if (t.about == null) {
                loose.add(t.asked);
                continue;
            }
            List<String> l = by.get(t.about);
            if (l == null) {
                l = new ArrayList<String>();
                by.put(t.about, l);
            }
            l.add(t.asked);
        }
        if (by.isEmpty() && loose.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("So far we've talked about:");
        for (Map.Entry<String, List<String>> e : by.entrySet()) {
            int n = e.getValue().size();
            b.append("\n• ").append(e.getKey()).append(" (").append(n).append(n == 1 ? " question" : " questions")
                    .append(n == 1 ? ": “" + clip(e.getValue().get(0), 60) + "”" : ", last: “" + clip(e.getValue().get(n - 1), 60) + "”").append(")");
        }
        if (!loose.isEmpty()) {
            b.append("\n• ").append(loose.size() == 1 ? "one other question" : loose.size() + " other questions")
                    .append(", the last: “").append(clip(loose.get(loose.size() - 1), 60)).append("”");
        }
        return b.toString();
    }

    /** V2: what was said about a subject, the latest first (up to two turns); null when nothing was. */
    public synchronized String recall(String subject) {
        List<Turn> found = about(subject);
        if (found.isEmpty()) {
            return null;
        }
        Turn last = found.get(found.size() - 1);
        StringBuilder b = new StringBuilder("You asked “").append(clip(last.asked, 80)).append("”, and I said: ").append(clip(last.said, 400));
        if (found.size() > 1) {
            Turn before = found.get(found.size() - 2);
            b.append("\n\nBefore that, about “").append(clip(before.asked, 80)).append("”: ").append(clip(before.said, 200));
        }
        return b.toString();
    }

    /** The turns about a subject: named as what a turn was about, or in what was asked; in order. */
    public synchronized List<Turn> about(String subject) {
        List<Turn> out = new ArrayList<Turn>();
        String s = subject.toLowerCase(Locale.ROOT);
        for (Turn t : turns) {
            if (Conversation.read(t.asked) != null) {
                continue;
            }
            boolean named = t.about != null && (t.about.toLowerCase(Locale.ROOT).contains(s) || s.contains(t.about.toLowerCase(Locale.ROOT)));
            if (named || Pattern.compile("(?i)\\b" + Pattern.quote(subject) + "\\b").matcher(t.asked).find()) {
                out.add(t);
            }
        }
        return out;
    }

    /** The name a subject went by in the talk ("nigeria" → "Nigeria"), or the words as given. */
    public synchronized String named(String subject) {
        List<Turn> t = about(subject);
        for (int i = t.size() - 1; i >= 0; i--) {
            if (t.get(i).about != null) {
                return t.get(i).about;
            }
        }
        return subject;
    }

    /** V4: the first or the last question asked (before this one); null when none was. */
    public synchronized String question(boolean first) {
        List<Turn> asked = new ArrayList<Turn>();
        for (Turn t : turns) {
            if (!t.asked.isEmpty() && Conversation.read(t.asked) == null) {
                asked.add(t);
            }
        }
        if (asked.isEmpty()) {
            return null;
        }
        return (first ? asked.get(0) : asked.get(asked.size() - 1)).asked;
    }

    static String clip(String s, int most) {
        String one = s.replaceAll("\\s+", " ").trim();
        if (one.length() <= most) {
            return one;
        }
        int cut = one.lastIndexOf(' ', most - 1);
        return one.substring(0, cut > most / 2 ? cut : most - 1) + "…";
    }
}
