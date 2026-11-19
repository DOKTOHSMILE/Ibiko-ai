package ai.ibiko;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignPlanner;
import ai.ibiko.social.campaign.CampaignRunner;
import java.util.ArrayList;
import java.util.List;

/**
 * A campaign brief from Social accounts, planned by the brain in use: the whole plan is built (with the steps shown in
 * the chat), then shown for review; only "Approve and start" makes it run. Nothing is posted before that yes.
 */
final class SocialCampaigns {
    private final MainActivity a;

    SocialCampaigns(MainActivity a) {
        this.a = a;
    }

    /**
     * A brief waiting from the Social accounts screen: planned now, on its own thread and apart from every chat (it
     * used to run in whatever chat was open, and clashed with the work there). A notification shows how far it is; the
     * plan is kept as a draft, and when it is ready it opens in a new chat of its own if nothing is going on, or waits
     * in Social accounts › Campaigns.
     */
    void takeDraft() {
        final Campaign c = CampaignDraft.take();
        if (c == null) {
            return;
        }
        if (a.model == null || !(a.model.ready() || a.model.remoteOn())) {
            a.note("Planning a campaign needs a brain: choose an API model or a downloaded one, then try again.");
            return;
        }
        final List<CampaignPlanner.Seat> seats = seats(c, true);
        final android.content.Context ctx = a.getApplicationContext();
        try {
            // what earlier campaigns showed works, for the brain to lean on
            c.brief.lessons = CampaignRunner.results(ctx).lessons(CampaignRunner.store(ctx).all(), java.util.TimeZone.getDefault());
        } catch (RuntimeException ignored) {
            c.brief.lessons = "";
        }
        final String title = clip(c.brief.offer.isEmpty() ? c.brief.business : c.brief.offer);
        c.name = title;
        try {
            CampaignRunner.store(ctx).put(c); // a draft from the start: shown as planning in Social accounts
        } catch (java.io.IOException ignored) {
            // planned all the same; kept when it is done
        }
        CampaignDraft.started(c.id);
        CampaignDraft.at(ctx, c.id, title, "Planning the campaign");
        openChat(c, title);
        new Thread(() -> {
            // on an API the batches run side by side, without the phone model's lock; the phone's model takes one at a time
            final boolean api = a.model.remoteOn();
            CampaignPlanner.Brain brain = background(a.model);
            String why;
            try {
                why = CampaignPlanner.build(c, seats, brain, line -> CampaignDraft.at(ctx, c.id, title, line),
                        () -> CampaignDraft.isStopped(c.id), java.util.TimeZone.getDefault(), api ? CampaignPlanner.WORKERS : 1);
            } catch (RuntimeException e) {
                why = "something went wrong while planning (" + e.getClass().getSimpleName() + ")";
            }
            boolean stopped = CampaignDraft.isStopped(c.id);
            if (why != null || stopped) {
                CampaignDraft.finished(ctx, c.id);
                try {
                    CampaignRunner.store(ctx).remove(c.id);
                } catch (java.io.IOException ignored) {
                    // the list shows what was kept
                }
                CampaignDraft.show(ctx, c.id, stopped ? "Campaign planning stopped" : "The campaign wasn't planned",
                        stopped ? "“" + title + "” was stopped. Nothing was posted." : why, false);
                final String said = stopped ? "Planning stopped. Nothing was posted." : "The campaign wasn't planned: " + why
                        + " Nothing was posted; plan it again from Social accounts › Campaigns.";
                a.handler.post(() -> say(c, said));
                return;
            }
            // every picture drawn and saved now: the plan is whole on the phone, and posting needs nothing made later
            for (int i = 0; i < c.items.size() && !CampaignDraft.isStopped(c.id); i++) {
                Campaign.Item it = c.items.get(i);
                if (!it.imageHtml.isEmpty()) {
                    if (i % 3 == 0) {
                        CampaignDraft.at(ctx, c.id, title, "Drawing pictures " + (i + 1) + " of " + c.items.size());
                    }
                    CampaignRunner.pictureOf(ctx, c, it);
                }
            }
            CampaignDraft.finished(ctx, c.id);
            try {
                CampaignRunner.store(ctx).put(c); // the whole plan, waiting for approval
            } catch (java.io.IOException ignored) {
                // shown now all the same
            }
            CampaignDraft.show(ctx, c.id, "“" + c.name + "” is ready to review", c.items.size() + " posts over "
                    + c.days + " days. Tap to read the plan and approve it. Nothing is posted before you do.", false);
            a.handler.post(() -> ready(c));
        }, "ibiko-campaign-plan").start();
    }

