package ai.ibiko.ibiko1.formulas;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Premeditation: thinking ahead before acting. Two parts. A habit model, learned from the pilot's own messages, says
 * what they are likely to need next after a request like this one; and a check the brain runs before it answers or
 * acts says what the request is for, what could go wrong and what comes after it. Pure arithmetic and text.
 *
 * <pre>
 * P1  transition        after a request of kind A, the next request of kind B:  n(A to B) += 1
 * P2  forgetting        each new transition from A first multiplies every n(A to *) by 0.95, so habits that stop fade
 * P3  likelihood        P(B | A) = (n(A to B) + 1) / (n(A) + k + 1)   k = how many kinds have followed A (Laplace)
 * P4  confident         a prediction is offered only when n(A) >= 2.5 (about three sightings after P2) and P(B | A) >= 0.40
 * P5  look before       before answering or acting, the brain weighs: what is asked; what it is for; what could go
 *                       wrong (cost, loss, safety, a step that cannot be undone); what will be needed next
 * P6  offer, not act    a predicted next step is offered in one short line; it is never carried out unasked
 * P7  same thread       a gap of more than 6 hours between two messages is a new session: no transition is counted
 * </pre>
 *
 * The kind of a request is its answer type and how it opens ("how:how do i", "other:fix the"), so one habit serves every
 * request of that kind. Kept in {@code brain/foresight.tsv}.
 */
public final class Foresight {
    /** P2, P4, P7. */
    public static final double FADE = 0.95, SURE = 0.40;
    public static final double SEEN = 2.5;
    public static final long SESSION_MS = 6L * 60 * 60 * 1000;

    /** P5: the check the brain runs before it answers or acts. */
    public static final String LOOK_BEFORE = " Think ahead before you answer or act (premeditate): silently weigh what the"
            + " user is really asking, what it is for, what could go wrong (cost, lost data, safety, a step that cannot be"
            + " undone) and what they will need next. Let that shape the answer: warn about a real risk in one line, do the"
            + " safe order of steps, and prepare what the next step needs. Do not write out this weighing.";

    /** One predicted next request. */
    public static final class Guess {
        public final String kind, example;
        public final double p;
        public final int seen;

        Guess(String kind, String example, double p, int seen) {
            this.kind = kind;
            this.example = example;
            this.p = p;
            this.seen = seen;
        }
    }

    private final File file;
    /** n(A to B), A then B joined by a tab. */
    private final Map<String, Double> counts = new LinkedHashMap<String, Double>();
    /** The last words seen of each kind: how a predicted request is put to the pilot. */
    private final Map<String, String> examples = new LinkedHashMap<String, String>();
    private String lastKind;
    private long lastAt;

    public Foresight(File file) {
        this.file = file;
        load();
    }

    /** P1, P2, P7: one of the pilot's messages, at {@code now}. */
    public synchronized void heard(String message, String kind, long now) {
        if (kind == null || kind.isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        if (lastKind != null && now - lastAt <= SESSION_MS) {
            for (Map.Entry<String, Double> e : counts.entrySet()) {
                if (e.getKey().startsWith(lastKind + "\t")) {
                    e.setValue(e.getValue() * FADE);
                }
            }
            String key = lastKind + "\t" + kind;
            counts.put(key, (counts.containsKey(key) ? counts.get(key) : 0) + 1);
        }
        examples.put(kind, clip(Shapes.mask(message.trim()), 120));
        lastKind = kind;
        lastAt = now;
        save();
    }

    /** P3, P4: the most likely next request after one of {@code kind}, or null when no habit is sure enough. */
    public synchronized Guess next(String kind) {
        double all = 0;
        int k = 0;
        String best = null;
        double bestN = 0;
        for (Map.Entry<String, Double> e : counts.entrySet()) {
            String[] ab = e.getKey().split("\t", 2);
            if (!ab[0].equals(kind)) {
                continue;
            }
            all += e.getValue();
            k++;
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = ab[1];
            }
        }
        if (best == null || all < SEEN) {
            return null;
        }
        double p = (bestN + 1) / (all + k + 1);
        if (p < SURE) {
            return null;
        }
        return new Guess(best, examples.get(best), p, (int) Math.ceil(all));
    }

    /** P5 and P6 for any brain, with the request: the look-before check, and the likely next step when it is sure. */
    public synchronized String hint(String kind) {
        Guess g = kind == null ? null : next(kind);
        return LOOK_BEFORE + (g == null || g.example == null ? "" : " From this user's habits, after a request like this they"
                + " usually go on to something like: “" + g.example + "” (" + Math.round(g.p * 100) + "% of " + g.seen
                + " times). If it fits, end with one short offer to do that next; never do it unasked.");
    }

    public synchronized int size() {
        return counts.size();
    }

