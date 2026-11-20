package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Questions answered exactly from the encyclopedia's fact tables (each article's information box): "When was Nelson
 * Mandela born?", "What is the population of Lagos?", "Who founded Microsoft?", "Who wrote Harry Potter and the
 * Philosopher's Stone?", "How old is Wizkid?". The question's form says which fact it wants and names what it is
 * about (the first group of its form that matched); the article is found (other names followed), its fact read, and the answer said as a sentence. Nothing when
 * the article has no such fact: the question goes on to the article's sentences. Pure Java.
 */
public final class FactsQA {
    /** A found answer: the sentence, the article's title, the relation it read. */
    public static final class Found {
        public final String text, title, relation;

        Found(String text, String title, String relation) {
            this.text = text;
            this.title = title;
            this.relation = relation;
        }
    }

    /** One way of asking: its form (the subject in its first group that matched), the relations it reads, and how the answer is said. */
    private static final class Form {
        final Pattern p;
        final String[] relations;
        final Say say;

        Form(String regex, Say say, String... relations) {
            this.p = Pattern.compile("(?i)^\\s*" + regex + "\\s*[?.!]*\\s*$");
            this.relations = relations;
            this.say = say;
        }
    }

    /** How a fact is said: the article's title, the fact, and all its facts (for "is" or "was", and the year). */
    interface Say {
        String say(String title, String value, LinkedHashMap<String, String> all);
    }

    private static final String S = "(.+?)";
    private static final List<Form> FORMS = new ArrayList<Form>();

