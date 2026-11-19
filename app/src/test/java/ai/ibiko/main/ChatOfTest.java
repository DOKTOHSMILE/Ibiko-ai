package ai.ibiko.main;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** Which chat a path is in, with ".." resolved. */
public class ChatOfTest {
    @Test
    public void dotDotIsResolved() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File a = ChatSpace.of(files, "A"), b = ChatSpace.of(files, "B");
        assertEquals(b.getCanonicalPath(), ChatSpace.chatOf(new File(a, "../B/repos/x.txt")).getPath());
        assertEquals("B", ChatSpace.otherChat(a, new File(a, "../B/repos/x.txt")).getName());
        assertEquals(null, ChatSpace.otherChat(a, new File(a, "repos/y.txt")));
    }
}
