package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** The posts a model sends back are read however it sends them, and a reply cut off keeps the posts that are whole. */
public class PostReadingTest {
    private static List<String[]> slots(int n) {
        List<String[]> s = new ArrayList<String[]>();
        for (int i = 0; i < n; i++) {
            s.add(new String[] {"facebook", "theme " + i, "angle"});
        }
        return s;
    }

    @Test
    public void theUsualFormIsRead() {
        Map<Integer, String[]> got = CampaignPlanner.posts("{\"posts\":[{\"n\":1,\"text\":\"First post\",\"headline\":\"One\"},"
                + "{\"n\":2,\"text\":\"Second post\"}]}", slots(2));
        assertEquals(2, got.size());
        assertEquals("First post", got.get(0)[0]);
        assertEquals("One", got.get(0)[1]);
    }

    @Test
    public void aNumberSentAsWordsAndABareListAreRead() {
        Map<Integer, String[]> got = CampaignPlanner.posts("{\"posts\":[{\"n\":\"1\",\"text\":\"First\"},{\"n\":\"2\",\"text\":\"Second\"}]}", slots(2));
        assertEquals("a post number as text (\"1\") is still its number", 2, got.size());
        Map<Integer, String[]> bare = CampaignPlanner.posts("Here you go:\n[{\"text\":\"Only words\"},{\"text\":\"More words\"}]", slots(2));
        assertEquals("a bare list, numbered by place", 2, bare.size());
        assertEquals("More words", bare.get(1)[0]);
        Map<Integer, String[]> other = CampaignPlanner.posts("{\"items\":[{\"n\":1,\"post\":\"Under another name\"}]}", slots(1));
        assertEquals("Under another name", other.get(0)[0]);
    }

    @Test
    public void aReplyCutOffKeepsItsWholePosts() {
        String cut = "{\"posts\":[{\"n\":1,\"text\":\"A long, expert post about apps {and} websites.\",\"headline\":\"Apps\"},"
                + "{\"n\":2,\"text\":\"Another whole post.\"},{\"n\":3,\"text\":\"This one was cut off in the mid";
        Map<Integer, String[]> got = CampaignPlanner.posts(cut, slots(3));
        assertEquals("the two whole posts are kept; the cut one is asked again: " + got.keySet(), 2, got.size());
        assertTrue(got.get(0)[0].contains("{and}"));
        assertEquals("Another whole post.", got.get(1)[0]);
    }

    @Test
    public void nothingReadableIsNothing() {
        assertTrue(CampaignPlanner.posts("Sorry, I can't help with that.", slots(2)).isEmpty());
        assertTrue(CampaignPlanner.posts(null, slots(2)).isEmpty());
    }
}
