package ai.ibiko.ibiko1.cognition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import ai.ibiko.ibiko1.formulas.Foresight;
import ai.ibiko.ibiko1.formulas.Truth;

/**
 * The cognitive pipeline every brain's answer goes through (IBIKO_COGNITIVE_LEARNING.md §4), whichever brain wrote it:
 * Ibiko v1, the phone's own model or an API model. The app owns it; the model only writes. Before: the plan (pass 1),
 * with the consequences of a high-impact action, goes to the brain with the request. After: the answer is checked
 * against what is held ({@link Truth}), its working and its consistency (passes 2 and 3), how sure it may be is worked
 * out and calibrated, and what was found is put right (Ibiko's own answers) or said in a note under the answer (a
 * model's, already shown). Each stage's outcome and time is logged, never the scratchwork. Pure Java.
 *
 * <pre>
 *   request → plan (Metacognition) + consequences (Foresight)        → the brain answers
 *   answer  → truth check (Truth) → working, consistency, cause (Reasoning, Consistency)
 *           → confidence (Calibration) → review and repair (Metacognition) → said
 *   rating  → calibration outcome; corrections and confirmations → learning records
 * </pre>
 */
public final class Cognition {
    private Cognition() {
    }

    /** Pass 1, for any brain with the request: the plan, and for a weighty action, what it could lead to. */
    public static String plan(String request) {
        if (request == null || request.trim().isEmpty()) {
            return "";
        }
        Metacognition.Plan p = Metacognition.plan(request);
        StringBuilder b = new StringBuilder(" ").append(p.brief());
        Foresight.Consequences c = Foresight.weigh(request);
        if (c != null) {
            b.append(" Weigh before advising: ").append(c.said().replace("\n", " "));
        }
        log("plan", p.complexity.name().toLowerCase(Locale.ROOT) + ", " + p.risk.name().toLowerCase(Locale.ROOT) + " risk, " + p.passes + " passes", 0);
        return b.toString();
    }

    /** What the review of one answer found. */
    public static final class Review {
        public final String text;
        public final String note;
        public final Calibration.Confidence confidence;
        public final Calibration.Label label;
        public final Metacognition.Outcome outcome;

        Review(String text, String note, Calibration.Confidence confidence, Calibration.Label label, Metacognition.Outcome outcome) {
            this.text = text;
            this.note = note;
            this.confidence = confidence;
            this.label = label;
            this.outcome = outcome;
        }
    }

    /**
     * Passes 2 and 3 for an answer already written. {@code checked} is the truth check of its sentences; {@code base} is
     * how reliable the brain that wrote it is (0.4 a model's memory, up to 0.95 for what the person confirmed);
     * {@code repair}: put right in the text (Ibiko's own, not yet shown), else said in a note (a model's, shown).
     */
    public static Review review(String question, String answer, List<Truth.Checked> checked, List<String> known, double base, Calibration calibration,
            boolean repair) {
        long t0 = System.nanoTime();
        String a = answer == null ? "" : answer;
        int yes = 0, no = 0;
        if (checked != null) {
            for (Truth.Checked c : checked) {
                yes += c.verdict == Truth.Verdict.TRUE ? 1 : 0;
                no += c.verdict == Truth.Verdict.FALSE ? 1 : 0;
            }
        }
        double evidence = yes + no == 0 ? base : (yes + 0.2 * base) / (yes + no + 0.2);
        Metacognition.Plan plan = Metacognition.plan(question);
        List<Metacognition.Defect> first = Metacognition.review(plan, a, known, null);
        int material = 0;
        for (Metacognition.Defect d : first) {
            material += d.material() ? 1 : 0;
        }
        double reasoning = Math.max(0.2, 1 - 0.25 * material);
        Evidence.Freshness f = Evidence.freshness(a, 0, System.currentTimeMillis());
        double fresh = f == Evidence.Freshness.TIME_SENSITIVE ? 0.6 : f == Evidence.Freshness.STALE ? 0.5 : 1;
        Calibration.Confidence conf = new Calibration.Confidence(evidence, reasoning, Math.max(base, yes > 0 ? 0.9 : 0), fresh);
        Calibration.Label label = calibration == null ? conf.label() : calibration.calibrated(conf);
        Metacognition.Outcome o = Metacognition.think(plan, a, known, label, null);
        String note = null;
        String text = a;
        if (repair) {
            text = o.text;
        } else if (!o.text.equals(a) || !o.left.isEmpty()) {
            note = noteFor(a, o, label);
        }
        log("review", (o.left.isEmpty() ? "clean" : o.left.size() + " left") + ", " + label.name().toLowerCase(Locale.ROOT) + ", " + o.passes + " passes",
                (System.nanoTime() - t0) / 1_000_000L);
        return new Review(text, note, conf, label, o);
    }

    /** What the review found in a model's answer, said under it in a few lines; null when there's nothing to say. */
    static String noteFor(String answer, Metacognition.Outcome o, Calibration.Label label) {
        List<String> lines = new ArrayList<String>();
        for (Reasoning.Wrong w : Reasoning.checkWorking(answer)) {
            lines.add("Checking the working: “" + w.written + "” comes to " + Reasoning.show(w.is) + ", not " + Reasoning.show(w.said) + ".");
        }
        for (Metacognition.Defect d : o.left) {
            if (d.kind == Metacognition.Kind.CONTRADICTION) {
                lines.add("These disagree: " + d.detail + ".");
            } else if (d.kind == Metacognition.Kind.CAUSAL) {
                lines.add("A link isn't proof of a cause: " + d.detail.replaceAll(" claims a cause from a link$", "") + " needs a mechanism or a trial.");
            }
        }
        for (String s : Reasoning.overreach(answer)) {
            String l = "A link isn't proof of a cause: “" + s + "” needs a mechanism or a trial.";
            if (!lines.contains(l) && lines.stream().noneMatch(x -> x.contains(s))) {
                lines.add(l);
            }
        }
        if ((label == Calibration.Label.LOW || label == Calibration.Label.UNVERIFIED) && answer.matches("(?is).*\\b(?:definitely|certainly|guaranteed|100%|without (?:a )?doubt)\\b.*")) {
            lines.add("I couldn't confirm this from what I hold, so take it as likely, not certain.");
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    // ------------------------------------------------------------------ the audit log: stage, outcome, time

    private static final Deque<String> LOG = new ArrayDeque<String>();

    static synchronized void log(String stage, String outcome, long ms) {
        LOG.addLast(String.format(Locale.US, "%tT %s: %s (%d ms)", System.currentTimeMillis(), stage, outcome, ms));
        while (LOG.size() > 200) {
            LOG.removeFirst();
        }
    }

    /** The latest stage outcomes, newest last: what was decided and how long it took, not how. */
    public static synchronized List<String> log() {
        return new ArrayList<String>(LOG);
    }
}
