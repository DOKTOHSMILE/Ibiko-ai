package ai.ibiko.ibiko1.words;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.ibiko.ibiko1.formulas.PartsOfSpeech;
import ai.ibiko.ibiko1.formulas.Syntax;

/**
 * Words and meanings, both ways, in context. A word's meanings (its senses); the one a sentence means (from the words
 * around it); that meaning said in a plain word instead of a long definition ("fast" here means "quick"); the word for a
 * meaning (a reverse dictionary: "someone who flies planes" is a "pilot"); a word's synonyms and opposites (in both
 * directions: "small" finds "big", which lists it); a whole sentence read word by word, its meanings put together and
 * said again in other words; and whether two sentences say the same thing, the opposite, or something else, however
 * they are worded. Built on {@link MeaningIndex}. Pure Java.
 */
public final class Meanings {
    private final MeaningIndex index;

    public Meanings(MeaningIndex index) {
        this.index = index;
        shared = this;
    }

    /** The dictionary the app opened last (memory and the truth checks use it without opening their own). */
    private static volatile Meanings shared;

    public static Meanings shared() {
        return shared;
    }

    public MeaningIndex index() {
        return index;
    }

    // ------------------------------------------------------------------ a word's senses

    /** The forms a written word may be the inflection of, itself first: "flies" → flies, fly; "bigger" → big. */
    public static List<String> bases(String word) {
        return ai.ibiko.ibiko1.comprehend.Dictionary.bases(word);
    }

    /** The headword a written word is (itself, or its base form), or null when the dictionary hasn't got it. */
    public String headword(String word) {
        for (String b : bases(word)) {
            if (index.has(b)) {
                return b;
            }
        }
        return null;
    }

