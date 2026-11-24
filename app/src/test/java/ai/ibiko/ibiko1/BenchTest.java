package ai.ibiko.ibiko1;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Ibiko v1's benchmark: real conversations (src/test/resources/bench/questions.tsv) through the whole brain, offline, on
 * the shipped knowledge. It scores each area, times each answer, and writes the report to build/bench/report.txt.
 *
 * It is a ratchet: the cases that pass are listed in bench/passing.txt, and one of them failing later fails the build, so
 * Ibiko only ever gets better. Run with BENCH_UPDATE=1 to write the list again after an improvement.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class BenchTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { }
        @Override public void stepsDone() { }
        @Override public void say(String persona, String text) { said.add(text); }
        @Override public void askRating(String question, String answer) { }
        @Override public void noRating(String text) { }
        @Override public boolean ratingWaiting() { return false; }
        @Override public void clearRating() { }
        @Override public boolean online() { return false; }
        @Override public String sum(String message) { return null; }
        @Override public String recallTalk(String question) { return null; }
        @Override public void learnTalk(String question, String answer) { }
        @Override public void forgetTalk(String question) { }
        @Override public int talkSize() { return 0; }
        @Override public int shapes() { return 0; }
        @Override public List<String> aboutPilot() { return new ArrayList<String>(); }
        @Override public void answered(Ibiko1.How how) { }
        @Override public void note(String text) { }
        @Override public void later(Runnable r, long ms) { r.run(); }
        @Override public void onScreen(Runnable r) { r.run(); }
        @Override public android.content.Context context() { return RuntimeEnvironment.getApplication(); }
    }

    private static String ask(Ibiko1 brain, Chat chat, String q) throws InterruptedException {
        int n = chat.said.size();
        brain.answer(q);
        for (int i = 0; i < 400 && chat.said.size() == n; i++) {
            Thread.sleep(5);
        }
        return chat.said.size() > n ? chat.said.get(n) : null;
    }

    private static final class Case {
        final String id, area, expect;
        final String[] turns;

        Case(String[] f) {
            id = f[0];
            area = f[1];
            turns = f[2].replace("\\n", "\n").split(" \\|\\| ");
            expect = f[3];
        }
    }

    @Test(timeout = 900000)
    public void score() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        File res = new File("src/test/resources/bench");
        List<Case> cases = new ArrayList<Case>();
        for (String line : Files.readAllLines(new File(res, "questions.tsv").toPath(), StandardCharsets.UTF_8)) {
            String[] f = line.split("\t");
            if (!line.startsWith("#") && f.length == 4) {
                cases.add(new Case(f));
            }
        }
        Map<String, int[]> areas = new LinkedHashMap<String, int[]>(); // area → passed, total
        Set<String> passed = new LinkedHashSet<String>();
        List<String> failures = new ArrayList<String>();
        List<Long> times = new ArrayList<Long>();
        try {
            for (Case c : cases) {
                Store.reset();
                Chat chat = new Chat();
                Ibiko1 brain = new Ibiko1(chat, Files.createTempDirectory("bench").toFile());
                String last = null;
                long took = 0;
                for (String turn : c.turns) {
                    long t0 = System.nanoTime();
                    last = ask(brain, chat, turn);
                    took = (System.nanoTime() - t0) / 1_000_000;
                }
                times.add(took);
                boolean ok = last != null && Pattern.compile(c.expect, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE).matcher(last).find();
                int[] a = areas.computeIfAbsent(c.area, k -> new int[2]);
                a[1]++;
                if (ok) {
                    a[0]++;
                    passed.add(c.id);
                } else {
                    failures.add(c.id + "  [" + c.area + "]  asked: " + c.turns[c.turns.length - 1].replace("\n", " ")
                            + "\n      said: " + (last == null ? "(nothing)" : clip(last.replace("\n", " | "), 220)));
                }
            }
        } finally {
            Plug.asset = was;
        }
        Collections.sort(times);
        long sum = 0;
        for (long t : times) {
            sum += t;
        }
        StringBuilder r = new StringBuilder("Ibiko v1 benchmark: ").append(passed.size()).append(" of ").append(cases.size())
                .append(" (").append(Math.round(100.0 * passed.size() / cases.size())).append("%)\n");
        r.append("Answer time: average ").append(sum / Math.max(1, times.size())).append(" ms, median ").append(times.get(times.size() / 2))
                .append(" ms, slowest ").append(times.get(times.size() - 1)).append(" ms (this machine, in a test VM)\n\n");
        for (Map.Entry<String, int[]> e : areas.entrySet()) {
            r.append(String.format("  %-10s %3d / %-3d %s%n", e.getKey(), e.getValue()[0], e.getValue()[1], bar(e.getValue()[0], e.getValue()[1])));
        }
        r.append("\nNot yet:\n");
        for (String f : failures) {
            r.append("  ").append(f).append('\n');
        }
        File out = new File("build/bench/report.txt");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), r.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(r);

        // the ratchet: what passed before must still pass
        File list = new File(res, "passing.txt");
        if ("1".equals(System.getenv("BENCH_UPDATE"))) {
            Files.write(list.toPath(), (String.join("\n", passed) + "\n").getBytes(StandardCharsets.UTF_8));
            return;
        }
        List<String> lost = new ArrayList<String>();
        if (list.isFile()) {
            for (String id : Files.readAllLines(list.toPath(), StandardCharsets.UTF_8)) {
                if (!id.trim().isEmpty() && !passed.contains(id.trim())) {
                    lost.add(id.trim());
                }
            }
        }
        assertTrue("These passed before and fail now: " + lost + "\n" + r, lost.isEmpty());
    }

    private static String bar(int got, int of) {
        int n = of == 0 ? 0 : Math.round(20f * got / of);
        return "█".repeat(n) + "░".repeat(20 - n);
    }

    private static String clip(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}
