package ai.ibiko.ibiko1.words;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.ibiko.ibiko1.formulas.PartsOfSpeech;

/**
 * Context tags: each word of a sentence marked with what kind of word it is and, when it has meanings in more than
 * one subject, which subject it means here. "Root" in "the root of the tree" is {@code n.root@botany}; in "you need
 * root access on the Linux server" it is {@code n.root@computing}; in "the square root of 16" it is
 * {@code n.root@maths}. The subject is read from the words around it, or from what the conversation is about when the
 * sentence itself doesn't say. A whole sentence gets a short code too ({@code [computing] v.need n.root@computing …}),
 * so what it learns and what it is asked can be matched by meaning, not by spelling.
 *
 * <p>The letters: n noun, m name, p pronoun, v verb, b auxiliary (be, have, do, can…), a adjective, r adverb,
 * i preposition, c conjunction, d determiner, j interjection, u number.
 *
 * <p>Meanings the dictionary lacks (root as the administrator account, mouse the pointing device) come from a small
 * table of senses by subject ({@code knowledge/senses_extra.tsv}). Pure Java.
 */
public final class SenseTags {

    /** One word read in its sentence. */
    public static final class Tag {
        public final String word;
        /** The kind of word: n, m, p, v, b, a, r, i, c, d, j, u. */
        public final char kind;
        /** The meaning chosen, or null for a word the dictionary hasn't got. */
        public final MeaningIndex.Sense sense;
        /** The subject of that meaning when the word has meanings in more than one; else null. */
        public final String subject;

        Tag(String word, char kind, MeaningIndex.Sense sense, String subject) {
            this.word = word;
            this.kind = kind;
            this.sense = sense;
            this.subject = subject;
        }

        @Override
        public String toString() {
            return kind + "." + word.toLowerCase(Locale.ROOT) + (subject == null ? "" : "@" + subject);
        }
    }

    /** The subjects and the words that point to them (written as keys, so "servers" and "server" both count). */
    static final Map<String, Set<String>> CUES = new LinkedHashMap<String, Set<String>>();

    private static void cues(String subject, String words) {
        Set<String> keys = new HashSet<String>();
        keys.add(Words.key(subject)); // "root in computing": the subject's own name says it
        for (String w : words.split("\\s+")) {
            keys.add(Words.key(w));
        }
        CUES.put(subject, keys);
    }

    static {
        cues("computing", "computer computers software program programs programming programmer code coding coder linux unix windows "
                + "macos server servers app apps file files folder directory install installed sudo terminal command data database "
                + "network internet website site browser online keyboard screen click password login log user users admin "
                + "administrator access android iphone laptop git github compile python javascript api cloud download upload email "
                + "tweet facebook instagram twitter tiktok linkedin whatsapp youtube hashtag wifi digital kernel cpu ram disk storage "
                + "binary algorithm script variable html css url permission permissions os device devices developer debug "
                + "malware hacker hacked encryption pointer cursor desktop smartphone phone username repo repository");
        cues("maths", "mathematical number numbers equation equations square cube multiply multiplied divide divided division sum plus minus "
                + "times polynomial algebra geometry calculus solve solution formula fraction integer decimal triangle angle circle "
                + "graph median statistics exponent quadratic prime digit digits math maths mathematics calculate calculation "
                + "exactly radius diameter perimeter percentage");
        cues("botany", "botanist tree trees plant plants leaf leaves soil seed seeds flower flowers garden farm farmer crop crops trunk "
                + "botany botanical vegetable forest grass bark herb weed sprout");
        cues("animals", "animal animals cat cats dog dogs bird birds fish insect insects wild zoo species pet mammal nest hunt prey "
                + "wing wings feathers fur tail cage rodent rodents snake snakes reptile reptiles boa boas vertebrate vertebrates aquatic "
                + "creature creatures gills shellfish goat goats cow cows lion");
        cues("finance", "money deposit deposited loan loans interest cash naira dollar dollars pay paid payment account accounts "
                + "savings invest investment investor investors stock shares market price prices profit credit debit atm withdraw "
                + "currency financial economy tax bank banks");
        cues("medicine", "doctor doctors hospital patient patients disease diseases illness sick fever pain blood heart treatment "
                + "medicine drug drugs nurse symptom symptoms infection health surgery clinic malaria vaccine eye eyes body pill");
        cues("music", "music musical song songs sing singer band guitar piano melody chord tune album beat drum concert instrument "
                + "orchestra loud play played playing listen");
        cues("sport", "football soccer team teams player players score scored league cup tennis cricket basketball stadium coach "
                + "referee win won champion championship fifa club fans");
        cues("law", "court judge lawyer lawyers law legal crime criminal guilty prison jail police accused trial arrest sued "
                + "contract prisoner justice");
        cues("geography", "river rivers lake sea ocean mountain mountains hill hills valley land coast island city country map "
                + "desert flows flow shore");
        cues("physics", "energy force electric electricity electron volt voltage watt watts circuit physics magnet motion "
                + "velocity gravity atom joule");
        cues("chemistry", "chemical element elements molecule molecules acid reaction compound chemistry oxygen carbon metal "
                + "hydrogen symbol");
        cues("astronomy", "space sun moon orbit orbits planet planets galaxy telescope astronomy solar earth sky constellation");
        cues("language", "word words grammar verb verbs noun nouns spelling language suffix prefix affix affixes linguistics "
                + "alphabet pronounce pronounced meaning spelled syllable");
        cues("transport", "car cars road drive driving vehicle vehicles ship ships boat boats harbour harbor train bus truck lorry "
                + "airport plane fuel engine traffic");
        cues("food", "food eat eating ate cook cooking recipe soup kitchen restaurant drink meal dish taste rice stew");
        cues("religion", "church god prayer pray priest mosque catholic christian muslim holy worship bible mythology");
        cues("politics", "government president election vote votes party minister senate politics governor policy");
        cues("business", "company companies business brand customers customer sell selling sales factory shop startup "
                + "marketing ceo founded firm");
        cues("military", "army soldier soldiers war military battle troops weapon weapons");
        cues("education", "school student students teacher teachers university class exam lesson");
        cues("weather", "weather season seasons rain summer winter autumn temperature");
        cues("fashion", "fashion clothes dress wear runway photoshoot");
        cues("art", "painting artist actor actress film movie novel writer poem author famous");
        cues("biology", "cell cells biology organism organisms gene genes dna tissue living");
    }

