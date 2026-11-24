package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The kind of question, read from how it starts: "why" is curiosity about a reason, "what is" asks what a thing is, "what
 * was" asks about the past, "what happened" for an event, "what is happening" for what's going on now, "what am I" is
 * about the person, "can I" asks leave, "can you" asks for something done. The kinds are knowledge
 * (knowledge/language/question_types.tsv, tools/build_question_types.py): a line added there teaches a new kind. Each
 * question's kind goes into Ibiko's thinking; a question that is only its opening ("What was?", "Why?") is answered from
 * its kind and the subject being talked about. Pure Java.
 */
public final class QuestionTypes {
    /** One kind of opening. */
    public static final class Row {
        public final String id, type, wants, when, alone;
        final Pattern opening;
        /** An opening with a slot in it ("what does … mean"): what fills the slot is the question, never only its opening. */
        final boolean slot;

        Row(String[] f) {
            id = f[0];
            slot = f[1].contains(".{") || f[1].contains(".*") || f[1].contains(".+");
            opening = Pattern.compile("(?i)^\\s*(?:(?:so|and|but|ok|okay|hey|um|well),?\\s+)?(?:" + f[1] + ")\\b");
            type = f[2];
            wants = f[3];
            when = f[4];
            alone = f[5];
        }
    }

    /** A question read: its kind, and whether it is only its opening (nothing said after "What was"). */
    public static final class Kind {
        public final Row row;
        public final boolean openingOnly;

        Kind(Row row, boolean openingOnly) {
            this.row = row;
            this.openingOnly = openingOnly;
        }

        /** For the thinking: "a why-question: you're curious about the reason something is so". */
        public String said() {
            return label(row) + ": you want " + row.wants;
        }
    }

    private final List<Row> rows = new ArrayList<Row>();

    public QuestionTypes(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (!line.startsWith("#") && f.length >= 6) {
                    rows.add(new Row(f));
                }
            }
        } catch (IOException ignored) {
            // none: questions are read without their kind
        }
    }

    public int size() {
        return rows.size();
    }

    /** The kind of a question, by its opening (the first that fits, most particular first); null when none fits. */
    public Kind of(String message) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        for (Row r : rows) {
            Matcher x = r.opening.matcher(m);
            if (x.find()) {
                String rest = m.substring(x.end()).replaceAll("[\\s?!.,…]+", "");
                return new Kind(r, rest.isEmpty() && !r.slot);
            }
        }
        return null;
    }

    /** Openings that are whole questions by themselves ("Who am I?", "What are you?", "How are you?"): not asked to finish. */
    private static final Set<String> WHOLE = new HashSet<String>(Arrays.asList("SELF", "IBIKO", "WELLBEING"));

    /**
     * The reply to a question that is only its opening ("What was?", "What happened?", "Can I?"), from its kind and the
     * subject being talked about; null when the opening is a whole question by itself.
     */
    public static String alone(Kind k, String topic) {
        if (k == null || !k.openingOnly || WHOLE.contains(k.row.type)) {
            return null;
        }
        boolean on = topic != null && !topic.trim().isEmpty();
        return k.row.alone.replace("{about}", on ? " about " + topic : "")
                .replace("{topic_q}", on ? " About " + topic + ", which we were on?" : "")
                .replace("{topic}", on ? topic : "it");
    }

    private static String label(Row r) {
        switch (r.type) {
            case "REASON": return "a why-question, out of curiosity";
            case "CAUSE": return "a question about a cause";
            case "CONSEQUENCE": return "a what-if question";
            case "MEANING": return "a question about a word's meaning";
            case "DEFINITION": return "a what-is question";
            case "FACT": return "a question of fact";
            case "PAST": return "a what-was question, about the past";
            case "PAST_EVENT": return "a question about something that happened";
            case "CURRENT_EVENT": return "a question about what's happening now";
            case "FUTURE_EVENT": return "a question about what will happen";
            case "FUTURE": return "a question about the future";
            case "SELF": return "a question about you";
            case "IBIKO": return "a question about me";
            case "PERMISSION": return "asking leave, or if something's possible";
            case "REQUEST": return "a request";
            case "ADVICE": return "asking for advice";
            case "OPINION": return "asking my view";
            case "IDENTITY": return "a who-question";
            case "OWNER": return "a whose-question";
            case "CHOICE": return "a which-question";
            case "PLACE": return "a where-question";
            case "TIME": return "a when-question";
            case "COUNT": return "a how-many question";
            case "AMOUNT": return "a how-much question";
            case "DURATION": return "a how-long question";
            case "AGE": return "a how-old question";
            case "MEASURE": return "a question about a measure";
            case "PROCESS": return "a how-to question";
            case "MECHANISM": return "a how-does-it-work question";
            case "MANNER": return "a how-question";
            case "WELLBEING": return "asking how I am";
            case "COMPARE": return "a question comparing two things";
            case "YES_NO": return "a yes-or-no question";
            case "TELL": return "a request to explain";
            default: return "a question";
        }
    }
}
