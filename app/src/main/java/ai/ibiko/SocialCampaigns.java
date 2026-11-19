package ai.ibiko;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignPlanner;
import ai.ibiko.social.campaign.CampaignRunner;
import java.util.ArrayList;
import java.util.List;

/**
 * The running campaigns' work while Ibiko is open: comments on their posts answered, people who need the offer found,
 * responses counted, pitches followed up. Planning and the campaigns' own conversations are in the campaign studio
 * ({@link ai.ibiko.social.campaign.CampaignStudio}), apart from every chat.
 */
final class SocialCampaigns {
    private final MainActivity a;

    SocialCampaigns(MainActivity a) {
        this.a = a;
    }

    /** The brief in a few lines (the campaign studio's first message). */
    static String brief(Campaign c, ai.ibiko.social.SocialAccounts all) {
        return ai.ibiko.social.campaign.CampaignBoard.brief(c, all);
    }

    /** Between two rounds of finding leads and answering comments. */
    static final long WORK_MS = 45 * 60_000L;
    /** Leads one campaign may gather in a round. */
    static final int LEADS_A_ROUND = 12;
    private boolean working;
    /** When the last round began: rounds are at least {@link #MIN_GAP_MS} apart, however often the app is opened. */
    private static long lastRound;
    static final long MIN_GAP_MS = 20 * 60_000L;

    /** The background work starts a little after the app opens, then every {@link #WORK_MS}. */
    void startWork() {
        a.handler.removeCallbacks(work);
        a.handler.postDelayed(work, 2 * 60_000L);
    }

    private final Runnable work = new Runnable() {
        @Override
        public void run() {
            a.handler.removeCallbacks(this);
            a.handler.postDelayed(this, WORK_MS);
            workNow();
        }
    };

    /** One round: for each running campaign, comments answered and leads found (pitches sent only when allowed). */
    void workNow() {
        long now = System.currentTimeMillis();
        if (working || a.isFinishing() || now - lastRound < MIN_GAP_MS) {
            return; // opening the app often does not mean reading the sites often
        }
        final java.util.List<Campaign> due = new ArrayList<Campaign>();
        for (Campaign c : CampaignRunner.store(a).all()) {
            if (c.status == Campaign.Status.ACTIVE) {
                due.add(c); // results are counted for every running campaign; comments and leads when asked
            }
        }
        if (due.isEmpty() || a.model == null || !(a.model.ready() || a.model.remoteOn()) || a.model.working()) {
            return; // nothing to do, no brain, or the brain is busy with the chat: the next round
        }
        working = true;
        lastRound = now;
        final android.content.Context ctx = a.getApplicationContext();
        new Thread(() -> {
            try {
                ai.ibiko.social.campaign.LeadSender.remind(ctx, System.currentTimeMillis());
                int found = 0;
                for (Campaign c : due) {
                    found += round(ctx, c);
                }
                final int n = found;
                if (n > 0) {
                    a.handler.post(() -> ai.ibiko.llm.ReplyNotice.show(ctx, "ibiko", n + (n == 1 ? " new lead is" : " new leads are")
                            + " waiting for you under Social accounts › Leads."));
                }
            } catch (RuntimeException e) {
                // one bad round never stops the campaigns: the next round tries again
            } finally {
                a.handler.post(() -> working = false);
            }
        }, "ibiko-campaign-work").start();
    }

    /** One campaign's round; how many leads now wait for the person. */
    private int round(android.content.Context ctx, Campaign c) {
        List<CampaignPlanner.Seat> seats = ai.ibiko.social.campaign.CampaignStudio.seats(ctx, c, false);
        if (seats.isEmpty()) {
            return 0;
        }
        count(ctx, c, seats);
        ai.ibiko.social.campaign.LeadStore store = ai.ibiko.social.campaign.LeadSender.store(ctx);
        ai.ibiko.social.campaign.Prospector.Reader reader = reader(ctx);
        CampaignPlanner.Brain brain = background(a.model);
        long now = System.currentTimeMillis();
        long day = now - 24 * 3_600_000L;
        int waiting = 0;
        if (c.engage) {
            for (ai.ibiko.social.campaign.Lead l : ai.ibiko.social.campaign.Prospector.engage(c, seats, reader, brain, store, now)) {
                keep(store, l);
                if (store.sentSince(c.id, ai.ibiko.social.campaign.Lead.Kind.REPLY, day) < ai.ibiko.social.campaign.LeadSender.MOST_REPLIES) {
                    ai.ibiko.social.campaign.LeadSender.send(ctx, l); // an answer to someone who spoke to the business
                }
            }
        }
        if (c.prospect && !c.searches.isEmpty()) {
            for (ai.ibiko.social.campaign.Lead l : ai.ibiko.social.campaign.Prospector.find(c, seats, reader, brain, store, now,
                    LEADS_A_ROUND)) {
                keep(store, l);
                if (c.autoPitch && store.sentSince(c.id, ai.ibiko.social.campaign.Lead.Kind.LEAD, day)
                        < ai.ibiko.social.campaign.LeadSender.MOST_AUTO_PITCHES) {
                    ai.ibiko.social.campaign.LeadSender.send(ctx, l);
                } else {
                    waiting++; // waits in Leads for a tap
                }
            }
        }
        return waiting;
    }

