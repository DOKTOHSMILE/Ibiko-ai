package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ai.ibiko.ibiko1.formulas.QuestionTypes;
import ai.ibiko.ibiko1.formulas.SearchPhrase;

/**
 * What a message asks, read with the running conversation: the words as said, the question as understood (its "he" and
 * "it" put back from the talk), its kind (a when-question, a why-question…), what the talk is about now, and the turns
 * just before it. Ibiko v1 works this out once a turn and every part reads it: the web search is phrased with it, the
 * device's model and the API model are asked with it, and the thinking says it. So no part answers the words alone, as if
 * the conversation hadn't happened. Pure Java.
 */
public final class Understanding {
    /** As typed, and as understood with the conversation. */
    public final String said, asked;
    /** The kind of question, by how it starts; null when none fits. */
    public final QuestionTypes.Kind kind;
    /** What the talk is about ("computing", "botany"), and the thing in focus ("Nigeria", "Mandela"); null when not clear. */
    public final String topic, focus;
    /** The turns just before this one, oldest first. */
    public final List<Conversation.Turn> recent;

    /** The turns read with a question. */
    static final int TURNS = 3;

    public Understanding(String said, String asked, QuestionTypes.Kind kind, String topic, String focus, List<Conversation.Turn> recent) {
        this.said = said == null ? "" : said.trim();
        this.asked = asked == null || asked.trim().isEmpty() ? this.said : asked.trim();
        this.kind = kind;
        this.topic = blank(topic) ? null : topic.trim();
        this.recent = recent == null ? Collections.<Conversation.Turn>emptyList() : recent;
        // a focus is only trusted when the talk or the question names it as a word ("Nation" is not in "national")
        this.focus = blank(focus) || !named(focus.trim(), this.asked, this.recent) ? null : focus.trim();
    }

    private static boolean named(String focus, String asked, List<Conversation.Turn> recent) {
        java.util.regex.Pattern word = java.util.regex.Pattern.compile("(?i)(?<![\\p{L}\\p{N}])" + java.util.regex.Pattern.quote(focus)
                + "(?![\\p{L}\\p{N}])");
        if (word.matcher(asked).find()) {
            return true;
        }
        for (Conversation.Turn t : recent) {
            if (word.matcher(t.asked).find() || word.matcher(t.said).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    /** The same reading with the question put another way (its name repaired, say). */
    public Understanding with(String question) {
        return blank(question) ? this : new Understanding(said, question, kind, topic, focus, recent);
    }

    /** Whether the conversation changed how it is read: its words were filled in from the talk. */
    public boolean readWithTalk() {
        return !asked.equalsIgnoreCase(said);
    }

    /** Whether the question names its own subject, or leans on the talk for it ("What's the population?", "Is it safe?"). */
    boolean namesItsSubject(SearchPhrase rules) {
        if (rules.core(asked, null) != null) {
            return true;
        }
        String k = rules.keywords(rules.bare(asked));
        return k.split("\\s+").length >= 3;
    }

    /**
     * The searches to try, best first, phrased with the conversation: a question that leans on the talk for its subject
     * is searched with the thing in focus ("population" while on Nigeria: "Nigeria population"), a lone word with what
     * the talk is about ("root" while on Linux: "root computing"), and news of now with this year.
     */
    public List<String> searches(SearchPhrase rules, int year) {
        List<String> tries = new ArrayList<String>(rules.tries(asked, year));
        if (tries.isEmpty()) {
            return tries;
        }
        String first = tries.get(0);
        String low = first.toLowerCase(Locale.ROOT);
        String better = null;
        if (focus != null && !low.contains(focus.toLowerCase(Locale.ROOT)) && !namesItsSubject(rules)) {
            better = focus + " " + first;
        } else if (topic != null && first.split("\\s+").length <= 2 && !low.contains(topic.toLowerCase(Locale.ROOT))) {
            better = first + " " + topic;
        }
        if (kind != null && year > 0 && ("CURRENT_EVENT".equals(kind.row.type) || "FUTURE_EVENT".equals(kind.row.type))
                && !(better == null ? first : better).matches(".*\\b(?:19|20)\\d\\d\\b.*")) {
            better = (better == null ? first : better) + " " + year;
        }
        if (better != null) {
            tries.add(0, better);
        }
        // the same search twice is no help
        List<String> out = new ArrayList<String>();
        for (String t : tries) {
            boolean seen = false;
            for (String o : out) {
                seen |= o.equalsIgnoreCase(t);
            }
            if (!seen) {
                out.add(t);
            }
        }
        return out;
    }

    /** For the thinking: how the question was read with the talk; null when the talk added nothing. */
    public String step() {
        List<String> parts = new ArrayList<String>();
        if (kind != null) {
            parts.add(kind.said());
        }
        if (focus != null) {
            parts.add("we're on " + focus);
        } else if (topic != null) {
            parts.add("we're talking about " + topic);
        }
        if (!recent.isEmpty()) {
            parts.add("read with the last " + (recent.size() == 1 ? "turn" : recent.size() + " turns"));
        }
        return parts.isEmpty() ? null : "What you're asking, with our conversation: " + String.join("; ", parts);
    }

    /**
     * What a model is asked: the question as understood, with the conversation before it, so the device's model or the
     * API model answers it in the talk, not as a question out of nowhere. Pasted orders are fenced off as text.
     */
    public String forModel(String question) {
        StringBuilder b = new StringBuilder();
        if (!recent.isEmpty()) {
            b.append("The conversation so far (for reference; answer only the question at the end):\n");
            for (Conversation.Turn t : recent) {
                b.append("Person: ").append(clip(PromptReader.guard(t.asked), 300)).append('\n');
                b.append("Ibiko: ").append(clip(t.said, 400)).append('\n');
            }
            b.append('\n');
        }
        if (focus != null || topic != null || kind != null) {
            b.append("Reading of the question:");
            if (focus != null) {
                b.append(" it is about ").append(focus).append('.');
            } else if (topic != null) {
                b.append(" the talk is about ").append(topic).append('.');
            }
            if (kind != null) {
                b.append(" It is ").append(kind.said()).append('.');
            }
            b.append('\n');
        }
        if (readWithTalk()) {
            b.append("The person typed: ").append(PromptReader.guard(said)).append('\n');
        }
        b.append("Question: ").append(PromptReader.guard(question == null ? asked : question));
        return b.toString();
    }

    private static String clip(String s, int n) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }
}
