package ai.ibiko.ibiko1.words;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Inflection, the grammatical endings English puts on a word without making a new word: the plural and the possessive
 * of a noun, a verb's third person (-s), past (-ed), participle and -ing, an adjective's comparative and superlative;
 * with the spelling rules they bring ("city" → "cities", "stop" → "stopped", "make" → "making") and the irregular forms
 * ("child" → "children", "go" → "went"). And the way their endings sound, which changes with the sound before them (one
 * morpheme, several allomorphs): the plural -s is /s/ in "cats", /z/ in "dogs", /ɪz/ in "buses"; the past -ed is /t/
 * in "walked", /d/ in "played", /ɪd/ in "waited". Pure Java.
 */
public final class Morph {
    private Morph() {
    }

    private static final Map<String, String> PLURAL = new HashMap<String, String>(), PAST = new HashMap<String, String>(),
            PARTICIPLE = new HashMap<String, String>();

    static {
        String[] p = {"child", "children", "man", "men", "woman", "women", "person", "people", "mouse", "mice", "foot", "feet",
                "tooth", "teeth", "goose", "geese", "ox", "oxen", "sheep", "sheep", "fish", "fish", "deer", "deer", "series", "series",
                "knife", "knives", "life", "lives", "wife", "wives", "leaf", "leaves", "half", "halves", "wolf", "wolves", "shelf",
                "shelves", "loaf", "loaves", "thief", "thieves", "calf", "calves", "cactus", "cacti", "crisis", "crises", "analysis",
                "analyses", "datum", "data", "criterion", "criteria", "phenomenon", "phenomena", "potato", "potatoes", "tomato",
                "tomatoes", "hero", "heroes", "echo", "echoes"};
        for (int i = 0; i + 1 < p.length; i += 2) {
            PLURAL.put(p[i], p[i + 1]);
        }
        String[] v = {"be", "was", "been", "go", "went", "gone", "do", "did", "done", "have", "had", "had", "make", "made", "made",
                "take", "took", "taken", "give", "gave", "given", "see", "saw", "seen", "come", "came", "come", "get", "got", "gotten",
                "know", "knew", "known", "think", "thought", "thought", "say", "said", "said", "find", "found", "found", "tell", "told",
                "told", "become", "became", "become", "leave", "left", "left", "feel", "felt", "felt", "bring", "brought", "brought",
                "begin", "began", "begun", "keep", "kept", "kept", "hold", "held", "held", "write", "wrote", "written", "stand", "stood",
                "stood", "hear", "heard", "heard", "let", "let", "let", "mean", "meant", "meant", "set", "set", "set", "meet", "met",
                "met", "run", "ran", "run", "pay", "paid", "paid", "sit", "sat", "sat", "speak", "spoke", "spoken", "lie", "lay", "lain",
                "lead", "led", "led", "read", "read", "read", "grow", "grew", "grown", "lose", "lost", "lost", "fall", "fell", "fallen",
                "send", "sent", "sent", "build", "built", "built", "understand", "understood", "understood", "draw", "drew", "drawn",
                "break", "broke", "broken", "spend", "spent", "spent", "cut", "cut", "cut", "rise", "rose", "risen", "drive", "drove",
                "driven", "buy", "bought", "bought", "wear", "wore", "worn", "choose", "chose", "chosen", "fly", "flew", "flown", "eat",
                "ate", "eaten", "drink", "drank", "drunk", "sing", "sang", "sung", "swim", "swam", "swum", "teach", "taught", "taught",
                "catch", "caught", "caught", "fight", "fought", "fought", "sell", "sold", "sold", "sleep", "slept", "slept", "win", "won",
                "won", "throw", "threw", "thrown", "forget", "forgot", "forgotten", "sell", "sold", "sold", "shine", "shone", "shone",
                "hide", "hid", "hidden", "ride", "rode", "ridden", "shake", "shook", "shaken", "steal", "stole", "stolen", "wake",
                "woke", "woken", "bite", "bit", "bitten", "feed", "fed", "fed", "hit", "hit", "hit", "hurt", "hurt", "hurt", "put",
                "put", "put", "shut", "shut", "shut", "light", "lit", "lit", "deal", "dealt", "dealt", "dig", "dug", "dug", "hang",
                "hung", "hung", "lend", "lent", "lent", "bend", "bent", "bent", "sink", "sank", "sunk", "ring", "rang", "rung"};
        for (int i = 0; i + 2 < v.length; i += 3) {
            PAST.put(v[i], v[i + 1]);
            PARTICIPLE.put(v[i], v[i + 2]);
        }
    }

