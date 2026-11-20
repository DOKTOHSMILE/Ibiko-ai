package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/** A deleted campaign stays deleted: a planner or a posting run still holding it never brings it back. */
public class DeleteStaysDeletedTest {
    @Test
    public void aLateSaveOrMessageDoesNotBringItBack() throws Exception {
        File dir = Files.createTempDirectory("campaigns").toFile();
        Campaign c = new Campaign();
        c.id = "camp-1";
        c.name = "PureGasSapeleWeekly";
        new CampaignStore(dir).put(c);
        CampaignChat chat = new CampaignChat(dir, c.id); // the planner opened its conversation before the delete
        chat.add(CampaignChat.STUDIO, "text", "Planning…", 1L);
        assertNotNull(new CampaignStore(dir).get(c.id));

        new CampaignStore(dir).remove(c.id); // Delete, from the three dots
        assertNull(new CampaignStore(dir).get(c.id));

        // the planner, still running, saves its copy and says it is done
        new CampaignStore(dir).put(c);
        chat.add(CampaignChat.STUDIO, "plan", "Your campaign is planned.", 2L);
        assertNull("it doesn't come back", new CampaignStore(dir).get(c.id));
        assertTrue(new CampaignStore(dir).all().isEmpty());
        assertTrue(CampaignStore.deleted(dir, c.id));
        assertEquals(1, new CampaignChat(dir, c.id).all().size()); // nothing new written to its conversation

        // another campaign is untouched
        Campaign other = new Campaign();
        other.id = "camp-2";
        new CampaignStore(dir).put(other);
        assertNotNull(new CampaignStore(dir).get(other.id));
    }
}
