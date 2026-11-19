package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

/** The studio: the owner's instruction becomes checked changes to the posts still to go out, never to those out. */
public class StudioEditTest {
    private static final List<CampaignPlanner.Seat> SEATS = Arrays.asList(new CampaignPlanner.Seat("fb1", "facebook", "Shop"));

    private static Campaign campaign() {
        Campaign c = new Campaign();
        c.id = "c1";
        c.name = "Gas week";
        c.brief.business = "Valineto3 Enterprises";
        c.status = Campaign.Status.ACTIVE;
        c.startAt = 1_800_000_000_000L;
        String[] texts = {"Already out", "Cook safely with gas", "Refill day is Friday", "Gadgets for the kitchen"};
        for (int i = 0; i < texts.length; i++) {
            Campaign.Item it = new Campaign.Item();
            it.id = "c1-" + i;
            it.account = "fb1";
            it.network = "facebook";
            it.text = texts[i];
            it.at = c.startAt + i * 86_400_000L;
            it.state = i == 0 ? Campaign.State.POSTED : Campaign.State.PLANNED;
            c.items.add(it);
        }
        return c;
    }

    @Test
    public void theBrainSeesOnlyThePostsStillToGoOut() {
        String p = StudioEdit.prompt(campaign(), SEATS, "make them shorter", TimeZone.getTimeZone("UTC"));
        assertTrue(p.contains("1. ") && p.contains("Cook safely with gas"));
        assertFalse("a post that is out is not offered for change", p.contains("Already out"));
        assertTrue(p.endsWith("The owner says: make them shorter"));
    }

    @Test
    public void editsRemovalsAndAdditionsAreChecked() {
        Campaign c = campaign();
        String reply = "Sure! {\"reply\":\"Shortened one, dropped the refill post, added a weekend one.\","
                + "\"edit\":[{\"n\":1,\"text\":\"Cook safe.\"},{\"n\":9,\"text\":\"no such post\"}],\"remove\":[2],"
                + "\"add\":[{\"day\":3,\"time\":\"10:00\",\"account\":\"fb1\",\"theme\":\"Weekend\",\"text\":\"Weekend deals!\",\"headline\":\"Deals\"},"
                + "{\"day\":3,\"time\":\"10:00\",\"account\":\"stranger\",\"text\":\"not ours\"},"
                + "{\"day\":3,\"time\":\"99:00\",\"account\":\"fb1\",\"text\":\"no such time\"}],\"pause\":true}";
        StudioEdit.Done d = StudioEdit.apply(c, reply, SEATS, TimeZone.getTimeZone("UTC"));
        assertEquals(1, d.edited);
        assertEquals(1, d.removed);
        assertEquals(1, d.added);
        assertTrue(d.paused);
        assertEquals(Campaign.Status.PAUSED, c.status);
        assertEquals("Already out", c.items.get(0).text);
        boolean shortened = false, refill = false, weekend = false;
        for (Campaign.Item it : c.items) {
            shortened |= it.text.equals("Cook safe.");
            refill |= it.text.contains("Refill");
            weekend |= it.text.equals("Weekend deals!") && it.imageHtml.contains("Deals");
        }
        assertTrue(shortened);
        assertFalse(refill);
        assertTrue(weekend);
        assertEquals("2 posts rewritten · 1 added · 1 taken out · paused".replace("2 posts rewritten", "1 post rewritten"),
                CampaignStudio.summary(d));
    }

    @Test
    public void aQuestionChangesNothing() {
        Campaign c = campaign();
        StudioEdit.Done d = StudioEdit.apply(c, "It's going well: 12 reactions so far.", SEATS, TimeZone.getDefault());
        assertFalse(d.changed());
        assertEquals("It's going well: 12 reactions so far.", d.reply);
        assertEquals(4, c.items.size());
    }

    @Test
    public void theConversationIsKept() throws Exception {
        File dir = Files.createTempDirectory("studio").toFile();
        CampaignChat chat = new CampaignChat(dir, "c1");
        chat.add(CampaignChat.YOU, "brief", "New social media campaign\nBusiness: X", 1);
        chat.add(CampaignChat.YOU, "text", "make them shorter", 2);
        chat.add(CampaignChat.STUDIO, "text", "Done: 3 posts rewritten.", 3);
        CampaignChat back = new CampaignChat(dir, "c1");
        assertEquals(3, back.all().size());
        assertEquals("Owner: make them shorter\nStudio: Done: 3 posts rewritten.", back.recent(5));
        back.delete();
        assertTrue(new CampaignChat(dir, "c1").all().isEmpty());
    }
}
