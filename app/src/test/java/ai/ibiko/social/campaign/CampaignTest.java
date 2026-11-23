package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

/** Campaigns: the brain's plan checked line by line, and kept on the phone whole. */
public class CampaignTest {
    private static final List<CampaignPlanner.Seat> SEATS = Arrays.asList(
            new CampaignPlanner.Seat("fb1", "facebook", "Shop page"), new CampaignPlanner.Seat("x1", "x", "Brand"));

    private static Campaign brief() {
        Campaign c = new Campaign();
        c.id = "c1";
        c.brief.business = "Ada's Bakery, Lagos";
        c.brief.offer = "Custom birthday cakes, delivered";
        c.brief.audience = "Parents in Lagos";
        c.brief.goal = "Orders on WhatsApp";
        c.days = 2;
        c.perDay = 1;
        c.startAt = 1_800_000_000_000L; // a fixed day
        return c;
    }

    /** A brain that answers the outline, then each post, as a model would (with a little prose around the JSON). */
    /** Every post the fake writes is numbered, so no two are the same words. */
    private static final java.util.concurrent.atomic.AtomicInteger WRITTEN = new java.util.concurrent.atomic.AtomicInteger();

    private static CampaignPlanner.Brain fake() {
        return (system, prompt, tokens) -> {
            if (prompt.contains("Plan its strategy as JSON")) {
                return "Here is the plan:\n```json\n{\"name\":\"Birthday cakes\",\"themes\":[\"Behind the oven\",\"Happy customers\"],"
                        + "\"searches\":[\"need a birthday cake Lagos\"],\"slots\":["
                        + "{\"day\":1,\"time\":\"09:30\",\"account\":\"fb1\",\"theme\":\"Behind the oven\",\"angle\":\"How a cake is made\"},"
                        + "{\"day\":1,\"time\":\"10:00\",\"account\":\"fb1\",\"theme\":\"x\",\"angle\":\"a second one on day 1: dropped\"},"
                        + "{\"day\":2,\"time\":\"18:15\",\"account\":\"x1\",\"theme\":\"Happy customers\",\"angle\":\"A party saved\"},"
                        + "{\"day\":9,\"time\":\"09:00\",\"account\":\"x1\",\"theme\":\"x\",\"angle\":\"past the end: dropped\"},"
                        + "{\"day\":2,\"time\":\"25:00\",\"account\":\"fb1\",\"theme\":\"x\",\"angle\":\"no such time: dropped\"},"
                        + "{\"day\":2,\"time\":\"08:00\",\"account\":\"someone-else\",\"theme\":\"x\",\"angle\":\"not chosen: dropped\"}]}\n```";
            }
            if (prompt.contains("Design the picture template")) {
                // one theme gets a template with a script in it, the other none at all (the built-in card is used)
                // one design for the whole campaign, with a script in it to be taken out
                return "```html\n<!doctype html><html><body onload=\"steal()\"><script>steal()</script>"
                        + "<h1>{{HEADLINE}}</h1><p>{{LINE}}</p><i>{{BRAND}}</i></body></html>\n```";
            }
            StringBuilder posts = new StringBuilder("{\"posts\":[");
            String[] lines = prompt.split("\n");
            int n = 0;
            for (String l : lines) {
                if (l.matches("^\\d+\\. .*")) {
                    n++;
                    // each post its own words, as a model writes them (the same words twice would be a repeat)
                    String text = l.contains(". x ") ? WRITTEN.incrementAndGet() + new String(new char[400]).replace('\0', 'a')
                            : "Fresh cakes for your party. Order now! Batch " + WRITTEN.incrementAndGet() + ".";
                    posts.append(n > 1 ? "," : "").append("{\"n\":").append(n).append(",\"text\":\"").append(text)
                            .append("\",\"headline\":\"Cakes <b>& more</b>\",\"line\":\"Baked fresh\"}");
                }
            }
            return posts.append("]}").toString();
        };
    }

