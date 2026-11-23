package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thinking about a question before looking anything up: what it asks for (a winner, a date, a place, a number), the
 * thing it is about ("the 2027 World Cup"), the year it names, and whether it asks about something done or still to
 * come. Then the research is held to it: an answer has to name the thing asked about, and "who won" has to say who
 * won; a question about something that hasn't happened yet ("who won the 2027 World Cup?", asked in 2026) is
 * answered so, not with whatever mentions 2027. A word that turns a known name into nonsense ("world up") is read as
 * the name it nearly is ("World Cup"). Pure Java.
 *
 * <pre>
 * A1  wants    who (a name), when (a date), where (a place), how many (a number), what (a description)
 * A2  about    the thing named: the noun phrase the question turns on, without "the" or its year
 * A3  year     a year the question names (read by NumberSense, never any four figures)
 * A4  done     a past verb ("won", "was", "happened") about a year still to come: not happened yet
 * A5  holds    a sentence answers only if it names the thing, its year, and (for "who won") a winner
 * A6  repair   a word pair that is no known name, one letter from one, is that name
 * </pre>
 */
public final class Asking {
    private Asking() {
    }

    /**
     * What a question asks for: who (a name), whose (an owner), what (a description), which (one of some), when (a
     * date or time), where (a place), why (a reason), how (a way or process), how many (a count), how much (an amount
     * or price), how long (a duration or length), how old (an age), how far / big / tall… (a measure), and yes or no.
     */
    public enum Wants {
        WHO, WHOSE, WHAT, WHICH, WHEN, WHERE, WHY, HOW, HOW_MANY, HOW_MUCH, HOW_LONG, HOW_OLD, MEASURE, YES_NO, OTHER
    }

    /** What each kind of question is looking for, in words (for the steps). */
    static String looking(Wants w) {
        switch (w) {
            case WHO: return "a name (who)";
            case WHOSE: return "an owner (whose)";
            case WHAT: return "a description (what)";
            case WHICH: return "one of the options (which)";
            case WHEN: return "a date or time (when)";
            case WHERE: return "a place (where)";
            case WHY: return "a reason (why)";
            case HOW: return "a way or process (how)";
            case HOW_MANY: return "a count (how many)";
            case HOW_MUCH: return "an amount or price (how much)";
            case HOW_LONG: return "a duration or length (how long)";
            case HOW_OLD: return "an age (how old)";
            case MEASURE: return "a measure (how far, big, tall…)";
            case YES_NO: return "a yes or a no";
            default: return "an answer";
        }
    }

    private static final Pattern REASON = Pattern.compile("(?i)\\b(because|due to|owing to|as a result|a result of|caused by|causes?|"
            + "led to|leads to|so that|in order to|thanks to|the reason|reasons|since|therefore|which is why|that is why|"
            + "results? from|resulted from|stems from|driven by|explained by)\\b");
    private static final Pattern MANNER = Pattern.compile("(?i)\\b(by|through|using|uses|used|via|with the help of|steps?|first|then|"
            + "process|method|works by|is done|is made|are made|involves|consists of|mix|heat|press|turn|follow)\\b");
    private static final Pattern DURATION = Pattern.compile("(?i)\\b\\d[\\d,.]*\\s*(seconds?|minutes?|hours?|days?|weeks?|months?|"
            + "years?|decades?|centuries|century)\\b|\\b(a|an|one|two|three|four|five|six|seven|eight|nine|ten|several|many|few)\\s+"
            + "(seconds?|minutes?|hours?|days?|weeks?|months?|years?|decades?|centuries)\\b");
    private static final Pattern NAME = Pattern.compile("(?<![.!?]\\s)(?<!^)\\b[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)*|^[A-Z][\\p{L}'-]+\\s+[A-Z][\\p{L}'-]+");
    private static final Pattern PLACE = Pattern.compile("\\b(in|at|near|from|on|located in|based in|found in|capital of)\\s+(the\\s+)?[A-Z][\\p{L}'-]+");

    /** A question read for what it asks. */
    public static final class Frame {
        public final Wants wants;
        /** The thing it is about, plain ("world cup"); "" when none was found. */
        public final String about;
        /** A year it names, or 0. */
        public final int year;
        /** The question's verb, plain ("won", "is"). */
        public final String verb;
        /** Asks about something done: a past verb. */
        public final boolean past;
        /** Asks who won, who was the champion: a winner is wanted. */
        public final boolean winner;

