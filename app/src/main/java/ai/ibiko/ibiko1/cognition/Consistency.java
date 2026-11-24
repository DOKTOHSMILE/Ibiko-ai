package ai.ibiko.ibiko1.cognition;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Truth;

/**
 * Engine 2, contradiction and consistency (priority 2): conflicts are found before they reach the person. Each sentence
 * is read as a statement (who or what, the relation, the value); two statements about the same thing and relation are
 * compared by their numbers (with their units put on one scale: "5 km" and "5,000 m" agree), their dates, and whether
 * one denies what the other says. A conflict is real only when both speak of the same time: "the population was 2
 * million in 1990" and "it is 5 million today" don't contradict each other. A real conflict is never dropped silently:
 * it is resolved by the more trusted source, then the newer one, or else said. Pure Java.
 *
 * <pre>
 * C1  statement   subject · relation · value, from "X is/was/has/costs/weighs/lasts … V", negated or not
 * C2  value       the numbers on one scale (km/m/cm, kg/g, hours/minutes/seconds, thousand/million/billion)
 * C3  compare     same subject and relation: different value, a date apart, or one denies the other
 * C4  context     a different year, "formerly", "now", "was" against "is": different times, an apparent conflict
 * C5  resolve     the more trusted source stands ({@link Truth} ranks); same rank: the newer; else disclosed
 * </pre>
 */
public final class Consistency {
    private Consistency() {
    }

    public enum Kind { VALUE, NEGATION, DATE }

    /** One statement read from a sentence. */
    public static final class Statement {
        public final String sentence, subject, relation, value;
        public final boolean negated;
        public final Double number;
        public final String year, tense;

        Statement(String sentence, String subject, String relation, String value, boolean negated, Double number, String year, String tense) {
            this.sentence = sentence;
            this.subject = subject;
            this.relation = relation;
            this.value = value;
            this.negated = negated;
            this.number = number;
            this.year = year;
            this.tense = tense;
        }
    }

    /** A conflict between two statements: real, or only apparent (different times). */
    public static final class Conflict {
        public final Statement a, b;
        public final Kind kind;
        public final boolean real;
        public final String why;

        Conflict(Statement a, Statement b, Kind kind, boolean real, String why) {
            this.a = a;
            this.b = b;
            this.kind = kind;
            this.real = real;
            this.why = why;
        }

        /** The conflict in a line, as it is disclosed. */
        public String said() {
            return "“" + a.sentence.trim() + "” and “" + b.sentence.trim() + "” " + (real ? "disagree" : "only seem to disagree") + " (" + why + ")";
        }
    }

    private static final Pattern STATEMENT = Pattern.compile("(?i)^\\s*(?:the\\s+|a\\s+|an\\s+)?(.{2,60}?)\\s+"
            + "(was born in|was born on|died in|died on|was founded in|was built in|opened in|became|"
            + "is|are|was|were|has|have|had|costs?|cost|weighs?|weighed|measures?|lasts?|lasted|takes?|took|holds?|contains?)\\s+(not\\s+|never\\s+|no\\s+)?(.{1,80}?)\\s*[.!]?\\s*$");
    private static final Pattern YEAR = Pattern.compile("\\b(1[0-9]{3}|20[0-9]{2})\\b");
    private static final Pattern NUMBER = Pattern.compile("(-?\\d[\\d,]*(?:\\.\\d+)?)\\s*(%|percent|km|kilometres?|kilometers?|m|metres?|meters?|cm|mm|"
            + "kg|kilograms?|g|grams?|tonnes?|tons?|hours?|hrs?|minutes?|mins?|seconds?|secs?|days?|weeks?|years?|thousand|million|billion|"
            + "naira|dollars?|pounds?|euros?)?\\b", Pattern.CASE_INSENSITIVE);

    /** C1: the statements a text makes, one a sentence (questions and hedged sentences are not statements). */
    public static List<Statement> statements(String text) {
        List<Statement> out = new ArrayList<Statement>();
        for (String s : Truth.sentences(text == null ? "" : text)) {
            if (s.trim().endsWith("?")) {
                continue;
            }
            Matcher m = STATEMENT.matcher(s.trim());
            if (!m.matches()) {
                continue;
            }
            String subject = m.group(1).toLowerCase(Locale.ROOT).replaceAll("^(?:the|a|an)\\s+", "").replaceAll("[,;:]+$", "").trim();
            String relation = m.group(2).toLowerCase(Locale.ROOT).replaceAll("^(was|were|are)$", "is").replaceAll("^(have|had)$", "has")
                    .replaceAll("^(costs|cost)$", "cost").replaceAll("^(weighs|weighed)$", "weigh").replaceAll("^(lasts|lasted)$", "last")
                    .replaceAll("^(takes|took)$", "take").replaceAll("^(measures)$", "measure");
            String value = m.group(4).trim();
            Matcher y = YEAR.matcher(s);
            String year = y.find() ? y.group(1) : null;
            String tense = m.group(2).toLowerCase(Locale.ROOT).matches("was|were|had|weighed|lasted|took|became|was .*|died .*") ? "past"
                    : s.toLowerCase(Locale.ROOT).matches("(?s).*\\b(?:formerly|used to|once|previously|originally)\\b.*") ? "past" : "present";
            Double n = number(value);
            out.add(new Statement(s.trim(), subject, relation, value, m.group(3) != null, n, year, tense));
        }
        return out;
    }

