package ai.ibiko.ibiko1.cognition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ai.ibiko.ibiko1.formulas.Critical;

/**
 * Engine 4, metacognitive planning and review (priority 4): Ibiko looks at its own thinking before it answers. A short
 * plan first (what is really asked, what a good answer is, what is known, what is missing, which way to reason, what
 * could make it wrong, how much checking the risk deserves); then the draft is reviewed for concrete defects, and only a
 * concrete defect is put right: a draft is never rewritten just to sound more elaborate. Three passes by default, each
 * doing a different job (understand and plan; solve and verify; critique and finalize), one or two for a simple request,
 * a fourth only for a high-stakes or failed check, aimed at what failed. It stops as soon as another pass would not
 * change the answer. Pure Java.
 *
 * <pre>
 * M1  plan        question, what counts as a good answer, what is known, what is missing, the method, the risks, the
 *                 verification, the passes
 * M2  complexity  SIMPLE (one short ask), NORMAL, COMPLEX (several parts, numbers to work, code, "step by step")
 * M3  risk        HIGH for health, law, money, safety, and steps that can't be undone; else NORMAL or LOW
 * M4  passes      SIMPLE 1–2, NORMAL 3, COMPLEX 3 + targeted checks, HIGH 4
 * M5  review      relevance, working that doesn't add up, contradictions, a cause claimed from a link, sure words on
 *                 weak support, a constraint not kept ("in one sentence", "under 50 words"); length and style are noted,
 *                 never a reason to redo it
 * M6  repair      each material defect put right on its own; the rest of the draft is left alone
 * M7  stop        no material defect left, or a repair changed nothing, or the passes are spent: what still stands is said
 * </pre>
 */
public final class Metacognition {
    private Metacognition() {
    }

    public enum Complexity { SIMPLE, NORMAL, COMPLEX }

    public enum Risk { LOW, NORMAL, HIGH }

    /** M1: the plan made before answering. */
    public static final class Plan {
        public final String question, wants, method;
        public final List<String> missing, risks, constraints;
        public final Complexity complexity;
        public final Risk risk;
        public final int passes;

        Plan(String question, String wants, String method, List<String> missing, List<String> risks, List<String> constraints, Complexity complexity,
                Risk risk, int passes) {
            this.question = question;
            this.wants = wants;
            this.method = method;
            this.missing = missing;
            this.risks = risks;
            this.constraints = constraints;
            this.complexity = complexity;
            this.risk = risk;
            this.passes = passes;
        }

        /** The plan in one line, for any brain to answer by (no scratchwork: what to do, not how it was found). */
        public String brief() {
            StringBuilder b = new StringBuilder("Plan: ").append(wants).append("; reason by ").append(method).append('.');
            if (!constraints.isEmpty()) {
                b.append(" Keep to: ").append(String.join(", ", constraints)).append('.');
            }
            if (!missing.isEmpty()) {
                b.append(" Missing: ").append(String.join(", ", missing)).append(": ask, or say what the answer assumes.");
            }
            if (!risks.isEmpty()) {
                b.append(" Could go wrong: ").append(String.join(", ", risks)).append('.');
            }
            if (risk == Risk.HIGH) {
                b.append(" High stakes: give the risks and an alternative, and say what should be checked with a professional.");
            }
            b.append(" Separate facts from assumptions; say how sure you are, no more than the evidence allows.");
            return b.toString();
        }
    }

    private static final Pattern HIGH_STAKES = Pattern.compile("(?i)\\b(?:dose|dosage|medic\\w*|drug|pill|symptom|diagnos\\w*|pregnan\\w*|surgery|"
            + "overdose|suicid\\w*|chest pain|bleeding|poison|allerg\\w*|lawsuit|sue|court|contract|legal|illegal|tax(?:es)?|visa|immigration|"
            + "invest\\w*|loan|mortgage|debt|crypto|bitcoin|stocks?|savings|life savings|bet|gambl\\w*|delete (?:all|every|my)|format|"
            + "wipe|quit (?:my|the) job|resign|sell (?:my|the) (?:house|car|land|business)|electric\\w*|wiring|gas leak|chemicals?)\\b");
    private static final Pattern COMPLEX = Pattern.compile("(?i)\\b(?:step by step|prove|derive|calculate|compute|work out|compare|analy[sz]e|design|"
            + "debug|implement|plan|strategy|why does|how does|explain how)\\b|```|\\n\\s*\\d+[.)]");
    private static final Pattern ONE_SENTENCE = Pattern.compile("(?i)\\b(?:in|as)\\s+(?:one|a single|1)\\s+sentence\\b");
    private static final Pattern WORD_LIMIT = Pattern.compile("(?i)\\b(?:under|in|at most|no more than|within|less than|max(?:imum)?)\\s+(\\d{1,4})\\s+words\\b");
    private static final Pattern BULLETS = Pattern.compile("(?i)\\b(?:as|in)\\s+(?:a\\s+)?(?:list|bullet points?|points)\\b");

