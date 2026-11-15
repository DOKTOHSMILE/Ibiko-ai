package ai.ibiko.learn;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ai.ibiko.brain.MiniJson;

/**
 * Ibiko learning from every model it talks to, online or on the device: the instructions, the conversation, the
 * request, the reply, which model, and the logic of the decision (what kind of act it was, the limits the person
 * stated, the lean those limits support, the moves the model made, and whether its reason agrees). The words alone
 * are not the lesson. Now: when the phone's own model answers, the closest past decision is given to it as the
 * logic to follow, with the earlier wording only as what was said ({@link #examplesFor}). Later:
 * {@link #exportForTraining} writes the lot in the chat format fine-tuning tools read, and keeps the logic beside
 * the messages. Keys are never part of a record; nothing leaves the phone unless the user exports it. Pure Java.
 */
public final class LearningLog {
    /** The file stops growing past this; the oldest half is dropped. */
    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final File file;

    public LearningLog(File dir) {
        dir.mkdirs();
        this.file = new File(dir, "interactions.jsonl");
    }

    public File file() {
        return file;
    }

    /** One answer, from an API model or from the model on the device: the turns, the reply, who wrote it, and why. */
    public synchronized void record(String system, List<String[]> turns, String reply, String source, String tier, long at) {
        if (reply == null || reply.trim().isEmpty() || turns == null || turns.isEmpty()) {
            return;
        }
        Map<String, Object> r = new LinkedHashMap<String, Object>();
        r.put("at", (double) at);
        r.put("source", source == null ? "" : source);
        r.put("tier", tier == null ? "" : tier);
        r.put("system", system == null ? "" : system.replace("/no_think", "").trim());
        List<Object> msgs = new ArrayList<Object>();
        for (String[] t : turns) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("role", "assistant".equals(t[0]) ? "assistant" : "user");
            m.put("content", t[1] == null ? "" : t[1]);
            msgs.add(m);
        }
        r.put("messages", msgs);
        r.put("reply", reply.trim());
        r.put("logic", DecisionLogic.of(DecisionLogic.asked(turns), reply, source).toMap());
        try {
            if (file.length() > MAX_BYTES) {
                dropOldestHalf();
            }
            CodeLog.append(file, MiniJson.write(r)); // one answer added in IBK codes, never the whole log again
        } catch (IOException e) {
            // learning is a bonus: a full disk must not stop the answer
        }
    }

    /** How many answers are kept. */
    public synchronized int count() {
        int n = 0;
        for (String line : lines()) {
            if (!line.trim().isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public synchronized long bytes() {
        return file.length();
    }

    public synchronized void clear() {
        file.delete();
    }

    /**
     * The closest past decision to this request, for the phone's own model: the logic first (what was decided and
     * why), then what was actually said, at most {@code most} of them and {@code maxChars} long. Empty when none is
     * close. The model is told to follow the logic and to copy the words only when the facts are the same. Only
     * conversation answers count (not one-line orders or Ibiko's tool steps). When the online model and the device
     * disagreed, both reasons are shown and the model is told not to blend the wordings.
     */
    /** The learned questions filed by code (L<n>) and tags, read once and added to as the log grows. */
    private Codes index;
    private final List<Kept> learned = new ArrayList<Kept>();
    private long indexedBytes = -1;

    /** One kept answer: the question, the reply, and the decision behind it. */
    private static final class Kept {
        final String asked;
        final String reply;
        final DecisionLogic logic;

        Kept(String asked, String reply, DecisionLogic logic) {
            this.asked = asked;
            this.reply = reply;
            this.logic = logic;
        }
    }

    /** Files any answers added since the last look; files all again when the log was cut. */
    private void fileNew() {
        long len = file.isFile() ? file.length() : 0;
        if (index != null && len == indexedBytes) {
            return;
        }
        index = new Codes();
        learned.clear();
        indexedBytes = len;
        for (String line : lines()) {
            Map<String, Object> r;
            try {
                r = MiniJson.obj(MiniJson.parse(line));
            } catch (RuntimeException e) {
                continue;
            }
            String reply = MiniJson.str(r.get("reply"), "");
            if (reply.startsWith("DO ") || reply.startsWith("SAY ") || reply.startsWith("CANNOT") || reply.contains("<tool ")) {
                continue; // routing orders and agent steps aren't answers to imitate
            }
            List<Object> msgs = MiniJson.list(r.get("messages"));
            if (msgs.isEmpty()) {
                continue;
            }
            String asked = MiniJson.str(MiniJson.obj(msgs.get(msgs.size() - 1)).get("content"), "");
            int cut = asked.lastIndexOf("The user says: ");
            if (cut >= 0) {
                asked = asked.substring(cut + 15);
            }
            asked = asked.trim();
            DecisionLogic logic = DecisionLogic.from(r);
            if (logic == null) {
                logic = DecisionLogic.of(asked, reply, MiniJson.str(r.get("source"), ""));
            }
            index.add("L" + learned.size(), asked);
            learned.add(new Kept(asked, reply.trim(), logic));
        }
    }

    public synchronized String examplesFor(String request, int most, int maxChars) {
        Set<String> want = words(request);
        if (want.size() < 2) {
            return "";
        }
        List<double[]> scored = new ArrayList<double[]>();
        // by code: the question's tags pull the ids of the learned questions about it; only those are compared, not
        // every answer ever learned (the log is read once, then only what is new)
        fileNew();
        for (String code : index.recall(request, 60, 1)) {
            Kept p = learned.get(Integer.parseInt(code.substring(1)));
            Set<String> have = words(p.asked);
            int same = 0;
            for (String w : want) {
                if (have.contains(w)) {
                    same++;
                }
            }
            double score = same / (double) (want.size() + have.size() - same);
            if (p.logic != null && p.logic.agrees(p.reply)) {
                score += 0.05; // a reply that states the lean is safer to show than one that fights it
            }
            if (score >= 0.3) {
                scored.add(new double[]{score, learnedIndex(code)});
            }
        }
        scored.sort((a, b) -> Double.compare(b[0], a[0]));
        StringBuilder out = new StringBuilder();
        Set<String> used = new HashSet<String>();
        int shown = 0;
        for (double[] s : scored) {
            Kept p = learned.get((int) s[1]);
            if (used.contains(p.asked) || shown >= most) {
                continue;
            }
            String block = block(p);
            Kept other = otherSide(p, scored);
            if (other != null) {
                block += "The other side (" + other.logic.side + ") was not the same. Its logic: "
                        + clip(other.logic.lesson, 180) + "\nSaid there: " + clip(other.reply, 120)
                        + "\nKeep the reason that uses a limit they stated. Do not blend the wordings.\n";
            }
            if (out.length() + block.length() > maxChars) {
                if (out.length() == 0 && block.length() > maxChars) {
                    block = clip(block, maxChars);
                } else {
                    break;
                }
            }
            used.add(p.asked);
            shown++;
            out.append(block);
        }
        return out.length() == 0 ? "" : "How a similar request was decided (follow the logic, not the wording):\n" + out;
    }

    private int learnedIndex(String code) {
        return Integer.parseInt(code.substring(1));
    }

    private static String block(Kept p) {
        DecisionLogic g = p.logic;
        StringBuilder b = new StringBuilder();
        b.append("Request: ").append(clip(p.asked, 220)).append('\n');
        if (g != null && g.decision != null && !g.decision.isEmpty()) {
            b.append(clip(g.decision, 220)).append('\n');
        }
        if (g != null && g.lesson != null && !g.lesson.isEmpty()) {
            b.append("Logic: ").append(clip(g.lesson, 360)).append('\n');
        }
        b.append("Said (").append(g == null || g.side.isEmpty() ? "a model" : g.side).append("): ")
                .append(clip(p.reply, 240)).append('\n');
        return b.toString();
    }

    /** Another model's answer to the same request that does not say the same thing, or null. */
    private Kept otherSide(Kept chosen, List<double[]> scored) {
        if (chosen.logic == null) {
            return null;
        }
        Set<String> want = words(chosen.asked);
        for (double[] s : scored) {
            Kept p = learned.get((int) s[1]);
            if (p == chosen || p.logic == null || p.logic.side.equals(chosen.logic.side)) {
                continue;
            }
            if (sameWords(want, words(p.asked)) < 0.5) {
                continue;
            }
            if (p.reply.equalsIgnoreCase(chosen.reply)) {
                continue;
            }
            return p;
        }
        return null;
    }

    private static double sameWords(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        int same = 0;
        for (String w : a) {
            if (b.contains(w)) {
                same++;
            }
        }
        return same / (double) (a.size() + b.size() - same);
    }

    /**
     * All answers in the chat format fine-tuning tools read: one {"messages": [system, …turns, assistant reply]} a
     * line. Returns how many were written.
     */
    public synchronized int exportForTraining(java.io.OutputStream out) throws IOException {
        int n = 0;
        for (String line : lines()) {
            Map<String, Object> r;
            try {
                r = MiniJson.obj(MiniJson.parse(line));
            } catch (RuntimeException e) {
                continue;
            }
            List<Object> msgs = new ArrayList<Object>();
            String system = MiniJson.str(r.get("system"), "");
            if (!system.isEmpty()) {
                Map<String, Object> s = new LinkedHashMap<String, Object>();
                s.put("role", "system");
                s.put("content", system);
                msgs.add(s);
            }
            msgs.addAll(MiniJson.list(r.get("messages")));
            Map<String, Object> a = new LinkedHashMap<String, Object>();
            a.put("role", "assistant");
            a.put("content", MiniJson.str(r.get("reply"), ""));
            msgs.add(a);
            Map<String, Object> example = new LinkedHashMap<String, Object>();
            example.put("messages", msgs);
            Object logic = r.get("logic");
            if (logic != null) {
                example.put("logic", logic); // the reason, so a later training run can learn it and not only the words
            }
            out.write((MiniJson.write(example) + "\n").getBytes(StandardCharsets.UTF_8));
            n++;
        }
        return n;
    }

    private List<String> lines() {
        try {
            if (!file.isFile()) {
                return new ArrayList<String>();
            }
            return CodeLog.lines(file);
        } catch (IOException e) {
            return new ArrayList<String>();
        }
    }

    private void dropOldestHalf() throws IOException {
        List<String> all = lines();
        List<String> keep = all.subList(all.size() / 2, all.size());
        CodeLog.write(file, new ArrayList<String>(keep));
    }

    private static final Set<String> SMALL = new HashSet<String>(java.util.Arrays.asList("the", "a", "an", "is", "are", "of",
            "to", "in", "on", "and", "or", "for", "with", "what", "how", "why", "who", "me", "my", "you", "your", "it", "i",
            "can", "do", "does", "please", "about", "this", "that", "be", "was", "were"));

    static Set<String> words(String s) {
        Set<String> out = new HashSet<String>();
        if (s == null) {
            return out;
        }
        for (String w : s.toLowerCase(Locale.US).split("[^a-z0-9]+")) {
            if (w.length() > 1 && !SMALL.contains(w)) {
                out.add(w.replaceAll("(ing|ed|es|s)$", ""));
            }
        }
        return out;
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