    static {
        FORMS.add(new Form("how old (?:is|was) " + S, FactsQA::age, "born"));
        FORMS.add(new Form("(?:when|what year|which year|in what year) (?:was|is) " + S + " born", (t, v, a) -> t + " was born " + onIn(v) + ".", "born"));
        FORMS.add(new Form("(?:what is|what's|when is) " + S + "(?:'s|’s) (?:birthday|date of birth|birth date)", (t, v, a) -> t + " was born " + onIn(v) + ".", "born"));
        FORMS.add(new Form("(?:what is|what's) the (?:birthday|date of birth|birth date) of " + S, (t, v, a) -> t + " was born " + onIn(v) + ".", "born"));
        FORMS.add(new Form("where (?:was|is) " + S + " born", (t, v, a) -> t + " was born in " + v + ".", "birth_place"));
        FORMS.add(new Form("(?:when|what year|which year|in what year) did " + S + " (?:die|pass away|pass)", (t, v, a) -> t + " died " + onIn(v) + ".", "died"));
        FORMS.add(new Form("where did " + S + " (?:die|pass away)", (t, v, a) -> t + " died in " + v + ".", "death_place"));
        FORMS.add(new Form("(?:is " + S + " (?:still )?alive|is " + S + " dead)", FactsQA::alive, "born"));
        FORMS.add(new Form("(?:what is|what's|what was) " + S + "(?:'s|’s) (?:real|full|birth|original) name", (t, v, a) -> t + "'s birth name is " + v + ".", "birth_name"));
        FORMS.add(new Form("(?:what is|what was) the (?:real|full|birth|original) name of " + S, (t, v, a) -> t + "'s birth name is " + v + ".", "birth_name"));
        FORMS.add(new Form("(?:what (?:does|did) " + S + " do(?: for a living)?|what (?:is|was) " + S + "(?:'s|’s) (?:job|occupation|profession|work))",
                (t, v, a) -> t + " " + isWas(a) + " " + withA(lower(v)) + ".", "occupation"));
        FORMS.add(new Form("(?:who (?:is|was) " + S + "(?:'s|’s) (?:wife|husband|spouse|partner)|who (?:is|was|did) " + S + " (?:married to|marry))",
                (t, v, a) -> t + (v.contains(",") ? "'s spouses: " + v : (a.containsKey("died") ? " was married to " : " is married to ") + v) + ".", "spouse"));
        FORMS.add(new Form("(?:who (?:are|were) " + S + "(?:'s|’s) (?:children|kids)|how many (?:children|kids) (?:does|did) " + S + " have)",
                (t, v, a) -> t + "'s children: " + and(v) + ".", "children"));
        FORMS.add(new Form("(?:who (?:are|were) " + S + "(?:'s|’s) parents|who (?:are|were) the parents of " + S + ")", (t, v, a) -> t + "'s parents: " + and(v) + ".", "parents"));
        FORMS.add(new Form("(?:what|which) (?:political )?party (?:is|was|does|did) " + S + " (?:in|belong to|a member of|from|represent)",
                (t, v, a) -> t + (a.containsKey("died") ? " was" : " is") + " a member of the " + v + (v.toLowerCase(Locale.ROOT).contains("party") || v.toLowerCase(Locale.ROOT).contains("congress") ? "" : " party") + ".", "party"));
        FORMS.add(new Form("where did " + S + " (?:study|go to (?:school|university|college)|attend (?:school|university|college)|graduate from)",
                (t, v, a) -> t + " studied at " + v + ".", "education"));
        FORMS.add(new Form("(?:what (?:is|was) " + S + " (?:famous|known) for|why is " + S + " famous)", (t, v, a) -> t + " is known for " + v + ".", "known_for"));
        FORMS.add(new Form("(?:what|which) (?:instruments?) (?:does|did) " + S + " play", (t, v, a) -> t + " plays " + lower(v) + ".", "instrument"));
        FORMS.add(new Form("(?:what|which) (?:team|club) (?:does|did) " + S + " play for", (t, v, a) -> t + " plays for " + v + ".", "team"));
        FORMS.add(new Form("(?:what|which) position (?:does|did) " + S + " play", (t, v, a) -> t + " plays as " + withA(lower(v)) + ".", "position"));
        FORMS.add(new Form("(?:what (?:kind of )?(?:genre|music|type of music) (?:is|does|did) " + S + "(?: play| make| sing)?|what is " + S + "(?:'s|’s) genre)",
                (t, v, a) -> t + (v.contains(",") ? "'s genres are " : "'s genre is ") + lower(v) + ".", "genre"));
        FORMS.add(new Form("(?:what|which) (?:record )?labels? (?:is|was|does|did) " + S + " (?:on|signed to|with|sign with)", (t, v, a) -> t + "'s label: " + v + ".", "label"));
        FORMS.add(new Form("(?:what|which) religion (?:is|was|does|did) " + S + "(?: follow| practise| practice| have)?", (t, v, a) -> t + "'s religion: " + v + ".", "religion"));

        FORMS.add(new Form("(?:what is|what's|which is|name) the capital(?: city)? of " + S, (t, v, a) -> "The capital of " + t + " is " + v + ".", "capital"));
        FORMS.add(new Form("(?:what is|what's) " + S + "(?:'s|’s) capital(?: city)?", (t, v, a) -> "The capital of " + t + " is " + v + ".", "capital"));
        FORMS.add(new Form("(?:what is|what's|which is) the (?:largest|biggest) city (?:in|of) " + S, (t, v, a) -> "The largest city in " + t + " is " + v + ".", "largest_city"));
        FORMS.add(new Form("(?:what is|what's) the population of " + S, FactsQA::population, "population"));
        FORMS.add(new Form("how many people (?:live|are|lived) (?:in|on) " + S, FactsQA::population, "population"));
        FORMS.add(new Form("(?:what is|what's) " + S + "(?:'s|’s) population", FactsQA::population, "population"));
        FORMS.add(new Form("(?:how big is|what is the (?:area|size|land area|total area) of|how large is) " + S, (t, v, a) -> t + " covers " + v + ".", "area"));
        FORMS.add(new Form("(?:how (?:high|tall) is|what is the (?:height|elevation) of) " + S, (t, v, a) -> t + " is " + v + " high.", "elevation", "height"));
        FORMS.add(new Form("(?:how long is|what is the length of) " + S, (t, v, a) -> t + " is " + v + " long.", "length_km", "runtime"));
        FORMS.add(new Form("who (?:is|was|are|were) the (?<role>president|prime minister|leader|mayor|governor|king|queen|head|ruler|chief|oba|emir|monarch|premier|chancellor|head of state|ceo|chairman) of " + S,
                FactsQA::leader, "leader", "key_people"));
        FORMS.add(new Form("who (?:leads|rules|governs|runs) " + S, FactsQA::leader, "leader"));
        FORMS.add(new Form("(?:what (?:is|was) the currency (?:of|in|used in)|what currency (?:is used|do they use) in|what money (?:is used|do they use) in) " + S,
                (t, v, a) -> "The currency of " + t + " is the " + v + ".", "currency"));
        FORMS.add(new Form("what currency does " + S + " use", (t, v, a) -> "The currency of " + t + " is the " + v + ".", "currency"));
        FORMS.add(new Form("(?:what (?:is|are) the (?:official )?languages? (?:of|in|spoken in)|what languages? (?:is|are) spoken in|what languages? do (?:they|people) speak in) " + S,
                (t, v, a) -> (v.contains(",") ? "The languages of " + t + " are " : "The language of " + t + " is ") + v + ".", "language"));
        FORMS.add(new Form("what languages? (?:is|are) spoken (?:in|on) " + S, (t, v, a) -> "The language of " + t + ": " + v + ".", "language"));
        FORMS.add(new Form("(?:what (?:kind|type) of government does " + S + " have|what is the government of " + S + ")", (t, v, a) -> t + " is " + withA(lower(v)) + ".", "government"));
        FORMS.add(new Form("(?:what (?:are|do you call) (?:the )?people (?:from|of) " + S + " called|what do you call (?:a person|someone|people) from " + S + "|what is the demonym (?:of|for) " + S + ")",
                (t, v, a) -> "People from " + t + " are called " + plural(v) + ".", "demonym"));
        FORMS.add(new Form("(?:what is|what's) the (?:national )?motto of " + S, (t, v, a) -> "The motto of " + t + " is " + quoted(v) + ".", "motto"));
        FORMS.add(new Form("(?:what is|what's) the (?:national )?anthem of " + S, (t, v, a) -> "The anthem of " + t + " is " + quoted(v) + ".", "anthem"));
        FORMS.add(new Form("(?:what|which) time ?zone is " + S + " in", (t, v, a) -> t + " is in the " + v + " time zone.", "time_zone"));

        FORMS.add(new Form("who (?:founded|started|created|established|set up|formed) " + S, (t, v, a) -> t + " was founded by " + and(v) + ".", "founder"));
        FORMS.add(new Form("who (?:is|was|are|were) the founders? of " + S, (t, v, a) -> t + " was founded by " + and(v) + ".", "founder"));
        FORMS.add(new Form("(?:when|what year|which year|in what year) (?:was|were) " + S + " (?:founded|established|started|formed|built|created|set up|opened)",
                (t, v, a) -> t + " was founded " + onIn(v) + ".", "founded"));
        FORMS.add(new Form("where (?:is|are) " + S + " (?:headquartered|based)|where is the headquarters of " + S, (t, v, a) -> t + " is headquartered in " + v + ".", "headquarters", "location"));
        FORMS.add(new Form("how many (?:employees|workers|staff|people work(?:ing)? (?:at|for)) (?:does|do|did)? ?" + S + "(?: have| employ)?", (t, v, a) -> t + " has " + v + " employees.", "employees"));
        FORMS.add(new Form("what (?:does|did) " + S + " (?:make|sell|produce)", (t, v, a) -> t + "'s products include " + v + ".", "products"));
        FORMS.add(new Form("what industry is " + S + " in", (t, v, a) -> t + " works in " + lower(v) + ".", "industry"));

        FORMS.add(new Form("(?:who (?:wrote|authored|is the author of|was the author of|penned)) " + S, (t, v, a) -> t + " was written by " + and(v) + ".", "author", "writer"));
        FORMS.add(new Form("who directed " + S + "|who (?:is|was) the director of " + S, (t, v, a) -> t + " was directed by " + and(v) + ".", "director"));
        FORMS.add(new Form("who produced " + S + "|who (?:is|was) the producer of " + S, (t, v, a) -> t + " was produced by " + and(v) + ".", "producer"));
        FORMS.add(new Form("who (?:starred|stars|acted|plays|played) in " + S + "|who (?:is|was) in the cast of " + S, (t, v, a) -> t + " stars " + and(v) + ".", "starring"));
        FORMS.add(new Form("who (?:composed|wrote) the music (?:for|of) " + S, (t, v, a) -> "The music for " + t + " is by " + and(v) + ".", "music"));
        FORMS.add(new Form("(?:when|what year|which year) (?:was|did) " + S + " (?:released|come out|published|first aired|air)", (t, v, a) -> t + " was released " + onIn(v) + ".", "released"));
        FORMS.add(new Form("who (?:developed|made|created) (?:the (?:game|app|software) )?" + S, (t, v, a) -> t + " was developed by " + and(v) + ".", "developer"));
        FORMS.add(new Form("who published " + S + "|who (?:is|was) the publisher of " + S, (t, v, a) -> t + " was published by " + and(v) + ".", "publisher"));
        FORMS.add(new Form("who (?:sang|sings|performed|recorded) " + S, (t, v, a) -> t + " is by " + and(v) + ".", "artist"));
        FORMS.add(new Form("(?:what was the budget (?:of|for)|how much did it cost to make) " + S, (t, v, a) -> t + "'s budget was " + v + ".", "budget"));
        FORMS.add(new Form("how much (?:money )?did " + S + " (?:make|earn|gross)", (t, v, a) -> t + " made " + v + ".", "gross"));
        FORMS.add(new Form("(?:what is|what's) the scientific name (?:of|for) (?:a |an |the )?" + S, (t, v, a) -> "The scientific name of " + t + " is " + v + ".", "scientific_name"));
        FORMS.add(new Form("where does (?:the )?" + S + " (?:flow into|end|empty into)", (t, v, a) -> t + " flows into " + v + ".", "mouth"));
        FORMS.add(new Form("where does (?:the )?" + S + " (?:start|begin|rise)", (t, v, a) -> t + " starts at " + v + ".", "source"));
        FORMS.add(new Form("(?:what|which) country is " + S + " in", (t, v, a) -> t + " is in " + v + ".", "country"));
    }

