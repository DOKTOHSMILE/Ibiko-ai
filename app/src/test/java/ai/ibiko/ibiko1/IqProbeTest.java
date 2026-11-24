package ai.ibiko.ibiko1;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * An IQ-style probe: random questions in the kinds an intelligence test asks (numbers, sequences, logic, words,
 * analogies, knowledge, memory, comprehension, word problems, typos), made fresh from a seed so nothing can be tuned to
 * them, asked as one live conversation through the whole brain (offline, the dictionary warmed as the app does). Each
 * area is scored and the report written to build/iq/report.txt. Not part of the normal run: IQ_RUN=1 (and IQ_SEED).
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class IqProbeTest {
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

    /** One turn: what's said, and (for a question) the area and what a right answer holds. */
    private static final class Turn {
        final String area, say, expect;

        Turn(String area, String say, String expect) {
            this.area = area;
            this.say = say;
            this.expect = expect;
        }
    }

    private static <T> T pick(Random r, List<T> l) {
        return l.get(r.nextInt(l.size()));
    }

    static List<Turn> battery(Random r) {
        List<Turn> t = new ArrayList<Turn>();
        // numbers
        for (int i = 0; i < 4; i++) {
            int a = 2 + r.nextInt(60), b = 2 + r.nextInt(30), c = 2 + r.nextInt(9);
            t.add(new Turn("numbers", "What is " + a + " + " + b + " × " + c + "?", "\\b" + (a + b * c) + "\\b"));
        }
        int whole = (10 + r.nextInt(40)) * 20, pct = 5 * (1 + r.nextInt(9));
        t.add(new Turn("numbers", "What is " + pct + "% of " + whole + "?", "\\b" + fmt(whole * pct / 100.0) + "\\b"));
        int sq = 4 + r.nextInt(20);
        t.add(new Turn("numbers", "What is the square root of " + (sq * sq) + "?", "\\b" + sq + "\\b"));
        // sequences
        int s0 = 1 + r.nextInt(9), d = 2 + r.nextInt(7);
        t.add(new Turn("sequences", "What comes next: " + s0 + ", " + (s0 + d) + ", " + (s0 + 2 * d) + ", " + (s0 + 3 * d) + "?", "\\b" + (s0 + 4 * d) + "\\b"));
        int g0 = 1 + r.nextInt(4), q = 2 + r.nextInt(2);
        t.add(new Turn("sequences", "What comes next in the sequence " + g0 + ", " + g0 * q + ", " + g0 * q * q + ", " + g0 * q * q * q + "?",
                "\\b" + g0 * q * q * q * q + "\\b"));
        int k = 1 + r.nextInt(4);
        t.add(new Turn("sequences", "Next number: " + k * k + ", " + (k + 1) * (k + 1) + ", " + (k + 2) * (k + 2) + ", " + (k + 3) * (k + 3) + "?",
                "\\b" + (k + 4) * (k + 4) + "\\b"));
        int f1 = 1 + r.nextInt(3), f2 = f1 + 1 + r.nextInt(3);
        t.add(new Turn("sequences", "Continue the pattern: " + f1 + ", " + f2 + ", " + (f1 + f2) + ", " + (f1 + 2 * f2) + ", " + (2 * f1 + 3 * f2) + ", ?",
                "\\b" + (3 * f1 + 5 * f2) + "\\b"));
        // logic, with made-up words so nothing is known in advance
        List<String> nonsense = Arrays.asList("blips", "zorps", "glims", "trevs", "wumps", "fizzles", "drongs", "quabs");
        List<String> shuffled = new ArrayList<String>(nonsense);
        Collections.shuffle(shuffled, r);
        List<String> names = Arrays.asList("Ada", "Tunde", "Ngozi", "Musa", "Bisi", "Emeka", "Zara", "Kofi", "Amina", "Femi");
        String n1 = pick(r, names);
        t.add(new Turn("logic", "All " + shuffled.get(0) + " are " + shuffled.get(1) + ". " + n1 + " is a " + shuffled.get(0).replaceAll("s$", "")
                + ". Is " + n1 + " a " + shuffled.get(1).replaceAll("s$", "") + "?", "^Yes"));
        t.add(new Turn("logic", "No " + shuffled.get(2) + " are " + shuffled.get(3) + ". " + n1 + " is a " + shuffled.get(2).replaceAll("s$", "")
                + ". Is " + n1 + " a " + shuffled.get(3).replaceAll("s$", "") + "?", "^No"));
        List<String> three = new ArrayList<String>(names);
        Collections.shuffle(three, r);
        t.add(new Turn("logic", three.get(0) + " is taller than " + three.get(1) + ". " + three.get(1) + " is taller than " + three.get(2)
                + ". Who is the shortest?", "\\b" + three.get(2) + "\\b"));
        t.add(new Turn("logic", three.get(3) + " is older than " + three.get(4) + " but younger than " + three.get(5) + ". Who is the oldest?",
                "\\b" + three.get(5) + "\\b"));
        t.add(new Turn("logic", "If it rains, the ground gets wet. The ground is wet. Did it rain?", "(not necessarily|can't tell|cannot tell|not sure|could)"));
        // words
        String[][] opp = {{"hot", "cold"}, {"tall", "short"}, {"happy", "sad"}, {"early", "late"}, {"strong", "weak"}, {"rich", "poor"},
                {"full", "empty"}, {"open", "closed|shut"}, {"heavy", "light"}, {"ancient", "modern|new"}};
        for (int i = 0; i < 2; i++) {
            String[] o = opp[r.nextInt(opp.length)];
            t.add(new Turn("words", "What is the opposite of " + o[0] + "?", "\\b(" + o[1] + ")\\b"));
        }
        String[][] plural = {{"child", "children"}, {"mouse", "mice"}, {"foot", "feet"}, {"person", "people"}, {"tooth", "teeth"}, {"woman", "women"}};
        String[] p = plural[r.nextInt(plural.length)];
        t.add(new Turn("words", "What is the plural of " + p[0] + "?", "\\b" + p[1] + "\\b"));
        String[][] past = {{"go", "went"}, {"eat", "ate"}, {"run", "ran"}, {"write", "wrote"}, {"see", "saw"}, {"take", "took"}};
        String[] pa = past[r.nextInt(past.length)];
        t.add(new Turn("words", "What is the past tense of " + pa[0] + "?", "\\b" + pa[1] + "\\b"));
        String[][] odd = {{"apple, banana, carrot, mango", "carrot"}, {"dog, cat, eagle, cow", "eagle"}, {"red, blue, green, chair", "chair"},
                {"Lagos, Abuja, Kano, Ghana", "Ghana"}, {"piano, guitar, violin, hammer", "hammer"}};
        String[] od = odd[r.nextInt(odd.length)];
        t.add(new Turn("words", "Which is the odd one out: " + od[0] + "?", "\\b" + od[1] + "\\b"));
        // analogies
        String[][] an = {{"Hot is to cold as up is to what?", "down"}, {"Bird is to fly as fish is to what?", "swim"},
                {"Hand is to glove as foot is to what?", "sock|shoe"}, {"Day is to night as summer is to what?", "winter"},
                {"Puppy is to dog as kitten is to what?", "cat"}, {"Book is to read as song is to what?", "sing|listen|hear"}};
        List<String[]> ans = new ArrayList<String[]>(Arrays.asList(an));
        Collections.shuffle(ans, r);
        for (int i = 0; i < 3; i++) {
            t.add(new Turn("analogies", ans.get(i)[0], "\\b(" + ans.get(i)[1] + ")\\b"));
        }
        // knowledge
        String[][] caps = {{"Ghana", "Accra"}, {"Kenya", "Nairobi"}, {"France", "Paris"}, {"Japan", "Tokyo"}, {"Egypt", "Cairo"},
                {"Canada", "Ottawa"}, {"Brazil", "Bras[ií]lia"}, {"Senegal", "Dakar"}, {"Germany", "Berlin"}, {"South Africa", "Pretoria|Cape Town"}};
        List<String[]> cl = new ArrayList<String[]>(Arrays.asList(caps));
        Collections.shuffle(cl, r);
        for (int i = 0; i < 2; i++) {
            t.add(new Turn("knowledge", "What is the capital of " + cl.get(i)[0] + "?", "\\b(" + cl.get(i)[1] + ")\\b"));
        }
        String[][] know = {{"What is the chemical symbol for iron?", "\\bFe\\b"}, {"How many planets are in the solar system?", "\\b(8|eight)\\b"},
                {"What is the largest planet?", "Jupiter"}, {"Who wrote Things Fall Apart?", "Achebe"}, {"At what temperature does water boil?", "100"},
                {"How many continents are there?", "\\b(7|seven)\\b"}, {"What gas do plants take in?", "carbon dioxide|CO2"}};
        List<String[]> kl = new ArrayList<String[]>(Arrays.asList(know));
        Collections.shuffle(kl, r);
        for (int i = 0; i < 3; i++) {
            t.add(new Turn("knowledge", kl.get(i)[0], kl.get(i)[1]));
        }
        // memory: told early, asked after other talk
        String name = pick(r, names), city = pick(r, Arrays.asList("Kano", "Enugu", "Ibadan", "Jos", "Calabar", "Owerri"));
        String pet = pick(r, Arrays.asList("Bingo", "Rex", "Snowy", "Tiger", "Lucky"));
        t.add(1, new Turn(null, "My name is " + name + ".", null));
        t.add(3, new Turn(null, "I live in " + city + ".", null));
        t.add(5, new Turn(null, "My dog's name is " + pet + ".", null));
        t.add(new Turn("memory", "What is my name?", "\\b" + name + "\\b"));
        t.add(new Turn("memory", "Where do I live?", "\\b" + city + "\\b"));
        t.add(new Turn("memory", "What is my dog called?", "\\b" + pet + "\\b"));
        // comprehension
        String who = pick(r, names);
        int cows = 3 + r.nextInt(9);
        t.add(new Turn("comprehension", who + " is a farmer in Jos. " + who + " keeps " + cows + " cows and grows maize. How many cows does " + who + " keep?",
                "\\b" + cows + "\\b"));
        // word problems
        int have = 2 + r.nextInt(8), more = 1 + r.nextInt(8);
        t.add(new Turn(null, "I have " + have + " oranges.", null));
        t.add(new Turn(null, "I buy " + more + " more oranges.", null));
        t.add(new Turn("problems", "How many oranges do I have now?", "\\b" + (have + more) + "\\b"));
        int total = (2 + r.nextInt(10)) * 6, ways = pick(r, Arrays.asList(2, 3, 6));
        t.add(new Turn("problems", "Share " + total + " naira equally among " + ways + " children. How much does each get?", "\\b" + (total / ways) + "\\b"));
        int km = (2 + r.nextInt(10)) * 10, h = pick(r, Arrays.asList(2, 5, 10));
        t.add(new Turn("problems", "A car travels " + km * h + " km in " + h + " hours. What is its average speed?", "\\b" + km + "\\b"));
        int paid = 1000, cost = 100 + 50 * r.nextInt(17);
        t.add(new Turn("problems", "I paid " + paid + " naira for something that costs " + cost + ". How much change do I get?", "\\b" + (paid - cost) + "\\b"));
        // typos
        String[][] typo = {{"wat is the captal of kenya", "Nairobi"}, {"hw many legs does a spidr have", "\\b(8|eight)\\b"},
                {"whos the autor of things fall apart", "Achebe"}};
        String[] ty = typo[r.nextInt(typo.length)];
        t.add(new Turn("typos", ty[0], ty[1]));
        return t;
    }

    private static String fmt(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9 ? String.format("%,d", (long) Math.rint(v)) : String.valueOf(v);
    }

    @Test(timeout = 1800000)
    public void probe() throws Exception {
        Assume.assumeTrue("set IQ_RUN=1 to run the IQ-style probe", "1".equals(System.getenv("IQ_RUN")));
        long seed = System.getenv("IQ_SEED") == null ? System.nanoTime() : Long.parseLong(System.getenv("IQ_SEED"));
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = path -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + path));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        StringBuilder log = new StringBuilder();
        Map<String, int[]> areas = new LinkedHashMap<String, int[]>();
        try {
            Store.reset();
            Chat chat = new Chat();
            File files = Files.createTempDirectory("iq").toFile();
            ai.ibiko.PlainAsset.commonSense(files); // the common-sense table the app installs on the phone (brain/knowledge)
            Ibiko1.warm(files);
            Ibiko1 brain = new Ibiko1(chat, files);
            for (Turn turn : battery(new Random(seed))) {
                int n = chat.said.size();
                brain.answer(turn.say);
                for (int i = 0; i < 1200 && chat.said.size() == n; i++) {
                    Thread.sleep(10);
                }
                String a = chat.said.size() > n ? chat.said.get(n) : "(no reply)";
                String shown = a.replace("\n", " | ");
                shown = shown.length() > 220 ? shown.substring(0, 220) + "…" : shown;
                if (turn.area == null) {
                    log.append("  (told)  ").append(turn.say).append("\n          → ").append(shown).append('\n');
                    continue;
                }
                boolean ok = Pattern.compile(turn.expect, Pattern.CASE_INSENSITIVE).matcher(a).find();
                int[] s = areas.computeIfAbsent(turn.area, x -> new int[2]);
                s[1]++;
                s[0] += ok ? 1 : 0;
                log.append(ok ? "  RIGHT   " : "  WRONG   ").append('[').append(turn.area).append("] ").append(turn.say.replace("\n", " "))
                        .append("\n          → ").append(shown).append('\n');
            }
        } finally {
            Plug.asset = was;
        }
        int right = 0, all = 0;
        StringBuilder r = new StringBuilder("IQ-style probe, seed " + seed + "\n\n");
        for (Map.Entry<String, int[]> e : areas.entrySet()) {
            right += e.getValue()[0];
            all += e.getValue()[1];
            r.append(String.format("  %-14s %2d / %-2d  %3d%%%n", e.getKey(), e.getValue()[0], e.getValue()[1], Math.round(100.0 * e.getValue()[0] / e.getValue()[1])));
        }
        r.append(String.format("%n  overall        %2d / %-2d  %3d%%%n%n", right, all, Math.round(100.0 * right / all))).append(log);
        File out = new File("build/iq/report-" + seed + ".txt");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), r.toString().getBytes("UTF-8"));
        System.out.println(r);
    }
}
