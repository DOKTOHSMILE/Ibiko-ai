package ai.ibiko.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Think 2: every available API model thinks about the message on its own, at the same time, in private notes (the goal,
 * what matters, a plan in steps, the risks, its best answer). Then the strongest of the models that answered judges
 * all the notes and writes the one answer the person sees. Nothing of the round is shown: no steps, no model names.
 * Pure Java: the calls are behind {@link Ask}.
 */
public final class ThinkTwo {
    /** One model that can think: which provider, which model. */
    public static final class Thinker {
        public final int provider;
        public final String model;

        public Thinker(int provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        @Override
        public String toString() {
            return provider + ":" + model;
        }
    }

    /** One call to one model. Null when it failed or was stopped. */
    public interface Ask {
        String ask(Thinker t, String system, String prompt);
    }

    /** How many models think at once: more is slower and costs more, and adds little past this. */
    public static final int MOST = 6;
    /** How long the private round may take before the judge works with what came back. */
    static final long ROUND_MS = 4 * 60_000L;

    static final String NOTES = "You are one of several independent thinkers. Think the message through on your own,"
            + " carefully, as private notes (the person never sees them): the real goal, what is known and what is"
            + " uncertain, a plan in numbered steps, the risks or mistakes to avoid, and then your best answer. Be concrete."
            + " Under 300 words. No greeting. The assistant you think for can act through its app: read the files in its"
            + " workspace, run its coding agent on a repository, search the web, use connectors and post on the person's"
            + " accounts. So when the person asks for work, plan the work itself; never plan to ask them for permission"
            + " they already gave, or to say the files haven't been read.";

    static final String JUDGE = "Below are private notes from several independent thinkers on the person's message."
            + " You are the final judge. Weigh them: keep what is right, drop what is wrong or unsupported, settle each"
            + " disagreement by the stronger reasoning, and add what all of them missed. Then write ONE answer for the"
            + " person, in your own words, complete and well ordered. Never mention notes, thinkers, models or rounds."
            + " When the person asked for something to be done (work on code or files, a search, a post), do not answer"
            + " that you haven't done it or ask leave to start: begin your reply with the function code from your"
            + " instructions that starts it, alone on the first line.";

    private ThinkTwo() {
    }

    /** A real question (or a carry-on of real work), not a greeting or a thank-you. */
    public static boolean worth(String subject) {
        String t = subject == null ? "" : subject.trim();
        if (t.length() < 8) {
            return false;
        }
        return !t.toLowerCase(Locale.US).matches("(hi|hey|hello|thanks|thank you|ok|okay|yes|no|yo|good morning|good night)[.!]*");
    }

    /**
     * A model's strength, from its name: the size class first (large, mid, small), then a reasoning model, then the
     * newer version. Higher is stronger. Used only to choose the judge; every model's notes count the same.
     */
    public static double strength(String model) {
        String m = model == null ? "" : model.toLowerCase(Locale.US);
        ModelRing.Band band = ModelRing.band(m);
        double s = band == ModelRing.Band.LARGE ? 300 : band == ModelRing.Band.MID ? 200 : 100;
        if (m.matches(".*(opus|\\bo1\\b|\\bo3\\b|\\bo4\\b|reasoner|\\br1\\b|thinking|-pro\\b|pro-|\\bultra\\b).*")) {
            s += 40;
        }
        if (m.matches(".*(mini|nano|flash|haiku|lite|small|tiny).*")) {
            s -= 30;
        }
        java.util.regex.Matcher v = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(m);
        double best = 0;
        while (v.find()) {
            try {
                double n = Double.parseDouble(v.group(1));
                if (n < 20) {
                    best = Math.max(best, n); // a version ("4.5", "5", "2.5"), not a size in billions
                }
            } catch (NumberFormatException ignored) {
                // not a number
            }
        }
        return s + best;
    }

