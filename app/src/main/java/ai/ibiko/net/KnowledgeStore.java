package ai.ibiko.net;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import ai.ibiko.brain.KnowledgePack;

/**
 * The basic-knowledge file. Ships inside the APK (first install), can be
 * replaced by a newer verified pack from the server, and falls back to the
 * bundled one if the downloaded copy is ever unreadable.
 */
public final class KnowledgeStore {
    private static final String ASSET = "knowledge/base_knowledge.json";

    private final Context context;
    private final File current;

    public KnowledgeStore(Context context) {
        this.context = context.getApplicationContext();
        File dir = new File(this.context.getFilesDir(), "knowledge");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("cannot create " + dir);
        }
        current = new File(dir, "base_knowledge.json");
    }

    /** A bundled file's plain bytes: shipped in the Ibiko v1 text code (ai.ibiko.ibiko1.TextCode) and decoded here. */
    private java.io.InputStream asset(String path) {
        return ai.ibiko.ibiko1.TextCode.open(p -> {
            try {
                return context.getAssets().open(p);
            } catch (IOException e) {
                return null;
            }
        }, path);
    }

    /** The core pack alone: small and fast, safe to load on the main thread. */
    public KnowledgePack loadCore() {
        return loadCoreFile();
    }

    /**
     * Core pack merged with the bundled shards (e.g. the English dictionary)
     * and every downloaded shard in files/knowledge/shards. Slow: call it off
     * the main thread.
     */
    public KnowledgePack load() {
        KnowledgePack core = loadCoreFile();
        try {
            String[] bundled = context.getAssets().list("knowledge/shards");
            if (bundled != null) {
                java.util.Arrays.sort(bundled);
                for (String name : bundled) {
                    if (name.endsWith(".json") || name.endsWith(".json.ibk")) {
                        try {
                            String plain = "knowledge/shards/" + name.replaceFirst("\\.ibk$", "");
                            java.io.InputStream in = asset(plain);
                            if (in == null) {
                                continue;
                            }
                            core = core.merge(KnowledgePack.parse(read(in)));
                        } catch (IOException | IllegalArgumentException broken) {
                            // skip a damaged shard
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // no bundled shards
        }
        File[] shards = new File(current.getParentFile(), "shards").listFiles();
        if (shards != null) {
            java.util.Arrays.sort(shards);
            for (File f : shards) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                try {
                    core = core.merge(KnowledgePack.parse(read(new FileInputStream(f))));
                } catch (IOException | IllegalArgumentException broken) {
                    // a damaged shard is skipped; the rest still work
                }
            }
        }
        return core;
    }

    private KnowledgePack loadCoreFile() {
        if (current.exists()) {
            try {
                return KnowledgePack.parse(read(new FileInputStream(current)));
            } catch (IOException | IllegalArgumentException broken) {
                // fall through to the bundled pack
            }
        }
        try {
            java.io.InputStream in = asset(ASSET);
            if (in == null) {
                throw new IOException("no bundled knowledge pack");
            }
            return KnowledgePack.parse(read(in));
        } catch (IOException e) {
            throw new IllegalStateException("bundled knowledge pack missing", e);
        }
    }

    /** Validates then atomically installs a downloaded pack. Returns it, or null if rejected. */
    public KnowledgePack install(String json, int minVersion) {
        KnowledgePack pack;
        try {
            pack = KnowledgePack.parse(json);
        } catch (IllegalArgumentException bad) {
            return null;
        }
        if (pack.version <= minVersion) {
            return null;
        }
        File tmp = new File(current.getParentFile(), "base_knowledge.json.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            return null;
        }
        return tmp.renameTo(current) ? pack : null;
    }

    static String read(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = s.read(chunk)) > 0) {
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