    /**
     * The campaign's own chat, opened as planning starts: the brief as the person's message, so the chat says what it
     * is for and what the plan is made from. It comes on screen when nothing is going on in the open chat; otherwise
     * it waits in the chat list and the open chat says where it is.
     */
    private void openChat(Campaign c, String title) {
        if (a.chats == null) {
            return;
        }
        boolean show = !a.generating && a.backgroundTask == null && a.thinkPace == null;
        String persona = a.chats.current().persona;
        ai.ibiko.chat.ChatBook.Section own = a.chats.openOwn("Campaign: " + title, persona, show);
        c.chat = own.id;
        a.chats.addTo(own.id, "user", "text", brief(c, ai.ibiko.social.SocialWeb.accounts(a)), persona);
        a.chats.addTo(own.id, "assistant", "text", "Planning this campaign now: the strategy, every post with its words and"
                + " picture, and the searches for people who need it. It runs on its own, so you can use other chats"
                + " meanwhile; the plan comes back here for you to review. Nothing is posted until you approve.", persona);
        try {
            CampaignRunner.store(a).put(c);
        } catch (java.io.IOException ignored) {
            // kept when the plan is done
        }
        if (show) {
            if (a.chatPane != null) {
                a.chatPane.show();
            }
            a.selectPersona(own.persona);
            a.closeDrawer();
        } else {
            a.note("Planning “" + title + "” in its own chat, “Campaign: " + title + "”, in your chat list. This chat isn't touched.");
        }
    }

    /** The brief as the person's message in the campaign's chat. */
    static String brief(Campaign c, ai.ibiko.social.SocialAccounts all) {
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
            for (ai.ibiko.social.campaign.Product p : c.brief.products) {
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
            also.add(c.autoPitch ? "find people who need it and pitch them (up to " + ai.ibiko.social.campaign.LeadSender.MOST_AUTO_PITCHES
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

    /** A message from the campaign in its own chat, wherever the person is. */
    private void say(Campaign c, String text) {
        if (a.chats == null || c.chat.isEmpty()) {
            return;
        }
        String persona = a.chats.current().persona;
        a.chats.addTo(c.chat, "assistant", "text", text, persona);
        if (a.chatPane != null && c.chat.equals(a.chats.current().id)) {
            a.chatPane.show();
        }
    }

    /** The plan, back in the campaign's chat; the review opens there when that chat is the one on screen. */
    private void ready(Campaign c) {
        if (a.isFinishing() || a.chats == null) {
            return;
        }
        say(c, "Your campaign “" + c.name + "” is planned: " + c.items.size() + " posts over " + c.days + " days"
                + (c.themes.isEmpty() ? "" : ", themes: " + String.join(", ", c.themes))
                + ". Every post's words and picture are ready and saved on the phone. Review it, edit or take out any post,"
                + " then approve; nothing is posted before you do. It also waits under Social accounts › Campaigns.");
        if (a.inFront && c.chat.equals(a.chats.current().id)) {
            ai.ibiko.chat.ui.CampaignReview.show(a, c, null);
        }
    }

    /**
     * The chosen accounts, as the planner sees them (only signed-in accounts on networks Ibiko posts to itself), and
     * with {@code handoff} the networks the person posts on from the app (Instagram, LinkedIn).
     */
    private List<CampaignPlanner.Seat> seats(Campaign c, boolean handoff) {
        List<CampaignPlanner.Seat> out = new ArrayList<CampaignPlanner.Seat>();
        if (handoff) {
            for (String n : c.handoff) {
                out.add(new CampaignPlanner.Seat(CampaignPlanner.HANDOFF + n, n, CampaignRunner.shown(n) + " (you tap Share)"));
            }
        }
        ai.ibiko.social.SocialAccounts all = ai.ibiko.social.SocialWeb.accounts(a);
        for (String id : c.accounts) {
            ai.ibiko.social.SocialAccounts.Account acc = all.get(id);
            if (acc != null && acc.signedIn && (acc.platform == ai.ibiko.social.Platform.X || acc.platform == ai.ibiko.social.Platform.FACEBOOK)) {
                out.add(new CampaignPlanner.Seat(acc.id, acc.platform == ai.ibiko.social.Platform.X ? "x" : "facebook", acc.label));
            }
        }
        return out;
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
        List<CampaignPlanner.Seat> seats = seats(c, false);
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
        return (system, prompt, tokens) -> ai.ibiko.llm.LocalModel.inBackground(() -> {
            String got = model.remoteOn() ? model.remoteTask(system, prompt, tokens) : null;
            return got != null ? got : model.task(system, prompt, tokens, null);
        });
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
