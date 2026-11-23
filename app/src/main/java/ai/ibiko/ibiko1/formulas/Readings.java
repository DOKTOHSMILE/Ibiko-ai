package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The thinking playground: before anything is answered, every way the message could be meant is weighed, not the first
 * pattern that fits. Each reader that recognises the message offers a reading (code pasted to be explained, code to
 * write, a website, a claim to check, a word's meaning, a question about the conversation…); each reading is scored by
 * how much of the message it accounts for (the words of its own subject in it), and by the conversation: what was being
 * talked about (the last readings), whether this is a short follow-up of it, and whether it speaks of the subject in
 * focus. The best reading is taken; the next ones are kept in order, to fall back on, and the reasoning is said in the
 * steps ("Read it as code to write (we were coding); also possible: a word's meaning"). Pure Java.
 *
 * <pre>
 * P1  offers   the readings the readers found (each with how surely its reader took it)
 * P2  words    the share of the message that is its subject's own vocabulary (programming, the web, facts, talk…)
 * P3  context  the same subject as the last turns (+), a short follow-up of it (+), the subject in focus named (+)
 * P4  ranks    best first; ties go to the more specific reading; the reasoning, in words
 * P5  unsure   two readings too close to tell apart, and nothing in the conversation for either: ask which, don't guess;
 *              the answer ("the first", "the website", "code") picks the reading, and the first message is read that way
 * </pre>
 */
public final class Readings {
    private Readings() {
    }

    /** What a message can be. */
    public enum Kind {
        CODE("code to explain", "code"), CODING("code to write", "code"), SITE("a website", "web"), CLAIM("a claim to check", "facts"),
        WORD("a word's meaning", "language"), SYNTAX("a sentence's structure", "language"), FACT("a question of fact", "facts"), TALK("about our conversation", "talk"),
        CONTRAST("two things to tell apart", "compare"), HOW("how something is done", "ways"),
        KINDQ("whether one thing is a kind of another", "kinds"), AHEAD("what something leads to", "reasons"), WHY("a reason", "reasons");

        public final String said, domain;

        Kind(String said, String domain) {
            this.said = said;
            this.domain = domain;
        }
    }

    /** One way the message could be meant, and how well it fits. */
    public static final class Reading {
        public final Kind kind;
        public final double score;
        public final String why;

        Reading(Kind kind, double score, String why) {
            this.kind = kind;
            this.score = score;
            this.why = why;
        }
    }

    /** What the conversation gives to go on: what the last turns were about, and the subject in focus. */
    public static final class Context {
        /** The domains of the last turns, the latest last ("code", "web", "facts"…). */
        public final List<String> recent = new ArrayList<String>();
        /** The subject in focus (a name), or null. */
        public String focus;

        public Context said(String domain) {
            if (domain != null) {
                recent.add(domain);
                while (recent.size() > 4) {
                    recent.remove(0);
                }
            }
            return this;
        }

        String last() {
            return recent.isEmpty() ? null : recent.get(recent.size() - 1);
        }
    }

    /** P2: each subject's own words. */
    public static final Map<String, Set<String>> VOCAB = new LinkedHashMap<String, Set<String>>();

    static {
        VOCAB.put("code", set("code", "coding", "program", "programming", "function", "functions", "method", "variable", "variables", "loop",
                "loops", "array", "arrays", "list", "string", "strings", "integer", "int", "compile", "compiler", "syntax", "bug", "bugs",
                "debug", "error", "exception", "class", "object", "api", "json", "script", "snippet", "library", "import", "return",
                "python", "java", "javascript", "typescript", "kotlin", "rust", "golang", "php", "ruby", "bash", "sql", "html", "css",
                "algorithm", "recursion", "regex", "parse", "write", "implement", "run", "output", "input", "file", "database", "query",
                "sort", "reverse", "factorial", "fibonacci", "prime", "dictionary", "hashmap", "framework", "react", "node", "terminal"));
        VOCAB.put("web", set("website", "websites", "site", "sites", "page", "pages", "landing", "homepage", "section", "sections", "header",
                "footer", "layout", "responsive", "design", "domain", "hosting", "html", "css", "menu", "navigation", "seo", "portfolio",
                "blog", "shop", "store", "ecommerce", "e-commerce", "template", "theme", "wordpress", "build", "online"));
        VOCAB.put("facts", set("capital", "president", "born", "population", "country", "city", "founded", "founder", "currency", "language",
                "spoken", "located", "where", "when", "who", "married", "tallest", "largest", "king", "queen", "leader", "history"));
        VOCAB.put("language", set("mean", "means", "meaning", "word", "words", "definition", "define", "synonym", "opposite", "spell",
                "grammar", "noun", "verb", "adjective", "plural", "sentence", "parse", "tree", "phrase", "clause", "subject"));
        VOCAB.put("reasons", set("why", "because", "cause", "caused", "causes", "reason", "reasons", "happens", "happen", "effect", "effects",
                "lead", "leads", "result", "results", "consequence", "consequences", "if"));
        VOCAB.put("ways", set("how", "made", "make", "work", "works", "formed", "form", "steps", "process", "done", "produced", "built",
                "grow", "cook"));
        VOCAB.put("compare", set("difference", "differences", "compare", "versus", "vs", "between", "unlike", "same", "different", "or"));
        VOCAB.put("kinds", set("kind", "type", "sort", "animal", "mammal", "mammals", "bird", "birds", "fish", "plant", "insect", "reptile",
                "member", "example", "metal", "element", "star", "planet", "fruit", "vegetable"));
        VOCAB.put("talk", set("said", "say", "talked", "talking", "asked", "earlier", "before", "conversation", "chat", "recap", "summarize",
                "back"));
    }

    private static Set<String> set(String... w) {
        return new HashSet<String>(Arrays.asList(w));
    }

    /** How surely each reader takes a message when it recognises it (before the words and the context). */
    private static double base(Kind k) {
        switch (k) {
            case CODE: return 0.9; // code is in the message: hard to mistake
            case TALK: return 0.8;
            case CLAIM: return 0.75;
            case SITE: return 0.7;
            case CODING: return 0.7;
            case FACT: return 0.55;
            case CONTRAST: return 0.7;
            case KINDQ: return 0.65;
            case AHEAD: return 0.65;
            case WHY:
            case HOW: return 0.6;
            default: return 0.5;
        }
    }

    /** P1–P4: the readings offered, weighed and ranked, best first. */
    public static List<Reading> weigh(String message, List<Kind> offered, Context ctx) {
        return weigh(message, offered, ctx, new HashSet<Kind>());
    }

    /**
     * P1–P4, with readings some readers offered only loosely: from the message's words, not its usual phrasing. Those
     * start lower (a reader sure of the phrasing outranks one going by the words alone), and say so.
     */
    public static List<Reading> weigh(String message, List<Kind> offered, Context ctx, Set<Kind> loose) {
        List<Reading> out = new ArrayList<Reading>();
        String[] words = message.toLowerCase(Locale.ROOT).replaceAll("```.*?```", " ").split("[^\\p{L}0-9+#-]+");
        int n = 0;
        for (String w : words) {
            n += w.isEmpty() ? 0 : 1;
        }
        boolean shortOne = n <= 6;
        for (Kind k : offered) {
            double score = base(k);
            List<String> why = new ArrayList<String>();
            if (loose.contains(k)) {
                score -= 0.1;
                why.add("from its words, not the usual way of asking");
            }
            Set<String> own = VOCAB.getOrDefault(k.domain, new HashSet<String>());
            int hits = 0;
            for (String w : words) {
                hits += own.contains(w) ? 1 : 0;
            }
            if (hits > 0) {
                score += Math.min(0.3, 0.1 * hits);
                why.add(hits + (hits == 1 ? " word" : " words") + " of " + domainSaid(k.domain));
            }
            if (ctx != null && k.domain.equals(ctx.last())) {
                score += 0.25;
                why.add("we were on " + domainSaid(k.domain));
                if (shortOne) {
                    score += 0.15; // a short message after that is a follow-up of it
                    why.add("a short follow-up");
                }
            } else if (ctx != null && ctx.recent.contains(k.domain)) {
                score += 0.1;
                why.add(domainSaid(k.domain) + " came up a moment ago");
            }
            if (ctx != null && ctx.focus != null && message.toLowerCase(Locale.ROOT).contains(ctx.focus.toLowerCase(Locale.ROOT))) {
                score += 0.05;
            }
            out.add(new Reading(k, Math.round(score * 100) / 100.0, String.join(", ", why)));
        }
        out.sort((a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : Double.compare(base(b.kind), base(a.kind)));
        return out;
    }

    private static String domainSaid(String domain) {
        switch (domain) {
            case "code": return "programming";
            case "web": return "websites";
            case "facts": return "facts";
            case "language": return "words and meanings";
            case "talk": return "our conversation";
            case "reasons": return "causes and reasons";
            case "ways": return "how things are done";
            case "compare": return "comparing";
            case "kinds": return "kinds of things";
            default: return domain;
        }
    }

    /** P4: the reasoning, as one step: "Read it as …; also possible: …". */
    public static String said(List<Reading> ranked) {
        if (ranked.isEmpty()) {
            return null;
        }
        Reading best = ranked.get(0);
        StringBuilder b = new StringBuilder("Weighing what you meant: ").append(best.kind.said);
        if (!best.why.isEmpty()) {
            b.append(" (").append(best.why).append(")");
        }
        if (ranked.size() > 1) {
            List<String> others = new ArrayList<String>();
            for (int i = 1; i < ranked.size() && i < 3; i++) {
                others.add(ranked.get(i).kind.said);
            }
            b.append("; also possible: ").append(String.join(", ", others));
        }
        return b.toString();
    }

    /** P5: how close two readings may be before the message can't be told apart by them. */
    static final double TIE = 0.05;

    /** P5: whether the two best readings are too close to choose between, with nothing from the conversation for either. */
    public static boolean unsure(List<Reading> ranked) {
        if (ranked.size() < 2) {
            return false;
        }
        Reading a = ranked.get(0), b = ranked.get(1);
        if (a.kind.domain.equals(b.kind.domain) || a.kind == Kind.CODE || b.kind == Kind.CODE) {
            return false; // the same subject either way, or code in the message: nothing to ask
        }
        return a.score - b.score < TIE && !fromContext(a) && !fromContext(b);
    }

    private static boolean fromContext(Reading r) {
        return r.why.contains("we were on") || r.why.contains("came up a moment ago");
    }

    /** P5: the question: "Do you mean a website, or code to write?" */
    public static String ask(List<Reading> ranked) {
        return "Do you mean " + ranked.get(0).kind.said + ", or " + ranked.get(1).kind.said + "? Say which, and I'll go on from there.";
    }

    private static final String[][] ORDINALS = {{"first", "1", "one", "former", "the first one", "1st"}, {"second", "2", "two", "latter",
        "the second one", "2nd"}};

    /** P5: the reading an answer to the question picks, or null when it doesn't pick one (then it's a new message). */
    public static Kind choose(String answer, List<Kind> asked) {
        String a = answer == null ? "" : answer.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9 ']", " ").trim().replaceAll("\\s+", " ");
        if (a.isEmpty() || a.split(" ").length > 8) {
            return null;
        }
        String bare = a.replaceFirst("^(?:i mean|i meant|i want|mean|meant|yes|no|the|it's|its|it is)\\s+", "").replaceFirst("^(?:the|a|an)\\s+", "")
                .replaceFirst("\\s+(?:one|please|thanks)$", "");
        for (int i = 0; i < ORDINALS.length && i < asked.size(); i++) {
            for (String o : ORDINALS[i]) {
                if (bare.equals(o) || bare.equals(o + " one")) {
                    return asked.get(i);
                }
            }
        }
        Kind picked = null;
        int best = 0;
        String[] words = a.split(" ");
        for (Kind k : asked) {
            int hits = 0;
            Set<String> own = VOCAB.getOrDefault(k.domain, new HashSet<String>());
            Set<String> said = new HashSet<String>(Arrays.asList(k.said.split(" ")));
            said.removeAll(Arrays.asList("a", "an", "the", "to", "of", "about", "our", "whether", "one", "another", "something", "is", "how"));
            for (String w : words) {
                hits += own.contains(w) || said.contains(w) ? 1 : 0;
            }
            if (hits > best) {
                best = hits;
                picked = k;
            } else if (hits == best && hits > 0) {
                picked = null; // both named: still not told apart
            }
        }
        return picked;
    }
}
