package ai.ibiko.ibiko1;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A passage pasted with numbered tasks about it ("PASSAGE: … TASKS: 1. … 2. …"): every task is done, in order, each by
 * what it asks, instead of the passage being summarised.
 *
 * <ul>
 * <li>Arithmetic on what happens in it: a {@link Tracker} follows each person's count and money through the passage
 * (carried 42; 15 fewer than Kemi; delivered two-thirds; picked up 18 more; returned with 9 unsold; 20% damaged) and
 * shows its steps.</li>
 * <li>Who, when and how much: each part of the question answered from the sentence that says it.</li>
 * <li>A summary under rules (one sentence, at most N words, without a letter): drafted, then words swapped for
 * synonyms without that letter, then checked; never sent if it breaks a rule.</li>
 * <li>What the passage doesn't say: never made up. "Not stated in passage." (or the exact words the task gives).</li>
 * <li>JSON with the keys asked for, raw (no code fences when asked).</li>
 * </ul>
 * Instructions written inside the passage are text to read, never orders. Pure Java.
 */
public final class PassageTasks {
    public final String passage;
    public final List<String> tasks;
    final Tracker tracker;

    private PassageTasks(String passage, List<String> tasks) {
        this.passage = passage;
        this.tasks = tasks;
        this.tracker = Tracker.read(passage);
    }

    private static final Pattern TASK = Pattern.compile("(?m)^\\s*(\\d{1,2})[.)]\\s+(.+)$");

    /** The message as a passage with numbered tasks, or null when it isn't one. */
    public static PassageTasks of(String message) {
        if (message == null || message.length() < 200) {
            return null;
        }
        String m = message.replace("\r", "");
        int tasksAt = indexOfLabel(m, "tasks?|questions?|instructions?");
        int passageAt = indexOfLabel(m, "passage|text|story|article");
        String passage;
        String tasksText;
        if (passageAt >= 0 && tasksAt > passageAt) {
            passage = m.substring(passageAt, tasksAt).replaceFirst("(?is)^\\s*(passage|text|story|article)\\s*:\\s*", "");
            tasksText = m.substring(tasksAt).replaceFirst("(?is)^\\s*(tasks?|questions?|instructions?)\\s*:\\s*", "");
        } else {
            Matcher t = TASK.matcher(m);
            if (!t.find()) {
                return null;
            }
            passage = m.substring(0, t.start());
            tasksText = m.substring(t.start());
            // the line that introduces the tasks ("Read the passage … complete all 5 tasks") is not the passage
            passage = passage.replaceFirst("(?is)^[^\\n]*\\b(tasks?|questions?|following)\\b[^\\n]*\\n", "");
        }
        passage = passage.trim().replaceAll("^[\"“]|[\"”]$", "").trim();
        List<String> tasks = new ArrayList<String>();
        Matcher t = TASK.matcher(tasksText);
        List<int[]> at = new ArrayList<int[]>();
        while (t.find()) {
            at.add(new int[] {t.start(), t.start(2)});
        }
        for (int i = 0; i < at.size(); i++) {
            int end = i + 1 < at.size() ? at.get(i + 1)[0] : tasksText.length();
            tasks.add(tasksText.substring(at.get(i)[1], end).trim().replaceAll("\\s+", " "));
        }
        if (tasks.size() < 2 || passage.length() < 120 || Passage.split(passage).size() < 2) {
            return null;
        }
        return new PassageTasks(passage, tasks);
    }

    private static int indexOfLabel(String m, String labels) {
        Matcher x = Pattern.compile("(?im)^\\s*(" + labels + ")\\s*:").matcher(m);
        return x.find() ? x.start() : -1;
    }

