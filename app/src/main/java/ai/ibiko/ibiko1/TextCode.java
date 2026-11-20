package ai.ibiko.ibiko1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The Ibiko v1 text form (IBK1T, {@code tools/encode_text.py}): a knowledge file (a table, a pack) with every word a
 * number from the file's own vocabulary, most-used words the smallest. It decodes to the very same bytes as the file
 * it was made from, so whatever reads the file reads the decoded stream unchanged. The app ships only
 * {@code <name>.ibk}; {@link #open} gives the plain file's bytes from it. Pure Java.
 */
public final class TextCode {
    private TextCode() {
    }

    /** The coded form's name for a file: {@code knowledge/lexicon.tsv} is shipped as {@code knowledge/lexicon.tsv.ibk}. */
    public static String coded(String path) {
        return path + ".ibk";
    }

    /**
     * The plain bytes of {@code path}: the file itself when it is there (a build that still ships it), otherwise its
     * coded form decoded; null when neither is.
     */
    public static InputStream open(java.util.function.Function<String, InputStream> files, String path) {
        InputStream plain = files.apply(path);
        if (plain != null) {
            return plain;
        }
        InputStream in = files.apply(coded(path));
        if (in == null) {
            return null;
        }
        try (InputStream src = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
            decode(src, out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Decodes a coded file to {@code out}. */
    public static void decode(InputStream in, OutputStream out) throws IOException {
        byte[] b = readAll(in);
        if (b.length < 5 || b[0] != 'I' || b[1] != 'B' || b[2] != 'K' || b[3] != '1' || b[4] != 'T') {
            throw new IOException("not an IBK1T file");
        }
        int[] p = {5};
        int count = Encyclopedia.varint(b, p);
        byte[][] words = new byte[count][];
        String[] text = new String[count];
        for (int i = 0; i < count; i++) {
            int n = Encyclopedia.varint(b, p);
            words[i] = java.util.Arrays.copyOfRange(b, p[0], p[0] + n);
            p[0] += n;
        }
        java.io.BufferedOutputStream o = new java.io.BufferedOutputStream(out, 1 << 16);
        while (p[0] < b.length) {
            int code = Encyclopedia.varint(b, p);
            int v = code >>> 3, kase = (code >>> 1) & 3;
            if ((code & 1) != 0) {
                o.write(' ');
            }
            if (v == 0) {
                int n = Encyclopedia.varint(b, p);
                o.write(b, p[0], n);
                p[0] += n;
            } else if (v - 1 < count) {
                if (kase == 0) {
                    o.write(words[v - 1]);
                } else {
                    String w = text[v - 1];
                    if (w == null) {
                        w = new String(words[v - 1], StandardCharsets.UTF_8);
                        text[v - 1] = w;
                    }
                    w = kase == 1 ? Encyclopedia.capital(w) : w.toUpperCase(Locale.ROOT);
                    o.write(w.getBytes(StandardCharsets.UTF_8));
                }
            } else {
                throw new IOException("a word number past the vocabulary: a damaged file");
            }
        }
        o.flush();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 15];
        int n;
        while ((n = in.read(buf)) > 0) {
            o.write(buf, 0, n);
        }
        return o.toByteArray();
    }
}
