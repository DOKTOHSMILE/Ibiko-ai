package ai.ibiko.ibiko1.formulas;

import java.util.Locale;

/**
 * What a sentence implies, not only what it says. Knowing the words is knowledge. Understanding is the limit,
 * the case, and what would undo it. Pure Java. No model.
 *
 * <pre>
 * U1  read     the sentence as a claim, a limit, a case, a possibility, or a recommendation
 * U2  assumes  what it takes as given
 * U3  follows  what is true of the situation if the sentence is accepted
 * U4  silent   what it does not say
 * U5  undone   what would make it no longer the thing to go on
 * </pre>
 */
public final class Understanding {
    /** One sentence, read for what follows from it. */
    public static final class Implication {
        public final String sentence, assumes, follows, silent, undone;

        Implication(String sentence, String assumes, String follows, String silent, String undone) {
            this.sentence = sentence;
            this.assumes = assumes;
            this.follows = follows;
            this.silent = silent;
            this.undone = undone;
        }

        /** One line a later answer can use, without the original wording repeated as the answer. */
        public String line() {
            return "Implies: " + follows + " Does not say: " + silent;
        }
    }

    private Understanding() {
    }

    /** U1–U5. An empty sentence has nothing to imply. */
    public static Implication of(String sentence) {
        String s = sentence == null ? "" : sentence.trim();
        if (s.isEmpty()) {
            return new Implication("", "Nothing was said.", "Nothing follows.", "There is no claim.", "Say what is meant.");
        }
        String low = s.toLowerCase(Locale.US);
        String assumes = "It is being offered as something to go on.";
        String follows = "What it says is to be treated as given, until something contradicts it.";
        String silent = "It does not say the opposite, and it does not give a reason unless the words do.";
        String undone = "A fact that contradicts it, or the person taking it back.";
        if (low.matches("(?s).*\\b(can't|cannot|can not|don't|do not|won't|must not|unable)\\b.*")) {
            assumes = "The limit is one they are stating, not a guess added later.";
            follows = "A limit: what it refuses is not available on the terms they stated.";
            silent = "It does not say the refused thing is bad in every other case.";
            undone = "The limit being lifted (they can afford it, or the refusal is withdrawn).";
        }
        if (low.matches("(?s).*\\b(might|may|could|perhaps|maybe)\\b.*")) {
            follows = follows + " It is possible, not settled.";
            silent = "It does not say it will happen.";
            undone = "A later word that settles it one way or the other.";
        }
        if (low.matches("(?s).*\\b(if|unless)\\b.*")) {
            follows = "It applies only in the case it names, not in every case.";
            silent = "It does not say what happens outside that case.";
            undone = "The case it names not holding.";
        }
        if (low.matches("(?s).*\\b(because|so that|since)\\b.*")) {
            assumes = "The reason it gives is part of the claim, not decoration.";
            follows = "If that reason is wrong, the claim does not stand as stated.";
            undone = "The reason being false.";
        }
        if (low.matches("(?s).*\\b(should|ought)\\b.*")) {
            follows = "A recommendation, not a fact that it is already so.";
            silent = "It does not say the recommended thing has already been done.";
        }
        return new Implication(s, assumes, follows, silent, undone);
    }
}