    private final Encyclopedia enc;

    public FactsQA(Encyclopedia enc) {
        this.enc = enc;
    }

    /** The answer to {@code q} from the fact tables, or null. */
    public Found answer(String q) {
        if (q == null) {
            return null;
        }
        String t = q.trim().replaceAll("[’`]", "'")
                .replaceFirst("(?i)^(please\\s+)?(can|could|would|will) you (please )?(tell me|say|let me know|find out)\\s+", "")
                .replaceFirst("(?i)^(please\\s+)?(do|did) you (happen to )?know\\s+", "")
                .replaceFirst("(?i)^(i (want|need|would like) to know|tell me)\\s+", "");
        for (Form f : FORMS) {
            Matcher m = f.p.matcher(t);
            if (!m.matches()) {
                continue;
            }
            String said = subjectOf(m);
            if (said == null) {
                continue;
            }
            Encyclopedia.Article a = article(said);
            if (a == null) {
                continue;
            }
            LinkedHashMap<String, String> all = enc.facts(a.title);
            for (String rel : f.relations) {
                String v = all.get(rel);
                v = v == null ? null : clean(rel, v);
                if (v == null || v.isEmpty()) {
                    continue;
                }
                if (rel.equals("leader") || rel.equals("key_people")) {
                    String role = role(m);
                    v = role + "\u0001" + v; // the role asked for travels with the value to the sentence
                }
                String text = f.say.say(a.title, v, all);
                if (text != null) {
                    return new Found(tidy(text), a.title, rel);
                }
            }
        }
        return null;
    }

