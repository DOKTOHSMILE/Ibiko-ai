package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Happenings;

/**
 * Answers worked out by putting facts together, not looked up whole: no fact says how old Chinua Achebe was when
 * Nigeria became independent, but one says when he was born and another when Nigeria became independent, and the
 * difference is the answer. The operations: an age at an event (with the exact dates when both are known), who is
 * older or what came first, the years between two things, whether someone was alive at the time, two mountains,
 * rivers or cities compared, the longest, highest or biggest in a place, and a chain ("the inventor of the telephone"
 * read as the person, then asked about). Every answer says the facts it rests on, so the working can be checked; a
 * question it can't put together is left to the rest of Ibiko (null). The dates of events are not listed anywhere:
 * they are read from the encyclopedia by the understanding formula for events ({@link Happenings}), the article
 * about the thing asked, when it is asked. Pure Java over {@link BuiltIn} and what it reads.
 */
final class Composer {

    /** A date (from the understanding formula for events). */
    static Happenings.When date(String v) {
        return Happenings.date(v);
    }

    /** Whole years from {@code a} to {@code b}. */
    static int years(Happenings.When a, Happenings.When b) {
        return a.yearsTo(b);
    }

    private final BuiltIn k;
    /** Where events are read from: the encyclopedia's article on a thing (null: only the facts' own dates). */
    private final java.util.function.Function<String, Encyclopedia.Article> articles;
    /** What was read from each article (by title), kept: an article is read once. */
    private final Map<String, List<Happenings.Happening>> read = new HashMap<String, List<Happenings.Happening>>();
    /** People's last names (lower case) → the people (keys) with a birth date, so "Achebe" finds Chinua Achebe. */
    private Map<String, List<String>> surnames;

    /** What it has read and kept about a thing (its memory of events), or null for none. */
    private final java.util.function.Function<String, List<Happenings.Happening>> memory;
    /** What it reads is kept: an article read for an answer is learned from (its facts and events go into memory). */
    private final java.util.function.Consumer<Encyclopedia.Article> keep;

    Composer(BuiltIn k, java.util.function.Function<String, Encyclopedia.Article> articles) {
        this(k, articles, null, null);
    }

    Composer(BuiltIn k, java.util.function.Function<String, Encyclopedia.Article> articles,
            java.util.function.Function<String, List<Happenings.Happening>> memory, java.util.function.Consumer<Encyclopedia.Article> keep) {
        this.k = k;
        this.articles = articles;
        this.memory = memory;
        this.keep = keep;
    }