    /** Subject labels the dictionary writes in front of a definition: "(botany) the usually underground organ …". */
    private static final Map<String, String> LABELS = new HashMap<String, String>();

    static {
        String[][] l = {{"botany", "botany"}, {"linguistics", "language"}, {"grammar", "language"}, {"computing", "computing"},
            {"computer science", "computing"}, {"mathematics", "maths"}, {"geometry", "maths"}, {"statistics", "maths"},
            {"music", "music"}, {"law", "law"}, {"medicine", "medicine"}, {"anatomy", "medicine"}, {"pathology", "medicine"},
            {"physics", "physics"}, {"chemistry", "chemistry"}, {"sports", "sport"}, {"football", "sport"}, {"baseball", "sport"},
            {"cricket", "sport"}, {"tennis", "sport"}, {"finance", "finance"}, {"economics", "finance"}, {"banking", "finance"},
            {"zoology", "animals"}, {"ornithology", "animals"}, {"astronomy", "astronomy"}, {"biology", "biology"},
            {"virology", "biology"}, {"military", "military"}, {"nautical", "transport"}, {"mythology", "religion"},
            {"theology", "religion"}, {"Greek mythology", "religion"}, {"Roman mythology", "religion"}};
        for (String[] p : l) {
            LABELS.put(p[0].toLowerCase(Locale.ROOT), p[1]);
        }
    }

    private final Meanings meanings;
    /** Extra senses by headword, from the table. */
    private final Map<String, List<MeaningIndex.Sense>> extra = new HashMap<String, List<MeaningIndex.Sense>>();
    /** Each extra sense's subject, by its id (negative, so it never meets a dictionary id). */
    private final Map<Integer, String> extraSubject = new HashMap<Integer, String>();

