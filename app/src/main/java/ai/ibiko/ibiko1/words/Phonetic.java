package ai.ibiko.ibiko1.words;

import java.util.Locale;

/**
 * A word as it sounds, kept as its consonant sounds: "phone" and "fone" are both FN, "knowledge" and "nolij" NLJ,
 * "school" and "skool" SKL, "believe" and "beleive" BLV. Spellings that sound alike share a key, so a word typed the way
 * it sounds finds the word it was meant to be, however far its letters are from it.
 * <p>
 * The letters are first made the sounds they stand for (ph is f, ck and hard c are k, soft c is s, kn at the start is
 * n, dg and soft g are j, tion is shun, silent gh goes), then the vowels go (the first sound is kept, as A when it is
 * one) and a sound said twice is said once. Pure Java.
 */
public final class Phonetic {
    private Phonetic() {
    }

    /** The word's consonant sounds; empty when it has no letters. */
    public static String key(String word) {
        if (word == null) {
            return "";
        }
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.isEmpty()) {
            return "";
        }
        // silent letters at the start: knife, gnome, pneumonia, wrong, psychology
        w = w.replaceFirst("^kn", "n").replaceFirst("^gn", "n").replaceFirst("^pn", "n").replaceFirst("^wr", "r")
                .replaceFirst("^ps", "s").replaceFirst("^wh", "w").replaceFirst("^x", "s");
        w = w.replaceAll("mb$", "m") // lamb, climb
                .replaceAll("(?<=[aeiou])gh(?![aeiou])", "") // night, though: gh unsaid
                .replaceAll("ough", "o")
                .replace("ph", "f")
                .replaceAll("tch", "ch")
                .replaceAll("sch", "sk")
                .replaceAll("dge", "j").replaceAll("dg", "j")
                .replaceAll("[ts]ion", "shn").replaceAll("cian", "shn").replaceAll("tia", "sha")
                .replace("ck", "k")
                .replace("qu", "kw").replace("q", "k")
                .replaceAll("c(?=[eiy])", "s").replace("c", "k")
                .replaceAll("g(?=[eiy])", "j")
                .replace("x", "ks")
                .replace("z", "s")
                .replaceAll("sh", "X").replaceAll("ch", "C").replaceAll("th", "0") // one sound each
                .replaceAll("(?<=[aeiou])[wh]", "") // w and h after a vowel are part of it: now, ah
                .replaceAll("h(?![aeiou])", ""); // h before a consonant is silent
        StringBuilder out = new StringBuilder();
        char first = w.charAt(0);
        boolean vowelFirst = "aeiouy".indexOf(first) >= 0;
        if (vowelFirst) {
            out.append('A');
        }
        char last = 0;
        for (int i = vowelFirst ? 1 : 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if ("aeiouy".indexOf(c) >= 0) {
                last = 0; // a vowel between two of the same sound keeps both: "papa" is PP
                continue;
            }
            if (c != last) {
                out.append(Character.toUpperCase(c));
            }
            last = c;
        }
        return out.toString();
    }

    /** The edit distance between two words (letters added, dropped, changed or swapped), stopping past {@code most}. */
    public static int distance(String a, String b, int most) {
        int n = a.length(), m = b.length();
        if (Math.abs(n - m) > most) {
            return most + 1;
        }
        int[] prev2 = new int[m + 1], prev = new int[m + 1], cur = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            int best = cur[0];
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, prev2[j - 2] + 1);
                }
                cur[j] = v;
                best = Math.min(best, v);
            }
            if (best > most) {
                return most + 1;
            }
            int[] t = prev2;
            prev2 = prev;
            prev = cur;
            cur = t;
        }
        return prev[m];
    }
}
