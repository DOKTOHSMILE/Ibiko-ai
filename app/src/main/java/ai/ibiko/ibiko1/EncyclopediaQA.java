package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Questions answered from the encyclopedia's articles, offline, however they are asked: "When did Nigeria gain
 * independence?", "How many people live in Lagos?", "Who founded Microsoft?". The question's subject is found as the
 * longest run of its words that is an article's title (or another name for one); the article's sentences are then
 * scored on the question's other words and on what kind of answer it wants (a date for "when", a number for "how
 * many", a place for "where", a name for "who", a reason for "why"); the best sentence answers, with the next when
 * it carries on from it. Below a sure score nothing is said, so small talk never gets an article. Pure Java.
 */
public final class EncyclopediaQA {
    /** What a found answer is: the words, the article's title, and how well it matched. */
    public static final class Found {
        public final String text, title;
        public final double score;

        Found(String text, String title, double score) {
            this.text = text;
            this.title = title;
            this.score = score;
        }

        /** The answer as said, with where it was read. */
        public String said() {
            return text + "\n(From Simple English Wikipedia, “" + title + "”.)";
        }
    }

    /** The least score a sentence needs to be said. */
    static final double SURE = 1.5;
    /** Words in the longest subject tried. */
    static final int LONGEST = 6;

    private final Encyclopedia enc;

    public EncyclopediaQA(Encyclopedia enc) {
        this.enc = enc;
    }

    static final Set<String> STOP = new HashSet<String>(Arrays.asList(("a an the of in on at to for from by with about as into"
            + " and or but is are was were be been being am do does did done have has had having can could will would shall"
            + " should may might must what which who whom whose when where why how many much long old far tall big large"
            + " i you he she it we they me him her us them my your his its our their this that these those there here"
            + " please tell know explain say said give get got make made name named called any some all most more"
            + " ever still also just very really kind type sort way thing things one ones").split(" ")));

    /** What "how tall", "how old" and the like are measured in: the sentence must say it. */
    static Pattern measure(String q) {
        String t = q.toLowerCase(Locale.ROOT);
        String unit = t.matches("^\\s*how (tall|high|deep)\\b.*") ? "\\b(metres?|meters?|m|feet|ft|km|kilometres?|kilometers?)\\b"
                : t.matches("^\\s*how old\\b.*") ? "\\b(years? old|years|million years|billion years|age|aged|born|founded|built|formed)\\b"
                : t.matches("^\\s*how (long|far)\\b.*") ? "\\b(km|kilometres?|kilometers?|miles?|metres?|meters?|years|days|hours|minutes)\\b"
                : t.matches("^\\s*how (big|large)\\b.*") ? "(km²|km2|square|hectares?|acres?|\\barea\\b|metres?|meters?)"
                : t.matches("^\\s*how much\\b.*") ? "(\\$|£|€|₦|\\bdollars?\\b|\\bnaira\\b|\\bpercent\\b|%|\\bcosts?\\b|\\bprice\\b|\\bkg\\b|\\btonnes?\\b)"
                : t.matches("^\\s*how many people\\b.*") ? "\\b(people|population|inhabitants|residents|lived|living)\\b"
                : null;
        return unit == null ? null : Pattern.compile(unit, Pattern.CASE_INSENSITIVE);
    }

    /** The kind of answer a question wants. */
    enum Wants { WHEN, HOW_MANY, WHERE, WHO, WHY, WHAT }

    static Wants wants(String q) {
        String t = q.toLowerCase(Locale.ROOT);
        if (t.matches("^\\s*(when|what year|which year|in what year|what date|on what date)\\b.*") || t.matches(".*\\b(what year|which year|born|died|founded|built|started|ended|began)\\b.*\\?\\s*$") && t.startsWith("when")) {
            return Wants.WHEN;
        }
        if (t.matches("^\\s*how (many|much|long|old|tall|high|big|far|deep|large|heavy)\\b.*")) {
            return Wants.HOW_MANY;
        }
        if (t.matches("^\\s*(where|in which (country|city|place|state|continent)|in what (country|city|place))\\b.*")) {
            return Wants.WHERE;
        }
        if (t.matches("^\\s*(who|by whom|whom)\\b.*")) {
            return Wants.WHO;
        }
        if (t.matches("^\\s*(why|for what reason|what caused|what causes)\\b.*")) {
            return Wants.WHY;
        }
        return Wants.WHAT;
    }

    /** A question (a question mark, or the words a question starts with), not a remark. */
    static boolean question(String q) {
        String t = q.trim().toLowerCase(Locale.ROOT);
        return t.endsWith("?") || t.matches("^(who|what|when|where|why|how|which|whom|whose|is|are|was|were|do|does|did|can|could|"
                + "tell me|explain)\\b.*");
    }

