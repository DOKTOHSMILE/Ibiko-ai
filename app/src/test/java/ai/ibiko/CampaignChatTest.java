package ai.ibiko;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.ibiko.chat.ChatBook;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.Product;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;

/** A campaign gets a chat of its own that says what it is for, without pulling the open chat away. */
public class CampaignChatTest {
    @Test
    public void theBriefIsTheChatsFirstMessage() {
        Campaign c = new Campaign();
        c.brief.business = "Valineto3 Enterprises";
        c.brief.offer = "Gas cylinders and\nhousehold gadgets";
        c.brief.whatsapp = "+2348031234567";
        Product p = new Product();
        p.name = "12.5kg cylinder";
        p.price = "₦45,000";
        c.brief.products.add(p);
        c.handoff.add("instagram");
        c.days = 7;
        c.perDay = 3;
        c.autoPitch = true;
        String b = SocialCampaigns.brief(c, null);
        assertTrue(b, b.startsWith("New social media campaign\n\nBusiness: Valineto3 Enterprises\nPromotes: Gas cylinders and household gadgets"));
        assertTrue(b.contains("Products: 12.5kg cylinder (₦45,000)"));
        assertTrue(b.contains("Posts from: Instagram (you share)"));
        assertTrue(b.contains("Runs: 7 days, 3 posts a day on each account"));
        assertTrue(b.contains("pitch them (up to"));
        assertFalse("no empty parts", b.contains("Tone:"));
    }

    @Test
    public void itsChatOpensBesideTheOneInUse() throws Exception {
        File dir = Files.createTempDirectory("chats").toFile();
        ChatBook book = new ChatBook(new File(dir, "chats.json"));
        String working = book.current().id;
        ChatBook.Section own = book.openOwn("Campaign: Gas", book.current().persona, false);
        assertEquals("the open chat stays open", working, book.current().id);
        assertTrue(book.addTo(own.id, "user", "text", "New social media campaign", own.persona));
        assertTrue(book.addTo(own.id, "assistant", "text", "Planning it now", own.persona));
        assertEquals(2, own.messages.size());
        assertEquals("Campaign: Gas", own.title);
        assertTrue(book.current().messages.isEmpty());
        assertFalse(book.addTo("gone", "assistant", "text", "x", own.persona));
    }
}
