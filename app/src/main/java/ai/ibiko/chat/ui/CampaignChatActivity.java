package ai.ibiko.chat.ui;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignBoard;
import ai.ibiko.social.campaign.CampaignChat;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.CampaignStudio;
import ai.ibiko.social.campaign.Results;

/**
 * One campaign's studio conversation, made for social media work rather than a plain chat: the brief it was made
 * from, the planning as it goes, the plan with Review and approve, every post that went out as a feed card (account,
 * time, picture, words, responses, its link), what is coming up next, and the owner's instructions with the studio's
 * answers. The composer talks to the studio ("make them shorter", "add two posts on Friday"); the chips above it do
 * the common things in one tap.
 */
public final class CampaignChatActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "campaign";
    private String id;
    private LinearLayout thread, banner, chips;
    private ScrollView scroll;
    private TextView title, subtitle;
    private EditText box;
    private int shownEntries = -1;
    private final Runnable refresh = () -> {
        if (!isFinishing()) {
            fill(false);
        }
    };

    public static Intent open(android.content.Context c, String campaignId) {
        return new Intent(c, CampaignChatActivity.class).putExtra(EXTRA_ID, campaignId);
    }

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
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6));
        head.addView(iconButton(ai.ibiko.R.drawable.ic_arrow_back, "Back to campaigns", v -> finish()),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        title = Ui.text(this, "Campaign", 17, Ui.TEXT, true);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.text(this, "", 12, Ui.SUB, false);
        subtitle.setMaxLines(1);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(title);
        names.addView(subtitle);
        head.addView(names, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(iconButton(ai.ibiko.R.drawable.ic_more_vert, "Campaign options", v -> menu()),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(head);

        banner = new LinearLayout(this); // where the campaign stands, always in view
        banner.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        banner.setPadding(pad, 0, pad, Ui.dp(this, 8));
        root.addView(banner);

        scroll = new ScrollView(this);
        thread = new LinearLayout(this);
        thread.setOrientation(LinearLayout.VERTICAL);
        thread.setPadding(Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 16));
        scroll.addView(thread);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 2));
        chipScroll.addView(chips);
        root.addView(chipScroll);

        LinearLayout composer = new LinearLayout(this);
        composer.setGravity(Gravity.BOTTOM);
        composer.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 10));
        box = Ui.field(this, "Tell the studio what to change…", "", false, true);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMinLines(1);
        box.setMaxLines(6);
        box.setContentDescription("Instruction for the campaign studio");
        composer.addView(box, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView send = iconButton(ai.ibiko.R.drawable.ic_send, "Send to the studio", v -> send(box.getText().toString()));
        composer.addView(send, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        root.addView(composer);

        setContentView(root);
        Ui.fitInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fill(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
        thread.removeCallbacks(refresh);
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

    private Campaign campaign() {
        return id == null ? null : CampaignRunner.store(this).get(id);
    }

    /** Draws the conversation; {@code toEnd} scrolls to the newest. */
    private void fill(boolean toEnd) {
        thread.removeCallbacks(refresh);
        final Campaign c = campaign();
        if (c == null) {
            finish();
            return;
        }
        long now = System.currentTimeMillis();
        String planningLine = CampaignDraft.planning(c.id);
        boolean planning = planningLine != null, thinking = CampaignStudio.thinking(c.id);
        String state = CampaignBoard.state(c, planning, now);
        title.setText(c.name.isEmpty() ? "Campaign" : c.name);
        List<String> nets = CampaignBoard.networks(c);
        subtitle.setText(state + (nets.isEmpty() ? "" : " · " + String.join(", ", nets)));
        banner(c, planning, planningLine, now);

        List<CampaignChat.Entry> entries = CampaignStudio.chat(this, c.id).all();
        boolean grew = entries.size() != shownEntries;
        shownEntries = entries.size();
        thread.removeAllViews();
        SocialAccounts accounts = SocialWeb.accounts(this);
        Results results = CampaignRunner.results(this);
        // the conversation and the posts that went out, in time order
        List<Object[]> line = new ArrayList<Object[]>();
        for (CampaignChat.Entry e : entries) {
            line.add(new Object[]{e.at, e});
        }
        for (Campaign.Item it : c.items) {
            if (it.state != Campaign.State.PLANNED) {
                line.add(new Object[]{it.at, it});
            }
        }
        line.sort((x, y) -> Long.compare((Long) x[0], (Long) y[0]));
        String lastDay = "";
        DateFormat dayHead = new java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault());
        for (Object[] o : line) {
            String d = dayHead.format(new java.util.Date((Long) o[0]));
            if (!d.equals(lastDay)) {
                lastDay = d;
                TextView dh = Ui.text(this, d, 12, Ui.SUB, true);
                dh.setGravity(Gravity.CENTER);
                thread.addView(dh, Ui.wide(this, 14));
            }
            if (o[1] instanceof CampaignChat.Entry) {
                thread.addView(entry(c, (CampaignChat.Entry) o[1]), Ui.wide(this, 8));
            } else {
                thread.addView(postCard(c, (Campaign.Item) o[1], accounts, results), Ui.wide(this, 8));
            }
        }
        // what goes out next
        if (c.status == Campaign.Status.ACTIVE || c.status == Campaign.Status.PAUSED) {
            List<Campaign.Item> next = new ArrayList<Campaign.Item>();
            for (Campaign.Item it : c.items) {
                if (it.state == Campaign.State.PLANNED && next.size() < 2) {
                    next.add(it);
                }
            }
            if (!next.isEmpty()) {
                thread.addView(Ui.text(this, "Coming up", 13, Ui.SUB, true), Ui.wide(this, 18));
                for (Campaign.Item it : next) {
                    thread.addView(postCard(c, it, accounts, results), Ui.wide(this, 8));
                }
            }
        }
        if (thinking) {
            thread.addView(studioBubble("Working on it…", true), Ui.wide(this, 8));
        }
        chips(c, planning);
        if (toEnd || grew) {
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        }
        if (planning || thinking) {
            thread.postDelayed(refresh, 2000); // planning and answers show as they come
        }
    }

    /** Where the campaign stands, under the title. */
    private void banner(final Campaign c, boolean planning, String planningLine, long now) {
        banner.removeAllViews();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, 0, 16));
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.ACCENT));
        if (planning) {
            card.addView(Ui.text(this, planningLine, 14, Ui.TEXT, false));
            bar.setIndeterminate(true);
            card.addView(bar, Ui.wide(this, 6));
        } else if (c.status == Campaign.Status.DRAFT) {
            if (c.items.isEmpty()) {
                card.addView(Ui.text(this, "Not planned yet. Say “plan it” below to plan it.", 14, Ui.TEXT, false));
            } else {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.addView(Ui.text(this, c.items.size() + " posts ready for your review", 14, Ui.TEXT, false),
                        new LinearLayout.LayoutParams(0, -2, 1f));
                TextView go = Ui.button(this, "Review", true);
                go.setTextSize(14);
                go.setOnClickListener(v -> CampaignReview.show(this, c, () -> fill(true)));
                row.addView(go);
                card.addView(row);
            }
        } else {
            int posted = CampaignBoard.posted(c);
            bar.setMax(Math.max(1, c.items.size()));
            bar.setProgress(posted);
            card.addView(bar);
            Campaign.Item next = CampaignBoard.next(c);
            DateFormat when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
            card.addView(Ui.text(this, posted + " of " + c.items.size() + " posted"
                    + (c.status == Campaign.Status.PAUSED ? " · paused"
                    : next != null ? " · next " + when.format(new java.util.Date(next.at)) : " · all out"), 13, Ui.SUB, false),
                    Ui.wide(this, 4));
        }
        banner.addView(card);
    }

    /** The chips: the common next steps for where the campaign stands. */
    private void chips(final Campaign c, boolean planning) {
        chips.removeAllViews();
        List<Object[]> list = new ArrayList<Object[]>();
        if (planning) {
            list.add(new Object[]{"Stop planning", (Runnable) () -> CampaignDraft.stop(c.id)});
        } else if (c.status == Campaign.Status.DRAFT && c.items.isEmpty()) {
            list.add(new Object[]{"Plan it", (Runnable) () -> send("Plan it")});
        } else if (c.status == Campaign.Status.DRAFT) {
            list.add(new Object[]{"Review and approve", (Runnable) () -> CampaignReview.show(this, c, () -> fill(true))});
            list.add(new Object[]{"Make the posts shorter", null});
            list.add(new Object[]{"More about my products", null});
            list.add(new Object[]{"Post in the evenings", null});
            list.add(new Object[]{"Friendlier tone", null});
        } else if (c.status == Campaign.Status.ACTIVE) {
            list.add(new Object[]{"How is it doing?", null});
            list.add(new Object[]{"Add 3 posts for this weekend", null});
            list.add(new Object[]{"Pause", (Runnable) () -> {
                CampaignRunner.setRunning(this, c.id, false);
                fill(false);
            }});
        } else if (c.status == Campaign.Status.PAUSED) {
            list.add(new Object[]{"Resume", (Runnable) () -> {
                CampaignRunner.setRunning(this, c.id, true);
                fill(false);
            }});
            list.add(new Object[]{"How did it do?", null});
        } else {
            list.add(new Object[]{"How did it do?", null});
            list.add(new Object[]{"Plan a new campaign like this", (Runnable) () -> startActivity(new Intent(this, CampaignsActivity.class))});
        }
        list.add(new Object[]{"All posts", (Runnable) () -> startActivity(new Intent(this, CampaignActivity.class)
                .putExtra(CampaignActivity.EXTRA_ID, c.id))});
        for (Object[] o : list) {
            final String label = (String) o[0];
            final Runnable act = (Runnable) o[1];
            TextView chip = Ui.text(this, label, 13, Ui.TEXT, false);
            chip.setBackground(Ui.round(this, Ui.SURFACE, Ui.LINE, 18));
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(Ui.dp(this, 36));
            chip.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
            chip.setOnClickListener(v -> {
                if (act != null) {
                    act.run();
                } else {
                    send(label);
                }
            });
            Ui.press(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMarginEnd(Ui.dp(this, 8));
            chips.addView(chip, lp);
        }
    }

    private void send(String said) {
        String t = said == null ? "" : said.trim();
        if (t.isEmpty()) {
            return;
        }
        box.setText("");
        CampaignStudio.instruct(this, id, t, () -> runOnUiThread(() -> {
            if (!isFinishing()) {
                fill(true);
            }
        }));
        fill(true);
    }

    /** One message of the conversation. */
    private View entry(final Campaign c, CampaignChat.Entry e) {
        if (CampaignChat.YOU.equals(e.who) && "brief".equals(e.kind)) {
            LinearLayout card = bubbleBox(true);
            card.addView(Ui.text(this, "Campaign brief", 12, Ui.ACCENT, true));
            card.addView(Ui.text(this, e.text.replaceFirst("^New social media campaign\\s*", ""), 14, Ui.TEXT, false), Ui.wide(this, 4));
            return side(card, true);
        }
        if (CampaignChat.YOU.equals(e.who)) {
            LinearLayout b = bubbleBox(true);
            b.addView(Ui.text(this, e.text, 15, Ui.TEXT, false));
            return side(b, true);
        }
        if ("note".equals(e.kind)) {
            TextView n = Ui.text(this, e.text, 12, Ui.SUB, false);
            n.setGravity(Gravity.CENTER);
            n.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
            return n;
        }
        if ("plan".equals(e.kind)) {
            LinearLayout card = bubbleBox(false);
            card.addView(Ui.text(this, "The plan is ready", 12, Ui.ACCENT, true));
            card.addView(Ui.text(this, e.text, 14, Ui.TEXT, false), Ui.wide(this, 4));
            if (c.status == Campaign.Status.DRAFT && !c.items.isEmpty()) {
                TextView go = Ui.button(this, "Review and approve", true);
                go.setTextSize(14);
                go.setOnClickListener(v -> CampaignReview.show(this, c, () -> fill(true)));
                card.addView(go, Ui.wide(this, 10));
            }
            return side(card, false);
        }
        return studioBubble(e.text, false);
    }

    private View studioBubble(String text, boolean working) {
        LinearLayout b = bubbleBox(false);
        TextView who = Ui.text(this, "Studio", 12, Ui.ACCENT, true);
        b.addView(who);
        b.addView(Ui.text(this, text, 15, working ? Ui.SUB : Ui.TEXT, false), Ui.wide(this, 2));
        return side(b, false);
    }

    private LinearLayout bubbleBox(boolean mine) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setBackground(Ui.round(this, mine ? Ui.CARD_ON : Ui.SURFACE, 0, 18));
        b.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        return b;
    }

    /** A bubble on its side: the owner's on the right, the studio's on the left, never full width. */
    private View side(View bubble, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        row.setPadding(mine ? Ui.dp(this, 48) : 0, 0, mine ? 0 : Ui.dp(this, 48), 0);
        row.addView(bubble, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    /** The colour a network is known by. */
    private static int netColor(String network) {
        switch (network == null ? "" : network) {
            case "facebook":
                return 0xFF1877F2;
            case "instagram":
                return 0xFFE1306C;
            case "linkedin":
                return 0xFF0A66C2;
            default:
                return 0xFF8A8898;
        }
    }

    /** A post as it looks in a feed: who and when, the words, the picture, then how it went and its link. */
    private View postCard(final Campaign c, final Campaign.Item it, SocialAccounts accounts, Results results) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(this, Ui.SURFACE, Ui.LINE, 18));
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        SocialAccounts.Account a = accounts.get(it.account);
        String name = it.handoff || a == null ? CampaignRunner.shown(it.network) : a.label;
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView avatar = Ui.text(this, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 15, 0xFFFFFFFF, true);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(netColor(it.network));
        avatar.setBackground(dot);
        top.addView(avatar, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36)));
        LinearLayout who = new LinearLayout(this);
        who.setOrientation(LinearLayout.VERTICAL);
        who.setPadding(Ui.dp(this, 10), 0, 0, 0);
        who.addView(Ui.text(this, name, 14, Ui.TEXT, true));
        DateFormat when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        who.addView(Ui.text(this, CampaignRunner.shown(it.network) + " · " + when.format(new java.util.Date(it.at)), 12, Ui.SUB, false));
        top.addView(who, new LinearLayout.LayoutParams(0, -2, 1f));
        String state = it.state == Campaign.State.POSTED ? (it.handoff ? "Handed to you" : it.checked == 1 ? "Posted ✓" : "Posted")
                : it.state == Campaign.State.FAILED ? "Didn't go out" : it.result.startsWith("waiting") ? "Waiting" : "Planned";
        int color = it.state == Campaign.State.POSTED ? Ui.GREEN : it.state == Campaign.State.FAILED ? Ui.AMBER : Ui.SUB;
        top.addView(Ui.text(this, state, 12, color, true));
        card.addView(top);
        TextView words = Ui.text(this, it.text, 14, Ui.TEXT, false);
        words.setMaxLines(8);
        words.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(words, Ui.wide(this, 10));
        if (!it.imagePath.isEmpty() && new java.io.File(it.imagePath).isFile()) {
            ImageView pic = new ImageView(this);
            pic.setAdjustViewBounds(true);
            pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.graphics.Bitmap b = ImageViewActivity.decode(it.imagePath, Ui.dp(this, 320));
            if (b != null) {
                pic.setImageBitmap(b);
            }
            pic.setClipToOutline(true);
            pic.setBackground(Ui.round(this, Ui.FIELD, 0, 14));
            pic.setContentDescription("The post's picture. Tap to open.");
            pic.setOnClickListener(v -> startActivity(new Intent(this, ImageViewActivity.class).putExtra("path", it.imagePath)));
            card.addView(pic, Ui.wide(this, 10));
        }
        if (it.state == Campaign.State.POSTED && !it.handoff) {
            Results.Count n = results.of(it.id);
            card.addView(Ui.text(this, n.reactions + (n.reactions == 1 ? " reaction" : " reactions") + " · " + n.talk
                    + (n.talk == 1 ? " comment or share" : " comments or shares"), 13, Ui.SUB, false), Ui.wide(this, 8));
        } else if (it.state == Campaign.State.FAILED || it.result.startsWith("waiting")) {
            TextView why = Ui.text(this, it.result.replaceFirst("^waiting:\\s*", ""), 13, Ui.SUB, false);
            why.setMaxLines(3);
            card.addView(why, Ui.wide(this, 8));
        }
        if (!it.url.isEmpty()) {
            TextView link = Ui.text(this, "Open the post on " + CampaignRunner.shown(it.network), 14, Ui.ACCENT, true);
            link.setMinHeight(Ui.dp(this, 40));
            link.setGravity(Gravity.CENTER_VERTICAL);
            link.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url)));
                } catch (RuntimeException ignored) {
                    // no browser
                }
            });
            card.addView(link);
        }
        card.setContentDescription(name + ", " + state + ". " + it.text);
        Ui.press(card);
        card.setOnClickListener(v -> PostActions.menu(this, c, it, () -> fill(false)));
        return card;
    }

    private void menu() {
        final Campaign c = campaign();
        if (c == null) {
            return;
        }
        final List<String> labels = new ArrayList<String>();
        if (c.status == Campaign.Status.DRAFT && !c.items.isEmpty()) {
            labels.add("Review and approve");
        }
        if (c.status == Campaign.Status.ACTIVE) {
            labels.add("Pause");
        }
        if (c.status == Campaign.Status.PAUSED) {
            labels.add("Resume");
        }
        labels.add("All posts");
        labels.add("Delete the campaign");
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    String l = labels.get(w);
                    if (l.equals("Review and approve")) {
                        CampaignReview.show(this, c, () -> fill(true));
                    } else if (l.equals("Pause") || l.equals("Resume")) {
                        CampaignRunner.setRunning(this, c.id, l.equals("Resume"));
                        fill(false);
                    } else if (l.equals("All posts")) {
                        startActivity(new Intent(this, CampaignActivity.class).putExtra(CampaignActivity.EXTRA_ID, c.id));
                    } else {
                        new androidx.appcompat.app.AlertDialog.Builder(this)
                                .setMessage("Delete “" + c.name + "” and its conversation? Posts already out stay on the networks.")
                                .setPositiveButton("Delete", (d2, w2) -> {
                                    CampaignRunner.delete(this, c);
                                    finish();
                                })
                                .setNegativeButton("Keep", null)
                                .show();
                    }
                })
                .show();
    }
}
