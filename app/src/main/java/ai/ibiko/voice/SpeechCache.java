package ai.ibiko.voice;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The voice of replies, made once and kept on the device for {@link #KEEP_MS} (30 days), as ordinary audio files
 * (16-bit WAV, which any player opens), so Listen plays the kept sound at once instead of making it while you wait.
 * <p>
 * Each reply's voice belongs to that message alone: it is kept in {@code files/voices/said/<chat>/<message>/}, one file
 * a sentence, numbered in the order they are said ({@code 000-<sha1>.wav}, {@code 001-…}); {@link #wholeMessage} joins
 * them into one file. The owner is
 * {@code "<chat>/<message>"}. Deleting a chat deletes {@code said/<chat>} with every message's voice in it
 * ({@link #forgetChat}). Speech that belongs to no message (a sentence read as it is written) is kept loose in
 * {@code said/} for the same 30 days. Past 30 days, or past {@link #MOST_BYTES} in all (the oldest first), files are
 * cleared; the voice is then simply made again when wanted. Files kept by older versions (raw floats, {@code .f32}) are
 * still read.
 */
public final class SpeechCache {
    /** 30 days: long enough to listen again a month on, short enough not to pile up. */
    public static final long KEEP_MS = 30L * 24 * 60 * 60 * 1000;
    /** About seven hours of speech (16-bit, 24 kHz). */
    public static final long MOST_BYTES = 1200L * 1024 * 1024;
    /** The rate a file is written at when the voice's own rate isn't given (Kokoro's). */
    public static final int RATE = 24000;

    private final File dir;

    public SpeechCache(File filesDir) {
        dir = root(filesDir);
    }

    static File root(File filesDir) {
        return new File(filesDir, "voices/said");
    }

    /** Deletes the voice kept for every message of a chat. */
    public static void forgetChat(File filesDir, String chat) {
        if (filesDir == null || chat == null || chat.isEmpty()) {
            return;
        }
        wipe(new File(root(filesDir), safe(chat)));
    }

    static String sha(String key) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(key.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) {
                b.append(String.format(java.util.Locale.US, "%02x", x));
            }
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(key.hashCode());
        }
    }

    /** A loose sentence's file name (no owner). */
    static String name(String key) {
        return sha(key) + ".wav";
    }

    /** A kept sound file (this version's WAV, or an older version's raw floats). */
    static boolean sound(String name) {
        return name.endsWith(".wav") || name.endsWith(".f32");
    }

    private static String safe(String part) {
        String s = part == null ? "" : part.replaceAll("[^A-Za-z0-9._-]", "_");
        return s.isEmpty() || s.startsWith(".") ? "_" : s;
    }

    /** The folder of one message's voice: {@code said/<chat>/<message>}; {@code said} itself with no owner. */
    File folder(String owner) {
        if (owner == null || owner.isEmpty()) {
            return dir;
        }
        File f = dir;
        for (String part : owner.split("/")) {
            if (!part.isEmpty()) {
                f = new File(f, safe(part));
            }
        }
        return f;
    }

    /** This sentence's kept file for this owner, or null. */
    private File find(String owner, String key) {
        String h = sha(key);
        if (owner == null || owner.isEmpty()) {
            File f = new File(dir, h + ".wav");
            if (f.isFile()) {
                return f;
            }
            f = new File(dir, h + ".f32");
            return f.isFile() ? f : null;
        }
        final String wav = "-" + h + ".wav", old = "-" + h + ".f32";
        File[] hit = folder(owner).listFiles((d, n) -> n.endsWith(wav) || n.endsWith(old));
        return hit == null || hit.length == 0 ? null : hit[0];
    }

    public float[] get(String key) {
        return get(null, key);
    }

    /** The kept sound of this sentence of this message, or null (none, or older than a week). */
    public float[] get(String owner, String key) {
        File f = find(owner, key);
        if (f == null) {
            return null;
        }
        if (System.currentTimeMillis() - f.lastModified() > KEEP_MS) {
            f.delete();
            return null;
        }
        return read(f);
    }

    /** A kept file's samples (-1 to 1), read in one go; null when it is damaged (and then deleted). */
    static float[] read(File f) {
        try {
            byte[] all = java.nio.file.Files.readAllBytes(f.toPath());
            java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(all);
            if (f.getName().endsWith(".wav")) {
                int data = wavData(all);
                if (data < 0) {
                    throw new IOException("not a WAV file");
                }
                b.order(java.nio.ByteOrder.LITTLE_ENDIAN).position(data);
                int n = Math.min(b.getInt(data - 4) / 2, (all.length - data) / 2);
                float[] s = new float[n];
                java.nio.ShortBuffer pcm = b.asShortBuffer();
                for (int i = 0; i < n; i++) {
                    s[i] = pcm.get(i) / 32768f;
                }
                return s;
            }
            int n = b.getInt(); // an older version's raw floats: a count, then the samples (big-endian)
            if (n <= 0 || n > 20_000_000 || all.length < 4 + 4L * n) {
                throw new IOException("damaged");
            }
            float[] s = new float[n];
            b.asFloatBuffer().get(s);
            return s;
        } catch (IOException | RuntimeException e) {
            f.delete();
            return null;
        }
    }

    /** Where a WAV file's samples start (just after its "data" chunk header), or -1. */
    static int wavData(byte[] w) {
        if (w.length < 44 || w[0] != 'R' || w[1] != 'I' || w[2] != 'F' || w[3] != 'F' || w[8] != 'W' || w[9] != 'A') {
            return -1;
        }
        int p = 12;
        while (p + 8 <= w.length) {
            int size = (w[p + 4] & 0xff) | (w[p + 5] & 0xff) << 8 | (w[p + 6] & 0xff) << 16 | (w[p + 7] & 0xff) << 24;
            if (w[p] == 'd' && w[p + 1] == 'a' && w[p + 2] == 't' && w[p + 3] == 'a') {
                return p + 8;
            }
            p += 8 + size + (size & 1);
        }
        return -1;
    }

    /** Samples as a 16-bit mono WAV file's bytes: what any player opens. */
    public static byte[] wav(float[] samples, int rate) {
        int n = samples.length, bytes = n * 2;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(44 + bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + bytes).put(new byte[]{'W', 'A', 'V', 'E'});
        b.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) 1).putInt(rate)
                .putInt(rate * 2).putShort((short) 2).putShort((short) 16);
        b.put(new byte[]{'d', 'a', 't', 'a'}).putInt(bytes);
        for (float v : samples) {
            float c = Math.max(-1f, Math.min(1f, v));
            b.putShort((short) Math.round(c * 32767f));
        }
        return b.array();
    }

    /**
     * One message's whole voice as one audio file ({@code said/<chat>/<message>/message.wav}): its sentences joined in
     * order, with a short pause between them. Made once and kept with them (the same 30 days); null when nothing of it
     * was kept.
     */
    public File wholeMessage(String owner, int rate) {
        List<File> parts = ordered(owner);
        if (parts.isEmpty()) {
            return null;
        }
        File out = new File(folder(owner), "message.wav");
        long newest = 0;
        for (File f : parts) {
            newest = Math.max(newest, f.lastModified());
        }
        if (out.isFile() && out.lastModified() >= newest) {
            return out;
        }
        int r = rate > 0 ? rate : RATE, gap = r / 4;
        List<float[]> sound = new ArrayList<float[]>();
        int total = 0;
        for (File f : parts) {
            float[] s = read(f);
            if (s != null) {
                sound.add(s);
                total += s.length + gap;
            }
        }
        if (sound.isEmpty()) {
            return null;
        }
        float[] all = new float[total];
        int at = 0;
        for (float[] s : sound) {
            System.arraycopy(s, 0, all, at, s.length);
            at += s.length + gap;
        }
        File tmp = new File(out.getPath() + ".part");
        try {
            java.nio.file.Files.write(tmp.toPath(), wav(all, r));
            if (!tmp.renameTo(out)) {
                out.delete();
                if (!tmp.renameTo(out)) {
                    return null;
                }
            }
            return out;
        } catch (IOException e) {
            tmp.delete();
            return null;
        }
    }

    public boolean has(String key) {
        return has(null, key);
    }

    public boolean has(String owner, String key) {
        File f = find(owner, key);
        return f != null && System.currentTimeMillis() - f.lastModified() <= KEEP_MS;
    }

    public void put(String key, float[] samples) {
        put(null, key, samples);
    }

    public void put(String owner, String key, float[] samples) {
        put(owner, key, samples, RATE);
    }

    /**
     * Keeps a sentence's sound under its message, numbered after the ones already there (the order they are said);
     * then clears what is too old and, past the size limit, the oldest.
     * <p>
     * Written to a temporary file first, then renamed. On Android {@code renameTo} fails when the target already
     * exists, so the old file is removed first and a byte copy is used if the rename still fails. The temporary file
     * is kept until the real one is in place, so a crash mid-write does not leave a half file that is later played.
     */
    public void put(String owner, String key, float[] samples, int rate) {
        if (samples == null || samples.length == 0) {
            return;
        }
        File folder = folder(owner);
        folder.mkdirs();
        File f = find(owner, key);
        if (f == null) {
            if (owner == null || owner.isEmpty()) {
                f = new File(folder, name(key));
            } else {
                File[] there = folder.listFiles((d, n) -> sound(n) && !n.equals("message.wav"));
                int next = there == null ? 0 : there.length;
                f = new File(folder, String.format(java.util.Locale.US, "%03d-%s.wav", next, sha(key)));
            }
        }
        File tmp = new File(folder, f.getName() + ".part");
        try {
            java.nio.file.Files.write(tmp.toPath(), wav(samples, rate > 0 ? rate : RATE)); // one write, not one per sample
        } catch (IOException e) {
            tmp.delete();
            return;
        }
        if (f.exists() && !f.delete()) {
            // the old file is locked or the device refused: leave the new one beside it under a unique name
            f = new File(folder, System.currentTimeMillis() + "-" + f.getName());
        }
        if (!tmp.renameTo(f)) {
            if (!copy(tmp, f)) {
                tmp.delete();
                return;
            }
            tmp.delete();
        }
        prune();
    }

    /** Copies the bytes when a rename is refused, so the sound is still kept. */
    private static boolean copy(File from, File to) {
        try (java.io.InputStream in = new java.io.BufferedInputStream(new java.io.FileInputStream(from));
                java.io.OutputStream out = new java.io.BufferedOutputStream(new java.io.FileOutputStream(to))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            to.delete();
            return false;
        }
    }

    /** One message's kept sentences, in the order they are said. */
    public List<File> ordered(String owner) {
        File[] all = owner == null || owner.isEmpty() ? null : folder(owner).listFiles((d, n) -> sound(n) && !n.equals("message.wav"));
        List<File> out = new ArrayList<File>(all == null ? new ArrayList<File>() : Arrays.asList(all));
        out.sort((a, b) -> a.getName().compareTo(b.getName()));
        return out;
    }

    /** Clears sounds older than 30 days (in every message's folder), then the oldest until all fits {@link #MOST_BYTES}. */
    public void prune() {
        List<File> all = new ArrayList<File>();
        collect(dir, all);
        long now = System.currentTimeMillis(), total = 0;
        List<File> kept = new ArrayList<File>();
        for (File f : all) {
            if (now - f.lastModified() > KEEP_MS) {
                f.delete();
            } else {
                kept.add(f);
                total += f.length();
            }
        }
        kept.sort((a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < kept.size() && total > MOST_BYTES; i++) {
            total -= kept.get(i).length();
            kept.get(i).delete();
        }
        dropEmpty(dir);
    }

    private static void collect(File d, List<File> out) {
        File[] kids = d.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory() && !java.nio.file.Files.isSymbolicLink(k.toPath())) {
                collect(k, out);
            } else if (sound(k.getName())) {
                out.add(k);
            }
        }
    }

    private void dropEmpty(File d) {
        File[] kids = d.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory() && !java.nio.file.Files.isSymbolicLink(k.toPath())) {
                dropEmpty(k);
                String[] left = k.list();
                if (left != null && left.length == 0) {
                    k.delete();
                }
            }
        }
    }

    private static void wipe(File f) {
        File[] kids = java.nio.file.Files.isSymbolicLink(f.toPath()) ? null : f.listFiles(); // a link is not followed
        if (kids != null) {
            for (File k : kids) {
                wipe(k);
            }
        }
        f.delete();
    }
}