    @Test
    public void theBrainsPlanIsCheckedBeforeAnythingIsKept() {
        Campaign c = brief();
        String why = CampaignPlanner.build(c, SEATS, fake(), null, null, TimeZone.getTimeZone("Africa/Lagos"), 4);
        assertNull(why);
        assertEquals("Birthday cakes", c.name);
        assertEquals(Arrays.asList("need a birthday cake Lagos"), c.searches);
        assertEquals(2, c.items.size()); // one a day an account; no day past the end, no bad time, no stranger's account
        Campaign.Item fb = c.items.get(0), x = c.items.get(1);
        assertEquals("fb1", fb.account);
        assertEquals("facebook", fb.network);
        assertTrue(fb.at < x.at);
        assertTrue("X's 280 characters", x.text.length() <= 280);
        assertFalse("no script in a picture", fb.imageHtml.contains("<script"));
        assertFalse("no event handler in a picture", fb.imageHtml.contains("onload"));
        assertFalse("no business name on the picture", fb.imageHtml.contains("Ada"));
        assertTrue("the theme as the picture's tag", fb.imageHtml.contains("Behind the oven"));
        assertTrue("the post's words go in as text, not markup", fb.imageHtml.toLowerCase().contains("&lt;b&gt;")
                && !fb.imageHtml.contains("<b>"));
        assertFalse("each theme its own designed layout", x.imageHtml.equals(fb.imageHtml.replace("Behind the oven", "Happy customers")));
        assertTrue(x.imageHtml.contains("fonts.googleapis.com"));
        assertEquals(Campaign.Status.DRAFT, c.status); // nothing runs before the person approves
        Campaign phone = brief();
        assertNull(CampaignPlanner.build(phone, SEATS, fake(), null, null, TimeZone.getTimeZone("Africa/Lagos"), 1));
        assertTrue("the phone's own model gets the same designed layouts", phone.items.get(0).imageHtml.contains("fonts.googleapis.com"));
    }

    @Test
    public void fromAStrategyAloneTheCalendarIsMadeHere() {
        Campaign c = brief();
        c.days = 3;
        c.perDay = 2;
        // counted from several threads at once (the planner asks for batches side by side): an atomic count
        final java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        CampaignPlanner.Brain brain = (system, prompt, tokens) -> {
            calls.incrementAndGet();
            if (prompt.contains("Plan its strategy as JSON")) {
                return "{\"name\":\"Cakes\",\"themes\":[{\"name\":\"Behind the oven\",\"angles\":[\"How a cake is made\",\"Our flour\"]},"
                        + "{\"name\":\"Happy customers\",\"angles\":[\"A party saved\"]}],\"searches\":[\"cake Lagos\"],"
                        + "\"times\":{\"facebook\":[\"10:00\",\"19:00\"],\"x\":[\"08:00\",\"20:00\"]}}";
            }
            if (prompt.contains("Design the picture template")) {
                return "Sorry.";
            }
            StringBuilder posts = new StringBuilder("{\"posts\":[");
            int n = 0;
            for (String l : prompt.split("\n")) {
                if (l.matches("^\\d+\\. .*")) {
                    n++;
                    posts.append(n > 1 ? "," : "").append("{\"n\":").append(n).append(",\"text\":\"Post ").append(n)
                            .append(" with plenty of words in it\",\"headline\":\"H\",\"line\":\"L\"}");
                }
            }
            return posts.append("]}").toString();
        };
        assertNull(CampaignPlanner.build(c, SEATS, brain, null, null, TimeZone.getTimeZone("UTC"), 4));
        assertEquals("3 days, 2 a day, 2 accounts", 12, c.items.size());
        assertEquals("the strategy, two batches of posts, and a picture designed for each of the 12 posts", 15, calls.get());
        java.util.Set<String> themes = new java.util.HashSet<String>();
        for (int i = 0; i < c.items.size(); i++) {
            themes.add(c.items.get(i).theme);
            if (i > 0) {
                assertTrue("in time order", c.items.get(i - 1).at <= c.items.get(i).at);
            }
        }
        assertEquals("both themes used", 2, themes.size());
    }

    @Test
    public void aReplyWithNoPlanIsSaidPlainly() {
        Campaign c = brief();
        assertEquals("The brain's plan couldn't be read. Try again.",
                CampaignPlanner.build(c, SEATS, (s, p, t) -> "Sorry, I can't help.", null, null, TimeZone.getDefault()));
        assertEquals("Choose at least one signed-in account.",
                CampaignPlanner.build(c, java.util.Collections.emptyList(), fake(), null, null, TimeZone.getDefault()));
    }

