package ai.ibiko.linux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** The folder the Linux shell was left in, shared by the terminal and the chat. */
public final class LinuxCwd {
    private LinuxCwd() {
    }

    public static String read(File rootfs) {
        if (rootfs == null) {
            return "/root";
        }
        try {
            String text = small(new File(rootfs, "root/.ibiko_cwd"));
            return text.startsWith("/") ? text : "/root";
        } catch (Exception e) {
            return "/root";
        }
    }

    /**
     * A status or folder file the guest writes, read at most 4 KB and never through a link: a command inside Linux can
     * replace it with a link to a huge file, and a whole read froze or crashed the app.
     */
    public static String small(File f) {
        if (f == null || !f.isFile() || Files.isSymbolicLink(f.toPath())) {
            return "";
        }
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[4096];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) {
                n += r;
            }
            return new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /** A command that starts in the folder the last command finished in, and records the new one. */
    public static String script(File rootfs, String command) {
        return Guest.wrap(read(rootfs), command);
    }
}
