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

/** The composition pasted on the phone (its task numbers lost in the paste), every task worked out from the story. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class MarketStoryTest {
    static final String PASTED = "The Market at Jos\n"
            + "Amina is a trader in Jos, the capital of Plateau State. Every Saturday she opens her stall at 7am and closes it at 4pm. This Saturday "
            + "she brought 120 tomatoes, 45 yams and 30 bags of garri.\n"
            + "By noon she had sold 3/4 of the tomatoes and 18 yams. Her friend Tunde, who sells suya at the next stall, bought 6 bags of garri for "
            + "his family. Each bag of garri costs 2,500 naira, and each yam costs 1,200 naira.\n"
            + "In the afternoon, a customer named Ngozi asked for a 20% discount on 10 yams. Amina agreed. Later, it began to rain, and Amina said, "
            + "\"No wahala, the rain will pass.\" It did, and she sold 5 more bags of garri before closing.\n"
            + "Amina is older than Tunde but younger than Ngozi. All traders in the market pay a stall fee. Tunde is a trader.\n"
            + "Tasks:\n"
            + "How many hours does Amina's stall stay open on Saturday?\n"
            + "How many tomatoes were left at noon?\n"
            + "How much money did Tunde pay for the garri?\n"
            + "How many bags of garri were left at closing time?\n"
            + "How much did Ngozi pay for the 10 yams after the discount?\n"
            + "Who is the oldest of Amina, Tunde and Ngozi?\n"
            + "Does Tunde pay a stall fee?\n"
            + "What does \"no wahala\" mean?\n"
            + "What is the capital of Plateau State?\n"
            + "Summarise the story in one sentence.";

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
            File files = Files.createTempDirectory("market").toFile();
            Ibiko1.warm(files);
            Ibiko1 brain = new Ibiko1(chat, files);
            brain.answer(PASTED);
            for (int i = 0; i < 2400 && chat.said.isEmpty(); i++) {
                Thread.sleep(10);
            }
            String r = chat.said.isEmpty() ? null : chat.said.get(0);
            assertNotNull(r);
            System.out.println("MARKET\n" + r);
            assertTrue(r, r.contains("1. 9 hours"));
            assertTrue(r, r.contains("2. 30 tomatoes at noon"));
            assertTrue(r, r.contains("3. Tunde paid 15,000 naira"));
            assertTrue(r, r.contains("4. 19 bags of garri"));
            assertTrue(r, r.contains("5. Ngozi paid 9,600 naira"));
            assertTrue(r, r.matches("(?s).*6\\. [^\\n]*Ngozi.*"));
            assertTrue(r, r.matches("(?s).*7\\. Yes.*"));
            assertTrue(r, r.matches("(?s).*8\\. [^\\n]*(problem|worry|fine).*"));
            assertTrue(r, r.contains("9. Jos is the capital of Plateau State."));
            assertTrue(r, r.matches("(?s).*10\\. Amina, a trader in Jos,.*"));
        } finally {
            Plug.asset = was;
        }
    }

    /** A different story, the same reasoning: nothing in it is the market's. */
    @Test
    public void anotherStoryTheSameWay() {
        PassageTasks t = PassageTasks.of("Kemi's Bakery\n"
                + "Kemi runs a bakery in Ibadan. Every day she opens at 6am and closes at 2pm. Today she baked 80 loaves and 40 cakes.\n"
                + "By noon she had sold half of the loaves and 12 cakes. Her neighbour Musa bought 10 cakes for a party. Each loaf costs 800 naira, "
                + "and each cake costs 3,000 naira. Later, a teacher named Ada asked for a 10% discount on 5 cakes. Kemi agreed.\n"
                + "Kemi is younger than Musa but older than Ada. All bakers in the town wake up early. Kemi is a baker.\n"
                + "Tasks:\n"
                + "How many hours is the bakery open?\n"
                + "How many loaves were left at noon?\n"
                + "How much did Musa pay for the cakes?\n"
                + "How many cakes were left at closing time?\n"
                + "How much did Ada pay after the discount?\n"
                + "Who is the youngest of Kemi, Musa and Ada?\n"
                + "Does Kemi wake up early?");
        assertNotNull(t);
        String r = t.reply();
        assertTrue(r, r.contains("1. 8 hours"));
        assertTrue(r, r.contains("2. 40 loaves at noon"));
        assertTrue(r, r.contains("3. Musa paid 30,000 naira"));
        assertTrue(r, r.contains("4. 13 cakes"));
        assertTrue(r, r.contains("5. Ada paid 13,500 naira"));
        assertTrue(r, r.matches("(?s).*6\\. Ada is the youngest.*"));
        assertTrue(r, r.matches("(?s).*7\\. Yes.*"));
    }
}
