package ai.ibiko;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;

import org.junit.Test;

/** Deleting a chat's files removes a link in them, never what it points to. */
public class DeleteTreeLinkTest {
    @Test
    public void aLinkIsRemovedNotFollowed() throws Exception {
        File top = Files.createTempDirectory("del").toFile();
        File photos = new File(top, "sdcard/DCIM");
        photos.mkdirs();
        File photo = new File(photos, "me.jpg");
        Files.write(photo.toPath(), new byte[]{1, 2, 3});
        File chat = new File(top, "files/ibiko/chats/A");
        new File(chat, "repos").mkdirs();
        Files.write(new File(chat, "repos/notes.txt").toPath(), new byte[]{1});
        Files.createSymbolicLink(new File(chat, "repos/pics").toPath(), photos.toPath());
        Retries.deleteTree(chat);
        assertFalse("the chat's files are gone", chat.exists());
        assertTrue("the photo the link pointed to is kept", photo.isFile());
    }
}