    /** C2: the first number in a value, on one scale for its unit; null when it has none. */
    static Double number(String value) {
        Matcher m = NUMBER.matcher(value == null ? "" : value);
        if (!m.find()) {
            return null;
        }
        double v;
        try {
            v = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        String u = m.group(2) == null ? "" : m.group(2).toLowerCase(Locale.ROOT);
        if (u.matches("km|kilomet.*")) {
            v *= 1000;
        } else if (u.equals("cm")) {
            v /= 100;
        } else if (u.equals("mm")) {
            v /= 1000;
        } else if (u.matches("kg|kilogram.*")) {
            v *= 1000;
        } else if (u.matches("tonnes?|tons?")) {
            v *= 1_000_000;
        } else if (u.matches("hours?|hrs?")) {
            v *= 3600;
        } else if (u.matches("minutes?|mins?")) {
            v *= 60;
        } else if (u.matches("days?")) {
            v *= 86400;
        } else if (u.matches("weeks?")) {
            v *= 604800;
        } else if (u.equals("thousand")) {
            v *= 1e3;
        } else if (u.equals("million")) {
            v *= 1e6;
        } else if (u.equals("billion")) {
            v *= 1e9;
        }
        return v;
    }

    private static boolean sameThing(Statement a, Statement b) {
        return a.relation.equals(b.relation) && (a.subject.equals(b.subject) || a.subject.endsWith(" " + b.subject) || b.subject.endsWith(" " + a.subject));
    }

    /** C3–C4: whether two statements conflict, and whether the conflict is real; null when they don't. */
    public static Conflict compare(Statement a, Statement b) {
        if (!sameThing(a, b)) {
            return null;
        }
        boolean differentTimes = a.year != null && b.year != null && !a.year.equals(b.year) || !a.tense.equals(b.tense);
        String why;
        Kind kind;
        if (a.negated != b.negated && plain(a.value).equals(plain(b.value))) {
            kind = Kind.NEGATION;
            why = "one says it, the other denies it";
        } else if (a.number != null && b.number != null) {
            double x = a.number, y = b.number;
            if (Math.abs(x - y) <= 1e-6 * Math.max(1, Math.abs(x) + Math.abs(y))) {
                return null; // the same, on one scale ("5 km", "5,000 m")
            }
            if (a.relation.matches("was born .*|died .*|was founded .*|was built .*|opened .*") || isYear(a.value) && isYear(b.value)) {
                kind = Kind.DATE;
                why = "two different dates";
                differentTimes = false; // one event has one date
            } else {
                kind = Kind.VALUE;
                why = "different amounts";
            }
        } else if (a.negated == b.negated && !plain(a.value).equals(plain(b.value)) && a.relation.matches("was born .*|died .*|was founded .*|was built .*")) {
            kind = Kind.DATE;
            why = "two different answers for one event";
            differentTimes = false;
        } else {
            return null; // different words, not a contradiction that can be shown
        }
        if (differentTimes) {
            return new Conflict(a, b, kind, false, "they speak of different times");
        }
        return new Conflict(a, b, kind, true, why);
    }

    private static boolean isYear(String v) {
        return v != null && v.trim().matches("(?:in\\s+)?(1[0-9]{3}|20[0-9]{2})\\.?");
    }

    private static String plain(String v) {
        return v.toLowerCase(Locale.ROOT).replaceAll("^(?:a|an|the)\\s+", "").replaceAll("[^\\p{L}\\p{N} ]", "").trim();
    }

    /** C3: the conflicts inside one text (an answer that contradicts itself). */
    public static List<Conflict> within(String text) {
        List<Statement> st = statements(text);
        List<Conflict> out = new ArrayList<Conflict>();
        for (int i = 0; i < st.size(); i++) {
            for (int j = i + 1; j < st.size(); j++) {
                Conflict c = compare(st.get(i), st.get(j));
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /** C3: the conflicts between a text and what is already held (each known sentence). */
    public static List<Conflict> against(String text, List<String> known) {
        List<Conflict> out = new ArrayList<Conflict>();
        if (known == null || known.isEmpty()) {
            return out;
        }
        List<Statement> held = new ArrayList<Statement>();
        for (String k : known) {
            held.addAll(statements(k));
        }
        for (Statement s : statements(text)) {
            for (Statement h : held) {
                Conflict c = compare(s, h);
                if (c != null) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    /**
     * C5: which of two conflicting pieces of evidence stands: the more trusted source; with the same rank, the newer.
     * Null when neither can be preferred (same rank, same time): then the disagreement is said.
     */
    public static Evidence.Item resolve(Evidence.Item a, Evidence.Item b) {
        double ra = Evidence.reliability(a.source), rb = Evidence.reliability(b.source);
        if (ra != rb) {
            return ra > rb ? a : b;
        }
        if (a.at != b.at) {
            return a.at > b.at ? a : b;
        }
        return null;
    }
}
