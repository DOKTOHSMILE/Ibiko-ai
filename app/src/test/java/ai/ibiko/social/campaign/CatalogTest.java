package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

/** The catalog: products kept whole, their photos put in pictures when drawn, and the planner using real prices. */
public class CatalogTest {
    @Test
    public void productsAreKeptAndPhotosGoInWhenAPictureIsDrawn() throws Exception {
        File dir = Files.createTempDirectory("cat").toFile();
        Catalog cat = new Catalog(dir);
        Product p = new Product();
        p.id = "p1";
        p.name = "Stun torch";
        p.price = "₦18,000";
        cat.put(p);
        Files.write(cat.photoFile("p1").toPath(), new byte[]{(byte) 0xFF, (byte) 0xD8, 1, 2});
        assertEquals("Stun torch", new Catalog(dir).get("p1").name);
        String html = "<img src=\"" + Catalog.PHOTO + "p1\"><img src=\"" + Catalog.PHOTO + "gone\">";
        String drawn = cat.inline(html);
        assertTrue(drawn.contains("data:image/jpeg;base64,/9gBAg=="));
        assertTrue("a photo that is gone becomes a blank", drawn.contains(Catalog.BLANK));
        assertFalse(drawn.contains(Catalog.PHOTO));
        cat.remove("p1");
        assertFalse(cat.photoFile("p1").exists());
    }

    @Test
    public void thePlanShowsTheRightProductAtItsRealPrice() {
        Campaign c = new Campaign();
        c.id = "c1";
        c.brief.business = "Valineto3 Enterprises";
        c.days = 1;
        c.perDay = 1;
        c.startAt = 1_800_000_000_000L;
        Product cyl = new Product();
        cyl.id = "cyl";
        cyl.name = "12.5kg gas cylinder";
        cyl.price = "₦45,000";
        cyl.photo = "cyl.jpg";
        c.brief.products.add(cyl);
        c.handoff.add("instagram");
        final String[] postsAsked = {""};
        CampaignPlanner.Brain brain = (system, prompt, tokens) -> {
            if (prompt.contains("Plan its strategy as JSON")) {
                return "{\"name\":\"Gas\",\"themes\":[\"Safety\"],\"slots\":["
                        + "{\"day\":1,\"time\":\"10:00\",\"account\":\"fb1\",\"theme\":\"Safety\",\"angle\":\"Cook safely\",\"product\":\"P1\"},"
                        + "{\"day\":1,\"time\":\"12:00\",\"account\":\"handoff:instagram\",\"theme\":\"Safety\",\"angle\":\"Refill day\",\"product\":\"P9\"}]}";
            }
            if (prompt.contains("Design the picture template")) {
                return "<html><body><h1>{{HEADLINE}}</h1><p>{{LINE}}</p><b>{{BRAND}}</b></body></html>"; // no photo place
            }
            postsAsked[0] += prompt;
            return "{\"posts\":[{\"n\":1,\"text\":\"Cook safely with a 12.5kg cylinder at ₦45,000.\",\"headline\":\"Cook safe\",\"line\":\"Delivered\"},"
                    + "{\"n\":2,\"text\":\"Refill day is here!\",\"headline\":\"Refill\",\"line\":\"Today\"}]}";
        };
        List<CampaignPlanner.Seat> seats = Arrays.asList(new CampaignPlanner.Seat("fb1", "facebook", "Shop"),
                new CampaignPlanner.Seat(CampaignPlanner.HANDOFF + "instagram", "instagram", "Instagram (you tap Share)"));
        assertEquals(null, CampaignPlanner.build(c, seats, brain, null, null, TimeZone.getTimeZone("UTC")));
        assertTrue("the brain is given the real price", postsAsked[0].contains("12.5kg gas cylinder, ₦45,000"));
        assertTrue(postsAsked[0].contains("shows P1"));
        Campaign.Item fb = c.items.get(0), ig = c.items.get(1);
        assertEquals("cyl", fb.product);
        assertTrue("the photo card, since the template has no photo place", fb.imageHtml.contains(Catalog.PHOTO + "cyl"));
        assertEquals("P9 isn't in the brief", "", ig.product);
        assertTrue(ig.handoff);
        assertFalse(fb.handoff);
    }
}
