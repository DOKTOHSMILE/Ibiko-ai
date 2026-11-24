package ai.ibiko.voice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * A voice of the pilot's own. Three parts, all kept here, none needing the web:
 *
 * <ol>
 *   <li>Recording: the pilot reads the sentences of {@code assets/voice/recording_script.tsv.ibk}
 *       (tools/build_voice_script.py) one at a time; each take is checked (long enough, not too loud, not too quiet,
 *       not drowned in noise), its silence at both ends is trimmed, and it is kept as {@code myvoice/wavs/NNNN.wav}
 *       (22,050 Hz, mono, 16-bit) with its words in {@code myvoice/metadata.csv} ("NNNN|sentence"): the form a Piper
 *       voice is trained from.</li>
 *   <li>Export: the recordings as one zip, for the training notebook (tools/voice/train_my_voice.ipynb).</li>
 *   <li>Install: the trained voice's zip (its {@code .onnx} model and {@code tokens.txt}) unpacked as the "My voice"
 *       pack ({@link VoicePacks#MY_VOICE}), which Ibiko then speaks with; the pronunciation data is shared with the
 *       Kokoro pack.</li>
 * </ol>
 */
public final class MyVoice {
    private MyVoice() {
    }

    /** The rate a Piper voice is trained at. */
    public static final int RATE = 22050;

    /** One sentence to read. */
    public static final class Line {
        public final String id, text;

        Line(String id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    /** The sentences to read, in order, from the script's text ("id\tsentence" a line). */
    public static List<Line> script(String text) {
        List<Line> out = new ArrayList<Line>();
        for (String line : (text == null ? "" : text).split("\n")) {
            String[] f = line.split("\t", 2);
            if (f.length == 2 && !line.startsWith("#") && !f[1].trim().isEmpty()) {
                out.add(new Line(f[0].trim(), f[1].trim()));
            }
        }
        return out;
    }

    public static File root(File filesDir) {
        return new File(filesDir, "myvoice");
    }

    static File wavs(File filesDir) {
        return new File(root(filesDir), "wavs");
    }

    public static File wav(File filesDir, String id) {
        return new File(wavs(filesDir), id + ".wav");
    }

    // ------------------------------------------------------------------ checking a take

    /** What a take is like, and whether it can be kept. */
    public static final class Take {
        public final double seconds, peak, loudness, noise;
        public final String problem;

        Take(double seconds, double peak, double loudness, double noise, String problem) {
            this.seconds = seconds;
            this.peak = peak;
            this.loudness = loudness;
            this.noise = noise;
            this.problem = problem;
        }

        public boolean ok() {
            return problem == null;
        }
    }

    /**
     * A take checked: too short or too long for its sentence, clipped (too loud: the voice distorts), too quiet, or
     * noisy (the quiet parts not much quieter than the speech). {@code words}: how many words the sentence has.
     */
    public static Take check(short[] pcm, int rate, int words) {
        int n = pcm == null ? 0 : pcm.length;
        double seconds = n / (double) rate;
        if (n == 0) {
            return new Take(0, 0, 0, 0, "Nothing was recorded. Check the microphone.");
        }
        int clipped = 0;
        double peak = 0;
        // loudness in 20 ms frames: the speech is the loud frames, the noise the quietest
        int frame = Math.max(1, rate / 50);
        List<Double> frames = new ArrayList<Double>();
        for (int i = 0; i < n; i += frame) {
            double sum = 0;
            int end = Math.min(n, i + frame);
            for (int j = i; j < end; j++) {
                double v = pcm[j] / 32768.0;
                sum += v * v;
                peak = Math.max(peak, Math.abs(v));
                clipped += Math.abs(pcm[j]) >= 32700 ? 1 : 0;
            }
            frames.add(Math.sqrt(sum / Math.max(1, end - i)));
        }
        List<Double> sorted = new ArrayList<Double>(frames);
        java.util.Collections.sort(sorted);
        // the background: the quietest twentieth (a kept take has a fifth of a second of it at each end)
        double noise = sorted.get(Math.max(0, (int) (sorted.size() * 0.05) - 1));
        double speech = sorted.get(Math.min(sorted.size() - 1, (int) (sorted.size() * 0.9)));
        String problem = null;
        double least = Math.max(0.8, words * 0.18), most = Math.max(6, words * 1.0 + 3);
        if (clipped > n / 1000) {
            problem = "Too loud: the sound is clipped. Hold the phone a little further away.";
        } else if (speech < 0.02) {
            problem = "Too quiet. Speak up a little, or hold the phone closer.";
        } else if (seconds < least) {
            problem = "That was cut short. Read the whole sentence.";
        } else if (seconds > most) {
            problem = "That took too long for this sentence. Stop recording when you finish.";
        } else if (noise > 0.0 && speech / Math.max(noise, 1e-6) < 6) {
            problem = "Too much background noise. Find a quieter place.";
        }
        return new Take(seconds, peak, 20 * Math.log10(Math.max(speech, 1e-6)), 20 * Math.log10(Math.max(noise, 1e-6)), problem);
    }

    /** The take with its silence at both ends cut, keeping a fifth of a second either side of the speech. */
    public static short[] trim(short[] pcm, int rate) {
        if (pcm == null || pcm.length == 0) {
            return pcm;
        }
        int frame = Math.max(1, rate / 50);
        double loudest = 0;
        List<Double> f = new ArrayList<Double>();
        for (int i = 0; i < pcm.length; i += frame) {
            double sum = 0;
            int end = Math.min(pcm.length, i + frame);
            for (int j = i; j < end; j++) {
                double v = pcm[j] / 32768.0;
                sum += v * v;
            }
            double r = Math.sqrt(sum / Math.max(1, end - i));
            f.add(r);
            loudest = Math.max(loudest, r);
        }
        double gate = loudest * 0.08;
        int first = 0, last = f.size() - 1;
        while (first < f.size() && f.get(first) < gate) {
            first++;
        }
        while (last > first && f.get(last) < gate) {
            last--;
        }
        int pad = rate / 5;
        int from = Math.max(0, first * frame - pad), to = Math.min(pcm.length, (last + 1) * frame + pad);
        return java.util.Arrays.copyOfRange(pcm, from, Math.max(from, to));
    }

    // ------------------------------------------------------------------ keeping it

    /** A take kept: its WAV written, and its line in metadata.csv (a take read again replaces the old one). */
    public static synchronized void keep(File filesDir, Line line, short[] pcm, int rate) throws IOException {
        File w = wav(filesDir, line.id);
        w.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(w)) {
            out.write(wavBytes(pcm, rate));
        }
        Map<String, String> meta = metadata(filesDir);
        meta.put(line.id, line.text);
        writeMetadata(filesDir, meta);
    }

    /** A take thrown away. */
    public static synchronized void drop(File filesDir, String id) throws IOException {
        wav(filesDir, id).delete();
        Map<String, String> meta = metadata(filesDir);
        meta.remove(id);
        writeMetadata(filesDir, meta);
    }

    /** What is recorded so far: id → sentence, in the order of the script. */
    public static synchronized Map<String, String> metadata(File filesDir) {
        Map<String, String> out = new java.util.TreeMap<String, String>();
        File f = new File(root(filesDir), "metadata.csv");
        if (!f.isFile()) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int bar = line.indexOf('|');
                if (bar > 0 && wav(filesDir, line.substring(0, bar)).isFile()) {
                    out.put(line.substring(0, bar), line.substring(bar + 1));
                }
            }
        } catch (IOException ignored) {
            // none yet
        }
        return out;
    }

    private static void writeMetadata(File filesDir, Map<String, String> meta) throws IOException {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : meta.entrySet()) {
            b.append(e.getKey()).append('|').append(e.getValue().replace('|', ' ').replace('\n', ' ')).append('\n');
        }
        File f = new File(root(filesDir), "metadata.csv");
        f.getParentFile().mkdirs();
        Files.write(f.toPath(), b.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** How many minutes of speech are recorded (from the WAVs' lengths). */
    public static double minutes(File filesDir) {
        long bytes = 0;
        for (String id : metadata(filesDir).keySet()) {
            bytes += Math.max(0, wav(filesDir, id).length() - 44);
        }
        return bytes / 2.0 / RATE / 60.0;
    }

    /** At least this much speech before training is worth it (more is better: 45 minutes or so). */
    public static final double ENOUGH_MINUTES = 30;

    /** A 16-bit mono WAV file's bytes. */
    public static byte[] wavBytes(short[] pcm, int rate) {
        int data = pcm.length * 2;
        ByteBuffer b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN);
        b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + data).put("WAVE".getBytes(StandardCharsets.US_ASCII));
        b.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate).putInt(rate * 2)
                .putShort((short) 2).putShort((short) 16);
        b.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(data);
        for (short s : pcm) {
            b.putShort(s);
        }
        return b.array();
    }

    /** The samples of a 16-bit mono WAV (for playing a take back). */
    public static short[] wavSamples(byte[] wav) {
        if (wav == null || wav.length <= 44) {
            return new short[0];
        }
        ByteBuffer b = ByteBuffer.wrap(wav, 44, wav.length - 44).order(ByteOrder.LITTLE_ENDIAN);
        short[] out = new short[(wav.length - 44) / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = b.getShort();
        }
        return out;
    }

    // ------------------------------------------------------------------ export, for training

    /**
     * The recordings as one zip for the training notebook: {@code my-voice/metadata.csv} and {@code my-voice/wavs/*.wav}.
     * How many takes went in.
     */
    public static int export(File filesDir, OutputStream to) throws IOException {
        Map<String, String> meta = metadata(filesDir);
        try (ZipOutputStream z = new ZipOutputStream(to)) {
            StringBuilder csv = new StringBuilder();
            for (Map.Entry<String, String> e : meta.entrySet()) {
                csv.append(e.getKey()).append('|').append(e.getValue()).append('\n');
            }
            z.putNextEntry(new ZipEntry("my-voice/metadata.csv"));
            z.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            for (String id : meta.keySet()) {
                z.putNextEntry(new ZipEntry("my-voice/wavs/" + id + ".wav"));
                z.write(Files.readAllBytes(wav(filesDir, id).toPath()));
                z.closeEntry();
            }
        }
        return meta.size();
    }

    // ------------------------------------------------------------------ install the trained voice

    /**
     * The trained voice's zip (from the notebook: a {@code .onnx} model and {@code tokens.txt}, anywhere inside),
     * unpacked as the My voice pack. Anything else in the zip is left out; nothing is written outside the pack's
     * folder. Throws, keeping the voice that was there, when the zip doesn't hold a voice.
     */
    public static void install(File filesDir, InputStream zip) throws IOException {
        File root = VoicePacks.root(filesDir);
        File into = new File(root, VoicePacks.MY_VOICE.dir + ".unpacking");
        deleteTree(into);
        into.mkdirs();
        boolean model = false, tokens = false;
        try (ZipInputStream z = new ZipInputStream(zip)) {
            ZipEntry e;
            byte[] buf = new byte[1 << 16];
            while ((e = z.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                String name = e.getName().replace('\\', '/');
                String base = name.substring(name.lastIndexOf('/') + 1);
                String to;
                if (base.toLowerCase(Locale.ROOT).endsWith(".onnx") && !model) {
                    to = VoicePacks.MY_VOICE.model;
                    model = true;
                } else if (base.equals("tokens.txt")) {
                    to = "tokens.txt";
                    tokens = true;
                } else if (base.toLowerCase(Locale.ROOT).endsWith(".onnx.json") || base.equals("config.json")) {
                    to = "config.json";
                } else {
                    continue;
                }
                try (OutputStream out = new FileOutputStream(new File(into, to))) {
                    long total = 0;
                    int n;
                    while ((n = z.read(buf)) > 0) {
                        total += n;
                        if (total > 400L * 1024 * 1024) {
                            throw new IOException("That file is too big to be a voice.");
                        }
                        out.write(buf, 0, n);
                    }
                }
            }
        } catch (IOException ex) {
            deleteTree(into);
            throw ex;
        }
        if (!model || !tokens || new File(into, VoicePacks.MY_VOICE.model).length() < 100_000) {
            deleteTree(into);
            throw new IOException("That zip doesn't hold a trained voice: it needs the .onnx model and tokens.txt from the notebook.");
        }
        File pack = VoicePacks.folder(filesDir, VoicePacks.MY_VOICE);
        deleteTree(pack);
        if (!into.renameTo(pack)) {
            deleteTree(into);
            throw new IOException("The voice couldn't be put in place.");
        }
    }

    /** Whether Ibiko speaks with the pilot's own voice (installed and chosen). */
    public static boolean chosen(File filesDir) {
        return new File(VoicePacks.root(filesDir), "my-voice.use").isFile() && VoicePacks.installed(filesDir, VoicePacks.MY_VOICE);
    }

    /** Speak with the pilot's own voice, or with Kokoro again. */
    public static void choose(File filesDir, boolean mine) {
        File f = new File(VoicePacks.root(filesDir), "my-voice.use");
        if (mine) {
            f.getParentFile().mkdirs();
            try {
                Files.write(f.toPath(), new byte[0]);
            } catch (IOException ignored) {
                // not chosen
            }
        } else {
            f.delete();
        }
    }

    /** The pack Ibiko speaks with: the pilot's own voice when chosen, else Kokoro. */
    public static VoicePacks.Pack pack(File filesDir) {
        return chosen(filesDir) ? VoicePacks.MY_VOICE : VoicePacks.KOKORO;
    }

    static void deleteTree(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] in = Files.isSymbolicLink(f.toPath()) ? null : f.listFiles();
        for (File c : in == null ? new File[0] : in) {
            deleteTree(c);
        }
        f.delete();
    }

    /** The whole of a stream (the script, from its asset). */
    static String read(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
        }
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
}
