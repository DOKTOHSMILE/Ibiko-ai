package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The campaigns on this phone, in one file ({@code files/campaigns/campaigns.json}), saved whole or not at all. A file
 * that can't be read is set aside as {@code .unreadable} rather than overwritten, so no campaign is lost to one bad
 * save. Thread-safe; pure Java.
 */
public final class CampaignStore {
    /** Campaigns kept at once; the oldest finished ones go first. */
    public static final int MOST = 50;

    private final File file;
    private final List<Campaign> all = new ArrayList<Campaign>();

    public CampaignStore(File dir) {
        this.file = new File(dir, "campaigns.json");
        load();
        all.removeIf(c -> deleted(dir, c.id)); // one written back by a late save before this fix: gone for good
    }

    /** The campaigns deleted, kept by id so that nothing still working on one (planning, posting) brings it back. */
    static final String DELETED = "deleted.txt";

    /** Whether the campaign {@code id} was deleted (kept in {@code dir}, the campaigns folder). */
    public static boolean deleted(File dir, String id) {
        File f = new File(dir, DELETED);
        if (id == null || !f.isFile()) {
            return false;
        }
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                if (line.trim().equals(id)) {
                    return true;
                }
            }
        } catch (IOException ignored) {
            // unreadable: treated as not deleted
        }
        return false;
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            if (file.length() > 16L * 1024 * 1024) {
                throw new IOException("too big");
            }
            Object parsed = MiniJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            for (Object o : MiniJson.list(MiniJson.obj(parsed).get("campaigns"))) {
                Campaign c = Campaign.of(o);
                if (!c.id.isEmpty()) {
                    all.add(c);
                }
            }
        } catch (IOException | RuntimeException e) {
            all.clear();
            file.renameTo(new File(file.getPath() + ".unreadable")); // kept for a look, never written over
        }
    }

    public synchronized List<Campaign> all() {
        return new ArrayList<Campaign>(all);
    }

    public synchronized Campaign get(String id) {
        for (Campaign c : all) {
            if (c.id.equals(id)) {
                return c;
            }
        }
        return null;
    }

    /** Keeps a campaign (a new one, or a changed one replacing its old copy) and saves. */
    public synchronized void put(Campaign c) throws IOException {
        if (deleted(file.getParentFile(), c.id)) {
            return; // deleted: a planner or a posting run still holding it never writes it back
        }
        all.removeIf(o -> o.id.equals(c.id));
        all.add(c);
        while (all.size() > MOST) {
            Campaign drop = null;
            for (Campaign o : all) {
                if (o.status == Campaign.Status.DONE && (drop == null || o.createdAt < drop.createdAt)) {
                    drop = o;
                }
            }
            if (drop == null) {
                break; // never a running or paused one
            }
            all.remove(drop);
        }
        save();
    }

    public synchronized void remove(String id) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !deleted(dir, id)) {
            dir.mkdirs();
            Files.write(new File(dir, DELETED).toPath(), (id + "\n").getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        all.removeIf(o -> o.id.equals(id));
        save();
    }

    private void save() throws IOException {
        List<Object> list = new ArrayList<Object>();
        for (Campaign c : all) {
            list.add(c.json());
        }
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<String, Object>();
        root.put("version", 1.0);
        root.put("campaigns", list);
        ai.ibiko.learn.SafeFile.write(file, MiniJson.write(root));
    }
}
