package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** Git's ask-pass gives the GitHub token to GitHub only. */
public class GitAskScopeTest {
    private static String ask(File script, String prompt) throws Exception {
        Process p = new ProcessBuilder("sh", script.getPath(), prompt).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p.waitFor();
        return out;
    }

    @Test
    public void anotherServerIsNotGivenTheToken() throws Exception {
        assumeTrue(new File("/bin/sh").exists());
        File dir = Files.createTempDirectory("askpass").toFile();
        File token = new File(dir, "token");
        Files.write(token.toPath(), "gho_secret".getBytes(StandardCharsets.UTF_8));
        File script = new File(dir, "ask");
        Files.write(script.toPath(), GitAsk.script().replace(GitAsk.TOKEN_FILE, token.getPath()).getBytes(StandardCharsets.UTF_8));
        assertEquals("x-access-token", ask(script, "Username for 'https://github.com': "));
        assertEquals("gho_secret", ask(script, "Password for 'https://x-access-token@github.com': "));
        assertEquals("not for another server", "", ask(script, "Password for 'https://x-access-token@evil.example': "));
        GitOp.open(dir, "push");
        GitOp.close(dir, "FAILED", "token gho_0123456789abcdefghijABCD and github_pat_11ABCDEFG0123456789_abcdefghij");
        String redacted = new String(Files.readAllBytes(new File(dir, GitOp.FILE).toPath()), StandardCharsets.UTF_8);
        assertTrue(redacted, !redacted.contains("0123456789abcdefghijABCD") && !redacted.contains("github_pat_11ABCDEFG"));
    }
}
