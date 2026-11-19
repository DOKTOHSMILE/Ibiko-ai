package ai.ibiko.social.campaign;

import java.util.ArrayList;
import java.util.List;

/**
 * The campaigns dashboard's arithmetic: which campaigns are running, coming up or past, what each one's state is
 * called, its progress and its next post. Pure Java.
 */
public final class CampaignBoard {
    /** Where a campaign sits on the dashboard. */
    public enum Bucket { RUNNING, UPCOMING, PAST }

    private CampaignBoard() {
    }

    /**
     * Running: posting now (or paused part way). Upcoming: being planned, waiting for approval, or approved with its
     * first post still ahead. Past: done.
     */
    public static Bucket bucket(Campaign c, boolean planning, long now) {
        if (planning || c.status == Campaign.Status.DRAFT) {
            return Bucket.UPCOMING;
        }
        if (c.status == Campaign.Status.DONE) {
            return Bucket.PAST;
        }
        if (c.status == Campaign.Status.ACTIVE && posted(c) == 0 && failed(c) == 0) {
            Campaign.Item next = next(c);
            if (next != null && next.at > now) {
                return Bucket.UPCOMING; // approved, its first post still ahead
            }
        }
        return Bucket.RUNNING;
    }

    /** The state in a word or two, for its chip. */
    public static String state(Campaign c, boolean planning, long now) {
        if (planning) {
            return "Planning";
        }
        switch (c.status) {
            case DRAFT:
                return c.items.isEmpty() ? "Planning stopped" : "Ready to review";
            case PAUSED:
                return "Paused";
            case DONE:
                return "Done";
            default:
                return bucket(c, false, now) == Bucket.UPCOMING ? "Scheduled" : "Running";
        }
    }

    public static int posted(Campaign c) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            n += it.state == Campaign.State.POSTED ? 1 : 0;
        }
        return n;
    }

    public static int failed(Campaign c) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            n += it.state == Campaign.State.FAILED ? 1 : 0;
        }
        return n;
    }

    /** The next post still to go out, or null. */
    public static Campaign.Item next(Campaign c) {
        Campaign.Item best = null;
        for (Campaign.Item it : c.items) {
            if (it.state == Campaign.State.PLANNED && (best == null || it.at < best.at)) {
                best = it;
            }
        }
        return best;
    }

    /** When the campaign's posts start and end (first and last post), or {0, 0} with none. */
    public static long[] span(Campaign c) {
        long first = Long.MAX_VALUE, last = 0;
        for (Campaign.Item it : c.items) {
            first = Math.min(first, it.at);
            last = Math.max(last, it.at);
        }
        return c.items.isEmpty() ? new long[]{0, 0} : new long[]{first, last};
    }

    /** The networks a campaign posts on, in the order first used ("Facebook", "X", "Instagram"). */
    public static List<String> networks(Campaign c) {
        List<String> out = new ArrayList<String>();
        for (Campaign.Item it : c.items) {
            String n = CampaignRunner.shown(it.network);
            if (!n.isEmpty() && !out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    /** The campaigns in {@code bucket}: running and upcoming soonest first, past newest first. */
    public static List<Campaign> of(List<Campaign> all, Bucket bucket, java.util.Set<String> planning, long now) {
        List<Campaign> out = new ArrayList<Campaign>();
        for (Campaign c : all) {
            if (bucket(c, planning.contains(c.id), now) == bucket) {
                out.add(c);
            }
        }
        if (bucket == Bucket.PAST) {
            out.sort((x, y) -> Long.compare(span(y)[1], span(x)[1]));
        } else {
            out.sort((x, y) -> {
                Campaign.Item a = next(x), b = next(y);
                long ax = a == null ? Long.MAX_VALUE : a.at, bx = b == null ? Long.MAX_VALUE : b.at;
                return ax != bx ? Long.compare(ax, bx) : Long.compare(y.createdAt, x.createdAt);
            });
        }
        return out;
    }

    /** The brief in a few lines: the campaign chat's first message, and its card on the dashboard. */
    public static String brief(Campaign c, ai.ibiko.social.SocialAccounts all) {
        StringBuilder s = new StringBuilder("New social media campaign\n");
        line(s, "Business", c.brief.business);
        line(s, "Promotes", c.brief.offer);
        line(s, "For", c.brief.audience);
        line(s, "Goal", c.brief.goal);
        line(s, "Tone", c.brief.tone);
        line(s, "Link", c.brief.link);
        line(s, "WhatsApp", c.brief.whatsapp);
        if (!c.brief.products.isEmpty()) {
            java.util.List<String> ps = new ArrayList<String>();
            for (Product p : c.brief.products) {
                ps.add(p.name + (p.price.isEmpty() ? "" : " (" + p.price + ")"));
            }
            line(s, "Products", String.join(", ", ps));
        }
        java.util.List<String> from = new ArrayList<String>();
        for (String id : c.accounts) {
            ai.ibiko.social.SocialAccounts.Account acc = all == null ? null : all.get(id);
            if (acc != null) {
                from.add(acc.label + " (" + acc.platform.shown + ")");
            }
        }
        for (String n : c.handoff) {
            from.add(CampaignRunner.shown(n) + " (you share)");
        }
        line(s, "Posts from", String.join(", ", from));
        line(s, "Runs", c.days + (c.days == 1 ? " day, " : " days, ") + c.perDay + (c.perDay == 1 ? " post" : " posts")
                + " a day on each account");
        java.util.List<String> also = new ArrayList<String>();
        if (c.engage) {
            also.add("answer comments");
        }
        if (c.prospect) {
            also.add(c.autoPitch ? "find people who need it and pitch them (up to " + LeadSender.MOST_AUTO_PITCHES
                    + " a day)" : "find people who need it (pitches wait for you)");
        }
        if (c.whatsappShare) {
            also.add("offer each post for WhatsApp Status");
        }
        line(s, "Also", String.join(" · ", also));
        return s.toString().trim();
    }

    private static void line(StringBuilder s, String name, String v) {
        if (v != null && !v.trim().isEmpty()) {
            s.append('\n').append(name).append(": ").append(v.trim().replaceAll("\\s*\\n\\s*", " "));
        }
    }

}
