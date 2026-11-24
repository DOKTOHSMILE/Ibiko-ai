package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to phrase a web search for the most results. A search engine matches words, not sentences: "Can you please tell
 * me who wrote Things Fall Apart?" is searched as "Things Fall Apart author". The asking words are dropped, the
 * question's shape is said as the thing and what is wanted of it, words that mean "now" bring this year, and words that
 * carry no meaning are left out. When a search finds nothing it is tried broader: the subject alone, then the question
 * as asked. The rules are knowledge (knowledge/language/search_phrasing.tsv, tools/build_search_phrasing.py): a line
 * added there teaches a better way to search. Pure Java.
 */
public final class SearchPhrase {
    private final List<Pattern> drop = new ArrayList<Pattern>();
    private final List<Pattern> shapes = new ArrayList<Pattern>();
    private final List<String> words = new ArrayList<String>();
    private final List<Pattern> fresh = new ArrayList<Pattern>();
    private final Set<String> stop = new HashSet<String>();

    /** Words kept in a search though they're short: they change what is meant. */
    private static final Set<String> MEANING = new HashSet<String>(Arrays.asList("not", "no", "without", "vs", "versus", "best", "worst",
            "how", "why"));
    private static final Pattern QUOTED = Pattern.compile("\"[^\"]+\"|“[^”]+”");
    private static final Pattern YEAR = Pattern.compile("\\b(?:19|20)\\d\\d\\b");

    public SearchPhrase(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (line.startsWith("#") || f.length < 3) {
                    continue;
                }
                switch (f[0]) {
                    case "DROP":
                        drop.add(Pattern.compile("(?i)^(?:" + f[1] + ")"));
                        break;
                    case "SHAPE":
                        shapes.add(Pattern.compile("(?i)^(?:" + f[1] + ")$"));
                        words.add(f[2]);
                        break;
                    case "FRESH":
                        fresh.add(Pattern.compile("(?i)\\b" + Pattern.quote(f[1]) + "\\b"));
                        break;
                    case "STOP":
                        stop.addAll(Arrays.asList(f[1].toLowerCase(Locale.ROOT).split("\\s+")));
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException ignored) {
            // none: questions are searched as asked
        }
    }

    private static volatile SearchPhrase shared;

    /** The rules shipped with the app (read once, when they could be read). */
    public static SearchPhrase get() {
        SearchPhrase s = shared;
        if (s == null) {
            InputStream in = null;
            try {
                in = ai.ibiko.ibiko1.TextCode.open(path -> ai.ibiko.ibiko1.Plug.asset.apply(path), "knowledge/language/search_phrasing.tsv");
            } catch (RuntimeException ignored) {
                // none shipped here
            }
            s = new SearchPhrase(in);
            if (s.size() > 0) {
                shared = s;
            }
        }
        return s;
    }

    public int size() {
        return drop.size() + shapes.size() + fresh.size() + stop.size();
    }

    /** The question without its asking words ("can you please tell me", "hey Ibiko") or end marks. */
    public String bare(String question) {
        String q = question == null ? "" : question.replaceAll("\\s+", " ").trim();
        for (int round = 0; round < 4; round++) {
            String was = q;
            for (Pattern p : drop) {
                q = p.matcher(q).replaceFirst("").trim();
            }
            if (q.equals(was)) {
                break;
            }
        }
        return q.replaceAll("[?!.\\s]+$", "").trim();
    }

