package ai.ibiko.suit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** The per-chat snapshot survives a Cortex recreation without leaking into another chat. */
public class ConversationContextTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void restoresContextAfterCortexRecreation() throws Exception {
        File chat = temporary.newFolder("chat-one");
        Cortex first = new Cortex(null, null);
        ConversationContext.build(null, first, "The car is red", "The car is red", "chat:one", chat);
        assertTrue(new File(new File(chat, "memory"), ".context-v1").isFile());

        Cortex resumed = new Cortex(null, null);
        String next = ConversationContext.build(null, resumed, "It is low", "It is low", "chat:one", chat);
        assertTrue(next.toLowerCase().contains("car"));
    }

    @Test public void anotherConversationDoesNotInheritTheFirstConversation() throws Exception {
        File chat = temporary.newFolder("chat-two");
        Cortex cortex = new Cortex(null, null);
        ConversationContext.build(null, cortex, "The car is red", "The car is red", "chat:one", chat);
        String next = ConversationContext.build(null, cortex, "It is low", "It is low", "chat:two", chat);
        assertFalse(next.toLowerCase().contains("it -> car"));
    }
}
