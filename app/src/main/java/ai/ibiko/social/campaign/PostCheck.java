package ai.ibiko.social.campaign;

import android.content.Context;
import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialWeb;

/**
 * Checks that a post really is on the network after Ibiko pressed Post: the composer going quiet is not proof, so
 * the post is looked for where the world sees it (the account's own Facebook timeline; an exact-words search on
 * X). Found, not found, or not known (the page couldn't be read). Call it with the posting account in use, off the
 * main thread.
 */
public final class PostCheck {
    /** First look after this long (a new post takes a moment to show), then once more after {@link #AGAIN_MS}. */
    static final long FIRST_MS = 20_000, AGAIN_MS = 40_000;

    private PostCheck() {
    }

    /** The words to look for: the post's opening, without links, tags or mentions (pages show those differently). */
    static String words(String text) {
        String t = (text == null ? "" : text).replaceAll("(?i)\\b(?:https?://|www\\.)\\S+", " ")
                .replaceAll("[#@]\\w+", " ").replaceAll("\\s+", " ").trim();
        String[] w = t.split(" ");
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < w.length && i < 8; i++) {
            s.append(i == 0 ? "" : " ").append(w[i]);
        }
        return s.toString();
    }

    /**
     * Whether a page shows the post: TRUE when its opening words are there, FALSE when the page was read and they
     * aren't, null when the page wasn't really read (a refusal, a sign-in wall, next to nothing).
     */
    static Boolean found(String page, String text, String network) {
        if (page == null) {
            return null;
        }
        String p = page.trim();
        String net = "x".equals(network) ? "X" : "Facebook";
        if (p.length() < 200 || p.startsWith(net + ":") || p.startsWith(net + " is switched off")) {
            return null; // not read: nothing to say either way
        }
        String want = Results.plain(words(text));
        if (want.length() < 12) {
            return null; // too few words to tell this post from any other
        }
        String shown = Results.plain(p);
        if (shown.contains(want)) {
            return Boolean.TRUE;
        }
        // a page may cut a long opening: the first five words are still telling
        String[] w = want.split(" ");
        if (w.length > 5) {
            StringBuilder five = new StringBuilder();
            for (int i = 0; i < 5; i++) {
                five.append(i == 0 ? "" : " ").append(w[i]);
            }
            if (five.length() >= 16 && shown.contains(five.toString())) {
                return Boolean.TRUE;
            }
        }
        return Boolean.FALSE;
    }

    /** Looks once: the account's timeline (Facebook) or an exact-words search (X). */
    static Boolean look(Context c, Platform p, String text) {
        String page = p == Platform.X
                ? SocialWeb.search(c, Platform.X, "\"" + words(text) + "\"", 9000)
                : SocialWeb.open(c, "https://m.facebook.com/me", 12000);
        return found(page, text, p == Platform.X ? "x" : "facebook");
    }

    /** Looks after a short wait, and once more if it wasn't there yet. */
    public static Boolean verify(Context c, Platform p, String text) {
        Boolean seen = null;
        long[] waits = {FIRST_MS, AGAIN_MS};
        for (long w : waits) {
            try {
                Thread.sleep(w);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return seen;
            }
            Boolean now = look(c, p, text);
            if (Boolean.TRUE.equals(now)) {
                return Boolean.TRUE;
            }
            seen = now == null ? seen : now;
        }
        return seen;
    }
}
