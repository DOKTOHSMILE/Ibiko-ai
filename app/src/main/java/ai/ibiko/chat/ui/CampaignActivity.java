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

        // every post, as a grid of their pictures (many at a glance); a tap opens one full size
        int out = 0, failed = 0;
        for (Campaign.Item it : c.items) {
            out += it.state == Campaign.State.POSTED ? 1 : 0;
            failed += it.state == Campaign.State.FAILED ? 1 : 0;
        }
        body.addView(Ui.text(this, "Posts · " + c.items.size() + (out > 0 ? " · " + out + " out" : "")
                + (failed > 0 ? " · " + failed + " didn't go out" : ""), 16, Ui.TEXT, true), Ui.wide(this, 20));
        java.util.List<Campaign.Item> inOrder = new java.util.ArrayList<Campaign.Item>(c.items);
        inOrder.sort((x, y) -> Long.compare(x.at, y.at));
        PostGrid.add(this, c, inOrder, results, body, this::fill);
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
}