    /** M1–M4: the plan for a request. */
    public static Plan plan(String request) {
        String q = request == null ? "" : request.trim();
        Critical.Reading r = Critical.read(q);
        String kind = r == null || r.kind == null ? "fact" : r.kind.name().toLowerCase(Locale.ROOT);
        String wants;
        String method;
        switch (kind) {
            case "cause":
                wants = "the cause, with how it works";
                method = "mechanism and order in time, other causes ruled out";
                break;
            case "comparison":
                wants = "a comparison on named measures";
                method = "the same measures for each, then the difference";
                break;
            case "judgement":
                wants = "a recommendation for this person's goal";
                method = "criteria first, both sides, then the choice";
                break;
            case "prediction":
                wants = "a forecast, not a fact";
                method = "what it rests on and how it could be wrong";
                break;
            case "definition":
                wants = "what it is, plainly";
                method = "the definition, then one example";
                break;
            case "howto":
                wants = "steps that work";
                method = "ordered steps, each checkable";
                break;
            default:
                wants = "the fact asked for";
                method = "the most trusted source, checked";
                break;
        }
        int parts = q.split("\\?").length + (q.matches("(?s).*\\b(?:and also|as well as|then)\\b.*") ? 1 : 0);
        int numbers = 0;
        Matcher n = Pattern.compile("\\d").matcher(q);
        while (n.find()) {
            numbers++;
        }
        Complexity c = parts >= 3 || COMPLEX.matcher(q).find() || numbers >= 6 || q.length() > 600 ? Complexity.COMPLEX
                : q.split("\\s+").length <= 8 && parts <= 2 ? Complexity.SIMPLE : Complexity.NORMAL;
        Risk risk = HIGH_STAKES.matcher(q).find() ? Risk.HIGH : c == Complexity.SIMPLE ? Risk.LOW : Risk.NORMAL;
        int passes = risk == Risk.HIGH ? 4 : c == Complexity.SIMPLE ? (risk == Risk.LOW ? 1 : 2) : 3;
        List<String> missing = new ArrayList<String>();
        if (kind.equals("judgement") && !q.matches("(?is).*\\b(?:for|because|budget|goal|so that|i need|i want|cheap|fast|quality|safe)\\b.*")) {
            missing.add("what matters most to them (cost, time, quality, safety)");
        }
        if (q.matches("(?is)^\\s*(?:how much|what) (?:does|will|would) (?:it|this|that) cost\\b.*")) {
            missing.add("which thing, and where");
        }
        if (q.matches("(?is).*\\b(?:my|this|that) (?:code|file|error|document|essay)\\b.*") && !q.contains("\n") && q.length() < 160) {
            missing.add("the " + q.replaceAll("(?is).*\\b(?:my|this|that) (code|file|error|document|essay)\\b.*", "$1") + " itself");
        }
        List<String> risks = new ArrayList<String>();
        if (r != null) {
            risks.addAll(Critical.notesList(r));
        }
        if (numbers >= 2) {
            risks.add("an arithmetic slip: work each step");
        }
        List<String> constraints = new ArrayList<String>();
        // a constraint on the reply is read from a short request only: in a passage with tasks it belongs to one task
        if (q.length() < 300 && ONE_SENTENCE.matcher(q).find()) {
            constraints.add("one sentence");
        }
        Matcher w = WORD_LIMIT.matcher(q);
        if (q.length() < 300 && w.find()) {
            constraints.add("at most " + w.group(1) + " words");
        }
        if (q.length() < 300 && BULLETS.matcher(q).find()) {
            constraints.add("a list");
        }
        return new Plan(q, wants, method, missing, risks, constraints, c, risk, passes);
    }

    // ------------------------------------------------------------------ M5 review

    public enum Kind { RELEVANCE, ARITHMETIC, CONTRADICTION, CAUSAL, OVERCONFIDENT, CONSTRAINT, LENGTH, STYLE }

    /** A defect the review found. */
    public static final class Defect {
        public final Kind kind;
        public final String detail;

        Defect(Kind kind, String detail) {
            this.kind = kind;
            this.detail = detail;
        }

        /** Length and style are noted, never a reason to redo an answer. */
        public boolean material() {
            return kind != Kind.LENGTH && kind != Kind.STYLE;
        }

