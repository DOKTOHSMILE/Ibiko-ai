package ai.ibiko.social.campaign;

import ai.ibiko.brain.MiniJson;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How people responded to each campaign post, read from the account's notifications ("Ada and 4 others reacted to
 * your post: “Fresh cakes…”"): reactions, and comments, replies and shares (worth more: someone spoke). Each
 * notification is counted once, its later growth ("and 6 others") adding only the difference. From these: a
 * campaign's summary, and lessons for the next plan (which themes and times did best). Kept in its own file beside
 * the campaigns ({@code results.json}), so counting never races a post being saved. Thread-safe; pure Java.
 */
public final class Results {
    /** One post's counts. */
    public static final class Count {
        public int reactions, talk;
        /** Each notification counted: its key and the number it showed. */
        final Map<String, Integer> seen = new LinkedHashMap<String, Integer>();

        /** Reactions, with a comment or share counted as three (someone took the time). */
        public int score() {
            return reactions + 3 * talk;
        }
    }

    /** Notifications remembered for each post, the oldest forgotten first. */
    static final int MOST_SEEN = 200;

    private static final Pattern REACT = Pattern.compile("(?i)\\b(liked|likes|reacted|loved|❤|favorited)\\b");
    private static final Pattern TALK = Pattern.compile("(?i)\\b(comment(?:ed)?|repl(?:ied|y)|shared|reposted|retweeted|quoted|mentioned)\\b");
    private static final Pattern OTHERS = Pattern.compile("(?i)\\band (\\d{1,6}) others?\\b");

    private final File file;
    private final Map<String, Count> counts = new LinkedHashMap<String, Count>();
    private final Map<String, Long> summarized = new LinkedHashMap<String, Long>();

    public Results(File dir) {
        this.file = new File(dir, "results.json");
        load();
    }

