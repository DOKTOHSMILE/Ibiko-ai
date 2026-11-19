package ai.ibiko.connect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

/** A clone's zip leaves out a file past the size limit instead of holding it all in memory. */
public class ZipLimitTest {
    @Test
    public void aFileTooLargeIsLeftOut() throws Exception {
        File dir = Files.createTempDirectory("zip").toFile();
        File zip = new File(dir, "repo.zip");
        try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zip))) {
            z.putNextEntry(new ZipEntry("repo-main/small.txt"));
            z.write("hello".getBytes());
            z.putNextEntry(new ZipEntry("repo-main/big.bin"));
            z.write(new byte[5000]);
        }
        File out = new File(dir, "out");
        out.mkdirs();
        GitHubGit.Record r = new GitHubGit.Record();
        assertEquals(1, GitHubGit.unzip(zip, out, r, 1000));
        assertTrue(new File(out, "small.txt").isFile());
        assertFalse(new File(out, "big.bin").exists());
    }
}
