package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a phone keyboard learns about the person, for {@link TypoFix}. A keyboard picks the word w that makes
 * P(w | typed) largest, and P(w | typed) ∝ P(typed | w) · P(w | context). TypoFix already has P(typed | w), the cost of
 * the slip (a neighbouring key, two letters swapped), and P(w) from the dictionary's word order. This adds the rest:
 * <ul>
 *   <li>the person's own words: a word they often type is likelier ({@link #uses});</li>
 *   <li>the word before: "the" after "on" is likelier than "thy" (a bigram of their own messages, {@link #follows});</li>
 *   <li>their verdict on past fixes: one they left alone was right and is trusted more ({@link #accepted}); one they
 *   undid is never made again ({@link #rejected}).</li>
 * </ul>
 * Each is a factor on TypoFix's score, so it can settle a close call ("wierd": weird or wired) that was left alone
 * before. Kept on the device, bounded, most recent kept. Pure Java.
 */
public final class TypingModel {
    static final int MOST_WORDS = 5000, MOST_PAIRS = 20000, MOST_FIXES = 2000;
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:'[a-z]+)?");

    private final File file;
    private final LinkedHashMap<String, Integer> words = lru(), pairs = lru(), fixes = lru();

    public TypingModel(File file) {
        this.file = file;
        load();
    }

    private static LinkedHashMap<String, Integer> lru() {
        return new LinkedHashMap<String, Integer>(64, 0.75f, true);
    }

    /** How often the person has typed {@code word}. */
    public synchronized int uses(String word) {
        Integer n = words.get(word);
        return n == null ? 0 : n;
    }

    /** How often {@code word} came right after {@code before} in what they typed. */
    public synchronized int follows(String before, String word) {
        if (before == null) {
            return 0;
        }
        Integer n = pairs.get(before + ' ' + word);
        return n == null ? 0 : n;
    }

    /** How often the fix typo → word was left alone. */
    public synchronized int accepted(String typo, String word) {
        Integer n = fixes.get(typo + ' ' + word);
        return n == null || n < 0 ? 0 : n;
    }

    /** Whether they undid typo → word. */
    public synchronized boolean rejected(String typo, String word) {
        Integer n = fixes.get(typo + ' ' + word);
        return n != null && n < 0;
    }

    /** Whether they undid a fix of {@code typo}: it is their spelling, and no word replaces it. */
    public synchronized boolean undone(String typo) {
        return fixes.containsKey(typo + " *");
    }

    /**
     * The factor on TypoFix's score for the fix typo → word after {@code before} (lower is likelier; 1 is no change).
     * Each of the person's signals divides it: by 1 + ln(1 + uses), by 1 + 2·ln(1 + follows), by 1 + accepted. A
     * rejected fix is infinitely unlikely.
     */
    public synchronized double factor(String typo, String word, String before) {
        if (rejected(typo, word)) {
            return Double.POSITIVE_INFINITY;
        }
        return 1.0 / ((1 + Math.log1p(uses(word))) * (1 + 2 * Math.log1p(follows(before, word))) * (1 + accepted(typo, word)));
    }

    /** A message as the brain reads it (after fixes): its words and each pair of neighbours are counted. */
    public synchronized void learn(String text) {
        if (text == null || TypoFix.leaveAlone(text)) {
            return;
        }
        for (String sentence : text.split("[.!?\\n]+")) {
            String before = null;
            Matcher m = WORD.matcher(sentence);
            while (m.find()) {
                String w = m.group().toLowerCase(Locale.ROOT);
                bump(words, w, 1);
                if (before != null) {
                    bump(pairs, before + ' ' + w, 1);
                }
                before = w;
            }
        }
        trim();
        save();
    }

    /** The person left these fixes alone: they were right. */
    public synchronized void keptFixes(List<String[]> made) {
        for (String[] f : made) {
            String k = f[0].toLowerCase(Locale.ROOT) + ' ' + f[1].toLowerCase(Locale.ROOT);
            Integer n = fixes.get(k);
            if (n == null || n >= 0) {
                bump(fixes, k, 1);
            }
        }
        trim();
        save();
    }

    /** The person undid these fixes: never again. */
    public synchronized void undid(List<String[]> made) {
        for (String[] f : made) {
            String typo = f[0].toLowerCase(Locale.ROOT);
            fixes.put(typo + ' ' + f[1].toLowerCase(Locale.ROOT), -1);
            fixes.put(typo + " *", -1); // the word itself is theirs: not changed into any other word
        }
        save();
    }

    /** The word before position {@code start} in the same sentence, lower case, or null. */
    public static String wordBefore(String text, int start) {
        int i = start - 1;
        while (i >= 0 && text.charAt(i) == ' ') {
            i--;
        }
        int end = i + 1;
        while (i >= 0 && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '\'')) {
            i--;
        }
        return end > i + 1 ? text.substring(i + 1, end).toLowerCase(Locale.ROOT) : null;
    }

    private static void bump(Map<String, Integer> m, String k, int by) {
        Integer n = m.get(k);
        m.put(k, (n == null ? 0 : n) + by);
    }

    private void trim() {
        cut(words, MOST_WORDS);
        cut(pairs, MOST_PAIRS);
        cut(fixes, MOST_FIXES);
    }

    private static void cut(LinkedHashMap<String, Integer> m, int most) {
        while (m.size() > most) {
            m.remove(m.keySet().iterator().next());
        }
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.split("\t");
                if (p.length != 3 || !p[2].matches("-?\\d+")) {
                    continue;
                }
                int n = Integer.parseInt(p[2]);
                Map<String, Integer> m = "w".equals(p[0]) ? words : "p".equals(p[0]) ? pairs : "f".equals(p[0]) ? fixes : null;
                if (m != null) {
                    m.put(p[1], n);
                }
            }
        } catch (IOException ignored) {
            // starts empty
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        write(b, "w", words);
        write(b, "p", pairs);
        write(b, "f", fixes);
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            ai.ibiko.learn.SafeFile.write(file, b.toString()); // whole or not at all
        } catch (IOException ignored) {
            // kept in memory for this run
        }
    }

    private static void write(StringBuilder b, String kind, Map<String, Integer> m) {
        for (Map.Entry<String, Integer> e : new ArrayList<Map.Entry<String, Integer>>(m.entrySet())) {
            b.append(kind).append('\t').append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
    }
}
