package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;
import org.junit.Test;

/** Results: notifications counted once against the right post, growth added, and lessons drawn from enough posts. */
public class ResultsTest {
    private static Campaign.Item item(String id, String theme, String text, long at) {
        Campaign.Item it = new Campaign.Item();
        it.id = id;
        it.theme = theme;
        it.text = text;
        it.at = at;
        it.network = "facebook";
        it.state = Campaign.State.POSTED;
        return it;
    }

    @Test
    public void eachNotificationIsCountedOnceAndItsGrowthAdded() throws Exception {
        File dir = Files.createTempDirectory("res").toFile();
        Results r = new Results(dir);
        Campaign.Item cakes = item("a", "Offers", "Fresh cakes for your party this weekend, order now!", 0);
        Campaign.Item other = item("b", "Tips", "How to keep a sponge cake soft for three days", 0);
        String page = "Ada and 4 others reacted to your post: \"Fresh cakes for your party this weekend…\"\n2h\nLINK https://m.facebook.com/x\n"
                + "Tunde commented on your post: \"How to keep a sponge cake soft for three…\"\n\nSomeone liked a photo you're tagged in";
        assertTrue(r.read(page, Arrays.asList(cakes, other)));
        assertEquals(5, r.of("a").reactions);
        assertEquals(1, r.of("b").talk);
        assertFalse("read again: nothing new", r.read(page, Arrays.asList(cakes, other)));
        r.read(page.replace("and 4 others", "and 6 others"), Arrays.asList(cakes, other));
        assertEquals("only the 2 new people", 7, r.of("a").reactions);
        r.save();
        Results back = new Results(dir);
        assertEquals(7, back.of("a").reactions);
        assertFalse("kept across a restart", back.read(page.replace("and 4 others", "and 6 others"), Arrays.asList(cakes, other)));
    }

    @Test
    public void lessonsNeedEnoughPostsAndNameTheBestThemeAndTime() throws Exception {
        Results r = new Results(Files.createTempDirectory("res").toFile());
        TimeZone utc = TimeZone.getTimeZone("UTC");
        Campaign c = new Campaign();
        c.id = "c";
        long evening = 19 * 3_600_000L, morning = 9 * 3_600_000L;
        StringBuilder page = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            c.items.add(item("o" + i, "Offers", "Offer number " + i + " for this week only, big discount", evening + i * 86_400_000L));
            c.items.add(item("t" + i, "Tips", "Tip number " + i + " about keeping your home safe and clean", morning + i * 86_400_000L));
            page.append("Ada and 3 others reacted to your post: Offer number ").append(i).append(" for this week only\n\n");
        }
        assertEquals("", r.lessons(Collections.emptyList(), utc));
        r.read(page.toString(), c.items);
        String lessons = r.lessons(Collections.singletonList(c), utc);
        assertTrue(lessons, lessons.contains("Theme “Offers” did best") && lessons.contains("evenings"));
        String sum = r.summary(c, utc);
        assertTrue(sum, sum.startsWith("8 posts out: 16 reactions"));
    }
}
