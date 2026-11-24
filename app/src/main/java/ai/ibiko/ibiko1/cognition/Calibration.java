package ai.ibiko.ibiko1.cognition;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.Map;

/**
 * Engine 5, confidence calibration (priority 5): how sure an answer may sound is worked out from what it rests on, one
 * dimension at a time, and checked against how such answers turned out. One strong dimension never hides a weak one:
 * the overall is held down by the weakest. Every rated answer (the person's 👍 or 👎, a correction) is counted against
 * the label it was given; a label that has been wrong too often is lowered for the next answers. Confidence is not
 * evidence: an answer with no evidence is UNVERIFIED however well reasoned. Kept in {@code brain/calibration.tsv}.
 *
 * <pre>
 * Q1  dimensions   evidence strength, reasoning validity, source reliability, freshness: each 0 to 1
 * Q2  overall      0.6 × the weakest + 0.4 × their mean
 * Q3  label        HIGH (overall ≥ 0.8, none under 0.6), MODERATE (≥ 0.55), LOW (≥ 0.3), else UNVERIFIED;
 *                  no evidence at all is always UNVERIFIED
 * Q4  outcomes     each rated answer counted under its label: right or wrong
 * Q5  calibrate    a label right less often than it promises (HIGH under 85%, MODERATE under 60%, after 10 ratings)
 *                  is lowered one step for the answers that follow
 * Q6  words        HIGH says it plainly; MODERATE "most likely"; LOW "I'm not sure, but"; UNVERIFIED "I couldn't check this"
 * </pre>
 */
public final class Calibration {
    public enum Label { HIGH, MODERATE, LOW, UNVERIFIED }

    /** Q1–Q3: the four dimensions of how well an answer is supported. */
    public static final class Confidence {
        public final double evidence, reasoning, reliability, freshness;

        public Confidence(double evidence, double reasoning, double reliability, double freshness) {
            this.evidence = clamp(evidence);
            this.reasoning = clamp(reasoning);
            this.reliability = clamp(reliability);
            this.freshness = clamp(freshness);
        }

        /** Q2: held down by the weakest dimension. */
        public double overall() {
            double min = Math.min(Math.min(evidence, reasoning), Math.min(reliability, freshness));
            double mean = (evidence + reasoning + reliability + freshness) / 4;
            return 0.6 * min + 0.4 * mean;
        }

        /** Q3: its label, before calibration. */
        public Label label() {
            if (evidence <= 0.05) {
                return Label.UNVERIFIED;
            }
            double o = overall();
            double min = Math.min(Math.min(evidence, reasoning), Math.min(reliability, freshness));
            if (o >= 0.8 && min >= 0.6) {
                return Label.HIGH;
            }
            if (o >= 0.55) {
                return Label.MODERATE;
            }
            return o >= 0.3 ? Label.LOW : Label.UNVERIFIED;
        }

        /** The weakest dimension, by name: what most needs checking. */
        public String weakest() {
            double min = Math.min(Math.min(evidence, reasoning), Math.min(reliability, freshness));
            return min == evidence ? "evidence" : min == reasoning ? "reasoning" : min == reliability ? "source reliability" : "freshness";
        }

        private static double clamp(double v) {
            return Math.max(0, Math.min(1, v));
        }
    }

    private final File file;
    private final Map<Label, int[]> outcomes = new EnumMap<Label, int[]>(Label.class); // {right, wrong}

    public Calibration(File file) {
        this.file = file;
        for (Label l : Label.values()) {
            outcomes.put(l, new int[2]);
        }
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                String[] f = line.split("\t");
                if (f.length == 3) {
                    try {
                        int[] o = outcomes.get(Label.valueOf(f[0]));
                        o[0] = Integer.parseInt(f[1]);
                        o[1] = Integer.parseInt(f[2]);
                    } catch (IllegalArgumentException ignored) {
                        // a line it doesn't know
                    }
                }
            }
        } catch (IOException ignored) {
            // starts fresh
        }
    }

    /** Q4: how an answer given under {@code label} turned out. */
    public synchronized void outcome(Label label, boolean right) {
        if (label == null) {
            return;
        }
        outcomes.get(label)[right ? 0 : 1]++;
        save();
    }

    /** How often answers under a label were right, or -1 before there are enough ratings. */
    public synchronized double accuracy(Label label) {
        int[] o = outcomes.get(label);
        int n = o[0] + o[1];
        return n < 10 ? -1 : o[0] / (double) n;
    }

    /** Q5: the label an answer gets, after what past answers under it showed. */
    public synchronized Label calibrated(Confidence c) {
        Label l = c.label();
        double acc = accuracy(l);
        if (l == Label.HIGH && acc >= 0 && acc < 0.85) {
            return Label.MODERATE;
        }
        if (l == Label.MODERATE && acc >= 0 && acc < 0.6) {
            return Label.LOW;
        }
        return l;
    }

    /** Q6: how an answer under a label begins, or "" for one said plainly. */
    public static String words(Label l) {
        switch (l) {
            case MODERATE: return "Most likely: ";
            case LOW: return "I'm not sure, but ";
            case UNVERIFIED: return "I couldn't check this: ";
            default: return "";
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Label, int[]> e : outcomes.entrySet()) {
            b.append(e.getKey().name()).append('\t').append(e.getValue()[0]).append('\t').append(e.getValue()[1]).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            Files.write(file.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // counted for this run
        }
    }
}
