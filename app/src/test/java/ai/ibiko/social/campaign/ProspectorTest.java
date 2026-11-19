package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Leads and replies: only posts that were on the page, real fits, safe replies, and never the same post twice. */
public class ProspectorTest {
    private static final String PAGE = "Menu\n"
            + "Anyone know a good baker in Lagos for my son's 5th birthday cake?\nLINK https://x.com/ada/status/111?s=20\n"
            + "Just ate the best cake ever, so happy\nLINK https://x.com/bola/status/222\n"
            + "Need a custom wedding cake by June, Lekki area\nLINK https://x.com/chi/status/333\n"
            + "Same post again\nLINK https://x.com/ada/status/111\n"
            + "Settings\nLINK https://x.com/settings\n";

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.id = "c1";
        c.brief.business = "Ada's Bakery";
        c.brief.offer = "Custom cakes in Lagos";
        c.brief.link = "https://adasbakery.ng";
        c.searches.add("birthday cake Lagos");
        return c;
    }

    @Test
    public void postsAreReadFromThePageOnceEach() {
        List<Prospector.Found> f = Prospector.posts(PAGE);
        assertEquals(3, f.size()); // the repeat (same post, another query string) and the settings link are not posts
        assertTrue(f.get(0).snippet.startsWith("Anyone know a good baker"));
        assertEquals("https://x.com/chi/status/333", f.get(2).url);
    }

    @Test
    public void theBrainsPicksAreChecked() {
        List<Prospector.Found> f = Prospector.posts(PAGE);
        String reply = "{\"leads\":["
                + "{\"n\":1,\"fit\":0.9,\"why\":\"asks for a baker\",\"reply\":\"Happy birthday to him! We bake custom cakes in Lagos: https://adasbakery.ng @ada\"},"
                + "{\"n\":2,\"fit\":0.3,\"why\":\"no need\",\"reply\":\"Buy ours\"},"
                + "{\"n\":3,\"fit\":0.8,\"why\":\"wedding cake\",\"reply\":\"Congratulations! See http://evil.example/x for our menu\"},"
                + "{\"n\":3,\"fit\":0.9,\"why\":\"twice\",\"reply\":\"again\"},"
                + "{\"n\":9,\"fit\":0.9,\"why\":\"not on the page\",\"reply\":\"x\"},"
                + "{\"n\":1,\"fit\":7,\"why\":\"bad fit\",\"reply\":\"x\"}]}";
        List<Lead> picks = Prospector.picks(reply, "leads", f, campaign().brief, "x", true);
        assertEquals(2, picks.size()); // a low fit, a post twice and a number not on the list are left out
        assertTrue("the business's own link stays", picks.get(0).reply.contains("https://adasbakery.ng"));
        assertFalse("no @mention", picks.get(0).reply.contains("@ada"));
        assertFalse("no other link: a page can talk a model into adding one", picks.get(1).reply.contains("evil.example"));
        assertEquals("https://x.com/ada/status/111?s=20", picks.get(0).url);
    }

    @Test
    public void aPostFoundOnceIsNeverFoundAgain() throws Exception {
        File dir = Files.createTempDirectory("leads").toFile();
        LeadStore store = new LeadStore(dir);
        CampaignPlanner.Brain brain = (system, prompt, tokens) -> "{\"leads\":[{\"n\":1,\"fit\":0.9,\"why\":\"needs it\",\"reply\":\"We can help!\"},"
                + "{\"n\":2,\"fit\":0.85,\"why\":\"needs it\",\"reply\":\"Happy to help!\"}]}";
        Prospector.Reader reader = new Prospector.Reader() {
            @Override
            public String search(String account, String query) {
                return "Need a cake for Saturday please\nLINK https://x.com/a/status/1\nWho bakes gluten free in Lagos?\nLINK https://x.com/b/status/2\n";
            }

            @Override
            public String notifications(String account) {
                return "Do you deliver to Ikeja?\nLINK https://x.com/c/status/9\n";
            }
        };
        List<CampaignPlanner.Seat> seats = Arrays.asList(new CampaignPlanner.Seat("x1", "x", "Brand"));
        List<Lead> first = Prospector.find(campaign(), seats, reader, brain, store, 1000, 10);
        assertEquals(2, first.size());
        assertEquals("x1", first.get(0).account);
        for (Lead l : first) {
            store.put(l);
        }
        assertTrue("the same posts again: nothing new", Prospector.find(campaign(), seats, reader, brain, store, 2000, 10).isEmpty());
        assertTrue(new LeadStore(dir).known("https://x.com/a/status/1/"));

        CampaignPlanner.Brain answers = (system, prompt, tokens) -> "{\"replies\":[{\"n\":1,\"reply\":\"Yes, we deliver to Ikeja!\"}]}";
        List<Lead> replies = Prospector.engage(campaign(), seats, reader, answers, store, 3000);
        assertEquals(1, replies.size());
        assertEquals(Lead.Kind.REPLY, replies.get(0).kind);
        assertEquals("answered from the account that was asked", "x1", replies.get(0).account);
        replies.get(0).state = Lead.State.SENT;
        replies.get(0).sentAt = 3000;
        store.put(replies.get(0));
        assertEquals(1, store.sentSince("c1", Lead.Kind.REPLY, 0));
        assertEquals(0, store.sentSince("c1", Lead.Kind.LEAD, 0));
    }
}