    /** Every task done, numbered as asked. */
    public String reply() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            if (b.length() > 0) {
                b.append("\n\n");
            }
            b.append(i + 1).append(". ").append(answer(tasks.get(i)));
        }
        return b.toString();
    }

    // ------------------------------------------------------------------ one task

    private static final Pattern EXACTLY = Pattern.compile("(?i)say exactly\\s*:?\\s*[\"“]([^\"”]+)[\"”]");
    private static final String NOT_STATED = "Not stated in passage.";

    /** One task's answer. */
    public String answer(String task) {
        String t = task.replaceFirst("^\\[[^\\]]*\\]\\s*", ""); // its label ("[Math & State Tracking]")
        Matcher ex = EXACTLY.matcher(t);
        String notStated = ex.find() ? ex.group(1).trim() : NOT_STATED;
        if (t.matches("(?is).*\\bjson\\b.*")) {
            return json(t);
        }
        if (t.matches("(?is).*\\b(summari[sz]e|summary|sum up)\\b.*")) {
            return summary(t);
        }
        List<String> parts = questions(t);
        if (parts.isEmpty()) {
            return notStated;
        }
        boolean steps = t.matches("(?is).*\\bshow\\b.{0,20}\\b(steps?|working|work)\\b.*");
        List<String> out = new ArrayList<String>();
        int known = 0;
        String lastSentence = null;
        for (String q : parts) {
            Found f = answerOne(q, lastSentence, steps);
            if (f == null) {
                out.add(notStated);
            } else {
                known++;
                out.add(f.text);
                lastSentence = f.sentence;
            }
        }
        if (known == 0) {
            return notStated;
        }
        return String.join(" ", out);
    }

    /** The questions a task asks: "Who signed, at what time, and what was the value?" is three. */
    static List<String> questions(String task) {
        List<String> out = new ArrayList<String>();
        String body = task.replaceAll("(?is)\\bif the passage does not state.*$", "").replaceAll("(?is)\\bshow\\b[^.?]*\\.?\\s*$", "");
        for (String sentence : body.split("(?<=\\?)\\s+")) {
            if (!sentence.contains("?")) {
                continue;
            }
            for (String part : sentence.split(",\\s*(?:and\\s+)?(?=(?:at |in |on |by )?(?:what|which|who|whom|whose|when|where|why|how)\\b)")) {
                String p = part.trim().replaceAll("\\?+$", "").trim();
                if (p.length() > 3) {
                    out.add(p + "?");
                }
            }
        }
        return out;
    }

    static final class Found {
        final String text, sentence;

        Found(String text, String sentence) {
            this.text = text;
            this.sentence = sentence;
        }
    }

    private Found answerOne(String q, String lastSentence, boolean steps) {
        String low = q.toLowerCase(Locale.ROOT);
        // never made up: a name or thing the question asks about must be in the passage
        if (!grounded(q)) {
            return null;
        }
        String counted = tracker.answer(q, steps);
        if (counted != null) {
            return new Found(counted, null);
        }
        if (low.matches("^(at )?what time\\b.*|^when\\b.*")) {
            String s = lastSentence != null && TIME.matcher(lastSentence).find() ? lastSentence : sentenceFor(q);
            Matcher m = s == null ? null : TIME.matcher(s);
            if (m != null && m.find()) {
                return new Found("At " + m.group().trim().replaceAll("\\.$", "") + ".", s);
            }
            return null;
        }
        if (low.matches("^(who|whom)\\b.*")) {
            String s = sentenceFor(q);
            if (s == null) {
                return null;
            }
            String who = agent(s, q);
            return who == null ? null : new Found(who + ".", s);
        }
        String s = sentenceFor(q);
        return s == null ? null : new Found(s, s);
    }

    static final Pattern TIME = Pattern.compile("(?i)\\b\\d{1,2}:\\d{2}\\s*(?:[ap]\\.?m\\.?)?|\\b\\d{1,2}\\s*[ap]\\.?m\\.?\\b");
    private static final Pattern NAMED = Pattern.compile("\\b(?:Mr|Mrs|Ms|Dr|Prof|Chief)\\.?\\s+[A-Z][\\p{L}'-]+|\\b[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)*");

    /** Who did it, in the sentence: the one "by" whom it was done, else the name it starts with. */
    private static String agent(String sentence, String q) {
        Matcher by = Pattern.compile("\\bby\\s+((?:Mr|Mrs|Ms|Dr|Prof|Chief)\\.?\\s+[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)?|[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)?)")
                .matcher(sentence);
        String verb = verbOf(q);
        while (by.find()) {
            if (verb == null || sentence.substring(0, by.start()).toLowerCase(Locale.ROOT).contains(verb)) {
                return by.group(1);
            }
        }
        Matcher n = NAMED.matcher(sentence);
        while (n.find()) {
            String name = n.group();
            if (!Passage.split(name).isEmpty() && !name.matches("(The|A|An|On|In|At|Note|Ignore)\\b.*")) {
                return name;
            }
        }
        return null;
    }

    /** The question's verb stem ("signed for" → "sign"). */
    private static String verbOf(String q) {
        Matcher m = Pattern.compile("(?i)^(?:who|whom)\\s+(?:did\\s+|was\\s+|were\\s+|has\\s+|had\\s+)?(\\w+)").matcher(q);
        if (!m.find()) {
            return null;
        }
        String v = m.group(1).toLowerCase(Locale.ROOT);
        return v.length() > 4 ? v.replaceAll("(ed|es|s|ing)$", "") : v;
    }

    /** The passage sentence that shares the most of the question's words (its verb counting double). */
    private String sentenceFor(String q) {
        String best = null;
        double top = 0;
        String verb = verbOf(q);
        List<String> want = Knowledge.meaning(q);
        for (String s : Passage.split(passage)) {
            if (s.matches("(?is)^note\\s*:.*")) {
                continue; // a note to the reader, not what happened
            }
            List<String> have = Knowledge.meaning(s);
            double score = 0;
            for (String w : want) {
                if (have.contains(w)) {
                    score += 1;
                }
            }
            if (verb != null && s.toLowerCase(Locale.ROOT).contains(verb)) {
                score += 2;
            }
            if (score > top) {
                top = score;
                best = s;
            }
        }
        return top >= 1 ? best : null;
    }

    /**
     * Whether what the question asks about is in the passage: every name it uses, and its main thing ("how many
     * batteries"), at least by its stem. A question about something the passage never mentions has no answer here.
     */
    boolean grounded(String q) {
        String low = passage.toLowerCase(Locale.ROOT);
        Matcher n = Pattern.compile("\\b[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)*").matcher(q);
        while (n.find()) {
            String name = n.group();
            if (n.start() == 0 || name.matches("I|I'm|LPG|JSON")) {
                continue; // the question's first word is capitalised anyway
            }
            if (!low.contains(name.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        Matcher many = Pattern.compile("(?i)\\bhow (?:many|much)\\s+([a-z]+)").matcher(q);
        if (many.find()) {
            String thing = Knowledge.stem(many.group(1).toLowerCase(Locale.ROOT));
            if (!thing.matches("money|time|naira|dollars?|more|of") && !low.matches("(?s).*\\b" + Pattern.quote(thing) + ".*")) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ JSON

    private static final Pattern KEY = Pattern.compile("[\"“]([A-Za-z_][A-Za-z0-9_]*)[\"”]\\s*\\(([^)]*)\\)");

    String json(String task) {
        StringBuilder b = new StringBuilder("{");
        Matcher k = KEY.matcher(task);
        boolean first = true;
        while (k.find()) {
            String key = k.group(1), type = k.group(2).toLowerCase(Locale.ROOT);
            if (!first) {
                b.append(", ");
            }
            first = false;
            b.append('"').append(key).append("\": ").append(value(key, type));
        }
        return b.append('}').toString();
    }

    private String value(String key, String type) {
        String words = key.replace('_', ' ').toLowerCase(Locale.ROOT);
        if (type.contains("bool")) {
            String what = words.replaceAll("\\b(mentioned|stated|named|present|is|in|passage)\\b", "").trim();
            return String.valueOf(!what.isEmpty() && passage.toLowerCase(Locale.ROOT).contains(what));
        }
        if (type.contains("int") || type.contains("number")) {
            Long n = tracker.total(words);
            return n == null ? "null" : String.valueOf(n);
        }
        if (type.contains("array") || type.contains("list")) {
            List<String> names = tracker.people.isEmpty() ? Passage.names(passage) : new ArrayList<String>(tracker.people.keySet());
            StringBuilder b = new StringBuilder("[");
            for (int i = 0; i < names.size(); i++) {
                b.append(i == 0 ? "" : ", ").append('"').append(names.get(i).replace("\"", "\\\"")).append('"');
            }
            return b.append(']').toString();
        }
        String s = sentenceFor(words);
        return s == null ? "null" : '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    // ------------------------------------------------------------------ a summary under rules

    private static final Pattern WORDS_AT_MOST = Pattern.compile("(?i)(?:no more than|at most|maximum of|max(?:imum)?|under|fewer than|up to)\\s+(\\d+)\\s+words");
    private static final Pattern NO_LETTER = Pattern.compile("(?i)(?:not use|without|avoid|no)\\s+(?:the\\s+)?letter\\s+[\"'“‘]?([a-z])[\"'”’]?");

    String summary(String task) {
        Matcher w = WORDS_AT_MOST.matcher(task);
        int most = w.find() ? Integer.parseInt(w.group(1)) - (w.group().toLowerCase(Locale.ROOT).contains("fewer") ? 1 : 0) : 25;
        Matcher l = NO_LETTER.matcher(task);
        char banned = l.find() ? Character.toLowerCase(l.group(1).charAt(0)) : 0;
        String said = Lipogram.summary(this, most, banned);
        return said != null ? said
                : "I couldn't write a sentence that keeps every rule (" + most + " words at most"
                + (banned == 0 ? "" : ", no letter “" + banned + "”") + ") and still says what happened.";
    }

    // ------------------------------------------------------------------ the people and their counts

    /** Follows each person's count and money through the passage, step by step. */
    static final class Tracker {
        static final class Person {
            final String name;
            String unit = "", unitPhrase = "";
            long count, start;
            boolean counted;
            long delivered, sold, unsold;
            boolean soldKnown;
            BigDecimal value;
            String currency = "";
            double damaged = -1;
            final List<String> steps = new ArrayList<String>();
            /** The count after each place it was in ("Warri" → 14). */
            final Map<String, Long> after = new LinkedHashMap<String, Long>();

            Person(String name) {
                this.name = name;
            }
        }

        final Map<String, Person> people = new LinkedHashMap<String, Person>();

        private static final String NUM = "(\\d[\\d,]*(?:\\.\\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
                + "thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|thirty|forty|fifty|hundred)";
        private static final String[] SMALL = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
                + "fifteen sixteen seventeen eighteen nineteen twenty").split(" ");

        static long number(String s) {
            String t = s.toLowerCase(Locale.ROOT).replace(",", "");
            for (int i = 0; i < SMALL.length; i++) {
                if (SMALL[i].equals(t)) {
                    return i;
                }
            }
            switch (t) {
                case "thirty": return 30;
                case "forty": return 40;
                case "fifty": return 50;
                case "hundred": return 100;
                default: return (long) Double.parseDouble(t);
            }
        }

        private static final Pattern FRACTION = Pattern.compile("(?i)\\b(half|a half|one-half|two-thirds|one-third|a third|"
                + "three-quarters|a quarter|one-quarter|one-fifth|a fifth|\\d+(?:\\.\\d+)?\\s*%|\\d+/\\d+)\\s+of\\b");

        static double fraction(String f) {
            String t = f.toLowerCase(Locale.ROOT).trim();
            if (t.endsWith("%")) {
                return Double.parseDouble(t.replace("%", "").trim()) / 100;
            }
            if (t.matches("\\d+/\\d+")) {
                String[] p = t.split("/");
                return Double.parseDouble(p[0]) / Double.parseDouble(p[1]);
            }
            switch (t) {
                case "two-thirds": return 2.0 / 3;
                case "one-third": case "a third": return 1.0 / 3;
                case "three-quarters": return 0.75;
                case "a quarter": case "one-quarter": return 0.25;
                case "one-fifth": case "a fifth": return 0.2;
                default: return 0.5;
            }
        }

        static Tracker read(String passage) {
            Tracker t = new Tracker();
            List<String> names = new ArrayList<String>();
            // the people: names listed together ("three couriers—Kemi, Tunde, and Musa—"), else those who act
            Matcher list = Pattern.compile("([A-Z][a-z]+)(?:\\s*,\\s*([A-Z][a-z]+))*,?\\s*(?:,\\s*)?and\\s+([A-Z][a-z]+)").matcher(passage);
            if (list.find()) {
                for (String n : list.group().split("\\s*,\\s*|\\s+and\\s+|,?\\s*and\\s+")) {
                    n = n.replaceAll("[^\\p{L}]", "");
                    if (!n.isEmpty() && !names.contains(n)) {
                        names.add(n);
                    }
                }
            }
            for (String n : names) {
                t.people.put(n, new Person(n));
            }
            Person current = null;
            for (String sentence : Passage.split(passage)) {
                if (sentence.matches("(?is)^note\\s*:.*")) {
                    continue; // a note to the reader: never an event, never an order
                }
                for (String clause : sentence.split(";\\s*|,\\s+and\\s+(?=(?:the\\s+)?[a-z]+\\s+(?:was|were)\\b)|,\\s+(?=and\\s+)|(?<=\\w),\\s+(?=[a-z]+ed\\b)")) {
                    String c = clause.replaceFirst("^and\\s+", "").trim();
                    Person p = t.subject(c);
                    if (p != null) {
                        current = p;
                    }
                    if (current != null) {
                        t.event(current, c);
                    }
                }
            }
            return t;
        }

        private Person subject(String clause) {
            for (Person p : people.values()) {
                if (clause.matches("(?s)^" + Pattern.quote(p.name) + "\\b.*")) {
                    return p;
                }
            }
            return null;
        }

        private void event(Person p, String c) {
            Matcher m;
            // "started with 15 fewer cylinders than Kemi"
            if ((m = Pattern.compile("(?i)\\b(?:started|began|left|set off|carried|had)\\s+with\\s+" + NUM + "\\s+(fewer|less|more)\\s+(\\w+)\\s+than\\s+([A-Z][a-z]+)").matcher(c)).find()) {
                Person o = people.get(m.group(4));
                if (o != null && o.counted) {
                    // compared with what the other one started with, not what they had left later
                    long n = number(m.group(1));
                    boolean fewer = !m.group(2).equalsIgnoreCase("more");
                    p.count = p.start = o.start + (fewer ? -n : n);
                    p.counted = true;
                    p.unit = o.unit;
                    p.unitPhrase = o.unitPhrase;
                    p.steps.add(o.start + (fewer ? " − " : " + ") + n + " = " + p.count + " to start");
                }
            } else if ((m = Pattern.compile("(?i)\\b(?:carried|started with|had|took|loaded|left with|began with|bought)\\s+" + NUM
                    + "\\s+([a-z]+(?:\\s+of\\s+[A-Z]{2,}|\\s+of\\s+[a-z]+)?)").matcher(c)).find()) {
                p.count = p.start = number(m.group(1));
                p.counted = true;
                p.unitPhrase = m.group(2);
                p.unit = Knowledge.stem(m.group(2).split("\\s+")[0].toLowerCase(Locale.ROOT));
                p.steps.add("starts with " + p.count);
            }
            // "worth ₦1,450,000 in total"
            if ((m = Pattern.compile("(?i)\\bworth\\s+([₦$£€]|N|NGN|USD)?\\s?(\\d[\\d,]*(?:\\.\\d+)?)").matcher(c)).find()) {
                p.currency = m.group(1) == null ? "" : m.group(1);
                p.value = new BigDecimal(m.group(2).replace(",", ""));
            }
            // "20% of the batch was damaged"
            if ((m = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)\\s*%\\s+of\\s+(?:the\\s+)?\\w+\\s+(?:was|were)\\s+(?:damaged|broken|lost|spoilt|spoiled|stolen|ruined)").matcher(c)).find()) {
                p.damaged = Double.parseDouble(m.group(1)) / 100;
            }
            // "delivered two-thirds of them in Warri"
            Matcher f;
            if ((m = Pattern.compile("(?i)\\b(delivered|sold|dropped off|unloaded|gave away|used)\\s+(.+)").matcher(c)).find() && p.counted) {
                String rest = m.group(2);
                long n = -1;
                if ((f = FRACTION.matcher(rest)).find()) {
                    double fr = fraction(f.group(1));
                    n = Math.round(p.count * fr);
                    p.steps.add(f.group(1) + " of " + p.count + " = " + n + " " + m.group(1).toLowerCase(Locale.ROOT));
                } else if ((f = Pattern.compile("^" + NUM + "\\b").matcher(rest)).find()) {
                    n = number(f.group(1));
                    p.steps.add(n + " " + m.group(1).toLowerCase(Locale.ROOT));
                }
                if (n >= 0) {
                    p.count -= n;
                    if (m.group(1).equalsIgnoreCase("sold")) {
                        p.sold += n;
                        p.soldKnown = true;
                    } else {
                        p.delivered += n;
                    }
                    p.steps.add(p.count + " left");
                    Matcher place = Pattern.compile("\\b(?:in|at)\\s+([A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)?)").matcher(rest);
                    if (place.find()) {
                        p.after.put(place.group(1), p.count);
                    }
                }
            }
            // "picked up 18 more at the Jesse junction"
            if ((m = Pattern.compile("(?i)\\b(?:picked up|collected|received|got|bought|added|loaded)\\s+" + NUM + "\\s+more").matcher(c)).find() && p.counted) {
                long n = number(m.group(1));
                p.steps.add(p.count + " + " + n + " = " + (p.count + n));
                p.count += n;
            }
            // "returned to the depot with 9 cylinders unsold"
            if ((m = Pattern.compile("(?i)\\bwith\\s+" + NUM + "\\s+(?:\\w+\\s+)?(unsold|left|remaining|left over)").matcher(c)).find() && p.counted) {
                long n = number(m.group(1));
                p.unsold = n;
                p.sold += p.count - n;
                p.soldKnown = true;
                p.steps.add(p.count + " − " + n + " unsold = " + (p.count - n) + " sold");
                p.count = n;
            }
        }

        /** An answer about a count or a value, with its steps when asked; null when the question isn't one. */
        String answer(String q, boolean steps) {
            String low = q.toLowerCase(Locale.ROOT);
            Person p = null;
            for (Person x : people.values()) {
                if (q.contains(x.name)) {
                    p = x;
                }
            }
            if (low.matches(".*\\b(value|worth|cost|naira|amount)\\b.*")) {
                Person v = p;
                if (v == null) {
                    for (Person x : people.values()) {
                        if (x.value != null) {
                            v = x;
                        }
                    }
                }
                if (v == null || v.value == null) {
                    return null;
                }
                boolean undamaged = low.matches(".*\\b(undamaged|unbroken|intact|good|delivered|signed|rest|remaining)\\b.*") && v.damaged >= 0;
                boolean damaged = !undamaged && low.matches(".*\\bdamaged\\b.*") && v.damaged >= 0;
                double share = undamaged ? 1 - v.damaged : damaged ? v.damaged : 1;
                BigDecimal worth = v.value.multiply(BigDecimal.valueOf(share)).setScale(0, java.math.RoundingMode.HALF_UP);
                String money = v.currency + String.format(Locale.US, "%,d", worth.longValue());
                return share == 1 ? money + "." : "Worth " + money + " (" + Math.round(share * 100) + "% of " + v.currency
                        + String.format(Locale.US, "%,d", v.value.longValue()) + ").";
            }
            if (p == null || !p.counted || !low.matches(".*\\bhow (many|much)\\b.*")) {
                return null;
            }
            String unit = p.unitPhrase.isEmpty() ? "" : " " + p.unit + (p.unit.endsWith("s") ? "" : "s");
            String how = steps && !p.steps.isEmpty() ? " (" + String.join("; ", p.steps) + ")" : "";
            if (low.matches(".*\\b(sell|sold)\\b.*") && p.soldKnown) {
                return p.name + " sold " + p.sold + unit + how + ".";
            }
            if (low.matches(".*\\b(deliver|delivered)\\b.*")) {
                return p.name + " delivered " + p.delivered + unit + how + ".";
            }
            if (low.matches(".*\\b(left|remain|remaining|still had|have)\\b.*")) {
                for (Map.Entry<String, Long> e : p.after.entrySet()) {
                    if (low.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                        String only = steps ? " (" + String.join("; ", p.steps.subList(0, Math.min(p.steps.size(), stepsTo(p, e.getValue())))) + ")" : "";
                        return p.name + " had " + e.getValue() + unit + " left after " + e.getKey() + only + ".";
                    }
                }
                return p.name + " had " + p.count + unit + " left" + how + ".";
            }
            return null;
        }

        /** How many steps it took to reach that count first. */
        private static int stepsTo(Person p, long count) {
            for (int i = 0; i < p.steps.size(); i++) {
                if (p.steps.get(i).equals(count + " left")) {
                    return i + 1;
                }
            }
            return p.steps.size();
        }

        /** A total asked for by its words ("total lpg delivered or sold"): what everyone delivered and/or sold of it. */
        Long total(String words) {
            boolean delivered = words.contains("deliver"), sold = words.contains("sold") || words.contains("sell");
            if (!delivered && !sold) {
                return null;
            }
            long sum = 0;
            boolean any = false;
            for (Person p : people.values()) {
                if (!p.counted) {
                    continue;
                }
                String thing = (p.unitPhrase + " " + p.unit).toLowerCase(Locale.ROOT);
                boolean fits = false;
                for (String w : words.split(" ")) {
                    if (w.length() > 2 && !w.matches("total|delivered|deliver|sold|sell|or|and|number|count") && thing.contains(w)) {
                        fits = true;
                    }
                }
                if (!fits && words.replaceAll("\\b(total|delivered|deliver|sold|sell|or|and|number|count)\\b", "").trim().length() > 0) {
                    continue;
                }
                sum += (delivered ? p.delivered : 0) + (sold ? p.sold : 0);
                any = true;
            }
            return any ? sum : null;
        }
    }
}
