package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Grammar;

/**
 * Who did what to whom: the roles in a sentence. A question is read for its subject (the doer), its action (the verb,
 * as its base form) and its object (what the action is done to), and which of them it asks for: "Who invented the
 * telephone?" asks for the subject of "invent" with the object "the telephone"; "What did Thomas Edison invent?" asks
 * for the object; "Who was the Mona Lisa painted by?" asks for the subject of a passive. The answer is then read from
 * the encyclopedia's sentences the same way, active ("Bell invented the telephone") or passive ("painted in oil by
 * Leonardo da Vinci", "a novel written by Chinua Achebe"), and said as a sentence with the roles in their places.
 * Nothing is said unless a sentence has the same action and the same object (or subject). Pure Java.
 */
public final class Roles {
    /** Which role a question asks for. */
    public enum Asks { SUBJECT, OBJECT, NONE }

    /** A sentence read for its roles. */
    public static final class Reading {
        public final String subject, verb, object;
        public final Asks asks;
        public final boolean passive;

        Reading(String subject, String verb, String object, Asks asks, boolean passive) {
            this.subject = subject == null ? "" : subject.trim();
            this.verb = verb == null ? "" : verb.trim();
            this.object = object == null ? "" : object.trim();
            this.asks = asks;
            this.passive = passive;
        }

        /** The reading as said in the steps: "subject ? · action invent · object the telephone". */
        public String said() {
            return "subject " + (asks == Asks.SUBJECT ? "?" : quote(subject)) + " · action " + quote(verb) + " · object "
                    + (asks == Asks.OBJECT ? "?" : quote(object)) + (passive ? " (said the other way round)" : "");
        }

        private static String quote(String s) {
            return s.isEmpty() ? "—" : "“" + s + "”";
        }
    }

    /** A found answer: the sentence, and the article it was read in. */
    public static final class Found {
        public final String text, title;

        Found(String text, String title) {
            this.text = text;
            this.title = title;
        }
    }

    private static final String WH = "(who|what|which (?:\\w+ )?(?:person|people|man|woman|company|country|team|scientist|artist|band|writer))";
    private static final String NOT_VERB = "is|are|was|were|am|be|do|does|did|has|have|had|can|could|will|would|should|may|might|must|"
            + "the|a|an|of|in|on|for|to|about|kind|type|time|year|day|country|city|colour|color";
    private static final Pattern ASK_SUBJECT = Pattern.compile("(?i)^" + WH + " (?!(?:" + NOT_VERB + ")\\b)(\\w+) (.+)$");
    private static final Pattern ASK_SUBJECT_PASSIVE = Pattern.compile("(?i)^(?:who|what|which \\w+) (?:was|were|is|are|has been|had been) (.+) (\\w+) by$");
    private static final Pattern ASK_SUBJECT_BY = Pattern.compile("(?i)^by whom (?:was|were|is) (.+) (\\w+)$");
    private static final Pattern ASK_OBJECT = Pattern.compile("(?i)^(?:what|which (?:\\w+ )?\\w+|who|whom) (?:did|does|do|has|have|had) (.+) (\\w+)$");
    private static final Pattern ASK_OBJECT_PASSIVE = Pattern.compile("(?i)^(?:what|which \\w+) (?:was|were|has been|have been) (\\w+) by (.+)$");

    /** What a question asks for, read for its roles; null when it isn't a who-did-what question. */
    public static Reading question(String q) {
        if (q == null) {
            return null;
        }
        String t = q.trim().replaceAll("[’`]", "'").replaceAll("[?!.]+$", "").replaceAll("\\s+", " ")
                .replaceFirst("(?i)^(please\\s+)?((can|could) you (tell me|say)|do you know|tell me)\\s+", "");
        Matcher m = ASK_SUBJECT_PASSIVE.matcher(t);
        if (m.matches() && verbish(m.group(2))) {
            return new Reading("", Grammar.base(m.group(2)), m.group(1), Asks.SUBJECT, true);
        }
        m = ASK_SUBJECT_BY.matcher(t);
        if (m.matches() && verbish(m.group(2))) {
            return new Reading("", Grammar.base(m.group(2)), m.group(1), Asks.SUBJECT, true);
        }
        m = ASK_OBJECT_PASSIVE.matcher(t);
        if (m.matches() && verbish(m.group(1))) {
            return new Reading(m.group(2), Grammar.base(m.group(1)), "", Asks.OBJECT, true);
        }
        m = ASK_OBJECT.matcher(t);
        if (m.matches() && !m.group(2).matches("(?i)(mean|think|say|like|want|know|do|go|come|feel|look|happen)")) {
            String subject = m.group(1);
            if (subject.matches("(?i)(you|i|we|they|it|he|she|this|that)")) {
                return null; // about the people talking, or a word Context hasn't put a name to
            }
            return new Reading(subject, Grammar.base(m.group(2)), "", Asks.OBJECT, false);
        }
        m = ASK_SUBJECT.matcher(t);
        if (m.matches() && verbish(m.group(2)) && !m.group(3).matches("(?i)^(you|me|us|i)\\b.*")) {
            return new Reading("", Grammar.base(m.group(2)), m.group(3), Asks.SUBJECT, false);
        }
        return null;
    }