    /** {@code extras} is the senses table (word, part of speech, subject, broader term, definition); it may be null. */
    public SenseTags(Meanings meanings, InputStream extras) {
        this.meanings = meanings;
        if (extras != null) {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(extras, StandardCharsets.UTF_8))) {
                String line;
                int id = -1;
                while ((line = r.readLine()) != null) {
                    if (line.startsWith("#") || line.trim().isEmpty()) {
                        continue;
                    }
                    String[] c = line.split("\t");
                    if (c.length < 5) {
                        continue;
                    }
                    String w = c[0].trim().toLowerCase(Locale.ROOT);
                    MeaningIndex.Sense s = new MeaningIndex.Sense(id, new String[] {w, c[1].trim(), c[4].trim(), "", "", c[3].trim()});
                    extraSubject.put(id, c[2].trim());
                    id--;
                    List<MeaningIndex.Sense> l = extra.get(w);
                    if (l == null) {
                        extra.put(w, l = new ArrayList<MeaningIndex.Sense>());
                    }
                    l.add(s);
                }
            } catch (IOException e) {
                // without the table, the dictionary's own senses still serve
            }
        }
    }

    // ------------------------------------------------------------------ subjects

    /** What a text is about, from the words in it, or null when it doesn't lean one way. {@code skip} isn't counted. */
    public static String subject(String text, String skip) {
        return subject(text, skip, 1);
    }

    /** As {@link #subject(String, String)}, needing at least {@code least} words that point to it. */
    static String subject(String text, String skip, int least) {
        Map<String, Integer> hits = new HashMap<String, Integer>();
        String skipKey = skip == null ? null : Words.key(skip.toLowerCase(Locale.ROOT));
        for (String w : Words.tokens(text)) {
            String k = Words.key(w.toLowerCase(Locale.ROOT));
            if (k.equals(skipKey)) {
                continue;
            }
            for (Map.Entry<String, Set<String>> e : CUES.entrySet()) {
                if (e.getValue().contains(k)) {
                    Integer n = hits.get(e.getKey());
                    hits.put(e.getKey(), n == null ? 1 : n + 1);
                }
            }
        }
        String best = null;
        int top = 0;
        boolean tie = false;
        for (Map.Entry<String, Integer> e : hits.entrySet()) {
            if (e.getValue() > top) {
                best = e.getKey();
                top = e.getValue();
                tie = false;
            } else if (e.getValue() == top) {
                tie = true;
            }
        }
        return tie || top < least ? null : best;
    }

    /** What a text is about, or null. */
    public static String subject(String text) {
        return subject(text, null);
    }

    /** The subject a meaning belongs to: its table entry, the label on its definition, or the words in its gloss. */
    public String subjectOf(MeaningIndex.Sense s) {
        String table = extraSubject.get(s.id);
        if (table != null) {
            return table;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\(([^)]+)\\)").matcher(s.definition);
        if (m.find()) {
            String label = LABELS.get(m.group(1).toLowerCase(Locale.ROOT));
            if (label != null) {
                return label;
            }
        }
        // its broader term alone ("plant organ", "programing language"), else two words of its gloss: one stray word
        // ("plane" in "a plane figure") doesn't make a subject
        String broader = subject(s.broader, s.word);
        if (broader != null) {
            return broader;
        }
        StringBuilder g = new StringBuilder(s.definition);
        for (String syn : s.synonyms) {
            g.append(' ').append(syn);
        }
        return subject(g.toString(), s.word, 2);
    }

    // ------------------------------------------------------------------ meanings

    /** Every meaning a word has: the dictionary's (most common first), then the table's for subjects it doesn't cover. */
    public List<MeaningIndex.Sense> senses(String word) {
        List<MeaningIndex.Sense> all = new ArrayList<MeaningIndex.Sense>(meanings == null ? new ArrayList<MeaningIndex.Sense>()
                : meanings.senses(word));
        Set<String> covered = new HashSet<String>();
        for (MeaningIndex.Sense s : all) {
            covered.add(s.pos + "|" + subjectOf(s));
        }
        String low = word.toLowerCase(Locale.ROOT);
        List<String> forms = new ArrayList<String>();
        forms.add(low);
        for (String b : Meanings.bases(low)) {
            if (!forms.contains(b)) {
                forms.add(b);
            }
        }
        for (String f : forms) {
            List<MeaningIndex.Sense> more = extra.get(f);
            if (more == null) {
                continue;
            }
            for (MeaningIndex.Sense s : more) {
                // "bank, finance" is in the dictionary already: the table's copy isn't needed; "root, computing" is not
                if (!covered.contains(s.pos + "|" + extraSubject.get(s.id)) || all.isEmpty()) {
                    all.add(s);
                }
            }
            break;
        }
        return all;
    }

    /** The subjects a word's meanings span, in the order of its meanings. */
    public List<String> subjects(String word) {
        Set<String> out = new LinkedHashSet<String>();
        for (MeaningIndex.Sense s : senses(word)) {
            String sub = subjectOf(s);
            if (sub != null) {
                out.add(sub);
            }
        }
        return new ArrayList<String>(out);
    }

    /** True when the word means different things in different subjects ("root", "bank", "mouse"). */
    public boolean ambiguous(String word) {
        return subjects(word).size() >= 2;
    }

    /**
     * The meaning a word has in a sentence: the one whose subject is the sentence's (or, when the sentence doesn't
     * say, the conversation's {@code context}), whose definition shares the most words with the sentence, of the part
     * of speech it is used as; the most common meaning when nothing tells them apart. Null for a word with none.
     */
    public MeaningIndex.Sense meant(String word, String sentence, String context, String pos) {
        List<MeaningIndex.Sense> all = senses(word);
        if (all.isEmpty()) {
            return null;
        }
        String text = sentence == null ? "" : sentence;
        // the other words first; then the sentence with the word itself ("a bug in my code": "code" says computing for
        // "bug"); then what the conversation is about
        String about = subject(text, word);
        if (about == null) {
            about = subject(text);
        }
        if (about == null) {
            about = context;
        }
        Set<String> around = new HashSet<String>(Words.content(sentence == null ? "" : sentence));
        around.remove(Words.key(word.toLowerCase(Locale.ROOT)));
        MeaningIndex.Sense best = null;
        double bestScore = -1e9;
        int dictionary = 0;
        for (MeaningIndex.Sense s : all) {
            boolean table = s.id < 0;
            double score = table ? 0.6 : 1.5 / (1 + dictionary++);
            Set<String> g = Meanings.gloss(s);
            for (String w : around) {
                if (g.contains(w)) {
                    score += 1;
                }
            }
            String sub = subjectOf(s);
            if (about != null && sub != null) {
                score += sub.equals(about) ? 5 : -1;
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

    // ------------------------------------------------------------------ tagging

    /** Each word of a sentence with its kind and, where it matters, its subject. {@code context} may be null. */
    public List<Tag> tag(String sentence, String context) {
        List<Tag> out = new ArrayList<Tag>();
        for (PartsOfSpeech.Word w : PartsOfSpeech.tag(sentence, null)) {
            if (w.tag == PartsOfSpeech.Tag.PUNCTUATION) {
                continue;
            }
            char kind = kind(w.tag);
            String text = w.text.replaceAll("^[\"'“‘(]+|[\"'”’),.!?;:]+$", "");
            if (text.isEmpty()) {
                continue;
            }
            MeaningIndex.Sense s = null;
            String sub = null;
            if ("nvar".indexOf(kind) >= 0 && ambiguous(text)) {
                s = meant(text, sentence, context, pos(kind));
                sub = s == null ? null : subjectOf(s);
            }
            out.add(new Tag(text, kind, s, sub));
        }
        return out;
    }

    /**
     * A sentence's code: its subject in brackets, then each word as kind.word, with @subject on the ones that need it.
     * "You need root access on the Linux server" → {@code [computing] p.you v.need n.root@computing n.access i.on d.the
     * n.linux n.server}.
     */
    public String code(String sentence, String context) {
        String about = subject(sentence);
        StringBuilder b = new StringBuilder("[").append(about == null ? (context == null ? "general" : context) : about).append(']');
        for (Tag t : tag(sentence, context)) {
            b.append(' ').append(t);
        }
        return b.toString();
    }

    private static char kind(PartsOfSpeech.Tag t) {
        switch (t) {
            case NOUN:
                return 'n';
            case PROPER_NOUN:
                return 'm';
            case PRONOUN:
                return 'p';
            case VERB:
                return 'v';
            case AUXILIARY:
                return 'b';
            case ADJECTIVE:
                return 'a';
            case ADVERB:
                return 'r';
            case PREPOSITION:
                return 'i';
            case CONJUNCTION:
                return 'c';
            case DETERMINER:
                return 'd';
            case INTERJECTION:
                return 'j';
            case NUMERAL:
                return 'u';
            default:
                return 'x';
        }
    }

    /** The dictionary's part of speech for a kind letter, or null. */
    private static String pos(char kind) {
        switch (kind) {
            case 'n':
                return "noun";
            case 'v':
                return "verb";
            case 'a':
                return "adjective";
            case 'r':
                return "adverb";
            default:
                return null;
        }
    }

    /** The subject said in words: "computing" → "in computing". */
    public static String in(String subject) {
        switch (subject) {
            case "maths":
                return "in maths";
            case "animals":
                return "for animals";
            case "botany":
                return "for plants";
            case "sport":
                return "in sport";
            default:
                return "in " + subject;
        }
    }
}
