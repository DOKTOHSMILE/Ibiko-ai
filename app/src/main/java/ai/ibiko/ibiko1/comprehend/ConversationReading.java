package ai.ibiko.ibiko1.comprehend;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps the existing Universal Comprehension Layer's short reference history separate for each chat. The WordNet and
 * ConceptNet tables are shared; each chat gets its own Comprehension instance, so a pronoun in one chat cannot resolve
 * to an object mentioned in another. The cache is deliberately bounded because the durable transcript lives elsewhere.
 */
public final class ConversationReading {
    public static final int MAX_CHATS = 8;

    private static final class Session {
        final Comprehension reader;
        String lastText;
        Comprehension.Meaning lastMeaning;

        Session(Dictionary dictionary, Concepts concepts) {
            reader = new Comprehension(dictionary, concepts);
        }
    }

    private final Dictionary dictionary;
    private final Concepts concepts;
    private final LinkedHashMap<String, Session> sessions =
            new LinkedHashMap<String, Session>(MAX_CHATS, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
                    return size() > MAX_CHATS;
                }
            };

    public ConversationReading(Dictionary dictionary, Concepts concepts) {
        this.dictionary = dictionary;
        this.concepts = concepts;
    }

    /** Reads a message in its chat. Re-reading the same latest message returns the cached Meaning without advancing. */
    public synchronized Comprehension.Meaning read(String conversationId, String message) {
        if (message == null) {
            return null;
        }
        Session session = session(conversationId);
        if (message.equals(session.lastText) && session.lastMeaning != null) {
            return session.lastMeaning;
        }
        session.lastMeaning = session.reader.read(message);
        session.lastText = message;
        return session.lastMeaning;
    }

    /** The parser for a chat, for existing callers that need the full UCL object. */
    public synchronized Comprehension parser(String conversationId) {
        return session(conversationId).reader;
    }

    /** Clears only one chat's short reference history; it does not delete the chat transcript or learned knowledge. */
    public synchronized void forget(String conversationId) {
        sessions.remove(key(conversationId));
    }

    public synchronized int activeChats() {
        return sessions.size();
    }

    private Session session(String conversationId) {
        String key = key(conversationId);
        Session found = sessions.get(key);
        if (found == null) {
            found = new Session(dictionary, concepts);
            sessions.put(key, found);
        }
        return found;
    }

    private static String key(String conversationId) {
        return conversationId == null || conversationId.trim().isEmpty() ? "default" : conversationId.trim();
    }
}