    private static boolean vowel(char c) {
        return "aeiou".indexOf(c) >= 0;
    }

    /** A short word ending consonant–vowel–consonant doubles its last letter: "stop" → "stopped", "big" → "bigger". */
    static boolean doubles(String w) {
        int n = w.length();
        if (n < 3 || n > 4 && !w.matches(".*(fer|mit|pel|gin|cur|trol)$")) {
            return false;
        }
        char a = w.charAt(n - 3), b = w.charAt(n - 2), c = w.charAt(n - 1);
        return !vowel(a) && vowel(b) && !vowel(c) && "wxy".indexOf(c) < 0;
    }

    /** A noun's plural. */
    public static String plural(String noun) {
        String w = noun.toLowerCase(Locale.ROOT);
        String irregular = PLURAL.get(w);
        if (irregular != null) {
            return irregular;
        }
        if (w.matches(".*(s|x|z|ch|sh)$")) {
            return noun + "es";
        }
        if (w.matches(".*[^aeiou]y$")) {
            return noun.substring(0, noun.length() - 1) + "ies";
        }
        return noun + "s";
    }

    /** A verb's third person singular present: "he walks", "she goes", "it flies". */
    public static String third(String verb) {
        String w = verb.toLowerCase(Locale.ROOT);
        switch (w) {
            case "be": return "is";
            case "have": return "has";
            case "do": return "does";
            case "go": return "goes";
            default: break;
        }
        if (w.matches(".*(s|x|z|ch|sh|o)$")) {
            return verb + "es";
        }
        if (w.matches(".*[^aeiou]y$")) {
            return verb.substring(0, verb.length() - 1) + "ies";
        }
        return verb + "s";
    }

    /** A verb's past tense. */
    public static String past(String verb) {
        String w = verb.toLowerCase(Locale.ROOT);
        String irregular = PAST.get(w);
        if (irregular != null) {
            return irregular;
        }
        if (w.endsWith("e")) {
            return verb + "d";
        }
        if (w.matches(".*[^aeiou]y$")) {
            return verb.substring(0, verb.length() - 1) + "ied";
        }
        if (doubles(w)) {
            return verb + verb.charAt(verb.length() - 1) + "ed";
        }
        return verb + "ed";
    }

    /** The base of an irregular past ("went" → "go"), or null when it isn't one. */
    public static String irregularPast(String w) {
        return baseOf(PAST, w);
    }

    /** The base of an irregular past participle ("written" → "write"), or null when it isn't one. */
    public static String irregularParticiple(String w) {
        return baseOf(PARTICIPLE, w);
    }

    /** Whether it is an irregular verb's base form ("go", "write"). */
    public static boolean irregularBase(String w) {
        return w != null && PAST.containsKey(w.toLowerCase(Locale.ROOT));
    }

