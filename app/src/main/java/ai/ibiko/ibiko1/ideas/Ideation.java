package ai.ibiko.ibiko1.ideas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Probability;

/**
 * Independent thinking about a thing to make: nothing is built in about what it should have. Research is read for the
 * parts its sources share, each part is given a probability of being needed, the parts that go together are found, and
 * new ideas are made by joining related parts no source joined. Pure Java.
 *
 * <pre>
 * I1 parts        each source's headings and list items, and the lists in its sentences ("such as A, B and C"), as
 *                 short parts; the thing's own words ("school", "website") and the web's furniture ("home", "login") left out
 * I2 probability  how likely a part is needed: its sources out of all (a heading counts 1, a mention in a sentence ½),
 *                 Laplace-smoothed, (n + 1) / (N + 2); then moved by evidence (P3): the person named it (sure), or what
 *                 Ibiko knows of the thing mentions it (likelihood ratio 2)
 * I3 together     parts the same sources mention: lift = P(A and B) / (P(A) P(B)); above 1, they go together
 * I4 who          the people the sources say it's for ("parents", "students"), counted the same way
 * I5 new ideas    two parts that go together (lift ≥ 1.2) joined into one that no source has ("admission" + "calendar":
 *                 an admission calendar), or a part made for one of the people it serves; scored p(A) p(B) min(lift, 3) / 3
 * I6 foresight    parts some sources have but most don't yet (p between ¼ and the line): what it could grow into
 * </pre>
 */
public final class Ideation {
    /** One part, with how likely it is needed and why. */
    public static final class Part {
        public final String key;
        /** As shown: "Admissions". */
        public String title;
        public double p;
        int labelled, mentioned;
        public final List<String> why = new ArrayList<String>();

        Part(String key, String title) {
            this.key = key;
            this.title = title;
        }

        public int sources() {
            return Math.max(labelled, mentioned);
        }
    }

    /** A new idea: two parts joined, or a part made for someone, with what it came from. */
    public static final class Idea {
        public final String title, from;
        public final double score;
        public final String a, b;

        Idea(String title, String a, String b, String from, double score) {
            this.title = title;
            this.a = a;
            this.b = b;
            this.from = from;
            this.score = score;
        }
    }

    /** What the thinking came to. */
    public static final class Plan {
        public final int sources;
        public final List<Part> parts = new ArrayList<Part>();
        public final List<Idea> ideas = new ArrayList<Idea>();
        public final List<String> audience = new ArrayList<String>();
        /** I6: what it could grow into. */
        public final List<Part> later = new ArrayList<Part>();

        Plan(int sources) {
            this.sources = sources;
        }
    }

    /** The web's furniture: on every page, part of no thing. */
    private static final Set<String> FURNITURE = new HashSet<String>(Arrays.asList(("home homepage menu main menu login log in sign in sign up register "
            + "search skip to content skip to main content cookie cookies cookie policy privacy privacy policy terms terms of use terms and conditions "
            + "copyright all rights reserved share facebook twitter instagram linkedin youtube tiktok whatsapp pinterest read more learn more "
            + "click here more next previous back top back to top subscribe follow us related posts related articles comments leave a comment "
            + "table of contents conclusion introduction summary faq faqs final thoughts free trial get started book a demo request a demo "
            + "sitemap accessibility language english advertisement sponsored author posted by tags categories category archives "
            + "recent posts popular posts you may also like").split(" (?=[a-z])")));
    private static final Set<String> FURNITURE_PHRASES = new HashSet<String>(Arrays.asList("skip to content", "main menu", "log in", "sign in",
            "sign up", "privacy policy", "cookie policy", "terms of use", "terms and conditions", "all rights reserved", "read more", "learn more",
            "click here", "back to top", "follow us", "related posts", "related articles", "leave a comment", "table of contents", "final thoughts",
            "free trial", "get started", "book a demo", "request a demo", "recent posts", "popular posts", "you may also like", "posted by"));
    /** Words that only frame a part ("the", "our", "page", "section"): taken off its ends. */
    private static final Pattern FRAME_START = Pattern.compile("^(?:the|a|an|our|your|their|its|my|all|latest|recent|upcoming|how to|why|what is|what are|tips for|best|top|"
            + "\\d+|step \\d+|[ivx]+)\\s+");
    private static final Pattern FRAME_END = Pattern.compile("\\s+(?:page|pages|section|sections|tab|area|info|information|details|feature|features|us|now|today|here)$");
    /** "such as A, B and C", "including A, B, and C", "include A, B or C". */
    private static final Pattern LIST = Pattern.compile("(?i)\\b(?:such as|including|includes?|like|e\\.g\\.,?|for example,?|from|featuring|with)\\s+"
            + "((?:[\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,3}\\s*,\\s*){1,8}(?:and|or)\\s+[\\p{L}'-]+(?:\\s+[\\p{L}'-]+){0,3})");

