package ai.ibiko.chat.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.Calendar;

import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignBoard;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.Results;

/**
 * One campaign: its state and dates, what to do with it (review, pause, resume, its chat, delete), how it is doing,
 * the brief it was made from, and every post by day with its time, account, state, responses and link. Tap a post
 * to see its picture, edit it, take it out, share it or try it again.
 */
public final class CampaignActivity extends AppCompatActivity {
    static final String EXTRA_ID = "campaign";
    private LinearLayout body;
    private TextView title;
    private String id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        id = getIntent().getStringExtra(EXTRA_ID);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4));
        ImageView back = Ui.icon(this, ai.ibiko.R.drawable.ic_close, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        back.setContentDescription("Back to campaigns");
        int cp = Ui.dp(this, 12);
        back.setPadding(cp, cp, cp, cp);
        back.setOnClickListener(v -> finish());
        Ui.press(back);
        head.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        title = Ui.text(this, "Campaign", 18, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(new View(this), new LinearLayout.LayoutParams(Ui.dp(this, 48), 1));
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.fitInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        CampaignRunner.seen(this); // what needed the person is in front of them now
        fill();
    }

    private void fill() {
        body.removeAllViews();
        final Campaign c = id == null ? null : CampaignRunner.store(this).get(id);
        if (c == null) {
            finish(); // deleted meanwhile
            return;
        }
        long now = System.currentTimeMillis();
        boolean planning = CampaignDraft.planning(c.id) != null;
        title.setText(c.name.isEmpty() ? "Campaign" : c.name);
        String state = CampaignBoard.state(c, planning, now);
        DateFormat day = DateFormat.getDateInstance(DateFormat.MEDIUM);
        long[] span = CampaignBoard.span(c);
        body.addView(Ui.text(this, state + (span[0] == 0 ? "" : " · " + day.format(new java.util.Date(span[0])) + " – "
                + day.format(new java.util.Date(span[1]))) + (CampaignBoard.networks(c).isEmpty() ? ""
                : " · " + String.join(", ", CampaignBoard.networks(c))), 14, Ui.SUB, false), Ui.wide(this, 2));

        // what can be done with it now
        LinearLayout acts = new LinearLayout(this);
        if (c.status == Campaign.Status.DRAFT && !c.items.isEmpty()) {
            acts.addView(action("Review and approve", true, v -> CampaignReview.show(this, c, this::fill)), grow(0));
        } else if (c.status == Campaign.Status.ACTIVE) {
            acts.addView(action("Pause", false, v -> {
                CampaignRunner.setRunning(this, c.id, false);
                fill();
            }), grow(0));
        } else if (c.status == Campaign.Status.PAUSED) {
            acts.addView(action("Resume", true, v -> {
                CampaignRunner.setRunning(this, c.id, true);
                fill();
            }), grow(0));
        }
        acts.addView(action("Studio chat", false, v -> startActivity(CampaignChatActivity.open(this, c.id))),
                grow(acts.getChildCount() > 0 ? 8 : 0));
        acts.addView(action("Delete", false, v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Delete “" + c.name + "”? Posts already out stay on the networks; the rest won't go out.")
                .setPositiveButton("Delete", (d, w) -> {
                    CampaignRunner.delete(this, c);
                    finish();
                })
                .setNegativeButton("Keep", null)
                .show()), grow(acts.getChildCount() > 0 ? 8 : 0));
        body.addView(acts, Ui.wide(this, 14));

        // how it is doing
        Results results = CampaignRunner.results(this);
        body.addView(card("How it's doing", results.summary(c, java.util.TimeZone.getDefault())), Ui.wide(this, 16));
        body.addView(card("Made from", CampaignBoard.brief(c, SocialWeb.accounts(this)).replaceFirst("^New social media campaign\\s*", "")),
                Ui.wide(this, 12));

        // every post, by day
        body.addView(Ui.text(this, "Posts", 16, Ui.TEXT, true), Ui.wide(this, 20));
        SocialAccounts accounts = SocialWeb.accounts(this);
        DateFormat dayHead = new java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault());
        DateFormat time = DateFormat.getTimeInstance(DateFormat.SHORT);
        String lastDay = "";
        Calendar cal = Calendar.getInstance();
        for (final Campaign.Item it : c.items) {
            cal.setTimeInMillis(it.at);
            String d = dayHead.format(cal.getTime());
            if (!d.equals(lastDay)) {
                lastDay = d;
                body.addView(Ui.text(this, d, 13, Ui.SUB, true), Ui.wide(this, 14));
            }
            body.addView(postRow(c, it, accounts, results, time), Ui.wide(this, 6));
        }
    }

    private LinearLayout.LayoutParams grow(int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(Ui.dp(this, startDp));
        return lp;
    }

    private TextView action(String label, boolean primary, View.OnClickListener tap) {
        TextView b = Ui.button(this, label, primary);
        b.setTextSize(14);
        b.setOnClickListener(tap);
        Ui.press(b);
        return b;
    }

    private View card(String head, String text) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 18));
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);
        card.addView(Ui.text(this, head, 13, Ui.SUB, true));
        card.addView(Ui.text(this, text, 14, Ui.TEXT, false), Ui.wide(this, 6));
        return card;
    }

    /** One post: time, account and state; its words; its responses and link; a tap for what can be done with it. */
    private View postRow(final Campaign c, final Campaign.Item it, SocialAccounts accounts, Results results, DateFormat time) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackground(Ui.round(this, Ui.SURFACE, 0, 16));
        int p = Ui.dp(this, 14);
        row.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        SocialAccounts.Account a = accounts.get(it.account);
        String from = it.handoff || a == null ? CampaignRunner.shown(it.network) : a.label + " (" + a.platform.shown + ")";
        String state = it.state == Campaign.State.POSTED ? (it.handoff ? "Handed to you" : it.checked == 1 ? "Posted ✓" : "Posted")
                : it.state == Campaign.State.FAILED ? "Didn't go out"
                : it.result.startsWith("waiting") ? "Waiting" : "Planned";
        int stateColor = it.state == Campaign.State.POSTED ? Ui.GREEN : it.state == Campaign.State.FAILED ? Ui.AMBER : Ui.SUB;
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.text(this, time.format(new java.util.Date(it.at)) + " · " + from, 13, Ui.SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(Ui.text(this, state, 13, stateColor, true));
        row.addView(top);
        TextView words = Ui.text(this, it.text, 14, Ui.TEXT, false);
        words.setMaxLines(4);
        words.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(words, Ui.wide(this, 6));
        if (it.state == Campaign.State.POSTED && !it.handoff) {
            Results.Count n = results.of(it.id);
            row.addView(Ui.text(this, n.reactions + (n.reactions == 1 ? " reaction" : " reactions") + " · " + n.talk
                    + (n.talk == 1 ? " comment or share" : " comments or shares"), 13, Ui.SUB, false), Ui.wide(this, 6));
        } else if (it.state == Campaign.State.FAILED || it.result.startsWith("waiting")) {
            TextView why = Ui.text(this, it.result.replaceFirst("^waiting:\\s*", ""), 13, Ui.SUB, false);
            why.setMaxLines(3);
            row.addView(why, Ui.wide(this, 6));
            if (it.state == Campaign.State.FAILED && !it.handoff) {
                // tried again on screen, where the network's page works as it does for the person
                TextView again = Ui.text(this, "Post it on screen", 14, Ui.ACCENT, true);
                again.setMinHeight(Ui.dp(this, 40));
                again.setGravity(Gravity.CENTER_VERTICAL);
                again.setOnClickListener(v -> startActivity(PostingActivity.intent(this, c.id, it.id)));
                row.addView(again);
            }
        }
        if (!it.url.isEmpty()) {
            TextView link = Ui.text(this, "Open the post", 14, Ui.ACCENT, true);
            link.setMinHeight(Ui.dp(this, 40));
            link.setGravity(Gravity.CENTER_VERTICAL);
            link.setContentDescription("Open the post on " + CampaignRunner.shown(it.network));
            link.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url)));
                } catch (RuntimeException ignored) {
                    // no browser
                }
            });
            row.addView(link);
        }
        row.setContentDescription(time.format(new java.util.Date(it.at)) + ", " + from + ", " + state + ". " + it.text);
        Ui.press(row);
        row.setOnClickListener(v -> PostActions.menu(this, c, it, this::fill));
        return row;
    }
}
