package ai.ibiko.agent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** The search tool can't climb out of the workspace. */
public class SearchContainmentTest {
    @Test
    public void aFolderOutsideIsRefused() throws Exception {
        File top = Files.createTempDirectory("app").toFile();
        File prefs = new File(top, "shared_prefs");
        prefs.mkdirs();
        Files.write(new File(prefs, "ibiko.xml").toPath(), "api_key=sk-secret".getBytes(StandardCharsets.UTF_8));
        File ws = new File(top, "files/ibiko");
        ws.mkdirs();
        Files.write(new File(ws, "main.py").toPath(), "api_key = os.environ['K']".getBytes(StandardCharsets.UTF_8));
        String out = SourceRead.search(ws, "api_key", "../../shared_prefs");
        assertFalse(out, out.contains("sk-secret"));
        assertTrue(out, out.contains("outside"));
        assertTrue(SourceRead.search(ws, "api_key", ".").contains("main.py"));
    }
}
