package ai.ibiko.net;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** A signed-in page's cookies go only to that site, never to where a redirect leads. */
public class RedirectCookieTest {
    @Test
    public void cookiesStayOnTheirSite() {
        assertTrue(WebGet.sameSite("x.com", "x.com"));
        assertTrue(WebGet.sameSite("x.com", "mobile.x.com"));
        assertTrue(WebGet.sameSite("www.facebook.com", "facebook.com"));
        assertFalse("a shortener's target", WebGet.sameSite("x.com", "example.org"));
        assertFalse("a look-alike", WebGet.sameSite("x.com", "evilx.com"));
        assertFalse(WebGet.sameSite("www.facebook.com", "com"));
    }
}
