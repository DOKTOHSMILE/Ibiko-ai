package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a number in a sentence is, decided, not assumed: "1960" can be a year, a PIN, a price, a count of people, a
 * distance or a time, and "0803 123 4567" is a phone number, not three numbers. Every number in the sentence (written
 * in figures or in words: "nineteen sixty", "two thousand") is given every reading it could have, each scored by its
 * shape (digits, leading zero, separators, length) and by the words around it and what the sentence is about; the
 * best is the reading, with the others kept and the reasons said. A four-figure number is a year only when the
 * sentence makes it one. Pure Java: the cleansing step in front of memory, so what is learned is read right.
 *
 * <pre>
 * N1  find      every number: figures (with spaces, dots, commas, +, /, :), or number words
 * N2  shape     what its form allows: digits, length, a leading 0 or +, a separator, a sign or unit stuck to it
 * N3  context   the words before and after it (in 1960, call 0803…, ₦1500, 1200 people, 30 km, at 14:30, aged 30)
 * N4  topic     what the sentence is about (history makes a year likelier, money a price, a phone a number to call)
 * N5  choose    the reading with the most weight; the rest kept, with why
 * </pre>
 */
public final class NumberSense {
    private NumberSense() {
    }

    /** The kinds of thing a number can be. */
    public enum Kind {
        YEAR, DATE, PHONE, MONEY, QUANTITY, MEASURE, TIME, PERCENT, AGE, ORDINAL, CODE, NUMBER
    }

    /** One reading of a number: what kind, how likely, and why. */
    public static final class Reading {
        public final Kind kind;
        public final double score;
        public final String why;

        Reading(Kind kind, double score, String why) {
            this.kind = kind;
            this.score = score;
            this.why = why;
        }

        @Override
        public String toString() {
            return kind.name().toLowerCase(Locale.US) + String.format(Locale.US, " %.1f", score);
        }
    }

    /** A number found in a sentence: where, as written, its value, and its readings (best first). */
    public static final class Found {
        public final String text;
        public final int start, end;
        /** The number's value (for a phone number or code, its digits as a number may be meaningless). */
        public final double value;
        public final List<Reading> readings;

        Found(String text, int start, int end, double value, List<Reading> readings) {
            this.text = text;
            this.start = start;
            this.end = end;
            this.value = value;
            this.readings = readings;
        }

        /** The reading chosen. */
        public Kind kind() {
            return readings.isEmpty() ? Kind.NUMBER : readings.get(0).kind;
        }

        /** Why it was read so ("after "in", in a sentence about history"). */
        public String why() {
            return readings.isEmpty() ? "" : readings.get(0).why;
        }

        /** Sure enough to act on: the best reading well ahead of the next. */
        public boolean clear() {
            return readings.size() < 2 || readings.get(0).score - readings.get(1).score >= 1.0;
        }

        @Override
        public String toString() {
            return text + " → " + readings;
        }
    }

    // ------------------------------------------------------------------ N1 find

