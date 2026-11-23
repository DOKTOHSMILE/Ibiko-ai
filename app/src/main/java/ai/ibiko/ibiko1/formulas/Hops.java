package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Questions inside questions: "what is the capital of the country where Mount Kilimanjaro is?" is two questions, the
 * inner one first (the country where Kilimanjaro is: Tanzania), then the outer one about its answer (the capital of
 * Tanzania). Each inner phrase is worked out from the facts of the thing it names, replaced by its answer, and the
 * question goes on, up to three steps; each step is said. A phrase that is the whole question ("what is the capital of
 * Tanzania?") is left to be answered as it is. Pure Java over a fact lookup (thing, relation) → value.
 *
 * <pre>
 * H1  the country where X is · the country X is in · the country of X       X's location → its country
 * H2  the city / town / place where X was born · X's birthplace             X's birth place → the town
 * H3  the country where X was born · the country X was born in              X's birth place → the country
 * H4  the capital of X · X's capital                                         X's capital
 * H5  X's wife / husband / spouse · the wife / husband / spouse of X         X's spouse
 * H6  the founder of X · X's founder · the person who founded X             X's founder (the first named)
 * H7  the largest / biggest city in X                                        X's largest city
 * H8  the president / leader / king / prime minister of X                    X's leader
 * </pre>
 */
public final class Hops {
    private Hops() {
    }

    /** The question with its inner questions answered, and each step as said. */
    public static final class Done {
        public final String text;
        public final List<String> steps;

        Done(String text, List<String> steps) {
            this.text = text;
            this.steps = steps;
        }
    }

    /** A name: words with capitals, "of", "the", "de", "and" between them ("Mount Kilimanjaro", "Bank of America"). */
    private static final String X = "((?:the\\s+)?\\p{Lu}[\\p{L}.'’-]*(?:\\s+(?:(?:of|de|da|del|la|le|von|van|and|&|the)\\s+)?\\p{Lu}[\\p{L}.'’-]*)*)";

    private static final class Hop {
        final Pattern p;
        final String relation, part, said;

        Hop(String re, String relation, String part, String said) {
            this.p = Pattern.compile("(?i:" + re.replace("X", ")" + X + "(?i:") + ")");
            this.relation = relation;
            this.part = part;
            this.said = said;
        }
    }

    private static final List<Hop> HOPS = new ArrayList<Hop>();

    static {
        HOPS.add(new Hop("the country (?:where|in which) X (?:was|is|were|are) born", "birth_place", "country", "where %s was born"));
        HOPS.add(new Hop("the country X (?:was|is|were|are) born in", "birth_place", "country", "where %s was born"));
        HOPS.add(new Hop("the (?:city|town|place) (?:where|in which) X (?:was|is|were|are) born", "birth_place", "town", "where %s was born"));
        HOPS.add(new Hop("the (?:city|town|place) X (?:was|is|were|are) born in", "birth_place", "town", "where %s was born"));
        HOPS.add(new Hop("the birthplace of X", "birth_place", "town", "where %s was born"));
        HOPS.add(new Hop("X's birthplace", "birth_place", "town", "where %s was born"));
        HOPS.add(new Hop("the country (?:where|in which) X (?:is|are|was|were|lies|stands)(?: located| found)?", "location", "country", "the country %s is in"));
        HOPS.add(new Hop("the country X (?:is|are|was|were) (?:located |found )?in", "location", "country", "the country %s is in"));
        HOPS.add(new Hop("the country of X", "location", "country", "the country %s is in"));
        HOPS.add(new Hop("the capital(?: city)? of X", "capital", "first", "the capital of %s"));
        HOPS.add(new Hop("X's capital(?: city)?", "capital", "first", "the capital of %s"));
        HOPS.add(new Hop("X's (?:wife|husband|spouse)", "spouse", "first", "%s's spouse"));
        HOPS.add(new Hop("the (?:wife|husband|spouse) of X", "spouse", "first", "%s's spouse"));
        HOPS.add(new Hop("the (?:founder|creator) of X", "founder", "first", "the founder of %s"));
        HOPS.add(new Hop("X's (?:founder|creator)", "founder", "first", "the founder of %s"));
        HOPS.add(new Hop("the (?:person|man|woman) who (?:founded|started|created) X", "founder", "first", "the founder of %s"));
        HOPS.add(new Hop("the (?:largest|biggest) city (?:in|of) X", "largest_city", "first", "the largest city in %s"));
        HOPS.add(new Hop("the (?:current )?(?:president|leader|king|queen|prime minister|head of state|ruler) of X", "leader", "first", "the leader of %s"));
    }

    /** A question that only asks the phrase itself ("what is the capital of Tanzania?") is the question, not a step. */
    private static final Pattern ITSELF = Pattern.compile("(?i)^\\s*(?:please\\s+)?(?:(?:can|could) you\\s+)?(?:tell me\\s+)?"
            + "(?:what|who|which|where)(?:'s|’s|\\s+(?:is|was|are|were))\\s+$");

    /**
     * H1–H8: the question with its inner questions answered from {@code fact} (a thing and a relation give the value as
     * stored, or null), innermost first, at most three; null when it has none to answer.
     */
    public static Done resolve(String question, BiFunction<String, String, String> fact) {
        if (question == null || fact == null) {
            return null;
        }
        String q = question.trim();
        List<String> steps = new ArrayList<String>();
        for (int round = 0; round < 3; round++) {
            Matcher best = null;
            Hop bestHop = null;
            for (Hop h : HOPS) {
                Matcher m = h.p.matcher(q);
                while (m.find()) {
                    String before = q.substring(0, m.start()), after = q.substring(m.end());
                    if (ITSELF.matcher(before).matches() && after.matches("\\s*[?.!]*\\s*")) {
                        continue; // the whole question: answered as it is
                    }
                    // the innermost (latest) phrase first; at one place, the longer reading
                    if (best == null || m.start() > best.start() || m.start() == best.start() && m.end() > best.end()) {
                        best = h.p.matcher(q);
                        best.find(m.start());
                        bestHop = h;
                    }
                }
            }
            if (best == null) {
                break;
            }
            String thing = best.group(1).replaceFirst("(?i)^the\\s+", "").trim();
            String raw = fact.apply(thing, bestHop.relation);
            if (raw == null && bestHop.relation.equals("location")) {
                raw = fact.apply(thing, "country"); // a thing may say its country plainly
            }
            String value = raw == null ? null : part(raw, bestHop.part);
            if (value == null || value.isEmpty()) {
                break; // not known: the question goes on as it is, to be looked up or searched for
            }
            steps.add("Working out " + String.format(bestHop.said, thing) + ": " + value);
            q = q.substring(0, best.start()) + value + q.substring(best.end());
        }
        return steps.isEmpty() ? null : new Done(q, steps);
    }

    private static final Pattern DIRECTION = Pattern.compile("(?i)^(?:(?:north|south|east|west|central|northern|southern|eastern|western|"
            + "north-?east|north-?west|south-?east|south-?west|upper|lower|mid)\\s+)+");

    /** The part of a stored value asked for: the first item, the town (first place), or the country (last place). */
    static String part(String raw, String part) {
        String v = raw.trim();
        // "Abeokuta, Nigeria Protectorate (now Ogun State, Nigeria)": what it is now, when the text says
        Matcher now = Pattern.compile("(?i)\\(now ([^()]+)\\)").matcher(v);
        String current = now.find() ? now.group(1).trim() : null;
        v = v.replaceAll("\\s*\\([^()]*\\)", "").trim();
        String[] pieces = v.split("\\s*[,;|]\\s*");
        if (pieces.length == 0 || pieces[0].isEmpty()) {
            return null;
        }
        switch (part) {
            case "town":
                return clean(pieces[0]);
            case "country": {
                String last = current != null ? current.split("\\s*,\\s*")[current.split("\\s*,\\s*").length - 1] : pieces[pieces.length - 1];
                last = DIRECTION.matcher(clean(last)).replaceFirst("");
                if (last.matches("(?i)U\\.?S\\.?A?\\.?|United States of America")) {
                    return "the United States";
                }
                if (last.matches("(?i)U\\.?K\\.?")) {
                    return "the United Kingdom";
                }
                return last;
            }
            default:
                return clean(pieces[0]);
        }
    }

    private static String clean(String s) {
        return s.replaceAll("[\\[\\]\"]", "").replaceAll("\\s+", " ").trim();
    }
}
