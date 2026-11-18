package ai.ibiko.ibiko1.comprehend;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** The Universal Comprehension Layer must not resolve one chat's pronouns from another chat. */
public class ConversationReadingTest {
    @Test public void referencesStayInsideTheirOwnChat() {
        ConversationReading reading = new ConversationReading(null, null);
        reading.read("chat:a", "The car is red.");
        Comprehension.Meaning sameChat = reading.read("chat:a", "Move it to the garage.");
        Comprehension.Meaning otherChat = reading.read("chat:b", "Move it to the garage.");
        assertEquals("car", sameChat.references.get("it"));
        assertEquals("it", otherChat.first().target);
        assertTrue(otherChat.clarify, otherChat.clarify != null && otherChat.clarify.contains("it"));
    }
    @Test public void sameLatestMessageIsCached() {
        ConversationReading reading = new ConversationReading(null, null);
        reading.read("chat:a", "The car is red.");
        Comprehension.Meaning first = reading.read("chat:a", "Move it to the garage.");
        assertTrue(first == reading.read("chat:a", "Move it to the garage."));
    }
    @Test public void activeConversationCacheIsBounded() {
        ConversationReading reading = new ConversationReading(null, null);
        for (int i = 0; i < ConversationReading.MAX_CHATS + 3; i++) reading.read("chat:" + i, "The car is red.");
        assertEquals(ConversationReading.MAX_CHATS, reading.activeChats());
    }
}
