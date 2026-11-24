package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finding a fact by what it is, not by its name: "What is the highest mountain in the world?" names no article, but the
 * encyclopedia says "Mount Everest is the highest mountain on Earth". The records it names were read out at build time
 * (knowledge/records.tsv, tools/build_records.py), keyed by the superlative, the thing and where; a question is read the
 * same way and looked up. Pure Java.
 *
 * <pre>
 * F1  read     "the highest mountain in the world", "Africa's longest river", "the most populous city in Nigeria"
 * F2  look up  the record for that place; for the world, one said with no place only for things with one world
 *              meaning (an ocean, a planet: not a city, where "the largest city" is the country's)
 * F3  infer    no record for the place: the world's record holder, when its own article puts it there ("the longest
 *              river on Earth" and "a river in Africa": the longest in Africa too), said with the reasoning
 * </pre>
 */
public final class Records {
    private static final class Row {
        final String sup, thing, scope, title, sentence, where;

        Row(String[] f) {
            sup = f[0];
            thing = f[1];
            scope = f[2];
            title = f[3];
            sentence = f[4];
            where = f.length > 5 ? f[5] : "";
        }
    }

    private final List<Row> rows = new ArrayList<Row>();

    public Records(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (!line.startsWith("#") && f.length >= 5) {
                    rows.add(new Row(f));
                }
            }
        } catch (IOException ignored) {
            // none: the question goes on
        }
    }

    public int size() {
        return rows.size();
    }

    private static final String[][] SUPER = {{"highest", "high"}, {"tallest", "tall"}, {"longest", "long"}, {"largest", "large"},
        {"biggest", "large"}, {"deepest", "deep"}, {"smallest", "small"}, {"oldest", "old"}, {"hottest", "hot"}, {"coldest", "cold"},
        {"fastest", "fast"}, {"heaviest", "heavy"}, {"shortest", "short"}, {"busiest", "busy"}, {"wettest", "wet"}, {"driest", "dry"},
        {"lowest", "low"}, {"most populous", "populous"}, {"most populated", "populous"}, {"most spoken", "spoken"}, {"richest", "rich"}};
    private static final Set<String> WORLD = new HashSet<String>(Arrays.asList("world", "earth", "the world", "the earth", "planet",
            "the planet", "world's", "earth's"));
    /** Things with one world meaning: "the largest ocean", said with no place, is the world's. */
    private static final Set<String> GLOBAL = new HashSet<String>(Arrays.asList("ocean", "sea", "continent", "planet", "desert", "island",
            "lake", "animal", "bird", "fish", "mammal", "reptile", "insect", "star", "moon", "tree", "flower", "organ", "bone", "muscle",
            "cell", "country", "river", "mountain", "volcano", "waterfall", "glacier", "canyon"));

    private static String sups() {
        List<String> w = new ArrayList<String>();
        for (String[] s : SUPER) {
            w.add(Pattern.quote(s[0]));
        }
        w.sort((a, b) -> b.length() - a.length());
        return String.join("|", w);
    }

    private static final Pattern ASKED = Pattern.compile("(?i)^\\s*(?:what|which|where|who)(?:'s| is| are| was)?\\s+(?:the\\s+)?"
            + "(?:((?:world|earth|africa|asia|europe|america|nigeria|\\p{Lu}\\p{L}+)'s)\\s+)?(" + sups() + ")\\s+([a-z][a-z-]*(?: [a-z][a-z-]*){0,2}?)"
            + "(?:\\s+(?:in|on|of)\\s+(?:the\\s+)?([\\p{L}][\\p{L} .'-]{1,40}?))?\\s*[?.!]*\\s*$");

    /** F1: what a question asks for: {superlative, thing, place ("world" when none)}, or null. */
    public static String[] asked(String question) {
        Matcher m = question == null ? null : ASKED.matcher(question.trim());
        if (m == null || !m.matches()) {
            return null;
        }
        String sup = null;
        for (String[] s : SUPER) {
            if (s[0].equalsIgnoreCase(m.group(2))) {
                sup = s[1];
            }
        }
        String thing = singular(m.group(3).trim().toLowerCase(Locale.ROOT));
        if (sup.equals("tall") && thing.matches("mountain|peak|volcano")) {
            sup = "high"; // the tallest mountain is the highest
        }
        String place = m.group(4) != null ? m.group(4).trim() : m.group(1) != null ? m.group(1).replace("'s", "") : "world";
        if (WORLD.contains(place.toLowerCase(Locale.ROOT))) {
            place = "world";
        }
        return new String[]{sup, thing, place};
    }

    static String singular(String w) {
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.matches(".*(ches|shes|sses|xes)$")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }
        return w;
    }

    /** F2–F3: the answer, with its source sentence (and the reasoning when inferred); null when it has no record. */
    public String answer(String[] q) {
        String sup = q[0], thing = q[1], place = q[2];
        Row exact = null, world = null, unsaid = null;
        for (Row r : rows) {
            if (!r.sup.equals(sup) || !r.thing.equals(thing)) {
                continue;
            }
            if (r.scope.equalsIgnoreCase(place)) {
                exact = r;
            } else if (r.scope.equals("world")) {
                world = r;
            } else if (r.scope.equals("-")) {
                unsaid = r;
            }
        }
        if (exact != null) {
            return exact.title + ". " + clean(exact.sentence);
        }
        if (place.equals("world") && unsaid != null && GLOBAL.contains(thing)) {
            return unsaid.title + ". " + clean(unsaid.sentence);
        }
        if (!place.equals("world") && world != null && !world.where.isEmpty() && world.where.toLowerCase(Locale.ROOT).contains(place.toLowerCase(Locale.ROOT))) {
            // the world's record holder is in that place: it's the record there too
            return world.title + ". " + clean(world.sentence) + " It's in " + world.where + ", so it's the " + superlative(q) + " " + thing
                    + " in " + place + " too.";
        }
        return null;
    }

    private static String superlative(String[] q) {
        for (String[] s : SUPER) {
            if (s[1].equals(q[0])) {
                return s[0];
            }
        }
        return q[0];
    }

    private static String clean(String s) {
        return s.replaceAll("\\s*\\([^()]*\\)", "").replaceAll("\\s+([,.;])", "$1").replaceAll("\\s{2,}", " ").trim();
    }
}
