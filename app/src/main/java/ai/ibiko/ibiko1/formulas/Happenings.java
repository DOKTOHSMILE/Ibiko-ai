package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reading what happened and when, from plain text: the understanding formula for events. A sentence is read for a
 * date ("1 October 1960", "November 9, 1989", "July 1969", "1957", "3000 BC"), the action it dates (the words that
 * say what happened: "gained independence", "was taken down", "became the first humans to land on the moon"), and
 * who or what it happened to: the sentence's own subject, or, when that is "it", "the country" or "they", what the
 * text is about. Spans ("from 1961 to 1989", "began on … and ended on …") give a start and an end. Nothing is looked
 * up: the facts come from the text read, so whatever Ibiko reads (its encyclopedia, a page, the person's own words)
 * teaches it dates. Pure Java.
 *
 * <pre>
 * H1  date      every date in the sentence, as precise as it is written
 * H2  action    the verb phrase the date belongs to (before "on/in DATE", or after "On DATE,")
 * H3  subject   the noun phrase before the action; a pronoun or "the country" is the topic
 * H4  span      "from A to B" and "… began on A and ended on B": a start and an end
 * </pre>
 */
public final class Happenings {
    private Happenings() {
    }

    /** A date as precise as the text gives it: month and day 0 when not written; BC years negative. */
    public static final class When {
        public final int y, m, d;
        public final String said;

        public When(int y, int m, int d, String said) {
            this.y = y;
            this.m = m;
            this.d = d;
            this.said = said;
        }

        public boolean full() {
            return m > 0 && d > 0;
        }

        /** Before {@code o}, by what both say (the same year with nothing finer is not before). */
        public boolean before(When o) {
            if (y != o.y) {
                return y < o.y;
            }
            if (m == 0 || o.m == 0) {
                return false;
            }
            if (m != o.m) {
                return m < o.m;
            }
            return d != 0 && o.d != 0 && d < o.d;
        }

        /** "on 16 November 1930", "in July 1969", "in 1876". */
        public String on() {
            return (full() ? "on " : "in ") + said;
        }

        /** Whole years from this to {@code b}: exact when both dates are full, from the years alone otherwise. */
        public int yearsTo(When b) {
            int n = b.y - y - (y < 0 && b.y > 0 ? 1 : 0); // there was no year 0
            if (full() && b.full() && (b.m < m || b.m == m && b.d < d)) {
                n--;
            }
            return n;
        }
    }

    /** One thing that happened: to what, what, when, and the sentence it was read from. */
    public static final class Happening {
        public final String subject, action, sentence;
        public final When when;

        public Happening(String subject, String action, When when, String sentence) {
            this.subject = subject;
            this.action = action;
            this.when = when;
            this.sentence = sentence;
        }

        /** "Nigeria gained independence", for saying it. */
        public String clause() {
            return (subject + " " + action).trim();
        }

        @Override
        public String toString() {
            return "(" + subject + " | " + action + " | " + when.said + ")";
        }
    }

    static final String MONTH = "january|february|march|april|may|june|july|august|september|october|november|december"
            + "|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec";
    private static final List<String> MONTHS = Arrays.asList("january", "february", "march", "april", "may", "june", "july",
            "august", "september", "october", "november", "december");
    /** A date in any of the ways it is written: day month year, month day year, month year, year (BC). */
    static final Pattern DATE = Pattern.compile("(?i)\\b(?:(\\d{1,2})(?:st|nd|rd|th)?\\s+(" + MONTH + ")\\.?,?\\s+(\\d{3,4})"
            + "|(" + MONTH + ")\\.?\\s+(\\d{1,2})(?:st|nd|rd|th)?,?\\s+(\\d{3,4})"
            + "|(" + MONTH + ")\\.?\\s+(\\d{3,4})"
            + "|(\\d{1,4})\\s*(BC|BCE|AD|CE)"
            + "|(1\\d{3}|20\\d{2}))\\b");

    /** The date at the start of {@code m} (a {@link #DATE} match). */
    static When when(Matcher m) {
        int y, mo = 0, d = 0;
        if (m.group(1) != null) {
            d = Integer.parseInt(m.group(1));
            mo = month(m.group(2));
            y = Integer.parseInt(m.group(3));
        } else if (m.group(4) != null) {
            mo = month(m.group(4));
            d = Integer.parseInt(m.group(5));
            y = Integer.parseInt(m.group(6));
        } else if (m.group(7) != null) {
            mo = month(m.group(7));
            y = Integer.parseInt(m.group(8));
        } else if (m.group(9) != null) {
            y = Integer.parseInt(m.group(9)) * (m.group(10).toUpperCase(Locale.US).startsWith("B") ? -1 : 1);
        } else {
            y = Integer.parseInt(m.group(11));
        }
        if (d > 31 || mo == 0 && d > 0) {
            d = 0;
        }
        String said = d > 0 ? d + " " + cap(MONTHS.get(mo - 1)) + " " + y : mo > 0 ? cap(MONTHS.get(mo - 1)) + " " + y
                : y < 0 ? -y + " BC" : String.valueOf(y);
        return new When(y, mo, d, said);
    }

    /** A date read from a fact's value ("16 November 1930", "1876", "3000 BC"), or null. */
    public static When date(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = DATE.matcher(text);
        return m.find() ? when(m) : null;
    }

    private static int month(String s) {
        String w = s.toLowerCase(Locale.US);
        for (int i = 0; i < MONTHS.size(); i++) {
            if (MONTHS.get(i).startsWith(w.length() >= 3 ? w.substring(0, 3) : w)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Words that stand for what the text is about ("it", "the country"): the topic is meant. */
    private static final Pattern STANDS_FOR_TOPIC = Pattern.compile("(?i)^(it|they|he|she|this|the (country|city|state|nation|company|war"
            + "|group|band|book|film|wall|building|river|island|team|party|organisation|organization|republic|kingdom|empire|town|"
            + "region|ship|mission|flight|event|treaty|movement|church|university|school|people))$");
    /** The verb a clause turns on: a past or present verb, or a helper and its verb. */
    private static final Pattern VERB = Pattern.compile("(?i)\\b(was|were|is|are|has been|had been|have been|became|become|began|begun|"
            + "ended|fell|won|lost|gained|got|declared|signed|founded|formed|built|opened|closed|launched|landed|died|born|started|"
            + "finished|elected|crowned|joined|took|held|reached|discovered|invented|created|established|introduced|"
            + "released|published|merged|split|collapsed|attacked|invaded|freed|abolished|adopted|renamed|moved|"
            + "(?-i:[a-z]\\w+ed))\\b"); // a lower-case -ed word: "United States" is not a verb

    /** What happened, read from {@code text} (about {@code topic}, which "it" and "the country" stand for). */
    public static List<Happening> read(String text, String topic) {
        List<Happening> out = new ArrayList<Happening>();
        if (text == null) {
            return out;
        }
        for (String sentence : text.split("(?<=[.!?])\\s+(?=[A-Z\"“(])")) {
            readSentence(sentence.trim(), topic, out);
        }
        return out;
    }

    private static void readSentence(String s, String topic, List<Happening> out) {
        Matcher dm = DATE.matcher(s);
        List<int[]> at = new ArrayList<int[]>();
        List<When> dates = new ArrayList<When>();
        while (dm.find()) {
            if (dm.group(11) != null && !yearAt(s, dm.start(), dm.end())) {
                continue; // a bare number ("equal to 1000", "1200 people") is not a year
            }
            at.add(new int[]{dm.start(), dm.end()});
            dates.add(when(dm));
        }
        if (dates.isEmpty()) {
            return;
        }
        // H4: a span in brackets after the subject: "World War II … was a global conflict (1 September 1939 – 2 September 1945)"
        if (dates.size() >= 2) {
            String open = s.substring(0, at.get(0)[0]);
            String between = s.substring(at.get(0)[1], at.get(1)[0]).trim();
            if (open.trim().endsWith("(") && between.matches("[-–—]|to|until")) {
                String head = open.substring(0, open.lastIndexOf('(')).trim();
                String subj = subject(head, topic);
                if (!subj.isEmpty()) {
                    out.add(new Happening(subj, "began", dates.get(0), s));
                    out.add(new Happening(subj, "ended", dates.get(1), s));
                    return;
                }
            }
        }
        // H4: a span "from A to B" (a start and an end)
        Matcher span = Pattern.compile("(?i)^(.*?)\\b(?:from|between)\\s+$").matcher(dates.size() >= 2 ? s.substring(0, at.get(0)[0]) : "");
        String mid = dates.size() >= 2 ? s.substring(at.get(0)[1], at.get(1)[0]).trim() : "";
        if (span.matches() && mid.matches("(?i)(to|until|till|and|-|–)")) {
            String head = span.group(1).trim();
            String subj = subject(head, topic);
            String act = action(head, subj);
            if (!subj.isEmpty()) {
                out.add(new Happening(subj, (act.isEmpty() ? "" : act + " ") + "began", dates.get(0), s));
                out.add(new Happening(subj, (act.isEmpty() ? "" : act + " ") + "ended", dates.get(1), s));
                return;
            }
        }
        // H1–H3: each date with the action written before it ("… gained independence on 1 October 1960"), or after it
        // when the sentence opens with the date ("On July 20, 1969, Armstrong and Aldrin landed on the moon")
        String carried = null;
        for (int i = 0; i < dates.size(); i++) {
            int from = i == 0 ? 0 : at.get(i - 1)[1];
            String before = s.substring(from, at.get(i)[0]).replaceAll("(?i)\\b(on|in|at|by|around|about|since|of|from|during)\\s*$", "").trim();
            before = before.replaceFirst("^[,;:\\s]*(and|but|then|while)?\\s*", "").trim();
            boolean opening = i == 0 && before.matches("(?i)(on|in|at|by|around|about|during)?\\s*");
            if (opening) {
                // the date opens the sentence: the clause after it is what happened
                String after = s.substring(at.get(i)[1]).replaceFirst("^[,\\s]+", "");
                int end = i + 1 < dates.size() ? Math.max(0, at.get(i + 1)[0] - at.get(i)[1]) : after.length();
                after = after.substring(0, Math.min(after.length(), end)).replaceAll("(?i)[,;]?\\s*(while|but|and then|when|because)\\b.*$", "");
                String subj = subject(after, topic);
                String act = action(after, subj);
                if (!subj.isEmpty() && !act.isEmpty()) {
                    out.add(new Happening(subj, act, dates.get(i), s));
                }
                continue;
            }
            String subj = subject(before, topic);
            String act = action(before, subj);
            if (subj.isEmpty() && carried != null && !act.isEmpty()) {
                subj = carried; // "… began on A and ended on B": the same subject
            }
            if (!subj.isEmpty() && !act.isEmpty()) {
                out.add(new Happening(subj, act, dates.get(i), s));
                carried = subj;
            }
        }
    }

    /** A bare four-figure number is a year only where a year is said: after "in", "since", "by"… or in brackets. */
    static boolean yearAt(String s, int start, int end) {
        String before = s.substring(Math.max(0, start - 12), start).toLowerCase(Locale.US);
        String after = s.substring(end, Math.min(s.length(), end + 12)).toLowerCase(Locale.US);
        if (after.matches("^\\s*(people|persons|men|women|km|kilometres|kilometers|metres|meters|miles|years|times|percent|%|"
                + "million|thousand|tons|tonnes|dollars|naira|feet|ft|m\\b).*")) {
            return false;
        }
        if (before.matches(".*\\b(equal|equals|equivalent|up|close|down|out of|per)\\s+to\\s*$")) {
            return false; // "equal to 1000": an amount
        }
        return before.matches(".*\\b(in|on|since|by|from|to|until|till|between|and|of|year|around|about|circa|c\\.|before|after|"
                + "during|early|late|mid|until)\\s*$") || before.matches(".*[(–-]\\s*$") || start == 0;
    }

    /** The subject of a clause: the noun phrase before its verb, the topic for "it" or "the country"; "" when none. */
    static String subject(String clause, String topic) {
        Matcher v = VERB.matcher(clause);
        if (!v.find()) {
            return "";
        }
        String s = clause.substring(0, v.start()).trim().replaceAll("[,;:]+$", "").trim();
        s = s.replaceFirst("(?i)^(on|in|at|then|later|finally|and|but|so)\\s+", "");
        int comma = s.lastIndexOf(", ");
        if (comma > 0 && s.substring(comma + 2).split(" ").length <= 5) {
            s = s.substring(comma + 2); // "Nigeria, officially …, gained": the phrase nearest the verb
        }
        if (s.matches("(?i)there|here|the|a|an")) {
            return ""; // "there were …": no one it happened to
        }
        if (s.matches("(?i)(or|also known as|known as)\\s.*") && topic != null) {
            return topic; // "World War II, or the Second World War, was …": another name for the topic
        }
        if (s.isEmpty() || STANDS_FOR_TOPIC.matcher(s).matches()) {
            return topic == null ? "" : topic;
        }
        if (s.split("\\s+").length > 8) {
            return topic == null ? "" : topic;
        }
        return s;
    }

    /** What happened: the clause from its verb on, without the subject; "" when there's no verb. */
    static String action(String clause, String subject) {
        Matcher v = VERB.matcher(clause);
        if (!v.find()) {
            return "";
        }
        String a = clause.substring(v.start()).trim().replaceAll("[,;:.]+$", "").trim();
        a = a.replaceAll("(?i)\\s+\\b(on|in|at|by|around|about|since|from|during)$", "");
        return a.split("\\s+").length > 14 ? String.join(" ", Arrays.asList(a.split("\\s+")).subList(0, 14)) : a;
    }

    /**
     * The words of an action that carry its meaning, lower case and stripped of endings ("gained independence" →
     * gain, independ), so "became independent" and "gained independence" are seen to share "independ".
     */
    public static Set<String> gist(String action) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : action.toLowerCase(Locale.US).split("[^a-z]+")) {
            if (w.length() < 3 || STOP.contains(w)) {
                continue;
            }
            out.add(stem(w));
        }
        return out;
    }

    private static final Set<String> STOP = new java.util.HashSet<String>(Arrays.asList("the", "was", "were", "has", "had", "have",
            "been", "being", "and", "but", "for", "from", "with", "its", "his", "her", "their", "this", "that", "into", "onto",
            "officially", "also", "first", "then", "later", "which", "who", "when", "where", "after", "before", "some", "many"));

    /** A rough stem: the endings a word changes with taken off ("independence", "independent" → "independ"). */
    static String stem(String w) {
        String s = w;
        for (String e : new String[]{"ence", "ent", "ance", "ant", "ation", "ing", "ed", "es", "s", "ly", "ion"}) {
            if (s.length() > e.length() + 3 && s.endsWith(e)) {
                s = s.substring(0, s.length() - e.length());
                break;
            }
        }
        return s;
    }
}
