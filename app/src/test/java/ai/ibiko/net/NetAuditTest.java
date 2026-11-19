package ai.ibiko.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The net package's audit fixes: blocks stripped in one pass, links that work, sources named truly. */
public class NetAuditTest {
    @Test
    public void blocksAreDroppedInOnePassEvenWhenATagIsNeverClosed() {
        String page = "<p>Keep this sentence.</p><script>var x='<p>not this</p>';</script><nav>menu</nav>"
                + "<SCRIPT type=x>drop</SCRIPT><p>And this one.</p>";
        String out = WebSearch.dropBlocks(page);
        assertTrue(out, out.contains("Keep this sentence.") && out.contains("And this one."));
        assertFalse(out, out.contains("not this") || out.contains("menu") || out.contains("drop"));
        assertTrue("<scripts> is not <script>", WebSearch.dropBlocks("<scripts>a</scripts>").contains("a"));
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            big.append("<script unclosed ").append(i).append("> text ");
        }
        long t0 = System.currentTimeMillis();
        WebSearch.dropBlocks(big.toString());
        assertTrue("fast on a large page with unclosed tags", System.currentTimeMillis() - t0 < 2000);
    }

    @Test
    public void wikipediaLinksAndTheNamedSourceAreRight() throws Exception {
        String json = "{\"query\":{\"search\":[{\"title\":\"C Sharp (programming language)\",\"snippet\":\"x\"},"
                + "{\"title\":\"C#\",\"snippet\":\"y\"}]}}";
        ai.ibiko.connect.Connectors.Http http = (m, url, h, body) -> new ai.ibiko.connect.Connectors.Reply(200, json, new java.util.HashMap<String, String>());
        java.util.List<WebSearch.Hit> hits = WebSearch.wikipedia(http, "c#", 5);
        assertEquals("https://en.wikipedia.org/wiki/C%23", hits.get(1).url);
        WebSearch.Browser was = WebSearch.browser;
        WebSearch.browser = null;
        try {
            String r = WebSearch.research("c#", http, new WebSearch.Settings("brave", "k", ""), null, 0, 2000);
            assertTrue("a failed Brave search fell back to Wikipedia, and says so: " + r, r.startsWith("Web results for “c#” from Wikipedia"));
        } finally {
            WebSearch.browser = was;
        }
    }
}
