package ai.ibiko.chat.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignBoard;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.Lead;
import ai.ibiko.social.campaign.LeadSender;
import ai.ibiko.social.campaign.Results;

/**
 * The campaigns dashboard, the social media manager's own corner: at a glance what is running, what is coming up and
 * what is past, with this week's numbers; every campaign as a card (its state, progress, next post and how people
 * responded) that opens to its studio conversation. New campaign, Products and Leads are one tap away.
 */
public final class CampaignsActivity extends AppCompatActivity {
    private static final int FORM = 51;
    private LinearLayout body;
    private CampaignBoard.Bucket tab = CampaignBoard.Bucket.RUNNING;
    private boolean planningNow;
    private final Runnable refresh = () -> {
        if (!isFinishing()) {
            fill();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        if (savedInstanceState != null && savedInstanceState.getString("tab") != null) {
            tab = CampaignBoard.Bucket.valueOf(savedInstanceState.getString("tab"));
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4));
        head.addView(iconButton(ai.ibiko.R.drawable.ic_close, "Close", v -> finish()),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView title = Ui.text(this, "Campaigns", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(iconButton(ai.ibiko.R.drawable.ic_plus, "New campaign", v -> newCampaign()),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
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
        fill();
    }

    @Override
    protected void onPause() {
        super.onPause();
        body.removeCallbacks(refresh);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("tab", tab.name());
    }

    private ImageView iconButton(int icon, String what, View.OnClickListener tap) {
        ImageView b = Ui.icon(this, icon, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        b.setContentDescription(what);
        int p = Ui.dp(this, 12);
        b.setPadding(p, p, p, p);
        b.setOnClickListener(tap);
        Ui.press(b);
        return b;
    }

    private void fill() {
        body.removeCallbacks(refresh);
        body.removeAllViews();
        planningNow = false;
        final long now = System.currentTimeMillis();
        List<Campaign> all = CampaignRunner.store(this).all();
        Set<String> planning = new HashSet<String>();
        for (Campaign c : all) {
            if (CampaignDraft.planning(c.id) != null) {
                planning.add(c.id);
                planningNow = true;
            }
        }
        Results results = CampaignRunner.results(this);
        List<Lead> leads = LeadSender.store(this).all();
        int waitingLeads = 0;
        for (Lead l : leads) {
            waitingLeads += l.kind == Lead.Kind.LEAD && l.state == Lead.State.NEW ? 1 : 0;
        }
        List<Campaign> running = CampaignBoard.of(all, CampaignBoard.Bucket.RUNNING, planning, now);
        List<Campaign> upcoming = CampaignBoard.of(all, CampaignBoard.Bucket.UPCOMING, planning, now);
        List<Campaign> past = CampaignBoard.of(all, CampaignBoard.Bucket.PAST, planning, now);

        // this week at a glance
        int weekPosts = 0, weekResponses = 0;
        for (Campaign c : all) {
            for (Campaign.Item it : c.items) {
                if (it.state == Campaign.State.POSTED && !it.handoff && now - it.at < 7 * 86_400_000L) {
                    weekPosts++;
                    Results.Count n = results.of(it.id);
                    weekResponses += n.reactions + n.talk;
                }
            }
        }
        LinearLayout tiles = new LinearLayout(this);
        tiles.addView(tile(String.valueOf(running.size()), "Running"), weighted(0));
        tiles.addView(tile(String.valueOf(upcoming.size()), "Coming up"), weighted(8));
        tiles.addView(tile(String.valueOf(weekPosts), "Posts this week"), weighted(8));
        tiles.addView(tile(String.valueOf(weekResponses), "Responses this week"), weighted(8));
        body.addView(tiles, Ui.wide(this, 4));

        LinearLayout quick = new LinearLayout(this);
        quick.addView(chip("New campaign", true, v -> newCampaign()), new LinearLayout.LayoutParams(-2, -2));
        quick.addView(chip("Products", false, v -> startActivity(new Intent(this, CatalogActivity.class))), marginStart(8));
        final int leadCount = waitingLeads;
        quick.addView(chip(leadCount > 0 ? "Leads · " + leadCount : "Leads", false,
                v -> startActivity(new Intent(this, SocialActivity.class).putExtra(SocialActivity.EXTRA_FOCUS, "leads"))), marginStart(8));
        quick.addView(chip("Accounts", false, v -> startActivity(new Intent(this, SocialActivity.class))), marginStart(8));
        android.widget.HorizontalScrollView qs = new android.widget.HorizontalScrollView(this);
        qs.setHorizontalScrollBarEnabled(false);
        qs.addView(quick);
        body.addView(qs, Ui.wide(this, 14));

        // Running · Coming up · Past
        LinearLayout tabs = new LinearLayout(this);
        tabs.setBackground(Ui.round(this, Ui.SURFACE, 0, 24));
        int tp = Ui.dp(this, 4);
        tabs.setPadding(tp, tp, tp, tp);
        tabs.addView(tabButton(CampaignBoard.Bucket.RUNNING, "Running", running.size()), new LinearLayout.LayoutParams(0, -2, 1f));
        tabs.addView(tabButton(CampaignBoard.Bucket.UPCOMING, "Coming up", upcoming.size()), new LinearLayout.LayoutParams(0, -2, 1f));
        tabs.addView(tabButton(CampaignBoard.Bucket.PAST, "Past", past.size()), new LinearLayout.LayoutParams(0, -2, 1f));
        body.addView(tabs, Ui.wide(this, 18));

        List<Campaign> shown = tab == CampaignBoard.Bucket.RUNNING ? running : tab == CampaignBoard.Bucket.UPCOMING ? upcoming : past;
        if (shown.isEmpty()) {
            String none = tab == CampaignBoard.Bucket.RUNNING ? "No campaign is running. Plan one, approve it, and it posts by itself."
                    : tab == CampaignBoard.Bucket.UPCOMING ? "Nothing coming up. Campaigns being planned, waiting for your review,"
                    + " or approved to start later show here." : "No finished campaigns yet.";
            TextView t = Ui.text(this, none, 14, Ui.SUB, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Ui.dp(this, 12), Ui.dp(this, 28), Ui.dp(this, 12), Ui.dp(this, 12));
            body.addView(t);
            if (tab != CampaignBoard.Bucket.PAST) {
                TextView plan = Ui.button(this, "Plan a campaign", true);
                plan.setOnClickListener(v -> newCampaign());
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                body.addView(plan, lp);
            }
        }
        SocialAccounts accounts = SocialWeb.accounts(this);
        for (Campaign c : shown) {
            int found = 0;
            for (Lead l : leads) {
                found += l.campaign.equals(c.id) && l.kind == Lead.Kind.LEAD ? 1 : 0;
            }
            body.addView(card(c, planning.contains(c.id), results, accounts, found, now), Ui.wide(this, 12));
        }
        if (planningNow) {
            body.postDelayed(refresh, 3000); // planning's progress, as it goes
        }
    }

    private LinearLayout.LayoutParams weighted(int startDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(Ui.dp(this, startDp));
        return lp;
    }

    private LinearLayout.LayoutParams marginStart(int dp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginStart(Ui.dp(this, dp));
        return lp;
    }

    private View tile(String number, String label) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setBackground(Ui.round(this, Ui.SURFACE, 0, 16));
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12));
        t.addView(Ui.text(this, number, 20, Ui.TEXT, true));
        TextView l = Ui.text(this, label, 11, Ui.SUB, false);
        l.setMaxLines(2);
        t.addView(l);
        t.setContentDescription(number + " " + label);
        return t;
    }

    private TextView chip(String label, boolean primary, View.OnClickListener tap) {
        TextView b = Ui.text(this, label, 14, primary ? Ui.ON_CORAL : Ui.TEXT, primary);
        b.setBackground(primary ? Ui.round(this, Ui.CORAL, 0, 20) : Ui.round(this, Ui.SURFACE, Ui.LINE, 20));
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this, 40));
        b.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        b.setOnClickListener(tap);
        Ui.press(b);
        return b;
    }

    private TextView tabButton(final CampaignBoard.Bucket which, String label, int count) {
        boolean on = tab == which;
        TextView b = Ui.text(this, label + (count > 0 ? " · " + count : ""), 14, on ? Ui.TEXT : Ui.SUB, on);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this, 40));
        if (on) {
            b.setBackground(Ui.round(this, Ui.CARD_ON, 0, 20));
        }
        b.setContentDescription(label + ", " + count + (on ? ", selected" : ""));
        b.setOnClickListener(v -> {
            tab = which;
            fill();
        });
        return b;
    }

    /** One campaign: name and state, where and when, progress, the next post, and what it brought. */
    private View card(final Campaign c, boolean planning, Results results, SocialAccounts accounts, int leads, long now) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        int p = Ui.dp(this, 16);
        card.setPadding(p, Ui.dp(this, 14), p, Ui.dp(this, 14));
        String state = CampaignBoard.state(c, planning, now);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, c.name.isEmpty() ? "Campaign" : c.name, 16, Ui.TEXT, true);
        name.setMaxLines(2);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(stateChip(state), marginStart(8));
        card.addView(top);
        DateFormat day = DateFormat.getDateInstance(DateFormat.MEDIUM), time = DateFormat.getTimeInstance(DateFormat.SHORT);
        long[] span = CampaignBoard.span(c);
        String where = String.join(", ", CampaignBoard.networks(c));
        String when = span[0] == 0 ? c.days + (c.days == 1 ? " day" : " days")
                : day.format(new java.util.Date(span[0])) + " – " + day.format(new java.util.Date(span[1]));
        card.addView(Ui.text(this, (where.isEmpty() ? "" : where + " · ") + when, 13, Ui.SUB, false), Ui.wide(this, 4));
        String planLine = CampaignDraft.planning(c.id);
        if (planning) {
            card.addView(Ui.text(this, planLine == null ? "Planning" : planLine, 14, Ui.TEXT, false), Ui.wide(this, 10));
            ProgressBar spin = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            spin.setIndeterminate(true);
            card.addView(spin, Ui.wide(this, 6));
        } else if (!c.items.isEmpty()) {
            int posted = CampaignBoard.posted(c), failed = CampaignBoard.failed(c);
            ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
            bar.setMax(c.items.size());
            bar.setProgress(posted);
            bar.setSecondaryProgress(posted + failed);
            bar.setProgressTintList(ColorStateList.valueOf(Ui.ACCENT));
            bar.setContentDescription(posted + " of " + c.items.size() + " posted");
            card.addView(bar, Ui.wide(this, 10));
            card.addView(Ui.text(this, posted + " of " + c.items.size() + " posted" + (failed > 0 ? " · " + failed + " didn't go out" : ""),
                    13, Ui.SUB, false), Ui.wide(this, 2));
            Campaign.Item next = CampaignBoard.next(c);
            if (next != null && c.status != Campaign.Status.DRAFT) {
                SocialAccounts.Account a = accounts.get(next.account);
                String from = next.handoff || a == null ? CampaignRunner.shown(next.network) : a.label + " (" + a.platform.shown + ")";
                card.addView(Ui.text(this, "Next: " + day.format(new java.util.Date(next.at)) + ", " + time.format(new java.util.Date(next.at))
                        + " on " + from, 14, Ui.TEXT, false), Ui.wide(this, 8));
            }
            int reactions = 0, talk = 0;
            for (Campaign.Item it : c.items) {
                Results.Count n = results.of(it.id);
                reactions += n.reactions;
                talk += n.talk;
            }
            if (posted > 0 || leads > 0) {
                card.addView(Ui.text(this, reactions + (reactions == 1 ? " reaction" : " reactions") + " · " + talk
                        + (talk == 1 ? " comment or share" : " comments or shares") + " · " + leads + (leads == 1 ? " lead" : " leads"),
                        13, Ui.SUB, false), Ui.wide(this, 4));
            }
            if (c.status == Campaign.Status.DRAFT) {
                card.addView(Ui.text(this, "Tap to review the plan and approve it", 14, Ui.ACCENT, false), Ui.wide(this, 8));
            }
        }
        card.setContentDescription(c.name + ", " + state);
        Ui.press(card);
        card.setOnClickListener(v -> open(c, planning));
        return card;
    }

    private View stateChip(String state) {
        int color = "Running".equals(state) ? Ui.GREEN : "Paused".equals(state) || "Planning stopped".equals(state) ? Ui.AMBER
                : "Done".equals(state) ? Ui.SUB : Ui.ACCENT;
        TextView t = Ui.text(this, state, 12, color, true);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4));
        t.setBackground(Ui.round(this, Ui.PILL, 0, 12));
        return t;
    }

    /** A campaign opens to its studio conversation (planning shows there as it goes). */
    private void open(final Campaign c, boolean planning) {
        startActivity(CampaignChatActivity.open(this, c.id));
    }

    private void newCampaign() {
        startActivityForResult(new Intent(this, CampaignFormActivity.class), FORM);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FORM && result == RESULT_OK) {
            // the campaign studio plans it on its own; its conversation opens, showing the plan as it is made
            String id = ai.ibiko.social.campaign.CampaignStudio.startWaiting(this);
            if (id != null) {
                startActivity(CampaignChatActivity.open(this, id));
            }
        }
    }
}
