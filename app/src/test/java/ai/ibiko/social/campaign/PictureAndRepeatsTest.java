package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Strict about what goes out: words that read on their picture, and no post put out twice on one account. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PictureAndRepeatsTest {

    @Test
    public void contrastIsWcags() {
        assertEquals(21.0, Layout.contrast(0x000000, 0xffffff), 0.01);
        assertEquals(1.0, Layout.contrast(0x777777, 0x777777), 0.01);
        assertTrue("yellow on pale yellow doesn't read", Layout.contrast(0xf5b83d, 0xfff3c4) < Layout.LEAST);
        assertTrue("white on navy reads", Layout.contrast(0xffffff, 0x101a40) > 7);
    }

    @Test
    public void theGroundBehindTheWordsIsFound() {
        int yellow = 0xf5b83d, pale = 0xfff3c4;
        int[] samples = new int[100];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = i % 3 == 0 ? yellow : pale; // letters a third of the box, the rest the ground
        }
        assertEquals(pale, Layout.ground(samples, yellow));
        java.util.Arrays.fill(samples, yellow);
        assertEquals("words on their own colour: the text colour itself", yellow, Layout.ground(samples, yellow));
    }

    @Test
    public void thePagesReportIsRead() {
        String said = "\"{\\\"boxes\\\":[{\\\"x\\\":10,\\\"y\\\":20,\\\"w\\\":300,\\\"h\\\":80,\\\"c\\\":\\\"255,255,255\\\"}],"
                + "\\\"overlap\\\":true,\\\"off\\\":false}\"";
        Layout.Report r = Layout.Report.of(said);
        assertEquals(1, r.boxes.size());
        assertEquals(0xffffff, r.boxes.get(0)[4]);
        assertEquals("words overlap", r.problem());
        assertNull(Layout.Report.of("null").problem());
        String js = Layout.legible("0:d,2:l");
        assertTrue(js, js.contains("t[0]") && js.contains("#ffffff") && js.contains("t[2]") && js.contains("#111111"));
    }

    @Test
    public void theFormulasLitWordAlwaysReads() {
        for (long seed = 1; seed <= 300; seed++) {
            java.util.Random r = new java.util.Random(seed);
            DesignGen.Palette p = DesignGen.palette(r);
            String accent = DesignGen.readable(p.dark ? p.a2 : p.a1, p);
            int a = Integer.parseInt(accent.substring(1), 16);
            assertTrue("seed " + seed + ": " + accent + " on " + p.bg,
                    Layout.contrast(a, Integer.parseInt(p.bg.substring(1), 16)) >= Layout.LEAST);
        }
    }

    @Test
    public void aPostIsNeverPutOutTwiceOnOneAccount() throws Exception {
        File f = new File(Files.createTempDirectory("posted").toFile(), "posted.tsv");
        PostedLog log = new PostedLog(f);
        long now = 1_000_000_000_000L;
        log.add("acct1", "A website isn't a luxury; it's your 24/7 storefront. #Lagos https://wa.me/234", "Grow With Your Business", now);
        PostedLog again = new PostedLog(f); // kept on the phone
        assertTrue("the same words, other links and tags", again.sameWords("acct1",
                "A website isn't a luxury — it's your 24/7 storefront! #Growth https://wa.me/999", now + 1000) > 0);
        assertEquals("another account may post it", 0, again.sameWords("acct2", "A website isn't a luxury; it's your 24/7 storefront.", now));
        assertTrue(again.sameHeadline("acct1", "grow with your business!", now) > 0);
        assertEquals("long after: allowed again", 0, again.sameWords("acct1", "A website isn't a luxury; it's your 24/7 storefront.",
                now + PostedLog.WINDOW_MS + 1));
        assertEquals("Timeline questions? We hit deadlines", CampaignRunner.firstWords("Timeline questions? We hit deadlines without cutting corners. #Lagos", 5));
    }

    @Test
    public void oneCampaignHasNoRepeats() {
        Campaign c = new Campaign();
        Campaign.Item first = new Campaign.Item();
        first.account = "fb1";
        first.text = "Grow your business online. #Lagos";
        first.headline = "Grow With Your Business";
        c.items.add(first);
        Campaign.Item same = new Campaign.Item();
        same.account = "fb1";
        same.text = "Grow your business online! #Growth https://wa.me/1";
        same.headline = "Something new";
        assertTrue(CampaignPlanner.repeats(c, same));
        same.account = "x1";
        assertTrue("another account may carry it", !CampaignPlanner.repeats(c, same));
        Campaign.Item other = new Campaign.Item();
        other.account = "fb1";
        other.text = "Deadlines met, every time.";
        other.headline = "grow with your business";
        assertTrue(!CampaignPlanner.repeats(c, other));
        assertTrue(CampaignPlanner.sameHeadline(c, other));
    }

    @Test
    public void onlyAPostNeverPressedIsTriedAgain() {
        assertTrue(ai.ibiko.social.SocialPoster.retryable("waiting"));
        assertTrue(ai.ibiko.social.SocialPoster.retryable("noimage"));
        assertTrue(ai.ibiko.social.SocialPoster.retryable("nottyped"));
        assertTrue(!ai.ibiko.social.SocialPoster.retryable("clicked"));
        assertTrue(!ai.ibiko.social.SocialPoster.retryable("checking"));
        assertTrue(!ai.ibiko.social.SocialPoster.retryable("done"));
        assertTrue(!ai.ibiko.social.SocialPoster.retryable("signedout"));
    }
}
