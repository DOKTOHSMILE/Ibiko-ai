package ai.ibiko.agent;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * The readers under one worker. They are not named by the model: each worker gets them, and they read the files
 * its part is about, several at once, before the worker starts. Pure Java.
 */
final class Readers {
    /** The most files one worker's readers open, and the most lines of each that are handed up. */
    static final int MOST = 8, LINES = 120, CHARS = 6000;

    private Readers() {
    }

    /** What the readers brought back: how many files, and the lines, clipped. */
    static final class Read {
        final int files;
        final String text;

        Read(int files, String text) {
            this.files = files;
            this.text = text;
        }
    }

    /** The files this part is about, read at once. Empty when the folder has nothing about the part. */
    static Read excerpts(File folder, String part, int atOnce) {
        if (folder == null || !folder.isDirectory() || part == null || part.trim().isEmpty()) {
            return new Read(0, "");
        }
        List<File> files = new ArrayList<File>();
        for (File f : Survey.pick(folder, part)) {
            if (files.size() >= MOST) {
                break;
            }
            if (f.isFile()) {
                files.add(f);
            }
        }
        if (files.isEmpty()) {
            return new Read(0, "");
        }
        int n = Math.max(1, Math.min(atOnce, files.size()));
        ExecutorService pool = Executors.newFixedThreadPool(n, r -> {
            Thread t = new Thread(r, "ibiko-reader");
            t.setDaemon(true);
            return t;
        });
        List<Future<String>> jobs = new ArrayList<Future<String>>();
        for (File f : files) {
            jobs.add(pool.submit(() -> one(folder, f)));
        }
        pool.shutdown();
        StringBuilder b = new StringBuilder();
        int got = 0;
        for (Future<String> j : jobs) {
            try {
                String piece = j.get(30, TimeUnit.SECONDS);
                if (piece == null || piece.isEmpty() || b.length() >= CHARS) {
                    continue;
                }
                if (b.length() + piece.length() > CHARS) {
                    piece = piece.substring(0, Math.max(0, CHARS - b.length()));
                }
                b.append(piece).append('\n');
                got++;
            } catch (Exception ignored) {
                // one file unreadable: the others still come back
            }
        }
        pool.shutdownNow();
        if (got == 0) {
            return new Read(0, "");
        }
        return new Read(got, "\n\nRead by your readers, " + got + " file" + (got == 1 ? "" : "s") + " at once. Use these lines;"
                + " do not read the same file again.\n" + b);
    }

    private static String one(File root, File f) {
        try {
            String rel = root.toPath().relativize(f.toPath()).toString();
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            int n = Math.min(LINES, lines.size());
            StringBuilder b = new StringBuilder();
            b.append("\n--- ").append(rel).append(" (").append(lines.size()).append(" lines) ---\n");
            for (int i = 0; i < n; i++) {
                b.append(i + 1).append("| ").append(lines.get(i)).append('\n');
            }
            if (lines.size() > n) {
                b.append("… ").append(lines.size() - n).append(" more lines\n");
            }
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