        @Override
        public String toString() {
            return kind.name().toLowerCase(Locale.ROOT) + ": " + detail;
        }
    }

    private static final Pattern CERTAIN = Pattern.compile("(?i)\\b(?:definitely|certainly|without (?:a )?doubt|undoubtedly|guaranteed|always works|"
            + "100%|proven fact|there is no question|it is certain|for sure|absolutely)\\b");
    private static final Set<String> SMALL = new HashSet<String>(java.util.Arrays.asList("what", "which", "who", "when", "where", "why", "how", "the",
            "a", "an", "is", "are", "was", "were", "do", "does", "did", "of", "in", "on", "to", "for", "and", "or", "it", "this", "that", "me", "my",
            "you", "your", "can", "could", "would", "should", "tell", "about", "please", "i", "be", "with", "there"));

    /** M5: the defects in a draft, for this plan; {@code known} is what is already held, {@code label} how sure it may be. */
    public static List<Defect> review(Plan plan, String draft, List<String> known, Calibration.Label label) {
        List<Defect> out = new ArrayList<Defect>();
        String d = draft == null ? "" : draft;
        if (d.trim().isEmpty()) {
            out.add(new Defect(Kind.RELEVANCE, "nothing was answered"));
            return out;
        }
        // relevance: a substantive question answered with none of its content words (not for talk)
        Set<String> qw = content(plan.question), dw = content(d);
        if (qw.size() >= 2 && plan.complexity != Complexity.SIMPLE && d.length() > 80) {
            Set<String> both = new HashSet<String>(qw);
            both.retainAll(dw);
            if (both.isEmpty()) {
                out.add(new Defect(Kind.RELEVANCE, "it doesn't speak to " + String.join(", ", qw)));
            }
        }
        for (Reasoning.Wrong w : Reasoning.checkWorking(d)) {
            out.add(new Defect(Kind.ARITHMETIC, "“" + w.written + "” comes to " + Reasoning.show(w.is) + ", not " + Reasoning.show(w.said)));
        }
        for (Consistency.Conflict c : Consistency.within(d)) {
            if (c.real) {
                out.add(new Defect(Kind.CONTRADICTION, c.said()));
            }
        }
        for (Consistency.Conflict c : Consistency.against(d, known)) {
            if (c.real) {
                out.add(new Defect(Kind.CONTRADICTION, c.said()));
            }
        }
        for (String s : Reasoning.overreach(d)) {
            out.add(new Defect(Kind.CAUSAL, "“" + s + "” claims a cause from a link"));
        }
        if ((label == Calibration.Label.LOW || label == Calibration.Label.UNVERIFIED) && CERTAIN.matcher(d).find()) {
            out.add(new Defect(Kind.OVERCONFIDENT, "it sounds certain on weak support"));
        }
        for (String c : plan.constraints) {
            if (c.equals("one sentence") && ai.ibiko.ibiko1.formulas.Truth.sentences(d).size() > 1) {
                out.add(new Defect(Kind.CONSTRAINT, "asked for one sentence"));
            } else if (c.startsWith("at most ")) {
                int most = Integer.parseInt(c.replaceAll("\\D", ""));
                if (d.trim().split("\\s+").length > most) {
                    out.add(new Defect(Kind.CONSTRAINT, "asked for " + c));
                }
            }
        }
        if (plan.complexity == Complexity.SIMPLE && d.length() > 1500) {
            out.add(new Defect(Kind.LENGTH, "long for a simple question"));
        }
        return out;
    }

