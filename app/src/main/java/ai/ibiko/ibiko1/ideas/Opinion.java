package ai.ibiko.ibiko1.ideas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Probability;

/**
 * Ibiko's own view of something ("what do you think about remote work?"), reasoned rather than repeated: what its
 * sources say for and against, counted source by source; a probability that it is good on balance; what could come of
 * it; and what would change its mind. The reasons are said in a few words of its own, never a source's sentence.
 * Pure Java.
 *
 * <pre>
 * O1 for/against  each sentence about the thing read for what it says in favour (helps, improves, saves…) or against
 *                 (risk, costly, harms…); "not" turns it round
 * O2 reasons      the few words after the word that judges ("improves *learning outcomes*"), counted once a source
 * O3 balance      P(good on balance) = (sources for + 1) / (sources for + sources against + 2), said in words (P8)
 * O4 foresight    what the sources say it leads to ("can lead to", "will likely"), the commonest first
 * O5 mind change  the strongest reason against, as what would change the view
 * </pre>
 */
public final class Opinion {
    private Opinion() {
    }

    private static final Pattern GOOD = Pattern.compile("(?i)\\b(benefits?|advantages?|improves?|improving|helps?|boosts?|saves?|increases? (?:access|productivity|"
            + "efficiency|income|growth)|effective|efficient|cheaper|safer|healthier|popular|convenient|flexib\\w+|creates? jobs|"
            + "opportunit\\w+|reduces? (?:costs?|risks?|stress|pollution|emissions)|protects?|empowers?|valuable|useful)\\b\\s*(?:to |for |in |of |the |your )?"
            + "([\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,3})?");
    private static final Pattern BAD = Pattern.compile("(?i)\\b(risks?|harms?|harmful|dangers?|dangerous|problems?|costly|expensive|concerns?|criticis\\w+|"
            + "disadvantages?|threats?|loss(?:es)?|pollut\\w+|unsafe|addict\\w+|fraud|scams?|declines?|fails?|failures?|controvers\\w+|"
            + "isolation|burnout|inequalit\\w+|side effects?|abuse|misuse|volatil\\w+)\\b\\s*(?:to |for |in |of |the |your |from |with )?"
            + "([\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,3})?");
    private static final Pattern LEADS = Pattern.compile("(?i)\\b(?:can|could|may|might|will likely|is likely to|tends? to)?\\s*(?:lead to|result in|cause|bring)\\s+"
            + "([\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,4})");
    private static final Pattern NOT = Pattern.compile("(?i)\\b(?:not|no|never|isn't|aren't|doesn't|don't|without|hardly)\\b");
    private static final Set<String> STOP = new HashSet<String>(java.util.Arrays.asList("the", "a", "an", "and", "or", "of", "to", "in",
            "is", "are", "it", "this", "that", "be", "can", "for", "with", "as", "on", "by", "their", "its", "more", "many", "some", "also"));

