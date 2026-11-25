package ai.ibiko.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

/** A voice of the pilot's own: the script, each take checked and kept, the recordings exported, the trained voice installed. */
public class MyVoiceTest {
    private static final int R = MyVoice.RATE;

    /** Speech-like sound: a voiced tone with a little noise under it, between quiet edges. */
    private static short[] speech(double seconds, double level, double noise, double edge) {
        Random rnd = new Random(7);
        int n = (int) (seconds * R), e = (int) (edge * R);
        short[] out = new short[n + 2 * e];
        for (int i = 0; i < out.length; i++) {
            double v = noise * (rnd.nextDouble() * 2 - 1);
            if (i >= e && i < e + n) {
                double t = (i - e) / (double) R;
                v += level * Math.sin(2 * Math.PI * 140 * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 3 * t));
            }
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(v * 32767)));
        }
        return out;
    }

    @Test
    public void theScriptShipsEncodedAndReads() throws Exception {
        File asset = new File("src/main/assets/voice/recording_script.tsv.ibk");
        assertTrue(asset.isFile());
        String text;
        try (java.io.InputStream in = ai.ibiko.ibiko1.TextCode.open(p -> {
            try {
                return new java.io.FileInputStream(new File("src/main/assets/" + p));
            } catch (java.io.IOException e) {
                return null;
            }
        }, "voice/recording_script.tsv")) {
            text = MyVoice.read(in);
        }
        List<MyVoice.Line> lines = MyVoice.script(text);
        assertEquals(750, lines.size());
        assertEquals("0001", lines.get(0).id);
        int words = 0;
        for (MyVoice.Line l : lines) {
            words += l.text.split("\\s+").length;
            assertFalse(l.text, l.text.matches(".*[0-9()\\[\\]].*"));
        }
        assertTrue("about 45 minutes or more read aloud: " + words, words / 150.0 >= 40);
    }

    @Test
    public void eachTakeIsChecked() {
        // as kept: trimmed, with a fifth of a second of quiet at each end
        assertTrue(MyVoice.check(speech(3.0, 0.3, 0.003, 0.2), R, 8).ok());
        assertFalse(MyVoice.check(new short[0], R, 8).ok());
        assertTrue(MyVoice.check(speech(3.0, 0.004, 0.0005, 0), R, 8).problem.startsWith("Too quiet"));
        assertTrue(MyVoice.check(speech(3.0, 1.4, 0.003, 0), R, 8).problem.startsWith("Too loud"));
        assertTrue(MyVoice.check(speech(0.5, 0.3, 0.003, 0), R, 12).problem.contains("cut short"));
        assertTrue(MyVoice.check(speech(25, 0.3, 0.003, 0), R, 8).problem.contains("too long"));
        assertTrue(MyVoice.check(speech(3.0, 0.3, 0.12, 0.6), R, 8).problem.contains("background noise"));
    }

    @Test
    public void silenceAtTheEndsIsTrimmed() {
        short[] take = speech(2.0, 0.3, 0.001, 1.5);
        short[] t = MyVoice.trim(take, R);
        double seconds = t.length / (double) R;
        assertTrue("2 s of speech, a fifth of a second either side: " + seconds, seconds > 2.0 && seconds < 2.8);
    }

    @Test
    public void takesAreKeptExportedAndRecounted() throws Exception {
        File files = Files.createTempDirectory("myvoice").toFile();
        MyVoice.Line a = new MyVoice.Line("0001", "Hello! How can I help you today?");
        MyVoice.Line b = new MyVoice.Line("0002", "The toys come in many different colors, shapes and sizes.");
        MyVoice.keep(files, a, speech(2.0, 0.3, 0.003, 0), R);
        MyVoice.keep(files, b, speech(4.0, 0.3, 0.003, 0), R);
        MyVoice.keep(files, a, speech(2.5, 0.3, 0.003, 0), R); // read again: replaces the first take
        Map<String, String> meta = MyVoice.metadata(files);
        assertEquals(2, meta.size());
        assertEquals(6.5 / 60, MyVoice.minutes(files), 0.01);
        byte[] wav = Files.readAllBytes(MyVoice.wav(files, "0001").toPath());
        assertEquals("RIFF", new String(wav, 0, 4, "US-ASCII"));
        assertEquals((int) (2.5 * R), MyVoice.wavSamples(wav).length);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        assertEquals(2, MyVoice.export(files, zip));
        List<String> names = new ArrayList<String>();
        String csv = null;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                names.add(e.getName());
                if (e.getName().endsWith("metadata.csv")) {
                    csv = new String(MyVoice.read(z).getBytes("UTF-8"), "UTF-8");
                }
            }
        }
        assertTrue(names.toString(), names.contains("my-voice/wavs/0001.wav") && names.contains("my-voice/wavs/0002.wav"));
        assertEquals("0001|Hello! How can I help you today?\n0002|The toys come in many different colors, shapes and sizes.\n", csv);
        MyVoice.drop(files, "0002");
        assertEquals(1, MyVoice.metadata(files).size());
    }

    @Test
    public void theTrainedVoiceIsInstalledAndChosen() throws Exception {
        File files = Files.createTempDirectory("myvoice").toFile();
        // Kokoro's pronunciation data, which the pilot's voice shares
        new File(VoicePacks.folder(files, VoicePacks.KOKORO), "espeak-ng-data").mkdirs();
        assertEquals(VoicePacks.DEFAULT, MyVoice.pack(files));
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(zip)) {
            z.putNextEntry(new ZipEntry("out/my_voice.onnx"));
            z.write(new byte[200_000]);
            z.closeEntry();
            z.putNextEntry(new ZipEntry("out/tokens.txt"));
            z.write("_ 0\n^ 1\n".getBytes("UTF-8"));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("../../evil.txt")); // nothing is written outside the voice's folder
            z.write("x".getBytes("UTF-8"));
            z.closeEntry();
        }
        MyVoice.install(files, new ByteArrayInputStream(zip.toByteArray()));
        assertTrue(VoicePacks.installed(files, VoicePacks.MY_VOICE));
        assertFalse(new File(files.getParentFile(), "evil.txt").exists());
        assertEquals(VoicePacks.MY_VOICE, VoicePacks.byKey("my-voice"));
        assertEquals(new File(VoicePacks.folder(files, VoicePacks.KOKORO), "espeak-ng-data"), VoicePacks.espeakData(files, VoicePacks.MY_VOICE));
        MyVoice.choose(files, true);
        assertTrue(MyVoice.chosen(files));
        assertEquals(VoicePacks.MY_VOICE, MyVoice.pack(files));
        MyVoice.choose(files, false);
        assertEquals(VoicePacks.DEFAULT, MyVoice.pack(files));
        // with the fast voice in place, the pilot's voice reads its pronunciation data (Kokoro may not be on the phone)
        new File(VoicePacks.folder(files, VoicePacks.DEFAULT), "espeak-ng-data").mkdirs();
        assertEquals(new File(VoicePacks.folder(files, VoicePacks.DEFAULT), "espeak-ng-data"), VoicePacks.espeakData(files, VoicePacks.MY_VOICE));
        // a zip without a voice is refused, and the voice that was there stays
        ByteArrayOutputStream bad = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bad)) {
            z.putNextEntry(new ZipEntry("photo.jpg"));
            z.write(new byte[10]);
            z.closeEntry();
        }
        try {
            MyVoice.install(files, new ByteArrayInputStream(bad.toByteArray()));
            fail("a zip with no voice in it is refused");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        }
        assertTrue(VoicePacks.installed(files, VoicePacks.MY_VOICE));
    }
}
