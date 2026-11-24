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
 * The last answer, said again the way it's asked for: shorter, in one sentence, as points, more simply, or with an
 * example. Its sentences are read first and the ones that say again what an earlier one said are dropped (an answer
 * built from several sources often says the same thing twice); then:
 *
 * <pre>
 * S1  shorter   its core: the first sentence of a short answer, the first two of a long one
 * S2  one       the sentence that shares the most with the others (the one the rest are about), brackets taken out
 * S3  points    one point a sentence; a single long sentence split at its clauses
 * S4  simpler   brackets out, long sentences split, hard words for plain ones (knowledge/language/plain_words.tsv)
 * S5  example   a sentence from the subject's own text that gives one ("for example", "such as"…), not said yet
 * </pre>
 *
 * Pure Java; the plain words are knowledge, a newer file on the phone replacing the shipped one.
 */
public final class Reshape {
    /** What's asked for. */
    public enum Ask {
        SHORTER("shorter"), ONE("in one sentence"), POINTS("as points"), SIMPLER("more simply"), EXAMPLE("with an example");

        public final String said;

        Ask(String said) {
            this.said = said;
        }
    }

    private final Map<String, String> plain = new LinkedHashMap<String, String>();

    public Reshape(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        List<String[]> pairs = new ArrayList<String[]>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                if (!line.startsWith("#") && f.length == 2) {
                    pairs.add(f);
                }
            }
        } catch (IOException ignored) {
            // no plain words: "more simply" still drops brackets and splits long sentences
        }
        pairs.sort((a, b) -> b[0].length() - a[0].length()); // "the process by which" before "process"
        for (String[] p : pairs) {
            plain.put(p[0].toLowerCase(Locale.ROOT), p[1]);
        }
    }

    private static final String END = "(?:,? please)?(?: thanks?(?: you)?)?\\s*[.!?]*$";
    private static final Pattern SHORTER = Pattern.compile("(?i)^\\s*(?:can you |could you |please )?(?:make it |say it |keep it |put it )?"
            + "(?:shorter|briefer|more briefly|briefly|in short|tl;?dr|too long|less detail|summari[sz]e (?:it|that|this)|the short version|in brief)" + END);
    private static final Pattern ONE = Pattern.compile("(?i)^\\s*(?:can you |could you |please )?(?:say it|sum it up|put it|explain it|tell me|give it to me|summari[sz]e it)?"
            + "\\s*in (?:one|a single|1) (?:sentence|line)" + END);
    private static final Pattern POINTS = Pattern.compile("(?i)^\\s*(?:can you |could you |please )?(?:put it |say it |give it |list it |write it )?"
            + "(?:in|as|into|with|use) (?:bullet ?points?|bullets|a list|points|a bulleted list)" + END
            + "|^\\s*(?:list (?:it|them|the points)(?: out)?|break it down(?: into points)?|bullet points?)" + END);
    private static final Pattern SIMPLER = Pattern.compile("(?i)^\\s*(?:can you |could you |please )?(?:(?:explain|say|put|tell) (?:it|that|this)(?: to me)? "
            + "(?:more )?(?:simply|simpler|plainly|in (?:plain|simple|easy|easier) (?:words|english|terms|language))|simpler|more simply|in simple terms|in plain english"
            + "|(?:explain it )?like i'?m (?:five|5|a child|a kid)|eli5|i don'?t (?:understand|get it)|that'?s too (?:technical|complicated|hard)"
            + "|(?:make it|say it) easier(?: to understand)?)" + END);
    private static final Pattern EXAMPLE = Pattern.compile("(?i)^\\s*(?:can you |could you |please )?(?:give me |show me |what'?s |what is |have you got )?"
            + "(?:an |one |some )?examples?(?: of (?:it|that|this))?" + END + "|^\\s*(?:for example|for instance|like what|such as)\\s*\\?*$");

    /** What a message asks for of the last answer, or null when it asks for something else. */
    public static Ask asked(String message) {
        String m = message == null ? "" : message.trim();
        return SHORTER.matcher(m).matches() ? Ask.SHORTER : ONE.matcher(m).matches() ? Ask.ONE : POINTS.matcher(m).matches() ? Ask.POINTS
                : SIMPLER.matcher(m).matches() ? Ask.SIMPLER : EXAMPLE.matcher(m).matches() ? Ask.EXAMPLE : null;
    }

    // ------------------------------------------------------------------------------------------------- reading it

    private static final Set<String> SMALL = new HashSet<String>(Arrays.asList("the", "a", "an", "of", "and", "or", "to", "in", "on", "is",
            "are", "was", "were", "it", "its", "this", "that", "these", "those", "by", "for", "with", "as", "at", "from", "be", "which", "who",
            "their", "they", "them", "has", "have", "had", "can", "into", "also", "such", "used", "process"));

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")) {
            if (w.length() > 2 && !SMALL.contains(w)) {
                out.add(w.length() > 4 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w);
            }
        }
        return out;
    }

    /** The answer's sentences, without the ones that say again what an earlier one said. */
    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<String>();
        List<Set<String>> seen = new ArrayList<Set<String>>();
        for (String s : text.replaceAll("(?m)^\\s*[•*-]\\s*", "").split("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])|\\n+")) {
            String t = s.trim();
            if (t.length() < 3) {
                continue;
            }
            Set<String> w = words(t);
            boolean again = false;
            for (Set<String> before : seen) {
                Set<String> both = new HashSet<String>(w);
                both.retainAll(before);
                if (!w.isEmpty() && both.size() >= Math.max(2, Math.min(w.size(), before.size()) / 2)) {
                    again = true; // most of what it says was said already
                    break;
                }
            }
            if (!again) {
                out.add(t);
                seen.add(w);
            }
        }
        return out;
    }

    private static String unbracket(String s) {
        return s.replaceAll("\\s*\\([^()]*\\)", "").replaceAll("\\s+([,.;:])", "$1").replaceAll("\\s{2,}", " ").trim();
    }

    // ------------------------------------------------------------------------------------------------- saying it again

    /** S1–S4: the answer said again as asked (EXAMPLE needs the subject's text: see {@link #example}); null: nothing to do. */
    public String again(String answer, Ask ask) {
        List<String> ss = answer == null || answer.contains("```") ? new ArrayList<String>() : sentences(answer);
        if (ss.isEmpty()) {
            return null;
        }
        switch (ask) {
            case SHORTER: {
                if (ss.size() == 1) {
                    String one = unbracket(ss.get(0));
                    String cut = one.replaceFirst("[,;:] .*$", ".");
                    return cut.length() < one.length() && cut.split("\\s+").length >= 5 ? cut : one;
                }
                return unbracket(String.join(" ", ss.subList(0, ss.size() <= 3 ? 1 : 2))); // a short answer: its core; a long one: two
            }
            case ONE:
                return unbracket(central(ss));
            case POINTS: {
                List<String> points = new ArrayList<String>();
                for (String s : ss) {
                    if (ss.size() == 1) {
                        for (String c : s.split(";\\s+|,\\s+(?=(?:and |but |which |so )?\\p{Ll})")) {
                            points.add(c.trim());
                        }
                    } else {
                        points.add(s);
                    }
                }
                StringBuilder b = new StringBuilder();
                for (String p : points) {
                    String q = p.replaceFirst("^(?:and|but|which|so)\\s+", "").replaceAll("[.;,]+$", "");
                    b.append(b.length() == 0 ? "" : "\n").append("• ").append(Character.toUpperCase(q.charAt(0))).append(q.substring(1)).append('.');
                }
                return b.toString();
            }
            case SIMPLER: {
                List<String> out = new ArrayList<String>();
                for (String s : ss.subList(0, Math.min(2, ss.size()))) {
                    // split where a new statement starts ("; it…", ", and oxygen is…"), never inside one (", which is used…")
                    for (String part : unbracket(s).split(";\\s+|,\\s+(?:and|so)\\s+(?=\\p{Ll}+ (?:is|are|was|were|has|have|can|will)\\b)")) {
                        String p = plainer(part.trim()).replaceAll("[.;,]+$", "");
                        if (!p.isEmpty()) {
                            out.add(Character.toUpperCase(p.charAt(0)) + p.substring(1) + ".");
                        }
                    }
                }
                return String.join(" ", out);
            }
            default:
                return null;
        }
    }

    /** S2: the sentence the others are most about: the most words shared with the rest, the earlier on a tie. */
    private static String central(List<String> ss) {
        String best = ss.get(0);
        int top = -1;
        for (int i = 0; i < ss.size(); i++) {
            Set<String> w = words(ss.get(i));
            int shared = 0;
            for (int j = 0; j < ss.size(); j++) {
                if (j != i) {
                    Set<String> both = new HashSet<String>(w);
                    both.retainAll(words(ss.get(j)));
                    shared += both.size();
                }
            }
            if (shared > top) {
                top = shared;
                best = ss.get(i);
            }
        }
        return best;
    }

    /** S4: hard words for plain ones, longest first, keeping a capital where there was one. */
    String plainer(String s) {
        String out = s;
        for (Map.Entry<String, String> e : plain.entrySet()) {
            Matcher m = Pattern.compile("(?i)(?<![\\p{L}-])" + Pattern.quote(e.getKey()) + "(?![\\p{L}-])").matcher(out);
            StringBuffer b = new StringBuffer();
            while (m.find()) {
                String v = e.getValue();
                if (Character.isUpperCase(m.group().charAt(0))) {
                    v = Character.toUpperCase(v.charAt(0)) + v.substring(1);
                }
                m.appendReplacement(b, Matcher.quoteReplacement(v));
            }
            m.appendTail(b);
            out = b.toString();
        }
        return out;
    }

    private static final Pattern GIVES_ONE = Pattern.compile("(?i)\\b(?:for example|for instance|such as|e\\.g\\.|including|an example)\\b");

    /** S5: a sentence of the subject's own text that gives an example, not said already; null when it has none. */
    public static String example(String text, String said) {
        if (text == null) {
            return null;
        }
        Set<String> already = said == null ? new HashSet<String>() : words(said);
        for (String s : text.split("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])")) {
            String t = s.trim();
            if (t.length() < 30 || t.length() > 320 || !GIVES_ONE.matcher(t).find()) {
                continue;
            }
            Set<String> w = words(t);
            Set<String> both = new HashSet<String>(w);
            both.retainAll(already);
            if (both.size() < w.size() * 0.8) {
                return unbracket(t);
            }
        }
        return null;
    }
}