        Frame(Wants wants, String about, int year, String verb, boolean past, boolean winner) {
            this.wants = wants;
            this.about = about;
            this.year = year;
            this.verb = verb;
            this.past = past;
            this.winner = winner;
        }

        /**
         * Whether a sentence gives the kind of answer asked for: a reason for why, a way for how, a date for when, a
         * place for where, a count, an amount, a duration, an age or a measure for the how-questions (read by
         * {@link NumberSense}), a name for who, an owner for whose. A sentence about the right thing that doesn't
         * answer the question asked is not the answer.
         */
        public boolean answers(String sentence) {
            if (sentence == null || sentence.isEmpty()) {
                return false;
            }
            java.util.EnumSet<NumberSense.Kind> numbers = java.util.EnumSet.noneOf(NumberSense.Kind.class);
            for (NumberSense.Found f : NumberSense.read(sentence)) {
                numbers.add(f.kind());
            }
            switch (wants) {
                case WHO:
                    return NAME.matcher(sentence).find();
                case WHOSE:
                    return sentence.matches("(?s).*(\\b\\w+'s\\b|\\bbelongs? to\\b|\\bowned by\\b|\\bproperty of\\b|\\bhis\\b|\\bher\\b|\\btheir\\b).*");
                case WHEN:
                    return numbers.contains(NumberSense.Kind.YEAR) || numbers.contains(NumberSense.Kind.DATE) || numbers.contains(NumberSense.Kind.TIME)
                            || sentence.matches("(?is).*\\b(january|february|march|april|may|june|july|august|september|october|november|"
                            + "december|century|centuries|decade|ago|yesterday|today|tomorrow|morning|evening|night)\\b.*");
                case WHERE:
                    return PLACE.matcher(sentence).find() || sentence.matches("(?is).*\\b(located|situated|lies|north|south|east|west)\\b.*");
                case WHY:
                    return REASON.matcher(sentence).find();
                case HOW:
                    return MANNER.matcher(sentence).find();
                case HOW_MANY:
                    return numbers.contains(NumberSense.Kind.QUANTITY) || numbers.contains(NumberSense.Kind.NUMBER)
                            || numbers.contains(NumberSense.Kind.MEASURE);
                case HOW_MUCH:
                    return numbers.contains(NumberSense.Kind.MONEY) || numbers.contains(NumberSense.Kind.QUANTITY)
                            || numbers.contains(NumberSense.Kind.MEASURE) || numbers.contains(NumberSense.Kind.PERCENT);
                case HOW_LONG:
                    return DURATION.matcher(sentence).find() || numbers.contains(NumberSense.Kind.MEASURE);
                case HOW_OLD:
                    return numbers.contains(NumberSense.Kind.AGE) || DURATION.matcher(sentence).find()
                            || sentence.matches("(?is).*\\b(born|founded|built|established|formed)\\b.*") && numbers.contains(NumberSense.Kind.YEAR);
                case MEASURE:
                    return numbers.contains(NumberSense.Kind.MEASURE);
                case WHICH:
                    return NAME.matcher(sentence).find();
                case WHAT:
                    return sentence.matches("(?is).*\\b(is|are|was|were|means|refers to|known as|called)\\b.*");
                default:
                    return true;
            }
        }

        /** A4: a past question about a year still to come (it can't have happened yet). */
        public boolean notYet(int thisYear) {
            return past && year > thisYear;
        }

        /**
         * A5: a sentence answers only if it names the thing asked about (its words, in order), the year asked (when
         * one was), and for "who won", a winner.
         */
        public boolean holds(String sentence) {
            String s = " " + plain(sentence) + " ";
            if (!about.isEmpty() && !hasPhrase(s, about)) {
                return false;
            }
            if (year > 0 && !s.contains(" " + year + " ")) {
                return false;
            }
            if (winner && !s.matches(".*\\b(won|win|wins|winner|winners|champion|champions|beat|beating|defeated|title|lifted|victory|"
                    + "victorious|crowned)\\b.*")) {
                return false;
            }
            return true;
        }

        /** For the steps: "a winner of the 2027 world cup (a past question)". */
        public String said() {
            String what = winner ? "a winner" : looking(wants);
            return what + (about.isEmpty() ? "" : " for “" + (year > 0 ? year + " " : "") + about + "”");
        }
    }

