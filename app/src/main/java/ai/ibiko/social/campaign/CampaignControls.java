package ai.ibiko.social.campaign;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The campaign's own controls, done on the campaign itself (no model needed, so they work offline and at once):
 * post the next one now, skip it, retry what failed, move the whole schedule, copy the campaign to run again, rename it,
 * and its posts as text to share or keep. Each says what it did, in words for the chat. Pure Java over {@link Campaign};
 * the caller saves and re-arms the runner.
 */
public final class CampaignControls {
    private CampaignControls() {
    }

    /** The next post waiting to go out, or null. */
    public static Campaign.Item next(Campaign c) {
        List<Campaign.Item> p = c.planned();
        return p.isEmpty() ? null : p.get(0);
    }

    /** Brings the next planned post forward to now; what was done, or null when nothing is waiting. */
    public static String postNextNow(Campaign c, long now) {
        Campaign.Item it = next(c);
        if (it == null) {
            return null;
        }
        it.at = now;
        return "The next post (" + network(it) + ": “" + CampaignRunner.firstWords(it.text, 8) + "…”) goes out now"
                + (c.status == Campaign.Status.ACTIVE ? "." : ", as soon as the campaign is running.");
    }

    /** Skips the next planned post (kept, marked skipped); what was done, or null. */
    public static String skipNext(Campaign c) {
        Campaign.Item it = next(c);
        if (it == null) {
            return null;
        }
        it.state = Campaign.State.SKIPPED;
        it.result = "Skipped by you";
        return "Skipped the next post (" + network(it) + ", " + when(it.at) + "). The others keep their times.";
    }

    /** Failed posts, back in line: each a quarter of an hour after the last, from {@code now}. How many. */
    public static int retryFailed(Campaign c, long now) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            if (it.state == Campaign.State.FAILED) {
                it.state = Campaign.State.PLANNED;
                it.result = "";
                it.at = now + (++n) * 15 * 60_000L;
            }
        }
        return n;
    }

    /** How many posts failed. */
    public static int failed(Campaign c) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            n += it.state == Campaign.State.FAILED ? 1 : 0;
        }
        return n;
    }

    /**
     * Moves every post still to go out by {@code by} milliseconds (later, or earlier when negative), never into the
     * past: a post that would land before {@code now} goes out a few minutes from now instead. How many moved.
     */
    public static int shift(Campaign c, long by, long now) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            if (it.state == Campaign.State.PLANNED) {
                it.at = Math.max(now + 5 * 60_000L, it.at + by);
                n++;
            }
        }
        if (c.startAt > 0) {
            c.startAt = Math.max(now, c.startAt + by);
        }
        return n;
    }

    /**
     * A copy to run again: the same brief, accounts, settings and themes, a new name, no posts yet (it is planned fresh,
     * from the lessons the first one taught). Starts as a draft, from {@code now}.
     */
    public static Campaign duplicate(Campaign c, long now) {
        Campaign copy = Campaign.of(c.json());
        copy.id = "c" + now;
        copy.name = (c.name.isEmpty() ? "Campaign" : c.name.replaceFirst("\\s*\\(again(?: \\d+)?\\)$", "")) + " (again)";
        copy.items.clear();
        copy.chat = "";
        copy.status = Campaign.Status.DRAFT;
        copy.createdAt = now;
        copy.startAt = now;
        return copy;
    }

    /** A tidy name, or null when it isn't one. */
    public static String name(String asked) {
        String n = asked == null ? "" : asked.trim().replaceAll("\\s+", " ");
        return n.isEmpty() || n.length() > 60 ? null : n;
    }

    /** Every post with its time, network and state, as text to share or keep. */
    public static String export(Campaign c) {
        StringBuilder b = new StringBuilder(c.name.isEmpty() ? "Campaign" : c.name);
        b.append("\n").append(c.progress()).append("\n");
        List<Campaign.Item> all = new ArrayList<Campaign.Item>(c.items);
        all.sort((x, y) -> Long.compare(x.at, y.at));
        for (Campaign.Item it : all) {
            b.append("\n— ").append(when(it.at)).append(" · ").append(network(it)).append(" · ").append(state(it.state)).append("\n");
            b.append(it.text.trim()).append("\n");
            if (!it.url.isEmpty()) {
                b.append(it.url).append("\n");
            }
        }
        return b.toString().trim();
    }

    /** One line on where the campaign stands, for the Campaign sheet: "3 posted · 11 to go · next today 18:00". */
    public static String standing(Campaign c, long now) {
        Campaign.Item it = next(c);
        String nextAt = it == null ? "" : " · next " + when(it.at);
        return c.progress() + nextAt;
    }

    static String state(Campaign.State s) {
        switch (s) {
            case POSTED: return "posted";
            case FAILED: return "failed";
            case SKIPPED: return "skipped";
            default: return "planned";
        }
    }

    static String network(Campaign.Item it) {
        return CampaignRunner.shown(it.network);
    }

    static String when(long at) {
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(at));
    }
}
