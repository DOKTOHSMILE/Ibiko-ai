package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One campaign's conversation in the studio: the owner's instructions and the studio's answers and notes, newest last,
 * in its own file ({@code campaigns/chat-<id>.json}) so it never races a post being saved. The posts themselves come
 * from the campaign, not from here. Thread-safe; pure Java.
 */
public final class CampaignChat {
    /** Who said it: the owner, or the studio. */
    public static final String YOU = "you", STUDIO = "studio";
    /** Messages kept; the oldest go first. */
    static final int MOST = 400;

    /** One message. {@code kind}: "text", or "plan" (the plan is ready: its card), or "note" (a small line). */
    public static final class Entry {
        public final long at;
        public final String who, kind, text;

        public Entry(long at, String who, String kind, String text) {
            this.at = at;
            this.who = who;
            this.kind = kind;
            this.text = text;
        }
    }

    private final File file;
    /** Where it is kept and whose it is: a deleted campaign's conversation gets nothing new. */
    private final File dir;
    private final String id;
    private final List<Entry> all = new ArrayList<Entry>();

    public CampaignChat(File dir, String campaignId) {
        this.dir = dir;
        this.id = campaignId;
        this.file = new File(dir, "chat-" + CampaignRunner.safe(campaignId) + ".json");
        if (!file.isFile()) {
            return;
        }
        try {
            for (Object o : MiniJson.list(MiniJson.obj(MiniJson.parse(new String(Files.readAllBytes(file.toPath()),
                    StandardCharsets.UTF_8))).get("messages"))) {
                Map<String, Object> m = MiniJson.obj(o);
                all.add(new Entry((long) MiniJson.num(m.get("at"), 0), MiniJson.str(m.get("who"), STUDIO),
                        MiniJson.str(m.get("kind"), "text"), MiniJson.str(m.get("text"), "")));
            }
        } catch (IOException | RuntimeException e) {
            all.clear();
            file.renameTo(new File(file.getPath() + ".unreadable"));
        }
    }

    public synchronized List<Entry> all() {
        return new ArrayList<Entry>(all);
    }

    public synchronized void add(String who, String kind, String text, long at) {
        if (text == null || text.trim().isEmpty() || CampaignStore.deleted(dir, id)) {
            return; // a deleted campaign's conversation gets nothing new (a late planning message would bring it back)
        }
        all.add(new Entry(at, who, kind, text.trim()));
        while (all.size() > MOST) {
            all.remove(0);
        }
        List<Object> list = new ArrayList<Object>();
        for (Entry e : all) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("at", (double) e.at);
            m.put("who", e.who);
            m.put("kind", e.kind);
            m.put("text", e.text);
            list.add(m);
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("messages", list);
        try {
            ai.ibiko.learn.SafeFile.write(file, MiniJson.write(root));
        } catch (IOException ignored) {
            // kept for this run; written with the next message
        }
    }

    public void delete() {
        file.delete();
    }

    /** The recent conversation, for the brain to follow what was asked before. */
    public synchronized String recent(int most) {
        StringBuilder s = new StringBuilder();
        for (int i = Math.max(0, all.size() - most); i < all.size(); i++) {
            Entry e = all.get(i);
            if ("text".equals(e.kind)) {
                s.append(YOU.equals(e.who) ? "Owner: " : "Studio: ").append(e.text.replace('\n', ' ')).append('\n');
            }
        }
        return s.toString().trim();
    }
}
