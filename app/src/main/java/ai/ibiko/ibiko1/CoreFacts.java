package ai.ibiko.ibiko1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The app's core facts (knowledge/base_knowledge.json: "water boils at 100 degrees Celsius", CPR, malaria, the planets),
 * the same checked pack the other brains are given, read by Ibiko v1 too, so it doesn't look up on the web, or say it
 * doesn't know, what the app already ships. A fact answers a question that names all its keys ("boil" and "water" in "At
 * what temperature does water boil?"); a one-key fact only a question that is that thing alone ("What is DNA?"). Pure Java.
 */
public final class CoreFacts {
    static final class Fact {
        final List<String> keys;
        final String answer;

        Fact(List<String> keys, String answer) {
            this.keys = keys;
            this.answer = answer;
        }
    }

    private final List<Fact> facts = new ArrayList<Fact>();

    /** Asking words around a one-key fact's thing: "what is", "tell me about", "explain". */
    private static final Set<String> ASKING = new HashSet<String>(Arrays.asList("what", "who", "is", "are", "was", "were", "a", "an", "the",
            "tell", "me", "about", "explain", "define", "describe", "please", "do", "does", "you", "know", "s", "whats", "how", "many",
            "there", "can", "could", "what's", "give", "some", "info", "information", "on"));

    public CoreFacts(InputStream json) {
        if (json == null) {
            return;
        }
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = json.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            Map<String, Object> root = Json.obj(Json.parse(new String(b.toByteArray(), StandardCharsets.UTF_8)));
            for (Object o : Json.list(root == null ? null : root.get("facts"))) {
                Map<String, Object> f = Json.obj(o);
                List<String> keys = f == null ? null : Json.strings(f.get("keys"));
                String answer = f == null ? null : Json.str(f.get("answer"), null);
                if (keys != null && !keys.isEmpty() && answer != null && !answer.trim().isEmpty()) {
                    List<String> low = new ArrayList<String>();
                    for (String k : keys) {
                        low.add(k.toLowerCase(Locale.ROOT).trim());
                    }
                    facts.add(new Fact(low, answer.trim()));
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // none: the other knowledge answers
        } finally {
            try {
                json.close();
            } catch (IOException ignored) {
                // closed
            }
        }
    }

    public int size() {
        return facts.size();
    }

    /** Whether a word of the question is the key, or a form of it ("boils", "boiling" for "boil"; "planets" for "planet"). */
    static boolean fits(String word, String key) {
        if (word.equals(key)) {
            return true;
        }
        if (key.contains(" ")) {
            return false;
        }
        return key.length() >= 3 && (word.startsWith(key) && word.length() - key.length() <= 3
                || key.startsWith(word) && key.length() - word.length() <= 1 && word.length() >= 4);
    }

    /** The core fact that answers the question; null when none does. */
    public String answer(String question) {
        if (question == null || facts.isEmpty()) {
            return null;
        }
        String q = question.toLowerCase(Locale.ROOT).replaceAll("[’']s\\b", "").replaceAll("[^\\p{L}\\p{Nd} ]", " ").trim();
        if (q.isEmpty()) {
            return null;
        }
        List<String> words = Arrays.asList(q.split("\\s+"));
        Fact best = null;
        int bestScore = 0;
        for (Fact f : facts) {
            int matched = 0;
            for (String k : f.keys) {
                boolean found = k.contains(" ") ? (" " + q + " ").contains(" " + k + " ") : false;
                for (String w : words) {
                    found |= fits(w, k);
                }
                if (found) {
                    matched++;
                }
            }
            if (matched < f.keys.size()) {
                continue;
            }
            if (f.keys.size() == 1) {
                // one key: only "what is DNA?", not "write Java code" or "DNA test near me"
                for (String w : words) {
                    if (!ASKING.contains(w) && !fits(w, f.keys.get(0))) {
                        matched = 0;
                        break;
                    }
                }
                if (matched == 0) {
                    continue;
                }
            }
            // more keys is a closer fit; the question's other words found in the answer break a tie ("in Fahrenheit")
            int score = f.keys.size() * 10;
            String a = f.answer.toLowerCase(Locale.ROOT);
            for (String w : words) {
                if (w.length() > 3 && !ASKING.contains(w) && a.contains(w)) {
                    score++;
                }
            }
            if (score > bestScore) {
                best = f;
                bestScore = score;
            }
        }
        return best == null ? null : best.answer;
    }
}