    /** The search words for a question: the most results for what it asks. */
    public String words(String question, int year) {
        String q = bare(question);
        if (q.isEmpty()) {
            return question == null ? "" : question.trim();
        }
        String out = null;
        for (int i = 0; i < shapes.size() && out == null; i++) {
            Matcher m = shapes.get(i).matcher(q);
            if (m.matches()) {
                String w = words.get(i);
                for (int g = m.groupCount(); g >= 1; g--) {
                    // "the Eiffel Tower" is searched as "Eiffel Tower"; "The Lion King", written with its capital, stays whole
                    String caught = m.group(g) == null ? "" : m.group(g).trim().replaceFirst("^(?:the|a|an)\\s+", "");
                    w = w.replace("$" + g, caught);
                }
                out = w.replaceAll("\\s+", " ").trim();
            }
        }
        if (out == null || out.isEmpty()) {
            out = keywords(q);
        }
        if (out.isEmpty()) {
            out = q;
        }
        return withYear(question, out, year);
    }

    /** The words that mean something: quoted phrases kept whole, the rest without words a search ignores. */
    public String keywords(String text) {
        List<String> kept = new ArrayList<String>();
        Matcher qm = QUOTED.matcher(text);
        StringBuilder rest = new StringBuilder();
        int at = 0;
        while (qm.find()) {
            kept.add(qm.group());
            rest.append(text, at, qm.start()).append(' ');
            at = qm.end();
        }
        rest.append(text.substring(at));
        for (String w : rest.toString().split("\\s+")) {
            String bare = w.replaceAll("^[^\\p{L}\\p{N}\"#+]+|[^\\p{L}\\p{N}#+]+$", "");
            if (bare.isEmpty()) {
                continue;
            }
            String low = bare.toLowerCase(Locale.ROOT);
            if (MEANING.contains(low) || !stop.contains(low)) {
                kept.add(bare);
            }
        }
        // "how"/"why" alone at the end mean nothing: kept only before what they ask about
        while (!kept.isEmpty() && kept.get(kept.size() - 1).matches("(?i)how|why")) {
            kept.remove(kept.size() - 1);
        }
        return String.join(" ", kept.size() > 10 ? kept.subList(0, 10) : kept);
    }

    private String withYear(String question, String out, int year) {
        if (year <= 0 || YEAR.matcher(out).find()) {
            return out;
        }
        for (Pattern f : fresh) {
            if (f.matcher(question).find()) {
                // "now" and "today" are dropped from the search words: a page doesn't say them, it says the year ("latest" stays: news pages say it)
                String w = out.replaceAll("(?i)\\b(?:current|currently|today|right now|now|this year|recently|recent|at the moment|these days|so far)\\b", " ")
                        .replaceAll("\\s+", " ").trim();
                return (w.isEmpty() ? out : w) + " " + year;
            }
        }
        return out;
    }

    /** The subject alone: a name in capitals ("Things Fall Apart", "Lagos"), else the search words' first three. */
    public String core(String question, String searched) {
        String q = bare(question);
        Matcher m = Pattern.compile("(?:\\b\\p{Lu}[\\p{L}\\p{N}'.-]*(?:\\s+(?:of|the|and|de|la|von)?\\s*\\p{Lu}[\\p{L}\\p{N}'.-]*)*)").matcher(q);
        String best = null;
        while (m.find()) {
            String s = m.group().trim();
            if (m.start() == 0 && !s.contains(" ")) {
                continue; // the sentence's first word, capitalised for being first
            }
            if (best == null || s.length() > best.length()) {
                best = s;
            }
        }
        if (best != null) {
            return best;
        }
        String[] w = keywords(searched == null ? q : searched).split("\\s+");
        return w.length <= 2 ? null : String.join(" ", Arrays.asList(w).subList(0, 3));
    }

    /** The searches to try, best first: the search words, then the subject alone, then the question as asked. */
    public List<String> tries(String question, int year) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        String best = words(question, year);
        add(out, best);
        add(out, core(question, best));
        String asked = bare(question);
        add(out, asked.isEmpty() ? question : asked);
        return new ArrayList<String>(out.values());
    }

    private static void add(Map<String, String> out, String q) {
        if (q != null && !q.trim().isEmpty()) {
            out.putIfAbsent(q.trim().toLowerCase(Locale.ROOT), q.trim());
        }
    }
}
