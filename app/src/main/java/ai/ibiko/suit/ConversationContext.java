package ai.ibiko.suit;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * One persistence path for the existing comprehension state, shared by ordinary replies, extended reasoning and
 * coding work. The transcript remains authoritative; this small snapshot only preserves the bounded per-chat cache.
 * Everything returned is advisory context, never an app command.
 */
public final class ConversationContext {
    private static final String SNAPSHOT = ".context-v1";
    private static final long MAX_SNAPSHOT_BYTES = 131072L;

    private ConversationContext() { }

    /**
     * Restore this chat's bounded Cortex state, read the original user wording through Ibiko v1's existing UCL,
     * produce the shared context and atomically save the updated snapshot. The interpreted text may differ from the
     * original wording; only the original wording advances UCL conversation history.
     */
    public static String build(ai.ibiko.ibiko1.Ibiko1 ibiko1, Cortex cortex, String modelText,
            String originalUserText, String conversationId, File chatHome) {
        if (cortex == null) return "";
        String key = conversationId == null || conversationId.trim().isEmpty() ? "default" : conversationId.trim();
        File snapshot = chatHome == null ? null : new File(new File(chatHome, "memory"), SNAPSHOT);
        restore(cortex, key, snapshot);
        ai.ibiko.ibiko1.comprehend.Comprehension.Meaning meaning = null;
        if (ibiko1 != null && originalUserText != null) {
            meaning = ibiko1.understood(originalUserText, key);
        }
        String result = cortex.context(modelText == null ? "" : modelText, key, meaning);
        save(cortex, key, snapshot);
        return result;
    }

    private static void restore(Cortex cortex, String key, File snapshot) {
        if (snapshot == null || !snapshot.isFile()) return;
        try {
            long length = snapshot.length();
            if (length <= 0 || length > MAX_SNAPSHOT_BYTES) return;
            String state = new String(Files.readAllBytes(snapshot.toPath()), StandardCharsets.UTF_8);
            if (state.startsWith("v2\n")) {
                int split = state.indexOf('\n', 3);
                if (split < 0) return;
                String storedKey = new String(java.util.Base64.getDecoder().decode(state.substring(3, split)), StandardCharsets.UTF_8);
                if (!key.equals(storedKey)) return;
                state = state.substring(split + 1);
            } // A legacy raw snapshot is accepted: phase 8 stored one file inside each chat's private folder.
            cortex.restoreConversationContext(key, state);
        } catch (Exception ignored) {
            // The transcript is authoritative; a damaged or old cache is disposable.
        }
    }

    private static void save(Cortex cortex, String key, File snapshot) {
        if (snapshot == null) return;
        String state = cortex.exportConversationContext(key);
        if (state == null || state.isEmpty()) return;
        state = "v2\n" + java.util.Base64.getEncoder().encodeToString(key.getBytes(StandardCharsets.UTF_8))
                + "\n" + state;
        if (state.length() > MAX_SNAPSHOT_BYTES) return;
        File parent = snapshot.getParentFile();
        File temp = new File(parent, SNAPSHOT + ".tmp");
        try {
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
            FileOutputStream out = new FileOutputStream(temp);
            try {
                out.write(state.getBytes(StandardCharsets.UTF_8));
                out.flush();
                out.getFD().sync();
            } finally {
                out.close();
            }
            try {
                Files.move(temp.toPath(), snapshot.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temp.toPath(), snapshot.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            temp.delete(); // persistence is best-effort and never blocks a reply
        }
    }
}
