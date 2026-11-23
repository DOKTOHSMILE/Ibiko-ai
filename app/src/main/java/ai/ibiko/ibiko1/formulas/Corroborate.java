package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Social media as a cross-check, never as the source: an answer found in the encyclopedia or on the web is held up
 * against what people post. A post counts only when it states the claim (it names the thing asked about and the
 * answer's own words: the winner, the date, the number), from an account of its own: the same account twice,
 * reposts and copied text count once; a question, a rumour ("I heard", "is it true", "allegedly") or a joke is not a
 * statement and isn't counted. Posts that say it is false (fake, hoax, debunked, not true) are counted against.
 * What comes out is how many independent accounts agree and how many dispute it, in words: support, not proof.
 * Pure Java.
 *
 * <pre>
 * S1  states   a post that names the thing and the answer's words, said as fact
 * S2  apart    one vote an account; near-identical text is one voice, however many post it
 * S3  against  a post that names the thing and calls it false, or names another answer for the same thing
 * S4  weigh    agreeing accounts against disputing ones: confirmed, mixed, disputed, or not enough to say
 * </pre>
 */
public final class Corroborate {
    private Corroborate() {
    }

    /** One post: where, who, what it says. */
    public static final class Post {
        public final String platform, author, text;

        public Post(String platform, String author, String text) {
            this.platform = platform == null ? "" : platform;
            this.author = author == null ? "" : author.trim().toLowerCase(Locale.US);
            this.text = text == null ? "" : text;
        }
    }

    /** What the posts came to. */
    public static final class Verdict {
        /** Independent accounts that state the answer, and that dispute it. */
        public final int agree, dispute;
        /** Posts looked at, and the platforms they came from. */
        public final int read;
        public final Set<String> platforms;

        Verdict(int agree, int dispute, int read, Set<String> platforms) {
            this.agree = agree;
            this.dispute = dispute;
            this.read = read;
            this.platforms = platforms;
        }

        /** S4: enough agreeing accounts, and few disputing. */
        public boolean confirmed() {
            return agree >= 3 && dispute * 3 <= agree;
        }

        public boolean disputed() {
            return dispute >= 2 && dispute >= agree;
        }

        /** In words, for the answer's note: "Social media: 5 accounts agree (Reddit, X)". */
        public String said() {
            String where = platforms.isEmpty() ? "" : " (" + String.join(", ", platforms) + ")";
            if (read == 0) {
                return "";
            }
            if (agree == 0 && dispute == 0) {
                return "Social media: " + read + " posts read, none states it either way";
            }
            String a = agree + (agree == 1 ? " account agrees" : " accounts agree");
            String d = dispute + (dispute == 1 ? " disputes it" : " dispute it");
            return "Social media: " + (agree > 0 ? a : "") + (agree > 0 && dispute > 0 ? ", " : "") + (dispute > 0 ? d : "") + where
                    + (confirmed() ? ": it holds up" : disputed() ? ": treat it with care" : "");
        }
    }

    /** A post that isn't a statement of fact: a question, a rumour, a joke, a guess. */
    private static final Pattern NOT_A_STATEMENT = Pattern.compile("(?i)(\\?\\s*$|\\b(is it true|is this true|did .{0,40} really|i heard|"
            + "rumou?rs?|allegedly|reportedly|apparently|unconfirmed|they say|people say|if true|could be|might be|maybe|i think|"
            + "i guess|lol|lmao|joke|jk|satire|parody|prediction|predict|my pick|bet on|fantasy)\\b)");
    private static final Pattern CALLS_FALSE = Pattern.compile("(?i)\\b(fake|false|hoax|debunked|not true|untrue|misinformation|"
            + "fact[- ]?check(ed)?: false|never happened|didn't happen|did not happen|wrong)\\b");

    /**
     * The words that make the answer the answer: names, numbers and dates in it that the question didn't already have
     * ("Argentina" for "who won the 2022 World Cup?"). These, with the thing asked about, are what a post must state.
     */
    public static Set<String> answerWords(String question, String answer) {
        Set<String> asked = new HashSet<String>(words(question));
        Asking.Frame f = Asking.of(question);
        String first = (answer == null ? "" : answer).split("(?<=[.!?])\\s+")[0];
        Set<String> out = new LinkedHashSet<String>();
        boolean numeric = f.wants == Asking.Wants.WHEN || f.wants == Asking.Wants.HOW_MANY || f.wants == Asking.Wants.HOW_MUCH
                || f.wants == Asking.Wants.HOW_LONG || f.wants == Asking.Wants.HOW_OLD || f.wants == Asking.Wants.MEASURE;
        if (numeric) {
            // the date or number that answers: its figures (and a month's name)
            Matcher n = Pattern.compile("\\b\\d[\\d,.]*\\b|\\b(January|February|March|April|May|June|July|August|September|October|"
                    + "November|December)\\b").matcher(first);
            while (n.find()) {
                String w = n.group().toLowerCase(Locale.US).replaceAll("[,.]$", "");
                if (!asked.contains(w)) {
                    out.add(w);
                }
            }
            return out;
        }
        // a name answers who, which, whose: the first name in the answer the question didn't have
        Matcher names = Pattern.compile("\\b[A-Z][\\p{L}'-]+(?:\\s+[A-Z][\\p{L}'-]+)*").matcher(first);
        while (names.find()) {
            List<String> fresh = new ArrayList<String>();
            for (String w : words(names.group())) {
                if (!asked.contains(w) && w.length() > 2 && !COMMON.contains(w)) {
                    fresh.add(w);
                }
            }
            if (!fresh.isEmpty()) {
                out.addAll(fresh);
                return out;
            }
        }
        return out;
    }

    private static final Set<String> COMMON = new HashSet<String>(java.util.Arrays.asList("the", "and", "fifa", "world", "cup",
            "this", "that", "it", "in", "on", "after", "before", "during", "final", "first", "january", "february", "march", "april",
            "may", "june", "july", "august", "september", "october", "november", "december"));

    static List<String> words(String s) {
        List<String> out = new ArrayList<String>();
        for (String w : (s == null ? "" : s).toLowerCase(Locale.US).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** S1–S4: the posts weighed against the answer to {@code question}. */
    public static Verdict weigh(String question, String answer, List<Post> posts) {
        Asking.Frame frame = Asking.of(question);
        Set<String> key = answerWords(question, answer);
        List<String> about = frame.about.isEmpty() ? new ArrayList<String>() : words(frame.about);
        Set<String> agreeing = new LinkedHashSet<String>(), disputing = new LinkedHashSet<String>();
        List<String> seenTexts = new ArrayList<String>();
        Set<String> platforms = new LinkedHashSet<String>();
        int read = 0;
        for (Post p : posts) {
            String text = p.text.replaceAll("https?://\\S+", " ").trim();
            if (text.length() < 15) {
                continue;
            }
            read++;
            List<String> w = words(text);
            boolean names = about.isEmpty() || w.containsAll(about);
            if (!names || frame.year > 0 && !w.contains(String.valueOf(frame.year))) {
                continue; // not about the thing asked
            }
            String voice = voice(text);
            boolean copied = false;
            for (String seen : seenTexts) {
                copied |= similar(seen, voice);
            }
            if (copied) {
                continue; // S2: a repost or copied text is one voice
            }
            seenTexts.add(voice);
            String who = p.author.isEmpty() ? "anon:" + voice.hashCode() : p.platform + ":" + p.author;
            boolean statesAnswer = !key.isEmpty() && w.containsAll(key) || !key.isEmpty() && share(w, key) >= Math.max(1, key.size() / 2 + 1);
            boolean callsFalse = CALLS_FALSE.matcher(text).find();
            if (statesAnswer && callsFalse || frame.winner && !statesAnswer && saysAnotherWinner(text)) {
                disputing.add(who); // S3
                platforms.add(p.platform);
                continue;
            }
            if (statesAnswer && !NOT_A_STATEMENT.matcher(text).find()) {
                agreeing.add(who); // S1
                platforms.add(p.platform);
            }
        }
        disputing.removeAll(agreeing); // an account that says both counts for neither side twice
        return new Verdict(agreeing.size(), disputing.size(), read, platforms);
    }

    private static int share(List<String> w, Set<String> key) {
        int n = 0;
        for (String k : key) {
            n += w.contains(k) ? 1 : 0;
        }
        return n;
    }

    /** A post naming a winner (someone "won", "are the champions") other than the answer's. */
    private static boolean saysAnotherWinner(String text) {
        return Pattern.compile("(?i)\\b[A-Z][\\p{L}]+\\s+(won|win|wins|are the champions|is the champion|lifted|beat)\\b").matcher(text).find()
                && !NOT_A_STATEMENT.matcher(text).find();
    }

    /** A post's words as compared for copies: lower case, no links, mentions or hashtags. */
    static String voice(String text) {
        return text.toLowerCase(Locale.US).replaceAll("https?://\\S+|[@#]\\w+|rt\\b", " ").replaceAll("[^\\p{L}\\p{N} ]", " ")
                .replaceAll("\\s+", " ").trim();
    }

    /** Two texts that are the same voice (a repost, a copy): most of their words shared. */
    static boolean similar(String a, String b) {
        Set<String> x = new HashSet<String>(java.util.Arrays.asList(a.split(" "))), y = new HashSet<String>(java.util.Arrays.asList(b.split(" ")));
        if (x.isEmpty() || y.isEmpty()) {
            return false;
        }
        int n = 0;
        for (String w : x) {
            n += y.contains(w) ? 1 : 0;
        }
        return n >= 0.8 * Math.max(x.size(), y.size()); // nearly the same post, not a short one inside a long one
    }
}
