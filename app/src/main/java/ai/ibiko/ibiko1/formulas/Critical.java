package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Critical reasoning: deep thinking that questions before it answers. Every request is read for what kind of question
 * it is and what it takes for granted; every answer says how sure it is and what would change it. Ibiko 1 runs it on
 * its own answers, and its instructions for this request go to every other brain with the request.
 *
 * <pre>
 * K1  kind            what is asked: a fact, a cause (why), a comparison, a judgement or advice (best, should,
 *                     better), a prediction (will), a definition, a how-to. Each kind needs a different kind of proof
 * K2  premise         what the question takes as given ("why is X …", "since X", "given that X", "now that X") is
 *                     checked before it is explained; a false premise is corrected, not argued from
 * K3  absolutes       all, every, always, never, no one, everyone, nothing, everything: one counter-example
 *                     disproves it, so the claim is weighed as "most" or "some" unless it is shown to hold for all
 * K4  two options     "A or B" for a judgement: both, neither, or a third way may be right; the choice is not
 *                     assumed to be only two
 * K5  cause           "causes", "because of", "leads to", "makes": two things happening together is not one
 *                     making the other; a cause needs a mechanism, the right order in time, and other causes ruled out
 * K6  who says        "everyone says", "experts say", "they say", "it is known": who says it is not evidence; what
 *                     was observed is
 * K7  criteria        best, better, good, right, should, worth: there is no answer until it is said what matters
 *                     (cost, time, safety, quality, the asker's goal); the criteria are named and the answer follows them
 * K8  future          will, going to, next year, predict: the future is not a fact; a forecast says what it rests
 *                     on and how it could be wrong
 * K9  how sure        the words said match how the answer was reached: you confirmed it (certain), built in or
 *                     reasoned from built-in values (sure), two sources agree (very likely), one source (likely),
 *                     a model's memory or a learning session not checked (unchecked); never more certain than that
 * K10 other side      a judgement gives the strongest case for the other answer too, and why it loses (or doesn't)
 * K11 second route    an answer that can be reached two ways is: if they disagree, both are said and it is less sure
 * K12 what would      a conclusion says what evidence would change it (a falsifier), so it can be tested
 * </pre>
 *
 * Pure text rules, no model: the same reading for every request, quick enough for every message.
 */
public final class Critical {
    private Critical() {
    }

    /** K1. */
    public enum Kind { FACT, CAUSE, COMPARISON, JUDGEMENT, PREDICTION, DEFINITION, HOWTO, OTHER }

    /** One thing found in a request, with the rule that found it and what to do about it. */
    public static final class Finding {
        public final String rule;
        /** For the person, in one plain line (said under an answer). */
        public final String note;
        /** For a brain, as an instruction. */
        public final String instruction;

        Finding(String rule, String note, String instruction) {
            this.rule = rule;
            this.note = note;
            this.instruction = instruction;
        }
    }

    /** A request as critical reasoning reads it. */
    public static final class Reading {
        public final Kind kind;
        /** K2: what the question takes as given, or null. */
        public final String premise;
        public final List<Finding> findings;

        Reading(Kind kind, String premise, List<Finding> findings) {
            this.kind = kind;
            this.premise = premise;
            this.findings = findings;
        }

        public boolean has(String rule) {
            for (Finding f : findings) {
                if (f.rule.equals(rule)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final Pattern ABSOLUTE = Pattern.compile("\\b(all|every|always|never|no one|nobody|everyone|everybody|nothing"
            + "|everything|none|any time|forever|impossible|guaranteed)\\b");
    private static final Pattern CAUSAL = Pattern.compile("\\b(causes?|caused|because of|leads? to|led to|makes? (people|you|it|them)"
            + "|results? in|due to|the reason (why|for)|is why)\\b");
    private static final Pattern WHO_SAYS = Pattern.compile("\\b(everyone says|everybody says|people say|they say|experts say"
            + "|scientists say|doctors say|it is known|it's known|studies show|research shows|i heard|i read that|most people think)\\b");
    private static final Pattern VALUE = Pattern.compile("\\b(best|better|worse|worst|good|bad|right|wrong|should|ought|worth"
            + "|recommend|ideal|smart|wise|which (one )?should)\\b");
    private static final Pattern FUTURE = Pattern.compile("\\b(will|going to|gonna|next (year|month|week|decade)|in the future"
            + "|predict|forecast|by 20\\d\\d|future of)\\b");
    private static final Pattern PREMISE = Pattern.compile("^(?:why|how come)\\s+(?:is|are|does|do|did|was|were|has|have|can|can't"
            + "|cannot|don't|doesn't)\\s+(.+?)\\??$|\\b(?:since|given that|now that|considering that|as we know)\\s+([^,?.]+)");

    /** K1–K8: reads a request. */
    public static Reading read(String request) {
        String q = request == null ? "" : request.toLowerCase(Locale.US).replace('’', '\'').trim();
        List<Finding> found = new ArrayList<Finding>();
        Kind kind = kind(q);
        String premise = null;
        Matcher p = PREMISE.matcher(q);
        if (p.find()) {
            premise = (p.group(1) != null ? p.group(1) : p.group(2)).trim();
            found.add(new Finding("K2", "Your question takes it as given that " + premise + "; I checked that first.",
                    "The question takes as given that \"" + premise + "\". Check that premise before answering; if it is"
                            + " false or doubtful, say so first and answer what is true instead of arguing from it."));
        }
        if (ABSOLUTE.matcher(q).find() && kind != Kind.HOWTO) {
            found.add(new Finding("K3", "Words like \"all\", \"always\" or \"never\" are strong: one exception would disprove them.",
                    "The request uses an absolute (all, always, never, everyone…). Do not accept or state an absolute"
                            + " unless it holds for every case; give the exceptions or say \"most\" or \"some\"."));
        }
        if ((kind == Kind.JUDGEMENT || kind == Kind.COMPARISON) && q.matches(".*\\b\\w+\\b,? or \\b\\w+.*")) {
            found.add(new Finding("K4", "These may not be the only two choices: both, neither or a third option can be right.",
                    "Two options are offered. Consider whether both, neither, or a third option is the better answer"
                            + " before choosing between them."));
        }
        if (CAUSAL.matcher(q).find()) {
            found.add(new Finding("K5", "Two things happening together doesn't prove one causes the other.",
                    "This is about a cause. Separate correlation from causation: name the mechanism, check the order in"
                            + " time, and rule out other causes before claiming one; say how strong the evidence for it is."));
        }
        if (WHO_SAYS.matcher(q).find()) {
            found.add(new Finding("K6", "Who says something isn't evidence; what was actually observed is.",
                    "The request leans on who says it (experts, everyone, studies). Weigh the evidence itself: what was"
                            + " observed, how, and how much; say if the popular view is wrong or uncertain."));
        }
        if (kind == Kind.JUDGEMENT) {
            found.add(new Finding("K7", "\"Best\" depends on what matters to you; I've said what I judged it by.",
                    "This asks for a judgement. Name the criteria that decide it (cost, time, safety, quality, the user's"
                            + " goal), judge by them openly, give the strongest case for the other answer (K10), and say what"
                            + " would change the recommendation."));
        }
        if (kind == Kind.PREDICTION) {
            found.add(new Finding("K8", "The future isn't a fact: this is a forecast, and it could be wrong.",
                    "This asks about the future. Give a forecast, not a fact: what it rests on, the likely range, and the"
                            + " main way it could turn out differently."));
        }
        if (constrained(q)) {
            found.add(new Finding("K13", "You already named a limit. The answer has to fit inside it, not around it.",
                    "The asker stated a constraint (money, time, a must, or a must-not). Treat it as a hard limit:"
                            + " an option that breaks it loses, even if it looks better on other measures."));
        }
        if (kind == Kind.JUDGEMENT && q.matches(".*\\b(quit|resign|buy|sell|delete|marry|move|permanent|irreversible)\\b.*")) {
            found.add(new Finding("K14", "One of these choices is hard to undo. That counts, especially if you are unsure.",
                    "At least one option is costly to reverse. Prefer the reversible option unless a stated constraint"
                            + " makes the permanent one clearly better, and say what would make the undo-cost worth paying."));
        }
        return new Reading(kind, premise, found);
    }

    /** K1: the kind of question. */
    public static Kind kind(String q) {
        String s = q == null ? "" : q.toLowerCase(Locale.US).trim();
        if (FUTURE.matcher(s).find() && s.matches("^(will|what will|when will|is .* going to|are .* going to|who will|how will|predict).*")) {
            return Kind.PREDICTION;
        }
        if (s.matches("^(why|how come)\\b.*") || CAUSAL.matcher(s).find() && s.endsWith("?")) {
            return Kind.CAUSE;
        }
        if (VALUE.matcher(s).find() && s.matches("^(what|which|who|should|is it|would|do you think|is .* (better|worth)|am i).*")) {
            return Kind.JUDGEMENT;
        }
        if (s.matches(".*\\b(bigger|smaller|more|less|higher|lower|heavier|lighter|compare|versus|vs|difference between)\\b.*")) {
            return Kind.COMPARISON;
        }
        if (s.matches("^(what is|what's|what are|define|meaning of|what does .* mean)\\b.*") && s.split("\\s+").length <= 6) {
            return Kind.DEFINITION;
        }
        if (s.matches("^(how (do|can|to|should) i|how to|steps to)\\b.*")) {
            return Kind.HOWTO;
        }
        if (s.matches("^(what|who|when|where|which|how many|how much|is|are|does|do|did|was|were)\\b.*")) {
            return Kind.FACT;
        }
        if (s.matches(".*\\b(torn between|stuck between|help me decide|help me choose|can'?t decide|cannot decide"
                + "|whether (i|we|to))\\b.*")) {
            return Kind.JUDGEMENT;
        }
        return Kind.OTHER;
    }

    /** K9: the words for how sure an answer is, from how it was reached. */
    public enum Ground { CONFIRMED, BUILT_IN, REASONED, TWO_SOURCES, ONE_SOURCE, UNCHECKED }

    public static String sure(Ground g) {
        switch (g) {
            case CONFIRMED:
                return "Certain: you confirmed this.";
            case BUILT_IN:
                return "Sure: from my built-in reference.";
            case REASONED:
                return "Sure: worked out from my built-in values, shown above.";
            case TWO_SOURCES:
                return "Very likely: two sources agree.";
            case ONE_SOURCE:
                return "Likely: from one source; worth checking if it matters.";
            default:
                return "Not checked yet: treat it as a lead, not a fact.";
        }
    }

    /**
     * What the person sees under an answer: at most two of the findings that change how to read it (K2–K8), one line
     * each. Empty when none apply.
     */
    public static String notes(Reading r) {
        if (r == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Finding f : r.findings) {
            if ("K2".equals(f.rule) || "K7".equals(f.rule)) {
                continue; // K2 is answered in the reply itself; K7 goes with a judgement's own reply
            }
            if (n++ == 2) {
                break;
            }
            b.append(b.length() == 0 ? "" : "\n").append(f.note);
        }
        return b.toString();
    }

    /** For any brain, with the request: deep, critical thinking for this request, in a few sentences. */
    public static String brief(Reading r) {
        StringBuilder b = new StringBuilder(" Think critically before you answer: the question is a ")
                .append(r.kind.name().toLowerCase(Locale.US)).append(" question, so ").append(proof(r.kind)).append(".");
        for (Finding f : r.findings) {
            b.append(' ').append(f.instruction);
        }
        b.append(" State how sure you are in one short clause, matching the evidence (K9): never more certain than it allows."
                + " Where it helps, say what would change your answer (K12). Do not write out this checklist.");
        return b.toString();
    }

    /** K1: the proof each kind of question needs. */
    static String proof(Kind k) {
        switch (k) {
            case FACT:
                return "answer from a reliable source and say which";
            case CAUSE:
                return "give the mechanism and the evidence for it, not just a story that fits";
            case COMPARISON:
                return "compare on the same measure, with the values";
            case JUDGEMENT:
                return "name the criteria, judge by them, and give the other side its best case";
            case PREDICTION:
                return "forecast from evidence and give the range and the risk";
            case DEFINITION:
                return "define it precisely, with an example";
            case HOWTO:
                return "give steps in the safe order, with what could go wrong";
            default:
                return "be exact about what is known and what isn't";
        }
    }

    /**
     * K7 and K10, for Ibiko 1 itself when no fact answers a judgement: the way to decide, laid out from the question's
     * own options, instead of an invented verdict.
     */
    public static String deliberate(String request) {
        Verdict v = decide(request);
        StringBuilder b = new StringBuilder("I can't judge that from facts alone, so here is how to decide it soundly:\n");
        b.append("This is a ").append(v.domain).append(" choice. ");
        if (v.constraints.isEmpty()) {
            b.append("You didn't give a limit, so I won't pretend one.\n");
        } else {
            b.append("Limits you already set: ").append(String.join("; ", v.constraints)).append(".\n");
        }
        b.append("1. Name what matters most to you (cost, time, safety, quality, what it's for); the answer follows from that.\n");
        if (v.options.size() >= 2) {
            for (String o : v.options) {
                b.append("- ").append(cap(o)).append(": what would make it the right choice? What would make it the wrong one?\n");
            }
            b.append("2. Check whether both, neither or a third option fits better: the choice may not be only these.\n");
        } else {
            b.append("2. Check whether both, neither or a third option fits better: list the real options, including doing nothing.\n");
        }
        b.append("3. Give the best case for the option you lean against; if it wins, change your mind.\n");
        if (v.lean == null) {
            b.append("Lean: none. ").append(v.because).append(" Confidence ").append(v.confidence)
                    .append("/100, because a missing fact is not a tie I get to break.\n");
        } else {
            b.append("Lean: ").append(v.lean).append(". ").append(v.because).append(" Confidence ").append(v.confidence)
                    .append("/100, and only from what you said, not from the world.\n");
        }
        b.append("4. Decide what result would show the choice was wrong, and check it after. What would flip it: ")
                .append(v.flip).append("\n");
        b.append("Tell me what matters most and I'll weigh it with you, or pick a model in the brain menu for a fuller opinion.");
        return b.toString();
    }

    /** A decision for the asker to make: options to choose between, or "should I …", "is it worth …". */
    public static boolean decision(String request) {
        String q = request == null ? "" : request.trim().replaceAll("[?.!]+$", "");
        String s = q.toLowerCase(Locale.US).replace('’', '\'');
        if (s.matches(".*\\b(help me (decide|choose)|torn between|stuck between|can'?t decide|cannot decide)\\b.*")) {
            return true;
        }
        return kind(q) == Kind.JUDGEMENT && (options(q).size() >= 2
                || s.matches("^(should (i|we)|is it worth|would it be (better|wise)|am i better off)\\b.*"));
    }

    /** The options a question offers ("Lagos or Abuja", "rent, buy or wait", "between staying and leaving"). */
    static List<String> options(String q) {
        List<String> out = new ArrayList<String>();
        String raw = q == null ? "" : q.trim().replaceAll("[?.!]+$", "");
        Matcher between = Pattern.compile("(?i)\\bbetween\\s+([\\w' -]{2,40}?)\\s+and\\s+([\\w' -]{2,40})").matcher(raw);
        if (between.find()) {
            addOption(out, between.group(1));
            addOption(out, between.group(2));
            return out;
        }
        Matcher listed = Pattern.compile("(?i)(?:between |^(?:should i |is it better to |which is better[,:]? |what is better[,:]? )?)"
                + "([\\w' -]{2,40}?)(?:,\\s*([\\w' -]{2,40}?))?,?\\s+or\\s+([\\w' -]{2,40})$").matcher(raw);
        if (listed.find()) {
            for (int i = 1; i <= 3; i++) {
                addOption(out, listed.group(i));
            }
            if (out.size() >= 2) {
                return out;
            }
            out.clear();
        }
        Matcher pair = Pattern.compile("(?i)\\b([a-z][\\w']{1,24})\\s+or\\s+([a-z][\\w']{1,24})\\b").matcher(raw);
        if (pair.find()) {
            addOption(out, pair.group(1));
            addOption(out, pair.group(2));
        }
        return out;
    }

    private static void addOption(List<String> out, String o) {
        if (o == null) {
            return;
        }
        o = o.replaceAll("(?i)^(should i|is it better to|which is better|what is better|is|to|the|a|an)\\s+", "").trim();
        o = o.replaceAll("(?i)^(should i|is it better to|which is better|what is better|is|to|the|a|an)\\s+", "").trim();
        if (o.isEmpty() || o.matches("(?i)or|and|not|than|versus|vs")) {
            return;
        }
        out.add(o);
    }

    /**
     * A decision, weighed only from what the asker said. No outside fact is invented: a city, a language or a job is
     * not declared the winner unless their own words separate the options. {@code lean} is null when they don't.
     */
    public static final class Verdict {
        public final String domain;
        public final List<String> options;
        public final List<String> constraints;
        /** The option their words support, or null when nothing they said separates the options. */
        public final String lean;
        public final String because;
        /** The one change that would make the lean wrong, or the one fact that would create a lean. */
        public final String flip;
        /** 0-100. Never high: this is their words, not the world. */
        public final int confidence;

        Verdict(String domain, List<String> options, List<String> constraints, String lean, String because, String flip,
                int confidence) {
            this.domain = domain;
            this.options = options;
            this.constraints = constraints;
            this.lean = lean;
            this.because = because;
            this.flip = flip;
            this.confidence = confidence;
        }
    }

    /**
     * K7, K10, K13, K14. Scores each option against the limits and the uncertainty the asker actually stated.
     * A hard limit (can't afford) outranks a soft preference. A short stay or "I'm not sure" pushes toward
     * the option that can be undone. If the scores do not separate, there is no lean.
     */
    public static Verdict decide(String request) {
        String q = request == null ? "" : request.trim();
        String s = q.toLowerCase(Locale.US).replace('’', '\'');
        List<String> options = options(q);
        String domain = domain(s, options);
        List<String> limits = limits(s);
        boolean unsure = s.matches(".*\\b(not sure|unsure|undecided|i don'?t know|i do not know|torn|stuck)\\b.*");
        boolean shortStay = s.matches(".*\\b(in a year|in a month|few months|might leave|temporary|short term"
                + "|not for long|a year or|only (a|one) year|for now)\\b.*");
        boolean tightMoney = s.matches(".*\\b(can'?t afford|cannot afford|small deposit|no deposit|no savings"
                + "|tight budget|little money|can'?t pay|cannot pay)\\b.*");
        String rather = rather(s);

        int n = options.size();
        int[] score = new int[n];
        List<String> reasons = new ArrayList<String>();
        for (int i = 0; i < n; i++) {
            String o = options.get(i).toLowerCase(Locale.US);
            boolean hard = false;
            if (tightMoney && costly(o)) {
                score[i] -= 4;
                hard = true;
                reasons.add(cap(options.get(i)) + " loses: you said the money isn't there");
            }
            if (shortStay && sticky(o)) {
                score[i] -= 3;
                reasons.add(cap(options.get(i)) + " loses: you said the time is short, and this one is hard to undo");
            }
            if (shortStay && reversible(o)) {
                score[i] += 2;
                reasons.add(cap(options.get(i)) + " fits a short stay: you can leave it");
            }
            if (unsure && sticky(o) && !hard) {
                score[i] -= 2;
                reasons.add(cap(options.get(i)) + " loses while you are unsure, because it is hard to undo");
            }
            if (unsure && reversible(o)) {
                score[i] += 1;
            }
            if (rather != null && o.contains(rather)) {
                score[i] += hard ? 0 : 2;
                if (!hard) {
                    reasons.add("You said you lean toward " + options.get(i));
                }
            }
        }
        String lean = null;
        int confidence = 20;
        String because = "Nothing you said separates "
                + (n >= 2 ? options.get(0) + " from " + options.get(n - 1) : "the options") + ".";
        String flip = decisive(domain);
        if (n >= 2) {
            int best = 0;
            for (int i = 1; i < n; i++) {
                if (score[i] > score[best]) {
                    best = i;
                }
            }
            int secondScore = Integer.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                if (i != best && score[i] > secondScore) {
                    secondScore = score[i];
                }
            }
            int margin = score[best] - secondScore;
            boolean blocked = false;
            for (int i = 0; i < n; i++) {
                if (score[i] <= -4) {
                    blocked = true;
                }
            }
            if (margin >= 2 && (score[best] > 0 || (blocked && score[best] >= 0))) {
                lean = options.get(best);
                confidence = Math.min(78, 40 + margin * 8);
                because = joinReasons(reasons, options.get(best));
                flip = flipOf(lean, shortStay, tightMoney, unsure);
            }
        }
        return new Verdict(domain, options, limits, lean, because, flip, confidence);
    }

    private static boolean constrained(String q) {
        return q.matches(".*\\b(can'?t afford|cannot afford|only have|budget|must not|as long as|i have to|i need to"
                + "|small deposit|no savings)\\b.*");
    }

    private static String domain(String s, List<String> options) {
        String blob = s + " " + String.join(" ", options).toLowerCase(Locale.US);
        if (blob.matches(".*\\b(rent|renting|mortgage|landlord|apartment|house|flat|deposit)\\b.*")) {
            return "housing";
        }
        if (blob.matches(".*\\b(quit|resign|salary|job|career|boss)\\b.*")) {
            return "work";
        }
        if (blob.matches(".*\\b(python|java|javascript|typescript|language|programming)\\b.*")) {
            return "learning";
        }
        if (blob.matches(".*\\b(city|relocate|lagos|abuja|london|accra|nairobi)\\b.*")) {
            return "place";
        }
        return "general";
    }

    /** Clauses that limit the choice, in the asker's words. The question itself is not a limit. */
    private static List<String> limits(String s) {
        List<String> out = new ArrayList<String>();
        for (String part : s.split("[.;]\\s*|\\s+\\band\\b\\s+")) {
            String p = part.trim();
            if (p.length() < 8 || p.length() > 90) {
                continue;
            }
            if (p.matches(".*\\b(can'?t|cannot|only|budget|deposit|afford|must|might|prefer|already|kids|children|family|savings)\\b.*")
                    && !p.matches("^(should|which|what|is it|would|do you).*")) {
                out.add(p);
            }
            if (out.size() == 4) {
                break;
            }
        }
        return out;
    }

    /** "I prefer X" / "I'd rather X", lowercased, or null. */
    private static String rather(String s) {
        Matcher m = Pattern.compile("\\b(?:i prefer|i'?d rather|i would rather|leaning toward|leaning towards)\\s+([a-z][\\w']{1,24})").matcher(s);
        return m.find() ? m.group(1) : null;
    }

    private static boolean costly(String option) {
        return option.matches(".*\\b(buy|buying|purchase|own|mortgage)\\b.*");
    }

    private static boolean sticky(String option) {
        return option.matches(".*\\b(buy|buying|quit|quitting|resign|leave|leaving|sell|selling|delete|marry|move|moving|commit)\\b.*");
    }

    private static boolean reversible(String option) {
        return option.matches(".*\\b(rent|renting|stay|staying|wait|waiting|keep|keeping|pause|learn|learning)\\b.*");
    }

    private static String decisive(String domain) {
        if ("housing".equals(domain)) {
            return "how many years you will actually stay, and whether the deposit is money you can lock up.";
        }
        if ("work".equals(domain)) {
            return "whether you can live without the pay, and whether leaving can be undone.";
        }
        if ("learning".equals(domain)) {
            return "what you will build with it in the next month.";
        }
        if ("place".equals(domain)) {
            return "whether you are choosing for work, for cost, or for the people you need to be near.";
        }
        return "which single measure matters most: cost, time, or how hard it is to undo.";
    }

    private static String flipOf(String lean, boolean shortStay, boolean tightMoney, boolean unsure) {
        if (tightMoney || shortStay) {
            return "a longer stay and money you can afford to lock up would flip this away from " + lean + ".";
        }
        if (unsure) {
            return "being sure, with a reason the other option is worth the undo-cost, would flip it.";
        }
        return "the other option winning on the one measure you actually care about.";
    }

    private static String joinReasons(List<String> reasons, String lean) {
        String needle = lean.toLowerCase(Locale.US);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (String r : reasons) {
            if (!r.toLowerCase(Locale.US).contains(needle)) {
                continue;
            }
            if (n++ == 3) {
                break;
            }
            b.append(b.length() == 0 ? "" : " ").append(r).append(r.endsWith(".") ? "" : ".");
        }
        if (b.length() == 0) {
            int extra = 0;
            for (String r : reasons) {
                if (extra++ == 3) {
                    break;
                }
                b.append(b.length() == 0 ? "" : " ").append(r).append(r.endsWith(".") ? "" : ".");
            }
        }
        if (b.length() == 0) {
            return cap(lean) + " is the one your words support.";
        }
        return b.toString();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
