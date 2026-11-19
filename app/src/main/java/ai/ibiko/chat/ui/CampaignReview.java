package ai.ibiko.chat.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignRunner;

/**
 * A planned campaign, to read before anything runs: every post with its time, account and picture, then Approve and
 * start, or Discard. Shown from the chat when a plan is ready, and from Social accounts for a plan waiting there.
 */
public final class CampaignReview {
    private CampaignReview() {
    }

    /** Shows the plan; {@code done} runs after Approve or Discard (to redraw a list). */
    public static void show(final Activity a, final Campaign c, final Runnable done) {
        Ui.init(a);
        LinearLayout list = new LinearLayout(a);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 16);
        list.setPadding(pad, 0, pad, pad);
        java.text.DateFormat when = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        TextView about = Ui.text(a, c.items.size() + " posts over " + c.days + " days"
                + (c.themes.isEmpty() ? "" : "\nThemes: " + String.join(" · ", c.themes))
                + (c.searches.isEmpty() || !c.prospect ? "" : "\nFinds people searching: " + String.join(" · ", c.searches))
                + "\n\nNothing is posted until you approve. Posts go out by themselves at these times, from these accounts,"
                + " within each account's daily limits.", 13, Ui.SUB, false);
        about.setPadding(0, 0, 0, Ui.dp(a, 8));
        list.addView(about);
        SocialAccounts accounts = SocialWeb.accounts(a);
        for (final Campaign.Item it : c.items) {
            SocialAccounts.Account acc = accounts.get(it.account);
            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, Ui.dp(a, 10), 0, Ui.dp(a, 10));
            row.addView(Ui.text(a, when.format(new java.util.Date(it.at)) + " · "
                    + (acc == null ? it.network : acc.label + " (" + acc.platform.shown + ")"), 13, Ui.SUB, false));
            row.addView(Ui.text(a, it.text, 14, Ui.TEXT, false), Ui.wide(a, 2));
            if (!it.imageHtml.isEmpty()) {
                row.addView(Ui.text(a, "Tap to see its picture", 13, Ui.ACCENT, false), Ui.wide(a, 4));
                Ui.press(row);
                row.setOnClickListener(v -> a.startActivity(new Intent(a, HtmlViewActivity.class)
                        .putExtra(HtmlViewActivity.EXTRA_HTML, it.imageHtml)));
            }
            list.addView(row);
        }
        final android.app.Dialog[] sheet = {null};
        TextView start = Ui.text(a, "Approve and start", 16, Ui.ON_CORAL, true);
        start.setBackground(Ui.round(a, Ui.CORAL, 0, 24));
        start.setGravity(Gravity.CENTER);
        start.setMinHeight(Ui.dp(a, 48));
        start.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            android.widget.Toast.makeText(a, "Starting “" + c.name + "”: drawing its pictures…", android.widget.Toast.LENGTH_LONG).show();
            final android.content.Context app = a.getApplicationContext();
            new Thread(() -> {
                String why = CampaignRunner.start(app, c);
                a.runOnUiThread(() -> {
                    android.widget.Toast.makeText(a, why == null ? "“" + c.name + "” is running: " + c.progress() : why,
                            android.widget.Toast.LENGTH_LONG).show();
                    if (done != null) {
                        done.run();
                    }
                });
            }, "ibiko-campaign-start").start();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = Ui.dp(a, 12);
        list.addView(start, lp);
        TextView discard = Ui.text(a, "Discard", 15, Ui.SUB, false);
        discard.setGravity(Gravity.CENTER);
        discard.setMinHeight(Ui.dp(a, 48));
        discard.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            try {
                CampaignRunner.store(a).remove(c.id);
            } catch (java.io.IOException ignored) {
                // the list shows what was kept
            }
            android.widget.Toast.makeText(a, "Campaign discarded. Nothing was posted.", android.widget.Toast.LENGTH_SHORT).show();
            if (done != null) {
                done.run();
            }
        });
        list.addView(discard);
        ScrollView scroll = new ScrollView(a);
        scroll.addView(list);
        sheet[0] = BottomSheet.showView(a, c.name.isEmpty() ? "Your campaign" : c.name, scroll);
    }
}
