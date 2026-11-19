package ai.ibiko.design;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.social.campaign.CampaignImage;
import org.junit.Test;

/** Pictures drawn as pages: the shape follows the ask, and only a clean page is drawn. */
public class HtmlDrawTest {
    @Test
    public void theShapeFollowsTheAsk() {
        assertArrayEquals(new int[]{1080, 1920}, HtmlDraw.shape("A phone wallpaper of a calm sea at night"));
        assertArrayEquals(new int[]{1080, 1350}, HtmlDraw.shape("a flyer for our Friday sale"));
        assertArrayEquals(new int[]{1920, 1080}, HtmlDraw.shape("YouTube thumbnail for my cooking video"));
        assertArrayEquals(new int[]{1080, 1080}, HtmlDraw.shape("a logo for Valineto3 Enterprises"));
        assertTrue(HtmlDraw.system(1920, 1080).contains("exactly 1920 by 1080 pixels"));
    }

    @Test
    public void theReplyBecomesACleanPage() {
        String reply = "Here is your design:\n```html\n<!doctype html><html><head><script>alert(1)</script></head>"
                + "<body onload=\"x()\"><div style=\"background:linear-gradient(#123,#456)\"><svg viewBox=\"0 0 10 10\">"
                + "<circle cx=\"5\" cy=\"5\" r=\"4\"/></svg></div></body></html>\n```\nEnjoy!";
        String page = CampaignImage.page(reply);
        assertTrue(page, page.startsWith("<!doctype html>"));
        assertTrue(page.endsWith("</html>"));
        assertTrue(page.contains("<svg"));
        assertFalse(page.contains("<script"));
        assertFalse(page.contains("onload"));
        assertFalse(page.contains("Enjoy"));
        assertEquals("", CampaignImage.page("Sorry, I can't draw."));
    }

    @Test
    public void onlyAPhotographGoesToAnImageModel() {
        assertTrue(HtmlDraw.wantsPhoto("a realistic photo of a woman cooking with a gas cooker"));
        assertTrue(HtmlDraw.wantsPhoto("Photograph of Lagos at sunset"));
        assertFalse(HtmlDraw.wantsPhoto("a flyer with a photo of our cylinder and the price"));
        assertFalse(HtmlDraw.wantsPhoto("a logo for Valineto3"));
        assertFalse(HtmlDraw.wantsPhoto("a cute cat"));
    }
}
