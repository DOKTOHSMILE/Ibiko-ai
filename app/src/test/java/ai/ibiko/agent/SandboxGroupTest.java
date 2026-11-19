package ai.ibiko.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** A command stopped by its time limit takes everything it started with it. */
public class SandboxGroupTest {
    @Test(timeout = 30000)
    public void aTimeoutStopsWhatTheCommandStartedInTheBackground() throws Exception {
        assumeTrue("needs setsid", SandboxShell.setsid() != null);
        File dir = Files.createTempDirectory("sandbox").toFile();
        File marker = new File(dir, "still-alive");
        // a background loop that would keep touching a file for a minute, then a foreground wait past the limit
        String out = SandboxShell.run("(for i in $(seq 1 60); do touch still-alive; sleep 0.2; done) & sleep 30", dir, 1500, 2000);
        assertTrue(out, out.contains("stopped after"));
        Thread.sleep(500);
        marker.delete();
        Thread.sleep(1200);
        assertFalse("the background loop was stopped with it", marker.exists());
    }
}