    /** The thinkers, strongest first; ties keep their order (the person's own choice first). */
    static List<Thinker> strongestFirst(List<Thinker> all) {
        List<Thinker> out = new ArrayList<Thinker>(all);
        out.sort((a, b) -> Double.compare(strength(b.model), strength(a.model)));
        return out;
    }

    /**
     * The round. {@code subject} is what to think about (for "continue", the plan under way, not the word). {@code
     * judgeSystem} is the persona the answer is written in (it may include the app's function codes). Null when no
     * model answered, so the caller asks once in the usual way.
     */
    public static String run(Ask ask, List<Thinker> thinkers, String subject, String context, String judgeSystem,
            BooleanSupplier stopped) {
        if (ask == null || thinkers == null || thinkers.isEmpty() || subject == null || subject.trim().isEmpty()) {
            return null;
        }
        List<Thinker> pool = thinkers.size() > MOST ? new ArrayList<Thinker>(thinkers.subList(0, MOST)) : thinkers;
        String base = (context == null || context.trim().isEmpty() ? "" : context.trim() + "\n\n") + subject.trim();
        final List<String> notes = new ArrayList<String>();
        final List<Thinker> answered = new ArrayList<Thinker>();
        ExecutorService at = Executors.newFixedThreadPool(pool.size());
        try {
            List<Future<String>> got = new ArrayList<Future<String>>();
            for (final Thinker t : pool) {
                got.add(at.submit(() -> clean(ask.ask(t, NOTES, base))));
            }
            long until = System.currentTimeMillis() + ROUND_MS;
            for (int i = 0; i < pool.size(); i++) {
                if (stopped != null && stopped.getAsBoolean()) {
                    return null;
                }
                String n;
                try {
                    n = got.get(i).get(Math.max(1, until - System.currentTimeMillis()), TimeUnit.MILLISECONDS);
                } catch (Exception slowOrFailed) {
                    got.get(i).cancel(true);
                    n = "";
                }
                if (n != null && !n.isEmpty()) {
                    notes.add(n);
                    answered.add(pool.get(i));
                }
            }
        } finally {
            at.shutdownNow();
        }
        if (notes.isEmpty() || stopped != null && stopped.getAsBoolean()) {
            return null;
        }
        StringBuilder all = new StringBuilder(base).append("\n\n");
        for (int i = 0; i < notes.size(); i++) {
            all.append("Notes ").append((char) ('A' + i)).append(":\n").append(notes.get(i)).append("\n\n");
        }
        String system = (judgeSystem == null || judgeSystem.trim().isEmpty() ? "" : judgeSystem.trim() + "\n\n") + JUDGE;
        for (Thinker judge : strongestFirst(answered)) { // the strongest that answered; the next if it fails
            if (stopped != null && stopped.getAsBoolean()) {
                return null;
            }
            String answer = clean(ask.ask(judge, system, all.toString().trim()));
            if (!answer.isEmpty()) {
                return answer;
            }
        }
        return null;
    }

    /**
     * What to think about. For a carry-on ("continue", "go on"), the work under way and where the last reply stopped,
     * so the round thinks about the plan, not about the word "continue".
     */
    public static String subject(String said, boolean carriesOn, String task, String lastReply) {
        String s = said == null ? "" : said.trim();
        if (!carriesOn) {
            return "The person says: " + s;
        }
        StringBuilder b = new StringBuilder("The person said “").append(s)
                .append("”: carry on with the work below, from where it stopped. Think about the next steps of that plan,")
                .append(" not about the word itself.");
        if (task != null && !task.trim().isEmpty()) {
            b.append("\n\nThe task: ").append(task.trim());
        }
        if (lastReply != null && !lastReply.trim().isEmpty()) {
            String r = lastReply.trim();
            b.append("\n\nWhere the last reply stopped:\n").append(r.length() > 4000 ? r.substring(r.length() - 4000) : r);
        }
        return b.toString();
    }

    private static String clean(String reply) {
        return reply == null ? "" : reply.replaceAll("(?s)<think>.*?(</think>|$)", "").replaceAll("</?final>", "").trim();
    }
}