    @Test
    public void campaignsAreKeptWholeAndABadFileIsSetAside() throws Exception {
        File dir = Files.createTempDirectory("camp").toFile();
        CampaignStore store = new CampaignStore(dir);
        Campaign c = brief();
        CampaignPlanner.build(c, SEATS, fake(), null, null, TimeZone.getDefault());
        c.status = Campaign.Status.ACTIVE;
        store.put(c);
        Campaign back = new CampaignStore(dir).get("c1");
        assertNotNull(back);
        assertEquals(Campaign.Status.ACTIVE, back.status);
        assertEquals(2, back.items.size());
        assertEquals(c.items.get(1).text, back.items.get(1).text);
        assertEquals(Arrays.asList("need a birthday cake Lagos"), back.searches);
        Files.write(new File(dir, "campaigns.json").toPath(), "{not json".getBytes("UTF-8"));
        assertTrue(new CampaignStore(dir).all().isEmpty());
        assertTrue("kept for a look", new File(dir, "campaigns.json.unreadable").isFile());
    }

    @Test
    public void onAnApiEachPostGetsAPictureTheModelDesigned() {
        Campaign c = brief();
        final int[] designs = {0};
        CampaignPlanner.Brain brain = (system, prompt, tokens) -> {
            if (prompt.contains("Design the picture for this")) {
                designs[0]++;
                return "<!doctype html><html><body><svg viewBox=\"0 0 10 10\"><circle r=\"4\"/></svg><h1>model design "
                        + designs[0] + "</h1></body></html>";
            }
            return fake().ask(system, prompt, tokens);
        };
        assertNull(CampaignPlanner.build(c, SEATS, brain, null, null, TimeZone.getTimeZone("UTC"), 4));
        assertEquals("one design a post", c.items.size(), designs[0]);
        for (Campaign.Item it : c.items) {
            assertTrue(it.designed);
            assertTrue(it.imageHtml.contains("model design"));
        }
    }

    /** A bigger plan: 3 days, 6 posts a day on each of the two accounts (36 posts, four batches). */
    private static CampaignPlanner.Brain big() {
        CampaignPlanner.Brain small = fake();
        return (system, prompt, tokens) -> {
            if (prompt.contains("Plan its strategy as JSON")) {
                StringBuilder slots = new StringBuilder();
                for (int day = 1; day <= 3; day++) {
                    for (int k = 0; k < 6; k++) {
                        for (String acc : new String[] {"fb1", "x1"}) {
                            slots.append(slots.length() == 0 ? "" : ",").append("{\"day\":").append(day).append(",\"time\":\"")
                                    .append(String.format("%02d:%02d", 8 + k * 2, acc.equals("fb1") ? 0 : 30))
                                    .append("\",\"account\":\"").append(acc).append("\",\"theme\":\"Behind the oven\",\"angle\":\"Post ")
                                    .append(day).append(k).append(acc).append("\"}");
                        }
                    }
                }
                return "{\"name\":\"Cakes\",\"themes\":[\"Behind the oven\"],\"searches\":[],\"slots\":[" + slots + "]}";
            }
            return small.ask(system, prompt, tokens);
        };
    }

    /** Posts reach the studio batch by batch, to read while the rest are written, not all at the end. */
    @Test
    public void postsArriveAsTheyAreWritten() {
        Campaign c = brief();
        c.days = 3;
        c.perDay = 6;
        final List<Integer> seen = new java.util.ArrayList<Integer>();
        CampaignPlanner.Progress p = new CampaignPlanner.Progress() {
            @Override
            public void at(String what) {
            }

            @Override
            public void ready(Campaign plan, int ready, int total) {
                seen.add(ready);
                assertEquals(36, total);
                assertEquals(ready, plan.items.size());
            }
        };
        assertNull(CampaignPlanner.build(c, SEATS, big(), p, null, TimeZone.getTimeZone("Africa/Lagos"), 1));
        assertEquals(36, c.items.size());
        assertTrue(seen.toString(), seen.size() >= 4 && seen.get(0) < 36 && seen.get(seen.size() - 1) == 36);
    }

    /** Stopping keeps the posts already written: they are never thrown away. */
    @Test
    public void stoppingKeepsWhatIsWritten() {
        Campaign c = brief();
        c.days = 3;
        c.perDay = 6;
        final boolean[] stop = {false};
        CampaignPlanner.Progress p = new CampaignPlanner.Progress() {
            @Override
            public void at(String what) {
            }

            @Override
            public void ready(Campaign plan, int ready, int total) {
                stop[0] = true; // stopped as soon as the first posts are in
            }
        };
        assertEquals("Stopped.", CampaignPlanner.build(c, SEATS, big(), p, () -> stop[0], TimeZone.getTimeZone("Africa/Lagos"), 1));
        assertTrue(c.items.size() + " kept", c.items.size() >= 10 && c.items.size() < 36);
    }
}
