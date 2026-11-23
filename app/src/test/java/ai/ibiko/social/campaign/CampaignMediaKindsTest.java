package ai.ibiko.social.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.Arrays;

import org.junit.Test;

import ai.ibiko.social.SocialPoster;

/** A campaign takes videos and GIFs as they are, and stills as JPEGs; each goes to the network as what it is. */
public class CampaignMediaKindsTest {
    @Test
    public void eachFileIsKeptAsWhatItIs() {
        assertEquals("jpg", CampaignMedia.kind("image/jpeg", "IMG_1.jpg"));
        assertEquals("jpg", CampaignMedia.kind("image/png", "a.png"));
        assertEquals("an animation stays moving", "gif", CampaignMedia.kind("image/gif", "a.gif"));
        assertEquals("mp4", CampaignMedia.kind("video/mp4", "VID_1.mp4"));
        assertEquals("mov", CampaignMedia.kind("video/quicktime", "clip"));
        assertEquals("by its name when the type isn't given", "webm", CampaignMedia.kind(null, "clip.webm"));
        assertTrue(CampaignMedia.moving("/x/own-1.mp4"));
        assertFalse(CampaignMedia.moving("/x/own-1.jpg"));
    }

    @Test
    public void theNetworkIsToldWhatTheFileIs() {
        assertEquals("video/mp4", SocialPoster.mime(new File("post.mp4")));
        assertEquals("video/quicktime", SocialPoster.mime(new File("post.mov")));
        assertEquals("image/gif", SocialPoster.mime(new File("post.gif")));
        assertEquals("image/jpeg", SocialPoster.mime(new File("post.jpg")));
        assertTrue(SocialPoster.isVideo(new File("own-1.MP4")));
        assertFalse(SocialPoster.isVideo(new File("own-1.gif")));
    }

    @Test
    public void whatWasAddedIsSaidAsPicturesAndVideos() {
        Campaign c = new Campaign();
        String said = CampaignMedia.place(c, Arrays.asList("/x/own-1.jpg", "/x/own-2.mp4", "/x/own-3.gif"));
        assertTrue(said, said.startsWith("2 pictures and 1 video added."));
        assertEquals("no post yet: all three wait for the posts written", 3, c.media.size());
    }
}