    /** The view formed, or null when the sources say nothing either way. */
    public static String form(String subject, Corpus c) {
        if (c == null || c.size() == 0 || subject == null) {
            return null;
        }
        String s = subject.toLowerCase(Locale.ROOT);
        List<String> about = new ArrayList<String>();
        for (String w : s.split("\\s+")) {
            if (w.length() > 2 && !STOP.contains(w)) {
                about.add(Ideation.singular(w));
            }
        }
        Map<String, Integer> pros = new LinkedHashMap<String, Integer>(), cons = new LinkedHashMap<String, Integer>(), ahead = new LinkedHashMap<String, Integer>();
        int forSources = 0, againstSources = 0, read = 0;
        for (Corpus.Source src : c.sources) {
            Set<String> p = new HashSet<String>(), n = new HashSet<String>(), a = new HashSet<String>();
            for (String sentence : src.sentences) {
                String low = sentence.toLowerCase(Locale.ROOT);
                boolean on = about.isEmpty();
                for (String w : about) {
                    on |= low.contains(w);
                }
                if (!on) {
                    continue;
                }
                read++;
                boolean negated = NOT.matcher(low).find();
                Matcher g = GOOD.matcher(sentence);
                while (g.find()) {
                    (negated ? n : p).add(reason(g.group(1), g.group(2), about));
                }
                Matcher b = BAD.matcher(sentence);
                while (b.find()) {
                    (negated ? p : n).add(reason(b.group(1), b.group(2), about));
                }
                Matcher l = LEADS.matcher(sentence);
                while (l.find()) {
                    String r = phrase(l.group(1), about);
                    if (r != null) {
                        a.add(r);
                    }
                }
            }
            p.remove(null);
            n.remove(null);
            for (String r : p) {
                pros.merge(r, 1, Integer::sum);
            }
            for (String r : n) {
                cons.merge(r, 1, Integer::sum);
            }
            for (String r : a) {
                ahead.merge(r, 1, Integer::sum);
            }
            forSources += p.isEmpty() ? 0 : 1;
            againstSources += n.isEmpty() ? 0 : 1;
        }
        if (forSources + againstSources == 0) {
            return null;
        }
        double good = (forSources + 1.0) / (forSources + againstSources + 2.0); // O3
        List<String> forR = top(pros, 3), againstR = top(cons, 3), next = top(ahead, 2);
        String name = subject.trim();
        StringBuilder out = new StringBuilder();
        String lean = good >= 0.6 ? "more good than bad" : good <= 0.4 ? "more trouble than it's worth" : "a real trade-off, with no clear winner";
        out.append("My view: ").append(name).append(" is ").append(lean).append(". I'd put it at about ").append(Math.round(good * 100))
                .append("% that it's good on balance (").append(Probability.words(good)).append("), weighing what ").append(c.size())
                .append(c.size() == 1 ? " source says" : " sources say").append(".");
        if (!forR.isEmpty()) {
            out.append("\n\nFor it: ").append(String.join("; ", forR)).append(".");
        }
        if (!againstR.isEmpty()) {
            out.append("\nAgainst it: ").append(String.join("; ", againstR)).append(".");
        }
        if (!next.isEmpty()) {
            out.append("\n\nLooking ahead, it could lead to ").append(String.join(", and to ", next)).append(".");
        }
        if (!againstR.isEmpty()) {
            out.append("\nWhat would change my mind: clear evidence that ").append(againstR.get(0)).append(" outweighs ")
                    .append(forR.isEmpty() ? "the gains" : forR.get(0)).append(".");
        }
        out.append(read < 3 ? "\n\nI've read little on it, so hold this loosely." : "");
        return out.toString();
    }

    private static String reason(String cue, String rest, List<String> about) {
        String r = phrase(rest, about);
        String c = cue.toLowerCase(Locale.ROOT);
        return r == null ? c : c + " " + r;
    }

    /** The meaningful few words of a reason, without the thing's own name and small words at the ends; null when none. */
    private static String phrase(String rest, List<String> about) {
        if (rest == null) {
            return null;
        }
        // the reason ends where another begins ("saves time and boosts…": "saves time")
        String r = rest.toLowerCase(Locale.ROOT).split("\\s+(?:and|but|or|which|that|because|while|so)\\b")[0];
        String whole = String.join(" ", about);
        if (!whole.isEmpty() && (r + " ").startsWith(whole + " ")) {
            r = r.substring(whole.length()).trim(); // the thing's own name, said again: not a reason
        }
        List<String> w = new ArrayList<String>();
        for (String x : r.split("\\s+")) {
            if (!x.isEmpty()) {
                w.add(x);
            }
        }
        while (!w.isEmpty() && STOP.contains(w.get(w.size() - 1))) {
            w.remove(w.size() - 1);
        }
        while (!w.isEmpty() && STOP.contains(w.get(0))) {
            w.remove(0);
        }
        return w.isEmpty() ? null : String.join(" ", w);
    }

    private static List<String> top(Map<String, Integer> m, int most) {
        List<Map.Entry<String, Integer>> e = new ArrayList<Map.Entry<String, Integer>>(m.entrySet());
        e.sort((a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> x : e) {
            if (out.size() < most) {
                out.add(x.getKey() + (x.getValue() > 1 ? " (" + x.getValue() + " sources)" : ""));
            }
        }
        return out;
    }
}
