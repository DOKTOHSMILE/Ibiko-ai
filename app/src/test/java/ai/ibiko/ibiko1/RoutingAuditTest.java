package ai.ibiko.ibiko1;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The brain's routing, checked as one conversation: every kind of message (talk, statements about the person, facts,
 * follow-ups, logic and order spread over turns, stories, compositions, sayings, opinions, requests, sums, time, text)
 * must reach the part that understands it, and that part's answer must come back. Each line is a message, the part it
 * should reach (by what its answer holds), and the answer is written to build/routing/report.txt with any misroute.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class RoutingAuditTest {
    private static final class Chat implements Ibiko1.Host {
        final List<String> said = Collections.synchronizedList(new ArrayList<String>());
        final List<String> steps = Collections.synchronizedList(new ArrayList<String>());
        @Override public String persona() { return "obi"; }
        @Override public void step(String step) { steps.add(step); }
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

    /** {area, message, what the right part's answer holds (a pattern), or null when any answer will do} */
    static final String[][] CONVERSATION = {
        {"talk", "Hello Ibiko", "(?i)^(good|hello|hi)"},
        {"talk", "Good", "(?i)^glad"},
        {"talk", "Thanks a lot", "(?i)(welcome|pleasure|glad|anytime)"},
        {"self", "What can you do?", "(?i)\\b(can|i)\\b"},
        {"person", "My name is Ada and I live in Kano.", "(?i)\\bAda\\b"},
        {"person", "What is my name?", "\\bAda\\b"},
        {"person-chain", "What is the capital of the state I live in?", "(?i)\\bKano\\b"},
        {"tally", "I have 5 mangoes.", "(?i)5 mangoes"},
        {"tally", "I gave away 2 mangoes.", "(?i)3 mangoes"},
        {"tally", "How many mangoes do I have?", "\\b3\\b"},
        {"facts", "What is the capital of Ghana?", "\\bAccra\\b"},
        {"follow-up", "And what is its currency?", "(?i)cedi"},
        {"facts", "Who wrote Things Fall Apart?", "Achebe"},
        {"follow-up", "When was he born?", "1930"},
        {"compare", "Which is bigger, Nigeria or Ghana?", "^Nigeria"},
        {"logic-turns", "All gloops are flarns.", null},
        {"logic-turns", "Zed is a gloop.", null},
        {"logic-turns", "Is Zed a flarn?", "^Yes"},
        {"order-turns", "Obi is taller than Emeka.", null},
        {"order-turns", "Emeka is taller than Femi.", null},
        {"order-turns", "Who is the shortest?", "\\bFemi\\b"},
        {"story-one-message", "Tunde is a farmer in Jos. Tunde keeps 4 cows and grows maize. How many cows does Tunde keep?", "\\b4\\b"},
        {"story-one-message", "Kemi baked 40 cakes and sold 15. How many cakes were left?", "\\b25\\b"},
        {"saying", "What does break a leg mean?", "(?i)luck"},
        {"word", "What does wahala mean?", "(?i)trouble"},
        {"word", "What is the opposite of ancient?", "(?i)(new|modern|young)"},
        {"grammar", "What is the plural of child?", "children"},
        {"typo", "wat is the captal of kenya", "Nairobi"},
        {"sums", "What is 25% of 480?", "\\b120\\b"},
        {"sums", "Solve 3x + 4 = 19", "\\b5\\b"},
        {"word-problem", "I paid 2000 naira for something that costs 1350. How much change do I get?", "\\b650\\b"},
        {"time", "It is 9am now. What time will it be in 5 hours?", "(?i)2\\s?pm|14:00"},
        {"units", "Convert 10 km to miles", "6\\.2"},
        {"text", "How many letters are in the word Ibadan?", "\\b6\\b"},
        {"sequence", "What comes next: 2, 4, 6, 8?", "\\b10\\b"},
        {"analogy", "Hot is to cold as up is to what?", "(?i)\\bdown\\b"},
        {"reshape", "Tell me about Lagos", null},
        {"reshape", "Shorter please", "(?i)lagos"},
        {"opinion", "What do you think about solar power?", "(?i)(view|don't have a view|haven't read)"},
        {"code", "Write a function to reverse a string in Python", "```python"},
        {"error", "TypeError: 'NoneType' object is not subscriptable", "(?i)none"},
        {"site", "Build me a website for my shop called Ada Stores in Kano", "<!doctype html>"},
        {"honest", "What will the weather be tomorrow?", "(?i)(forecast|online|can't|cannot)"},
        {"learning-talk", "Start a learning conversation with my API model", "(?i)API model"},
        {"memory", "What have you learned so far?", "(?i)(confirm|learned|answers)"},
    };

    @Test(timeout = 900000)
    public void everyMessageReachesThePartThatUnderstandsIt() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        StringBuilder report = new StringBuilder();
        List<String> misrouted = new ArrayList<String>();
        try {
            Store.reset();
            Chat chat = new Chat();
            File files = Files.createTempDirectory("routing").toFile();
            Ibiko1.warm(files);
            Ibiko1 brain = new Ibiko1(chat, files);
            for (String[] line : CONVERSATION) {
                int n = chat.said.size();
                chat.steps.clear();
                brain.answer(line[1]);
                for (int i = 0; i < 1500 && chat.said.size() == n; i++) {
                    Thread.sleep(10);
                }
                String a = chat.said.size() > n ? chat.said.get(n) : "(no reply)";
                boolean ok = line[2] == null || Pattern.compile(line[2]).matcher(a).find();
                String shown = a.replace("\n", " | ");
                report.append(ok ? "  ok        " : "  MISROUTE  ").append(String.format("%-18s", line[0])).append(line[1]).append("\n            → ")
                        .append(shown.length() > 200 ? shown.substring(0, 200) + "…" : shown).append("\n            steps: ")
                        .append(String.join(" | ", chat.steps).replaceAll("(.{0,240}).*", "$1")).append('\n');
                if (!ok) {
                    misrouted.add(line[0] + ": " + line[1]);
                    // what the shared knowledge held at the miss, to tell a wrong route from missing knowledge
                    report.append("            built-in facts: ").append(Ibiko1.builtIn().size()).append(", currency of Ghana: ")
                            .append(Ibiko1.builtIn().answer("What is the currency of Ghana?")).append(", asset: ")
                            .append(Plug.asset.apply("knowledge/world_facts.tsv.ibk") != null).append('\n');
                }
            }
        } finally {
            Plug.asset = was;
        }
        String head = "Routing audit: " + (CONVERSATION.length - misrouted.size()) + " of " + CONVERSATION.length + " reached the right part\n\n";
        File out = new File("build/routing/report.txt");
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), (head + report).getBytes("UTF-8"));
        System.out.println(head + report);
        assertTrue("misrouted: " + misrouted + "\n" + report, misrouted.isEmpty() || "1".equals(System.getenv("ROUTING_REPORT_ONLY")));
    }
}
