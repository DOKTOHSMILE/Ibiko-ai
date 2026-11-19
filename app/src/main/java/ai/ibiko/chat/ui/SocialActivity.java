package ai.ibiko.chat.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialLimits;
import ai.ibiko.social.SocialWeb;

/**
 * The user's social accounts: several on each network, one in use at a time (tap another to switch), each signed in on
 * the site itself; what Ibiko has done with each today against its limits, and a Resume when the site showed a
 * warning and the account was paused; the pace (careful or normal) and whether fact checks look at what people post.
 */
public final class SocialActivity extends AppCompatActivity {
    private LinearLayout body;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        prefs = getSharedPreferences("ibiko", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4));
        android.widget.ImageView close = Ui.icon(this, ai.ibiko.R.drawable.ic_close, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        close.setContentDescription("Close");
        int cp = Ui.dp(this, 12);
        close.setPadding(cp, cp, cp, cp);
        close.setOnClickListener(v -> finish());
        Ui.press(close);
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48))); // a 48 dp target
        TextView title = Ui.text(this, "Social accounts", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 48), 1)); // as wide as Close: the title centred
        root.addView(head);
        ScrollView scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, 0, pad, Ui.dp(this, 32));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        Ui.fitInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill(); // back from signing in
    }

    /** A campaign is being planned: the list redraws every few seconds, so its progress shows. */
    private boolean planningNow;
    private final Runnable refresh = () -> {
        if (!isFinishing()) {
            fill();
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        body.removeCallbacks(refresh);
    }

    private void fill() {
        body.removeCallbacks(refresh);
        planningNow = false;
        final android.view.View scroller = (android.view.View) body.getParent();
        final int place = scroller == null ? 0 : scroller.getScrollY();
        if (scroller != null) {
            scroller.post(() -> scroller.setScrollY(place)); // a redraw keeps the place on the screen
        }
        body.removeAllViews();
        TextView intro = Ui.text(this, "Sign in on the sites themselves. Ibiko reads with your accounts when you ask (“search X"
                + " for …”, “what are people saying on Reddit about …”) and in fact checks, and posts and comments for you on"
                + " Facebook and X after your yes, a little at a time like a person; it stops if a site shows a warning.", 14, Ui.SUB, false);
        intro.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), 0);
        body.addView(intro);
        SocialAccounts accounts = SocialWeb.accounts(this);
        SocialLimits limits = SocialWeb.limits(this);
        long now = System.currentTimeMillis();
        for (final Platform p : Platform.values()) {
            LinearLayout card = section(p.shown, icon(p));
            List<SocialAccounts.Account> on = accounts.on(p);
            for (final SocialAccounts.Account a : on) {
                String paused = limits.pausedWhy(a.id, now);
                String state = paused != null ? "Paused: " + paused
                        : !a.signedIn ? "Signed out: tap Add account to sign in again"
                        : (a.inUse ? "In use · " : "Tap to use · ") + limits.used(a.id, SocialLimits.Kind.READ, now)
                                + " of " + limits.cap(p, SocialLimits.Kind.READ, true) + " reads today"
                                + (ai.ibiko.social.SocialPoster.direct(p == Platform.X ? "x" : p == Platform.FACEBOOK ? "facebook" : "") == null ? ""
                                        : " · " + limits.used(a.id, SocialLimits.Kind.POST, now) + " of " + limits.cap(p, SocialLimits.Kind.POST, true)
                                        + " posts · " + limits.used(a.id, SocialLimits.Kind.COMMENT, now) + " of "
                                        + limits.cap(p, SocialLimits.Kind.COMMENT, true) + " comments");
                LinearLayout row = row(a.label, a.inUse, state, () -> remove(a), () -> rename(a));
                row.setContentDescription(a.label + ", " + p.shown + ". " + state);
                if (!a.inUse) {
                    Ui.press(row); // only a row that switches the account looks tappable
                }
                row.setOnClickListener(v -> {
                    if (!a.inUse) {
                        boolean ok = SocialWeb.switchTo(this, a.id);
                        android.widget.Toast.makeText(this, ok ? "Using " + a.label + " on " + p.shown
                                : "That sign-in wasn't kept; sign in again with Add account.", android.widget.Toast.LENGTH_SHORT).show();
                        fill();
                    }
                });
                card.addView(row);
                if (a.inUse && a.signedIn && paused == null) {
                    card.addView(actions(p, a)); // the standard things to do with the account in use
                }
                if (paused != null) {
                    TextView resume = Ui.text(this, "Resume " + a.label, 15, Ui.ACCENT, false);
                    resume.setGravity(Gravity.CENTER_VERTICAL);
                    resume.setMinHeight(Ui.dp(this, 48));
                    Ui.press(resume);
                    resume.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setMessage("Open " + p.shown + " in your browser or its app first and clear the warning or check."
                                    + " Resume " + a.label + " now?")
                            .setPositiveButton("Resume", (d, w) -> {
                                limits.resume(a.id);
                                SocialWeb.keepLimits(this);
                                fill();
                            })
                            .setNegativeButton("Not yet", null)
                            .show());
                    card.addView(resume);
                }
            }
            TextView add = Ui.text(this, on.isEmpty() ? "Sign in to " + p.shown : "Add account", 15, Ui.ACCENT, false);
            add.setGravity(Gravity.CENTER_VERTICAL);
            add.setMinHeight(Ui.dp(this, 48));
            Ui.press(add);
            add.setOnClickListener(v -> startActivity(new Intent(this, SocialLoginActivity.class)
                    .putExtra(SocialLoginActivity.EXTRA_PLATFORM, p.name())));
            card.addView(add);
        }

        campaigns();

        // ---- posting: how Ibiko posts, the scheduled posts, and what it did
        LinearLayout posting = section("Posting", ai.ibiko.R.drawable.ic_send);
        posting.addView(toggle("Post directly from your account", "After your yes, Ibiko types the post into Facebook or X in the"
                + " account in use and presses Post, then tells you whether the site confirmed it. Off: it opens the app with the"
                + " post ready, and you press Post.", "social_direct", true), Ui.wide(this, 10));
        posting.addView(toggle("Auto-post on your behalf", "Posts and comments you ask for go out at once, without asking you"
                + " first. Still only what you ask for, within the limits below, and never tagging people you didn't name."
                + " Off: each one waits for your yes.", "social_auto", false), Ui.wide(this, 10));
        posting.addView(toggle("Three versions to pick from", "A post you ask for is written three ways (professional, personal,"
                + " short); you pick one before anything goes out.", "post_versions", true), Ui.wide(this, 10));
        java.util.List<ai.ibiko.social.PostPlan> plans = ai.ibiko.social.PostAlarm.plans(this);
        java.util.Collections.sort(plans, (x, y) -> Long.compare(x.at, y.at));
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        posting.addView(Ui.text(this, plans.isEmpty() ? "No posts scheduled. Ask: \u201cschedule a Facebook post every Monday at 9\u201d."
                : "Scheduled (" + plans.size() + ") \u00B7 tap one to cancel it", 14, Ui.SUB, false), Ui.wide(this, 12));
        for (final ai.ibiko.social.PostPlan plan : plans) {
            LinearLayout r = iconRow(ai.ibiko.R.drawable.ic_calendar, df.format(new java.util.Date(plan.at)) + " \u00B7 " + plan.network
                    + " \u00B7 " + plan.repeatWords(), plan.text);
            r.setMinimumHeight(Ui.dp(this, 48));
            Ui.press(r);
            r.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage("Cancel this scheduled post?\n\n" + plan.text)
                    .setPositiveButton("Cancel it", (d, w) -> {
                        ai.ibiko.social.PostAlarm.cancel(this, plan.id);
                        fill();
                    })
                    .setNegativeButton("Keep", null)
                    .show());
            posting.addView(r);
        }
        java.util.List<String> done = new java.util.ArrayList<String>();
        for (String line : ai.ibiko.act.ActionLog.read(getFilesDir())) {
            String[] f = line.split("\t", -1);
            if (f.length >= 6 && f[1].matches("post|comment|share|schedule|pause|sign-out") && done.size() < 8) {
                done.add(line);
            }
        }
        posting.addView(Ui.text(this, done.isEmpty() ? "Nothing posted from Ibiko yet." : "Recent activity", 14, Ui.SUB, false),
                Ui.wide(this, 12));
        for (String line : done) {
            String[] f = line.split("\t", -1);
            posting.addView(iconRow(ai.ibiko.R.drawable.ic_history, f[0] + " \u00B7 " + f[1] + " \u00B7 " + f[2] + " \u00B7 " + f[4],
                    f[5].length() > 120 ? f[5].substring(0, 120) + "\u2026" : f[5]));
        }

        LinearLayout safe = section("Keeping accounts safe", ai.ibiko.R.drawable.ic_key);
        safe.addView(Ui.text(this, "Pace", 16, Ui.TEXT, false), Ui.wide(this, 8));
        LinearLayout pace = new LinearLayout(this);
        for (final String[] c : new String[][]{{"Careful", "careful"}, {"Normal", "normal"}}) {
            boolean picked = c[1].equals(prefs.getString("social_pace", "normal"));
            TextView pill = Ui.text(this, c[0], 14, picked ? Ui.BG : Ui.TEXT, picked);
            pill.setBackground(Ui.round(this, picked ? Ui.TEXT : Ui.FIELD, 0, 16));
            pill.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
            pill.setGravity(Gravity.CENTER);
            pill.setMinHeight(Ui.dp(this, 40));
            pill.setSelected(picked);
            pill.setContentDescription(c[0] + " pace" + (picked ? ", selected" : ""));
            pill.setOnClickListener(v -> {
                prefs.edit().putString("social_pace", c[1]).apply();
                SocialWeb.limits(this);
                fill();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = Ui.dp(this, 8);
            pace.addView(pill, lp);
        }
        safe.addView(pace, Ui.wide(this, 6));
        safe.addView(Ui.text(this, "Normal keeps each account to about a third of what an active person does by hand, with"
                + " gaps that vary like a person's; Careful halves that. Limits an hour and a day, for reads (X: "
                + limits.cap(Platform.X, SocialLimits.Kind.READ, false) + " an hour, " + limits.cap(Platform.X, SocialLimits.Kind.READ, true)
                + " a day). When a site asks for a check or shows a warning, that account stops for 24 hours or until you"
                + " resume it.", 13, Ui.SUB, false), Ui.wide(this, 8));
        safe.addView(toggle("Look at what people post in fact checks", "Online fact checks also search your signed-in accounts;"
                + " posts count as what people say, not as proof.", "social_factcheck", true), Ui.wide(this, 12));
        TextView only = Ui.text(this, "Posts and comments go out only after your yes (or when a post you scheduled is due), within"
                + " these limits: on Facebook " + limits.cap(Platform.FACEBOOK, SocialLimits.Kind.POST, true) + " posts and "
                + limits.cap(Platform.FACEBOOK, SocialLimits.Kind.COMMENT, true) + " comments a day; on X "
                + limits.cap(Platform.X, SocialLimits.Kind.POST, true) + " posts and " + limits.cap(Platform.X, SocialLimits.Kind.COMMENT, true)
                + " comments a day. Ibiko never tags people you didn't name.", 13, Ui.SUB, false);
        only.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 10));
        safe.addView(only);
        if (planningNow) {
            body.postDelayed(refresh, 3000); // the planning line moves on while this screen is open
        }
    }

    /**
     * Campaigns: with the switch on, a brief becomes a whole plan (the brain builds it in the chat, you approve it), and
     * the approved plan runs by itself from the accounts chosen.
     */
    private void campaigns() {
        LinearLayout card = section("Campaigns", ai.ibiko.R.drawable.ic_calendar);
        card.addView(toggle("Run campaigns", "Give Ibiko a short brief (your business, the offer, who it's for) and it plans"
                + " the whole campaign: themes, a dated post calendar with words and a picture for each, and searches for people"
                + " who need what you offer. You approve the plan once; then posts go out by themselves from the accounts you"
                + " chose, within each account's daily limits.", "social_campaigns", false), Ui.wide(this, 4));
        if (!prefs.getBoolean("social_campaigns", false)) {
            return;
        }
        java.text.DateFormat df = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM);
        for (final ai.ibiko.social.campaign.Campaign c : ai.ibiko.social.campaign.CampaignRunner.store(this).all()) {
            final String planning = ai.ibiko.social.campaign.CampaignDraft.planning(c.id);
            boolean draft = c.status == ai.ibiko.social.campaign.Campaign.Status.DRAFT;
            boolean cut = draft && planning == null && c.items.isEmpty(); // Ibiko closed while it was planning
            String state = planning != null ? "Planning" : cut ? "Planning didn't finish" : draft ? "Ready to review"
                    : c.status == ai.ibiko.social.campaign.Campaign.Status.ACTIVE ? "Running"
                    : c.status == ai.ibiko.social.campaign.Campaign.Status.PAUSED ? "Paused" : "Done";
            String under = planning != null ? planning + " · tap to stop" : cut ? "Tap to remove it, then plan it again"
                    : draft ? c.items.size() + " posts · tap to read the plan and approve it"
                    : c.progress() + " · from " + df.format(new java.util.Date(c.startAt));
            LinearLayout r = iconRow(planning != null ? ai.ibiko.R.drawable.ic_calendar : ai.ibiko.R.drawable.ic_send,
                    c.name + " · " + state, under);
            r.setMinimumHeight(Ui.dp(this, 52));
            r.setContentDescription(c.name + ", " + state + ", " + under);
            Ui.press(r);
            if (planning != null) {
                planningNow = true;
                r.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setMessage("Stop planning “" + c.name + "”? Nothing has been posted.")
                        .setPositiveButton("Stop", (d, w) -> {
                            ai.ibiko.social.campaign.CampaignDraft.stop(c.id);
                            android.widget.Toast.makeText(this, "Stopping after the posts being written now.",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Keep planning", null)
                        .show());
            } else if (cut) {
                r.setOnClickListener(v -> {
                    try {
                        ai.ibiko.social.campaign.CampaignRunner.store(this).remove(c.id);
                    } catch (java.io.IOException ignored) {
                        // the list shows what was kept
                    }
                    fill();
                });
            } else if (draft) {
                r.setOnClickListener(v -> CampaignReview.show(this, c, this::fill));
            } else {
                r.setOnClickListener(v -> campaignMenu(c));
            }
            card.addView(r);
        }
        TextView add = Ui.text(this, "New campaign", 15, Ui.ACCENT, false);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(Ui.dp(this, 48));
        Ui.press(add);
        add.setOnClickListener(v -> newCampaign());
        card.addView(add);
        leads();
    }

    /** People the campaigns found who may need the offer, each with the reply drafted for them: Send, Edit, Dismiss. */
    private void leads() {
        LinearLayout card = section("Leads", ai.ibiko.R.drawable.ic_people);
        java.util.List<ai.ibiko.social.campaign.Lead> waiting = new java.util.ArrayList<ai.ibiko.social.campaign.Lead>();
        int sent = 0;
        for (ai.ibiko.social.campaign.Lead l : ai.ibiko.social.campaign.LeadSender.store(this).all()) {
            if (l.state == ai.ibiko.social.campaign.Lead.State.NEW && l.kind == ai.ibiko.social.campaign.Lead.Kind.LEAD) {
                waiting.add(l);
            } else if (l.state == ai.ibiko.social.campaign.Lead.State.SENT) {
                sent++;
            }
        }
        waiting.sort((x, y) -> Double.compare(y.fit, x.fit));
        card.addView(Ui.text(this, waiting.isEmpty() ? "No leads waiting. While a campaign runs, Ibiko looks for people who"
                + " need your offer about every 45 minutes (Ibiko has to be running, with a brain)." + (sent > 0 ? " Sent so far: " + sent + "." : "")
                : waiting.size() + " waiting, best fit first · tap one to read the post and the reply drafted for it"
                + (sent > 0 ? " · " + sent + " sent" : ""), 13, Ui.SUB, false), Ui.wide(this, 8));
        for (int i = 0; i < waiting.size() && i < 30; i++) {
            final ai.ibiko.social.campaign.Lead l = waiting.get(i);
            LinearLayout r = iconRow(l.network.equals("x") ? ai.ibiko.R.drawable.ic_net_x : ai.ibiko.R.drawable.ic_net_facebook,
                    Math.round(l.fit * 100) + "% fit · " + (l.why.isEmpty() ? "may need your offer" : l.why),
                    l.snippet.length() > 140 ? l.snippet.substring(0, 140) + "…" : l.snippet);
            r.setMinimumHeight(Ui.dp(this, 52));
            Ui.press(r);
            r.setOnClickListener(v -> lead(l));
            card.addView(r);
        }
    }

    /** One lead: the post, the reply drafted for it (edit it here), and Send, Dismiss or Open the post. */
    private void lead(final ai.ibiko.social.campaign.Lead l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(Ui.text(this, "Their post", 13, Ui.SUB, false));
        box.addView(Ui.text(this, l.snippet, 14, Ui.TEXT, false), Ui.wide(this, 4));
        box.addView(Ui.text(this, "Your reply, under their post", 13, Ui.SUB, false), Ui.wide(this, 14));
        final android.widget.EditText reply = Ui.field(this, "Reply", l.reply, false, false);
        reply.setMinLines(3);
        box.addView(reply, Ui.wide(this, 4));
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(Math.round(l.fit * 100) + "% fit")
                .setView(sv)
                .setPositiveButton("Send", (d, w) -> {
                    l.reply = reply.getText().toString().trim();
                    android.widget.Toast.makeText(this, "Sending…", android.widget.Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        ai.ibiko.social.SocialPoster.Result r = ai.ibiko.social.campaign.LeadSender.send(getApplicationContext(), l);
                        runOnUiThread(() -> {
                            android.widget.Toast.makeText(this, r.said, android.widget.Toast.LENGTH_LONG).show();
                            if (!isFinishing()) {
                                fill();
                            }
                        });
                    }, "ibiko-lead-send").start();
                })
                .setNegativeButton("Dismiss", (d, w) -> {
                    l.state = ai.ibiko.social.campaign.Lead.State.DISMISSED;
                    try {
                        ai.ibiko.social.campaign.LeadSender.store(this).put(l);
                    } catch (java.io.IOException ignored) {
                        // shown again next time
                    }
                    fill();
                })
                .setNeutralButton("Open post", (d, w) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(l.url)));
                    } catch (RuntimeException ignored) {
                        // no browser
                    }
                })
                .show();
    }

    /** One campaign: pause or resume it, see its posts, or delete it. */
    private void campaignMenu(final ai.ibiko.social.campaign.Campaign c) {
        boolean running = c.status == ai.ibiko.social.campaign.Campaign.Status.ACTIVE;
        boolean done = c.status == ai.ibiko.social.campaign.Campaign.Status.DONE;
        java.util.List<String> labels = new java.util.ArrayList<String>();
        if (!done) {
            labels.add(running ? "Pause" : "Resume");
        }
        labels.add("Show its posts");
        labels.add("Delete");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(c.name)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String l = labels.get(which);
                    if (l.equals("Pause") || l.equals("Resume")) {
                        ai.ibiko.social.campaign.CampaignRunner.setRunning(this, c.id, !running);
                        fill();
                    } else if (l.equals("Show its posts")) {
                        posts(c);
                    } else {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setMessage("Delete “" + c.name + "”? Posts already out stay on the networks; the rest won't go out.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    try {
                                        ai.ibiko.social.campaign.CampaignRunner.store(this).remove(c.id);
                                        ai.ibiko.social.campaign.LeadSender.store(this).forget(c.id);
                                    } catch (java.io.IOException ignored) {
                                        // the list shows what was kept
                                    }
                                    ai.ibiko.social.campaign.CampaignRunner.arm(this, 0);
                                    fill();
                                })
                                .setNegativeButton("Keep", null)
                                .show();
                    }
                })
                .show();
    }

    /** A campaign's posts, each with its time, account and what happened. */
    private void posts(ai.ibiko.social.campaign.Campaign c) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 16));
        java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
        SocialAccounts accounts = SocialWeb.accounts(this);
        for (ai.ibiko.social.campaign.Campaign.Item it : c.items) {
            SocialAccounts.Account a = accounts.get(it.account);
            String state = it.state == ai.ibiko.social.campaign.Campaign.State.POSTED ? "Posted"
                    : it.state == ai.ibiko.social.campaign.Campaign.State.FAILED ? "Didn't go out: " + it.result
                    : it.result.startsWith("waiting") ? "Waiting: " + it.result.substring(8).trim() : "Planned";
            list.addView(iconRow(it.state == ai.ibiko.social.campaign.Campaign.State.POSTED ? ai.ibiko.R.drawable.ic_check
                    : ai.ibiko.R.drawable.ic_calendar, df.format(new java.util.Date(it.at)) + " · "
                    + (a == null ? it.network : a.label) + " · " + state, it.text));
        }
        ScrollView sv = new ScrollView(this);
        sv.addView(list);
        BottomSheet.showView(this, c.name, sv);
    }

    private static final int FORM = 41;

    /** The brief, on its own screen (roomy boxes for long answers); a usable account is needed first. */
    private void newCampaign() {
        boolean any = false;
        for (SocialAccounts.Account a : SocialWeb.accounts(this).all()) {
            any |= a.signedIn && (a.platform == Platform.X || a.platform == Platform.FACEBOOK);
        }
        if (!any) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage("A campaign posts from your Facebook or X accounts. Sign in to one first (above).")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        startActivityForResult(new Intent(this, CampaignFormActivity.class), FORM);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == FORM && result == RESULT_OK) {
            backToChat(); // the brief is with the brain: it plans on its own and shows the plan for approval
        }
    }

    /** To the chat, nothing put in its message box (whatever was typed there stays). */
    private void backToChat() {
        if (getCallingActivity() != null) {
            setResult(RESULT_OK);
        } else {
            startActivity(new Intent(this, ai.ibiko.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }

    /** A new name for the account ("Work", "Shop page"): how it shows here, in Connectors and in replies. */
    private void rename(final SocialAccounts.Account a) {
        final android.widget.EditText field = Ui.field(this, "Name", a.label, false, false);
        LinearLayout box = new LinearLayout(this);
        int p = Ui.dp(this, 20);
        box.setPadding(p, Ui.dp(this, 8), p, 0);
        box.addView(field, new LinearLayout.LayoutParams(-1, -2));
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Rename " + a.platform.shown + " account")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    SocialWeb.accounts(this).rename(a.id, field.getText().toString());
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void remove(final SocialAccounts.Account a) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Remove " + a.label + " (" + a.platform.shown + ") from Ibiko? Its sign-in on this device is deleted.")
                .setPositiveButton("Remove", (d, w) -> {
                    SocialWeb.remove(this, a.id);
                    ai.ibiko.act.ActionLog.add(getFilesDir(), "sign-out", a.platform.shown + " · " + a.label, "user", "removed", "");
                    fill();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The network's icon (Ibiko's own line icons, not the networks' logos). */
    static int icon(Platform p) {
        switch (p) {
            case FACEBOOK:
                return ai.ibiko.R.drawable.ic_net_facebook;
            case X:
                return ai.ibiko.R.drawable.ic_net_x;
            case INSTAGRAM:
                return ai.ibiko.R.drawable.ic_net_instagram;
            case REDDIT:
                return ai.ibiko.R.drawable.ic_net_reddit;
            case TELEGRAM:
                return ai.ibiko.R.drawable.ic_net_telegram;
            default:
                return ai.ibiko.R.drawable.ic_people;
        }
    }

    /** The standard actions for the account in use: write a post, open the profile, see what people say. */
    private LinearLayout actions(final Platform p, final SocialAccounts.Account a) {
        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, 0, 0, Ui.dp(this, 6));
        boolean posts = p == Platform.FACEBOOK || p == Platform.X;
        if (posts) {
            row.addView(chip(ai.ibiko.R.drawable.ic_edit, "Write a post", () -> ask("Write a " + p.shown + " post about ")));
        }
        row.addView(chip(ai.ibiko.R.drawable.ic_open, "Open", () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(p.home)));
            } catch (RuntimeException ignored) {
                // no browser
            }
        }));
        if (p != Platform.TELEGRAM) {
            row.addView(chip(ai.ibiko.R.drawable.ic_search, "Trending", () -> ask("What's trending on " + p.shown + "?")));
        }
        return row;
    }

    /** Back to the chat with words ready in the message box, for the user to finish and send. */
    private void ask(String words) {
        if (getCallingActivity() != null) {
            setResult(RESULT_OK, new Intent().putExtra("compose", words));
        } else {
            // opened from Tools or Connectors, not for a result: the words were dropped and the chips did nothing. They
            // wait for the chat, which is brought to the front (any screen above it closes)
            ShareActivity.offerText(words);
            startActivity(new Intent(this, ai.ibiko.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        }
        finish();
    }

    private android.view.View chip(int icon, String label, Runnable tap) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(Ui.round(this, Ui.FIELD, 0, 16));
        c.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setColorFilter(Ui.ACCENT);
        c.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, ai.ibiko.IconSize.INLINE), Ui.dp(this, ai.ibiko.IconSize.INLINE)));
        TextView t = Ui.text(this, label, 13, Ui.TEXT, false);
        t.setPadding(Ui.dp(this, 6), 0, 0, 0);
        c.addView(t);
        c.setMinimumHeight(Ui.dp(this, 40));
        c.setContentDescription(label);
        Ui.press(c);
        c.setOnClickListener(v -> tap.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = Ui.dp(this, 8);
        c.setLayoutParams(lp);
        return c;
    }

    /** A line with an icon, a title and a line under it. */
    private LinearLayout iconRow(int icon, String title, String under) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setColorFilter(Ui.ACCENT);
        r.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, ai.ibiko.IconSize.STANDARD), Ui.dp(this, ai.ibiko.IconSize.STANDARD)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(Ui.dp(this, 12), 0, 0, 0);
        words.addView(Ui.text(this, title, 14, Ui.TEXT, false));
        if (under != null && !under.isEmpty()) {
            words.addView(Ui.text(this, under, 13, Ui.SUB, false));
        }
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        return r;
    }

    private LinearLayout section(String name, int icon) {
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 6));
        ImageView i = new ImageView(this);
        i.setImageResource(icon);
        i.setColorFilter(Ui.ACCENT);
        head.addView(i, new LinearLayout.LayoutParams(Ui.dp(this, ai.ibiko.IconSize.STANDARD), Ui.dp(this, ai.ibiko.IconSize.STANDARD)));
        TextView t = Ui.text(this, name, 15, Ui.TEXT, true);
        t.setPadding(Ui.dp(this, 10), 0, 0, 0);
        head.addView(t);
        body.addView(head);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 20));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        body.addView(card, Ui.wide(this, 4));
        return card;
    }

    private LinearLayout row(String what, boolean inUse, String under, final Runnable remove, final Runnable rename) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(Ui.dp(this, 52));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        LinearLayout name = new LinearLayout(this);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.addView(Ui.text(this, what, 15, Ui.TEXT, false));
        if (inUse) {
            ImageView check = new ImageView(this); // the standard check mark: the account in use
            check.setImageResource(ai.ibiko.R.drawable.ic_check);
            check.setColorFilter(Ui.ACCENT);
            check.setContentDescription("In use");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, ai.ibiko.IconSize.INLINE), Ui.dp(this, ai.ibiko.IconSize.INLINE));
            lp.leftMargin = Ui.dp(this, 8);
            name.addView(check, lp);
        }
        words.addView(name);
        words.addView(Ui.text(this, under, 13, Ui.SUB, false));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView edit = new ImageView(this);
        edit.setImageResource(ai.ibiko.R.drawable.ic_edit);
        edit.setColorFilter(Ui.ACCENT);
        int ep = Ui.dp(this, 12);
        edit.setPadding(ep, ep, ep, ep);
        edit.setContentDescription("Rename " + what);
        edit.setOnClickListener(v -> rename.run());
        Ui.press(edit);
        r.addView(edit, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        ImageView x = new ImageView(this);
        x.setImageResource(ai.ibiko.R.drawable.ic_close);
        x.setColorFilter(Ui.ACCENT);
        int p = Ui.dp(this, 12);
        x.setPadding(p, p, p, p);
        x.setContentDescription("Remove " + what);
        x.setOnClickListener(v -> remove.run());
        Ui.press(x);
        r.addView(x, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        return r;
    }

    private android.view.View toggle(String title, String about, final String key, boolean def) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12)); // as in Settings: air around each switch
        r.setMinimumHeight(Ui.dp(this, 56));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(0, 0, Ui.dp(this, 16), 0);
        words.addView(Ui.text(this, title, 16, Ui.TEXT, false));
        words.addView(Ui.text(this, about, 13, Ui.SUB, false), Ui.wide(this, 4));
        r.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        final android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setContentDescription(title);
        sw.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{Ui.CORAL, Ui.FIELD}));
        sw.setOnCheckedChangeListener((b, on) -> {
            if (on && "social_auto".equals(key) && !prefs.getBoolean(key, false)) {
                // posting without a yes is a big step: a stray tap on the row turned it on unasked
                b.setChecked(false);
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Post without asking?")
                        .setMessage("Posts and comments you ask for will go out from your account at once, without showing"
                                + " them to you first. Still only what you ask for, within the daily limits.")
                        .setPositiveButton("Turn on", (d, w) -> {
                            prefs.edit().putBoolean(key, true).apply();
                            fill();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
            prefs.edit().putBoolean(key, on).apply();
            if ("social_campaigns".equals(key)) {
                b.post(this::fill); // the campaigns list shows or hides with its switch
            }
        });
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        Ui.press(r);
        return r;
    }
}
