package ai.ibiko.ibiko1.language;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Semantic intelligence, how words relate: synonyms and antonyms, the general and the specific (hypernyms, hyponyms),
 * the part and the whole (meronyms, holonyms), words said alike (homophones), spelled alike but said differently
 * (homographs), one word with unrelated meanings (homonyms) or related ones (polysemy), the words a word goes with
 * (collocations), and the words that mean their own opposite (contronyms). The dictionary (Open English WordNet) and
 * ConceptNet answer most of it through {@link Source}; the small closed lists are here. Pure Java.
 *
 * <pre>
 * synonyms, antonyms     the dictionary's sense lists
 * hypernyms              each sense's broader term ("dog" → canine, domestic animal)
 * hyponyms               ConceptNet: what IS-A this ("animal" ← dog, cat)
 * meronyms / holonyms    ConceptNet HasPart, HasA / PartOf ("car" has wheel; "wheel" is part of car)
 * homonym vs polysemy    two senses of one part of speech whose broader terms and definitions share nothing: homonyms
 *                        (bank: a slope of land; a financial institution); senses that share words: polysemy (head)
 * homophones             the table (their, there, they're)   homographs  lead, read, tear, wind, bow…
 * collocations           make a decision, heavy rain, strong coffee…   contronyms  sanction, dust, cleave, left…
 * </pre>
 */
public final class Relations {
    /** What the dictionary and the concept net know. */
    public interface Source {
        /** A word's senses, each {part of speech, definition, broader term, synonyms (comma list), opposites (comma list)}. */
        List<String[]> senses(String word);

        /** ConceptNet: the words a word is linked to by one relation ("HasPart", "PartOf", "HasA"). */
        List<String> linked(String word, String relation);

        /** ConceptNet: what is linked to this word by one relation ("dog" IsA "animal" is found from "animal"). */
        List<String> linkedTo(String word, String relation);
    }

    private final Source source;
    private final Map<String, Set<String>> homophones = new HashMap<String, Set<String>>();

    /** @param homophonePairs pairs said alike (figures.tsv "homophone" rows), or null for the list here */
    public Relations(Source source, List<String[]> homophonePairs) {
        this.source = source;
        for (String[] p : homophonePairs == null ? java.util.Arrays.asList(BUILT_IN_HOMOPHONES) : homophonePairs) {
            link(p[0], p[1]);
        }
    }

    private void link(String a, String b) {
        homophones.computeIfAbsent(a, k -> new LinkedHashSet<String>()).add(b);
        homophones.computeIfAbsent(b, k -> new LinkedHashSet<String>()).add(a);
    }

    /** Every relation a word has, by name, each list closest first; relations with nothing are left out. */
    public Map<String, List<String>> of(String word) {
        String w = word.trim().toLowerCase(Locale.ROOT);
        Map<String, List<String>> out = new LinkedHashMap<String, List<String>>();
        List<String[]> senses = senses(w);
        Set<String> syn = new LinkedHashSet<String>(), ant = new LinkedHashSet<String>(), broader = new LinkedHashSet<String>();
        for (String[] s : senses) {
            addAll(syn, s.length > 3 ? s[3] : "", w);
            addAll(ant, s.length > 4 ? s[4] : "", w);
            addAll(broader, s.length > 2 ? s[2] : "", w);
        }
        put(out, "synonyms", syn, 8);
        put(out, "antonyms", ant, 6);
        put(out, "hypernyms", broader, 6);
        put(out, "hyponyms", linkedTo(w, "IsA"), 8);
        Set<String> parts = new LinkedHashSet<String>(linked(w, "HasPart"));
        parts.addAll(linked(w, "HasA"));
        put(out, "meronyms", parts, 8);
        Set<String> wholes = new LinkedHashSet<String>(linked(w, "PartOf"));
        wholes.addAll(linkedTo(w, "HasPart"));
        put(out, "holonyms", wholes, 6);
        put(out, "homophones", homophones.getOrDefault(w, new LinkedHashSet<String>()), 4);
        if (HOMOGRAPHS.containsKey(w)) {
            out.put("homograph", Arrays.asList(HOMOGRAPHS.get(w)));
        }
        String meanings = homonymOrPolysemy(senses);
        if (meanings != null) {
            out.put(meanings.startsWith("homonym") ? "homonym" : "polysemy", Arrays.asList(meanings.substring(meanings.indexOf(':') + 1).trim()));
        }
        if (COLLOCATIONS.containsKey(w)) {
            out.put("collocations", Arrays.asList(COLLOCATIONS.get(w)));
        }
        if (CONTRONYMS.containsKey(w)) {
            out.put("contronym", Arrays.asList(CONTRONYMS.get(w)));
        }
        return out;
    }

    /** How two words are related, in plain words; null when no relation is known. */
    public String between(String a, String b) {
        String x = a.trim().toLowerCase(Locale.ROOT), y = b.trim().toLowerCase(Locale.ROOT);
        Map<String, List<String>> rx = of(x), ry = of(y);
        if (in(rx, "synonyms", y) || in(ry, "synonyms", x)) {
            return "“" + x + "” and “" + y + "” are synonyms: they mean nearly the same.";
        }
        if (in(rx, "antonyms", y) || in(ry, "antonyms", x)) {
            return "“" + x + "” and “" + y + "” are antonyms: they mean opposite things.";
        }
        if (in(rx, "hypernyms", y) || in(ry, "hyponyms", x)) {
            return "“" + y + "” is a hypernym of “" + x + "” (the general kind), and “" + x + "” a hyponym of it (a specific kind).";
        }
        if (in(ry, "hypernyms", x) || in(rx, "hyponyms", y)) {
            return "“" + x + "” is a hypernym of “" + y + "” (the general kind), and “" + y + "” a hyponym of it (a specific kind).";
        }
        if (in(rx, "holonyms", y) || in(ry, "meronyms", x)) {
            return "“" + x + "” is a meronym of “" + y + "” (a part of it), and “" + y + "” a holonym of “" + x + "” (the whole).";
        }
        if (in(rx, "meronyms", y) || in(ry, "holonyms", x)) {
            return "“" + y + "” is a meronym of “" + x + "” (a part of it), and “" + x + "” a holonym of “" + y + "” (the whole).";
        }
        if (in(rx, "homophones", y)) {
            return "“" + x + "” and “" + y + "” are homophones: said the same, spelled and meant differently.";
        }
        return null;
    }

    /** Every relation, said as a short paragraph. */
    public String said(String word) {
        Map<String, List<String>> r = of(word);
        if (r.isEmpty()) {
            return null;
        }
        StringBuilder b = new StringBuilder("How “" + word.trim() + "” relates to other words:");
        for (Map.Entry<String, List<String>> e : r.entrySet()) {
            b.append("\n• ").append(Character.toUpperCase(e.getKey().charAt(0))).append(e.getKey().substring(1)).append(": ")
                    .append(String.join(", ", e.getValue()));
        }
        return b.toString();
    }

    /** Two senses of one part of speech that share nothing are homonyms; ones that share words, polysemy. */
    static String homonymOrPolysemy(List<String[]> senses) {
        for (int i = 0; i < senses.size(); i++) {
            for (int j = i + 1; j < senses.size(); j++) {
                String[] a = senses.get(i), b = senses.get(j);
                if (!a[0].equals(b[0]) || a.length < 3 || b.length < 3) {
                    continue;
                }
                Set<String> wa = content(a[1] + " " + a[2]), wb = content(b[1] + " " + b[2]);
                Set<String> shared = new LinkedHashSet<String>(wa);
                shared.retainAll(wb);
                if (shared.isEmpty() && !a[2].isEmpty() && !b[2].isEmpty() && !a[2].equals(b[2])) {
                    return "homonym: " + a[1] + " / " + b[1];
                }
            }
        }
        if (senses.size() >= 2 && senses.get(0)[0].equals(senses.get(1)[0])) {
            return "polysemy: " + senses.size() + " related meanings, such as " + senses.get(0)[1] + " / " + senses.get(1)[1];
        }
        return null;
    }

    private static Set<String> content(String s) {
        Set<String> out = new LinkedHashSet<String>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            if (w.length() > 3 && !w.matches("that|with|from|which|used|having|something|someone|other|into|more|their|being|especially")) {
                out.add(w);
            }
        }
        return out;
    }

    private List<String[]> senses(String w) {
        try {
            return source == null ? new ArrayList<String[]>() : source.senses(w);
        } catch (RuntimeException e) {
            return new ArrayList<String[]>();
        }
    }

    private List<String> linked(String w, String rel) {
        try {
            return source == null ? new ArrayList<String>() : source.linked(w, rel);
        } catch (RuntimeException e) {
            return new ArrayList<String>();
        }
    }

    private List<String> linkedTo(String w, String rel) {
        try {
            return source == null ? new ArrayList<String>() : source.linkedTo(w, rel);
        } catch (RuntimeException e) {
            return new ArrayList<String>();
        }
    }

    private static void addAll(Set<String> to, String list, String self) {
        for (String p : list.split(",")) {
            String t = p.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
            if (!t.isEmpty() && !t.equals(self)) {
                to.add(t);
            }
        }
    }

    private static void put(Map<String, List<String>> out, String name, java.util.Collection<String> values, int most) {
        List<String> l = new ArrayList<String>();
        for (String v : values) {
            String t = v.replace('_', ' ');
            if (!l.contains(t)) {
                l.add(t);
            }
            if (l.size() >= most) {
                break;
            }
        }
        if (!l.isEmpty()) {
            out.put(name, l);
        }
    }

    private static boolean in(Map<String, List<String>> r, String rel, String w) {
        List<String> l = r.get(rel);
        if (l == null) {
            return false;
        }
        for (String x : l) {
            if (x.equals(w) || x.endsWith(" " + w) || w.equals(x + "s") || x.equals(w + "s")) {
                return true;
            }
        }
        return false;
    }

    static final String[][] BUILT_IN_HOMOPHONES = {{"their", "there"}, {"there", "they're"}, {"to", "too"}, {"to", "two"}, {"your", "you're"},
        {"its", "it's"}, {"hear", "here"}, {"write", "right"}, {"knight", "night"}, {"son", "sun"}, {"whole", "hole"}, {"flour", "flower"},
        {"peace", "piece"}, {"weather", "whether"}, {"break", "brake"}, {"buy", "by"}, {"sea", "see"}, {"meet", "meat"}, {"week", "weak"}};

    static final Map<String, String[]> HOMOGRAPHS = new HashMap<String, String[]>();
    static final Map<String, String[]> COLLOCATIONS = new HashMap<String, String[]>();
    static final Map<String, String[]> CONTRONYMS = new HashMap<String, String[]>();

    static {
        homograph("lead", "/liːd/ to guide", "/lɛd/ a heavy metal");
        homograph("read", "/riːd/ now", "/rɛd/ in the past");
        homograph("tear", "/tɪə/ a drop from the eye", "/tɛə/ to rip");
        homograph("wind", "/wɪnd/ moving air", "/waɪnd/ to turn or coil");
        homograph("bow", "/baʊ/ to bend in respect", "/bəʊ/ a knot or a weapon for arrows");
        homograph("live", "/lɪv/ to be alive", "/laɪv/ happening now");
        homograph("close", "/kləʊz/ to shut", "/kləʊs/ near");
        homograph("wound", "/wuːnd/ an injury", "/waʊnd/ turned or coiled");
        homograph("minute", "/ˈmɪnɪt/ sixty seconds", "/maɪˈnjuːt/ tiny");
        homograph("object", "/ˈɒbdʒɪkt/ a thing", "/əbˈdʒɛkt/ to disagree");
        homograph("present", "/ˈprɛzənt/ a gift, or now", "/prɪˈzɛnt/ to show or give");
        homograph("record", "/ˈrɛkɔːd/ a stored account", "/rɪˈkɔːd/ to store sound or facts");
        homograph("desert", "/ˈdɛzət/ a dry land", "/dɪˈzɜːt/ to abandon");
        homograph("row", "/rəʊ/ a line, or to paddle", "/raʊ/ a quarrel");
        homograph("bass", "/beɪs/ a low sound", "/bæs/ a fish");
        homograph("dove", "/dʌv/ a bird", "/dəʊv/ dived");
        homograph("content", "/ˈkɒntɛnt/ what is inside", "/kənˈtɛnt/ satisfied");
        homograph("refuse", "/ˈrɛfjuːs/ rubbish", "/rɪˈfjuːz/ to say no");
        collocation("make", "make a decision", "make a mistake", "make money", "make progress", "make an effort", "make a choice");
        collocation("do", "do homework", "do business", "do your best", "do the dishes", "do a favour", "do damage");
        collocation("take", "take a photo", "take a break", "take a risk", "take notes", "take place", "take care");
        collocation("pay", "pay attention", "pay a visit", "pay a compliment", "pay the price", "pay respect");
        collocation("catch", "catch a cold", "catch a bus", "catch fire", "catch sight of", "catch someone's eye");
        collocation("rain", "heavy rain", "light rain", "pouring rain", "torrential rain", "rain falls");
        collocation("coffee", "strong coffee", "black coffee", "weak coffee", "a cup of coffee");
        collocation("decision", "make a decision", "reach a decision", "a tough decision", "a final decision");
        collocation("mistake", "make a mistake", "a big mistake", "a common mistake", "learn from a mistake");
        collocation("attention", "pay attention", "draw attention", "attract attention", "full attention");
        collocation("crime", "commit a crime", "a serious crime", "solve a crime", "crime rate");
        collocation("traffic", "heavy traffic", "traffic jam", "traffic light", "stuck in traffic");
        collocation("break", "break a record", "break a promise", "break the law", "break the news", "break the ice");
        collocation("save", "save time", "save money", "save energy", "save a life");
        collocation("fast", "fast food", "fast asleep", "a fast car", "hold fast");
        collocation("deep", "deep sleep", "deep trouble", "deep thought", "deeply sorry");
        contronym("sanction", "to approve", "to penalise");
        contronym("cleave", "to split apart", "to cling together");
        contronym("dust", "to remove dust", "to sprinkle with dust (or powder)");
        contronym("oversight", "watchful care", "a failure to notice");
        contronym("left", "remaining", "gone away");
        contronym("bolt", "to fasten", "to run away");
        contronym("fast", "moving quickly", "fixed firmly in place");
        contronym("screen", "to show", "to hide");
        contronym("trim", "to cut pieces off", "to add decorations");
        contronym("weather", "to withstand", "to wear away");
        contronym("buckle", "to fasten together", "to bend and collapse");
        contronym("seed", "to plant seeds", "to remove seeds");
        contronym("overlook", "to watch over", "to fail to notice");
        contronym("custom", "usual and ordinary", "made specially");
        contronym("rent", "to let out", "to hire from someone");
        contronym("off", "deactivated (switched off)", "activated (the alarm went off)");
        contronym("bound", "heading somewhere", "unable to move");
        contronym("peer", "an equal", "a noble (above others)");
    }

    private static void homograph(String w, String... readings) {
        HOMOGRAPHS.put(w, readings);
    }

    private static void collocation(String w, String... with) {
        COLLOCATIONS.put(w, with);
    }

    private static void contronym(String w, String a, String b) {
        CONTRONYMS.put(w, new String[]{a, b});
    }
}
