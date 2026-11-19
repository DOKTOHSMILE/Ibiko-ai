package ai.ibiko.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** A command, an unzip or a pull can change files without naming them: cached searches and listings go, reads stay. */
public class ReadCacheStaleTest {
    @Test
    public void staleSearchesDropsSearchesAndListingsOnly() {
        ReadCache c = new ReadCache();
        c.keep("search", "Foo\u0000src", 1L, "src/A.java:3: Foo");
        c.keep("list_files", "src", 7L, "A.java");
        c.keep("read_file", "src/A.java\u00001-", 42L, "class A {}");
        c.staleSearches();
        assertNull(c.again("search", "Foo\u0000src", 1L, ""));
        assertNull(c.again("list_files", "src", 7L, ""));
        assertEquals(true, c.again("read_file", "src/A.java\u00001-", 42L, "") != null); // a read keeps its own stamp
    }
}
