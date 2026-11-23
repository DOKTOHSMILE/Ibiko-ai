package ai.ibiko.social.campaign;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What each account has put out, across every campaign, so the same post never goes out twice on one account: the
 * same words (links, hashtags, emoji and case aside) are not posted again within {@link #WINDOW_MS}, and the same
 * picture headline is not shown again on that page (the post gets a new one). Kept on the phone, one line a post.
 */
public final class PostedLog {
    /** How long a post counts as recent: the same words aren't posted again on that account within it. */
    static final long WINDOW_MS = 45L * 24 * 60 * 60 * 1000;

    private final File file;
    private final List<String[]> lines = new ArrayList<String[]>(); // account, words key, headline key, time

    public PostedLog(File file) {
        this.file = file;
        try {
            if (file != null && file.isFile()) {
                for (String l : ai.ibiko.learn.CodeFile.lines(file)) {
                    String[] p = l.split("\t", -1);
                    if (p.length == 4) {
                        lines.add(p);
                    }
                }
            }
        } catch (IOException ignored) {
            // an unreadable log starts empty
        }
    }

    /** The words as they are compared: lower case, no links, hashtags, mentions, emoji or punctuation. */
    static String key(String text) {
        return (text == null ? "" : text).toLowerCase(Locale.ROOT).replaceAll("https?://\\S+|www\\.\\S+|[#@]\\w+", " ")
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }

    /** When this account last put out these words (within the window), or 0. */
    public synchronized long sameWords(String account, String text, long now) {
        return find(account, 1, key(text), now);
    }

    /** When this account last showed this headline on a picture (within the window), or 0. */
    public synchronized long sameHeadline(String account, String headline, long now) {
        String k = key(headline);
        return k.isEmpty() ? 0 : find(account, 2, k, now);
    }

    private long find(String account, int field, String k, long now) {
        if (k.isEmpty()) {
            return 0;
        }
        for (int i = lines.size() - 1; i >= 0; i--) {
            String[] p = lines.get(i);
            long at = parse(p[3]);
            if (p[0].equals(account) && p[field].equals(k) && now - at < WINDOW_MS) {
                return at;
            }
        }
        return 0;
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Keeps that this account put out this post. */
    public synchronized void add(String account, String text, String headline, long now) {
        lines.add(new String[]{account == null ? "" : account, key(text), key(headline), Long.toString(now)});
        while (lines.size() > 2000) {
            lines.remove(0);
        }
        if (file == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (String[] p : lines) {
            b.append(String.join("\t", p)).append('\n');
        }
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            ai.ibiko.learn.CodeFile.write(file, b.toString());
        } catch (IOException ignored) {
            // kept for this run
        }
    }
}
