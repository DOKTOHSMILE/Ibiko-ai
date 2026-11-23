package ai.ibiko.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Knowledge replaced on the phone: checked, newer only, read before the app's own, and the app's own as the fallback. */
public class KnowledgeFilesTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static String read(InputStream in) throws Exception {
        return in == null ? null : new String(KnowledgeFiles.readAll(in, 1 << 20), StandardCharsets.UTF_8);
    }

    private static final Function<String, InputStream> APP = path -> path.equals("knowledge/facts.tsv.ibk")
            ? new ByteArrayInputStream("old coded".getBytes(StandardCharsets.UTF_8)) : null;

    @Test
    public void aNewerCheckedCopyIsReadBeforeTheAppsOwn() throws Exception {
        File files = tmp.newFolder();
        Function<String, InputStream> assets = KnowledgeFiles.over(files, APP);
        assertEquals("old coded", read(assets.apply("knowledge/facts.tsv.ibk")));
        assertNull(read(assets.apply("knowledge/facts.tsv")));

        KnowledgeFiles k = new KnowledgeFiles(files);
        byte[] plain = "Lagos\tpopulation\t15000000\n".getBytes(StandardCharsets.UTF_8);
        assertFalse("a wrong checksum is never installed", k.install("knowledge/facts.tsv", 2, KnowledgeFiles.sha256(new byte[] {1}), plain));
        assertTrue(k.install("knowledge/facts.tsv", 2, KnowledgeFiles.sha256(plain), plain));
        assertEquals(2, k.version("knowledge/facts.tsv"));
        // the plain update is read first: the text code reads a plain file before its coded form
        assertEquals("Lagos\tpopulation\t15000000\n", read(ai.ibiko.ibiko1.TextCode.open(assets, "knowledge/facts.tsv")));
        assertFalse("an older or equal version is not taken", k.install("knowledge/facts.tsv", 2, KnowledgeFiles.sha256(plain), plain));

        k.remove("knowledge/facts.tsv");
        assertEquals(0, k.version("knowledge/facts.tsv"));
        assertEquals("back to the app's own", "old coded", read(assets.apply("knowledge/facts.tsv.ibk")));
    }

    @Test
    public void onlyKnowledgePathsAreTaken() throws Exception {
        KnowledgeFiles k = new KnowledgeFiles(tmp.newFolder());
        byte[] b = "x".getBytes(StandardCharsets.UTF_8);
        String sha = KnowledgeFiles.sha256(b);
        assertFalse(k.install("../shared_prefs/session.xml", 1, sha, b));
        assertFalse(k.install("knowledge/../../x", 1, sha, b));
        assertFalse(k.install("lib/arm64/libx.so", 1, sha, b));
        assertTrue(k.install("knowledge/encyclopedia/shard_001.bin", 1, sha, b));
        assertEquals(1, k.count());
    }

    @Test
    public void aPlainUpdateRetiresTheOlderCodedOne() throws Exception {
        File files = tmp.newFolder();
        KnowledgeFiles k = new KnowledgeFiles(files);
        byte[] coded = "coded".getBytes(StandardCharsets.UTF_8), plain = "plain".getBytes(StandardCharsets.UTF_8);
        assertTrue(k.install("knowledge/lexicon.tsv.ibk", 3, KnowledgeFiles.sha256(coded), coded));
        assertTrue(k.install("knowledge/lexicon.tsv", 4, KnowledgeFiles.sha256(plain), plain));
        assertNull(k.open("knowledge/lexicon.tsv.ibk"));
        assertEquals("plain", read(k.open("knowledge/lexicon.tsv")));
    }
}
