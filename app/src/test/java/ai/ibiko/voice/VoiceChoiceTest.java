package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.View;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

/** The human voice: a failure to start is told (no other voice reads in its place); a voice choice redraws in place. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class VoiceChoiceTest {
    @Test
    public void aVoiceThatCantStartIsToldAndNoOtherVoiceReads() throws Exception {
        android.app.Application app = RuntimeEnvironment.getApplication();
        File files = app.getFilesDir();
        // the child's voice on the device (its files in place), but no engine to run it here: as on a device whose library fails
        File d = VoicePacks.folder(files, VoicePacks.PIPER_AMY);
        d.mkdirs();
        Files.write(new File(d, VoicePacks.PIPER_AMY.model).toPath(), new byte[] {1});
        Files.write(new File(VoicePacks.root(files), VoicePacks.PIPER_AMY.key + ".ready").toPath(),
                VoicePacks.PIPER_AMY.sha256.getBytes(StandardCharsets.UTF_8));
        assertTrue(VoicePacks.installed(files, VoicePacks.PIPER_AMY));
        TalkBridge t = new TalkBridge(app);
        File problem = new File(VoicePacks.root(files), NeuralVoice.PROBLEM);
        long until = System.currentTimeMillis() + 10000;
        while (!problem.isFile() && System.currentTimeMillis() < until) {
            Thread.sleep(50);
        }
        assertTrue("why it couldn't start is kept for Settings", problem.isFile());
        assertFalse(t.human());
        assertTrue(new String(Files.readAllBytes(problem.toPath()), StandardCharsets.UTF_8).contains("Piper"));
        t.shutdown();
    }

    /** One voice, the child's: Piper Amy played higher (fast, carried in the app); no Voice page, no voice choice. */
    @Test
    public void theChildsVoiceIsTheOnlyVoiceAndNotASetting() throws Exception {
        assertEquals(java.util.Arrays.asList(VoicePacks.PIPER_AMY), VoicePacks.ALL);
        File none = Files.createTempDirectory("voices").toFile();
        assertEquals("the child's voice, always", VoicePacks.PIPER_AMY, VoicePacks.chosen(none));
        assertEquals(VoicePacks.PIPER_AMY, ai.ibiko.main.AppFiles.VOICE);
        assertEquals("amy", TalkBridge.CHILD_SPEAKER);
        assertEquals(1.32f, TalkBridge.CHILD_PITCH, 0.0001f);
        java.lang.reflect.Field f = ai.ibiko.chat.ui.SettingsActivity.class.getDeclaredField("SECTIONS");
        f.setAccessible(true);
        for (String[] section : (String[][]) f.get(null)) {
            assertFalse("no Voice page", "Voice".equals(section[0]));
        }
        java.lang.reflect.Field sc = ai.ibiko.chat.ui.SettingsActivity.class.getDeclaredField("SCREENS");
        sc.setAccessible(true);
        assertEquals("every section still opens its own screen", ((String[][]) f.get(null)).length, ((Class<?>[]) sc.get(null)).length);
        // the voices older versions kept and no longer offered (Piper Ryan, Kokoro) are removed
        File files = RuntimeEnvironment.getApplication().getFilesDir();
        File piper = VoicePacks.folder(files, VoicePacks.PIPER_RYAN), kokoro = VoicePacks.folder(files, VoicePacks.KOKORO);
        piper.mkdirs();
        kokoro.mkdirs();
        Files.write(new File(piper, "model.onnx").toPath(), new byte[] {1});
        Files.write(new File(VoicePacks.root(files), VoicePacks.PIPER_RYAN.key + ".ready").toPath(), new byte[] {1});
        assertEquals(2, VoicePacks.dropOthers(files));
        assertFalse(piper.exists());
        assertFalse(kokoro.exists());
        assertFalse(new File(VoicePacks.root(files), VoicePacks.PIPER_RYAN.key + ".ready").exists());
    }

    @Test
    public void aNewPlayerSpeedPlaysTheKeptSoundFasterAndNeverMakesItAgain() throws Exception {
        TalkBridge t = new TalkBridge(RuntimeEnvironment.getApplication());
        float made = t.madeRate(1f);
        String key = NeuralVoice.madeKey("Hello there.", 4, made);
        for (float speed : new float[] {0.75f, 1.25f, 1.5f, 2f}) {
            t.setPlayerSpeed(speed);
            assertEquals("the sound is made and kept at the voice's own speed", made, t.madeRate(1f), 0f);
            assertEquals("so the kept sound is found again", key, NeuralVoice.madeKey("Hello there.", 4, t.madeRate(1f)));
        }
        assertEquals(2f, t.playerSpeed(), 0f);
        t.shutdown();
    }
}