    /** The words of a text, lower case. */
    static List<String> words(String text) {
        List<String> out = new ArrayList<String>();
        for (String w : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** A word's stem, enough to match "independence"/"independent", "founded"/"founder", "lives"/"living". */
    static String stem(String w) {
        String s = w;
        for (String end : new String[]{"ations", "ation", "ences", "ence", "ments", "ment", "ness", "ings", "ing", "edly", "ers",
                "ies", "ied", "est", "ed", "er", "es", "ly", "s", "e"}) {
            if (s.length() >= end.length() + 3 && s.endsWith(end)) {
                return s.substring(0, s.length() - end.length());
            }
        }
        return s;
    }

    /**
     * The subjects the question may be about: every run of its words that names an article, best first (a run with
     * a capitalised name, as "Lagos" or "Microsoft", before a common word like "people"; then the longer). At most
     * {@code most}. Each is {article, the run's words}.
     */
    List<Object[]> subjects(String q, int most) {
        List<String> raw = new ArrayList<String>();
        for (String w : q.replaceAll("[?!.,;:\"\u201c\u201d]", " ").trim().split("\\s+")) {
            if (!w.isEmpty()) {
                raw.add(w.replaceAll("'s$|\u2019s$", ""));
            }
        }
        List<Object[]> found = new ArrayList<Object[]>();
        Set<String> titles = new HashSet<String>();
        List<int[]> spans = new ArrayList<int[]>(); // where the names already found stand: a word inside one isn't another
        for (int n = Math.min(LONGEST, raw.size()); n >= 1; n--) {
            for (int i = 0; i + n <= raw.size(); i++) {
                List<String> run = raw.subList(i, i + n);
                String first = run.get(0).toLowerCase(Locale.ROOT), last = run.get(n - 1).toLowerCase(Locale.ROOT);
                if (STOP.contains(first) && !(n > 1 && first.equals("the")) || STOP.contains(last)) {
                    continue; // a run starting or ending on a little word is not a name
                }
                boolean content = false;
                int capitals = 0;
                for (int j = 0; j < run.size(); j++) {
                    String w = run.get(j);
                    content |= !STOP.contains(w.toLowerCase(Locale.ROOT));
                    // a capital that isn't just the question's first letter marks a name
                    if (Character.isUpperCase(w.codePointAt(0)) && (i + j > 0)) {
                        capitals++;
                    }
                }
                if (!content || n == 1 && (run.get(0).length() < 3 || verbish(run.get(0)))) {
                    continue; // a verb ("erupt", "invented") is what is asked, not what it is about
                }
                boolean inside = false;
                for (int[] sp : spans) {
                    inside |= i >= sp[0] && i + n <= sp[1];
                }
                if (inside) {
                    continue; // "States" in "United States" is not a subject of its own
                }
                Encyclopedia.Article a = enc.find(String.join(" ", run));
                if (a != null) {
                    spans.add(new int[]{i, i + n});
                }
                if (a != null && titles.add(a.title)) {
                    // a name first, then the longer run, then the later one (the thing asked about comes after the verb)
                    double rank = (capitals > 0 ? 10 : 0) + capitals + n * 2 + (i + n) * 0.1
                            - (verbish(run.get(n - 1)) ? 3 : 0);
                    found.add(new Object[]{a, new HashSet<String>(words(String.join(" ", run))), rank});
                }
            }
        }
        found.sort((x, y) -> Double.compare((Double) y[2], (Double) x[2]));
        return found.size() > most ? found.subList(0, most) : found;
    }

    /** A word that reads as the question's verb ("erupt", "invented"), not its subject. */
    static boolean verbish(String w) {
        String t = w.toLowerCase(Locale.ROOT);
        return t.matches(".*(ed|ing)$") && t.length() > 5 || VERBS.contains(t);
    }

    static final Set<String> VERBS = new HashSet<String>(Arrays.asList(("erupt erupts happen happens work works start starts end ends"
            + " live lives grow grows eat eats fly flies win wins die dies mean means cause causes begin begins become becomes"
            + " invent invents write writes build builds find finds use uses change changes come comes go goes run runs").split(" ")));

    /** The best subject (as {@link #subjects}), or null. */
    Object[] subject(String q) {
        List<Object[]> all = subjects(q, 1);
        return all.isEmpty() ? null : all.get(0);
    }

    private static final Pattern YEAR = Pattern.compile("\\b(1[0-9]{3}|20[0-9]{2})\\b|\\b\\d{1,2} (January|February|March|April|May|June|July|August|September|October|November|December)\\b|\\b(BC|AD|century)\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d[\\d,.]*\\b|\\b(million|billion|thousand|hundred|dozen)\\b");
    private static final Pattern NAME = Pattern.compile("\\b[A-Z][a-z]+ [A-Z][a-z]+\\b");
    private static final Pattern PLACE = Pattern.compile("\\b(in|at|near|on the|located|capital|city|country|region|state|province) [A-Z][a-z]+");
    private static final Pattern REASON = Pattern.compile("(?i)\\b(because|due to|caused by|as a result|so that|since|reason|led to|in order to)\\b");

    private static final Pattern LIFE = Pattern.compile("\\(([^()]*\\b(1[0-9]{3}|20[0-9]{2}))\\s*[\u2013\u2014-]");

    /** The answer to {@code q} from the encyclopedia, or null when no sentence answers it surely. */
    public Found answer(String q) {
        if (q == null || !question(q) || ai.ibiko.ibiko1.formulas.Critical.decision(q)) {
            return null;
        }
        List<Object[]> subs = subjects(q, 3);
        // a question that names something (a capital) is about that, never about a common word in it
        boolean named = !subs.isEmpty() && (Double) subs.get(0)[2] >= 10;
        for (Object[] sub : subs) {
            if (named && (Double) sub[2] < 10) {
                break;
            }
            @SuppressWarnings("unchecked")
            Found f = from((Encyclopedia.Article) sub[0], (Set<String>) sub[1], q);
            if (f != null) {
                return f; // the best-ranked subject that answers it, not a lower one that matched a stray word
            }
        }
        return null;
    }

    /** The best sentence of one article for the question, or null below the bar. */
    Found from(Encyclopedia.Article a, Set<String> subjectWords, String q) {
        Set<String> asked = new HashSet<String>();
        for (String w : words(q)) {
            if (!STOP.contains(w) && !subjectWords.contains(w) && w.length() > 2) {
                asked.add(stem(w));
            }
        }
        Wants kind = wants(q);
        Pattern unit = measure(q);
        boolean life = asked.contains(stem("born")) || asked.contains(stem("birth")) || asked.contains(stem("died"))
                || asked.contains(stem("death")) || asked.contains(stem("die"));
        String[] sentences = a.text.split("(?<=[.!?])\\s+(?=[A-Z\"\u201c(])");
        double best = -1;
        int at = -1;
        for (int i = 0; i < sentences.length; i++) {
            String s = sentences[i];
            Set<String> has = new HashSet<String>();
            for (String w : words(s)) {
                has.add(stem(w));
            }
            double score = 0;
            for (String w : asked) {
                if (has.contains(w)) {
                    score += 1.5;
                }
            }
            if (life && LIFE.matcher(s).find()) {
                score += 2; // "(18 July 1918 – 5 December 2013)": when someone was born and died
            }
            boolean cue;
            switch (kind) {
                case WHEN:
                    cue = YEAR.matcher(s).find();
                    score += cue ? 1.5 : -1;
                    break;
                case HOW_MANY:
                    cue = NUMBER.matcher(s).find() && (unit == null || unit.matcher(s).find());
                    score += cue ? 1.5 : -1;
                    break;
                case WHERE:
                    cue = PLACE.matcher(s).find();
                    score += cue ? 1 : 0;
                    break;
                case WHO:
                    cue = NAME.matcher(s).find();
                    score += cue ? 1 : 0;
                    break;
                case WHY:
                    cue = REASON.matcher(s).find();
                    score += cue ? 1.5 : 0;
                    break;
                default:
                    break;
            }
            score -= i * 0.05; // earlier sentences say the main things
            if (score > best) {
                best = score;
                at = i;
            }
        }
        // a question with nothing asked beyond the subject ("what about Lagos?") is the article's opening
        if (asked.isEmpty() && kind == Wants.WHAT) {
            return new Found(sentences[0] + (sentences.length > 1 ? " " + sentences[1] : ""), a.title, SURE);
        }
        // with nothing asked but the kind ("where is Kilimanjaro"), a sentence with that kind of answer will do
        double bar = asked.isEmpty() ? 0.9 : SURE;
        if (at < 0 || best < bar) {
            return null;
        }
        String text = sentences[at];
        // the next sentence when it carries on from this one ("It was…", "This made…")
        if (at + 1 < sentences.length && sentences[at + 1].matches("^(It|This|These|That|They|He|She|His|Her|Its|Their)\\b.*")
                && text.length() + sentences[at + 1].length() < 420) {
            text += " " + sentences[at + 1];
        }
        return new Found(text, a.title, best);
    }
}