    private void load() {
        if (!file.isFile()) {
            return;
        }
        try {
            Map<String, Object> root = MiniJson.obj(MiniJson.parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)));
            for (Map.Entry<String, Object> e : MiniJson.obj(root.get("posts")).entrySet()) {
                Map<String, Object> m = MiniJson.obj(e.getValue());
                Count c = new Count();
                c.reactions = (int) MiniJson.num(m.get("reactions"), 0);
                c.talk = (int) MiniJson.num(m.get("talk"), 0);
                for (Map.Entry<String, Object> s : MiniJson.obj(m.get("seen")).entrySet()) {
                    c.seen.put(s.getKey(), (int) MiniJson.num(s.getValue(), 1));
                }
                counts.put(e.getKey(), c);
            }
            for (Map.Entry<String, Object> e : MiniJson.obj(root.get("summarized")).entrySet()) {
                summarized.put(e.getKey(), (long) MiniJson.num(e.getValue(), 0));
            }
        } catch (IOException | RuntimeException e) {
            counts.clear();
            file.renameTo(new File(file.getPath() + ".unreadable"));
        }
    }

    public synchronized Count of(String itemId) {
        Count c = counts.get(itemId);
        Count out = new Count();
        if (c != null) {
            out.reactions = c.reactions;
            out.talk = c.talk;
        }
        return out;
    }

    /** A post's words as the notifications quote them: lower case, letters and digits, single spaces. */
    static String plain(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /**
     * Counts what an account's notifications page says about {@code posted} (that account's posts); true when a count
     * changed. Each notification is matched to a post by the post's opening words.
     */
    public synchronized boolean read(String page, List<Campaign.Item> posted) {
        if (page == null || posted.isEmpty()) {
            return false;
        }
        List<String> blocks = new ArrayList<String>();
        StringBuilder b = new StringBuilder();
        for (String line : page.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.regionMatches(true, 0, "LINK ", 0, 5)) {
                if (b.length() > 0) {
                    blocks.add(b.toString());
                    b.setLength(0);
                }
                continue;
            }
            b.append(l).append(' ');
        }
        if (b.length() > 0) {
            blocks.add(b.toString());
        }
        boolean changed = false;
        for (String block : blocks) {
            boolean react = REACT.matcher(block).find(), talk = TALK.matcher(block).find();
            if (!react && !talk) {
                continue;
            }
            String said = plain(block);
            Campaign.Item hit = null;
            for (Campaign.Item it : posted) {
                String opening = opening(it.text);
                if (opening.length() >= 16 && said.contains(opening)) {
                    hit = it;
                    break;
                }
            }
            if (hit == null) {
                continue;
            }
            Matcher others = OTHERS.matcher(block);
            int people = 1 + (others.find() ? Integer.parseInt(others.group(1)) : 0);
            // the same notification grows ("and 4 others" → "and 6 others"): its key leaves the number out
            String key = Integer.toHexString(said.replaceAll("\\d+", "#").hashCode());
            Count c = counts.computeIfAbsent(hit.id, k -> new Count());
            int before = c.seen.getOrDefault(key, 0);
            if (people <= before) {
                continue;
            }
            int more = people - before;
            c.seen.remove(key);
            c.seen.put(key, people);
            while (c.seen.size() > MOST_SEEN) {
                c.seen.remove(c.seen.keySet().iterator().next());
            }
            if (talk && !react) {
                c.talk += more;
            } else {
                c.reactions += more;
            }
            changed = true;
        }
        return changed;
    }

    /** The post's first words as a notification would quote them (about 30 characters). */
    static String opening(String text) {
        String p = plain(text);
        if (p.length() <= 30) {
            return p;
        }
        int cut = p.lastIndexOf(' ', 30);
        return p.substring(0, cut > 16 ? cut : 30);
    }

    /** When a campaign's weekly summary was last given (0: never). */
    public synchronized long summarizedAt(String campaign) {
        return summarized.getOrDefault(campaign, 0L);
    }

    public synchronized void summarized(String campaign, long at) {
        summarized.put(campaign, at);
    }

    public synchronized void save() throws IOException {
        Map<String, Object> posts = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Count> e : counts.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("reactions", (double) e.getValue().reactions);
            m.put("talk", (double) e.getValue().talk);
            Map<String, Object> seen = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, Integer> s : e.getValue().seen.entrySet()) {
                seen.put(s.getKey(), (double) s.getValue());
            }
            m.put("seen", seen);
            posts.put(e.getKey(), m);
        }
        Map<String, Object> sum = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Long> e : summarized.entrySet()) {
            sum.put(e.getKey(), (double) e.getValue());
        }
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", 1.0);
        root.put("posts", posts);
        root.put("summarized", sum);
        ai.ibiko.learn.SafeFile.write(file, MiniJson.write(root));
    }

    /** Forgets a deleted campaign's counts. */
    public synchronized void forget(Campaign c) {
        for (Campaign.Item it : c.items) {
            counts.remove(it.id);
        }
        summarized.remove(c.id);
    }

    /** The part of the day a post went out in, by the hour on the phone's clock. */
    static String partOfDay(long at, java.util.TimeZone zone) {
        java.util.Calendar cal = java.util.Calendar.getInstance(zone);
        cal.setTimeInMillis(at);
        int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
        return h >= 5 && h < 12 ? "mornings (5–12)" : h < 17 && h >= 12 ? "afternoons (12–17)" : h >= 17 && h < 22 ? "evenings (17–22)" : "late nights (22–5)";
    }

    /** The best and worst of a set of averages, as words; "" when there aren't two to compare. */
    private static String compare(Map<String, int[]> groups, String kind) {
        String best = null, worst = null;
        double bestAvg = -1, worstAvg = Double.MAX_VALUE;
        for (Map.Entry<String, int[]> e : groups.entrySet()) {
            if (e.getValue()[1] < 2) {
                continue; // one post says little
            }
            double avg = e.getValue()[0] / (double) e.getValue()[1];
            if (avg > bestAvg) {
                bestAvg = avg;
                best = e.getKey();
            }
            if (avg < worstAvg) {
                worstAvg = avg;
                worst = e.getKey();
            }
        }
        if (best == null || best.equals(worst) || bestAvg <= worstAvg) {
            return "";
        }
        return kind + " “" + best + "” did best (" + String.format(Locale.US, "%.1f", bestAvg) + " a post), “" + worst
                + "” least (" + String.format(Locale.US, "%.1f", worstAvg) + ").";
    }

    /** A campaign's results in a few lines: totals, the best post, and which themes and times worked. */
    public synchronized String summary(Campaign c, java.util.TimeZone zone) {
        int posted = 0, reactions = 0, talk = 0;
        Campaign.Item best = null;
        int bestScore = -1;
        Map<String, int[]> themes = new LinkedHashMap<String, int[]>(), times = new LinkedHashMap<String, int[]>();
        for (Campaign.Item it : c.items) {
            if (it.state != Campaign.State.POSTED || it.handoff) {
                continue;
            }
            posted++;
            Count n = counts.get(it.id);
            int score = n == null ? 0 : n.score();
            reactions += n == null ? 0 : n.reactions;
            talk += n == null ? 0 : n.talk;
            if (score > bestScore) {
                bestScore = score;
                best = it;
            }
            add(themes, it.theme.isEmpty() ? "other" : it.theme, score);
            add(times, partOfDay(it.at, zone), score);
        }
        if (posted == 0) {
            return "No posts have gone out yet.";
        }
        StringBuilder s = new StringBuilder().append(posted).append(posted == 1 ? " post" : " posts").append(" out: ")
                .append(reactions).append(reactions == 1 ? " reaction, " : " reactions, ").append(talk)
                .append(talk == 1 ? " comment or share." : " comments or shares.");
        if (best != null && bestScore > 0) {
            String t = best.text.replaceAll("\\s+", " ").trim();
            s.append("\nBest post: “").append(t.length() > 80 ? t.substring(0, 80) + "…" : t).append("”");
        }
        String th = compare(themes, "Theme"), ti = compare(times, "Posting in");
        if (!th.isEmpty()) {
            s.append('\n').append(th);
        }
        if (!ti.isEmpty()) {
            s.append('\n').append(ti);
        }
        return s.toString();
    }

    private static void add(Map<String, int[]> groups, String key, int score) {
        int[] g = groups.computeIfAbsent(key, k -> new int[2]);
        g[0] += score;
        g[1]++;
    }

    /** The ways a post can be told apart, to see which kind of content draws the most response. */
    static final String[] KINDS = {"Theme", "Network", "Posting in", "Day", "Length", "Picture", "Asks a question",
        "Hashtags", "Emoji", "Link"};

    /** Which group a post falls in, for each of {@link #KINDS}. */
    static String[] traits(Campaign.Item it, java.util.TimeZone zone) {
        java.util.Calendar cal = java.util.Calendar.getInstance(zone);
        cal.setTimeInMillis(it.at);
        String words = it.text.replaceAll("https?://\\S+", "").trim();
        int len = words.length();
        int tags = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("#\\w+").matcher(it.text);
        while (m.find()) {
            tags++;
        }
        boolean emoji = it.text.codePoints().anyMatch(cp -> Character.getType(cp) == Character.OTHER_SYMBOL || cp >= 0x1F300);
        return new String[]{
            it.theme.isEmpty() ? "other" : it.theme,
            it.network.isEmpty() ? "other" : it.network,
            partOfDay(it.at, zone),
            new java.text.SimpleDateFormat("EEEE", Locale.US).format(cal.getTime()),
            len < 100 ? "short (under 100 characters)" : len < 220 ? "medium (100–220)" : "long (over 220)",
            !it.product.isEmpty() ? "a product photo" : it.designed ? "a designed picture" : it.imageHtml.isEmpty() && it.imagePath.isEmpty()
                    ? "no picture" : "the formula's picture",
            words.contains("?") ? "yes" : "no",
            tags == 0 ? "none" : tags <= 2 ? "1–2" : "3 or more",
            emoji ? "with emoji" : "without",
            it.text.matches("(?s).*https?://.*") ? "with a link" : "without a link"};
    }

    /**
     * Every campaign's response, put together: posts out, reactions and comments or shares, the best posts, and for
     * each kind of difference (theme, network, time, day, length, picture, a question, hashtags, emoji, a link) which
     * did best and least. Counts are from the accounts' notifications (reactions, comments, replies, shares); the
     * networks don't show views to Ibiko, so reach isn't counted.
     */
    public synchronized String insights(List<Campaign> all, java.util.TimeZone zone) {
        List<Map<String, int[]>> groups = new java.util.ArrayList<Map<String, int[]>>();
        for (int k = 0; k < KINDS.length; k++) {
            groups.add(new LinkedHashMap<String, int[]>());
        }
        int posts = 0, reactions = 0, talk = 0;
        List<Object[]> scored = new java.util.ArrayList<Object[]>();
        for (Campaign c : all) {
            for (Campaign.Item it : c.items) {
                if (it.state != Campaign.State.POSTED || it.handoff) {
                    continue;
                }
                Count n = counts.get(it.id);
                int score = n == null ? 0 : n.score();
                posts++;
                reactions += n == null ? 0 : n.reactions;
                talk += n == null ? 0 : n.talk;
                scored.add(new Object[]{score, it, c.name});
                String[] t = traits(it, zone);
                for (int k = 0; k < KINDS.length; k++) {
                    add(groups.get(k), t[k], score);
                }
            }
        }
        if (posts == 0) {
            return "No posts have gone out yet. Once they do, Ibiko counts the reactions, comments and shares on each and"
                    + " shows here which kinds of post do best.";
        }
        StringBuilder s = new StringBuilder().append(posts).append(posts == 1 ? " post" : " posts").append(" out · ")
                .append(reactions).append(reactions == 1 ? " reaction · " : " reactions · ").append(talk)
                .append(talk == 1 ? " comment or share" : " comments or shares").append(" · ")
                .append(String.format(Locale.US, "%.1f", (reactions + 3.0 * talk) / posts)).append(" response points a post");
        scored.sort((x, y) -> Integer.compare((Integer) y[0], (Integer) x[0]));
        int shown = 0;
        for (Object[] o : scored) {
            if ((Integer) o[0] <= 0 || shown == 3) {
                break;
            }
            Campaign.Item it = (Campaign.Item) o[1];
            String t = it.text.replaceAll("\\s+", " ").trim();
            s.append(shown == 0 ? "\n\nBest posts:" : "").append("\n• “").append(t.length() > 70 ? t.substring(0, 70) + "…" : t)
                    .append("” (").append(o[2]).append(", ").append(o[0]).append(" points)");
            shown++;
        }
        StringBuilder works = new StringBuilder();
        for (int k = 0; k < KINDS.length; k++) {
            String line = compare(groups.get(k), KINDS[k]);
            if (!line.isEmpty()) {
                works.append("\n• ").append(line);
            }
        }
        s.append(works.length() == 0 ? "\n\nWhat works shows here once a few more posts have had responses."
                : "\n\nWhat works:" + works);
        return s.toString();
    }

    /**
     * What earlier campaigns showed, for the brain planning the next one: of every kind of difference (theme, time,
     * day, network, length, picture, a question, hashtags, emoji, a link), what drew the most response and what the
     * least. "" until there is enough to say (at least 6 posts with responses counted).
     */
    public synchronized String lessons(List<Campaign> earlier, java.util.TimeZone zone) {
        List<Map<String, int[]>> groups = new java.util.ArrayList<Map<String, int[]>>();
        for (int k = 0; k < KINDS.length; k++) {
            groups.add(new LinkedHashMap<String, int[]>());
        }
        int posts = 0, total = 0;
        for (Campaign c : earlier) {
            for (Campaign.Item it : c.items) {
                if (it.state != Campaign.State.POSTED || it.handoff) {
                    continue;
                }
                Count n = counts.get(it.id);
                int score = n == null ? 0 : n.score();
                posts++;
                total += score;
                String[] t = traits(it, zone);
                for (int k = 0; k < KINDS.length; k++) {
                    add(groups.get(k), t[k], score);
                }
            }
        }
        if (posts < 6 || total == 0) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int k = 0; k < KINDS.length; k++) {
            String line = compare(groups.get(k), KINDS[k]);
            if (!line.isEmpty()) {
                out.append(out.length() == 0 ? "" : " ").append(line);
            }
        }
        return out.toString();
    }
}
