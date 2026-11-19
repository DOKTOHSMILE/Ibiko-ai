package ai.ibiko.ibiko1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ibiko 1's encyclopedia, inside the app: the opening of every article of Simple English Wikipedia (CC BY-SA 4.0, see
 * {@code assets/knowledge/encyclopedia/LICENSE.txt}), in plain words, with the redirects as other names ("USA" for
 * "United States"). It answers "what is a volcano", "who was Nelson Mandela", "tell me about Lagos" offline, saying
 * where it read it. Split by title into {@link #SHARDS} small files ({@code tools/build_encyclopedia.py} makes them),
 * so one lookup reads one small file; the files last read are kept. Pure Java: the files come through {@link Plug#asset}.
 */
public final class Encyclopedia {
    /** How many files the articles are split into (as the build tool). */
    public static final int SHARDS = 512;
    static final String DIR = "knowledge/encyclopedia/";
    /** Shards kept in memory once read (the most recent). */
    static final int KEEP = 12;
    /** Redirects followed before giving up (a chain that loops is cut). */
    static final int HOPS = 3;

    /** One article: its title and its opening. */
    public static final class Article {
        public final String title, text;

        Article(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private final java.util.function.Function<String, InputStream> files;
    private final LinkedHashMap<Integer, Map<String, String[]>> cache = new LinkedHashMap<Integer, Map<String, String[]>>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Map<String, String[]>> eldest) {
            return size() > KEEP;
        }
    };

    /** Reads its files from the app's assets ({@link Plug#asset}). */
    public Encyclopedia() {
        this(path -> Plug.asset.apply(path));
    }

    /** Reads its files with {@code files} (a test gives its own). */
    public Encyclopedia(java.util.function.Function<String, InputStream> files) {
        this.files = files;
    }

    /** A title as the files key it: lower case, letters and digits, single spaces (as the build tool). */
    public static String key(String title) {
        String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
        t = t.replaceAll("[^\\p{L}\\p{N}_]+", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    static int shard(String key) {
        return (key.hashCode() & 0x7fffffff) % SHARDS;
    }

    /** The article for {@code name} (redirects followed), or null. */
    public synchronized Article get(String name) {
        String k = key(name);
        for (int hop = 0; hop <= HOPS && !k.isEmpty(); hop++) {
            String[] row = load(shard(k)).get(k);
            if (row == null) {
                return null;
            }
            if (!row[1].startsWith("=>")) {
                return new Article(row[0], row[1]);
            }
            k = key(row[1].substring(2)); // another name for it
        }
        return null;
    }

    /**
     * The article for what a question names, trying the forms a name comes in: as written, without "a/an/the", the
     * singular of a plural, and each word capitalised the same (keys are not case-sensitive). Null when none.
     */
    public Article find(String subject) {
        if (subject == null || subject.trim().isEmpty()) {
            return null;
        }
        for (String form : forms(subject)) {
            Article a = get(form);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    /** The ways a subject may be titled. */
    static List<String> forms(String subject) {
        List<String> out = new ArrayList<String>();
        String s = subject.trim().replaceAll("[?.!]+$", "").trim();
        add(out, s);
        String bare = s.replaceFirst("(?i)^(a|an|the)\\s+", "");
        add(out, bare);
        String low = bare.toLowerCase(Locale.ROOT);
        if (low.endsWith("ies") && low.length() > 4) {
            add(out, bare.substring(0, bare.length() - 3) + "y");
        } else if (low.matches(".*(ches|shes|sses|xes|zes|oes)$")) {
            add(out, bare.substring(0, bare.length() - 2)); // churches, volcanoes
            add(out, bare.substring(0, bare.length() - 1));
        } else if (low.endsWith("s") && !low.endsWith("ss") && low.length() > 3) {
            add(out, bare.substring(0, bare.length() - 1));
        }
        return out;
    }

    private static void add(List<String> out, String s) {
        if (!s.isEmpty() && !out.contains(s)) {
            out.add(s);
        }
    }

    private static final Pattern ASKS = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:(?:can|could) you\\s+)?(?:"
            + "what(?:'s| is| are| was| were)|who(?:'s| is| are| was| were)|where(?:'s| is| was)|"
            + "tell me (?:about|more about)|explain|describe|what do you know about|"
            + "give me (?:information|info|facts) (?:on|about)|information (?:on|about)|facts about)\\s+(.+?)[?.!]*\\s*$");

    /** What a question asks to be explained ("what is a volcano" → "a volcano"), or null when it isn't that kind. */
    public static String asked(String question) {
        if (question == null) {
            return null;
        }
        Matcher m = ASKS.matcher(question.trim());
        if (!m.matches()) {
            return null;
        }
        String s = m.group(1).trim();
        // "what is the capital of X" and other questions about a part of a thing are for the facts, not an article
        if (s.isEmpty() || s.split("\\s+").length > 7 || s.matches("(?i).*\\b(of|in|for|between|than|from|to)\\b.*")
                && !s.matches("(?i)^(the )?(united|republic|kingdom|state|states|bank|church|house|university|city|isle|island|bay|gulf|sea|lake|river|mount|battle|war|treaty|museum|order|book|history|theory|law|age|statue|tower|cup|league|prince|king|queen|duke|museum)\\b.*")) {
            return null;
        }
        return s;
    }

    /**
     * The answer to {@code question} from the encyclopedia: the article's opening, a little shortened, and where it
     * comes from; null when the question doesn't ask to explain something it has.
     */
    public String answer(String question) {
        String s = asked(question);
        Article a = s == null ? null : find(s);
        if (a == null) {
            return null;
        }
        return a.text + "\n(From Simple English Wikipedia, “" + a.title + "”.)";
    }

    /** One shard: key → {title, text or "=>Target"}. Empty when the file isn't there. */
    private Map<String, String[]> load(int n) {
        Map<String, String[]> got = cache.get(n);
        if (got != null) {
            return got;
        }
        got = new java.util.HashMap<String, String[]>();
        InputStream in = files.apply(DIR + String.format(Locale.ROOT, "%03d.tsv", n));
        if (in != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16)) {
                String line;
                while ((line = r.readLine()) != null) {
                    int a = line.indexOf('\t'), b = a < 0 ? -1 : line.indexOf('\t', a + 1);
                    if (b > a && a > 0) {
                        String k = line.substring(0, a);
                        if (!got.containsKey(k) || !line.startsWith("=>", b + 1)) {
                            got.put(k, new String[]{line.substring(a + 1, b), line.substring(b + 1)}); // an article wins over a redirect
                        }
                    }
                }
            } catch (IOException ignored) {
                // what was read stands
            }
        }
        cache.put(n, got);
        return got;
    }
}
