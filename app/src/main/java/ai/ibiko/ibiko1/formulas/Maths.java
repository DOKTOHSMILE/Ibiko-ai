package ai.ibiko.ibiko1.formulas;

/**
 * All of Ibiko v1's exact maths behind one door: calculus, a problem in lettered parts, algebra, real-world problems
 * (money, rates, travel, percentages). Used three ways: Ibiko answers with it; any model calls it as a tool (§CA);
 * and before a question goes to a model (a chat, a discussion, deep thinking), its maths is worked out here first and
 * handed over with the question, so the model reasons over exact numbers instead of doing sums in its head.
 */
public final class Maths {
    private Maths() {
    }

    /** The worked answer for the maths in {@code text}, or null when it holds none Ibiko can work out. */
    public static String answer(String text) {
        if (text == null || text.trim().isEmpty() || text.length() > 8000) {
            return null;
        }
        try {
            String s = Calculus.reply(text);
            if (s == null) {
                s = Algebra.parts(text);
            }
            if (s == null) {
                s = Algebra.reply(text);
            }
            if (s == null) {
                s = WordProblems.reply(text);
            }
            return s;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * The question as it goes to a model: with Ibiko's exact working attached when it holds maths, so the model uses
     * those results (and still explains, discusses and checks them) rather than computing them itself.
     */
    public static String forModel(String question) {
        String work = answer(question);
        if (work == null) {
            return question;
        }
        return question + "\n\n[Worked out exactly by Ibiko's maths engine, offline. Use these results as correct; don't redo the"
                + " arithmetic yourself. Explain, discuss or build on them as the question needs:\n" + work + "]";
    }
}
