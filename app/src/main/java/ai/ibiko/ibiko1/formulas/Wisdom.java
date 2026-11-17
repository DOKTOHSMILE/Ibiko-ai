package ai.ibiko.ibiko1.formulas;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ai.ibiko.ibiko1.Knowledge;

/**
 * Wisdom is the application of knowledge and understanding. Knowledge is the sentences already kept.
 * Understanding is what each of those sentences implies. Wisdom does not repeat the sentences: it says
 * what to do with them for this question. Pure Java. No model.
 *
 * <pre>
 * W1  know      sentences already stored that share this question's content words
 * W2  understand what each of those sentences implies ({@link Understanding})
 * W3  apply     the move that follows from both, for this question, not a copy of the wording
 * </pre>
 */
public final class Wisdom {
    /** Knowledge, what it implies, and the application of the two. */
    public static final class Applied {
        public final String known, implies, apply;

        Applied(String known, String implies, String apply) {
            this.known = known;
            this.implies = implies;
            this.apply = apply;
        }

        /** What a Think level hands to the model before it answers. */
        public String brief() {
            StringBuilder b = new StringBuilder();
            b.append("ibiko1 reasoning: apply knowledge through what it implies. Do not copy an earlier sentence as the answer.\n");
            if (!known.isEmpty()) {
                b.append("Known: ").append(known).append('\n');
            }
            b.append("Implies: ").append(implies).append('\n');
            b.append("Apply: ").append(apply).append('\n');
            b.append("Reason: state the implication directly; a limit stays a limit, a possibility is not treated as settled.");
            return b.toString();
        }
    }

    private Wisdom() {
    }

    /** W1–W3. {@code known} may be empty: then only what the question itself implies is applied. */
    public static Applied of(String question, List<String> known) {
        List<String> want = Knowledge.meaning(question);
        StringBuilder held = new StringBuilder();
        StringBuilder implies = new StringBuilder();
        int n = 0;
        for (String sentence : known == null ? Collections.<String>emptyList() : known) {
            if (sentence == null || sentence.trim().isEmpty()) {
                continue;
            }
            if (!want.isEmpty() && shared(want, Knowledge.meaning(sentence)) == 0) {
                continue;
            }
            Understanding.Implication u = Understanding.of(sentence);
            if (held.length() > 0) {
                held.append(' ');
            }
            held.append(u.sentence);
            if (implies.length() > 0) {
                implies.append(' ');
            }
            implies.append(u.follows);
            if (++n >= 3) {
                break;
            }
        }
        if (n == 0) {
            Understanding.Implication u = Understanding.of(question == null ? "" : question);
            return new Applied("", u.follows,
                    "Nothing stored mentions these details. Apply only what this question itself implies, and say so.");
        }
        return new Applied(held.toString().trim(), implies.toString().trim(),
                "Use those implications on this question. A limit they stated is kept. A possibility is not treated as settled."
                        + " The answer follows from that, not from repeating the stored words.");
    }

    private static int shared(List<String> want, List<String> have) {
        int n = 0;
        for (String w : want) {
            if (have.contains(w)) {
                n++;
            }
        }
        return n;
    }

    /** The sentences worth handing to {@link #of}, dropping blanks. */
    public static List<String> lines(String... sentences) {
        List<String> out = new ArrayList<String>();
        if (sentences == null) {
            return out;
        }
        for (String s : sentences) {
            if (s != null && !s.trim().isEmpty()) {
                out.add(s.trim());
            }
        }
        return out;
    }

    /** The ibiko1 reasoning module's own working brief, used by every chat level. */
    public static String brief(String question, List<String> known) {
        return of(question, known).brief();
    }
}
