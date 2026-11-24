package ai.ibiko.ibiko1;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** The second composition pasted on the phone (bold title and label, numbered tasks), every task worked out. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WorkshopStoryTest {
    static final String PASTED = "**The Workshop at Onitsha**\n"
            + "Chinedu runs an electronics repair workshop in Onitsha, the commercial hub of Anambra State. From Monday to Friday, he opens at 8:30am "
            + "and closes at 6:00pm, taking a 45-minute lunch break at 1:00pm. On Tuesday morning, he had 24 broken smartphones, 15 laptops, and 40 "
            + "charging cables in stock.\n"
            + "By midday, he had repaired 2/3 of the smartphones and sold 12 charging cables at 1,500 naira each. His apprentice, Musa, accidentally "
            + "dropped 3 of the remaining unrepaired smartphones, rendering them completely unrepairable, and then went out to buy 10 new charging "
            + "cables to restock the shelf.\n"
            + "In the afternoon, a regular client named Zainab brought in 4 tablets for screen replacement. Standard screen replacement costs 18,000 "
            + "naira per tablet, but Chinedu gave her a 15% loyalty discount on the total bill. Just before closing, power went out, and Chinedu "
            + "laughed and said, \"Abeg, fire up the gen, work must go on.\" He finished repairing 5 laptops before locking up.\n"
            + "Chinedu has more years of experience than Zainab, while Musa has fewer years of experience than Zainab. Every technician at the plaza "
            + "must wear a safety badge. Musa is an apprentice, not a certified technician.\n"
            + "**Tasks:**\n"
            + " 1. How many hours and minutes does Chinedu actually spend working on Tuesday (excluding his lunch break)?\n"
            + " 2. How many unrepaired smartphones were still salvageable at the end of the day?\n"
            + " 3. How much money did Chinedu make from selling charging cables on Tuesday?\n"
            + " 4. How many charging cables were in stock at closing time?\n"
            + " 5. How much did Zainab pay in total for the 4 tablet screen replacements after the discount?\n"
            + " 6. Rank Chinedu, Musa, and Zainab from least experienced to most experienced.\n"
            + " 7. Is Musa required by plaza rules to wear a safety badge?\n"
            + " 8. What does \"Abeg, fire up the gen\" mean in this context?\n"
            + " 9. Is Onitsha the capital of Anambra State? (Check carefully!)\n"
            + " 10. Summarise the story in one sentence without confusing the characters' roles.";

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

    @Test(timeout = 300000)
    public void everyTaskWorkedOut() throws Exception {
        java.util.function.Function<String, java.io.InputStream> was = Plug.asset;
        Plug.asset = p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        };
        Plug.web = Plug.NONE;
        try {
            Store.reset();
            Chat chat = new Chat();
            File files = Files.createTempDirectory("workshop").toFile();
            Ibiko1.warm(files);
            Ibiko1 brain = new Ibiko1(chat, files);
            brain.answer(PASTED);
            for (int i = 0; i < 2400 && chat.said.isEmpty(); i++) {
                Thread.sleep(10);
            }
            String r = chat.said.isEmpty() ? null : chat.said.get(0);
            assertNotNull(r);
            System.out.println("WORKSHOP\n" + r);
            File out = new File("build/routing/workshop.txt");
            out.getParentFile().mkdirs();
            Files.write(out.toPath(), r.getBytes("UTF-8"));
            assertTrue(r, r.matches("(?s).*1\\. 8 hours 45 minutes.*"));
            assertTrue(r, r.matches("(?s).*2\\. 5 [^\\n]*smartphones.*"));
            assertTrue(r, r.matches("(?s).*3\\. [^\\n]*18,000 naira.*"));
            assertTrue(r, r.matches("(?s).*4\\. 38 charging cables.*"));
            assertTrue(r, r.matches("(?s).*5\\. Zainab paid 61,200 naira.*"));
            assertTrue(r, r.matches("(?s).*6\\. Musa, Zainab, Chinedu.*"));
            assertTrue(r, r.matches("(?s).*7\\. No\\b.*"));
            assertTrue(r, r.matches("(?s).*8\\. [^\\n]*generator.*"));
            assertTrue(r, r.matches("(?s).*9\\. No\\b[^\\n]*Awka.*"));
            assertTrue(r, r.matches("(?s).*10\\. Chinedu, [^\\n]*repair[^\\n]*Onitsha.*"));
        } finally {
            Plug.asset = was;
        }
    }
}
