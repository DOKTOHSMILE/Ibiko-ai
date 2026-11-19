package ai.ibiko.agent;

/**
 * The person changed the model while a job was running. The new model is told, on every step of that job, that it took
 * the work over part-way: the work so far is real and goes on from where it stands, but the new model is free to
 * correct what was wrong. So it neither starts a new train of thought that scatters the progress, nor is bound by the
 * earlier model's limits. Pure Java.
 */
public final class Takeover {
    private Takeover() {
    }

    /** What the new model is told; {@code from} and {@code to} are the models' names as the header shows them. */
    public static String note(String from, String to) {
        return "\n\nYou are taking over this job part-way: the person switched the model from " + clip(from) + " to " + clip(to)
                + ". The work so far in the transcript is real and stands: the task, the plan, the steps done and their"
                + " results. Read it first and carry on from the next step that is not done. Do not restart, do not plan"
                + " afresh, do not redo finished steps, and keep the files and decisions already made. You are not bound by"
                + " the earlier model's thinking: where a step was wrong, or there is a clearly better way for what remains,"
                + " correct it and say why in one line, then go on.";
    }

    /** {@code system} with the takeover note, when there is one. */
    public static String with(String system, String takeover) {
        if (takeover == null || takeover.isEmpty()) {
            return system;
        }
        return (system == null ? "" : system) + takeover;
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.isEmpty() ? "another model" : t.length() > 60 ? t.substring(0, 60) : t;
    }
}
