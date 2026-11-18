package ai.ibiko.agent;

import java.io.File;
import org.junit.Assert;
import org.junit.Test;

public class ReadCacheTest {
    @Test
    public void aSecondReadOfAnUnchangedFileIsRefused() {
        ReadCache c = new ReadCache();
        Assert.assertNull(c.again("read_file", "a.java\u00001-40", 7L, "2. write the method"));
        c.keep("read_file", "a.java\u00001-40", 7L, "class A {}");
        String stop = c.again("read_file", "a.java\u00001-40", 7L, "2. write the method");
        Assert.assertNotNull(stop);
        Assert.assertTrue(stop.startsWith("Cached."));
        Assert.assertTrue(stop.contains("Write the fix now"));
        Assert.assertTrue(stop.contains("2. write the method"));
        Assert.assertFalse("not handed the file again", stop.contains("class A"));
        Assert.assertNull("a changed file is read again", c.again("read_file", "a.java\u00001-40", 8L, ""));
    }

    @Test
    public void aWriteIsNotDoneTwiceAndAChangeIsReadAgain() throws Exception {
        File dir = new File("build/read-cache-test");
        dir.mkdirs();
        File kept = new File(dir, "reads.cache");
        kept.delete();
        ReadCache c = new ReadCache();
        c.bind(kept);
        c.keep("read_file", "src/A.java\u00001-20", 3L, "old");
        Assert.assertNull(c.repeat("write", "src/A.java\u00001", "3. the next one"));
        Assert.assertNotNull(c.repeat("write", "src/A.java\u00001", "3. the next one"));
        c.changed("src/A.java");
        Assert.assertNull(c.again("read_file", "src/A.java\u00001-20", 3L, ""));
        ReadCache later = new ReadCache();
        later.bind(kept);
        Assert.assertNotNull(later.repeat("write", "src/A.java\u00001", ""));
    }
}
