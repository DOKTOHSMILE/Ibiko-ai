package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;

/** A worker's readers open the files its part names, several at once, and hand the lines up. */
public class ReadersTest {
    @Test
    public void readersOpenTheFilesThePartIsAbout() throws Exception {
        File root = Files.createTempDirectory("readers").toFile();
        File src = new File(root, "src");
        src.mkdirs();
        Files.write(new File(src, "Rent.java").toPath(), "class Rent {\n    int deposit;\n}\n".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(src, "Other.java").toPath(), "class Other {\n    int unused;\n}\n".getBytes(StandardCharsets.UTF_8));
        Readers.Read got = Readers.excerpts(root, "Read Rent.java and report the deposit field", 4);
        assertTrue(got.files >= 1);
        assertTrue(got.text, got.text.contains("Rent.java") && got.text.contains("deposit"));
        assertEquals(0, Readers.excerpts(root, "nothing about this tree at all zzzz", 2).files);
    }
}
