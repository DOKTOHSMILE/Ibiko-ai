package ai.ibiko.ibiko1.language;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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

import ai.ibiko.ibiko1.formulas.Expressions;

/**
 * Expression intelligence: the figures of speech in a sentence, each found by its own classifier, because they are not
 * the same thing. An idiom's words don't add up to its meaning ("it's raining cats and dogs" is heavy rain, not
 * animals); a metaphor says one thing is another ("my boss is a dragon"); a simile says it is like it ("brave as a
 * lion"); irony is a situation that turns against itself (a fire station burns down); sarcasm is praise that means the
 * opposite, and needs something bad to be praising ("Oh great" after the car broke down). So each figure says why it
 * was found, and what is meant instead of what is said. Pure Java.
 *
 * <pre>
 * IDIOM        a saying from the sayings table (knowledge/language/expressions.tsv) that is a phrase ("break the ice")
 * PROVERB      a saying that is a whole statement of general advice or truth ("actions speak louder than words")
 * SIMILE       "as ADJ as a N" or "VERB like a N", where N is an animal, a thing or nature (not a person compared
 *              with a person: "as tall as his brother" is a comparison, not a figure)
 * METAPHOR     "X is a N" where X and N are different kinds of thing (a person is an animal; an idea is a
 *              substance), or a conventional one from the table ("time is money")
 * PERSONIFICATION  a thing, nature or an idea doing what only people do ("the wind whispered")
 * HYPERBOLE    an exaggeration no one means literally ("a million times", "I'm starving", "the best ever")
 * IRONY        a role suffers what it exists to prevent (a fire station burns, a dentist's own teeth decay)
 * SARCASM      a praising phrase ("oh great", "just perfect", "thanks a lot") said about something bad, here or in
 *              the message before; or a phrase that is only ever sarcastic ("yeah right", "thanks for nothing")
 * OXYMORON     two words side by side whose meanings pull apart ("deafening silence", "bittersweet")
 * EUPHEMISM    a soft way to say a hard thing ("passed away": died)
 * ALLITERATION three or more stressed words starting with the same consonant sound close together
 * ONOMATOPOEIA a word that imitates the sound it names ("buzz", "sizzle")
 * PARADOX      a statement that seems to contradict itself but holds a truth ("less is more"; "X is Y" for opposites)
 * PUN          a play on a word's two meanings or two sounds ("two-tired")
 * </pre>
 */
public final class Figures {
    public enum Kind { IDIOM, PROVERB, METAPHOR, SIMILE, PERSONIFICATION, HYPERBOLE, IRONY, SARCASM, OXYMORON, EUPHEMISM,
        ALLITERATION, ONOMATOPOEIA, PARADOX, PUN }

    /** One figure found: what kind, the words it is in, what it means, and why it was read so. */
    public static final class Figure {
        public final Kind kind;
        public final String words, meaning, why;
        /** How sure the classifier is (0..1). */
        public final double sure;

        Figure(Kind kind, String words, String meaning, String why, double sure) {
            this.kind = kind;
            this.words = words;
            this.meaning = meaning == null ? "" : meaning;
            this.why = why;
            this.sure = sure;
        }

        public String name() {
            return kind.name().toLowerCase(Locale.ROOT);
        }

        /** As a step or an answer: "“raining cats and dogs” is an idiom: it means it's raining very heavily." */
        public String said() {
            String m = meaning.isEmpty() ? "" : ": it means " + lowerFirst(meaning.replaceAll("[.]$", ""));
            return "“" + words + "” is " + article(name()) + " " + name() + m + " (" + why + ").";
        }

        @Override
        public String toString() {
            return kind + " " + words;
        }
    }

    /** What the dictionary knows that the classifiers need; either may answer null or false when it can't tell. */
    public interface Lexicon {
        /** What kind of thing a noun names: "person", "animal", "thing", "nature", "substance", "place", "abstract". */
        String kindOf(String noun);

        /** Whether two words mean opposite things. */
        boolean opposite(String a, String b);
    }

