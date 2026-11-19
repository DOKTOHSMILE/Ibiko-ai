package ai.ibiko.learn;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Writes a file whole or not at all: the bytes go to a part file beside it, are synced to storage, then moved over it.
 * A crash or a power cut mid-save leaves the old file, never half of the new one. One part file per thread, so two
 * saves of the same file at once can't move each other's half-written bytes into place.
 */
public final class SafeFile {
    private SafeFile() {
    }

    public static void write(File f, byte[] bytes) throws IOException {
        if (f.getParentFile() != null) {
            f.getParentFile().mkdirs();
        }
        File part = new File(f.getPath() + "." + Thread.currentThread().getId() + ".part");
        try {
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(part)) {
                out.write(bytes);
                out.getFD().sync();
            }
            try {
                Files.move(part.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(part.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            part.delete();
            throw e;
        }
    }

    public static void write(File f, String text) throws IOException {
        write(f, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
