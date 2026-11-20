package ai.ibiko;

import java.io.File;

/**
 * A knowledge file as plain text, for a test that reads it directly: the app ships it in the Ibiko v1 text code
 * ({@code <name>.ibk}, ai.ibiko.ibiko1.TextCode), so it is decoded once into the build folder.
 */
public final class PlainAsset {
    private PlainAsset() {
    }

    /** The plain file for {@code path} (relative to the assets folder, e.g. "knowledge/world_facts.tsv"). */
    public static synchronized File of(String path) {
        File assets = new File("src/main/assets");
        if (!assets.isDirectory()) {
            assets = new File("app/src/main/assets");
        }
        File plain = new File(assets, path);
        if (plain.isFile()) {
            return plain;
        }
        File coded = new File(assets, ai.ibiko.ibiko1.TextCode.coded(path));
        File out = new File(assets.getParentFile().getParentFile().getParentFile(), "build/plain-assets/" + path);
        out.getParentFile().mkdirs();
        // the files shipped as they are (wordrank.bin, read next to the dictionary) go beside the decoded one
        File[] near = coded.getParentFile().listFiles();
        if (near != null) {
            for (File f : near) {
                File to = new File(out.getParentFile(), f.getName());
                if (f.isFile() && !f.getName().endsWith(".ibk") && (!to.isFile() || to.lastModified() < f.lastModified())) {
                    try {
                        java.nio.file.Files.copy(f.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException("can't copy " + f, e);
                    }
                }
            }
        }
        if (out.isFile() && out.lastModified() >= coded.lastModified()) {
            return out;
        }
        try (java.io.InputStream in = new java.io.FileInputStream(coded);
                java.io.OutputStream o = new java.io.FileOutputStream(out)) {
            ai.ibiko.ibiko1.TextCode.decode(in, o);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("can't decode " + coded, e);
        }
        return out;
    }
}
