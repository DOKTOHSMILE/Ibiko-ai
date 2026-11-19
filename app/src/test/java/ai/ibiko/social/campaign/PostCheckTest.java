package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** The check after posting: found on the page, not there, or not known when the page wasn't really read. */
public class PostCheckTest {
    private static final String POST = "Fresh 12.5kg gas cylinders delivered across Lagos today! #gas https://wa.me/2348031234567";

    private static String page(String body) {
        StringBuilder s = new StringBuilder("Valineto3 Enterprises\nPosts\n");
        while (s.length() < 300) {
            s.append("Menu Home Friends Watch Marketplace Notifications\n");
        }
        return s.append(body).toString();
    }

    @Test
    public void theOpeningWordsWithoutLinksAndTagsAreLookedFor() {
        assertEquals("Fresh 12.5kg gas cylinders delivered across Lagos today!", PostCheck.words(POST));
        assertEquals(Boolean.TRUE, PostCheck.found(page("Valineto3 · 1m\nFresh 12.5kg gas cylinders delivered across Lagos today! #gas"),
                POST, "facebook"));
        assertEquals("a page that cut the post short", Boolean.TRUE,
                PostCheck.found(page("Fresh 12.5kg gas cylinders deliv… See more"), POST, "facebook"));
        assertEquals(Boolean.FALSE, PostCheck.found(page("Yesterday: New stock of blenders"), POST, "facebook"));
    }

    @Test
    public void aPageThatWasntReadSaysNothing() {
        assertNull(PostCheck.found(null, POST, "x"));
        assertNull(PostCheck.found("X: not read. Too many reads this hour.", POST, "x"));
        assertNull("next to nothing on the page", PostCheck.found("Log in", POST, "facebook"));
        assertNull("too few words to tell posts apart", PostCheck.found(page("Hi"), "Hi!", "facebook"));
    }
}