    /**
     * The thinking, from research on a thing ("school" "website").
     *
     * @param subject   what it is about ("school")
     * @param goal      what is being made ("website"), or null
     * @param asked     the person's own words: a part they name is sure
     * @param known     whether what Ibiko knows of the subject mentions a word (its dictionary meanings, its article)
     * @param person    whether a word names a kind of person ("parents"), for I4
     */
    public static Plan think(Corpus c, String subject, String goal, String asked, Predicate<String> known, Predicate<String> person) {
        int n = c.size();
        Plan plan = new Plan(n);
        if (n == 0) {
            return plan;
        }
        Set<String> own = new HashSet<String>();
        for (String w : ((subject == null ? "" : subject) + " " + (goal == null ? "" : goal)).toLowerCase(Locale.ROOT).split("[^\\p{L}]+")) {
            if (!w.isEmpty()) {
                own.add(singular(w));
            }
        }
        own.addAll(Arrays.asList("website", "web", "site", "app", "page"));
        // I1: the parts, by source
        Map<String, Part> parts = new LinkedHashMap<String, Part>();
        List<Set<String>> perSource = new ArrayList<Set<String>>();
        for (Corpus.Source s : c.sources) {
            Set<String> here = new HashSet<String>();
            for (String l : s.labels) {
                // "News and events", "Fees & payments": two parts
                for (String piece : l.split("\\s+(?:and|&)\\s+|\\s*[,/|]\\s*")) {
                    addPart(parts, here, piece, own, true);
                }
            }
            for (String sentence : s.sentences) {
                Matcher m = LIST.matcher(sentence);
                while (m.find()) {
                    for (String item : m.group(1).split("\\s*,\\s*(?:and\\s+|or\\s+)?|\\s+(?:and|or)\\s+")) {
                        addPart(parts, here, item, own, false);
                    }
                }
            }
            perSource.add(here);
        }
        // a part named inside a longer one is the same part ("gallery" and "photo gallery"): the shorter name kept
        Map<String, String> same = new LinkedHashMap<String, String>();
        for (String longer : parts.keySet()) {
            for (String shorter : parts.keySet()) {
                if (!longer.equals(shorter) && (" " + longer + " ").contains(" " + shorter + " ")
                        && (!same.containsKey(longer) || shorter.length() < same.get(longer).length())) {
                    same.put(longer, shorter);
                }
            }
        }
        for (Map.Entry<String, String> e : same.entrySet()) {
            parts.remove(e.getKey());
            for (Set<String> here : perSource) {
                if (here.remove(e.getKey())) {
                    here.add(e.getValue());
                }
            }
        }
        // I1, I2: each part counted in every source that names it anywhere
        List<String> texts = new ArrayList<String>();
        for (Corpus.Source s : c.sources) {
            texts.add(s.all());
        }
        String lowAsked = asked == null ? "" : " " + asked.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}' ]", " ") + " ";
        for (Part p : parts.values()) {
            for (int i = 0; i < n; i++) {
                boolean label = perSource.get(i).contains(p.key);
                boolean said = label || mentions(texts.get(i), p.key);
                if (label) {
                    p.labelled++;
                } else if (said) {
                    p.mentioned++;
                }
            }
            double weight = p.labelled + 0.5 * p.mentioned;
            p.p = (weight + 1) / (n + 2.0);
            p.why.add("in " + (p.labelled + p.mentioned) + " of " + n + " sources");
            if (mentions(lowAsked, p.key)) {
                p.p = 0.99;
                p.why.add("you asked for it");
            } else if (known != null && known.test(p.key)) {
                p.p = Probability.update(p.p, 2.0); // P3: what Ibiko knows of the thing backs it
                p.why.add("what I know of a " + (subject == null ? "thing" : subject) + " mentions it");
            }
        }
        List<Part> ranked = new ArrayList<Part>(parts.values());
        // a part in one source alone is that source's own: not what a thing like this has
        ranked.removeIf(p -> p.p < 0.99 && n >= 3 && p.labelled + p.mentioned < 2);
        Collections.sort(ranked, (a, b) -> Double.compare(b.p, a.p));
        double line = n >= 3 ? 0.35 : 0.3;
        for (Part p : ranked) {
            if (p.p >= line && plan.parts.size() < 10) {
                plan.parts.add(p);
            } else if (p.p >= 0.2 && plan.later.size() < 4 && !plan.parts.contains(p)) {
                plan.later.add(p); // I6
            }
        }
        // I4: who it is for
        if (person != null) {
            Map<String, Integer> who = new LinkedHashMap<String, Integer>();
            Pattern forWho = Pattern.compile("\\b(?:for|helps?|lets?|allows?|by|to|where|so|that)\\s+(?:all\\s+|the\\s+|your\\s+|their\\s+|prospective\\s+|current\\s+|new\\s+)?"
                    + "([a-z]{4,14}s)(?:\\s*,\\s*([a-z]{4,14}s))?(?:,?\\s+(?:and|or)\\s+([a-z]{4,14}s))?\\b");
            for (String t : texts) {
                Set<String> once = new HashSet<String>();
                Matcher m = forWho.matcher(t);
                while (m.find()) {
                    for (int g = 1; g <= 3; g++) {
                        String w = m.group(g);
                        if (w != null && !own.contains(singular(w)) && person.test(singular(w)) && once.add(w)) {
                            who.merge(w, 1, Integer::sum);
                        }
                    }
                }
            }
            List<Map.Entry<String, Integer>> e = new ArrayList<Map.Entry<String, Integer>>(who.entrySet());
            e.sort((a, b) -> b.getValue() - a.getValue());
            for (Map.Entry<String, Integer> x : e) {
                if (plan.audience.size() < 3 && (x.getValue() >= 2 || n < 3)) {
                    plan.audience.add(x.getKey());
                }
            }
        }
        // I3, I5: parts that go together, joined into what no source has
        List<Idea> ideas = new ArrayList<Idea>();
        String everything = String.join(" ", texts);
        List<Part> top = plan.parts.subList(0, Math.min(8, plan.parts.size()));
        for (int i = 0; i < top.size(); i++) {
            for (int j = 0; j < top.size(); j++) {
                Part a = top.get(i), b = top.get(j);
                if (i == j || a.key.contains(b.key) || b.key.contains(a.key) || b.key.contains(" ")) {
                    continue; // b names the kind of thing (one word); a says what it's of
                }
                int both = 0, na = 0, nb = 0;
                for (int s = 0; s < n; s++) {
                    boolean ha = mentions(texts.get(s), a.key), hb = mentions(texts.get(s), b.key);
                    na += ha ? 1 : 0;
                    nb += hb ? 1 : 0;
                    both += ha && hb ? 1 : 0;
                }
                if (na == 0 || nb == 0 || both == 0) {
                    continue;
                }
                double lift = (both / (double) n) / ((na / (double) n) * (nb / (double) n));
                int inSentence = 0; // said in one breath: the strongest sign two parts belong together
                for (Corpus.Source src : c.sources) {
                    for (String sentence : src.sentences) {
                        String low = " " + sentence.toLowerCase(Locale.ROOT) + " ";
                        if (mentions(low, a.key) && mentions(low, b.key)) {
                            inSentence++;
                        }
                    }
                }
                if (inSentence == 0) {
                    continue;
                }
                String joined = a.key + " " + b.key;
                if (lift < 0.8 && n >= 3 || mentions(everything, joined) || mentions(everything, b.key + " of " + a.key)) {
                    continue; // kept apart by the sources, or a source already joined them: not new
                }
                // related: said in one breath (most), and more often together than chance would have it (some)
                double related = 0.5 + 0.4 * Math.min(1, inSentence / 2.0) + 0.1 * Math.min(1, Math.max(0, lift - 1));
                double score = a.p * b.p * related;
                ideas.add(new Idea(a.title + " " + b.key, a.title, b.title, "sources name " + a.title.toLowerCase(Locale.ROOT) + " and " + b.key
                        + " in the same breath (" + inSentence + (inSentence == 1 ? " time" : " times") + "), but none joins them", score));
            }
        }
        for (String w : plan.audience) {
            for (Part a : top.subList(0, Math.min(3, top.size()))) {
                String joined = a.key + " for " + w;
                if (!mentions(everything, joined)) {
                    ideas.add(new Idea(a.title + " for " + w, a.title, w, "it serves " + w + ", and " + a.key + " matters most to them", a.p * 0.5));
                }
            }
        }
        ideas.sort((x, y) -> Double.compare(y.score, x.score));
        Set<String> used = new HashSet<String>();
        for (Idea idea : ideas) {
            // each part in one new idea at most: three different ideas, not one idea three ways
            if (plan.ideas.size() < 3 && used.add(idea.a.toLowerCase(Locale.ROOT)) && used.add(idea.b.toLowerCase(Locale.ROOT))) {
                plan.ideas.add(idea);
            }
        }
        return plan;
    }

    /** A plan read back from what was kept ({@link Research#recall}). */
    static Plan restored(int sources) {
        return new Plan(sources);
    }

    static Part restoredPart(String key, double p, int sources) {
        Part part = new Part(key, cap(key));
        part.p = p;
        part.labelled = sources;
        part.why.add("learned before: in " + sources + " sources");
        return part;
    }

    static Idea restoredIdea(String title, String a, String b, String from) {
        return new Idea(title, a, b, from, 0);
    }

    private static void addPart(Map<String, Part> parts, Set<String> here, String raw, Set<String> own, boolean label) {
        String t = raw.toLowerCase(Locale.ROOT).replaceAll("[“”\"‘’]", "").replaceAll("[^\\p{L}\\s&'-]", " ").replace("&", " and ")
                .replaceAll("\\s+", " ").trim();
        String before;
        do {
            before = t;
            t = FRAME_START.matcher(t).replaceFirst("").trim();
            t = FRAME_END.matcher(t).replaceFirst("").trim();
        } while (!t.equals(before));
        if (t.isEmpty() || FURNITURE_PHRASES.contains(t) || FURNITURE.contains(t)) {
            return;
        }
        List<String> words = new ArrayList<String>();
        for (String w : t.split(" ")) {
            if (!own.contains(singular(w))) {
                words.add(w);
            }
        }
        if (words.isEmpty() || words.size() > 3 || words.get(0).matches("and|or|of|for|to|with|in|on|is|are|be|how|why|what")) {
            return; // the thing's own name ("school website"), or a phrase rather than a part
        }
        String shown = cap(String.join(" ", words)); // as people write it: "Admissions", "Term dates"
        words.set(words.size() - 1, singular(words.get(words.size() - 1)));
        String key = String.join(" ", words);
        if (key.length() < 3 || FURNITURE.contains(key)) {
            return;
        }
        Part p = parts.get(key);
        if (p == null) {
            p = new Part(key, shown);
            parts.put(key, p);
        }
        if (label) {
            here.add(key);
        }
    }

    /** Whether a text names a part, as whole words, one or many ("calendar", "calendars"). */
    static boolean mentions(String text, String key) {
        if (text == null || key.isEmpty()) {
            return false;
        }
        return Pattern.compile("(?<![\\p{L}])" + Pattern.quote(key) + "(?:s|es)?(?![\\p{L}])").matcher(text).find();
    }

    static String singular(String w) {
        if (w.length() <= 3 || w.matches(".*(ss|us|is|ews|ics|ies?s|series|species|news)$")) {
            return w;
        }
        if (w.endsWith("ies")) {
            return w.substring(0, w.length() - 3) + "y";
        }
        if (w.matches(".*(ches|shes|xes|ses)$")) {
            return w.substring(0, w.length() - 2);
        }
        return w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
    }

    static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