    private static Set<String> content(String t) {
        Set<String> out = new HashSet<String>();
        for (String w : t.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (w.length() > 2 && !SMALL.contains(w)) {
                out.add(w.replaceAll("(?:ies)$", "y").replaceAll("(?<=[a-z]{3})s$", ""));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ M6 repair, M7 the passes

    /** M6: each material defect put right on its own, without a model: the rest of the draft is left as it is. */
    public static String repair(String draft, List<Defect> defects, Plan plan) {
        String d = draft;
        List<String> notes = new ArrayList<String>();
        for (Defect f : defects) {
            switch (f.kind) {
                case ARITHMETIC:
                    d = Reasoning.repairWorking(d);
                    break;
                case OVERCONFIDENT:
                    d = d.replaceAll("(?i)\\b(definitely|certainly|undoubtedly|for sure|absolutely)\\b", "probably")
                            .replaceAll("(?i)\\bwithout (?:a )?doubt\\b", "most likely").replaceAll("(?i)\\bguaranteed\\b", "likely")
                            .replaceAll("(?i)\\b100%\\b", "very likely").replaceAll("(?i)\\bproven fact\\b", "widely held view")
                            .replaceAll("(?i)\\b(?:there is no question|it is certain)\\b", "it seems");
                    break;
                case CAUSAL:
                    notes.add("That shows a link, not that one causes the other.");
                    break;
                case CONTRADICTION:
                    notes.add("Note: " + f.detail + ".");
                    break;
                case CONSTRAINT:
                    if (f.detail.equals("asked for one sentence")) {
                        List<String> s = ai.ibiko.ibiko1.formulas.Truth.sentences(d);
                        d = s.isEmpty() ? d : s.get(0).trim();
                    } else if (f.detail.startsWith("asked for at most ")) {
                        int most = Integer.parseInt(f.detail.replaceAll("\\D", ""));
                        String[] w = d.trim().split("\\s+");
                        d = String.join(" ", java.util.Arrays.copyOf(w, Math.min(w.length, most))).replaceAll("[,;:]$", "") + "…";
                    }
                    break;
                default:
                    break; // relevance, length and style are not put right by rule
            }
        }
        for (String n : notes) {
            if (!d.contains(n)) {
                d = d.trim() + "\n\n" + n;
            }
        }
        return d;
    }

    /** What the passes did: the answer, the passes run, what is left (said, not hidden), and a line a pass. */
    public static final class Outcome {
        public final String text;
        public final int passes;
        public final List<Defect> left;
        public final List<String> log;

        Outcome(String text, int passes, List<Defect> left, List<String> log) {
            this.text = text;
            this.passes = passes;
            this.left = left;
            this.log = log;
        }

        /** The thinking step shown with the answer: what was checked, in a line. */
        public String step() {
            if (passes <= 1 && left.isEmpty() && log.size() <= 1) {
                return null;
            }
            return "Checked it in " + passes + (passes == 1 ? " pass" : " passes") + (log.size() > 1 ? ": " + log.get(log.size() - 1) : "");
        }
    }

    /**
     * M7: the passes. Pass 1 is the plan (made already); pass 2 checks the draft's working and facts; pass 3 critiques
     * it; a fourth only when the plan asks for it or a check failed. A repair is made only for a material defect, and
     * the passes stop when nothing material is left, a repair changes nothing, or the budget is spent. {@code improve}
     * is a better draft from a brain for what was found (null: repair by rule only).
     */
    public static Outcome think(Plan plan, String draft, List<String> known, Calibration.Label label, Function<List<Defect>, String> improve) {
        List<String> log = new ArrayList<String>();
        long t0 = System.nanoTime();
        log.add("understood: " + plan.wants + " (" + plan.complexity.name().toLowerCase(Locale.ROOT) + ", " + plan.risk.name().toLowerCase(Locale.ROOT) + " risk)");
        String text = draft;
        int pass = 1;
        List<Defect> found = new ArrayList<Defect>();
        boolean failed = false;
        // something to check (working with "=", sure words on weak support) is always checked once, even when simple
        boolean checkable = draft != null && (draft.contains("=") || label == Calibration.Label.LOW || label == Calibration.Label.UNVERIFIED);
        int budget = Math.max(plan.passes, checkable ? 2 : 1);
        while (pass < budget || failed && pass < 4) {
            pass++;
            found = review(plan, text, known, label);
            List<Defect> material = new ArrayList<Defect>();
            for (Defect f : found) {
                if (f.material()) {
                    material.add(f);
                }
            }
            if (material.isEmpty()) {
                log.add("pass " + pass + ": no defect found" + (found.isEmpty() ? "" : " (style only, left alone)"));
                break; // M7: nothing material: another pass would not change it
            }
            String better = improve == null ? null : improve.apply(material);
            String repaired = repair(better == null || better.trim().isEmpty() ? text : better, material, plan);
            failed = true;
            if (repaired.equals(text)) {
                log.add("pass " + pass + ": found " + material.size() + ", nothing more to change");
                break; // diminishing returns: say what stands
            }
            log.add("pass " + pass + ": put right " + kinds(material));
            text = repaired;
        }
        List<Defect> left = new ArrayList<Defect>();
        for (Defect f : review(plan, text, known, label)) {
            if (f.material()) {
                left.add(f);
            }
        }
        log.add(String.format(Locale.US, "%s in %.1f ms", left.isEmpty() ? "nothing material left" : left.size() + " disclosed",
                (System.nanoTime() - t0) / 1e6));
        return new Outcome(text, pass, left, log);
    }

    private static String kinds(List<Defect> ds) {
        Set<String> k = new java.util.LinkedHashSet<String>();
        for (Defect d : ds) {
            k.add(d.kind.name().toLowerCase(Locale.ROOT));
        }
        return String.join(", ", k);
    }
}
