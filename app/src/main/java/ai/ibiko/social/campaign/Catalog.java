package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * The business's products, in one file ({@code files/catalog/products.json}), each photo beside it as a JPEG. Saved
 * whole or not at all; a file that can't be read is set aside, never overwritten. Thread-safe; pure Java.
 */
public final class Catalog {
    public static final int MOST = 200;
    /** A photo larger than this isn't put in a picture (it travels in the page as text). */
    static final long MOST_PHOTO = 1_500_000;
    /** In a picture's HTML, a product's photo is written as this and the id, and filled in when the picture is drawn. */
    public static final String PHOTO = "ibiko-photo:";

    private final File dir, file;
    private final List<Product> all = new ArrayList<Product>();

    public Catalog(File dir) {
        this.dir = dir;
        this.file = new File(dir, "products.json");
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            if (file.length() > 4L * 1024 * 1024) {
                throw new IOException("too big");
            }
            Object parsed = MiniJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (Object o : MiniJson.list(MiniJson.obj(parsed).get("products"))) {
                Product p = Product.of(o);
                if (!p.id.isEmpty()) {
                    all.add(p);
                }
            }
        } catch (IOException | RuntimeException e) {
            all.clear();
            file.renameTo(new File(file.getPath() + ".unreadable"));
        }
    }

    public synchronized List<Product> all() {
        List<Product> out = new ArrayList<Product>();
        for (Product p : all) {
            out.add(p.copy());
        }
        return out;
    }

    public synchronized Product get(String id) {
        for (Product p : all) {
            if (p.id.equals(id)) {
                return p.copy();
            }
        }
        return null;
    }

    /** Where a product's photo is kept. */
    public File photoFile(String id) {
        return new File(dir, safe(id) + ".jpg");
    }

    public synchronized void put(Product p) throws IOException {
        if (p.name.trim().isEmpty()) {
            throw new IOException("a product needs a name");
        }
        all.removeIf(o -> o.id.equals(p.id));
        if (all.size() >= MOST) {
            throw new IOException("the catalog is full");
        }
        all.add(p.copy());
        save();
    }

    public synchronized void remove(String id) throws IOException {
        all.removeIf(o -> o.id.equals(id));
        photoFile(id).delete();
        save();
    }

    private void save() throws IOException {
        List<Object> list = new ArrayList<Object>();
        for (Product p : all) {
            list.add(p.json());
        }
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<String, Object>();
        root.put("version", 1.0);
        root.put("products", list);
        ai.ibiko.learn.SafeFile.write(file, MiniJson.write(root));
    }

    static String safe(String s) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "_");
        return t.isEmpty() ? "_" : t;
    }

    /**
     * The picture's HTML with each {@link #PHOTO} id replaced by the photo itself (a data address, so the picture
     * needs nothing from outside); a photo that is gone becomes a blank, so the picture still draws.
     */
    public String inline(String html) {
        if (html == null || !html.contains(PHOTO)) {
            return html;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(PHOTO) + "([A-Za-z0-9_-]+)").matcher(html);
        StringBuffer b = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(dataOf(photoFile(m.group(1)))));
        }
        m.appendTail(b);
        return b.toString();
    }

    /** A transparent pixel: what a missing photo becomes. */
    static final String BLANK = "data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    static String dataOf(File f) {
        try {
            if (f.isFile() && f.length() <= MOST_PHOTO) {
                return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(f.toPath()));
            }
        } catch (IOException ignored) {
            // drawn without it
        }
        return BLANK;
    }
}
