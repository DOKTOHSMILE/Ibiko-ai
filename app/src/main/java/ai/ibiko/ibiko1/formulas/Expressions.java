package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Idioms and sayings ("me, myself and I", "break a leg", "no wahala"): their words don't add up to what they mean, so
 * each is kept whole with its meaning (knowledge/language/expressions.tsv, tools/build_expressions.py), and a note on
 * what it implies when it's played with. A saying is explained when the message asks what it means ("What does this
 * imply?"), when it's all that was said, or when the last message held it and this one asks "what does that mean?".
 * Pure Java.
 */
public final class Expressions {
    static final class Saying {
        final String phrase, words, meaning, note;

        Saying(String phrase, String meaning, String note) {
            this.phrase = phrase;
            this.words = plain(phrase);
            this.meaning = meaning;
            this.note = note;
        }
    }

    private final List<Saying> sayings = new ArrayList<Saying>();

    /** Asking what something means or implies. */
    private static final Pattern ASKS = Pattern.compile("(?i)\\b(?:what (?:does|do|did|is|'s) (?:this|that|it|the (?:saying|phrase|expression|idiom))"
            + "(?:\\s+\\w+)?\\s+(?:mean|means|imply|implies|implicate|implicates|signify|suggest|indicate|say about)|what does .{1,60} mean|"
            + "meaning of|what is meant by|explain (?:this|that|the saying|the phrase|the idiom)|what(?:'s| is) the meaning|"
            + "what(?:'s| is) (?:this|that) (?:saying|phrase|idiom|expression)|implication|what are you implying)\\b");

    public Expressions(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (!line.startsWith("#") && f.length >= 2 && !f[0].trim().isEmpty()) {
                    sayings.add(new Saying(f[0].trim(), f[1].trim(), f.length > 2 ? f[2].trim() : ""));
                }
            }
        } catch (IOException ignored) {
            // none: sayings are read word by word
        }
    }

    public int size() {
        return sayings.size();
    }

    /** Lower case, "&" as "and", marks gone, one space: "Me, myself & I!" is "me myself and i". */
    static String plain(String s) {
        return " " + s.toLowerCase(Locale.ROOT).replace('’', '\'').replace("&", " and ").replaceAll("[^\\p{L}\\p{N}' -]", " ")
                .replaceAll("\\s+", " ").trim() + " ";
    }

    /** The longest saying in a text, as whole words; null when none. */
    Saying find(String text) {
        String t = plain(text);
        Saying best = null;
        for (Saying s : sayings) {
            if (s.words.trim().contains(" ") && t.contains(s.words) && (best == null || s.words.length() > best.words.length())) {
                best = s;
            }
        }
        return best;
    }

    /**
     * The explanation of a saying in the message (or in the last one, when this asks "what does that mean?"); null when
     * there's none, or nothing asks about it.
     */
    public String explain(String message, String previous) {
        if (message == null || sayings.isEmpty()) {
            return null;
        }
        boolean asks = ASKS.matcher(message).find();
        Saying s = find(message);
        if (s != null) {
            int extra = plain(message).trim().split(" ").length - s.words.trim().split(" ").length;
            if (!asks && extra > 1) {
                return null; // a saying used in a sentence ("how far is Lagos"): not asked about
            }
        } else if (asks && previous != null && plain(message).trim().split(" ").length <= 8) {
            s = find(previous); // "what does that mean?" after the saying
        }
        if (s == null) {
            return null;
        }
        String shown = (Character.toUpperCase(s.phrase.charAt(0)) + s.phrase.substring(1)).replaceAll("\\bi\\b", "I");
        String meaning = s.meaning.replaceAll("\\.$", "");
        meaning = Character.toLowerCase(meaning.charAt(0)) + meaning.substring(1);
        return "“" + shown + "” is a saying: it means " + meaning + "." + (s.note.isEmpty() ? "" : "\n\n" + s.note);
    }
}