    /** A word that reads as a past action ("invented", "wrote", "founded"): only then is it a who-did-what question. */
    static boolean verbish(String w) {
        String v = w.toLowerCase(Locale.ROOT);
        return v.matches("\\w{3,}ed") || IRREGULAR_PAST.contains(v) || v.matches("\\w{3,}(s|es)") && Grammar.forms(Grammar.base(v)).contains(v)
                && !v.matches(".*(ss|us|is|ics)$");
    }

    private static final java.util.Set<String> IRREGULAR_PAST = new java.util.HashSet<String>(java.util.Arrays.asList(("wrote written "
            + "built made led won began begun found bought sold taught drew drawn sang sung gave given took taken ran became came saw "
            + "seen knew known told held brought fought spoke spoken chose chosen flew grew grown left met paid rose set shot sent "
            + "invented composed painted directed discovered founded designed developed created produced published sculpted "
            + "killed defeated conquered married").split(" ")));

    // ------------------------------------------------------------------ answers

    private final Encyclopedia enc;

    public Roles(Encyclopedia enc) {
        this.enc = enc;
    }

    /** The answer to a who-did-what question from the encyclopedia's sentences, or null. */
    public Found answer(String q) {
        Reading r = question(q);
        if (r == null || r.verb.isEmpty() || r.asks == Asks.NONE) {
            return null;
        }
        Found f = answer(r, r.verb);
        if (f == null) {
            // an article may say it with a near word ("developed" for "invented", "started" for "founded")
            for (String near : NEAR.getOrDefault(r.verb, new String[0])) {
                f = answer(r, near);
                if (f != null) {
                    break;
                }
            }
        }
        return f;
    }

    /** Verbs an article may use for the one asked about. */
    private static final java.util.Map<String, String[]> NEAR = new java.util.HashMap<String, String[]>();

    static {
        NEAR.put("invent", new String[]{"develop", "create", "design", "build", "make"});
        NEAR.put("found", new String[]{"start", "establish", "create", "form"});
        NEAR.put("discover", new String[]{"find", "identify"});
        NEAR.put("write", new String[]{"author", "compose"});
        NEAR.put("create", new String[]{"make", "design", "develop", "invent"});
        NEAR.put("build", new String[]{"construct", "make"});
        NEAR.put("make", new String[]{"create", "produce", "build"});
        NEAR.put("start", new String[]{"found", "establish", "begin"});
        NEAR.put("compose", new String[]{"write"});
        NEAR.put("lead", new String[]{"head", "command", "rule"});
    }

    private Found answer(Reading r, String verb) {
        Set<String> forms = Grammar.forms(verb);
        if (r.asks == Asks.SUBJECT) {
            Encyclopedia.Article a = article(r.object);
            if (a == null) {
                return null;
            }
            String agent = agentIn(a.text, forms, a.title, r.object);
            if (agent == null) {
                return null;
            }
            String participle = participle(a.text, forms, verb);
            String thing = thing(r.object, a.title);
            return new Found(thing + " " + (plural(thing) ? "were" : "was") + " " + participle + " by " + agent + ".", a.title);
        }
        Encyclopedia.Article a = article(r.subject);
        if (a == null) {
            return null;
        }
        String[] done = objectIn(a.text, forms, a.title);
        if (done == null) {
            return null;
        }
        return new Found(a.title.replaceFirst("\\s*\\([^()]+\\)$", "") + " " + done[0] + " " + done[1] + ".", a.title);
    }

    private Encyclopedia.Article article(String name) {
        String n = name.replaceFirst("(?i)^(the|a|an)\\s+", "").trim();
        Encyclopedia.Article a = enc.find(n);
        if (a == null) {
            a = enc.find(name);
        }
        return a;
    }

