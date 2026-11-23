package ai.ibiko.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

/**
 * Knowledge that can be upgraded without a new app: every knowledge file the app ships (the encyclopedia, the fact
 * tables, the lexicon, the neural files, the packs) can be replaced on the phone by a newer copy, kept in
 * {@code files/knowledge-updates/} under the same path. A replaced file is read first; the one in the app is the
 * fallback. An update may be the plain file (no text code needed: {@link ai.ibiko.ibiko1.TextCode} reads a plain file
 * before its coded form) or the coded one. Every copy is checked against its SHA-256 before it is used, written beside
 * the old one and moved over it, so a broken download never replaces a good file. Pure Java over a folder.
 */
public final class KnowledgeFiles {
    /** The largest file taken (the biggest encyclopedia shard is far smaller). */
    public static final long MOST = 64L * 1024 * 1024;

    private final File dir;
    private final File versions;

    public KnowledgeFiles(File filesDir) {
        this.dir = new File(filesDir, "knowledge-updates");
        this.versions = new File(dir, "versions.properties");
    }

    /** The app's files with the replaced ones over them: a replaced copy first, the app's own otherwise. */
    public static java.util.function.Function<String, InputStream> over(File filesDir,
            java.util.function.Function<String, InputStream> app) {
        final KnowledgeFiles updates = new KnowledgeFiles(filesDir);
        return path -> {
            InputStream newer = updates.open(path);
            return newer != null ? newer : app.apply(path);
        };
    }

    /** A path that is a knowledge file, plainly: "knowledge/…", no way out of the folder. */
    public static boolean allowed(String path) {
        return path != null && path.startsWith("knowledge/") && !path.contains("..") && !path.contains("\\")
                && !path.contains("//") && path.matches("[A-Za-z0-9_./-]+") && path.length() < 200;
    }

    /** The replaced copy of {@code path}, or null when none was installed (the app's own is used). */
    public InputStream open(String path) {
        if (!allowed(path)) {
            return null;
        }
        File f = new File(dir, path);
        try {
            return f.isFile() ? new FileInputStream(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** The version installed for {@code path}; 0 when it is the app's own. */
    public synchronized int version(String path) {
        Properties p = load();
        try {
            return Integer.parseInt(p.getProperty(path, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Installs {@code bytes} as {@code path} at {@code version}: only when the SHA-256 matches, the path is a knowledge
     * file, and the version is newer than the one installed. True when it was installed.
     */
    public synchronized boolean install(String path, int version, String sha256, byte[] bytes) {
        if (!allowed(path) || bytes == null || bytes.length == 0 || bytes.length > MOST || version <= version(path)
                || sha256 == null || !sha256.equalsIgnoreCase(sha256(bytes))) {
            return false;
        }
        File target = new File(dir, path);
        File parent = target.getParentFile();
        if (parent == null || !parent.isDirectory() && !parent.mkdirs()) {
            return false;
        }
        File tmp = new File(parent, target.getName() + ".part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(bytes);
            out.getFD().sync();
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target)) {
                tmp.delete();
                return false;
            }
        }
        // the other form of the same file goes: a plain update must not be shadowed by an older coded one, and back
        String other = path.endsWith(".ibk") ? path.substring(0, path.length() - 4) : path + ".ibk";
        new File(dir, other).delete();
        Properties p = load();
        p.setProperty(path, String.valueOf(version));
        p.remove(other);
        save(p);
        return true;
    }

    /** Goes back to the app's own copy of {@code path}. */
    public synchronized void remove(String path) {
        if (!allowed(path)) {
            return;
        }
        new File(dir, path).delete();
        Properties p = load();
        p.remove(path);
        save(p);
    }

    /** How many files are replaced now. */
    public synchronized int count() {
        return load().size();
    }

    private Properties load() {
        Properties p = new Properties();
        if (versions.isFile()) {
            try (InputStream in = new FileInputStream(versions)) {
                p.load(in);
            } catch (IOException ignored) {
                // unreadable: treated as nothing installed, the files are read again on the next install
            }
        }
        return p;
    }

    private void save(Properties p) {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return;
        }
        File tmp = new File(dir, "versions.properties.part");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "knowledge files replaced on this phone: path=version");
        } catch (IOException e) {
            return;
        }
        if (!tmp.renameTo(versions)) {
            versions.delete();
            tmp.renameTo(versions);
        }
    }

    public static String sha256(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : d) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] readAll(InputStream in, long most) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
        byte[] buf = new byte[1 << 16];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > most) {
                throw new IOException("too large");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
