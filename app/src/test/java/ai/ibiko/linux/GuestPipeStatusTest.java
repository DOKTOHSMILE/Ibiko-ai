package ai.ibiko.linux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** The piped command's own exit code is kept, not pwd's. */
public class GuestPipeStatusTest {
    @Test
    public void aFailingCommandReportsItsCode() throws Exception {
        assumeTrue(new File("/bin/sh").exists());
        File root = Files.createTempDirectory("guest").toFile();
        String script = Guest.pipe("exit_three() { return 3; }; exit_three").replace("/root/", root.getAbsolutePath() + "/");
        Process p = new ProcessBuilder("sh", "-c", script).start();
        p.waitFor();
        assertEquals("3", new String(Files.readAllBytes(new File(root, ".ibiko_status").toPath()), StandardCharsets.UTF_8).trim());
    }
}