    private static String subjectOf(Matcher m) {
        // a form with two ways of naming the subject has it in whichever matched
        for (int g = 1; g <= m.groupCount(); g++) {
            String s = m.group(g);
            if (s != null && !s.trim().isEmpty() && !isRole(m, g)) {
                return s.trim().replaceFirst("(?i)^(the|a|an)\\s+(?=\\S)", "")
                        .replaceAll("\\s+(?i)(now|today|currently|at the moment|right now)$", "").trim();
            }
        }
        return null;
    }

    private static boolean isRole(Matcher m, int g) {
        try {
            return m.start("role") == m.start(g) && m.end("role") == m.end(g);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String role(Matcher m) {
        try {
            return m.group("role") == null ? "" : m.group("role");
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** The article a name in a question means: as written, then the article the words name best. */
    Encyclopedia.Article article(String said) {
        Encyclopedia.Article a = enc.find(said);
        if (a == null && !said.equals(said.toLowerCase(Locale.ROOT))) {
            a = enc.find(said.toLowerCase(Locale.ROOT));
        }
        if (a == null) {
            List<Object[]> subs = new EncyclopediaQA(enc).subjects(said, 1);
            a = subs.isEmpty() ? null : (Encyclopedia.Article) subs.get(0)[0];
        }
        return a;
    }

    // ---- how facts are said ----

    private static final Pattern FULL_DATE = Pattern.compile("^(\\d{1,2}) (January|February|March|April|May|June|July|August|September|October|November|December) (-?\\d{1,4})\\b.*");
    private static final Pattern MONTH_YEAR = Pattern.compile("^(January|February|March|April|May|June|July|August|September|October|November|December) (\\d{3,4})\\b.*");
    private static final Pattern YEAR = Pattern.compile("\\b(\\d{3,4})\\b");

    /** "on 18 July 1918", "in 1975", "in the 15th century". */
    static String onIn(String v) {
        if (FULL_DATE.matcher(v).matches() || v.matches("^(January|February|March|April|May|June|July|August|September|October|November|December) \\d{1,2},? \\d{3,4}.*")) {
            return "on " + v;
        }
        if (v.matches("^\\d{1,2}(st|nd|rd|th) (century|millennium)\\b.*")) {
            return "in the " + v;
        }
        return "in " + v;
    }

    private static String isWas(LinkedHashMap<String, String> all) {
        return all.containsKey("died") ? "was" : "is";
    }

    static String lower(String v) {
        // lower case the common words of a list ("Singer, Songwriter"), never a name ("Afrobeats" stays as it is when
        // a capital is inside it, as "R&B")
        StringBuilder b = new StringBuilder();
        for (String part : v.split(",\\s*")) {
            String p = part.trim();
            if (p.matches("[A-Z][a-z]+( [a-z]+)*")) {
                p = p.toLowerCase(Locale.ROOT);
            }
            b.append(b.length() == 0 ? "" : ", ").append(p);
        }
        return and(b.toString());
    }

    /** "a, b, c" → "a, b and c". */
    static String and(String list) {
        int at = list.lastIndexOf(", ");
        return at < 0 || list.contains(" and ") ? list : list.substring(0, at) + " and " + list.substring(at + 2);
    }

    /** "a singer" / "an activist" / a list kept as it is. */
    static String withA(String v) {
        if (v.isEmpty()) {
            return v;
        }
        String first = v.split("[ ,]")[0].toLowerCase(Locale.ROOT);
        if (first.matches("the|a|an")) {
            return v;
        }
        return (first.matches("^[aeiou].*") && !first.startsWith("uni") && !first.startsWith("eu") ? "an " : "a ") + v;
    }

    static String plural(String demonym) {
        String d = demonym.split(",")[0].trim();
        return d.matches(".*(ese|ish|ch|sh|s|i|x)$") ? "the " + d : d + "s";
    }

    static String quoted(String v) {
        String s = v.replaceAll("^[\"“]+|[\"”]+$", "");
        return "“" + s + "”";
    }

    static String population(String t, String v, LinkedHashMap<String, String> all) {
        String year = all.get("population_year");
        String y = year == null ? "" : year.replaceAll("[()]", "").trim();
        return t + " has a population of " + v.replaceAll("\\s*\\(.*$", "") + (y.isEmpty() ? "" : " (" + y + ")") + ".";
    }

    private static String leader(String t, String v, LinkedHashMap<String, String> all) {
        int cut = v.indexOf('\u0001');
        String asked = cut < 0 ? "" : v.substring(0, cut);
        String who = cut < 0 ? v : v.substring(cut + 1);
        String title = all.get("leader_title");
        if (title == null || title.isEmpty()) {
            title = asked.isEmpty() ? "leader" : asked;
        }
        if (all.containsKey("leader") && who.equals(all.get("leader"))) {
            return "The " + lowerTitle(title) + " of " + t + " is " + who + ", as my encyclopedia last recorded.";
        }
        return "Key people at " + t + ": " + who + ".";
    }

    private static String lowerTitle(String title) {
        return title.matches("[A-Z][a-z]+( [A-Za-z]+)*") && !title.matches("(Oba|Emir|Obi|Ooni|Alaafin|Sultan)\\b.*")
                ? title.toLowerCase(Locale.ROOT) : title;
    }

    /** "Nelson Mandela was born on 18 July 1918 and died on 5 December 2013, at 95." */
    private static String age(String t, String born, LinkedHashMap<String, String> all) {
        java.time.LocalDate b = date(born);
        String died = all.get("died");
        if (b == null) {
            return null;
        }
        if (died != null) {
            java.time.LocalDate d = date(died);
            if (d == null) {
                return null;
            }
            int years = years(b, d, born, died);
            return t + " was born " + onIn(born) + " and died " + onIn(died) + ", at the age of " + years + ".";
        }
        java.time.LocalDate now = java.time.LocalDate.now();
        int years = years(b, now, born, null);
        if (years > 120) {
            return null; // no death date given for someone who must have died: not an age
        }
        return t + " was born " + onIn(born) + ", so " + (FULL_DATE.matcher(born).matches() ? "" : "about ") + years + " years old today.";
    }

    private static String alive(String t, String born, LinkedHashMap<String, String> all) {
        String died = all.get("died");
        if (died != null) {
            return "No: " + t + " died " + onIn(died) + ".";
        }
        java.time.LocalDate b = date(born);
        if (b == null || years(b, java.time.LocalDate.now(), born, null) > 110) {
            return null;
        }
        return "As far as my encyclopedia knows, yes: it gives no date of death for " + t + ", who was born " + onIn(born) + ".";
    }

    private static int years(java.time.LocalDate from, java.time.LocalDate to, String a, String b) {
        boolean exact = FULL_DATE.matcher(a).matches() && (b == null || FULL_DATE.matcher(b).matches());
        return exact ? java.time.Period.between(from, to).getYears() : to.getYear() - from.getYear();
    }

    private static final String[] MONTHS = {"January", "February", "March", "April", "May", "June", "July", "August",
            "September", "October", "November", "December"};

    static java.time.LocalDate date(String v) {
        Matcher m = FULL_DATE.matcher(v);
        try {
            if (m.matches()) {
                return java.time.LocalDate.of(Integer.parseInt(m.group(3)), month(m.group(2)), Integer.parseInt(m.group(1)));
            }
            m = MONTH_YEAR.matcher(v);
            if (m.matches()) {
                return java.time.LocalDate.of(Integer.parseInt(m.group(2)), month(m.group(1)), 1);
            }
            m = YEAR.matcher(v);
            if (m.find()) {
                return java.time.LocalDate.of(Integer.parseInt(m.group(1)), 1, 1);
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    private static int month(String name) {
        for (int i = 0; i < MONTHS.length; i++) {
            if (MONTHS[i].equals(name)) {
                return i + 1;
            }
        }
        return 1;
    }

    /** A box value as said: no "List of …" links in a list, no stray comma before a bracket, a party without its dates. */
    static String clean(String rel, String v) {
        StringBuilder b = new StringBuilder();
        for (String part : v.split(",\\s+(?![^()]*\\))")) {
            String p = part.trim();
            if (p.isEmpty() || p.startsWith("List of") || p.startsWith("(") && b.length() > 0 && rel.equals("party")) {
                continue;
            }
            b.append(b.length() == 0 ? "" : ", ").append(p);
        }
        String out = b.toString().replaceAll(",\\s*\\(", " (");
        if (rel.equals("party")) {
            out = out.replaceAll("\\s*\\([^)]*\\)", "").trim();
        }
        return out;
    }

    private static String tidy(String s) {
        return s.replaceAll("\\.\\.$", ".").replaceAll("\\s+([,.])", "$1").replaceAll("\\s{2,}", " ").trim();
    }
}
