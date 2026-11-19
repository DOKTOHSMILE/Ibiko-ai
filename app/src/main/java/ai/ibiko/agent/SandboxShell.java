package ai.ibiko.agent;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Ibiko's commands: one shell command at a time, run by the phone's own shell (sh with toybox) inside Ibiko's
 * project folder, as the app (so only the app's own files can be touched), with a time limit and the output cut
 * short. Pure Java.
 */
public final class SandboxShell {
    private SandboxShell() {
    }

    /** The command's output and exit code, or why it didn't run. */
    public static String run(String command, File dir, long timeoutMs, int maxChars) {
        dir.mkdirs();
        String sh = new File("/system/bin/sh").exists() ? "/system/bin/sh" : "sh";
        // in a process group of its own (setsid), so a timeout stops everything the command started, not only the
        // shell: a server or a watcher it ran in the background kept running, with no one to stop it
        String setsid = setsid();
        ProcessBuilder pb = (setsid == null ? new ProcessBuilder(sh, "-c", command)
                : new ProcessBuilder(setsid, sh, "-c", command)).directory(dir).redirectErrorStream(true);
        pb.environment().put("HOME", dir.getAbsolutePath());
        pb.environment().put("TMPDIR", dir.getAbsolutePath());
        Process p;
        try {
            p = pb.start();
        } catch (java.io.IOException e) {
            return "Couldn't start the shell: " + e.getMessage();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> {
            try (InputStream in = p.getInputStream()) {
                byte[] b = new byte[4096];
                int n;
                while ((n = in.read(b)) > 0) {
                    synchronized (out) {
                        if (out.size() < maxChars * 2) {
                            out.write(b, 0, n);
                        }
                    }
                }
            } catch (java.io.IOException e) {
                // the process ended
            }
        }, "ibiko-shell-output");
        reader.start();
        boolean finished;
        try {
            finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            finished = false;
        }
        if (!finished) {
            stopGroup(p, setsid != null);
            p.destroyForcibly();
        }
        try {
            reader.join(1000);
        } catch (InterruptedException e) {
            // carry on with what was read
        }
        String text;
        synchronized (out) {
            text = new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
        if (text.length() > maxChars) {
            text = text.substring(0, maxChars) + "\n…(output cut short)";
        }
        return (text.isEmpty() ? "(no output)" : text.trim()) + "\n[" + (finished ? "exit " + p.exitValue() : "stopped after " + timeoutMs / 1000 + " s") + "]";
    }

    /** The setsid program on this device, or null. */
    static String setsid() {
        for (String path : new String[]{"/system/bin/setsid", "/usr/bin/setsid", "/bin/setsid"}) {
            if (new File(path).canExecute()) {
                return path;
            }
        }
        return null;
    }

    /** Stops the command and everything it started: the whole process group it leads. */
    static void stopGroup(Process p, boolean ownGroup) {
        long pid = ai.ibiko.linux.ProotSession.pidOf(p);
        if (!ownGroup || pid <= 0) {
            return;
        }
        String kill = new File("/system/bin/kill").canExecute() ? "/system/bin/kill" : "kill";
        try {
            new ProcessBuilder(kill, "-9", "--", "-" + pid).redirectErrorStream(true).start().waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // the shell itself is still stopped below
        }
    }

    /** A web page as readable text: scripts, styles and tags out, whitespace tidied. */
    public static String text(String html) {
        if (html == null) {
            return null;
        }
        return html.replaceAll("(?is)<(script|style|noscript)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|li|h[1-6]|tr)>", "\n")
                .replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n\n").trim();
    }
}