    private static String baseOf(Map<String, String> forms, String w) {
        if (w == null) {
            return null;
        }
        String l = w.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : forms.entrySet()) {
            if (e.getValue().equals(l)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** A verb's past participle ("written", "walked"). */
    public static String participle(String verb) {
        String irregular = PARTICIPLE.get(verb.toLowerCase(Locale.ROOT));
        return irregular != null ? irregular : past(verb);
    }

    /** A verb's -ing form. */
    public static String ing(String verb) {
        String w = verb.toLowerCase(Locale.ROOT);
        if (w.endsWith("ie")) {
            return verb.substring(0, verb.length() - 2) + "ying";
        }
        if (w.endsWith("e") && !w.endsWith("ee") && !w.endsWith("ye") && !w.endsWith("oe") && w.length() > 2) {
            return verb.substring(0, verb.length() - 1) + "ing";
        }
        if (doubles(w)) {
            return verb + verb.charAt(verb.length() - 1) + "ing";
        }
        return verb + "ing";
    }

    /** An adjective's comparative ("bigger", "happier", "more careful"). */
    public static String comparative(String adj) {
        String w = adj.toLowerCase(Locale.ROOT);
        switch (w) {
            case "good": return "better";
            case "bad": return "worse";
            case "far": return "farther";
            case "little": return "less";
            case "many": case "much": return "more";
            default: break;
        }
        int syllables = syllables(w);
        if (syllables > 2 || syllables == 2 && !w.matches(".*(y|le|er|ow)$")) {
            return "more " + adj;
        }
        if (w.endsWith("e")) {
            return adj + "r";
        }
        if (w.matches(".*[^aeiou]y$")) {
            return adj.substring(0, adj.length() - 1) + "ier";
        }
        if (doubles(w)) {
            return adj + adj.charAt(adj.length() - 1) + "er";
        }
        return adj + "er";
    }

    /** An adjective's superlative ("biggest", "happiest", "most careful"). */
    public static String superlative(String adj) {
        String c = comparative(adj);
        switch (c) {
            case "better": return "best";
            case "worse": return "worst";
            case "farther": return "farthest";
            case "less": return "least";
            case "more": return "most";
            default: break;
        }
        if (c.startsWith("more ")) {
            return "most " + adj;
        }
        return c.substring(0, c.length() - 2) + (c.endsWith("ier") ? "est" : "est");
    }

    /** Roughly how many syllables a written word has (vowel groups, a final silent e not counted). */
    public static int syllables(String w) {
        String s = w.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (s.isEmpty()) {
            return 0;
        }
        String t = s.replaceAll("(?:[^laeiouy]es|ed|[^laeiouy]e)$", "").replaceAll("^y", "");
        int n = 0;
        boolean in = false;
        for (char c : t.toCharArray()) {
            boolean v = "aeiouy".indexOf(c) >= 0;
            if (v && !in) {
                n++;
            }
            in = v;
        }
        return Math.max(1, n);
    }

    /**
     * A plain word given the same ending as the written word it stands in for: if "flew" (the past of "fly") is put
     * as "aviate", it is "aviated"; "planes" put as "aircraft" stays "aircraft" when the plural is the same word.
     */
    public static String inflectLike(String plain, String written, String base, String pos) {
        String w = written.toLowerCase(Locale.ROOT), b = base.toLowerCase(Locale.ROOT);
        if (w.equals(b) || plain.contains(" ")) {
            return plain;
        }
        if ("noun".equals(pos) && (w.endsWith("s") || PLURAL.containsValue(w))) {
            return plural(plain);
        }
        if ("verb".equals(pos)) {
            if (w.endsWith("ing")) {
                return ing(plain);
            }
            if (w.equals(third(b))) {
                return third(plain);
            }
            if (w.equals(participle(b)) && !w.equals(past(b))) {
                return participle(plain);
            }
            return past(plain);
        }
        if ("adjective".equals(pos)) {
            if (w.endsWith("est")) {
                return superlative(plain);
            }
            if (w.endsWith("er")) {
                return comparative(plain);
            }
        }
        return plain;
    }

    // ------------------------------------------------------------------ how endings sound

    /** The sound a written word ends on, for choosing an ending's allomorph. */
    enum End { SIBILANT, VOICELESS, T_OR_D, VOICED }

    static End end(String word) {
        String w = word.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        if (w.matches(".*(s|ss|x|z|zz|ch|sh|ge|dge|ce|se|ze)$")) {
            return End.SIBILANT;
        }
        if (w.matches(".*(t|te|d|de)$")) {
            return End.T_OR_D;
        }
        if (w.matches(".*(p|pe|k|ke|ck|f|fe|ph|gh|th|c)$") && !w.matches(".*(the|ough)$")) {
            return End.VOICELESS;
        }
        return End.VOICED;
    }

    /** How the plural (or third person, or possessive) -s sounds after a word: /s/, /z/ or /ɪz/. */
    public static String sSound(String word) {
        return pluralSound(word);
    }

    /** How the past -ed sounds after a verb: /t/, /d/ or /ɪd/. */
    public static String edSound(String verb) {
        End e = end(verb);
        if (e == End.T_OR_D) {
            return "/ɪd/";
        }
        String w = verb.toLowerCase(Locale.ROOT);
        return e == End.VOICELESS || e == End.SIBILANT && w.matches(".*(s|ss|x|ch|sh|ce)$") ? "/t/" : "/d/";
    }

    /** The plural's sound, with "t" counted voiceless ("cats" /s/) and "d" voiced ("beds" /z/). */
    public static String pluralSound(String noun) {
        String w = noun.toLowerCase(Locale.ROOT);
        if (end(w) == End.SIBILANT) {
            return "/ɪz/";
        }
        if (w.matches(".*(t|te|p|pe|k|ke|ck|f|fe|ph|gh|th)$") && !w.matches(".*(the|ough)$")) {
            return "/s/";
        }
        return "/z/";
    }
}
