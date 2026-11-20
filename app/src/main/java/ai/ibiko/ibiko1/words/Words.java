package ai.ibiko.ibiko1.words;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The small tools every part of the words engine shares: the words that carry meaning in a text (not "the", "of",
 * "is"), and one key per word family so "flies", "flew", "flying" and "fly" meet ({@link #key}). Pure Java.
 */
public final class Words {
    private Words() {
    }

    /** Function words: they hold a sentence together but don't carry its meaning. */
    public static final Set<String> STOP = new HashSet<String>(Arrays.asList(("a an the of to in on at by for with from and or but "
            + "nor is are was were be been being am it its this that these those my your his her their our i you he she we they me "
            + "him them us do does did done have has had having not no so as if then than there here what which who whom whose how "
            + "why when where can could will would should may might must shall just very too also into onto out up down off over "
            + "under about some any all each every one other such own same something someone somebody anything anyone thing things "
            + "especially usually often etc used use using make made way kind type sort part act cause causing").split(" ")));

    /** Irregular forms to their base, for {@link #key}. */
    private static final String[] IRREGULAR = {"flew", "fly", "flown", "fly", "went", "go", "gone", "go", "made", "make", "took", "take",
            "taken", "take", "gave", "give", "given", "give", "bought", "buy", "brought", "bring", "thought", "think", "saw", "see",
            "seen", "see", "ran", "run", "wrote", "write", "written", "write", "built", "build", "children", "child", "men", "man",
            "women", "woman", "people", "person", "mice", "mouse", "feet", "foot", "teeth", "tooth", "geese", "goose", "ate", "eat",
            "eaten", "eat", "drove", "drive", "driven", "drive", "spoke", "speak", "spoken", "speak", "sold", "sell", "taught", "teach",
            "caught", "catch", "fought", "fight", "sang", "sing", "sung", "sing", "swam", "swim", "began", "begin", "begun", "begin",
            "knew", "know", "known", "know", "grew", "grow", "grown", "grow", "drew", "draw", "drawn", "draw", "threw", "throw",
            "thrown", "throw", "better", "good", "best", "good", "worse", "bad", "worst", "bad", "lives", "life", "knives", "knife",
            "wives", "wife", "leaves", "leaf", "halves", "half", "said", "say", "paid", "pay", "laid", "lay", "met", "meet", "left",
            "leave", "felt", "feel", "kept", "keep", "slept", "sleep", "held", "hold", "found", "find", "stood", "stand", "won", "win"};
    private static final java.util.Map<String, String> BASE = new java.util.HashMap<String, String>();

    static {
        for (int i = 0; i + 1 < IRREGULAR.length; i += 2) {
            BASE.put(IRREGULAR[i], IRREGULAR[i + 1]);
        }
    }

    /**
     * A word's family key: lower case, irregular forms to their base, and the common endings taken off the same way for
     * every word ("planes" and "plane", "flies" and "fly", "operated" and "operate" meet). Not always a real word: it is
     * only for matching.
     */
    public static String key(String word) {
        String w = word == null ? "" : word.toLowerCase(Locale.ROOT).replaceAll("['’]s$", "").replaceAll("[^\\p{L}\\p{N}-]", "");
        String b = BASE.get(w);
        if (b != null) {
            return b;
        }
        if (w.length() <= 3) {
            return w;
        }
        if (w.endsWith("ies") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.endsWith("ied") && w.length() > 4) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.matches(".*(ss|us|is|ous)$")) {
            return w;
        }
        if (w.endsWith("sses") || w.endsWith("shes") || w.endsWith("ches") || w.endsWith("xes")) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("ing") && w.length() > 5) {
            return undouble(w.substring(0, w.length() - 3));
        }
        if (w.endsWith("ed") && w.length() > 4) {
            return undouble(w.substring(0, w.length() - 2));
        }
        if (w.endsWith("s")) {
            w = w.substring(0, w.length() - 1);
        }
        return w.endsWith("e") && w.length() > 4 ? w.substring(0, w.length() - 1) : w; // "planes", "plane": one key
    }

    /** "stopp" → "stop", "operat" stays (a stem's own e is dropped by every form alike). */
    private static String undouble(String s) {
        if (s.length() > 3 && s.charAt(s.length() - 1) == s.charAt(s.length() - 2) && "lsz".indexOf(s.charAt(s.length() - 1)) < 0) {
            return s.substring(0, s.length() - 1);
        }
        return s.endsWith("e") && s.length() > 4 ? s.substring(0, s.length() - 1) : s;
    }

    /** The words of a text that carry meaning, as their keys, in order, each once. */
    public static List<String> content(String text) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : tokens(text)) {
            String low = w.toLowerCase(Locale.ROOT);
            if (low.length() > 1 && !STOP.contains(low) && !low.matches("\\d+")) {
                out.add(key(low));
            }
        }
        return new ArrayList<String>(out);
    }

    /** A text's words as written (letters, digits, inner apostrophes and hyphens). */
    public static List<String> tokens(String text) {
        List<String> out = new ArrayList<String>();
        for (String w : (text == null ? "" : text).split("[^\\p{L}\\p{N}'’-]+")) {
            String t = w.replaceAll("^['’-]+|['’-]+$", "");
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
