package ai.ibiko.learn;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

import ai.ibiko.main.ChatSpace;

/** One chat cannot open another's files. Only the central store keeps a lesson from every chat. */
public class CentralTest {
    @Test
    public void aChatCannotOpenAnotherAndTheCentreKeepsBothLessons() throws Exception {
        File root = Files.createTempDirectory("ibiko-central").toFile();
        File files = new File(root, "files");
        files.mkdirs();
        File a = ChatSpace.of(files, "chat-a");
        File b = ChatSpace.of(files, "chat-b");
        File secret = new File(b, "secret.txt");
        Files.write(secret.toPath(), "other chat".getBytes("UTF-8"));
        assertNull(ChatSpace.otherChat(a, new File(a, "memory")));
        assertNotNull(ChatSpace.otherChat(a, secret));
        assertTrue(ChatSpace.holds(a, new File(a, "memory")));
        assertTrue(!ChatSpace.holds(a, secret));
        File ibiko = ChatSpace.root(files);
        new File(ibiko, ChatSpace.CHATS).mkdirs();
        File seen = ChatSpace.visible(ibiko);
        assertTrue(seen.getPath(), seen.getName().equals("bench"));
        assertTrue(!seen.getPath().contains(ChatSpace.CHATS + File.separator + "chat-b"));
        Central.learn(files, "chat-a", "online", "Should I rent?", "Rent, because the deposit is a limit.");
        Central.learn(files, "chat-b", "offline", "What is the capital?", "Abuja.");
        String all = String.join("\n", Central.lessons(files));
        assertTrue(all.contains("chat-a") && all.contains("deposit"));
        assertTrue(all.contains("chat-b") && all.contains("Abuja"));
        assertTrue(new File(a, ChatSpace.MEMORY + "/learned.txt").isFile());
        assertTrue(!Files.readAllLines(new File(a, ChatSpace.MEMORY + "/learned.txt").toPath()).toString().contains("Abuja"));
    }
}
