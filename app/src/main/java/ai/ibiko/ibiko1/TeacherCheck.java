package ai.ibiko.ibiko1;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * An answer from another model (an API model the pilot set up), checked by Ibiko v1 before it is shown or kept, the way
 * a careful student checks a teacher's answer against the books:
 *
 * <pre>
 * C1  against   a fact Ibiko holds sure (a date, a capital, a population: {@link Facts#conflicts}), or what the pilot
 *               taught it ({@link Knowledge#check}), says otherwise: CONFLICT; Ibiko keeps its own and asks which is right
 * C2  agrees    most of what it says is in Ibiko's own article on the subject, or matches what the pilot taught: CONFIRMED,
 *               kept as known
 * C3  new       neither: NEW, kept as unsure until the pilot says it's right
 * </pre>
 */
final class TeacherCheck {
    private TeacherCheck() {
    }

    enum Verdict { CONFIRMED, NEW, CONFLICT }

    static final class Result {
        final Verdict verdict;
        /** What it goes against, in words (CONFLICT), or what it agreed with (CONFIRMED). */
        final String because;

        Result(Verdict verdict, String because) {
            this.verdict = verdict;
            this.because = because;
        }
    }

    private static final Set<String> SMALL = new HashSet<String>(java.util.Arrays.asList("the", "a", "an", "of", "and", "or", "to", "in", "on",
            "is", "are", "was", "were", "it", "its", "this", "that", "by", "for", "with", "as", "at", "from", "be", "which", "who", "has", "have",
            "had", "also", "about", "known", "called", "one", "most", "more", "many", "some", "there", "their", "they"));

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}0-9]+")) {
            if (w.length() > 2 && !SMALL.contains(w)) {
                out.add(w);
            }
        }
        return out;
    }

    /** C1–C3: the answer checked against what Ibiko holds. {@code article} is its own text on the subject, or null. */
    static Result check(String answer, String subject, Facts facts, Knowledge knowledge, String article) {
        // C1: against a fact it holds sure
        if (facts != null) {
            List<Facts.Conflict> against = facts.conflicts(answer, subject);
            if (!against.isEmpty()) {
                Facts.Conflict c = against.get(0);
                return new Result(Verdict.CONFLICT, c.held.subject + " " + c.held.relation + " " + c.held.object);
            }
        }
        List<String> sentences = new ArrayList<String>();
        for (String s : answer.split("(?<=[.!?])\\s+")) {
            if (s.trim().length() > 10) {
                sentences.add(s.trim());
            }
        }
        boolean taught = false;
        if (knowledge != null) {
            for (String s : sentences) {
                ai.ibiko.ibiko1.formulas.Truth.Checked c = knowledge.check(s);
                if (c.verdict == ai.ibiko.ibiko1.formulas.Truth.Verdict.FALSE) {
                    return new Result(Verdict.CONFLICT, c.correction == null ? "what you told me" : c.correction.replaceAll("[.]+$", ""));
                }
                taught |= c.verdict == ai.ibiko.ibiko1.formulas.Truth.Verdict.TRUE;
            }
        }
        if (taught) {
            return new Result(Verdict.CONFIRMED, "what you told me");
        }
        // C2: most of it is in its own article on the subject
        if (article != null && !sentences.isEmpty()) {
            Set<String> read = words(article);
            Set<String> about = subject == null ? new HashSet<String>() : words(subject);
            int backed = 0;
            for (String s : sentences) {
                Set<String> w = words(s);
                w.removeAll(about); // the subject's own name says nothing
                if (w.isEmpty()) {
                    continue;
                }
                int in = 0;
                for (String x : w) {
                    in += read.contains(x) ? 1 : 0;
                }
                backed += in * 10 >= w.size() * 6 ? 1 : 0;
            }
            if (backed * 2 > sentences.size()) {
                return new Result(Verdict.CONFIRMED, "my encyclopedia's article on " + subject);
            }
        }
        return new Result(Verdict.NEW, null);
    }
}