    private final Map<String, String> euphemism = new LinkedHashMap<String, String>(), hyperbole = new LinkedHashMap<String, String>(),
            paradox = new LinkedHashMap<String, String>(), metaphor = new LinkedHashMap<String, String>(), pun = new LinkedHashMap<String, String>(),
            role = new LinkedHashMap<String, String>();
    private final Set<String> onomatopoeia = new HashSet<String>(), oxymoron = new HashSet<String>(), sarcasm = new HashSet<String>(),
            human = new HashSet<String>(), opposed = new HashSet<String>();
    private final Expressions sayings;
    private final Lexicon lexicon;

    /**
     * @param table   knowledge/language/figures.tsv (tools/build_figures.py), or null for the rules alone
     * @param sayings the idioms and proverbs, or null
     * @param lexicon the dictionary's kinds and opposites, or null for the small built-in list
     */
    public Figures(InputStream table, Expressions sayings, Lexicon lexicon) {
        this.sayings = sayings;
        this.lexicon = lexicon;
        if (table == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(table, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t", -1);
                if (f.length < 2) {
                    continue;
                }
                String k = f[0], p = f[1].trim().toLowerCase(Locale.ROOT), m = f.length > 2 ? f[2].trim() : "";
                switch (k) {
                    case "euphemism": euphemism.put(p, m); break;
                    case "onomatopoeia": onomatopoeia.add(p); break;
                    case "hyperbole": hyperbole.put(p, m); break;
                    case "oxymoron": oxymoron.add(p); break;
                    case "opposed": opposed.add(p + "|" + m.toLowerCase(Locale.ROOT)); break;
                    case "paradox": paradox.put(p, m); break;
                    case "metaphor": metaphor.put(p, m); break;
                    case "role": role.put(p, m.toLowerCase(Locale.ROOT)); break;
                    case "sarcasm": sarcasm.add(p); break;
                    case "human": human.add(p); break;
                    case "pun": pun.put(p, m); break;
                    default: break;
                }
            }
        } catch (IOException ignored) {
            // the rules alone
        }
    }

    /** The figures of speech in a sentence, read with the message before it (for sarcasm); most certain first. */
    public List<Figure> find(String sentence, String previous) {
        List<Figure> out = new ArrayList<Figure>();
        if (sentence == null || sentence.trim().isEmpty()) {
            return out;
        }
        String plain = plain(sentence);
        saying(sentence, out);
        table(plain, euphemism, Kind.EUPHEMISM, "a soft way to say it", out);
        table(plain, paradox, Kind.PARADOX, "it seems to contradict itself, but holds a truth", out);
        table(plain, metaphor, Kind.METAPHOR, "a well-known metaphor", out);
        table(plain, pun, Kind.PUN, "a play on two meanings", out);
        hyperbole(plain, out);
        simile(sentence, out);
        metaphor(sentence, out);
        personification(sentence, out);
        irony(plain, out);
        sarcasm(plain, previous == null ? "" : plain(previous), out);
        oxymoron(plain, out);
        alliteration(sentence, out);
        onomatopoeia(plain, out);
        paradoxByForm(plain, out);
        out.sort((a, b) -> Double.compare(b.sure, a.sure));
        return out;
    }