    /**
     * The brain for a campaign's own work (planning, comments, leads): the API when one is on, which needs no turn on the
     * phone's model, else the phone's model; either way background work, never shown as the chat working and never
     * holding up a message the person sends.
     */
    static CampaignPlanner.Brain background(final ai.ibiko.llm.LocalModel model) {
        return ai.ibiko.social.campaign.CampaignStudio.background(model);
    }

    /** Days a post is still worth counting responses for (notifications pages show the recent ones). */
    static final long COUNT_DAYS_MS = 14 * 24 * 3_600_000L;

    /**
     * Reads each account's notifications and counts how people responded to the campaign's posts from it; once a week
     * the campaign's results are given in a notification.
     */
    private static void count(android.content.Context ctx, Campaign c, List<CampaignPlanner.Seat> seats) {
        try {
            CampaignRunner.recheck(ctx, c); // posts not yet seen on their network are looked for again
        } catch (RuntimeException ignored) {
            // looked for next round
        }
        ai.ibiko.social.campaign.Results results = CampaignRunner.results(ctx);
        ai.ibiko.social.campaign.Prospector.Reader reader = reader(ctx);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (CampaignPlanner.Seat s : seats) {
            List<Campaign.Item> posted = new ArrayList<Campaign.Item>();
            for (Campaign.Item it : c.items) {
                if (it.state == Campaign.State.POSTED && !it.handoff && s.id.equals(it.account) && now - it.at < COUNT_DAYS_MS) {
                    posted.add(it);
                }
            }
            if (!posted.isEmpty()) {
                changed |= results.read(reader.activity(s.id), posted);
            }
        }
        long last = results.summarizedAt(c.id);
        if (last == 0) {
            results.summarized(c.id, now); // the first week starts now
            changed = true;
        } else if (now - last >= 7 * 24 * 3_600_000L) {
            results.summarized(c.id, now);
            changed = true;
            ai.ibiko.llm.ReplyNotice.show(ctx, "ibiko", "This week for “" + c.name + "”:\n"
                    + results.summary(c, java.util.TimeZone.getDefault()));
        }
        if (changed) {
            try {
                results.save();
            } catch (java.io.IOException ignored) {
                // counted again next round
            }
        }
    }

    private static void keep(ai.ibiko.social.campaign.LeadStore store, ai.ibiko.social.campaign.Lead l) {
        try {
            store.put(l);
        } catch (java.io.IOException ignored) {
            // the next round finds it again
        }
    }

    /** Reads with a campaign's own account: its search, or its notifications (mentions and replies). */
    private static ai.ibiko.social.campaign.Prospector.Reader reader(final android.content.Context ctx) {
        return new ai.ibiko.social.campaign.Prospector.Reader() {
            @Override
            public String search(String account, String query) {
                final ai.ibiko.social.SocialAccounts.Account acc = ai.ibiko.social.SocialWeb.accounts(ctx).get(account);
                return acc == null ? null : ai.ibiko.social.campaign.LeadSender.as(ctx, acc,
                        () -> ai.ibiko.social.SocialWeb.search(ctx, acc.platform, query, 6000), null);
            }

            @Override
            public String notifications(String account) {
                final ai.ibiko.social.SocialAccounts.Account acc = ai.ibiko.social.SocialWeb.accounts(ctx).get(account);
                if (acc == null) {
                    return null;
                }
                final String url = acc.platform == ai.ibiko.social.Platform.X ? "https://x.com/notifications/mentions"
                        : "https://m.facebook.com/notifications.php";
                return ai.ibiko.social.campaign.LeadSender.as(ctx, acc, () -> ai.ibiko.social.SocialWeb.open(ctx, url, 6000), null);
            }

            @Override
            public String activity(String account) {
                final ai.ibiko.social.SocialAccounts.Account acc = ai.ibiko.social.SocialWeb.accounts(ctx).get(account);
                if (acc == null) {
                    return null;
                }
                // every notification (likes and shares too), not only mentions
                final String url = acc.platform == ai.ibiko.social.Platform.X ? "https://x.com/notifications"
                        : "https://m.facebook.com/notifications.php";
                return ai.ibiko.social.campaign.LeadSender.as(ctx, acc, () -> ai.ibiko.social.SocialWeb.open(ctx, url, 8000), null);
            }
        };
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() <= 50 ? t : t.substring(0, 50) + "…";
    }
}
