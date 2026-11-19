package ai.ibiko.suit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Cortex's reading of a message, made by Ibiko itself in microseconds before any model sees it: what kind of
 * act it is (a question, a request, a greeting, thanks, a correction, a feeling), whether it is negated, the
 * numbers, names, repositories, files and links in it, the words that carry its topic, and any arithmetic inside.
 * The reading goes to the model as one line ({@link #brief}), so a small model on the device knows what the user
 * means and what they refer to without spending its own thinking on it. It never runs a function: only the model
 * decides that. Pure Java.
 */
public final class Comprehension {
    /** Deterministic local linguistic evidence; heuristic labels must not be treated as ground truth. */
    public ai.ibiko.intelligence.UnderstandingEngine.Result understanding;
    /** What the message does. */
    public enum Act { QUESTION, REQUEST, GREETING, THANKS, CORRECTION, FEELING, STATEMENT }

    public final Act act;
    public final boolean negated;
    public final List<String> numbers = new ArrayList<String>();
    public final List<String> names = new ArrayList<String>();
    public final List<String> repos = new ArrayList<String>();
    public final List<String> files = new ArrayList<String>();
    public final List<String> links = new ArrayList<String>();
    public final List<String> topic = new ArrayList<String>();
    /** An arithmetic expression in the message and its exact value, or null. */
    public final String math, mathValue;
    /** The function the words ask for, or null when they want an answer. A second one when the message has two steps. */
    public final FunctionCode want, also;
    /** They told Ibiko not to act ("don't post", "never search"). */
    public final boolean refused;
    /** "What if" / "what would happen": explain, never perform. */
    public final boolean hypothetical;
    /** A choice to weigh, not a fact and not a command. */
    public final boolean judgement;

    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])-?\\d[\\d,]*(?:\\.\\d+)?%?(?![\\w])");
    private static final Pattern LINK = Pattern.compile("https?://\\S+");
    private static final Pattern REPO = Pattern.compile("(?<![\\w./-])([A-Za-z0-9][\\w.-]{0,38})/([\\w.-]{1,100})(?![\\w/])");
    private static final Pattern FILE = Pattern.compile("(?<![\\w/])([\\w./-]+\\.(?:java|kt|py|js|ts|tsx|jsx|go|rs|c|cpp|h|cs|rb|php|swift"
            + "|md|txt|json|xml|yml|yaml|gradle|toml|html|css|sql|sh|pdf|docx|xlsx|csv|png|jpg|zip))\\b");
    private static final Pattern NAME = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b");
    private static final Set<String> STOP = new java.util.HashSet<String>(java.util.Arrays.asList(
            "the", "a", "an", "and", "or", "but", "to", "of", "in", "on", "at", "for", "with", "is", "are", "was", "were",
            "be", "it", "this", "that", "these", "those", "i", "you", "me", "my", "your", "we", "our", "they", "them",
            "he", "she", "his", "her", "do", "does", "did", "can", "could", "would", "should", "will", "please", "what",
            "how", "why", "when", "where", "which", "who", "just", "so", "if", "then", "there", "here", "about", "from",
            "up", "out", "not", "no", "yes", "have", "has", "had", "want", "need", "like", "get", "make", "let"));

    private Comprehension(Act act, boolean negated, boolean refused, boolean hypothetical, boolean judgement,
            FunctionCode want, FunctionCode also, String math, String mathValue) {
        this.act = act;
        this.negated = negated;
        this.refused = refused;
        this.hypothetical = hypothetical;
        this.judgement = judgement;
        this.understanding = new ai.ibiko.intelligence.UnderstandingEngine().analyze("", null);
        this.want = want;
        this.also = also;
        this.math = math;
        this.mathValue = mathValue;
    }

    /** Reads {@code text}. */
    public static Comprehension of(String text) {
        String t = text == null ? "" : text.trim();
        String low = t.toLowerCase(Locale.US);
        String asked = Arithmetic.asked(t);
        String math = null, value = null;
        if (asked != null) {
            math = asked;
            value = Arithmetic.show(Arithmetic.eval(asked));
        } else {
            // arithmetic inside a longer message ("I have 12 boxes of 24, how many?" is left to the model)
            Matcher m = Pattern.compile("(\\d[\\d,.]*\\s*[-+*/x×÷^]\\s*[\\d(][\\d,.()\\s+*/x×÷^-]*)").matcher(t);
            if (m.find() && Arithmetic.eval(m.group(1)) != null) {
                math = m.group(1).trim();
                value = Arithmetic.show(Arithmetic.eval(math));
            }
        }
        boolean no = refused(low);
        boolean hypothetical = low.matches(".*\\b(what if|what would happen|suppose that|suppose i|imagine if|if i were)\\b.*");
        boolean judgement = !hypothetical && (ai.ibiko.ibiko1.formulas.Critical.decision(t)
                || low.matches(".*\\b(should i|should we|which is better|help me decide|torn between|stuck between)\\b.*"));
        FunctionCode[] picked = no || hypothetical || judgement ? new FunctionCode[] {null, null} : pick(low, act(low), math != null);
        Comprehension c = new Comprehension(act(low), negated(low), no, hypothetical, judgement, picked[0], picked[1], math, value);
        c.understanding = new ai.ibiko.intelligence.UnderstandingEngine().analyze(t, null);
        collect(NUMBER, t, 0, c.numbers);
        collect(LINK, t, 0, c.links);
        String noLinks = LINK.matcher(t).replaceAll(" ");
        collect(FILE, noLinks, 1, c.files);
        Matcher r = REPO.matcher(noLinks);
        while (r.find()) {
            String repo = r.group(1) + "/" + r.group(2);
            if (!c.files.contains(repo) && !repo.matches("\\d+/\\d+") && !r.group(2).contains(".")) {
                c.repos.add(repo);
            }
        }
        Matcher n = NAME.matcher(noLinks);
        while (n.find() && c.names.size() < 6) {
            String name = n.group(1);
            String before = noLinks.substring(0, n.start()).replaceAll("\\s+$", "");
            if (before.isEmpty() || before.endsWith(".") || before.endsWith("!") || before.endsWith("?")) {
                if (!name.contains(" ")) {
                    continue; // a capital that only starts a sentence is not a name
                }
                name = name.substring(name.indexOf(' ') + 1); // "Ask Ada Lovelace": the first word only starts it
            }
            if (!STOP.contains(name.toLowerCase(Locale.US)) && !c.names.contains(name)) {
                c.names.add(name);
            }
        }
        Set<String> words = new LinkedHashSet<String>();
        for (String w : low.replaceAll("https?://\\S+", " ").split("[^\\p{L}\\p{N}'-]+")) {
            if (w.length() > 2 && !STOP.contains(w) && !w.matches("\\d+")) {
                words.add(w);
            }
        }
        for (String w : words) {
            if (c.topic.size() >= 6) {
                break;
            }
            c.topic.add(w);
        }
        return c;
    }

    static Act act(String low) {
        String t = low.replaceAll("[\\s.!]+$", "");
        if (t.matches("^(no|nope|wrong|that'?s wrong|not that|that'?s not (it|right|what i (meant|asked|wanted)))\\b.*")) {
            return Act.CORRECTION;
        }
        if (t.matches("^(hi|hello|hey|good (morning|afternoon|evening)|howdy|yo|hiya)\\b.{0,20}$")) {
            return Act.GREETING;
        }
        if (t.matches("^(thanks|thank you|thx|cheers|appreciate it)\\b.{0,30}$")) {
            return Act.THANKS;
        }
        if (t.matches(".*\\b(i('?m| am) (so )?(sad|tired|angry|happy|stressed|worried|frustrated|excited|upset))\\b.*")) {
            return Act.FEELING;
        }
        // "can you fix the bug" is a request. "can you tell me what a quark is" and "how do I …" stay questions.
        String core = t.replaceFirst("^(?:(?:please|pls|kindly|can you|could you|would you|will you|i want you to|i need you to|help me)\\s+)+", "");
        if (core.matches("^(what|who|whom|whose|when|where|why|how|which|is|are|was|were|do|does|did)\\b.*")
                || t.matches("^(what|who|whom|whose|when|where|why|how|which)\\b.*")) {
            return Act.QUESTION;
        }
        if (core.matches("^(tell|explain|describe)\\b.*") && core.matches(".*\\b(what|why|how|who|about)\\b.*")) {
            return Act.QUESTION;
        }
        if (core.matches("^(make|build|create|write|draw|show|find|search|look|open|run|fix|add|remove|delete"
                + "|change|update|send|post|clone|push|pull|merge|give|list|check|read|summari[sz]e|translate"
                + "|design|plan|start|stop|continue|go|share|mkdir)\\b.*")) {
            return Act.REQUEST;
        }
        if (t.endsWith("?") || t.matches("^(what|who|whom|whose|when|where|why|how|which|is|are|was|were|do|does|did|can|could"
                + "|would|should|will|shall|may|might|have|has)\\b.*")) {
            return Act.QUESTION;
        }
        return Act.STATEMENT;
    }

    static boolean negated(String low) {
        return low.matches(".*\\b(not|don'?t|doesn'?t|didn'?t|never|no longer|won'?t|can'?t|isn'?t|aren'?t|without)\\b.*");
    }

    /** "Don't post" and "never search" are a refusal, not a request to do that thing. */
    static boolean refused(String low) {
        return low.matches(".*\\b(do not|don't|dont|never)\\s+(you\\s+)?(search|post|share|run|clone|draw|fix|build|code|push|upload)\\b.*")
                || low.matches(".*\\b(no need to|don't bother)\\b.*");
    }

    /** The function this message asks for, and a second one when two steps are joined by "and" or "then". */
    static FunctionCode[] pick(String low, Act act, boolean math) {
        if (act == Act.GREETING || act == Act.THANKS || act == Act.FEELING || act == Act.CORRECTION) {
            return new FunctionCode[] {null, null};
        }
        String[] parts = low.split("\\s+\\b(?:and then|then|and)\\b\\s+");
        FunctionCode first = one(parts[0], act, math);
        FunctionCode second = null;
        for (int i = 1; i < parts.length; i++) {
            FunctionCode n = one(parts[i], Act.REQUEST, false);
            if (n != null && n != first) {
                second = n;
                break;
            }
        }
        if (first == null && second != null) {
            first = second;
            second = null;
        }
        return new FunctionCode[] {first, second};
    }

    /** One clause: the function it asks for, or null when it wants words. */
    static FunctionCode one(String t, Act act, boolean math) {
        if (act == Act.QUESTION && t.matches("^(how|why|who|when|where|what|which)\\b.*")) {
            if (t.matches(".*\\b(time|date|clock)\\b.*")) {
                return FunctionCode.TM;
            }
            if (t.matches(".*\\b(weather|news|price|latest|today)\\b.*")) {
                return FunctionCode.SR;
            }
            if (t.matches(".*\\b(files?|folder|repos?|repositor(?:y|ies))\\b.*")) {
                return FunctionCode.RF;
            }
            return null; // "how do I clone" is an explanation, not a command
        }
        if (t.matches(".*\\b(photograph|photo of|real photo)\\b.*") && !t.matches(".*\\b(draw|logo|icon|illustration)\\b.*")) {
            return FunctionCode.PH;
        }
        if (t.matches(".*\\b(draw|logo|icon|illustration|artwork|sketch|wallpaper)\\b.*")
                && !t.matches(".*\\b(website|web ?page|app|site|landing)\\b.*")) {
            return FunctionCode.IM;
        }
        if (t.matches(".*\\b(website|web ?page|landing|bug|refactor|endpoint|button|screen|repositor(?:y|ies)|compile|audit|code|codes|java|python)\\b.*")
                && t.matches(".*\\b(build|fix|write|add|make|design|implement|code|change|update|create)\\b.*")) {
            return FunctionCode.WK;
        }
        if (t.matches(".*\\b(fix|implement|refactor)\\b.*") && t.matches(".*\\b(bug|code|class|function|java|app|screen|button)\\b.*")) {
            return FunctionCode.WK;
        }
        if (t.matches(".*\\b(audit\\w*|inspect\\w*|review\\w*|scan\\w*|analy[sz]\\w*|debug\\w*|go(?:ing)? through|check\\w*)\\b.*")
                && t.matches(".*\\b(codebase|code ?base|project|repo|repos|repository|files?|functions?|app|source|codes?)\\b.*")) {
            return FunctionCode.WK; // "audit the entire project codebase", "inspect the files for broken functions"
        }
        if (t.matches(".*\\b(story|essay|poem|chapter)\\b.*") && !t.matches(".*\\b(bug|repo|class|function)\\b.*")) {
            return FunctionCode.WP;
        }
        if (t.matches(".*\\b(pdf|docx|word document|spreadsheet|xlsx)\\b.*")) {
            return FunctionCode.MF;
        }
        if (t.matches(".*\\b(schedule|scheduled)\\b.*") && t.matches(".*\\bpost\\b.*")) {
            return FunctionCode.SP;
        }
        if (t.matches(".*\\bsentiment\\b.*") || t.matches(".*\\binvestigate\\b.*\\b(post|tweet)\\b.*")) {
            return FunctionCode.SI;
        }
        if (t.matches(".*\\b(tweet|trending|followers)\\b.*") || t.matches(".*\\bpost on\\b.*")) {
            return FunctionCode.SO;
        }
        if (t.matches(".*\\b(share|whatsapp)\\b.*") || t.matches(".*\\bcopy (it|this|that)\\b.*")) {
            return FunctionCode.AC;
        }
        if (t.matches(".*\\bunzip\\b.*") || t.matches(".*\\bextract\\b.*\\bzip\\b.*")) {
            return FunctionCode.UZ;
        }
        if (t.matches(".*\\b(find|search)\\b.*\\bzip\\b.*")) {
            return FunctionCode.FZ;
        }
        if (t.matches(".*\\.(?:java|kt|py|js|ts|md)\\b.*") && t.matches(".*\\b(read|open|look at|what'?s in)\\b.*")) {
            return FunctionCode.RF;
        }
        if (t.matches(".*\\b(mkdir|\\bls\\b|list the files|make a directory|disk space|shell|terminal|clone|git pull|git push)\\b.*")
                || t.matches(".*\\b(run|install)\\b.*\\b(command|tests?|script|python|node|npm)\\b.*")) {
            return FunctionCode.SH;
        }
        if (t.matches(".*\\b(search|look up|google|weather)\\b.*")) {
            return FunctionCode.SR;
        }
        if (t.matches(".*\\b(call me|my name is)\\b.*")) {
            return FunctionCode.NM;
        }
        if (t.matches(".*\\b(new chat|start over|fresh chat)\\b.*")) {
            return FunctionCode.NC;
        }
        if (t.matches(".*\\bsettings\\b.*")) {
            return FunctionCode.OS;
        }
        if (t.matches(".*\\b(carry on|keep going|continue)\\b.*")) {
            return FunctionCode.CO;
        }
        if (t.matches(".*\\b(plan mode|plan first)\\b.*")) {
            return FunctionCode.PM;
        }
        if (math && !t.matches(".*\\b(file|code|repo)\\b.*")) {
            return FunctionCode.CA;
        }
        return null;
    }

    private static void collect(Pattern p, String t, int group, List<String> into) {
        Matcher m = p.matcher(t);
        while (m.find() && into.size() < 12) {
            String v = m.group(group);
            if (!into.contains(v)) {
                into.add(v);
            }
        }
    }

    /** What to do with this message, in one sentence, ahead of the details so a short context still keeps it. */
    public String decisionLine() {
        if (refused) {
            return "Decision: they said not to act. Answer in words. Do not write a function code.";
        }
        if (hypothetical) {
            return "Decision: hypothetical. Explain what would follow. Do not act and do not write a function code.";
        }
        if (want != null) {
            String s = "Decision: write " + want.code() + ".";
            if (also != null) {
                s += " Then " + also.code() + ".";
            }
            return s + (act == Act.QUESTION ? " This question needs that, not an explanation." : " They asked for this to be done, not explained.");
        }
        if (judgement) {
            return "Decision: this is a judgement, not a fact. Name the criteria in their words, weigh both sides,"
                    + " including the best case against your lean, and say what one fact would flip it. Do not write a function code.";
        }
        if (act == Act.QUESTION) {
            return "Decision: answer the question in words. Do not write a function code.";
        }
        return "Decision: answer in words. Do not write a function code.";
    }

    /**
     * What the model is given: the decision first, then what the message is and what it refers to, with the exact
     * value of any arithmetic.
     */
    public String brief() {
        StringBuilder b = new StringBuilder(decisionLine()).append(" Ibiko's reading of the message: ").append(act.name().toLowerCase(Locale.US));
        if (negated) {
            b.append(", negated");
        }
        if (!topic.isEmpty()) {
            b.append("; about: ").append(String.join(", ", topic));
        }
        if (!names.isEmpty()) {
            b.append("; names: ").append(String.join(", ", names));
        }
        if (!repos.isEmpty()) {
            b.append("; repositories: ").append(String.join(", ", repos));
        }
        if (!files.isEmpty()) {
            b.append("; files: ").append(String.join(", ", files));
        }
        if (!links.isEmpty()) {
            b.append("; links: ").append(String.join(", ", links));
        }
        if (math != null && mathValue != null && !mathValue.isEmpty()) {
            b.append("; exact arithmetic: ").append(math).append(" = ").append(mathValue).append(" (use this value)");
        }
        if (understanding != null) {
            b.append("; ").append(understanding.toPromptContext());
        }
        return b.append('.').toString();
    }

    /** A cut context still starts with the decision, so a short prompt does not lose what the message asked for. */
    public static String keepDecision(String gathered, int room) {
        if (gathered == null || room < 0) {
            return gathered;
        }
        if (gathered.length() <= room) {
            return gathered;
        }
        int at = gathered.indexOf("Decision:");
        if (at < 0) {
            return gathered.substring(0, room);
        }
        int end = gathered.indexOf('.', at);
        String decision = gathered.substring(at, end < 0 ? Math.min(gathered.length(), at + 240) : end + 1);
        if (decision.length() >= room) {
            return decision.substring(0, room);
        }
        String rest = (at == 0 ? "" : gathered.substring(0, at)) + gathered.substring(Math.min(gathered.length(), end < 0 ? at : end + 1));
        int left = room - decision.length() - 1;
        if (left < 1) {
            return decision;
        }
        if (rest.length() > left) {
            rest = rest.substring(0, left);
        }
        return decision + " " + rest;
    }
}
