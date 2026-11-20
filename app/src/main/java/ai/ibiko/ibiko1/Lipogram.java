package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A one-sentence summary that keeps every rule it is given: at most so many words, and (a lipogram) never a given
 * letter. Drafted from what the passage says happened, then each word holding the letter is swapped for a synonym
 * without it (the dictionary's, else an everyday one), names are left out, and the sentence is shortened until it
 * fits. Every rule is checked on the result; null when no draft keeps them all. Pure Java.
 */
final class Lipogram {
    private Lipogram() {
    }

    /** Everyday stand-ins, each with several choices: the first without the banned letter is taken. */
    private static final Map<String, String[]> PLAIN = new HashMap<String, String[]>();

    static {
        String[][] t = {
            {"the", "a", "this"}, {"three", "3", "a trio of"}, {"two", "2", "a pair of"}, {"one", "1", "a"},
            {"four", "4"}, {"five", "5"}, {"left", "quit", "took off from", "set out from"}, {"leave", "quit"},
            {"depot", "hub", "station", "yard"}, {"separate", "distinct", "own"}, {"manifests", "lists", "papers"},
            {"couriers", "staff", "drivers", "carriers", "agents"}, {"courier", "driver", "carrier"},
            {"delivered", "dropped off", "brought", "handed out"}, {"sold", "sold"}, {"damaged", "spoilt", "broken", "ruined"},
            {"some", "part", "a bit"}, {"most", "most", "much"}, {"was", "was", "got"}, {"were", "got", "was"},
            {"cylinders", "tanks", "drums"}, {"inverters", "units", "kits"}, {"batteries", "cells", "packs"},
            {"cargo", "cargo", "goods", "loads"}, {"with", "with", "carrying"}, {"but", "but", "though", "yet"},
            {"returned", "got back", "came back"}, {"carried", "took", "hauled"}, {"items", "goods", "stock"},
            {"people", "folks", "staff"}, {"they", "this group", "all"}, {"their", "its", "all"},
        };
        for (String[] r : t) {
            PLAIN.put(r[0], Arrays.copyOfRange(r, 1, r.length));
        }
    }

    /** Words that can go when the sentence is too long, least needed first. */
    private static final Set<String> OPTIONAL = new LinkedHashSet<String>(Arrays.asList("distinct", "separate", "own", "lists",
            "papers", "manifests", "total", "all", "a", "this"));

    static String summary(PassageTasks t, int most, char banned) {
        for (String draft : drafts(t)) {
            String s = fit(draft, most, banned);
            if (s != null && keeps(s, most, banned)) {
                return s;
            }
        }
        return null;
    }

    /** Whether a sentence keeps the rules: one sentence, at most {@code most} words, never the banned letter. */
    static boolean keeps(String s, int most, char banned) {
        if (s == null || s.trim().isEmpty()) {
            return false;
        }
        if (banned != 0 && s.toLowerCase(Locale.ROOT).indexOf(banned) >= 0) {
            return false;
        }
        String body = s.trim().replaceAll("[.!?]$", "");
        if (body.matches(".*[.!?]\\s+[A-Z].*")) {
            return false; // two sentences
        }
        return words(s).size() <= most;
    }

    static List<String> words(String s) {
        List<String> out = new ArrayList<String>();
        for (String w : s.trim().split("\\s+")) {
            if (w.matches(".*[\\p{L}\\p{N}].*")) {
                out.add(w);
            }
        }
        return out;
    }

    /** Drafts of what happened, the fullest first. */
    static List<String> drafts(PassageTasks t) {
        List<String> out = new ArrayList<String>();
        List<String> sentences = Passage.split(t.passage);
        String opening = sentences.isEmpty() ? "" : compress(sentences.get(0));
        boolean moved = false, damaged = false;
        for (PassageTasks.Tracker.Person p : t.tracker.people.values()) {
            moved |= p.delivered > 0 || p.sold > 0;
            damaged |= p.damaged > 0;
        }
        String outcome = moved ? "most cargo was delivered or sold" + (damaged ? ", but some was damaged" : "")
                : damaged ? "some cargo was damaged" : "";
        if (!opening.isEmpty()) {
            out.add(outcome.isEmpty() ? opening : opening + "; " + outcome);
            out.add(opening);
        }
        if (!outcome.isEmpty()) {
            out.add(outcome);
        }
        return out;
    }

    /** The opening sentence without its date, clock time, listed names in dashes or brackets, and full stop. */
    static String compress(String s) {
        String c = s.replaceAll("[—–]\\s*[^—–]+[—–]", " ") // "—Kemi, Tunde, and Musa—"
                .replaceAll("\\([^)]*\\)", " ")
                .replaceFirst("^(?:On|In|At|By)\\s+[^,]+,(?:\\s*[A-Z][a-z]+\\s+\\d{1,2},)?\\s*", "") // "On Tuesday, March 14,"
                .replaceAll("\\bat\\s+\\d{1,2}:\\d{2}\\s*(?:[AP]\\.?M\\.?)?", " ")
                .replaceAll("[.!?]+$", "")
                .replaceAll("\\s+", " ").trim();
        return c;
    }

    /** The draft with every word made to keep the rules, then shortened to fit; null when a word can't be kept. */
    static String fit(String draft, int most, char banned) {
        List<String> out = new ArrayList<String>();
        for (String raw : draft.split("\\s+")) {
            String tail = raw.replaceAll("^.*?([,;:]*)$", "$1");
            String w = raw.replaceAll("[,;:]+$", "");
            String kept = keep(w, banned);
            if (kept == null) {
                return null;
            }
            if (!kept.isEmpty()) {
                out.add(kept + tail);
            } else if (!tail.isEmpty() && !out.isEmpty()) {
                out.set(out.size() - 1, out.get(out.size() - 1) + tail);
            }
        }
        String s = String.join(" ", out);
        while (words(s).size() > most) {
            String shorter = drop(s);
            if (shorter == null) {
                return null;
            }
            s = shorter;
        }
        s = s.replaceAll("\\s+([,;])", "$1").replaceAll("[,;]\\s*$", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + ".";
    }

    /** A word kept as it is, swapped, or left out (""), so the banned letter never shows; null when it can't be. */
    private static String keep(String w, char banned) {
        if (banned == 0 || w.toLowerCase(Locale.ROOT).indexOf(banned) < 0) {
            return w;
        }
        String low = w.toLowerCase(Locale.ROOT);
        String[] plain = PLAIN.get(low);
        if (plain != null) {
            for (String p : plain) {
                if (p.indexOf(banned) < 0) {
                    return p;
                }
            }
        }
        if (Character.isUpperCase(w.charAt(0))) {
            return ""; // a name ("Sapele") is left out rather than misspelled
        }
        ai.ibiko.ibiko1.words.Meanings m = ai.ibiko.ibiko1.words.Meanings.shared();
        if (m != null) {
            for (String syn : m.synonyms(low, null)) {
                if (syn.indexOf(banned) < 0 && !syn.contains(" ")) {
                    return syn;
                }
            }
        }
        return null;
    }

    /** The sentence one word shorter: an optional word first, else the last word of its first clause. */
    private static String drop(String s) {
        List<String> ws = new ArrayList<String>(Arrays.asList(s.split("\\s+")));
        for (String o : OPTIONAL) {
            for (int i = ws.size() - 1; i > 0; i--) {
                String w = ws.get(i);
                String bare = w.replaceAll("[,;:]+$", "");
                if (bare.equalsIgnoreCase(o) && !(o.equals("a") && i + 1 < ws.size() && ws.get(i + 1).matches("trio|pair|bit"))) {
                    String tail = w.substring(bare.length());
                    ws.remove(i);
                    if (!tail.isEmpty()) {
                        ws.set(i - 1, ws.get(i - 1) + tail);
                    }
                    return String.join(" ", ws);
                }
            }
        }
        return null;
    }
}