    public synchronized void clear() {
        counts.clear();
        examples.clear();
        lastKind = null;
        save();
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private void load() {
        if (file == null || !file.isFile()) {
            return;
        }
        try {
            for (String line : ai.ibiko.learn.CodeFile.lines(file)) {
                String[] p = line.split("\t");
                if (p.length == 4 && "n".equals(p[0])) {
                    counts.put(p[1] + "\t" + p[2], Double.parseDouble(p[3]));
                } else if (p.length == 3 && "e".equals(p[0])) {
                    examples.put(p[1], p[2]);
                }
            }
        } catch (IOException | RuntimeException e) {
            // starts again from what it hears next
        }
    }

    private void save() {
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Double> e : counts.entrySet()) {
            b.append("n\t").append(e.getKey()).append('\t').append(String.format(Locale.US, "%.4f", e.getValue())).append('\n');
        }
        for (Map.Entry<String, String> e : examples.entrySet()) {
            b.append("e\t").append(e.getKey()).append('\t').append(e.getValue().replace('\t', ' ').replace('\n', ' ')).append('\n');
        }
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            ai.ibiko.learn.CodeFile.write(file, b.toString()); // whole or not at all: a crash mid-save cut it
        } catch (IOException e) {
            // kept in memory
        }
    }

    // ------------------------------------------------------------------ P8 consequences (IBIKO_COGNITIVE_LEARNING.md, engine 7)

    /** What an action could lead to, weighed before it is recommended. */
    public static final class Consequences {
        public final String action;
        public final java.util.List<String> benefits = new java.util.ArrayList<String>(), costs = new java.util.ArrayList<String>(),
                risks = new java.util.ArrayList<String>(), alternatives = new java.util.ArrayList<String>(), assumptions = new java.util.ArrayList<String>();
        public boolean irreversible;

        Consequences(String action) {
            this.action = action;
        }

        /** In a few lines, said as a forecast, never as a certainty. */
        public String said() {
            StringBuilder b = new StringBuilder("Before you " + action + ":");
            if (!benefits.isEmpty()) {
                b.append("\n• Likely gain: ").append(String.join("; ", benefits));
            }
            if (!costs.isEmpty()) {
                b.append("\n• Cost: ").append(String.join("; ", costs));
            }
            if (!risks.isEmpty()) {
                b.append("\n• Risk: ").append(String.join("; ", risks));
            }
            if (irreversible) {
                b.append("\n• It is hard to undo, so check first.");
            }
            if (!alternatives.isEmpty()) {
                b.append("\n• Another way: ").append(String.join("; or ", alternatives));
            }
            if (!assumptions.isEmpty()) {
                b.append("\n• This rests on: ").append(String.join("; ", assumptions)).append(". If that's wrong, so is the forecast.");
            }
            return b.toString();
        }
    }

    private static final java.util.regex.Pattern ACTION = java.util.regex.Pattern.compile("(?i)\\b(?:should i|shall i|is it (?:wise|a good idea) to|"
            + "do you recommend|would you recommend|i(?:'m| am) (?:going|planning|thinking of|about) to|i want to|i plan to)\\s+(.{3,80}?)[?.!]*$");

    /**
     * P8: the consequences of an action asked about ("should I quit my job to start a business?"): what it is likely to
     * bring, what it costs, what could go wrong, whether it can be undone, another way, and what the forecast rests on.
     * Null when the message isn't about an action of consequence.
     */
    public static Consequences weigh(String request) {
        java.util.regex.Matcher m = ACTION.matcher(request == null ? "" : request.trim());
        if (!m.find()) {
            return null;
        }
        String act = m.group(1).trim().replaceAll("(?i)\\bmy\\b", "your").replaceAll("(?i)\\bme\\b", "you").replaceAll("(?i)\\bi\\b", "you");
        String low = act.toLowerCase(java.util.Locale.ROOT);
        Consequences c = new Consequences(act);
        boolean money = low.matches(".*\\b(?:invest|buy|sell|loan|borrow|lend|spend|crypto|bitcoin|stocks?|shares|business|property|land|house|car)\\b.*");
        boolean work = low.matches(".*\\b(?:quit|resign|leave (?:your|the) job|change jobs?|start (?:a|your) business|go freelance)\\b.*");
        boolean health = low.matches(".*\\b(?:stop taking|start taking|take|dose|surgery|diet|fast|medication|drug|pills?)\\b.*");
        boolean move = low.matches(".*\\b(?:move|relocate|japa|emigrate|travel abroad|study abroad)\\b.*");
        boolean delete = low.matches(".*\\b(?:delete|wipe|format|reset|cancel|close (?:your|the) account|divorce|sign)\\b.*");
        if (!(money || work || health || move || delete)) {
            return null;
        }
        if (money) {
            c.benefits.add("a return or a saving, if it goes as hoped");
            c.costs.add("the money put in, fees and taxes");
            c.risks.add("losing some or all of it; scams promise more than markets give");
            c.alternatives.add("start with a small amount you can afford to lose");
            c.assumptions.add("prices and income behave as expected");
        }
        if (work) {
            c.benefits.add("more freedom, or work that suits you better");
            c.costs.add("your steady income while it starts");
            c.risks.add("months with little or no income");
            c.alternatives.add("start it on the side first, and leave when it pays");
            c.assumptions.add("you have savings for at least six months, and people will pay for it");
            c.irreversible = true;
        }
        if (health) {
            c.benefits.add("relief, if it suits your body");
            c.risks.add("side effects or interactions with what you already take");
            c.alternatives.add("ask a doctor or pharmacist first, with what you already take");
            c.assumptions.add("nothing about your health makes it unsafe");
        }
        if (move) {
            c.benefits.add("new opportunities");
            c.costs.add("the move, papers and settling in");
            c.risks.add("papers refused, or work slower to find than hoped");
            c.alternatives.add("secure the job, school or visa before you go");
            c.assumptions.add("your papers and plans hold");
        }
        if (delete) {
            c.risks.add("what is gone may not come back");
            c.alternatives.add("back it up, or pause it, first");
            c.irreversible = true;
        }
        return c;
    }
}