    /** A written word's senses (of its base form when it is inflected), most common first. */
    public List<MeaningIndex.Sense> senses(String word) {
        // "fixed" is an adjective of its own and the past of "fix": both words' senses, the written word's first
        List<MeaningIndex.Sense> out = new ArrayList<MeaningIndex.Sense>();
        Set<Integer> seen = new HashSet<Integer>();
        for (String b : bases(word)) {
            for (MeaningIndex.Sense s : index.senses(b)) {
                if (seen.add(s.id)) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** The keys of what a sense says: its definition, synonyms and broader term. */
    static Set<String> gloss(MeaningIndex.Sense s) {
        Set<String> g = new HashSet<String>(Words.content(s.definition));
        for (String syn : s.synonyms) {
            g.addAll(Words.content(syn));
        }
        g.addAll(Words.content(s.broader));
        return g;
    }

    /**
     * The sense a word has in a sentence (extended Lesk): the sense whose meaning shares the most with the words around
     * it and with what those words themselves mean, its part of speech as the sentence uses it, the more common sense
     * when nothing tells them apart. Null when the word isn't in the dictionary.
     */
    public MeaningIndex.Sense meant(String word, String sentence, String pos) {
        List<MeaningIndex.Sense> all = senses(word);
        if (all.isEmpty()) {
            return null;
        }
        String self = Words.key(word);
        Set<String> around = new LinkedHashSet<String>(Words.content(sentence));
        around.remove(self);
        for (String b : bases(word)) {
            around.remove(Words.key(b));
        }
        Set<String> wider = new HashSet<String>();
        for (String w : around) {
            List<MeaningIndex.Sense> ws = index.senses(w);
            if (ws.isEmpty()) {
                String h = headword(w);
                ws = h == null ? ws : index.senses(h);
            }
            if (!ws.isEmpty()) {
                wider.addAll(gloss(ws.get(0)));
            }
        }
        MeaningIndex.Sense best = null;
        double bestScore = -1e9;
        for (int r = 0; r < all.size(); r++) {
            MeaningIndex.Sense s = all.get(r);
            Set<String> g = gloss(s);
            double score = 1.5 / (1 + r); // the more common sense, all else equal (the dictionary lists it first)
            for (String w : around) {
                if (g.contains(w)) {
                    score += 2 * Math.min(3, index.idf(w));
                }
            }
            for (String w : wider) {
                if (g.contains(w)) {
                    score += 0.5;
                }
            }
            if (pos != null && !pos.isEmpty()) {
                score += s.pos.equals(pos) ? 2 : -2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return best;
    }

    /**
     * A meaning in one plain word, to use in conversation instead of its definition: of the sense's synonyms (and its
     * broader term), the most everyday one (the one with the most senses of its own, then the shortest) that isn't the
     * word itself; when it has none, the definition's first short phrase.
     */
    public String plain(MeaningIndex.Sense s, String word) {
        if (s == null) {
            return null;
        }
        String self = Words.key(word == null ? s.word : word);
        String best = null;
        double bestScore = -1;
        for (String syn : s.synonyms) {
            String c = syn.trim();
            if (c.isEmpty() || Words.key(c).equals(self) || c.contains(" ") && c.split(" ").length > 2) {
                continue;
            }
            double score = Math.log(1 + index.frequency(c)) * 2 - c.length() * 0.08 - (c.contains(" ") ? 1.5 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null && !s.opposites.isEmpty()) {
            // no synonyms of its own: a word with the same opposite means the same ("fast" and "quick" are both not "slow")
            for (String opp : s.opposites) {
                for (int id : index.postings(index.asOpposite, opp.toLowerCase(Locale.ROOT))) {
                    String head = index.word[id];
                    if (!index.pos(id).equals(s.pos) || Words.key(head).equals(self) || head.contains(" ")) {
                        continue;
                    }
                    double score = Math.log(1 + index.frequency(head)) * 2 - head.length() * 0.08;
                    if (score > bestScore) {
                        bestScore = score;
                        best = head;
                    }
                }
            }
        }
        if (best == null) {
            // no synonyms of its own: a word of the same part of speech that lists this one as its synonym, in a sense
            // that means the same (it shares words with this sense's meaning: "frank" lists "dog" only as a hot dog)
            Set<String> mine = gloss(s);
            for (int id : index.postings(index.asSynonym, s.word.toLowerCase(Locale.ROOT))) {
                String head = index.word[id];
                if (!index.pos(id).equals(s.pos) || Words.key(head).equals(self) || head.contains(" ")) {
                    continue;
                }
                MeaningIndex.Sense other = index.sense(id);
                int shared = other == null ? 0 : near(gloss(other), mine);
                if (shared < 2) {
                    continue; // one shared word ("moving") is too thin to call them the same meaning
                }
                // the closest in meaning first (the most words shared), then the more everyday
                double score = shared * 3 + Math.log(1 + index.frequency(head)) - head.length() * 0.08;
                if (score > bestScore) {
                    bestScore = score;
                    best = head;
                }
            }
        }
        if (best == null) {
            // its definition's lead phrase, kept short
            String d = s.definition.replaceAll("\\([^)]*\\)", "").trim();
            String cut = d.split(";|, (?:especially|usually|often|as)\\b")[0].trim();
            if (cut.split("\\s+").length <= 6) {
                return cut;
            }
            if (!s.broader.isEmpty() && !Words.key(s.broader).equals(self)) {
                return (s.pos.equals("noun") ? "a kind of " : "") + s.broader;
            }
            return cut;
        }
        return best;
    }

    /** How many words two meanings share (by their first four letters, so "quick" and "quickly", "move" and "moving" meet). */
    static int near(Set<String> a, Set<String> b) {
        Set<String> pa = new HashSet<String>();
        for (String w : a) {
            pa.add(w.length() > 4 ? w.substring(0, 4) : w);
        }
        Set<String> shared = new HashSet<String>();
        for (String w : b) {
            String p = w.length() > 4 ? w.substring(0, 4) : w;
            if (pa.contains(p)) {
                shared.add(p);
            }
        }
        return shared.size();
    }

    // ------------------------------------------------------------------ meaning to word

    /** A word found for a meaning, with how well it fits. */
    public static final class Found {
        public final String word, pos, definition;
        public final double score;

        Found(String word, String pos, String definition, double score) {
            this.word = word;
            this.pos = pos;
            this.definition = definition;
            this.score = score;
        }

        @Override
        public String toString() {
            return word + " (" + pos + ") " + String.format(Locale.ROOT, "%.2f", score);
        }
    }

    /** What part of speech a description asks for: "someone who…" a person (noun), "to…" a verb, "full of…" an adjective. */
    static String wants(String description) {
        String d = description.trim().toLowerCase(Locale.ROOT);
        if (d.matches("^(to|the act of)\\s+\\w+.*") && !d.startsWith("the act")) {
            return "verb";
        }
        if (d.matches("^(in a|in an)\\s+.*\\b(way|manner)\\b.*")) {
            return "adverb";
        }
        if (d.matches("^(having|being|full of|without|not|very|able to|unable to|showing|lacking|feeling)\\b.*")) {
            return "adjective";
        }
        if (d.matches("^(someone|somebody|a person|person|one|people|a man|a woman|an expert|a worker|a place|a thing|something|"
                + "the act|a device|a tool|an animal|a building|a machine|a room|a group|the study|the science).*")) {
            return "noun";
        }
        return null;
    }

    private static final Set<String> PERSON = new HashSet<String>(Arrays.asList("someone", "somebody", "person", "people", "man",
            "woman", "expert", "worker", "one"));

    /**
     * The words for a meaning (a reverse dictionary): every sense is scored by the words the description shares with its
     * definition, synonyms and broader term, each weighed by how rare it is (rare words tell meanings apart), with the
     * description's words widened to their own synonyms ("planes" also finds "aircraft"); the part of speech the
     * description asks for is preferred. The best {@code most} words, best first.
     */
    public List<Found> wordsFor(String description, int most) {
        String low = description.toLowerCase(Locale.ROOT).trim();
        String pos = wants(description);
        boolean person = low.matches("^(someone|somebody|a person|person|one|people|a man|a woman|anyone)\\b.*");
        boolean place = low.matches("^(a place|place|somewhere|a room|a building)\\b.*");
        boolean very = low.matches(".*\\b(very|extremely|really|so|super|incredibly)\\b.*");
        List<String> asked = new ArrayList<String>();
        Set<String> hints = new HashSet<String>();
        for (String h : "someone somebody person people place thing something man woman anyone somewhere word term call name".split(" ")) {
            hints.add(Words.key(h));
        }
        for (String k : Words.content(description)) {
            // "someone", "a place", "a thing": what kind of word is wanted, not what it means
            if (!hints.contains(k)) {
                asked.add(k);
            }
        }
        if (asked.isEmpty()) {
            return new ArrayList<Found>();
        }
        // the description's words, and each word's own synonyms (half weight)
        Map<String, Double> weight = new LinkedHashMap<String, Double>();
        Map<String, String> from = new HashMap<String, String>();
        for (String k : asked) {
            weight.put(k, index.idf(k));
            from.put(k, k);
        }
        for (String t : Words.tokens(description)) {
            String w = t.toLowerCase(Locale.ROOT);
            if (Words.STOP.contains(w) || w.length() < 3 || !asked.contains(Words.key(w))) {
                continue;
            }
            List<MeaningIndex.Sense> ss = senses(w);
            for (int i = 0; i < Math.min(3, ss.size()); i++) {
                List<String> near = new ArrayList<String>(ss.get(i).synonyms);
                if (!ss.get(i).broader.isEmpty()) {
                    near.add(ss.get(i).broader);
                }
                for (String syn : near) {
                    if (!syn.contains(" ")) {
                        String k = Words.key(syn);
                        if (!weight.containsKey(k)) {
                            weight.put(k, index.idf(k) * 0.6);
                            from.put(k, Words.key(w));
                        }
                    }
                }
            }
        }
        if (very) {
            for (String k : new String[]{"great", "extreme", "intense", "overwhelm", "high", "rapture", "delight"}) {
                weight.putIfAbsent(k, 1.0);
                from.putIfAbsent(k, "+very");
            }
        }
        // each word: the description's words its senses cover between them (a person who flies: the noun "pilot" says
        // "someone", its verb "fly")
        Map<String, Map<String, Double>> byWord = new HashMap<String, Map<String, Double>>();
        Map<String, Boolean> fitsPos = new HashMap<String, Boolean>();
        for (Map.Entry<String, Double> e : weight.entrySet()) {
            double w = e.getValue();
            String origin = from.get(e.getKey());
            for (int pass = 0; pass < 2; pass++) {
                int[] ids = index.postings(pass == 0 ? index.inMeaning : index.asSynonym, e.getKey());
                double m = pass == 0 ? 1.0 : 1.3;
                if (ids.length > 6000) {
                    continue; // a word in that many meanings tells none apart
                }
                for (int id : ids) {
                    String head = index.word[id].toLowerCase(Locale.ROOT);
                    Map<String, Double> cov = byWord.computeIfAbsent(head, x -> new HashMap<String, Double>());
                    cov.merge(origin, w * m, Math::max);
                    if (pos != null && index.pos(id).equals(pos)) {
                        fitsPos.put(head, true);
                    }
                }
            }
        }
        Set<String> askedSelf = new HashSet<String>(asked);
        List<Found> all = new ArrayList<Found>();
        for (Map.Entry<String, Map<String, Double>> e : byWord.entrySet()) {
            String head = e.getKey();
            if (askedSelf.contains(Words.key(head)) || head.contains(" ") && head.split(" ").length > 2) {
                continue; // the answer isn't a word of the question
            }
            double sum = 0;
            int covered = 0;
            for (Map.Entry<String, Double> c : e.getValue().entrySet()) {
                sum += c.getValue();
                covered += c.getKey().equals("+very") ? 0 : 1;
            }
            double coverage = covered / (double) asked.size();
            double s = sum * Math.pow(coverage, 2);
            if (pos != null) {
                s *= fitsPos.containsKey(head) ? 1.4 : 0.4;
            }
            if (head.contains(" ")) {
                s *= 0.7; // one word is what was asked for
            }
            all.add(new Found(head, pos == null ? "" : pos, null, s));
        }
        all.sort((a, b) -> Double.compare(b.score, a.score));
        ai.ibiko.ibiko1.neural.Neural neural = ai.ibiko.ibiko1.neural.Neural.get();
        int pool = Math.min(all.size(), most * 24);
        if (neural != null && !all.isEmpty()) {
            // the words whose use sits nearest the description's meaning join the candidates (docs/IBIKO_NEURAL.md)
            float[] v = neural.attention.encode(description);
            Set<String> have = new HashSet<String>();
            for (int i = 0; i < pool; i++) {
                have.add(all.get(i).word);
            }
            List<Found> more = new ArrayList<Found>();
            Set<String> said = new HashSet<String>();
            for (String t : Words.tokens(low)) {
                said.add(Words.key(t));
            }
            if (v != null) {
                for (String w : neural.embeddings.nearest(v, 30, null)) {
                    // not a word of the description itself, in any form ("poems" doesn't answer "writes poems")
                    if (!have.contains(w) && !index.senses(w).isEmpty() && !Words.STOP.contains(w) && !said.contains(Words.key(w))) {
                        more.add(new Found(w, pos == null ? "" : pos, null, all.get(Math.min(pool - 1, most * 2)).score));
                    }
                }
            }
            all = new ArrayList<Found>(all.subList(0, pool));
            all.addAll(more);
            pool = all.size();
        }
        List<Found> ranked = new ArrayList<Found>();
        for (int i = 0; i < pool; i++) {
            Found f = all.get(i);
            MeaningIndex.Sense best = bestSenseFor(f.word, asked, pos);
            double s = f.score;
            for (MeaningIndex.Sense any : index.senses(f.word)) {
                // the kind of thing asked for: any of the word's noun senses says it is a person, or a place
                if (any.pos.equals("noun") && (person && any.definition.matches("(?i)^(someone|a person|one|an? \\w+ (?:who|whose)|a worker|an expert|a specialist|a craftsman).*")
                        || place && any.definition.matches("(?i)^(a|an|the)? ?(place|room|building|area|site|establishment|institution|facility|depository)\\b.*"))) {
                    s *= 1.8;
                    break;
                }
            }
            s *= 1 + 0.06 * Math.log(1 + index.frequency(f.word)); // the everyday word, when two fit as well
            if (neural != null) {
                // the word vectors: how well the description lines up with the word as it is used, and with what the
                // dictionary says the word means (docs/IBIKO_NEURAL.md)
                double a = best == null ? 0 : neural.attention.match(description, best.definition);
                s *= Math.pow(Math.max(0.05, a), 2) * 4;
            }
            ranked.add(new Found(f.word, best == null ? f.pos : best.pos, best == null ? null : best.definition, s));
        }
        ranked.sort((a, b) -> Double.compare(b.score, a.score));
        return ranked.size() > most ? new ArrayList<Found>(ranked.subList(0, most)) : ranked;
    }

    /** Of a word's senses, the one closest to the asked words (for showing what it means). */
    private MeaningIndex.Sense bestSenseFor(String word, List<String> asked, String pos) {
        MeaningIndex.Sense best = null;
        int bestHit = -1;
        for (MeaningIndex.Sense s : index.senses(word.toLowerCase(Locale.ROOT))) {
            if (pos != null && !s.pos.equals(pos) && best != null) {
                continue;
            }
            Set<String> g = gloss(s);
            int hit = 0;
            for (String a : asked) {
                hit += g.contains(a) ? 1 : 0;
            }
            if (hit > bestHit) {
                bestHit = hit;
                best = s;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ synonyms and opposites

    /** A word's synonyms: those its senses list, and the words that list it, of the same part of speech; the sense in a
     * sentence first when one is given. */
    public List<String> synonyms(String word, String sentence) {
        Set<String> out = new LinkedHashSet<String>();
        String self = Words.key(word);
        List<MeaningIndex.Sense> all = senses(word);
        MeaningIndex.Sense first = sentence == null ? (all.isEmpty() ? null : all.get(0)) : meant(word, sentence, null);
        List<MeaningIndex.Sense> order = new ArrayList<MeaningIndex.Sense>();
        if (first != null) {
            order.add(first);
        }
        for (MeaningIndex.Sense s : all) {
            if (first == null || s.id != first.id) {
                order.add(s);
            }
        }
        String pos = first == null ? null : first.pos;
        for (MeaningIndex.Sense s : order) {
            if (pos != null && !s.pos.equals(pos)) {
                continue;
            }
            for (String syn : s.synonyms) {
                if (!Words.key(syn).equals(self)) {
                    out.add(syn);
                }
            }
        }
        String h = headword(word);
        for (int id : index.postings(index.asSynonym, h == null ? word.toLowerCase(Locale.ROOT) : h)) {
            String head = index.word[id];
            if (!Words.key(head).equals(self) && (pos == null || index.pos(id).equals(pos)) && out.size() < 16) {
                out.add(head);
            }
        }
        List<String> l = new ArrayList<String>(out);
        return l.size() > 12 ? l.subList(0, 12) : l;
    }

    /** The common words by their sounds ({@link Phonetic}): built once, the first time a typo needs it. */
    private volatile Map<String, List<String>> bySound;

    /**
     * Common words that sound like this one ("fone": phone; "nolij": knowledge), from their consonant sounds; empty when
     * none. Only words with two meanings or more are kept by sound, so the index stays small and the rare ones don't win.
     */
    public List<String> soundsLike(String typed) {
        Map<String, List<String>> m = bySound;
        if (m == null) {
            synchronized (this) {
                if (bySound == null) {
                    Map<String, List<String>> built = new HashMap<String, List<String>>();
                    for (Map.Entry<String, int[]> e : index.heads.entrySet()) {
                        String w = e.getKey();
                        if (e.getValue().length >= 2 && w.length() >= 3 && w.length() <= 14 && w.matches("[a-z]+")) {
                            built.computeIfAbsent(Phonetic.key(w), k -> new ArrayList<String>(2)).add(w);
                        }
                    }
                    bySound = built;
                }
                m = bySound;
            }
        }
        List<String> l = m.get(Phonetic.key(typed));
        return l == null ? new ArrayList<String>() : l;
    }

    /** A word's opposites: those its senses list, the words that list it, and its own negative form when there is one. */
    public List<String> opposites(String word) {
        Set<String> out = new LinkedHashSet<String>();
        String h = headword(word);
        String w = h == null ? word.toLowerCase(Locale.ROOT) : h;
        for (MeaningIndex.Sense s : index.senses(w)) {
            out.addAll(s.opposites);
        }
        for (int id : index.postings(index.asOpposite, w)) {
            out.add(index.word[id]);
        }
        for (String neg : new String[]{"un" + w, "in" + w, "im" + w, "dis" + w, "non" + w, "ir" + w, "il" + w}) {
            if (index.has(neg) && out.size() < 10) {
                out.add(neg);
            }
        }
        if (w.matches("(un|dis|non)\\w{3,}")) {
            String bare = w.replaceFirst("^(un|dis|non)", "");
            if (index.has(bare)) {
                out.add(bare);
            }
        }
        out.remove(w);
        return new ArrayList<String>(out);
    }

    /** An opposite found through what a word means, and the way it was found. */
    public static final class Reasoned {
        public final List<String> opposites;
        /** "“ancient” means very old, and the opposite of “old” is…" */
        public final String via, through;

        Reasoned(List<String> opposites, String via, String through) {
            this.opposites = opposites;
            this.via = via;
            this.through = through;
        }
    }

    private static final java.util.regex.Pattern INTENSIFIER = java.util.regex.Pattern.compile(
            "(?i)^(?:very|extremely|quite|rather|highly|exceedingly|especially|really|most|so|too|somewhat|fairly|slightly|a bit|more|less|being)\\s+");

    /**
     * Opposites reasoned from meaning when the dictionary lists none for the word itself: a sense said in a word or two
     * ("ancient": very old) takes that word's opposites ("old": new, young); else a synonym's opposites ("huge" is like
     * "large", whose opposite is "small"). Null when neither gives one.
     */
    public Reasoned oppositesByMeaning(String word) {
        String h = headword(word);
        String w = h == null ? word.toLowerCase(Locale.ROOT) : h;
        for (MeaningIndex.Sense s : index.senses(w)) {
            String gloss = s.definition.replaceAll("\\(.*?\\)", " ").split("[;:]")[0].trim();
            String core = INTENSIFIER.matcher(gloss).replaceFirst("").trim();
            if (core.isEmpty() || core.split("\\s+").length > 2) {
                continue; // a long definition: no single word to take the opposite of
            }
            String key = core.split("\\s+")[core.split("\\s+").length - 1].toLowerCase(Locale.ROOT);
            if (key.equals(w) || !index.has(key)) {
                continue;
            }
            List<String> opp = direct(key);
            if (!opp.isEmpty()) {
                return new Reasoned(opp, gloss, key);
            }
        }
        for (MeaningIndex.Sense s : index.senses(w)) {
            for (String syn : s.synonyms) {
                String k = syn.toLowerCase(Locale.ROOT);
                if (!k.equals(w) && !k.contains(" ")) {
                    List<String> opp = direct(k);
                    if (!opp.isEmpty()) {
                        return new Reasoned(opp, "like “" + k + "”", k);
                    }
                }
            }
        }
        return null;
    }

    /** The opposites the dictionary lists for a word itself (its senses, and the words that list it). */
    private List<String> direct(String w) {
        Set<String> out = new LinkedHashSet<String>();
        for (MeaningIndex.Sense s : index.senses(w)) {
            out.addAll(s.opposites);
        }
        for (int id : index.postings(index.asOpposite, w)) {
            out.add(index.word[id]);
        }
        out.remove(w);
        List<String> l = new ArrayList<String>(out);
        return l.size() > 6 ? l.subList(0, 6) : l;
    }

    // ------------------------------------------------------------------ a sentence read

    /** One word of a sentence, read: as written, its base, its part of speech here, the sense it has here. */
    public static final class Word {
        public final String written, base, tag;
        public final MeaningIndex.Sense sense;
        /** The meaning in a plain word. */
        public final String plain;

        Word(String written, String base, String tag, MeaningIndex.Sense sense, String plain) {
            this.written = written;
            this.base = base;
            this.tag = tag;
            this.sense = sense;
            this.plain = plain;
        }
    }

    /** A sentence read: each meaningful word's sense, who does what to whom, and the sentence said again in other words. */
    public static final class Reading {
        public final String sentence;
        public final List<Word> words = new ArrayList<Word>();
        public String subject, verb, object, tree;
        /** The same sentence in other words (each word its plain synonym where it has one). */
        public String inOtherWords;

        Reading(String sentence) {
            this.sentence = sentence;
        }
    }

    private static String posOf(PartsOfSpeech.Tag t) {
        switch (t) {
            case NOUN: return "noun";
            case VERB: return "verb";
            case ADJECTIVE: return "adjective";
            case ADVERB: return "adverb";
            default: return null;
        }
    }

    /**
     * A sentence read with speed: every meaningful word looked up, its sense in this sentence chosen from the words
     * around it, its meaning said in a plain word; its structure parsed (subject, verb, object); and the whole said
     * again with the plain words in place, so the meaning is put together from the words' meanings.
     */
    public Reading read(String sentence) {
        Reading r = new Reading(sentence);
        List<PartsOfSpeech.Word> tagged = PartsOfSpeech.tag(sentence, null);
        StringBuilder again = new StringBuilder();
        for (PartsOfSpeech.Word w : tagged) {
            String low = w.text.toLowerCase(Locale.ROOT);
            String pos = posOf(w.tag);
            boolean meaningful = pos != null && !Words.STOP.contains(low) && w.text.matches("[\\p{L}'-]+") && low.length() > 2;
            String out = w.text;
            if (meaningful) {
                MeaningIndex.Sense s = meant(w.text, sentence, pos);
                // a word is only put another way when its sense here is its usual one (of that part of speech): a rare
                // sense guessed from little context ("dog" as a marine) is shown, never swapped in
                boolean usual = false;
                for (MeaningIndex.Sense first : senses(w.text)) {
                    if (first.pos.equals(pos)) {
                        usual = s != null && first.id == s.id;
                        break;
                    }
                }
                if (s != null) {
                    String plain = plain(s, w.text);
                    String base = headword(w.text);
                    r.words.add(new Word(w.text, base == null ? low : base, pos, s, plain));
                    String b0 = base == null ? low : base;
                    // only a word at least as everyday as the one written: plainer, never rarer ("apace" for "quickly")
                    boolean everyday = plain != null && index.frequency(plain) >= Math.max(20, index.frequency(b0) * 0.8);
                    if (plain != null && !plain.contains(" ") && plain.matches("[\\p{L}-]+") && everyday && usual) {
                        out = Morph.inflectLike(plain, w.text, base == null ? low : base, pos);
                        if (Character.isUpperCase(w.text.charAt(0))) {
                            out = Character.toUpperCase(out.charAt(0)) + out.substring(1);
                        }
                    }
                }
            }
            if (w.tag == PartsOfSpeech.Tag.PUNCTUATION) {
                again.append(out);
            } else {
                again.append(again.length() == 0 ? "" : " ").append(out);
            }
        }
        r.inOtherWords = again.toString();
        List<Syntax.Tree> trees = Syntax.parse(sentence);
        if (!trees.isEmpty()) {
            Syntax.Tree t = trees.get(0);
            r.tree = t.bracketed();
            for (Syntax.Tree k : t.kids) {
                if (k.label.equals("NP") && r.subject == null) {
                    r.subject = k.words();
                } else if (k.label.equals("VP")) {
                    for (Syntax.Tree v : k.phrases()) {
                        for (Syntax.Tree leaf : v.kids) {
                            if (leaf.word != null && leaf.label.equals("V") && r.verb == null) {
                                r.verb = leaf.word;
                            }
                        }
                        if (v != k && v.label.equals("NP") && r.object == null) {
                            r.object = v.words();
                        }
                    }
                }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ the same meaning, worded another way

    /** How two sentences' meanings compare. */
    public enum Verdict { SAME, OPPOSITE, RELATED, DIFFERENT }

    public static final class Comparison {
        public final Verdict verdict;
        public final double score;
        /** The word pairs that matched (by the same word, a synonym or a shared broader term), and any opposites. */
        public final List<String> matched = new ArrayList<String>(), opposed = new ArrayList<String>();

        Comparison(Verdict verdict, double score) {
            this.verdict = verdict;
            this.score = score;
        }
    }

    /** What a word can mean, as keys: itself, its synonyms, its broader terms, in its first senses. */
    private Set<String> concept(String word) {
        Set<String> c = new HashSet<String>();
        c.add(Words.key(word));
        String h = headword(word);
        if (h != null) {
            c.add(Words.key(h));
            List<MeaningIndex.Sense> ss = index.senses(h);
            for (int i = 0; i < Math.min(3, ss.size()); i++) {
                for (String syn : ss.get(i).synonyms) {
                    c.add(Words.key(syn));
                }
                if (!ss.get(i).broader.isEmpty()) {
                    c.add(Words.key(ss.get(i).broader));
                }
            }
            for (int id : index.postings(index.asSynonym, h)) {
                c.add(Words.key(index.word[id]));
            }
        }
        return c;
    }

    /**
     * Whether two sentences say the same thing however they are worded: each meaningful word of one is matched to a word
     * of the other by the word itself, a synonym or a shared broader term; a word whose opposite stands in the other
     * makes them opposites ("the car is fast" and "the car is slow").
     */
    public Comparison compare(String a, String b) {
        List<String> wa = meaningful(a), wb = meaningful(b);
        if (wa.isEmpty() || wb.isEmpty()) {
            return new Comparison(Verdict.DIFFERENT, 0);
        }
        Map<String, Set<String>> cb = new LinkedHashMap<String, Set<String>>();
        for (String w : wb) {
            cb.put(w, concept(w));
        }
        Comparison tmp = new Comparison(Verdict.DIFFERENT, 0);
        int hitA = 0;
        Set<String> usedB = new HashSet<String>();
        boolean opposite = false;
        for (String w : wa) {
            Set<String> ca = concept(w);
            String match = null;
            for (Map.Entry<String, Set<String>> e : cb.entrySet()) {
                Set<String> both = new HashSet<String>(ca);
                both.retainAll(e.getValue());
                if (!both.isEmpty()) {
                    match = e.getKey();
                    break;
                }
            }
            if (match != null) {
                hitA++;
                usedB.add(match);
                tmp.matched.add(w + " ≈ " + match);
                continue;
            }
            List<String> opp = opposites(w);
            boolean opposed = false;
            for (String o : wb) {
                for (String x : opp) {
                    if (Words.key(x).equals(Words.key(o))) {
                        opposite = opposed = true;
                        tmp.opposed.add(w + " ↔ " + o);
                        usedB.add(o);
                    }
                }
            }
            // no link in the dictionary and not opposites: the word vectors say whether they are used alike
            // ("vehicle" and "car", "freezing" and "cold")
            ai.ibiko.ibiko1.neural.Neural n = opposed ? null : ai.ibiko.ibiko1.neural.Neural.get();
            if (n != null) {
                String best = null;
                double top = CLOSE;
                for (String o : wb) {
                    double sim = n.embeddings.similarity(w, o);
                    if (sim >= top && !opposites(o).contains(w)) {
                        top = sim;
                        best = o;
                    }
                }
                if (best != null) {
                    hitA++;
                    usedB.add(best);
                    tmp.matched.add(w + " ~ " + best);
                }
            }
        }
        double score = (hitA / (double) wa.size() + usedB.size() / (double) wb.size()) / 2;
        Verdict v = opposite && score >= 0.4 ? Verdict.OPPOSITE : score >= 0.75 ? Verdict.SAME : score >= 0.4 ? Verdict.RELATED : Verdict.DIFFERENT;
        Comparison c = new Comparison(v, score);
        c.matched.addAll(tmp.matched);
        c.opposed.addAll(tmp.opposed);
        return c;
    }

    // ------------------------------------------------------------------ the same thing asked, guarded for the truth

    private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final java.util.regex.Pattern NOT = java.util.regex.Pattern.compile(
            "(?i)\\b(not|no|never|none|nobody|nothing|neither|nor|without)\\b|n't\\b");

    /** The question and frame words the gate leaves aside: they don't say what is asked about. */
    private static final Set<String> FRAME = new HashSet<String>(Arrays.asList(("how what which who whom whose where when why "
            + "did do does is are was were be been will would can could should tell me please know about you your i my "
            + "much many city place country person people year time thing kind type name one").split(" ")));

    /**
     * Two words name different things: kinds of the same thing (Kenya and Uganda, Monday and Tuesday), or both names
     * of a kind of thing at all (car and bus: the dictionary reaches "vehicle" from one and "conveyance" from the
     * other). Alike in use, never the same; only the dictionary calling them synonyms joins them.
     */
    boolean siblings(String a, String b) {
        if (thing(a) && thing(b)) {
            return true;
        }
        Set<String> ba = broader(a), bb = broader(b);
        ba.retainAll(bb);
        return !ba.isEmpty();
    }

    /** The word is first of all the name of a kind of thing: its usual sense is a noun with a broader term. */
    private boolean thing(String word) {
        String h = headword(word);
        List<MeaningIndex.Sense> ss = h == null ? null : index.senses(h);
        return ss != null && !ss.isEmpty() && ss.get(0).pos.equals("noun") && !ss.get(0).broader.isEmpty();
    }

    /** Every broader term of the word's noun senses. */
    private Set<String> broader(String word) {
        Set<String> out = new HashSet<String>();
        String h = headword(word);
        if (h == null) {
            return out;
        }
        for (MeaningIndex.Sense s : index.senses(h)) {
            if (!s.broader.isEmpty() && s.pos.equals("noun")) {
                out.add(s.broader.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    /** Two words the dictionary makes the same idea: the same word, a synonym, or one listed as the other's. */
    boolean linked(String a, String b) {
        if (Words.key(a).equals(Words.key(b))) {
            return true;
        }
        Set<String> ca = concept(a);
        ca.remove(Words.key(a));
        String h = headword(a);
        if (h != null) {
            List<MeaningIndex.Sense> ss = index.senses(h);
            for (int i = 0; i < Math.min(3, ss.size()); i++) {
                ca.remove(Words.key(ss.get(i).broader)); // a shared broader term makes siblings, not synonyms
            }
        }
        return ca.contains(Words.key(b));
    }

    /**
     * Whether two questions (or statements) ask the same thing however they are worded, strictly enough that what
     * was confirmed or judged for one may be used for the other. The numbers must be the same, both or neither
     * negated, and every word of each must have its partner in the other: the same word, a dictionary synonym, or a
     * word the vectors put close that the dictionary doesn't call a sibling ("Kenya" and "Uganda") or an opposite
     * ("rose" and "fell"). Vectors alone never decide what is true.
     */
    public boolean sameAsked(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        if (!numbers(a).equals(numbers(b)) || NOT.matcher(a).find() != NOT.matcher(b).find()) {
            return false;
        }
        List<String> wa = asked(a), wb = asked(b);
        if (wa.isEmpty() || wb.isEmpty()) {
            return false;
        }
        ai.ibiko.ibiko1.neural.Neural n = ai.ibiko.ibiko1.neural.Neural.get();
        return partnered(wa, wb, n) && partnered(wb, wa, n);
    }

    private List<String> asked(String s) {
        List<String> out = new ArrayList<String>();
        for (String w : meaningful(s)) {
            if (!FRAME.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    private static Set<String> numbers(String s) {
        Set<String> out = new HashSet<String>();
        java.util.regex.Matcher m = NUMBER.matcher(s);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private boolean partnered(List<String> from, List<String> to, ai.ibiko.ibiko1.neural.Neural n) {
        for (String w : from) {
            boolean ok = false;
            for (String o : to) {
                if (linked(w, o) || linked(o, w)) {
                    ok = true;
                    break;
                }
            }
            if (!ok && n != null) {
                for (String o : to) {
                    if (n.embeddings.similarity(w, o) >= SAME_IDEA && !siblings(w, o)
                            && !opposites(w).contains(o) && !opposites(o).contains(w)) {
                        ok = true;
                        break;
                    }
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** How close two words' vectors must be for the gate to call them the same idea (the dictionary not objecting). */
    static final double SAME_IDEA = 0.5;

    /** How close two words' vectors must be to count as the same idea when the dictionary doesn't link them. */
    static final double CLOSE = 0.62;

    /** A sentence's meaningful words, as written, each once. */
    static List<String> meaningful(String s) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String t : Words.tokens(s)) {
            String low = t.toLowerCase(Locale.ROOT);
            if (low.length() > 1 && !Words.STOP.contains(low) && !low.matches("\\d+") && seen.add(Words.key(low))) {
                out.add(low);
            }
        }
        return out;
    }
}