    private static final Set<String> STOP = new HashSet<String>(Arrays.asList("the", "a", "an", "of", "in", "on", "at", "for", "to",
            "and", "or", "is", "was", "were", "are", "be", "been", "did", "does", "do", "who", "what", "when", "where", "which", "how",
            "many", "much", "won", "win", "wins", "first", "last", "this", "that", "year", "in", "by", "with", "it", "its"));
    private static final Pattern PAST = Pattern.compile("(?i)\\b(won|was|were|did|happened|became|took place|held|hosted|finished|ended|"
            + "beat|defeated|scored|lost|came|went|got|made|released|invented|died)\\b");

    /** A1–A4: what a question asks for, about what, which year, and whether done. */
    public static Frame of(String question) {
        String q = question == null ? "" : question.trim();
        String low = q.toLowerCase(Locale.US);
        Wants wants = wants(low);
        int year = 0;
        for (NumberSense.Found f : NumberSense.read(q)) {
            if (f.kind() == NumberSense.Kind.YEAR || f.text.matches("\\d{4}") && f.readings.stream().anyMatch(r -> r.kind == NumberSense.Kind.YEAR)) {
                year = (int) f.value;
            }
        }
        if (year == 0) {
            // "the 2027 World Cup": a year written as the first word of a name is a year
            Matcher y = Pattern.compile("\\b(?:the\\s+)?(1[5-9]\\d\\d|20\\d\\d)\\s+[A-Za-z]").matcher(q);
            if (y.find()) {
                year = Integer.parseInt(y.group(1));
            }
        }
        Matcher pv = PAST.matcher(low);
        boolean past = pv.find();
        String verb = past ? pv.group(1) : "";
        boolean winner = wants == Wants.WHO && low.matches(".*\\b(won|win|wins|winner|champion|champions|beat)\\b.*")
                || low.matches(".*\\bwho (is|was) the (winner|champion)s?\\b.*");
        return new Frame(wants, about(low), year, verb, past, winner);
    }

    /** A1: the kind of answer a question wants, after any lead-in ("can you tell me why…", "explain how…"). */
    static Wants wants(String low) {
        String q = low.trim().replaceFirst("^(please\\s+)?((can|could|would) you\\s+)?(tell me|explain|show me|let me know|do you know|"
                + "i want to know|i'd like to know|i wonder|any idea)\\s+", "").replaceFirst("^(and|but|so|ok|okay)[,]?\\s+", "");
        if (q.matches("^how many\\b.*")) {
            return Wants.HOW_MANY;
        }
        if (q.matches("^how much\\b.*")) {
            return Wants.HOW_MUCH;
        }
        if (q.matches("^how long\\b.*")) {
            return Wants.HOW_LONG;
        }
        if (q.matches("^how old\\b.*")) {
            return Wants.HOW_OLD;
        }
        if (q.matches("^how (far|big|tall|high|deep|large|wide|heavy|fast|hot|cold|small|short)\\b.*")) {
            return Wants.MEASURE;
        }
        if (q.matches("^how\\b.*")) {
            return Wants.HOW;
        }
        if (q.matches("^why\\b.*|^what (made|makes|caused|causes|led to)\\b.*|^for what reason\\b.*")) {
            return Wants.WHY;
        }
        if (q.matches("^whose\\b.*")) {
            return Wants.WHOSE;
        }
        if (q.matches("^(who|whom)\\b.*")) {
            return Wants.WHO;
        }
        if (q.matches("^when\\b.*|^what (year|date|day|time|month|century)\\b.*|^in what year\\b.*")) {
            return Wants.WHEN;
        }
        if (q.matches("^where\\b.*|^in (what|which) (country|city|state|town|place)\\b.*")) {
            return Wants.WHERE;
        }
        if (q.matches("^which (team|country|player|club|person|man|woman|company|band|artist|writer)\\b.*")) {
            return Wants.WHO;
        }
        if (q.matches("^which\\b.*")) {
            return Wants.WHICH;
        }
        if (q.matches("^what\\b.*")) {
            return Wants.WHAT;
        }
        if (q.matches("^(is|are|was|were|do|does|did|can|could|will|would|has|have|had|should|shall|may|might|must|isn't|aren't|"
                + "wasn't|didn't|doesn't|don't|won't|can't)\\b.*")) {
            return Wants.YES_NO;
        }
        return Wants.OTHER;
    }

