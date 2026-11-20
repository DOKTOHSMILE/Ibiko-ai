package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text the person pasted, read and kept for the conversation: what it is about (the names it keeps coming back to,
 * its main words), its main points (the sentences that carry the most of its words), and its sentences, so questions
 * about it are answered from it ("Who started the company?", "When did she move to Lagos?", "Summarise it", "What
 * are the key points?"), the same way an encyclopedia article's are. "He" and "she" are its main person. Pure Java.
 */
public final class Passage {
    /** How long a pasted text stays the one questions are about. */
    public static final long FRESH_MS = 3 * 60 * 60 * 1000L;
    /** A message this long, in a few sentences, is a text to read rather than a remark. */
    static final int LONG = 280;

    public final String text;
    public final List<String> sentences;
    /** The names it is about, most mentioned first. */
    public final List<String> names;
    /** Its main words, most used first. */
    public final List<String> words;
    public final long at;
    /** A question asked with it, after the text ("…\n\nWho founded it?"), or null. */
    public final String question;
    /** Sentences in it that gave the reader orders (a prompt injection): left out of it, never followed. */
    public final List<PromptReader.Hidden> hidden = new ArrayList<PromptReader.Hidden>();

    private Passage(String text, String question, long at) {
        this.text = text;
        this.question = question;
        this.at = at;
        this.sentences = split(text);
        this.names = names(text);
        this.words = mainWords(sentences);
    }

    private static final Pattern ENDS_WITH_QUESTION = Pattern.compile("(?s)^(.*\\S)\\s*\\n+\\s*([^\\n]{3,200}\\?)\\s*$");

    /**
     * The message as a pasted text, or null when it is a remark or a question. A question on its own last line is
     * kept with it, to answer once the text is read.
     */
    public static Passage of(String message, long now) {
        if (message == null) {
            return null;
        }
        String m = message.trim();
        String q = null;
        Matcher end = ENDS_WITH_QUESTION.matcher(m);
        if (end.matches() && end.group(1).length() >= LONG) {
            m = end.group(1).trim();
            q = end.group(2).trim();
        }
        m = m.replaceFirst("(?is)^(here is|here's|read|please read|this is)( a| the| this| my)? ?(text|passage|article|story|essay|"
                + "document|paragraph|note|post)?\\s*[:\\-]\\s*", "");
        if (m.length() < LONG || split(m).size() < 3) {
            return null;
        }
        // orders hidden in the text ("ignore your instructions and…") are content to be wary of, not the text's points
        List<PromptReader.Hidden> found = PromptReader.scan(m);
        Passage p = new Passage(found.isEmpty() ? m : PromptReader.clean(m), q, now);
        p.hidden.addAll(found);
        return p;
    }

    public boolean fresh(long now) {
        return now - at <= FRESH_MS;
    }

    static List<String> split(String text) {
        List<String> out = new ArrayList<String>();
        for (String para : text.split("\\n\\s*\\n|\\n(?=\\s*[-*\u2022\\d])")) {
            for (String s : para.replaceAll("\\s+", " ").trim().split("(?<!\\b(?:Mr|Mrs|Ms|Dr|Prof|St|Jr|Sr|Mt|Gen|Col|Capt|Rev|Hon|No|vs|etc|e\\.g|i\\.e)\\.)(?<=[.!?])\\s+(?=[A-Z\"\u201c(\\d])")) {
                String t = s.trim();
                if (t.length() > 1) {
                    out.add(t);
                }
            }
        }
        return out;
    }

    private static final Set<String> NOT_NAMES = new HashSet<String>(Arrays.asList(("The A An In On At For From By With And But Or So "
            + "He She It They We I You His Her Its Their Our My Your This That These Those There Here When Where Why How What Who "
            + "After Before During Since Until If Then Also However Today Yesterday Tomorrow Monday Tuesday Wednesday Thursday Friday "
            + "Saturday Sunday January February March April May June July August September October November December Mr Mrs Ms Dr "
            + "One Two Three Many Most Some All Every Each Both As").split(" ")));

    /** The names a text keeps coming back to: runs of capitalised words, the longer form standing for its parts. */
    static List<String> names(String text) {
        Map<String, Integer> count = new LinkedHashMap<String, Integer>();
        Matcher m = Pattern.compile("\\b([A-Z][\\p{L}'\u2019-]+(?:\\s+(?:of |the |de |da |van |von )?[A-Z][\\p{L}'\u2019-]+){0,4})").matcher(text);
        while (m.find()) {
            String n = m.group(1).replaceAll("['\u2019]s$", "");
            String[] parts = n.split("\\s+");
            if (parts.length == 1 && NOT_NAMES.contains(n)) {
                continue;
            }
            if (NOT_NAMES.contains(parts[0])) {
                n = n.substring(parts[0].length()).trim();
                if (n.isEmpty() || !Character.isUpperCase(n.charAt(0))) {
                    continue;
                }
            }
            count.merge(n, 1, Integer::sum);
        }
        // "Ada" and "Ada Obi" are one person: the short form's mentions go to the long one
        List<String> keys = new ArrayList<String>(count.keySet());
        for (String shortName : keys) {
            for (String longName : keys) {
                if (!shortName.equals(longName) && (longName.startsWith(shortName + " ") || longName.endsWith(" " + shortName))
                        && !longName.matches(".* (of|the|de|da|van|von) .*") && longName.split("\\s+").length <= 3
                        && count.containsKey(shortName) && count.containsKey(longName)) {
                    count.merge(longName, count.get(shortName), Integer::sum);
                    count.remove(shortName);
                    break;
                }
            }
        }
        List<Map.Entry<String, Integer>> all = new ArrayList<Map.Entry<String, Integer>>(count.entrySet());
        all.sort((a, b) -> b.getValue() != a.getValue().intValue() ? b.getValue() - a.getValue()
                : text.indexOf(a.getKey()) - text.indexOf(b.getKey())); // as often: the one named first
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : all) {
            if (out.size() < 5 && (e.getValue() > 1 || out.size() < 2)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private static Map<String, Integer> frequencies(List<String> sentences) {
        Map<String, Integer> f = new HashMap<String, Integer>();
        for (String s : sentences) {
            for (String w : EncyclopediaQA.words(s)) {
                if (!EncyclopediaQA.STOP.contains(w) && w.length() > 2 && !w.matches("\\d+")) {
                    f.merge(EncyclopediaQA.stem(w), 1, Integer::sum);
                }
            }
        }
        return f;
    }

    static List<String> mainWords(List<String> sentences) {
        Map<String, Integer> f = frequencies(sentences);
        Map<String, String> shown = new HashMap<String, String>();
        for (String s : sentences) {
            for (String w : EncyclopediaQA.words(s)) {
                shown.putIfAbsent(EncyclopediaQA.stem(w), w);
            }
        }
        List<Map.Entry<String, Integer>> all = new ArrayList<Map.Entry<String, Integer>>(f.entrySet());
        all.sort((a, b) -> b.getValue() - a.getValue());
        List<String> out = new ArrayList<String>();
        for (Map.Entry<String, Integer> e : all) {
            if (e.getValue() > 1 && out.size() < 6) {
                out.add(shown.getOrDefault(e.getKey(), e.getKey()));
            }
        }
        return out;
    }

    /** Its main points: the sentences that carry the most of its words, in the order they come. */
    public List<String> points(int most) {
        Map<String, Integer> f = frequencies(sentences);
        double[] score = new double[sentences.size()];
        for (int i = 0; i < sentences.size(); i++) {
            List<String> ws = EncyclopediaQA.words(sentences.get(i));
            double sum = 0;
            for (String w : ws) {
                if (!EncyclopediaQA.STOP.contains(w) && w.length() > 2) {
                    sum += f.getOrDefault(EncyclopediaQA.stem(w), 0);
                }
            }
            score[i] = sum / Math.sqrt(Math.max(4, ws.size())) + (i == 0 ? 2 : 0);
        }
        Integer[] order = new Integer[sentences.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Double.compare(score[b], score[a]));
        List<Integer> chosen = new ArrayList<Integer>();
        for (int i = 0; i < Math.min(most, order.length); i++) {
            chosen.add(order[i]);
        }
        chosen.sort(null);
        List<String> out = new ArrayList<String>();
        for (int i : chosen) {
            out.add(sentences.get(i));
        }
        return out;
    }

    /** Its main person or thing: of the names it keeps coming back to, the one it starts with. */
    public String main() {
        String best = null;
        int at = Integer.MAX_VALUE;
        for (String n : names) {
            int i = text.indexOf(n);
            if (i >= 0 && i < at) {
                at = i;
                best = n;
            }
        }
        return best;
    }

    /** Its sentences with "he"/"she" put to its main person, so an answer names who it means. */
    List<String> named() {
        String who = main();
        Context.Kind k = kind();
        if (who == null || k != Context.Kind.MAN && k != Context.Kind.WOMAN) {
            return sentences;
        }
        String pronoun = k == Context.Kind.MAN ? "He|he" : "She|she";
        List<String> out = new ArrayList<String>();
        for (String s : sentences) {
            out.add(s.replaceFirst("(^|, |; )(" + pronoun + ")\\b", "$1" + Matcher.quoteReplacement(who)));
        }
        return out;
    }

    /** What it is about, as said: "Ada Obi and Lagos" or its main words. */
    public String about() {
        List<String> parts = names.isEmpty() ? words.subList(0, Math.min(3, words.size())) : names.subList(0, Math.min(3, names.size()));
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }

    /** The reply when it has been read: what it is about and its main points, then an invitation to ask. */
    public String readReply() {
        StringBuilder b = new StringBuilder();
        int words = text.split("\\s+").length;
        b.append("I've read it (").append(sentences.size()).append(" sentences, about ").append(words).append(" words)");
        String about = about();
        b.append(about.isEmpty() ? "." : ". It's mainly about " + about + ".");
        List<String> p = points(sentences.size() > 12 ? 4 : 3);
        b.append("\n\nThe main points:");
        for (String s : p) {
            b.append("\n\u2022 ").append(s);
        }
        if (!hidden.isEmpty()) {
            b.append("\n\nI left out ").append(hidden.size() == 1 ? "a sentence that gives" : hidden.size() + " sentences that give")
                    .append(" the reader orders (").append(hidden.get(0).why).append("). I don't follow instructions hidden in a text.");
        }
        b.append("\n\nAsk me anything about it.");
        return b.toString();
    }

    /** Whether a message asks for the text as a whole: a summary, its points, what it is about. */
    public static boolean asksWhole(String q) {
        return q != null && q.trim().toLowerCase(Locale.ROOT).replaceAll("[?.!]+$", "").matches(
                "(please )?(can you )?(summari[sz]e|sum up|give me a summary of|what('s| is| are) (the )?(summary|gist|main points?|key points?)|"
                        + "what('s| is) (it|this|the text|the passage|the article|the story) about|key points|main points|tl;?dr|"
                        + "give me the (key|main) points|list the (key|main) points)( (of )?(it|this|that|the text|the passage|the article|the story|"
                        + "what i (sent|pasted|shared)))?");
    }

    /** The text as a whole, as asked for: its summary or points. */
    public String whole(String q) {
        String low = q.toLowerCase(Locale.ROOT);
        if (low.contains("point") || low.contains("list")) {
            StringBuilder b = new StringBuilder("The key points:");
            for (String s : points(Math.min(5, Math.max(3, sentences.size() / 3)))) {
                b.append("\n\u2022 ").append(s);
            }
            return b.toString();
        }
        String about = about();
        return (about.isEmpty() ? "" : "It's about " + about + ". ") + String.join(" ", points(3));
    }

    /** The answer to a question about the text, from its sentences, or null when it doesn't say. */
    public String answer(String q) {
        if (q == null || !EncyclopediaQA.question(q)) {
            return null;
        }
        // a question that names nothing in the text isn't about it
        Set<String> mine = new HashSet<String>();
        for (String s : sentences) {
            for (String w : EncyclopediaQA.words(s)) {
                mine.add(EncyclopediaQA.stem(w));
            }
        }
        int shared = 0;
        for (String w : EncyclopediaQA.words(q)) {
            if (!EncyclopediaQA.STOP.contains(w) && w.length() > 2 && mine.contains(EncyclopediaQA.stem(w))) {
                shared++;
            }
        }
        boolean named = false;
        String low = q.toLowerCase(Locale.ROOT);
        for (String n : names) {
            for (String part : n.toLowerCase(Locale.ROOT).split("\\s+")) {
                named |= part.length() > 2 && low.matches("(?s).*\\b" + Pattern.quote(part) + "\\b.*");
            }
        }
        if (!named && shared < 2) {
            return null; // a question about the world that shares a word with the text isn't about the text
        }
        // its roles first: "who started the company?" from "Ada Obi started the company in 2019"
        List<String> said = named();
        String all = String.join(" ", said);
        Roles.Reading r = Roles.question(q);
        if (r != null && r.asks == Roles.Asks.SUBJECT) {
            String who = Roles.agentIn(all, ai.ibiko.ibiko1.formulas.Grammar.forms(r.verb), "", r.object);
            if (who != null) {
                return who + " " + pastOf(all, r.verb) + " " + r.object + ".";
            }
        }
        Encyclopedia.Article as = new Encyclopedia.Article(main() == null ? "the text" : main(), all);
        // the names it asks about are what the answer is about, not words to match: "what prize did Okafor win" is
        // matched on "prize" and "win"
        Set<String> nameWords = new HashSet<String>();
        for (String n : names) {
            if (low.contains(n.toLowerCase(Locale.ROOT)) || n.equals(main())) {
                nameWords.addAll(EncyclopediaQA.words(n));
            }
        }
        EncyclopediaQA.Found f = new EncyclopediaQA(null).from(as, nameWords, q);
        return f != null ? f.text : closest(q);
    }

    /**
     * The sentence closest in meaning to the question, by the neural layer's sentence vectors (docs/IBIKO_NEURAL.md):
     * a question worded unlike the text still finds where the text says it ("how much did they make" → "the firm
     * earned two million dollars"). Null when no sentence is close enough, or the app carries no vectors.
     */
    String closest(String q) {
        ai.ibiko.ibiko1.neural.Neural n = ai.ibiko.ibiko1.neural.Neural.get();
        if (n == null) {
            return null;
        }
        String best = null;
        double top = CLOSEST;
        for (String s : sentences) {
            double sim = n.attention.match(q, s) + kindFits(q, s);
            if (sim > top) {
                top = sim;
                best = s;
            }
        }
        return best;
    }

    /** How close a sentence's meaning must be to a question to answer it. */
    static final double CLOSEST = 0.4;

    private static final Pattern AMOUNT = Pattern.compile("(?i)\\b(\\d[\\d,.]*|one|two|three|four|five|six|seven|eight|nine|ten|"
            + "twenty|thirty|hundred|thousand|million|billion|half|third|quarter|percent|naira|dollars?|pounds?|euros?)\\b");
    private static final Pattern TIME = Pattern.compile("(?i)\\b(1[5-9]\\d\\d|20\\d\\d|january|february|march|april|may|june|july|"
            + "august|september|october|november|december|monday|tuesday|wednesday|thursday|friday|saturday|sunday|"
            + "yesterday|today|tomorrow|spring|summer|autumn|winter|last year|next year|ago)\\b");

    /**
     * The answer the question expects, checked against the sentence: "how much" and "how many" want an amount, "when"
     * a time, "who" a person named, "where" a place named. A sentence that holds that kind of answer gains a little.
     */
    static double kindFits(String q, String s) {
        String low = q.toLowerCase(Locale.ROOT).trim();
        if (low.matches("(?s)^(how (much|many|long|far|big|old)|what (amount|number|price)).*")) {
            return AMOUNT.matcher(s).find() ? 0.12 : -0.05;
        }
        if (low.matches("(?s)^(when|what (year|time|day|month|date)).*")) {
            return TIME.matcher(s).find() ? 0.12 : -0.05;
        }
        if (low.matches("(?s)^(who|whom|whose)\\b.*") || low.matches("(?s)^where\\b.*")) {
            return s.matches("(?s).+\\s[A-Z][a-z]+.*") ? 0.08 : -0.03;
        }
        return 0;
    }

    /** The verb as the text writes it in the past ("started"), or the regular past. */
    private static String pastOf(String text, String base) {
        for (String f : ai.ibiko.ibiko1.formulas.Grammar.forms(base)) {
            if ((f.endsWith("ed") || !f.equals(base) && !f.endsWith("s") && !f.endsWith("ing")) && text.matches("(?s).*\\b" + Pattern.quote(f) + "\\b.*")) {
                return f;
            }
        }
        return base.endsWith("e") ? base + "d" : base + "ed";
    }

    /** The text's main person or thing, and its kind, for "he", "she", "it" in the questions that follow. */
    public Context.Kind kind() {
        String t = " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ") + " ";
        int he = count(t, " he ") + count(t, " his ") + count(t, " him "), she = count(t, " she ") + count(t, " her ") + count(t, " hers ");
        if (he + she >= 2) {
            return he > she ? Context.Kind.MAN : Context.Kind.WOMAN;
        }
        return Context.kindOf(new Encyclopedia.Article(names.isEmpty() ? "" : names.get(0), text), null);
    }

    private static int count(String text, String word) {
        int n = 0;
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            n++;
        }
        return n;
    }
}