    /** Whether a sentence holds any figure: it is not to be read word for word. */
    public boolean figurative(String sentence, String previous) {
        return !find(sentence, previous).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------- the classifiers

    private void saying(String sentence, List<Figure> out) {
        String[] s = sayings == null ? null : sayings.sayingIn(sentence);
        if (s == null) {
            return;
        }
        boolean proverb = proverb(s[0]);
        out.add(new Figure(proverb ? Kind.PROVERB : Kind.IDIOM, s[0], s[1], proverb ? "a saying of general wisdom, a whole statement"
                : "a fixed phrase whose words don't add up to its meaning", 0.95));
    }

    /** A proverb is a whole statement of general truth or advice; an idiom is a phrase used inside one. */
    static boolean proverb(String phrase) {
        String p = phrase.trim().toLowerCase(Locale.ROOT);
        if (p.matches("^(?:don't|do not|never|always|better|too many|where there's|when in|if you|you can't|you cannot|a |an |all |every |no |the early|"
                + "actions|two heads|practice|honesty|knowledge|time|money|beauty|blood|absence|curiosity|necessity|patience|silence|"
                + "laughter|rome|the pen|the grass|the squeaky|great minds|birds of|still waters|easy come|first come|out of sight|"
                + "look before|strike while|let sleeping|half a loaf|haste|charity|he who|people who|those who).*")) {
            return p.split(" ").length >= 3;
        }
        SentenceForm.Form f = SentenceForm.read(phrase);
        if (f.tense == null || f.structure == SentenceForm.Structure.FRAGMENT || f.function == SentenceForm.Function.IMPERATIVE) {
            return false;
        }
        String first = p.split("\\s+")[0];
        return p.split(" ").length >= 4 && !first.matches("it|it's|i|i'm|you|he|she|we|they|that|this|there") && f.tense.aspect.equals("simple");
    }

    private static void table(String plain, Map<String, String> t, Kind kind, String why, List<Figure> out) {
        String best = null;
        for (String p : t.keySet()) {
            if (contains(plain, p) && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        if (best != null) {
            out.add(new Figure(kind, best, t.get(best), why, 0.9));
        }
    }

    private static final Pattern BIG_NUMBER = Pattern.compile("\\b(?:a|one|about a) (?:million|billion|trillion|zillion|gazillion|thousand|hundred) "
            + "(?:times|years|people|things|questions|miles|pounds|tons|messages|calls)\\b");
    private static final Pattern EXTREME = Pattern.compile("\\b(?:the (?:best|worst|greatest|longest|hardest|funniest|happiest|saddest) \\w+(?: \\w+)? "
            + "(?:ever|in the (?:whole )?world|of all time|in history)|never ever|literally (?:dying|died|dead|exploded|melting|starving)|"
            + "(?:bored|scared|worried|tired) to death|dying of (?:hunger|thirst|boredom|laughter|embarrassment))\\b");

    private void hyperbole(String plain, List<Figure> out) {
        String best = null;
        for (String p : hyperbole.keySet()) {
            if (contains(plain, p) && (best == null || p.length() > best.length())) {
                best = p;
            }
        }
        if (best != null) {
            out.add(new Figure(Kind.HYPERBOLE, best, hyperbole.get(best), "an exaggeration, not meant literally", 0.85));
            return;
        }
        Matcher m = BIG_NUMBER.matcher(plain);
        if (m.find()) {
            out.add(new Figure(Kind.HYPERBOLE, m.group(), "very many", "a huge number no one counted", 0.75));
            return;
        }
        m = EXTREME.matcher(plain);
        if (m.find()) {
            out.add(new Figure(Kind.HYPERBOLE, m.group(), "very much so", "an extreme no one means literally", 0.7));
        }
    }

    private static final Pattern AS_AS = Pattern.compile("(?i)\\bas (\\w+) as ((?:a|an|the) )?(\\w+(?: \\w+)?)");
    private static final Pattern LIKE = Pattern.compile("(?i)\\b(\\w+) like (a|an|the) (\\w+)");
    private static final Set<String> NOT_SIMILE = set("soon", "well", "long", "far", "much", "many", "often", "few", "little", "quickly",
            "possible", "usual", "always", "ever", "follows", "such");

    private void simile(String sentence, List<Figure> out) {
        Matcher m = AS_AS.matcher(sentence);
        while (m.find()) {
            String adj = m.group(1).toLowerCase(Locale.ROOT), noun = m.group(3).toLowerCase(Locale.ROOT);
            String head = noun.split(" ")[0];
            if (NOT_SIMILE.contains(adj) || head.matches("i|me|you|he|him|she|her|we|us|they|them|it|his|my|your|their|our|possible|"
                    + "usual|before|ever|expected|always|\\d+")) {
                continue;
            }
            String k = kind(head);
            if (k != null && k.equals("person")) {
                continue; // a person compared with a person: a comparison, not a figure
            }
            if (m.group(2) == null && k == null) {
                continue;
            }
            out.add(new Figure(Kind.SIMILE, m.group().trim(), "very " + adj, "it compares using “as … as” with "
                    + (k == null ? "something" : article(k) + " " + k) + " of another kind", 0.85));
            return;
        }
        m = LIKE.matcher(sentence);
        while (m.find()) {
            String before = m.group(1).toLowerCase(Locale.ROOT), noun = m.group(3).toLowerCase(Locale.ROOT);
            if (before.matches("i|you|we|they|would|do|does|did|don't|not|really|also|just|people|who|really|nothing|something|anything")) {
                continue; // "I like a challenge": liking, not likeness
            }
            String k = kind(noun);
            if (k == null || k.equals("person") || k.equals("place")) {
                continue;
            }
            out.add(new Figure(Kind.SIMILE, m.group().trim(), "", "it compares using “like” with " + article(k) + " " + k, 0.8));
            return;
        }
    }

    private static final Pattern IS_A = Pattern.compile("(?i)\\b(?:(my|his|her|their|our|your|the|this|that) )?(\\w+) (?:is|was|are|were|'s|’s) "
            + "(?:(?:a|an|the|my|our|such a|just a|nothing but a|like a) )?(?:(?:real|total|complete|little|big|walking|human|true|living|absolute) )?(\\w+)\\b");

    private void metaphor(String sentence, List<Figure> out) {
        for (Figure f : out) {
            if (f.kind == Kind.METAPHOR || f.kind == Kind.SIMILE || f.kind == Kind.IDIOM || f.kind == Kind.PROVERB) {
                return;
            }
        }
        Matcher m = IS_A.matcher(sentence);
        while (m.find()) {
            String subject = m.group(2).toLowerCase(Locale.ROOT), noun = m.group(3).toLowerCase(Locale.ROOT);
            if (subject.matches("it|this|that|there|what|who|which|here|one") || noun.equals(subject)
                    || sentence.substring(Math.max(0, m.start(3) - 5), m.start(3)).toLowerCase(Locale.ROOT).contains("like")) {
                continue;
            }
            String a = subject.matches("he|she|i|you|we|they") ? "person" : kind(subject), b = kind(noun);
            if (a == null || b == null || a.equals(b) || a.equals("place") && b.equals("nature") || a.equals("substance") && b.equals("thing")
                    || a.equals("thing") && b.equals("substance")) {
                continue;
            }
            out.add(new Figure(Kind.METAPHOR, m.group().trim(), "", "it says " + article(a) + " " + a + " is " + article(b) + " " + b
                    + ", which it can't literally be: it means it is like one", 0.75));
            return;
        }
    }

    private static final Pattern SUBJECT_VERB = Pattern.compile("(?i)\\b(?:the|a|an|my|our|his|her|their|this|that) (\\w+) (?:\\w+ly )?(\\w+)\\b");

    private void personification(String sentence, List<Figure> out) {
        Matcher m = SUBJECT_VERB.matcher(sentence);
        while (m.find()) {
            String noun = m.group(1).toLowerCase(Locale.ROOT), verb = m.group(2).toLowerCase(Locale.ROOT);
            if (!human.contains(verb) && !HUMAN_BUILT_IN.contains(verb)) {
                continue;
            }
            String k = kind(noun);
            if (k == null || k.equals("person") || k.equals("animal")) {
                continue;
            }
            out.add(new Figure(Kind.PERSONIFICATION, m.group().trim(), "", "“" + noun + "” (" + article(k) + " " + k + ") is said to "
                    + base(verb) + ", which only people do", 0.8));
            return;
        }
    }

    private void irony(String plain, List<Figure> out) {
        for (Map.Entry<String, String> e : role.entrySet()) {
            if (!contains(plain, e.getKey())) {
                continue;
            }
            for (String w : e.getValue().split(" (?=\\S)")) {
                if (contains(plain, w)) {
                    out.add(new Figure(Kind.IRONY, e.getKey() + " … " + w, "the opposite of what you'd expect happened", "a "
                            + e.getKey() + " exists to deal with " + firstWord(e.getValue()) + ", and here it suffers it", 0.8));
                    return;
                }
            }
        }
    }

    static final Set<String> BAD = set("broke", "broken", "breaks", "lost", "lose", "failed", "fail", "fails", "late", "missed", "crashed", "crash",
            "rain", "raining", "rained", "spilled", "spilt", "error", "wrong", "ruined", "cancelled", "canceled", "stuck", "flat", "sick", "fired",
            "burnt", "burned", "dead", "died", "delay", "delayed", "bug", "bugs", "again", "stolen", "flooded", "leaking", "no light", "nepa",
            "traffic", "hate", "terrible", "awful", "worst", "fell", "dropped", "forgot", "overslept", "power cut", "down", "useless",
            "rejected", "denied", "refused", "slow", "hangs", "frozen", "froze", "angry", "mess");
    /** Praise that turns sarcastic when said about something bad (the table has many more). */
    private static final Set<String> PRAISE = set("oh great", "just great", "that's just great", "just perfect", "oh perfect", "oh wonderful",
            "how wonderful", "thanks a lot", "great job", "well done", "brilliant", "lucky me", "just what i needed");
    private static final Set<String> ALWAYS_SARCASTIC = set("yeah right", "yeah, right", "thanks for nothing", "as if", "sure you did",
            "like that's going to happen", "like that will happen", "nice going", "well done genius", "big surprise", "shocker",
            "because that worked so well last time", "good luck with that", "tell me something i don't know", "you don't say");

    private void sarcasm(String plain, String previous, List<Figure> out) {
        String marker = null;
        for (String p : sarcasm.isEmpty() ? PRAISE : sarcasm) {
            if (contains(plain, p) && (marker == null || p.length() > marker.length())) {
                marker = p;
            }
        }
        for (String p : ALWAYS_SARCASTIC) {
            if (contains(plain, p) && (marker == null || p.length() > marker.length())) {
                marker = p;
            }
        }
        if (marker == null) {
            return;
        }
        String bad = badIn(plain.replace(marker, " "));
        if (bad == null) {
            bad = badIn(previous);
        }
        boolean always = ALWAYS_SARCASTIC.contains(marker);
        if (bad == null && !always) {
            return; // "Oh great!" about good news is just glad
        }
        out.add(new Figure(Kind.SARCASM, marker, "the opposite: it's bad, not good", bad != null
                ? "praise said about something bad (“" + bad + "”)" : "a phrase that is said only to mean its opposite", bad != null ? 0.8 : 0.7));
    }

    private static String badIn(String plain) {
        for (String b : BAD) {
            if (contains(plain, b)) {
                return b;
            }
        }
        return null;
    }

    private void oxymoron(String plain, List<Figure> out) {
        for (String p : oxymoron) {
            if (contains(plain, p)) {
                out.add(new Figure(Kind.OXYMORON, p, "", "its two words contradict each other, joined for effect", 0.9));
                return;
            }
        }
        String[] w = plain.trim().split(" ");
        for (int i = 0; i + 1 < w.length; i++) {
            if (w[i].length() < 3 || w[i + 1].length() < 3) {
                continue;
            }
            if (opposed.contains(w[i] + "|" + w[i + 1]) || lexicon != null && lexicon.opposite(w[i], w[i + 1])) {
                out.add(new Figure(Kind.OXYMORON, w[i] + " " + w[i + 1], "", "“" + w[i] + "” and “" + w[i + 1]
                        + "” pull opposite ways, side by side", 0.75));
                return;
            }
        }
    }

    private static final Set<String> SMALL = set("a", "an", "the", "and", "or", "of", "to", "in", "on", "at", "for", "with", "by", "is", "are",
            "was", "were", "it", "its", "his", "her", "their", "my", "your", "our", "be", "as", "that", "this", "but", "so", "if", "i", "we",
            "he", "she", "they", "you", "me", "us", "them", "not", "no", "do", "did", "does", "has", "have", "had", "will", "would", "can");

    private void alliteration(String sentence, List<Figure> out) {
        List<String> words = new ArrayList<String>();
        for (String t : sentence.toLowerCase(Locale.ROOT).split("[^\\p{L}'-]+")) {
            if (!t.isEmpty()) {
                words.add(t);
            }
        }
        int bestStart = -1, bestEnd = -1, bestCount = 0;
        for (int i = 0; i < words.size(); i++) {
            String s = onset(words.get(i));
            if (s == null || SMALL.contains(words.get(i))) {
                continue;
            }
            int count = 1, gap = 0, end = i;
            for (int j = i + 1; j < words.size() && gap <= 1; j++) {
                String w = words.get(j);
                if (SMALL.contains(w)) {
                    gap++;
                    continue;
                }
                if (s.equals(onset(w))) {
                    count++;
                    end = j;
                    gap = 0;
                } else {
                    break;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestStart = i;
                bestEnd = end;
            }
        }
        if (bestCount >= 3) {
            String words3 = String.join(" ", words.subList(bestStart, bestEnd + 1));
            out.add(new Figure(Kind.ALLITERATION, words3, "", bestCount + " words start with the same “" + onset(words.get(bestStart))
                    + "” sound", 0.85));
        }
    }

    /** A word's first consonant sound, or null when it starts with a vowel sound. */
    static String onset(String w) {
        if (w == null || w.isEmpty()) {
            return null;
        }
        if (w.startsWith("ph")) {
            return "f";
        }
        if (w.startsWith("kn") || w.startsWith("gn")) {
            return "n";
        }
        if (w.startsWith("wr")) {
            return "r";
        }
        if (w.startsWith("wh")) {
            return w.startsWith("who") ? "h" : "w";
        }
        if (w.startsWith("ps")) {
            return "s";
        }
        if (w.startsWith("sh") || w.startsWith("ch") || w.startsWith("th")) {
            return w.substring(0, 2);
        }
        char c = w.charAt(0);
        if ("aeiou".indexOf(c) >= 0 || w.startsWith("hour") || w.startsWith("honest")) {
            return null;
        }
        if (c == 'c') {
            return w.length() > 1 && "eiy".indexOf(w.charAt(1)) >= 0 ? "s" : "k";
        }
        if (c == 'q') {
            return "k";
        }
        return String.valueOf(c);
    }

    private void onomatopoeia(String plain, List<Figure> out) {
        List<String> found = new ArrayList<String>();
        for (String w : plain.trim().split(" ")) {
            String b = w.replaceAll("(?:ed|ing|es|s)$", "");
            if (onomatopoeia.contains(w) || onomatopoeia.contains(b) || ONOMATOPOEIA_BUILT_IN.contains(w)) {
                if (!found.contains(w)) {
                    found.add(w);
                }
            }
        }
        if (!found.isEmpty()) {
            out.add(new Figure(Kind.ONOMATOPOEIA, String.join(", ", found), "", (found.size() == 1 ? "a word that sounds" : "words that sound")
                    + " like what " + (found.size() == 1 ? "it names" : "they name"), 0.8));
        }
    }

    private static final Pattern X_IS_Y = Pattern.compile("^(?:the )?(\\w+) (?:is|are) (?:the )?(\\w+)$");
    private static final Pattern THE_MORE = Pattern.compile("\\bthe more\\b.*\\bthe (?:less|fewer)\\b|\\bthe less\\b.*\\bthe more\\b");
    private static final Set<String> OPPOSITES = set("less|more", "more|less", "war|peace", "peace|war", "slow|fast", "fast|slow", "freedom|slavery",
            "weakness|strength", "strength|weakness", "ignorance|strength", "end|beginning", "beginning|end", "nothing|everything",
            "everything|nothing", "small|big", "big|small", "old|new", "new|old", "loss|gain", "gain|loss", "lose|win", "win|lose");

    private void paradoxByForm(String plain, List<Figure> out) {
        for (Figure f : out) {
            if (f.kind == Kind.PARADOX) {
                return;
            }
        }
        Matcher m = X_IS_Y.matcher(plain.trim());
        if (m.matches() && (OPPOSITES.contains(m.group(1) + "|" + m.group(2)) || lexicon != null && lexicon.opposite(m.group(1), m.group(2)))) {
            out.add(new Figure(Kind.PARADOX, plain.trim(), "", "it says a thing is its own opposite, to make a point", 0.75));
            return;
        }
        m = THE_MORE.matcher(plain);
        if (m.find()) {
            out.add(new Figure(Kind.PARADOX, m.group(), "", "more of one thing gives less of what you'd expect it to give", 0.6));
        }
    }

    // ------------------------------------------------------------------------------------------------------ kinds

    /** A noun's kind: the dictionary's, then the small list here. */
    String kind(String noun) {
        String n = noun.toLowerCase(Locale.ROOT);
        String k = KINDS.get(n); // the hand-made list first: the dictionary's first sense can be a rare one
        if (k == null && lexicon != null) {
            k = lexicon.kindOf(n);
        }
        if (k == null && n.endsWith("s")) {
            k = lexicon == null ? null : lexicon.kindOf(n.substring(0, n.length() - 1));
            if (k == null) {
                k = KINDS.get(n.substring(0, n.length() - 1));
            }
        }
        return k;
    }

    static final Map<String, String> KINDS = new HashMap<String, String>();

    static {
        put("person", "man woman boy girl person child baby teacher father mother brother sister friend doctor boss uncle aunt king queen wife "
                + "husband son daughter student pilot people farmer lawyer nurse dad mum mom grandmother grandfather neighbour neighbor "
                + "manager chef driver soldier player singer leader he she him");
        put("animal", "lion tiger dog cat horse snake fox owl pig mouse bird eagle bee ant elephant dragon shark wolf bear mule sheep lamb "
                + "peacock donkey chicken rat monkey goat cow ox snail tortoise turtle hawk dove butterfly fish whale mosquito beast "
                + "cheetah rabbit hare parrot vulture hyena giraffe camel bull");
        put("nature", "wind sun moon sea ocean river rain storm sky star thunder lightning fire snow tree flower leaf leaves mountain night "
                + "day morning stars waves wave sunrise sunset breeze clouds cloud dawn earth hurricane volcano rainbow");
        put("thing", "car house door phone computer clock chair table book rock stone feather machine robot dictionary encyclopedia engine "
                + "diamond glass knife wall rollercoaster pillow brick bulldozer computer train rocket bus city road bridge window bell drum "
                + "tank sponge magnet jewel treasure gem sword shield blanket oven furnace mirror toy");
        put("substance", "water ice gold silver honey milk sugar salt iron steel oil smoke mud sand butter bread money");
        put("abstract", "time love life hope fear death freedom opportunity justice knowledge success failure anger joy sorrow truth history "
                + "fate memory idea ideas grief happiness sadness silence wisdom ignorance beauty patience laughter music news word words "
                + "world peace war duty dream dreams thought thoughts mind heart soul loneliness envy jealousy pride shame");
        put("place", "school office market church mosque hospital village town country nigeria lagos london room kitchen home");
    }

    private static void put(String kind, String words) {
        for (String w : words.split(" ")) {
            KINDS.put(w, kind);
        }
    }

    static final Set<String> HUMAN_BUILT_IN = set("whispered", "whispers", "smiled", "smiles", "laughed", "laughs", "wept", "weeps", "cried",
            "danced", "dances", "sang", "sings", "spoke", "speaks", "kissed", "kisses", "beckoned", "beckons", "sighed", "sighs", "winked",
            "frowned", "moaned", "mourned", "dreamed", "yawned", "hugged", "waved", "greeted", "called", "begged", "promised", "lied",
            "screamed", "shouted", "groaned", "stared", "marched", "welcomed");
    static final Set<String> ONOMATOPOEIA_BUILT_IN = set("buzz", "sizzle", "bang", "boom", "splash", "hiss", "crash", "woof", "meow",
            "tick-tock", "thud", "whoosh", "zoom", "clang", "drip", "chirp", "quack", "oink", "moo", "honk", "beep", "kaboom");

    // ------------------------------------------------------------------------------------------------------ words

    static String plain(String s) {
        return " " + s.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[^\\p{L}\\p{N}' -]", " ").replaceAll("\\s+", " ").trim() + " ";
    }

    static boolean contains(String plain, String phrase) {
        return plain.contains(" " + phrase.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[^\\p{L}\\p{N}' -]", " ")
                .replaceAll("\\s+", " ").trim() + " ");
    }

    private static String base(String verb) {
        return verb.replaceAll("(?<=[^aeiou])ied$", "y").replaceAll("ed$", "").replaceAll("(?<=[^s])s$", "");
    }

    private static String firstWord(String s) {
        int sp = s.indexOf(' ');
        return sp < 0 ? s : s.substring(0, sp);
    }

    static String article(String w) {
        return w.isEmpty() || "aeiou".indexOf(Character.toLowerCase(w.charAt(0))) < 0 ? "a" : "an";
    }

    static String lowerFirst(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static Set<String> set(String... w) {
        return new HashSet<String>(Arrays.asList(w));
    }
}