    /** A number in figures: digits, with the separators numbers are written with, a leading + or currency sign. */
    private static final Pattern FIGURES = Pattern.compile("(?<![\\p{L}\\d])([+]?\\d[\\d\\s().,:/-]*\\d|\\d)(?:\\s?(st|nd|rd|th))?(?![\\d])");
    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};
    private static final Pattern WORDS = Pattern.compile("(?i)\\b((?:(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|"
            + "twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|"
            + "ninety|hundred|thousand|million|billion|and)[\\s-]+)*(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|"
            + "twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|sixty|seventy|eighty|"
            + "ninety|hundred|thousand|million|billion))\\b");

    /** Every number in a sentence, with its readings. */
    public static List<Found> read(String sentence) {
        List<Found> out = new ArrayList<Found>();
        if (sentence == null || sentence.isEmpty()) {
            return out;
        }
        String topic = topicOf(sentence);
        Matcher m = FIGURES.matcher(sentence);
        while (m.find()) {
            String raw = m.group(1).replaceAll("[\\s.,:/(-]+$", "");
            int end = m.start(1) + raw.length();
            // a run like "1960 and 1963" or "30, 40" is several numbers, not one: split where a space separates
            // groups that aren't a phone number's
            for (int[] part : parts(sentence, m.start(1), end)) {
                String t = sentence.substring(part[0], part[1]);
                String suffix = part[1] == end && m.group(2) != null ? m.group(2) : null;
                out.add(found(sentence, t, part[0], suffix != null ? m.end() : part[1], suffix, topic));
            }
        }
        Matcher w = WORDS.matcher(sentence);
        while (w.find()) {
            String phrase = w.group(1);
            if (phrase.matches("(?i)(and|one)") || overlaps(out, w.start(), w.end())) {
                continue; // "one" alone is as often a word ("one of them") as a number
            }
            double v = words(phrase);
            if (v < 0) {
                continue;
            }
            out.add(found(sentence, phrase, w.start(), w.end(), null, topic, v));
        }
        Collections.sort(out, (a, b) -> Integer.compare(a.start, b.start));
        return out;
    }

    private static boolean overlaps(List<Found> found, int s, int e) {
        for (Found f : found) {
            if (s < f.end && e > f.start) {
                return true;
            }
        }
        return false;
    }

    /** Where a run of figures splits into separate numbers ("1960 and", "30, 40"); a phone number stays whole. */
    private static List<int[]> parts(String s, int start, int end) {
        String t = s.substring(start, end);
        List<int[]> out = new ArrayList<int[]>();
        String digits = t.replaceAll("\\D", "");
        boolean phoneLike = t.startsWith("+") || digits.length() >= 10 && t.matches("[+\\d\\s().-]+");
        boolean dateLike = t.matches("\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}");
        boolean grouped = t.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?|\\d+(\\.\\d+)?|\\d{1,2}:\\d{2}");
        if (phoneLike || dateLike || grouped) {
            out.add(new int[]{start, end});
            return out;
        }
        Matcher m = Pattern.compile("\\d[\\d.,:]*\\d|\\d").matcher(t);
        while (m.find()) {
            out.add(new int[]{start + m.start(), start + m.end()});
        }
        return out;
    }

    /** The value of number words ("nineteen sixty" 1960, "two thousand and five" 2005), or -1. */
    static double words(String phrase) {
        String[] w = phrase.toLowerCase(Locale.US).split("[\\s-]+");
        // "nineteen sixty", "twenty twenty-four": a year said as two pairs
        if (w.length >= 2 && w.length <= 3 && teen(w[0]) >= 10 && !phrase.matches("(?i).*\\b(hundred|thousand|million|billion|and)\\b.*")) {
            int hi = teen(w[0]) >= 0 ? teen(w[0]) : -1;
            int lo = 0;
            for (int i = 1; i < w.length; i++) {
                int t = tens(w[i]), o = teen(w[i]);
                lo += t >= 0 ? t : o >= 0 ? o : -1000;
            }
            if (hi >= 10 && lo >= 0 && lo < 100) {
                return hi * 100 + lo;
            }
        }
        double total = 0, current = 0;
        boolean any = false;
        for (String x : w) {
            int o = teen(x), t = tens(x);
            if (o >= 0) {
                current += o;
                any = true;
            } else if (t >= 0) {
                current += t;
                any = true;
            } else if (x.equals("hundred")) {
                current = (current == 0 ? 1 : current) * 100;
                any = true;
            } else if (x.equals("thousand") || x.equals("million") || x.equals("billion")) {
                double k = x.equals("thousand") ? 1e3 : x.equals("million") ? 1e6 : 1e9;
                total += (current == 0 ? 1 : current) * k;
                current = 0;
                any = true;
            } else if (!x.equals("and")) {
                return -1;
            }
        }
        return any ? total + current : -1;
    }

    private static int teen(String w) {
        return Arrays.asList(ONES).indexOf(w);
    }

    private static int tens(String w) {
        int i = Arrays.asList(TENS).indexOf(w);
        return i >= 2 ? i * 10 : -1;
    }

    // ------------------------------------------------------------------ N2–N5

    private static Found found(String s, String t, int start, int end, String suffix, String topic) {
        double v;
        try {
            String plain = t.replaceAll("[\\s,]", "");
            v = plain.matches("\\d+(\\.\\d+)?") ? Double.parseDouble(plain) : Double.NaN;
        } catch (NumberFormatException e) {
            v = Double.NaN;
        }
        return found(s, t, start, end, suffix, topic, v);
    }

    private static Found found(String s, String t, int start, int end, String suffix, String topic, double value) {
        String before = s.substring(Math.max(0, start - 30), start).toLowerCase(Locale.US);
        String after = s.substring(Math.min(s.length(), end), Math.min(s.length(), end + 30)).toLowerCase(Locale.US);
        String prev = lastWord(before), next = firstWord(after);
        String digits = t.replaceAll("\\D", "");
        List<Reading> r = new ArrayList<Reading>();
        boolean words = !t.matches(".*\\d.*");

        // N2 shape
        boolean plain4 = t.matches("\\d{4}") || words && value >= 1000 && value <= 2199;
        boolean leadingZero = digits.startsWith("0") && digits.length() > 1;
        boolean plus = t.startsWith("+");
        boolean grouped = t.matches("\\d{1,3}(,\\d{3})+(\\.\\d+)?");
        boolean decimal = t.matches("\\d+\\.\\d+");
        boolean clock = t.matches("\\d{1,2}:\\d{2}(:\\d{2})?");
        boolean slashDate = t.matches("\\d{1,4}[/.-]\\d{1,2}[/.-]\\d{1,4}");

        // phone: +country code, a leading 0 with 10 or 11 digits, or 7+ digits in groups of a phone's shape
        double phone = 0;
        if (plus && digits.length() >= 8 && digits.length() <= 15) {
            phone += 4;
        }
        if (leadingZero && (digits.length() == 11 || digits.length() == 10)) {
            phone += 3;
        }
        if (digits.length() >= 7 && t.matches("[+\\d][\\d\\s().-]+") && t.matches(".*[\\s.-].*") && !slashDate && !grouped) {
            phone += 1.5;
        }
        if (has(before, "call", "phone", "tel", "telephone", "mobile", "number", "whatsapp", "contact", "text", "sms", "reach", "dial", "line")) {
            phone += digits.length() >= 7 ? 3 : 0.5;
        }
        if (phone > 0) {
            r.add(new Reading(Kind.PHONE, phone, "a phone number's shape" + (phone >= 4 ? " and words" : "")));
        }

        // date: d/m/y, or a number beside a month name
        if (slashDate) {
            r.add(new Reading(Kind.DATE, 5, "written as a date (day, month and year)"));
        }
        if ((before.matches(".*\\b(" + MONTHS + ")\\.?,?\\s*(\\d{1,2}(st|nd|rd|th)?,?\\s*)?$") || after.matches("^\\s*(st|nd|rd|th)?\\s*(of\\s+)?(" + MONTHS + ")\\b.*"))
                && (value >= 1 && value <= 31 || plain4)) {
            r.add(new Reading(Kind.DATE, plain4 ? 3.5 : 4, "beside a month's name"));
        }

        // time: hh:mm, am/pm, o'clock, "1430 hours"
        if (clock) {
            r.add(new Reading(Kind.TIME, 5, "written as a time of day"));
        }
        if (next.matches("am|pm|a\\.m\\.|p\\.m\\.|o'clock|hours|hrs|gmt|wat|utc") && !grouped) {
            r.add(new Reading(Kind.TIME, 4, "followed by “" + next + "”"));
        }

        // money: a currency sign or word, k/m stuck on, or words of buying and paying
        if (before.matches(".*(₦|\\$|£|€)\\s*$") || before.matches(".*(^|[^\\p{L}])(ngn|usd|n)\\s?$") || has(after, "naira", "dollars", "dollar", "pounds", "euros", "cedis", "kobo",
                "usd", "ngn") || suffix != null && suffix.matches("(?i)k|m|bn")) {
            r.add(new Reading(Kind.MONEY, 5, "a currency with it"));
        } else if (has(before, "cost", "costs", "price", "pay", "paid", "pays", "sell", "sold", "buy", "bought", "salary", "fee", "fees",
                "charge", "charged", "worth", "budget", "earn", "earned", "spent", "spend", "owe")) {
            r.add(new Reading(Kind.MONEY, 2.5, "words of paying or prices before it"));
        }

        // percent
        if (after.matches("^\\s*(%|percent|per cent).*")) {
            r.add(new Reading(Kind.PERCENT, 6, "followed by a percent"));
        }

        // age
        if (has(before, "aged", "age") || after.matches("^\\s*(years?|yrs?)[\\s-]*old.*") || before.matches(".*\\b(is|was|turned|turns|at)\\s*$")
                && after.matches("^\\s*(years?|yrs?)\\b.*") && value < 130) {
            r.add(new Reading(Kind.AGE, 4.5, "an age (“years old”, “aged”)"));
        }

        // ordinal
        if (suffix != null && suffix.matches("(?i)st|nd|rd|th")) {
            r.add(new Reading(Kind.ORDINAL, 5, "written as an ordinal (“" + suffix + "”)"));
        }

        // a measure: a unit after it
        if (after.matches("^\\s*(km|kilomet(er|re)s?|m|met(er|re)s?|cm|mm|miles?|ft|feet|kg|kilograms?|g|grams?|tonnes?|tons?|litres?|liters?"
                + "|l|ml|square|sq|hectares?|acres?|kph|km/h|mph|°c|°f|degrees?|mb|gb|tb|kb|mbps|watts?|w|kw|volts?|v)\\b.*")) {
            r.add(new Reading(Kind.MEASURE, 5, "followed by a unit (“" + next + "”)"));
        }

        // a count of things: a plural noun after it
        if (!words && next.matches("[a-z]{3,}s") && !next.matches("years|hours|days|weeks|months|minutes|seconds|percent|naira|dollars|pounds")
                || has(after, "people", "persons", "men", "women", "children", "students", "members", "copies", "units", "items", "votes",
                "cases", "deaths", "followers", "likes", "views", "residents", "inhabitants")) {
            r.add(new Reading(Kind.QUANTITY, 3.5, "a count of “" + next + "”"));
        }
        if (grouped || decimal) {
            r.add(new Reading(Kind.QUANTITY, 2, "written as an amount (" + (grouped ? "grouped thousands" : "a decimal") + ")"));
        }

        // a code: a PIN, an account, a room, a flat, a reference, an OTP; a leading zero not a phone's
        String cue = which(before, "pin", "otp", "code", "password", "passcode", "account", "acct", "id", "ref", "reference", "room", "flat",
                "house", "no.", "serial", "ticket", "invoice", "order", "plot", "bus", "route", "zip", "postcode", "bvn", "nin");
        if (cue != null && !has(before, "call", "phone", "mobile", "whatsapp")) {
            r.add(new Reading(Kind.CODE, 4, "after “" + cue + "”: a code or label, not an amount"));
        } else if (leadingZero && phone == 0) {
            r.add(new Reading(Kind.CODE, 2.5, "a leading zero: a code, not an amount"));
        }

        // a year: four figures in a year's range, made likely by the words that say a year
        if (plain4 || t.matches("\\d{1,4}") && after.matches("^\\s*(bc|bce|ad|ce)\\b.*")) {
            double y = 0;
            String why = "";
            if (after.matches("^\\s*(bc|bce|ad|ce)\\b.*")) {
                y += 6;
                why = "followed by “" + next.toUpperCase(Locale.US) + "”";
            }
            if (value >= 1000 && value <= 2199) {
                y += 0.9; // in a year's range: possible, not yet likely (a plain number wins with nothing else)
                if (prev.matches("in|since|by|from|until|till|between|during|before|after|circa|c\\.|year|of|early|late|mid|around|to|and")) {
                    y += 2.5;
                    why = "after “" + prev + "”";
                }
                if (before.matches(".*\\b(born|died|founded|established|built|created|independence|independent|elected|won|released|"
                        + "published|started|began|ended|opened|discovered|invented|war|century|era|year|years)\\b.*")) {
                    y += 1.5;
                    why += (why.isEmpty() ? "" : ", ") + "words of time and events before it";
                }
                if ("history".equals(topic)) {
                    y += 1;
                    why += (why.isEmpty() ? "" : ", ") + "in a sentence about events";
                }
                if (before.matches(".*[(]\\s*$") || after.matches("^\\s*[)–-].*")) {
                    y += 1; // in brackets or a span: (1939 – 1945)
                }
                if (s.substring(0, start).trim().isEmpty() && after.matches("^\\s*[,:].*")) {
                    y += 1.5; // "1960: independence."
                }
            }
            if (y > 0) {
                r.add(new Reading(Kind.YEAR, y, why.isEmpty() ? "four figures in a year's range, nothing more" : why));
            }
        }

        // N4: what the sentence is about weighs in
        for (int i = 0; i < r.size(); i++) {
            Reading x = r.get(i);
            double bonus = "money".equals(topic) && x.kind == Kind.MONEY || "contact".equals(topic) && x.kind == Kind.PHONE
                    || "time".equals(topic) && x.kind == Kind.TIME ? 1 : 0;
            if (bonus > 0) {
                r.set(i, new Reading(x.kind, x.score + bonus, x.why + ", in a sentence about " + topic));
            }
        }
        r.add(new Reading(Kind.NUMBER, 1, "a plain number"));
        // N5: the best first; one reading per kind (its best)
        r.sort((a, b) -> Double.compare(b.score, a.score));
        Set<Kind> seen = new HashSet<Kind>();
        List<Reading> kept = new ArrayList<Reading>();
        for (Reading x : r) {
            if (seen.add(x.kind)) {
                kept.add(x);
            }
        }
        return new Found(t, start, end, value, kept);
    }

    private static final String MONTHS = "january|february|march|april|may|june|july|august|september|october|november|december"
            + "|jan|feb|mar|apr|jun|jul|aug|sep|sept|oct|nov|dec";

    /** What a sentence is about, for N4: history, money, contact, time; or "". */
    static String topicOf(String s) {
        String t = s.toLowerCase(Locale.US);
        if (t.matches(".*\\b(born|died|war|independence|independent|founded|empire|king|queen|president|revolution|century|history|"
                + "colony|colonial|treaty|elected|reign|ancient|built in)\\b.*")) {
            return "history";
        }
        if (t.matches(".*\\b(price|cost|pay|paid|naira|dollar|money|salary|budget|sell|buy|discount|fee|₦|\\$)\\b.*") || t.contains("₦")) {
            return "money";
        }
        if (t.matches(".*\\b(call|phone|whatsapp|mobile|contact|text me|sms|dial)\\b.*")) {
            return "contact";
        }
        if (t.matches(".*\\b(meeting|o'clock|am|pm|tonight|morning|evening|schedule|appointment|alarm)\\b.*")) {
            return "time";
        }
        return "";
    }

    /** The last of {@code words} in the text, or null. */
    private static String which(String text, String... words) {
        String found = null;
        int at = -1;
        for (String w : words) {
            Matcher m = Pattern.compile("(?<![\\p{L}])" + Pattern.quote(w) + "(?![\\p{L}])").matcher(text);
            while (m.find()) {
                if (m.start() > at) {
                    at = m.start();
                    found = w;
                }
            }
        }
        return found;
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (Pattern.compile("(?<![\\p{L}])" + Pattern.quote(w) + "(?![\\p{L}])").matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String lastWord(String s) {
        String[] w = s.trim().split("[^\\p{L}\\p{N}.']+");
        return w.length == 0 ? "" : w[w.length - 1];
    }

    private static String firstWord(String s) {
        Matcher m = Pattern.compile("[\\p{L}%°'.]+|[\\p{L}]+").matcher(s);
        return m.find() ? m.group().toLowerCase(Locale.US).replaceAll("\\.$", "") : "";
    }

    /** True when the number at {@code start..end} of the sentence is read as a year (its best reading). */
    public static boolean isYear(String sentence, int start, int end) {
        for (Found f : read(sentence)) {
            if (f.start <= start && f.end >= end) {
                return f.kind() == Kind.YEAR;
            }
        }
        return false;
    }

    /** The numbers of a sentence and what each was read as, in words (for the steps, or to explain). */
    public static String explain(String sentence) {
        StringBuilder b = new StringBuilder();
        for (Found f : read(sentence)) {
            b.append(b.length() == 0 ? "" : "\n").append("“").append(f.text).append("”: ").append(name(f.kind())).append(" (")
                    .append(f.why()).append(')');
            if (f.readings.size() > 1 && !f.clear()) {
                b.append("; could also be ").append(name(f.readings.get(1).kind));
            }
        }
        return b.toString();
    }

    static String name(Kind k) {
        switch (k) {
            case YEAR: return "a year";
            case DATE: return "a date";
            case PHONE: return "a phone number";
            case MONEY: return "an amount of money";
            case QUANTITY: return "a count or amount";
            case MEASURE: return "a measurement";
            case TIME: return "a time of day";
            case PERCENT: return "a percentage";
            case AGE: return "an age";
            case ORDINAL: return "a position (an ordinal)";
            case CODE: return "a code or label";
            default: return "a plain number";
        }
    }
}