    /** A phrase as events are matched: plain, hyphens as spaces ("COVID-19" is "covid 19"). */
    private static String flat(String s) {
        return BuiltIn.plainQuestion(s).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    /** What the articles say happened to a thing, read once and kept (in memory too, when there is one). */
    private synchronized List<Happenings.Happening> happenings(Encyclopedia.Article a) {
        List<Happenings.Happening> h = read.get(a.title);
        if (h == null) {
            h = Happenings.read(a.text, a.title);
            read.put(a.title, h);
            if (keep != null) {
                try {
                    keep.accept(a); // read once: learned for good, its facts and events in memory
                } catch (RuntimeException ignored) {
                    // answered all the same; learned next time
                }
            }
        }
        return h;
    }

    /**
     * The date of an event named in a phrase ("Nigeria became independent", "the Berlin Wall fell", "World War II
     * ended"), understood from reading, not looked up in a list: the thing it is about is found as an article (the
     * longest run of the phrase's words that is one), the article is read for what happened when ({@link Happenings}),
     * and the happening whose words share the most meaning with the phrase's (the same stems, or a dictionary synonym)
     * is the one meant. Null when nothing read dates it.
     */
    Dated event(String phrase) {
        if (articles == null && memory == null) {
            return null;
        }
        String p = flat(phrase).replaceFirst("^(the|a|an)\\s+", "");
        String[] w = p.split(" ");
        // first what it already knows: events it read or was told before, kept in its memory
        if (memory != null) {
            for (int len = Math.min(5, w.length); len >= 1; len--) {
                for (int i = 0; i + len <= w.length; i++) {
                    String about = String.join(" ", Arrays.asList(w).subList(i, i + len));
                    if (about.length() < 3 || STOPWORDS.contains(about)) {
                        continue;
                    }
                    List<Happenings.Happening> known = memory.apply(about);
                    if (known == null || known.isEmpty()) {
                        continue;
                    }
                    String rest = (String.join(" ", Arrays.asList(w).subList(0, i)) + " " + String.join(" ", Arrays.asList(w).subList(i + len, w.length))).trim();
                    this.phrase = typed(flat(phrase));
                    Dated d = best(known, rest, about, about);
                    if (d != null) {
                        return new Dated(d.when, d.clause, d.read, true);
                    }
                }
            }
        }
        if (articles == null) {
            return null;
        }
        for (int len = Math.min(5, w.length); len >= 1; len--) {
            for (int i = 0; i + len <= w.length; i++) {
                String title = String.join(" ", Arrays.asList(w).subList(i, i + len));
                if (title.length() < 3 || STOPWORDS.contains(title)) {
                    continue;
                }
                Encyclopedia.Article a = articles.apply(title);
                if (a == null || !flat(a.title).equals(title)) {
                    continue; // only an article that is exactly the thing named
                }
                String rest = (String.join(" ", Arrays.asList(w).subList(0, i)) + " " + String.join(" ", Arrays.asList(w).subList(i + len, w.length))).trim();
                this.phrase = typed(said(flat(phrase), a));
                // the thing named has its own article: what it says decides, a shorter title inside it is not tried
                return best(a, rest, title);
            }
        }
        return null;
    }

    /**
     * What event words mean, in families: an ending ("fell", "came down", "was taken down", "collapsed") or a beginning
     * ("began", "was founded", "was built", "opened"). Knowledge of the language, not of any event: it lets "the wall
     * fell" find "the wall was taken down".
     */
    static final String[][] FAMILIES = {
        {"end", "ended", "fell", "fall", "fallen", "collapsed", "collapse", "down", "demolished", "destroyed", "dissolved",
            "abolished", "finished", "over", "closed", "sank", "sunk", "surrendered", "defeated"},
        {"began", "begin", "begun", "start", "started", "founded", "built", "opened", "created", "established", "formed",
            "launched", "broke out", "inaugurated"},
        {"independence", "independent", "freedom", "free"},
        {"landed", "landing", "land", "touched down"},
        {"president", "elected", "sworn", "inaugurated", "became the first president"}};

    /** The words of a family a word belongs to, as stems (empty when it is in none). */
    static java.util.Set<String> family(String word) {
        java.util.Set<String> out = new java.util.HashSet<String>();
        String g = Happenings.gist(word).isEmpty() ? "" : Happenings.gist(word).iterator().next();
        for (String[] f : FAMILIES) {
            boolean in = false;
            for (String x : f) {
                in |= !g.isEmpty() && Happenings.gist(x).contains(g);
            }
            if (in) {
                for (String x : f) {
                    out.addAll(Happenings.gist(x));
                }
            }
        }
        return out;
    }

    private static final java.util.Set<String> STOPWORDS = new java.util.HashSet<String>(Arrays.asList("the", "was", "were", "when",
            "first", "became", "began", "ended", "fell", "did", "and", "its", "his", "her"));

    /** Of what an article says happened, the one the asked words mean; null when none shares a word's meaning. */
    private Dated best(Encyclopedia.Article a, String asked, String title) {
        return best(happenings(a), asked, title, a.title);
    }

    /** Of what happened (read from an article, or remembered), the one the asked words mean; null when none fits. */
    private Dated best(List<Happenings.Happening> all, String asked, String title, String about) {
        java.util.Set<String> want = Happenings.gist(asked);
        java.util.Set<String> wanted = new java.util.HashSet<String>(want);
        ai.ibiko.ibiko1.words.Meanings m = ai.ibiko.ibiko1.words.Meanings.shared();
        if (m != null) {
            for (String word : asked.split(" ")) {
                if (word.length() < 3) {
                    continue;
                }
                for (String syn : m.synonyms(word, null)) {
                    wanted.addAll(Happenings.gist(syn)); // "fell": come down, go down… ("taken down")
                }
            }
        }
        for (String word : asked.split(" ")) {
            wanted.addAll(family(word)); // an ending, a beginning, independence, a landing
        }
        if (want.isEmpty()) {
            wanted.addAll(Happenings.gist(title)); // "the moon landing": the happening that names it
        }
        Happenings.Happening pick = null;
        double bestScore = 0;
        for (Happenings.Happening h : all) {
            java.util.Set<String> has = Happenings.gist(h.action + " " + (want.isEmpty() ? h.sentence : ""));
            double score = 0;
            for (String g : wanted) {
                score += has.contains(g) ? (want.contains(g) ? 3 : 1) : 0;
            }
            if (score > 0 && flat(h.subject).equals(flat(about))) {
                score += 0.5; // about the thing itself, not something in its article: only to break a tie
            }
            if (score > 0 && h.when.full()) {
                score += 0.25; // a full date over a bare year, all else equal
            }
            if (score > bestScore) {
                bestScore = score;
                pick = h;
            }
        }
        if (pick == null) {
            return null;
        }
        return new Dated(pick.when, phrase, pick.sentence);
    }

    /** The phrase being dated, as asked, with the article's own spelling of the thing ("the berlin wall fell" → "the Berlin Wall fell"). */
    private String phrase = "";

    private static String said(String asked, Encyclopedia.Article a) {
        String p = flat(asked);
        String t = flat(a.title);
        int at = (" " + p + " ").indexOf(" " + t + " ");
        return at < 0 ? a.title + " " + p : (p.substring(0, at) + a.title + p.substring(at + t.length())).trim();
    }

    /**
     * Something that happened to a person ("he became President of South Africa"), read from the person's own article:
     * the pronoun is the person.
     */
    Dated personEvent(String who, String what) {
        return personEvent(who, what, what);
    }

    /** As above, said back as the question put it ({@code asWritten}: "he became President of South Africa"). */
    Dated personEvent(String who, String what, String asWritten) {
        if (articles == null) {
            return null;
        }
        Encyclopedia.Article a = articles.apply(k.name(who));
        if (a == null || !flat(a.title).equals(flat(k.name(who)))) {
            return null;
        }
        phrase = typed(asWritten); // as the person put it: "he became President of South Africa"
        return best(a, flat(what), flat(a.title));
    }

    // ------------------------------------------------------------------ the things named

    /** A thing's key as it is in the facts, trying the ways names are shortened ("the Niger" for the Niger River). */
    String resolve(String phrase, BuiltIn.About needs) {
        String p = BuiltIn.plainQuestion(phrase).replaceFirst("^(the|a|an)\\s+", "").trim();
        if (p.isEmpty()) {
            return null;
        }
        String[] tries = {p, p + " river", "river " + p, "mount " + p, p + " mountain", "lake " + p, p + " city", p + " state"};
        for (String t : tries) {
            if (needs == null ? !k.get(t, BuiltIn.About.COUNTRY).isEmpty() || k.keys().contains(t) : !k.get(t, needs).isEmpty()) {
                return t;
            }
        }
        return null;
    }

    /** The person a phrase names (full name, or a last name only one person with a birth date has); or null. */
    String person(String phrase) {
        String p = BuiltIn.plainQuestion(phrase).replaceFirst("^(the|a|an)\\s+", "").trim();
        for (String key : k.mentions(phrase)) {
            if (!k.get(key, BuiltIn.About.BORN).isEmpty()) {
                return key;
            }
        }
        if (!k.get(p, BuiltIn.About.BORN).isEmpty()) {
            return p;
        }
        String[] words = p.split(" ");
        String last = words[words.length - 1];
        List<String> same = surnames().get(last);
        if (same == null) {
            return null;
        }
        if (words.length > 1) {
            for (String key : same) {
                if (key.startsWith(words[0] + " ")) {
                    return key;
                }
            }
            return null;
        }
        // a last name alone: the one of that name the facts say most about (Chinua Achebe, not a namesake), when clearly so
        String best = null;
        int most = -1, next = -1;
        for (String key : same) {
            int n = 0;
            for (BuiltIn.About a : new BuiltIn.About[]{BuiltIn.About.BORN, BuiltIn.About.DIED, BuiltIn.About.OCCUPATION,
                BuiltIn.About.NATIONALITY, BuiltIn.About.INVENTED}) {
                n += k.get(key, a).size();
            }
            if (n > most) {
                next = most;
                most = n;
                best = key;
            } else if (n > next) {
                next = n;
            }
        }
        return most > next ? best : null;
    }

    private synchronized Map<String, List<String>> surnames() {
        if (surnames == null) {
            surnames = new HashMap<String, List<String>>();
            for (String key : k.keys()) {
                if (!k.get(key, BuiltIn.About.BORN).isEmpty() && key.contains(" ")) {
                    String last = key.substring(key.lastIndexOf(' ') + 1);
                    surnames.computeIfAbsent(last, x -> new ArrayList<String>()).add(key);
                }
            }
        }
        return surnames;
    }

    /** What a phrase dates: an event, a person's birth (or death, when it says so), an invention, or a state's creation. */
    static final class Dated {
        final Happenings.When when;
        /** What happened, as a clause ("Nigeria became independent", "Chinua Achebe was born"). */
        final String clause;
        /** The sentence it was read from, when it was read (null for a fact's own date). */
        final String read;
        /** From its memory (read or told before), not read just now. */
        final boolean remembered;

        Dated(Happenings.When when, String clause) {
            this(when, clause, null);
        }

        Dated(Happenings.When when, String clause, String read) {
            this(when, clause, read, false);
        }

        Dated(Happenings.When when, String clause, String read, boolean remembered) {
            this.when = when;
            this.clause = clause;
            this.read = read;
            this.remembered = remembered;
        }
    }

    Dated dated(String phrase) {
        String p = BuiltIn.plainQuestion(phrase);
        boolean died = p.matches(".*\\b(died|death|passed away)\\b.*");
        String who = person(p.replaceAll("\\b(was born|born|died|death|passed away|birth)\\b", " "));
        if (who != null) {
            List<String> v = k.get(who, died ? BuiltIn.About.DIED : BuiltIn.About.BORN);
            Happenings.When w = v.isEmpty() ? null : date(v.get(0));
            if (w != null) {
                return new Dated(w, k.name(who) + (died ? " died" : " was born"));
            }
        }
        for (String key : k.mentions(p)) {
            List<String> inv = k.get(key, BuiltIn.About.INVENTED);
            if (!inv.isEmpty() && date(inv.get(0)) != null) {
                return new Dated(date(inv.get(0)), "the " + k.name(key) + " was invented");
            }
            List<String> made = k.get(key, BuiltIn.About.CREATED);
            if (!made.isEmpty() && date(made.get(0)) != null) {
                return new Dated(date(made.get(0)), k.name(key) + " was created");
            }
        }
        // an invention by its short name ("the light bulb" for the incandescent light bulb)
        String core = p.replaceAll("\\b(the|a|an|was|were|invented|discovered|made|first)\\b", " ").replaceAll("\\s+", " ").trim();
        if (core.length() >= 3) {
            for (String key : inventions()) {
                if (key.endsWith(" " + core)) {
                    Happenings.When w = date(k.get(key, BuiltIn.About.INVENTED).get(0));
                    if (w != null) {
                        return new Dated(w, "the " + k.name(key) + " was invented");
                    }
                }
            }
        }
        // anything else that happened: understood from reading about it
        return event(phrase);
    }

    private List<String> inventions;

    private synchronized List<String> inventions() {
        if (inventions == null) {
            inventions = new ArrayList<String>();
            for (String key : k.keys()) {
                if (!k.get(key, BuiltIn.About.INVENTED).isEmpty()) {
                    inventions.add(key);
                }
            }
        }
        return inventions;
    }

    // ------------------------------------------------------------------ answering

    private static final Pattern AGE_WHEN = Pattern.compile("^(?:how old (?:was|were|is) (.+?) when (.+)|what age was (.+?) when (.+)"
            + "|(.+?) was how old when (.+))$");
    private static final Pattern AGE_DIED = Pattern.compile("^(?:at what age did (.+?) (?:die|pass away)|how old (?:was|were) (.+?) when"
            + " (?:he|she|they) (?:died|passed away)|how old did (.+?) live to(?: be)?|how long did (.+?) live)$");
    private static final Pattern OLDER = Pattern.compile("^(?:who (?:is|was) (older|younger|born first|born earlier|born later|the oldest|"
            + "the youngest)|which (?:came|was|happened) (first|earlier|later)|what (?:came|happened) (first|earlier))\\b(.*)$");
    private static final Pattern BETWEEN = Pattern.compile("^how many years (?:were there |passed |are there |went by )?(?:between|from)"
            + " (.+?) (?:and|to|until|till) (.+)$");
    private static final Pattern AFTER = Pattern.compile("^how (?:long|many years) (after|before) (.+?) (?:was|were|did|is) (.+)$");
    private static final Pattern ALIVE = Pattern.compile("^(?:was|were|is) (.+?) (?:still )?(alive|born|living) (?:when|at the time) (.+)$");
    private static final Pattern THE_MOST = Pattern.compile("^(?:what|which|name)? ?(?:is |are |was )?(?:the )?(longest|highest|tallest"
            + "|biggest|largest|most populous) (river|mountain|peak|city|town|lake)s? (?:in|of) (.+)$");
    private static final Pattern INVENTOR_OF = Pattern.compile("\\bthe (inventor|discoverer|creator) of (?:the )?([a-z0-9 -]+?)(?= (?:born"
            + "|die|died|from|when|have|come|was|is|live)\\b|$)");

    /** The answer worked out from what it knows, with its steps; null when this isn't a question it can put together. */
    /** The question as typed, for saying its own words back with their capitals. */
    private String asked = "";

    /** The words of a plain phrase as the question typed them ("the berlin wall" → "the Berlin Wall"); else as given. */
    String typed(String plain) {
        String[] w = plain.trim().split("\\s+");
        if (w.length == 0 || asked.isEmpty()) {
            return plain;
        }
        StringBuilder re = new StringBuilder("(?i)");
        for (int i = 0; i < w.length; i++) {
            re.append(i == 0 ? "" : "[\\s\\p{P}]+").append(Pattern.quote(w[i]));
        }
        Matcher m = Pattern.compile(re.toString()).matcher(asked);
        return m.find() ? m.group() : plain;
    }

    Reasoner.Result answer(String question) {
        if (question == null) {
            return null;
        }
        asked = question;
        String q = BuiltIn.plainQuestion(question);
        List<String> steps = new ArrayList<String>();
        // a chain: "the inventor of the telephone" is a person, then the question goes on about them
        Matcher chain = INVENTOR_OF.matcher(q);
        if (chain.find()) {
            String thing = resolveThing(chain.group(2));
            String who = thing == null ? null : inventorWithDates(thing);
            if (who != null) {
                String rest = q.substring(0, chain.start()) + who + q.substring(chain.end());
                rest = rest.replaceAll("\\b(invented|discovered|created) it\\b", "$1 the " + thing);
                steps.add("The " + k.name(thing) + "'s " + chain.group(1) + ": " + k.name(who));
                Reasoner.Result r = answerPlain(rest, steps);
                String lead = k.name(who) + " is credited with the " + k.name(thing) + ". ";
                if (r != null) {
                    return new Reasoner.Result(lead + r.text, r.steps);
                }
                String fact = k.answer(rest);
                if (fact != null) {
                    steps.add("Then: " + fact);
                    return new Reasoner.Result(lead + fact, steps);
                }
            }
        }
        return answerPlain(q, steps);
    }

    private String resolveThing(String phrase) {
        String p = BuiltIn.plainQuestion(phrase).trim();
        if (!k.get(p, BuiltIn.About.INVENTOR).isEmpty()) {
            return p;
        }
        for (String key : k.mentions(p)) {
            if (!k.get(key, BuiltIn.About.INVENTOR).isEmpty()) {
                return key;
            }
        }
        return null;
    }

    /** Of a thing's credited inventors, the first whose birth date is known (else the first). */
    private String inventorWithDates(String thing) {
        List<String> who = k.get(thing, BuiltIn.About.INVENTOR);
        for (String w : who) {
            String key = BuiltIn.plainQuestion(w);
            if (!k.get(key, BuiltIn.About.BORN).isEmpty()) {
                return key;
            }
        }
        return who.isEmpty() ? null : BuiltIn.plainQuestion(who.get(0));
    }

    private Reasoner.Result answerPlain(String q, List<String> steps) {
        Matcher m;
        if ((m = AGE_DIED.matcher(q)).matches()) {
            String who = person(first(m));
            return who == null ? null : ageAtDeath(who, steps);
        }
        if ((m = AGE_WHEN.matcher(q)).matches()) {
            String p = m.group(1) != null ? m.group(1) : m.group(3) != null ? m.group(3) : m.group(5);
            String e = m.group(2) != null ? m.group(2) : m.group(4) != null ? m.group(4) : m.group(6);
            String who = person(p);
            if (who == null) {
                return null;
            }
            if (e.matches("^(he|she|they|" + Pattern.quote(who) + "|" + Pattern.quote(lastWord(who)) + ") (died|passed away)$")) {
                return ageAtDeath(who, steps);
            }
            Matcher made = Pattern.compile("^(?:he|she|they|" + Pattern.quote(lastWord(who)) + ") (invented|discovered|created|made) (?:the )?(.+)$")
                    .matcher(e);
            Matcher self = Pattern.compile("^(?:he|she|they|" + Pattern.quote(lastWord(who)) + ") (.+)$").matcher(e);
            Dated at = made.matches() ? dated(made.group(2)) : self.matches() ? personEvent(who, self.group(1), e) : dated(e);
            return at == null ? null : ageAt(who, at, steps);
        }
        if ((m = ALIVE.matcher(q)).matches()) {
            String who = person(m.group(1));
            Dated at = dated(m.group(3));
            return who == null || at == null ? null : alive(who, at, steps);
        }
        if ((m = BETWEEN.matcher(q)).matches()) {
            Dated a = dated(m.group(1)), b = dated(m.group(2));
            return a == null || b == null ? null : between(a, b, steps);
        }
        if ((m = AFTER.matcher(q)).matches()) {
            Dated a = dated(m.group(2)), b = dated(pastTense(m.group(3)));
            return a == null || b == null ? null : between(a, b, steps);
        }
        if ((m = OLDER.matcher(q)).find()) {
            return order(q, m, steps);
        }
        if ((m = THE_MOST.matcher(q)).matches()) {
            return most(m.group(1), m.group(2), m.group(3), steps);
        }
        return compare(q, steps);
    }

    /** "did nigeria become independent" → "nigeria became independent": the verb after "did" said as it happened. */
    static String pastTense(String clause) {
        String[] w = clause.split(" ");
        for (int i = 0; i < w.length; i++) {
            if (w[i].matches("become|gain|get|end|begin|start|fall|win|join|leave|land|sink|die|take|come|go|happen|open|close|sign")) {
                w[i] = ai.ibiko.ibiko1.words.Morph.past(w[i]);
                return String.join(" ", w);
            }
        }
        return clause;
    }

    private static String first(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return m.group(i);
            }
        }
        return "";
    }

    private static String lastWord(String key) {
        return key.substring(key.lastIndexOf(' ') + 1);
    }

    private Happenings.When born(String who) {
        List<String> v = k.get(who, BuiltIn.About.BORN);
        return v.isEmpty() ? null : date(v.get(0));
    }

    private Happenings.When died(String who) {
        List<String> v = k.get(who, BuiltIn.About.DIED);
        return v.isEmpty() ? null : date(v.get(0));
    }

    private static String about(Happenings.When a, Happenings.When b) {
        return a.full() && b.full() ? "" : "about ";
    }

    private Reasoner.Result ageAtDeath(String who, List<String> steps) {
        Happenings.When b = born(who), d = died(who);
        String name = k.name(who);
        if (b == null) {
            return null;
        }
        steps.add(name + " was born " + b.on());
        if (d == null) {
            return new Reasoner.Result("As far as I know, " + name + " is alive: born " + b.on() + ", with no date of death in what I have.", steps);
        }
        steps.add(name + " died " + d.on());
        int n = years(b, d);
        steps.add("From one date to the other: " + n + " years");
        return new Reasoner.Result(name + " was " + about(b, d) + n + " at death: born " + b.on() + ", died " + d.on() + ".", steps);
    }

    /** Where a date came from, for the steps: memory, or the article just read (which is now in memory). */
    private static String source(Dated d) {
        return d.read == null ? "" : d.remembered ? " (remembered: I read it before)" : " (read just now in the encyclopedia, and kept)";
    }

    private Reasoner.Result ageAt(String who, Dated at, List<String> steps) {
        Happenings.When b = born(who), d = died(who);
        if (b == null) {
            return null;
        }
        String name = k.name(who);
        steps.add(name + " was born " + b.on());
        steps.add(cap(at.clause) + " " + at.when.on() + source(at));
        if (at.when.before(b) || at.when.y < b.y) {
            int n = years(at.when, b);
            return new Reasoner.Result(name + " wasn't born yet when " + at.clause + ": that was " + at.when.on() + ", and " + name
                    + " was born " + about(at.when, b) + n + (n == 1 ? " year" : " years") + " later, " + b.on() + ".", steps);
        }
        if (d != null && d.before(at.when)) {
            steps.add(name + " died " + d.on());
            return new Reasoner.Result(name + " had died by then: " + name + " died " + d.on() + ", and " + at.clause + " " + at.when.on()
                    + ".", steps);
        }
        int n = years(b, at.when);
        steps.add("From the birth to that day: " + n + " years");
        return new Reasoner.Result(name + " was " + about(b, at.when) + n + " when " + at.clause + ": born " + b.on() + ", and "
                + at.clause + " " + at.when.on() + ".", steps);
    }

    private Reasoner.Result alive(String who, Dated at, List<String> steps) {
        Happenings.When b = born(who), d = died(who);
        if (b == null) {
            return null;
        }
        String name = k.name(who);
        steps.add(name + ": born " + b.on() + (d == null ? "" : ", died " + d.on()));
        steps.add(cap(at.clause) + " " + at.when.on());
        if (at.when.before(b) || at.when.y < b.y) {
            return new Reasoner.Result("No: " + name + " wasn't born yet. " + cap(at.clause) + " " + at.when.on() + "; " + name
                    + " was born " + b.on() + ".", steps);
        }
        if (d != null && d.before(at.when)) {
            return new Reasoner.Result("No: " + name + " had died by then (" + d.on() + "); " + at.clause + " " + at.when.on() + ".", steps);
        }
        return new Reasoner.Result("Yes: " + name + " was " + about(b, at.when) + years(b, at.when) + " when " + at.clause + " ("
                + at.when.on() + ").", steps);
    }

    private Reasoner.Result between(Dated a, Dated b, List<String> steps) {
        steps.add(cap(a.clause) + " " + a.when.on());
        steps.add(cap(b.clause) + " " + b.when.on());
        Dated early = b.when.before(a.when) || b.when.y < a.when.y ? b : a, late = early == a ? b : a;
        int n = years(early.when, late.when);
        steps.add("The difference: " + n + " years");
        return new Reasoner.Result(cap(about(early.when, late.when)) + n + (n == 1 ? " year" : " years") + ": " + early.clause + " "
                + early.when.on() + ", and " + late.clause + " " + late.when.on() + ".", steps);
    }

    /** "Who is older, Achebe or Soyinka?", "which came first, the telephone or the radio?" */
    private Reasoner.Result order(String q, Matcher m, List<String> steps) {
        String rest = m.group(4) == null ? "" : m.group(4);
        String[] two = rest.replaceFirst("^\\s*(of|between)?\\s*", "").split("\\s+(?:or|and)\\s+|\\s*,\\s*");
        if (two.length < 2) {
            return null;
        }
        List<Dated> found = new ArrayList<Dated>();
        for (String part : two) {
            if (part.trim().isEmpty()) {
                continue;
            }
            Dated d = dated(part);
            if (d == null) {
                return null;
            }
            found.add(d);
        }
        if (found.size() < 2) {
            return null;
        }
        String word = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        boolean wantLater = word.matches("younger|born later|later|the youngest");
        Dated pick = found.get(0);
        for (Dated d : found) {
            steps.add(cap(d.clause) + " " + d.when.on());
            boolean earlier = d.when.before(pick.when) || d.when.y < pick.when.y;
            boolean later = pick.when.before(d.when) || pick.when.y < d.when.y;
            if (wantLater ? later : earlier) {
                pick = d;
            }
        }
        Dated other = found.get(0) == pick ? found.get(1) : found.get(0);
        if (pick.when.y == other.when.y && !(pick.when.before(other.when) || other.when.before(pick.when))) {
            return new Reasoner.Result("I can't tell them apart: both " + (pick.when.full() ? "" : "in ") + pick.when.y + " in what I have ("
                    + pick.clause + " " + pick.when.on() + ", " + other.clause + " " + other.when.on() + ").", steps);
        }
        String who = pick.clause.replaceFirst(" (was born|died|was invented|was created|became independent|began|ended)$", "");
        int n = Math.abs(years(wantLater ? other.when : pick.when, wantLater ? pick.when : other.when));
        String gap = n == 0 ? "less than a year apart" : about(pick.when, other.when) + n + (n == 1 ? " year" : " years") + " apart";
        boolean people = pick.clause.endsWith(" was born") && other.clause.endsWith(" was born");
        String lead = who.isEmpty() ? who : Character.toUpperCase(who.charAt(0)) + who.substring(1);
        String verdict = people ? lead + " is " + (wantLater ? "younger" : "older") : lead + " came " + (wantLater ? "later" : "first");
        return new Reasoner.Result(verdict + ": " + pick.clause + " " + pick.when.on() + ", " + other.clause + " " + other.when.on() + " ("
                + gap + ").", steps);
    }

    private static final String[][] MEASURES = {
        {"higher|taller|height|elevation|high|tall", "elevation", "high"},
        {"longer|length|long", "length", "long"},
        {"bigger|larger|more populous|more people|population|populated|big|large", "population", "people"}};

    /** Two mountains, rivers or cities compared ("which is longer, the Niger or the Nile?"); null for anything else. */
    private Reasoner.Result compare(String q, List<String> steps) {
        String measure = null, unitWord = null;
        BuiltIn.About about = null;
        for (String[] ms : MEASURES) {
            if (q.matches(".*\\b(" + ms[0] + ")\\b.*")) {
                about = BuiltIn.About.named(ms[1]);
                measure = ms[1];
                unitWord = ms[2];
                break;
            }
        }
        if (about == null || !q.matches(".*\\b(which|what|is|are|compare|than|or|versus|vs)\\b.*")) {
            return null;
        }
        String body = q.replaceFirst("^(which|what) (is|was|are) (the )?(higher|taller|longer|bigger|larger|more populous|longest|highest)\\s*", "")
                .replaceFirst("^(is|was|are) ", "").replaceFirst("^how (much|many times) (higher|taller|longer|bigger|larger) (is|was) ", "");
        String[] two = body.split("\\s+(?:or|than|and|versus|vs)\\s+|\\s*,\\s*");
        if (two.length != 2) {
            return null;
        }
        String a = resolve(two[0].replaceAll("\\b(higher|taller|longer|bigger|larger|more populous)\\b", " "), about);
        String b = resolve(two[1], about);
        if (a == null || b == null || a.equals(b)) {
            return null;
        }
        if (!k.get(a, BuiltIn.About.CAPITAL).isEmpty() && !k.get(b, BuiltIn.About.CAPITAL).isEmpty()) {
            return null; // two countries: the reasoner over countries answers it
        }
        double va = BuiltIn.number(k.get(a, about).get(0)), vb = BuiltIn.number(k.get(b, about).get(0));
        if (Double.isNaN(va) || Double.isNaN(vb)) {
            return null;
        }
        String sa = k.get(a, about).get(0), sb = k.get(b, about).get(0);
        steps.add(k.name(a) + ": " + sa);
        steps.add(k.name(b) + ": " + sb);
        String big = va >= vb ? a : b, small = big.equals(a) ? b : a;
        double hi = Math.max(va, vb), lo = Math.min(va, vb);
        String unit = "population".equals(measure) ? "" : sa.replaceAll("^[-\\d,.\\s]+", "").trim();
        String diff = String.format(Locale.US, "%,.0f", hi - lo) + (unit.isEmpty() ? (measure.equals("population") ? " people" : "") : " " + unit);
        String word = "elevation".equals(measure) ? "higher" : "length".equals(measure) ? "longer" : "bigger";
        String times = lo > 0 && hi / lo >= 1.5 ? String.format(Locale.US, ", about %.1f times as %s", hi / lo, "population".equals(measure)
                ? "many people" : unitWord) : "";
        steps.add("The difference: " + diff);
        return new Reasoner.Result(k.name(big) + " is " + word + ": " + k.name(big) + " " + (va >= vb ? sa : sb) + ", " + k.name(small) + " "
                + (va >= vb ? sb : sa) + " (" + diff + " more" + times + ").", steps);
    }

    /** "The longest river in Nigeria", "the highest mountain in Tanzania", "the biggest city in Kano State". */
    private Reasoner.Result most(String word, String kind, String place, List<String> steps) {
        BuiltIn.About about = word.matches("longest") ? BuiltIn.About.LENGTH : word.matches("highest|tallest") ? BuiltIn.About.ELEVATION
                : BuiltIn.About.POPULATION;
        if ("lake".equals(kind) && about == BuiltIn.About.POPULATION) {
            about = BuiltIn.About.AREA;
        }
        String where = BuiltIn.plainQuestion(place).replaceFirst("^the ", "");
        List<Object[]> hits = new ArrayList<Object[]>();
        for (String key : k.keys()) {
            List<String> v = k.get(key, about);
            if (v.isEmpty()) {
                continue;
            }
            if ("river".equals(kind) && !key.contains("river") && k.get(key, BuiltIn.About.LENGTH).isEmpty()) {
                continue;
            }
            if (("city".equals(kind) || "town".equals(kind)) && (!k.get(key, BuiltIn.About.CAPITAL).isEmpty()
                    || !k.get(key, BuiltIn.About.LGA_COUNT).isEmpty())) {
                continue; // a country or a state is not a city
            }
            if ("lake".equals(kind) && !key.contains("lake")) {
                continue;
            }
            boolean in = false;
            for (String c : k.get(key, BuiltIn.About.COUNTRY)) {
                in |= BuiltIn.plainQuestion(c).equals(where);
            }
            for (String s : k.get(key, BuiltIn.About.STATE)) {
                in |= BuiltIn.plainQuestion(s).equals(where) || BuiltIn.plainQuestion(s).equals(where + " state");
            }
            double n = BuiltIn.number(v.get(0));
            if (in && !Double.isNaN(n)) {
                hits.add(new Object[]{n, key, v.get(0)});
            }
        }
        if (hits.isEmpty()) {
            return null;
        }
        hits.sort((x, y) -> Double.compare((Double) y[0], (Double) x[0]));
        steps.add("Looking through every " + kind + " in " + cap(where) + " I know: " + hits.size());
        steps.add("Ranked by " + about.name + ", the largest first");
        StringBuilder s = new StringBuilder("The " + word + " " + kind + " in " + cap(where) + " that I know of is " + k.name((String) hits.get(0)[1])
                + " (" + hits.get(0)[2] + ").");
        if (hits.size() > 1) {
            s.append(" Next: ");
            for (int i = 1; i < Math.min(3, hits.size()); i++) {
                s.append(i == 1 ? "" : ", ").append(k.name((String) hits.get(i)[1])).append(" (").append(hits.get(i)[2]).append(')');
            }
            s.append('.');
        }
        return new Reasoner.Result(s.toString(), steps);
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        String[] w = s.split(" ");
        StringBuilder out = new StringBuilder();
        for (String x : w) {
            out.append(out.length() == 0 ? "" : " ").append(x.isEmpty() ? x : Character.toUpperCase(x.charAt(0)) + x.substring(1));
        }
        return s.equals(s.toLowerCase(Locale.US)) && s.split(" ").length <= 3 ? out.toString()
                : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
