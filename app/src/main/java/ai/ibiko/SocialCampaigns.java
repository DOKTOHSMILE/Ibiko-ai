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

    /** A brief waiting from the Social accounts screen: planned now. */
    void takeDraft() {
        final Campaign c = CampaignDraft.take();
        if (c == null) {
            return;
        }
        if (a.model == null || !(a.model.ready() || a.model.remoteOn())) {
            a.note("Planning a campaign needs a brain: choose an API model or a downloaded one, then try again.");
            return;
        }
        final List<CampaignPlanner.Seat> seats = seats(c);
        final int id = a.nextAsk();
        a.armThink("Planning the campaign", "Planning the campaign");
        a.activity("Planning the campaign “" + clip(c.brief.offer.isEmpty() ? c.brief.business : c.brief.offer) + "”");
        a.hub.worker.execute(() -> {
            // on an API the batches run side by side, without the phone model's lock; the phone's model takes one at a time
            final boolean api = a.model.remoteOn();
            CampaignPlanner.Brain brain = (system, prompt, tokens) -> {
                String got = api ? a.model.remoteTask(system, prompt, tokens) : null;
                return got != null ? got : a.model.task(system, prompt, tokens, null);
            };
            String why = CampaignPlanner.build(c, seats, brain, line -> a.handler.post(() -> {
                        if (a.alive(id)) {
                            a.activity(line);
                        }
                    }), () -> !a.alive(id), java.util.TimeZone.getDefault(), api ? CampaignPlanner.WORKERS : 1);
            a.handler.post(() -> {
                a.endThink();
                if (why != null) {
                    a.note("The campaign wasn't planned: " + why);
                    return;
                }
                review(c);
            });
        });
    }

    /** The chosen accounts, as the planner sees them (only signed-in accounts on networks Ibiko posts to itself). */
    private List<CampaignPlanner.Seat> seats(Campaign c) {
        List<CampaignPlanner.Seat> out = new ArrayList<CampaignPlanner.Seat>();
        ai.ibiko.social.SocialAccounts all = ai.ibiko.social.SocialWeb.accounts(a);
        for (String id : c.accounts) {
            ai.ibiko.social.SocialAccounts.Account acc = all.get(id);
            if (acc != null && acc.signedIn && (acc.platform == ai.ibiko.social.Platform.X || acc.platform == ai.ibiko.social.Platform.FACEBOOK)) {
                out.add(new CampaignPlanner.Seat(acc.id, acc.platform == ai.ibiko.social.Platform.X ? "x" : "facebook", acc.label));
            }
        }
        return out;
    }

    /** The whole plan, to read before anything runs: Approve and start, or Discard. */
    void review(final Campaign c) {
        android.widget.LinearLayout list = new android.widget.LinearLayout(a);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = a.dp(16);
        list.setPadding(pad, 0, pad, pad);
        java.text.DateFormat when = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        android.widget.TextView about = new android.widget.TextView(a);
        about.setText(c.items.size() + " posts over " + c.days + " days"
                + (c.themes.isEmpty() ? "" : "\nThemes: " + String.join(" · ", c.themes))
                + (c.searches.isEmpty() || !c.prospect ? "" : "\nFinds people searching: " + String.join(" · ", c.searches))
                + "\n\nNothing is posted until you approve. Posts go out by themselves at these times, from these accounts,"
                + " within each account's daily limits.");
        about.setTextColor(a.getColor(R.color.text_secondary));
        about.setTextSize(13.5f);
        about.setPadding(0, 0, 0, a.dp(8));
        list.addView(about);
        ai.ibiko.social.SocialAccounts accounts = ai.ibiko.social.SocialWeb.accounts(a);
        for (final Campaign.Item it : c.items) {
            ai.ibiko.social.SocialAccounts.Account acc = accounts.get(it.account);
            String head = when.format(new java.util.Date(it.at)) + " · " + (acc == null ? it.network : acc.label + " (" + acc.platform.shown + ")");
            android.view.View row = a.modelMenu.brainLink(head, it.text + (it.imageHtml.isEmpty() ? "" : "\nTap to see its picture"), null);
            if (!it.imageHtml.isEmpty()) {
                row.setOnClickListener(v -> a.startActivity(new android.content.Intent(a, ai.ibiko.chat.ui.HtmlViewActivity.class)
                        .putExtra(ai.ibiko.chat.ui.HtmlViewActivity.EXTRA_HTML, it.imageHtml)));
            }
            list.addView(row);
        }
        final android.app.Dialog[] sheet = {null};
        android.widget.TextView start = new android.widget.TextView(a);
        start.setText("Approve and start");
        start.setTextColor(a.getColor(R.color.on_accent));
        start.setBackgroundResource(R.drawable.bg_send_button);
        start.setGravity(android.view.Gravity.CENTER);
        start.setTextSize(16f);
        start.setMinHeight(a.dp(48));
        start.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            a.note("Starting “" + c.name + "”: drawing its pictures…");
            new Thread(() -> {
                String why = CampaignRunner.start(a.getApplicationContext(), c);
                a.handler.post(() -> a.note(why == null ? "“" + c.name + "” is running: " + c.progress()
                        + ". See it under Social accounts › Campaigns." : why));
            }, "ibiko-campaign-start").start();
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = a.dp(12);
        list.addView(start, lp);
        android.widget.TextView discard = new android.widget.TextView(a);
        discard.setText("Discard");
        discard.setTextColor(a.getColor(R.color.text_secondary));
        discard.setGravity(android.view.Gravity.CENTER);
        discard.setMinHeight(a.dp(48));
        discard.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            a.note("Campaign discarded. Nothing was posted.");
        });
        list.addView(discard);
        android.widget.ScrollView scroll = new android.widget.ScrollView(a);
        scroll.addView(list);
        sheet[0] = ai.ibiko.chat.ui.BottomSheet.showView(a, c.name.isEmpty() ? "Your campaign" : c.name, scroll);
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
            if (c.status == Campaign.Status.ACTIVE && (c.engage || c.prospect)) {
                due.add(c);
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
        List<CampaignPlanner.Seat> seats = seats(c);
        if (seats.isEmpty()) {
            return 0;
        }
        ai.ibiko.social.campaign.LeadStore store = ai.ibiko.social.campaign.LeadSender.store(ctx);
        ai.ibiko.social.campaign.Prospector.Reader reader = reader(ctx);
        CampaignPlanner.Brain brain = (system, prompt, tokens) -> a.model.task(system, prompt, tokens, null);
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
        };
    }

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() <= 50 ? t : t.substring(0, 50) + "…";
    }
}
