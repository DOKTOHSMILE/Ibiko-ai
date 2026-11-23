package ai.ibiko.ibiko1;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the conversation is about, so a question can lean on the one before: "Who was Nelson Mandela?" then "When was
 * he born?" (he = Nelson Mandela), "What is the capital of Nigeria?" then "What about Ghana?" (the same question, about
 * Ghana), "Who founded Microsoft?" then "When was the company founded?" (the company = Microsoft), "and his wife?",
 * "tell me more". It keeps the last person (and whether "he" or "she" fits), the last thing or place, the last group,
 * and the last question, for {@link #FRESH_MS}; then a pronoun is left as it is. A message is rewritten into one that
 * stands on its own, and the rewriting is said in the steps. Pure Java.
 */
public final class Context {
    /** How long what was talked about stays the subject. */
    public static final long FRESH_MS = 20 * 60 * 1000L;

    /** What a subject is, so the right pronoun finds it. */
    public enum Kind { MAN, WOMAN, PERSON, GROUP, PLACE, THING }

    /** One thing talked about: its name and kind. */
    public static final class Focus {
        public final String name;
        public final Kind kind;
        final long at;

        Focus(String name, Kind kind, long at) {
            this.name = name;
            this.kind = kind;
            this.at = at;
        }
    }

    /** A message read with the conversation: the message as it stands alone, and what was put in ("he" → name). */
    public static final class Resolved {
        public final String text;
        public final LinkedHashMap<String, String> put = new LinkedHashMap<String, String>();
        /** "what about oil?" while talking about Nigeria: the new thing and the subject it is to be linked to; else null. */
        public String link, linkTo;

        Resolved(String text) {
            this.text = text;
        }

        public boolean changed() {
            return !put.isEmpty();
        }

        /** The step that says it: "“he” is Nelson Mandela". */
        public String said() {
            StringBuilder b = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : put.entrySet()) {
                b.append(b.length() == 0 ? "" : "; ").append("“").append(e.getKey()).append("” is ").append(e.getValue());
            }
            return b.toString();
        }
    }

    /** A message read as it is, with nothing put in. */
    public static Resolved unchanged(String message) {
        return new Resolved(message == null ? "" : message.trim());
    }

    private Focus person, thing, place, group, last;
    private String lastQuestion, lastSubjectWords;
    private long lastAt;

    /** Forgets it all (a new chat). */
    public synchronized void clear() {
        person = thing = place = group = last = null;
        lastQuestion = lastSubjectWords = null;
    }

    /** The current subject, or null. */
    public synchronized Focus focus(long now) {
        return last != null && now - last.at <= FRESH_MS ? last : null;
    }

    /**
     * Remembers what a question was about: {@code subject}, the name its answer was found under (an article's title),
     * {@code words}, the words the question named it with ("Nigeria" in "the capital of Nigeria"), and its kind.
     */
    public synchronized void about(String question, String subject, String words, Kind kind, long now) {
        if (subject == null || subject.trim().isEmpty()) {
            return;
        }
        String name = subject.replaceFirst("\\s*\\([^()]+\\)$", "").trim();
        Focus f = new Focus(name, kind == null ? Kind.THING : kind, now);
        switch (f.kind) {
            case MAN:
            case WOMAN:
            case PERSON:
                person = f;
                break;
            case GROUP:
                group = f;
                break;
            case PLACE:
                place = f;
                thing = f;
                break;
            default:
                thing = f;
                break;
        }
        last = f;
        lastQuestion = question;
        lastSubjectWords = words == null || words.trim().isEmpty() ? name : words.trim();
        lastAt = now;
    }

    private Focus fresh(Focus f, long now) {
        return f != null && now - f.at <= FRESH_MS ? f : null;
    }

    /**
     * Whether two things are of a kind ("Ghana" and "Nigeria": both countries), so "what about Ghana?" asks the last
     * question again about Ghana; when they aren't ("oil" while talking about Nigeria), it asks how the new thing links
     * to the subject. Null (or true) when it can't tell: the last question again, as before.
     */
    public volatile java.util.function.BiPredicate<String, String> peers;

    /** Words that name a part or a property of a thing ("the capital", "the population", "the wife"): of what is talked about. */
    private static final Pattern PART = Pattern.compile("(?i)\\bthe\\s+(capital|population|president|prime minister|leader|currency|"
            + "official language|languages?|area|size|climate|weather|history|economy|flag|anthem|motto|founders?|ceo|owners?|"
            + "headquarters|religion|government|king|queen|ruler|governor|mayor|wife|husband|children|kids|parents|father|mother|"
            + "birthday|age|height|time zone|borders?|neighbou?rs|independence day|nickname|meaning|origin|price|cost|colou?rs?|"
            + "author|director|cast|plot|ending|release date|members|players|coach|stadium|largest city|biggest city|national dish|"
            + "national animal|national sport|main export|exports|imports|gdp|capital city|tallest building|highest point|"
            + "longest river|founding date|first president|current president|name)\\b(?!\\s+(?:of|in|for|at)\\b|'s)");
    private static final Pattern PEOPLE_PART = Pattern.compile("(?i)wife|husband|children|kids|parents|father|mother|birthday|age|height");

    private static final Pattern ABOUT = Pattern.compile("(?i)^\\s*(?:and |so |ok(?:ay)?,? |then )?(?:what about|how about|and what about|what of|same for|same question for|and for|and|also)\\s+(.+?)\\s*[?.!]*$");
    private static final Pattern MORE = Pattern.compile("(?i)^\\s*(?:(?:please )?tell me more|more|go on|continue|say more|anything else|what else|more please|tell me more about (?:him|her|it|them|that))\\s*[?.!]*$");
    private static final Pattern HIS = Pattern.compile("(?i)^\\s*(?:and |so |what about |how about )?(his|her|its|their)\\s+([a-z][a-z ]{1,30}?)\\s*[?.!]*$");

    /** The message as it stands on its own, using what was talked about. */
    public synchronized Resolved resolve(String message, long now) {
        if (message == null) {
            return new Resolved("");
        }
        String t = message.trim();
        Focus who = fresh(person, now), it = fresh(thing, now), there = fresh(place, now), they = fresh(group, now), cur = fresh(last, now);
        if (cur == null) {
            return new Resolved(t);
        }
        // "tell me more"
        if (MORE.matcher(t).matches()) {
            Resolved r = new Resolved("Tell me more about " + cur.name);
            r.put.put(t.replaceAll("[?.!]+$", "").toLowerCase(Locale.ROOT), "more about " + cur.name);
            return r;
        }
        // "and his wife?" → "Who is Nelson Mandela's wife?"
        Matcher h = HIS.matcher(t);
        if (h.matches()) {
            Focus owner = owner(h.group(1), who, it, they, cur);
            if (owner != null) {
                String noun = h.group(2).trim();
                String wh = noun.matches("(wife|husband|spouse|partner|father|mother|son|daughter|brother|sister|boss|leader|founder|"
                        + "president|ceo|coach|manager|owner|author|director|teacher|friend|parents|children|kids|team)s?") ? "Who" : "What";
                String be = noun.matches(".*(children|kids|parents|people)$") || noun.endsWith("s") && !noun.endsWith("ss") ? "are" : "is";
                Resolved r = new Resolved(wh + " " + be + " " + owner.name + "'s " + noun + "?");
                r.put.put(h.group(1).toLowerCase(Locale.ROOT), owner.name);
                return r;
            }
        }
        // "what about Ghana?": the last question again, about Ghana
        Matcher a = ABOUT.matcher(t);
        if (a.matches() && lastQuestion != null && lastSubjectWords != null && now - lastAt <= FRESH_MS) {
            String other = a.group(1).trim();
            java.util.function.BiPredicate<String, String> same = peers;
            if (!other.matches("(?i)(you|me|us|him|her|it|them|that|this|so|then|yes|no|ok|okay)") && other.split("\\s+").length <= 6
                    && same != null && !same.test(other, cur.name)) {
                // "what about oil?" while talking about Nigeria: not Nigeria's question again about oil, but oil in Nigeria
                Resolved r = new Resolved("Tell me about " + other + " in " + cur.name);
                r.put.put(t.replaceAll("[?.!]+$", ""), other + ", with " + cur.name);
                r.link = other;
                r.linkTo = cur.name;
                return r;
            }
            if (!other.matches("(?i)(you|me|us|him|her|it|them|that|this|so|then|yes|no|ok|okay)") && other.split("\\s+").length <= 6
                    && containsWords(lastQuestion, lastSubjectWords)) {
                String again = replaceWords(lastQuestion, lastSubjectWords, other);
                Resolved r = new Resolved(again);
                r.put.put(t.replaceAll("[?.!]+$", ""), again.replaceAll("[?.!]+$", ""));
                return r;
            }
        }
        // pronouns and "the company" in a question
        if (!EncyclopediaQA.question(t) && !t.matches("(?i)^(tell me|explain|describe|show me)\\b.*")) {
            return new Resolved(t);
        }
        Resolved r = new Resolved(t);
        String out = t;
        Focus man = who != null && who.kind != Kind.WOMAN ? who : null, woman = who != null && who.kind != Kind.MAN ? who : null;
        out = swap(out, "\\b(he|him)\\b", man, r, false);
        out = swap(out, "\\b(she)\\b", woman, r, false);
        out = swap(out, "\\b(his)\\b", man, r, true);
        out = swap(out, "\\b(her)\\b(?=\\s+[a-z])", woman, r, true); // "her husband": hers
        out = swap(out, "\\b(her)\\b", woman, r, false);
        out = swap(out, "\\b(they|them)\\b", they != null ? they : who == null && it != null && it.kind == Kind.GROUP ? it : null, r, false);
        out = swap(out, "\\b(their)\\b", they, r, true);
        Focus thingNow = it != null ? it : cur.kind == Kind.THING || cur.kind == Kind.PLACE ? cur : null;
        if (out.matches("(?i).*\\b(what time is it|what day is it|what date is it|is it (true|right|ok|okay|possible|safe|good|bad)|"
                + "how is it going|what is it like|it's (raining|cold|hot|late|early)|is it raining)\\b.*")) {
            thingNow = null; // "it" with no thing behind it
        }
        out = swap(out, "\\b(it)\\b", thingNow, r, false);
        out = swap(out, "\\b(its)\\b", thingNow, r, true);
        out = swap(out, "\\b(there)\\b(?!\\s+(?:is|are|was|were|be)\\b)", there, r, false);
        // "the company", "the country", "the city", "the band", "the film": the last thing, when it is one
        Matcher d = Pattern.compile("(?i)\\bthe (company|firm|country|nation|city|town|state|band|group|team|club|movie|film|book|novel|"
                + "song|album|game|river|mountain|lake|island|place|planet|university|school|party|show|series|man|woman|person|"
                + "player|singer|president|king|queen)\\b").matcher(out);
        if (d.find()) {
            Focus target = d.group(1).matches("(?i)man|person|player|singer|president|king") ? who
                    : d.group(1).matches("(?i)woman|queen") ? woman
                    : d.group(1).matches("(?i)band|group|team|club|party") && they != null ? they : it != null ? it : there;
            if (target != null) {
                r.put.put("the " + d.group(1).toLowerCase(Locale.ROOT), target.name);
                out = out.substring(0, d.start()) + target.name + out.substring(d.end());
            }
        }
        // "what is the capital?", "who is the president?": a part of the thing talked about, when the question names nothing
        // else ("the capital" of Nigeria)
        if (r.put.isEmpty() && !out.substring(1).matches("(?s).*\\b\\p{Lu}.*")) {
            Matcher p = PART.matcher(out);
            if (p.find()) {
                Focus of = PEOPLE_PART.matcher(p.group(1)).matches() ? who : cur.kind == Kind.MAN || cur.kind == Kind.WOMAN ? cur : cur;
                if (of != null) {
                    r.put.put("the " + p.group(1).toLowerCase(Locale.ROOT), "the " + p.group(1).toLowerCase(Locale.ROOT) + " of " + of.name);
                    out = out.substring(0, p.end()) + " of " + of.name + out.substring(p.end());
                }
            }
        }
        return r.put.isEmpty() ? r : withText(r, out);
    }

    private static Resolved withText(Resolved from, String text) {
        Resolved r = new Resolved(text);
        r.put.putAll(from.put);
        r.link = from.link;
        r.linkTo = from.linkTo;
        return r;
    }

    private static Focus owner(String word, Focus who, Focus it, Focus they, Focus cur) {
        switch (word.toLowerCase(Locale.ROOT)) {
            case "his":
                return who != null && who.kind != Kind.WOMAN ? who : null;
            case "her":
                return who != null && who.kind != Kind.MAN ? who : null;
            case "their":
                return they != null ? they : cur;
            default:
                return it != null ? it : cur;
        }
    }

    /** One pronoun put to its name: "he" → "Nelson Mandela", "his" → "Nelson Mandela's". */
    private static String swap(String text, String pronoun, Focus f, Resolved r, boolean owner) {
        if (f == null) {
            return text;
        }
        Matcher m = Pattern.compile("(?i)" + pronoun).matcher(text);
        if (!m.find()) {
            return text;
        }
        String word = m.group(1);
        String name = owner ? f.name + "'s" : f.name;
        r.put.put(word.toLowerCase(Locale.ROOT), f.name);
        return m.replaceAll(Matcher.quoteReplacement(name));
    }

    private static boolean containsWords(String text, String words) {
        return text.toLowerCase(Locale.ROOT).contains(words.toLowerCase(Locale.ROOT));
    }

    private static String replaceWords(String text, String words, String with) {
        int at = text.toLowerCase(Locale.ROOT).indexOf(words.toLowerCase(Locale.ROOT));
        return text.substring(0, at) + with + text.substring(at + words.length());
    }

    /** A subject's kind from its article: a person ("he" or "she" by how the article speaks of them), a place, a group. */
    public static Kind kindOf(Encyclopedia.Article a, java.util.Map<String, String> facts) {
        if (a == null) {
            return Kind.THING;
        }
        String text = " " + a.text.toLowerCase(Locale.ROOT) + " ";
        boolean person = facts != null && (facts.containsKey("born") || facts.containsKey("died") || facts.containsKey("occupation"))
                || text.matches("(?s).*\\((born |c\\. )?\\d{1,2}? ?\\w* ?\\d{3,4} ?[–—-].*") && text.matches("(?s).* (he|she) .*");
        if (person) {
            int he = count(text, " he ") + count(text, " his ") + count(text, " him "), she = count(text, " she ") + count(text, " her ");
            return he > she ? Kind.MAN : she > he ? Kind.WOMAN : Kind.PERSON;
        }
        if (facts != null && (facts.containsKey("population") || facts.containsKey("capital") || facts.containsKey("area")
                || facts.containsKey("elevation")) || text.matches("(?s).* is a (city|town|country|village|state|province|region|"
                + "island|continent|district|county|capital|mountain|river|lake) .*")) {
            return Kind.PLACE;
        }
        if (facts != null && (facts.containsKey("years_active") || facts.containsKey("label") && !facts.containsKey("born"))
                || text.matches("(?s).* (is|was) an? (\\w+ )?(band|group|duo|team|club) .*")) {
            return Kind.GROUP;
        }
        return Kind.THING;
    }

    private static int count(String text, String word) {
        int n = 0;
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            n++;
        }
        return n;
    }
}