    /** "the telephone" as the answer begins: the question's words, the article's capitals for a name. */
    private static String thing(String asked, String title) {
        String t = asked.trim();
        String bare = t.replaceFirst("(?i)^(the|a|an)\\s+", "");
        if (bare.equalsIgnoreCase(title.replaceFirst("\\s*\\([^()]+\\)$", "")) && !title.equals(title.toLowerCase(Locale.ROOT))
                && Character.isUpperCase(title.charAt(0)) && !bare.equals(bare.toLowerCase(Locale.ROOT))) {
            t = (t.length() > bare.length() ? t.substring(0, t.length() - bare.length()) : "") + title.replaceFirst("\\s*\\([^()]+\\)$", "");
        }
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    private static boolean plural(String thing) {
        String t = thing.toLowerCase(Locale.ROOT).replaceFirst("^(the|a|an) ", "");
        return t.endsWith("s") && !t.endsWith("ss") && !t.matches(".*(news|is|us|physics|mathematics)$") && !t.contains(" ");
    }

    private static final String NAME = "((?:the )?[A-Z][\\p{L}.'’-]*(?:,? (?:[A-Z][\\p{L}.'’-]*|(?:da|de|del|della|der|di|du|van|von|la|le|bin|ibn|al|and|&)\\b)){0,7})";

    /** Sentences of a text. */
    private static List<String> sentences(String text) {
        List<String> out = new ArrayList<String>();
        for (String s : text.split("(?<=[.!?])\\s+(?=[A-Z\"“(])")) {
            out.add(s.trim());
        }
        return out;
    }

    /** Who did it, in a sentence of the object's own article: "painted … by Leonardo da Vinci", "Bell invented it". */
    static String agentIn(String text, Set<String> forms, String title, String object) {
        for (String s : sentences(text)) {
            for (String f : forms) {
                // passive: "<form> (up to four words) by NAME"
                Matcher m = Pattern.compile("\\b" + Pattern.quote(f) + "\\b(?:\\s+[\\p{L},'-]+){0,4}?\\s+by\\s+" + NAME).matcher(s);
                if (m.find() && f.matches(".*(ed|en|n|t|wn|ng)$|made|built|written|drawn|sung|led|won|found|held|taught|bought|sold|given|taken|seen|known")) {
                    String who = tidyName(m.group(1));
                    if (who != null) {
                        return who;
                    }
                }
            }
        }
        // active: "NAME <form> … <object>" in the same sentence
        String[] want = object.toLowerCase(Locale.ROOT).replaceFirst("^(the|a|an) ", "").split("\\s+");
        for (String s : sentences(text)) {
            String low = s.toLowerCase(Locale.ROOT).replace(" ", "");
            boolean has = true;
            for (String w : want) {
                has &= low.contains(w);
            }
            if (!has) {
                continue;
            }
            for (String f : forms) {
                Matcher m = Pattern.compile(NAME + "\\s+(?:also\\s+|first\\s+)?" + Pattern.quote(f) + "\\b").matcher(s);
                if (m.find() && !f.endsWith("ing")) {
                    String who = tidyName(m.group(1));
                    if (who != null && !who.equalsIgnoreCase(title) && !who.matches("(?i)(it|he|she|they|this|the \\w+)")) {
                        return who;
                    }
                }
            }
        }
        return null;
    }

    /** A name as found, or null when it isn't one (a sentence's first word, a lone "The"). */
    private static String tidyName(String raw) {
        String n = raw.replaceAll("[,]?\\s+(and|&|da|de|del|della|der|di|du|van|von|la|le|bin|ibn|al)$", "").replaceAll(",$", "").trim();
        if (n.endsWith(".") && !n.matches(".*\\b\\p{Lu}\\.$")) {
            n = n.substring(0, n.length() - 1); // the sentence's full stop, not an initial ("J. K.")
        }
        if (n.matches("(?i)^(the|a|an|it|he|she|they|this|that|in|on|at|during|after|before|when|while|some|many|most|people|scientists?)$")
                || n.length() < 3 || n.matches("(?i)^the \\p{Ll}.*")) {
            return null;
        }
        return n;
    }

    /** The action as "was … by" wants it: the past participle the article uses, or the regular one. */
    private static String participle(String text, Set<String> forms, String base) {
        for (String f : forms) {
            if (f.matches(".*(ed|en|wn)$|made|built|written|drawn|sung|led|won|found|held|taught|bought|sold|given|taken|seen|known")
                    && text.matches("(?s).*\\b" + Pattern.quote(f) + "\\b.*") && !f.equals(base)) {
                return f;
            }
        }
        String e = base.endsWith("e") ? base.substring(0, base.length() - 1) : base;
        return e + "ed";
    }

    /** What the subject did, in a sentence of its own article about it: {verb as written, the thing done}. */
    static String[] objectIn(String text, Set<String> forms, String title) {
        String last = title.replaceFirst("\\s*\\([^()]+\\)$", "");
        String[] parts = last.split("\\s+");
        String surname = parts[parts.length - 1];
        for (String s : sentences(text)) {
            for (String f : forms) {
                if (f.endsWith("ing") || f.equals(Grammar.base(f)) && !f.matches(".*(ed|en)$") && !f.matches("wrote|made|built|led|won|found|drew|sang|gave|took|ran|became|came|saw|knew|told|held|brought|fought|spoke|chose|flew|grew|left|met|paid|sent")) {
                    continue; // a past action, as an article tells a life
                }
                Matcher m = Pattern.compile("(?:^|\\b)(?:" + Pattern.quote(last) + "|" + Pattern.quote(surname) + "|He|She|They|It)\\s+(?:also\\s+|later\\s+|first\\s+)?("
                        + Pattern.quote(f) + ")\\s+((?:the |a |an |his |her |their |its )?[^,.;:()]{2,70}?)(?=\\s+(?:in \\d{4}|in the \\d|during|while|when|which|that|after|before|because|and (?:later|then)|with)\\b|[,.;:(]|$)")
                        .matcher(s);
                if (m.find()) {
                    String done = m.group(2).trim();
                    if (!done.matches("(?i)^(to|that|in|on|at|from|into|for|by|of|as|it|him|her|them)\\b.*") && done.split("\\s+").length <= 12) {
                        return new String[]{m.group(1), done};
                    }
                }
            }
        }
        return null;
    }
}
