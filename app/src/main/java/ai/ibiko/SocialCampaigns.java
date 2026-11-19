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
            String why = CampaignPlanner.build(c, seats, (system, prompt, tokens) -> a.model.task(system, prompt, tokens, null),
                    line -> a.handler.post(() -> {
                        if (a.alive(id)) {
                            a.activity(line);
                        }
                    }), () -> !a.alive(id), java.util.TimeZone.getDefault());
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

    private static String clip(String s) {
        String t = s == null ? "" : s.trim();
        return t.length() <= 50 ? t : t.substring(0, 50) + "…";
    }
}