    /** A2: the thing named: the content words after the verb (or the whole), without its year and small words. */
    static String about(String low) {
        String body = low.replaceAll("[?!.]+$", "").replaceFirst("^\\s*(who|whom|whose|what|when|where|which|why|how (many|much|long|old|far|"
                + "big|tall|high|deep|large|wide|heavy|fast)|how)\\b", "");
        Matcher after = Pattern.compile("\\b(won|win|wins|was|were|is|are|did|does|held|hosted|happened|of)\\s+(.*)$").matcher(body);
        String phrase = after.find() ? after.group(2) : body;
        phrase = phrase.trim().replaceFirst("^(\\w+ed|founded|invented|discovered|built|made|wrote|created)\\s+", ""); // "founded microsoft": the thing, not the verb
        List<String> kept = new ArrayList<String>();
        for (String w : phrase.split("[^a-z0-9'-]+")) {
            if (w.isEmpty() || STOP.contains(w) || w.matches("\\d{4}")) {
                if (!kept.isEmpty() && (w.equals("of") || w.equals("the"))) {
                    kept.add(w); // "cup of nations", "president of the republic": inside a name
                }
                continue;
            }
            kept.add(w);
        }
        while (!kept.isEmpty() && kept.get(kept.size() - 1).matches("of|the")) {
            kept.remove(kept.size() - 1);
        }
        return String.join(" ", kept).trim();
    }

    /** Text as compared: lower case, letters and digits, single spaces. */
    static String plain(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.US).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** The phrase's words in order in {@code s} (" … "), a plural allowed ("cups"). */
    static boolean hasPhrase(String s, String phrase) {
        StringBuilder re = new StringBuilder(".*\\b");
        String[] w = phrase.split(" ");
        for (int i = 0; i < w.length; i++) {
            re.append(i == 0 ? "" : "\\s+(?:the\\s+|of\\s+)*").append(Pattern.quote(w[i])).append("(?:s|es)?");
        }
        return s.matches(re.append("\\b.*").toString());
    }

    /**
     * A6: the question with a word pair that is no known name, but one letter from one, read as that name ("the 2027
     * world up" → "the 2027 World Cup"); and what was changed, for the steps. {@code known} says whether a phrase is a
     * name (an encyclopedia title). Unchanged when nothing is near a name.
     */
    public static String[] repair(String question, Predicate<String> known) {
        List<String> words = new ArrayList<String>(Arrays.asList(question.split("\\s+")));
        for (int i = 0; i + 1 < words.size(); i++) {
            String a = core(words.get(i)), b = core(words.get(i + 1));
            if (a.length() < 2 || b.length() < 2 || STOP.contains(a) && STOP.contains(b) || a.matches("\\d+") || b.matches("\\d+")) {
                continue;
            }
            if (known.test(a + " " + b)) {
                continue; // a name already
            }
            for (int side = 0; side < 2; side++) {
                String fix = side == 0 ? a : b, keep = side == 0 ? b : a;
                if (fix.length() > 7 || STOP.contains(keep)) {
                    continue;
                }
                for (String edit : edits(fix)) {
                    String pair = side == 0 ? edit + " " + keep : keep + " " + edit;
                    if (known.test(pair)) {
                        String was = words.get(i) + " " + words.get(i + 1);
                        String now = cap(side == 0 ? edit : a) + " " + cap(side == 0 ? b : edit) + tail(words.get(i + 1));
                        words.set(i, cap(side == 0 ? edit : a));
                        words.set(i + 1, cap(side == 0 ? b : edit) + tail(words.get(i + 1)));
                        return new String[]{String.join(" ", words), "Read “" + was.replaceAll("[?.!]+$", "") + "” as “"
                                + now.replaceAll("[?.!]+$", "") + "”"};
                    }
                }
            }
        }
        return new String[]{question, null};
    }

    private static String core(String w) {
        return w.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    private static String tail(String w) {
        Matcher m = Pattern.compile("[?.!,]+$").matcher(w);
        return m.find() ? m.group() : "";
    }

    private static String cap(String w) {
        String c = core(w);
        return c.isEmpty() ? w : Character.toUpperCase(c.charAt(0)) + c.substring(1);
    }

    /** Every word one letter away: a letter added, dropped or changed (most likely first: added at the start). */
    static List<String> edits(String w) {
        List<String> out = new ArrayList<String>();
        String abc = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i <= w.length(); i++) {
            for (char c : abc.toCharArray()) {
                out.add(w.substring(0, i) + c + w.substring(i));
            }
        }
        for (int i = 0; i < w.length(); i++) {
            for (char c : abc.toCharArray()) {
                if (c != w.charAt(i)) {
                    out.add(w.substring(0, i) + c + w.substring(i + 1));
                }
            }
            if (w.length() > 2) {
                out.add(w.substring(0, i) + w.substring(i + 1));
            }
        }
        return out;
    }
}
