package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The leads and replies found for campaigns, in one file ({@code files/campaigns/leads.json}), saved whole. A post is
 * kept once whatever happens to it (sent, dismissed), so it is never found or answered twice. Thread-safe; pure Java.
 */
public final class LeadStore {
    /** Kept at once; the oldest handled ones go first. */
    public static final int MOST = 600;

    private final File file;
    private final List<Lead> all = new ArrayList<Lead>();

    public LeadStore(File dir) {
        this.file = new File(dir, "leads.json");
        if (file.isFile()) {
            try {
                if (file.length() > 16L * 1024 * 1024) {
                    throw new IOException("too big");
                }
                Object parsed = MiniJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
                for (Object o : MiniJson.list(MiniJson.obj(parsed).get("leads"))) {
                    Lead l = Lead.of(o);
                    if (!l.id.isEmpty() && !l.url.isEmpty()) {
                        all.add(l);
                    }
                }
            } catch (IOException | RuntimeException e) {
                all.clear();
                file.renameTo(new File(file.getPath() + ".unreadable"));
            }
        }
    }

    public synchronized List<Lead> all() {
        return new ArrayList<Lead>(all);
    }

    /** Whether this post was found before (in any state): it is not found or answered again. */
    public synchronized boolean known(String url) {
        String key = key(url);
        for (Lead l : all) {
            if (key(l.url).equals(key)) {
                return true;
            }
        }
        return false;
    }

    /** How many of this kind went out for a campaign since {@code since}. */
    public synchronized int sentSince(String campaign, Lead.Kind kind, long since) {
        int n = 0;
        for (Lead l : all) {
            if (l.campaign.equals(campaign) && l.kind == kind && l.state == Lead.State.SENT && l.sentAt >= since) {
                n++;
            }
        }
        return n;
    }

    public synchronized Lead get(String id) {
        for (Lead l : all) {
            if (l.id.equals(id)) {
                return l;
            }
        }
        return null;
    }

    /** Keeps a lead (new, or changed) and saves. */
    public synchronized void put(Lead lead) throws IOException {
        all.removeIf(o -> o.id.equals(lead.id));
        all.add(lead);
        while (all.size() > MOST) {
            Lead drop = null;
            for (Lead o : all) {
                if (o.state != Lead.State.NEW && o.state != Lead.State.SENT && (drop == null || o.at < drop.at)) {
                    drop = o; // a dismissed or failed one first: a pitched person is followed up on
                }
            }
            for (Lead o : all) {
                if (drop == null && o.state == Lead.State.SENT || drop != null && drop.state == Lead.State.SENT
                        && o.state == Lead.State.SENT && o.at < drop.at) {
                    drop = o;
                }
            }
            if (drop == null) {
                drop = all.get(0);
            }
            all.remove(drop);
        }
        save();
    }

    /** Removes every lead of a deleted campaign. */
    public synchronized void forget(String campaign) throws IOException {
        if (all.removeIf(o -> o.campaign.equals(campaign))) {
            save();
        }
    }

    /** The same post whatever its query string or ending slash ("…/status/1?s=20" is ".../status/1"). */
    static String key(String url) {
        String u = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        int q = u.indexOf('?');
        if (q >= 0 && !u.contains("story.php")) {
            u = u.substring(0, q); // a Facebook story link keeps its query: that is its address
        }
        return u.replaceAll("/+$", "").replaceFirst("^https?://(www\\.|mobile\\.|m\\.)?", "");
    }

    private void save() throws IOException {
        List<Object> list = new ArrayList<Object>();
        for (Lead l : all) {
            list.add(l.json());
        }
        java.util.Map<String, Object> root = new java.util.LinkedHashMap<String, Object>();
        root.put("version", 1.0);
        root.put("leads", list);
        ai.ibiko.learn.SafeFile.write(file, MiniJson.write(root));
    }
}
