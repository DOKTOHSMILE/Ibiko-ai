package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** WhatsApp, free: a click-to-chat link built from what the person typed, put in posts and kept in replies. */
public class WhatsAppTest {
    @Test
    public void aNumberWithItsCountryCodeBecomesAChatLink() {
        assertEquals("https://wa.me/2348031234567", WhatsApp.link("+234 803 123 4567", null));
        assertEquals("https://wa.me/2348031234567", WhatsApp.link("00234-803-123-4567", ""));
        assertEquals("https://wa.me/2348031234567?text=Hello%2C%20I%20saw%20your%20post.",
                WhatsApp.link("+2348031234567", "Hello, I saw your post."));
    }

    @Test
    public void aLinkStaysAndALocalNumberIsRefused() {
        assertEquals("https://wa.me/message/WFUCNJQPRPFBN1", WhatsApp.link("https://wa.me/message/WFUCNJQPRPFBN1", "hi"));
        assertEquals("https://wa.me/message/ABC", WhatsApp.link("wa.me/message/ABC", null));
        assertTrue(WhatsApp.isLink("https://chat.whatsapp.com/Gx1"));
        assertNull("no country code: can't guess the country", WhatsApp.link("0803 123 4567", null));
        assertNull(WhatsApp.link("call me maybe", null));
        assertNull(WhatsApp.link("+12", null));
    }

    @Test
    public void postsGetTheLinkWhenThereIsRoom() {
        String chat = "https://wa.me/2348031234567";
        assertEquals("Fresh cakes!\n\nChat with us on WhatsApp: " + chat, WhatsApp.withLink("Fresh cakes!", chat, 280));
        assertEquals("Order: " + chat, WhatsApp.withLink("Order: " + chat, chat, 280)); // there already
        String full = new String(new char[270]).replace('\0', 'a');
        assertEquals("no room on X: left as it is", full, WhatsApp.withLink(full, chat, 280));
        assertEquals("no WhatsApp given", "Hi", WhatsApp.withLink("Hi", "", 280));
    }

    @Test
    public void theBriefsChatLinkIsKeptInRepliesAndOthersAreNot() {
        Campaign.Brief b = new Campaign.Brief();
        b.offer = "Birthday cakes";
        b.whatsapp = "+2348031234567";
        assertTrue(b.chat().startsWith("https://wa.me/2348031234567?text=Hello"));
        assertTrue(b.words().contains("WhatsApp chat link: https://wa.me/"));
        String r = Prospector.cleanKeeping("We'd love to help! " + b.chat() + " or see https://evil.example/x", "x", b.link, b.chat());
        assertTrue(r.contains(b.chat()));
        assertFalse(r.contains("evil.example"));
    }
}
