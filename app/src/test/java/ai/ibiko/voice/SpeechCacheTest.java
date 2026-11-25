package ai.ibiko.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;

/** A reply's voice is kept, as WAV files, under its message's id for 30 days, in the order it is said, and goes with its chat. */
public class SpeechCacheTest {
    @Test
    public void keptFor30DaysThenCleared() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        SpeechCache c = new SpeechCache(files);
        String key = NeuralVoice.madeKey("Hello there.", 4, 1f);
        float[] sound = {0.1f, -0.2f, 0.3f};
        assertNull(c.get(key));
        c.put(key, sound);
        assertTrue(c.has(key));
        assertArrayEquals("16-bit audio: within a step", sound, c.get(key), 1e-4f);
        File f = new File(files, "voices/said/" + SpeechCache.name(key));
        assertTrue(f.setLastModified(System.currentTimeMillis() - SpeechCache.KEEP_MS - 1000));
        assertFalse("older than 30 days: made again when wanted", c.has(key));
        assertNull(c.get(key));
        assertFalse(f.exists());
        assertEquals(30L * 24 * 60 * 60 * 1000, SpeechCache.KEEP_MS);
    }

    @Test
    public void eachMessageKeepsItsOwnVoiceInOrderAndGoesWithItsChat() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        SpeechCache c = new SpeechCache(files);
        String one = NeuralVoice.madeKey("First sentence.", 4, 1f), two = NeuralVoice.madeKey("Second sentence.", 4, 1f);
        c.put("g7/mA", one, new float[]{0.1f});
        c.put("g7/mA", two, new float[]{0.2f});
        c.put("g8/mB", one, new float[]{0.3f});
        assertTrue(c.has("g7/mA", one));
        assertFalse("another message's voice is not this one's", c.has("g7/mZ", one));
        List<File> order = c.ordered("g7/mA");
        assertEquals(2, order.size());
        assertTrue(order.get(0).getName().startsWith("000-") && order.get(1).getName().startsWith("001-"));
        assertArrayEquals(new float[]{0.2f}, c.get("g7/mA", two), 1e-4f);
        SpeechCache.forgetChat(files, "g7");
        assertFalse(c.has("g7/mA", one));
        assertFalse(new File(files, "voices/said/g7").exists());
        assertTrue("other chats keep theirs", c.has("g8/mB", one));
    }

    @Test
    public void theVoiceIsAmyAtHerNaturalPitch() {
        assertEquals("the voice at its natural pitch", 1f, TalkBridge.PITCH, 0.0001f);
        assertTrue("Amy is in the pack", VoicePacks.byKey("piper-amy").byName(TalkBridge.SPEAKER) != null);
    }

    @Test
    public void keptAsAudioFilesAndJoinedIntoOne() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        SpeechCache c = new SpeechCache(files);
        c.put("g1/m1", "one", new float[]{0.5f, -0.5f}, 24000);
        c.put("g1/m1", "two", new float[]{0.25f}, 24000);
        File first = c.ordered("g1/m1").get(0);
        byte[] w = Files.readAllBytes(first.toPath());
        assertEquals("RIFF", new String(w, 0, 4, "US-ASCII"));
        assertEquals("WAVE", new String(w, 8, 4, "US-ASCII"));
        assertEquals(44, SpeechCache.wavData(w));
        File whole = c.wholeMessage("g1/m1", 24000);
        assertTrue(whole.getName().equals("message.wav") && whole.length() > 44);
        float[] all = SpeechCache.read(whole);
        assertEquals("both sentences and a quarter-second pause after each", 2 + 1 + 2 * 6000, all.length);
        assertEquals(0.5f, all[0], 1e-4f);
        assertEquals("the whole file isn't one of the sentences", 2, c.ordered("g1/m1").size());
    }

    @Test
    public void anOlderVersionsRawFloatsAreStillRead() throws Exception {
        File files = Files.createTempDirectory("files").toFile();
        File said = new File(files, "voices/said");
        said.mkdirs();
        String key = NeuralVoice.madeKey("Old.", 4, 1f);
        File f = new File(said, SpeechCache.sha(key) + ".f32");
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.FileOutputStream(f))) {
            out.writeInt(2);
            out.writeFloat(0.25f);
            out.writeFloat(-0.75f);
        }
        assertArrayEquals(new float[]{0.25f, -0.75f}, new SpeechCache(files).get(key), 0f);
    }
}
