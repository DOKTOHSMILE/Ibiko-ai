package ai.ibiko.chat.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Only files another app hands over are taken in, never Ibiko's own. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class ShareSourceTest {
    @Test
    public void ownFilesAndPlainPathsAreRefused() {
        assertTrue(ShareActivity.fromAnotherApp(Uri.parse("content://com.android.providers.media.documents/document/image%3A12"), "ai.ibiko"));
        assertFalse("a raw path, here Ibiko's own settings", ShareActivity.fromAnotherApp(
                Uri.parse("file:///data/data/ai.ibiko/shared_prefs/ibiko.xml"), "ai.ibiko"));
        assertFalse("Ibiko's own provider", ShareActivity.fromAnotherApp(Uri.parse("content://ai.ibiko.files/files/chats/1.json"), "ai.ibiko"));
        assertFalse(ShareActivity.fromAnotherApp(Uri.parse("content://ai.ibiko.other/x"), "ai.ibiko"));
        assertFalse(ShareActivity.fromAnotherApp(null, "ai.ibiko"));
    }
}
