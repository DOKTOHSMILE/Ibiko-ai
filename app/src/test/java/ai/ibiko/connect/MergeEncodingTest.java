package ai.ibiko.connect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

/** Only UTF-8 is merged line by line: a Latin-1 file decoded as UTF-8 was written back changed. */
public class MergeEncodingTest {
    @Test
    public void onlyValidUtf8IsMergedAsText() {
        assertTrue(GitHubGit.utf8("café — ok\r\n".getBytes(StandardCharsets.UTF_8)));
        assertTrue(GitHubGit.utf8(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a'})); // a BOM is still UTF-8
        assertFalse(GitHubGit.utf8("café".getBytes(StandardCharsets.ISO_8859_1)));
    }
}
