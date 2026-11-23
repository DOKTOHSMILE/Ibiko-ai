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
import ai.ibiko.social.campaign.CampaignControls;
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
    private TextView title, subtitle, modelChip;
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

        // the header as in a chat: back on the left, the campaign's name centred with where it stands, options on the right
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 6));
        head.addView(iconButton(ai.ibiko.R.drawable.ic_arrow_back, "Back to campaigns", v -> finish()),
                new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setGravity(Gravity.CENTER_HORIZONTAL);
        title = Ui.text(this, "Campaign", 17, Ui.TEXT, true);
        title.setMaxLines(1);
        title.setGravity(Gravity.CENTER);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        subtitle = Ui.text(this, "", 12, Ui.SUB, false);
        subtitle.setMaxLines(1);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        names.addView(title, new LinearLayout.LayoutParams(-1, -2));
        names.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));
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

        // the entry as in a chat: one rounded box, the words on top, pictures (+) on the left and send on the right below
        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackgroundResource(ai.ibiko.R.drawable.bg_input_container);
        composer.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        box = new EditText(this);
        box.setHint("Message the studio…");
        box.setHintTextColor(Ui.SUB);
        box.setTextColor(Ui.TEXT);
        box.setTextSize(16);
        box.setBackground(null);
        box.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMinLines(1);
        box.setMaxLines(6);
        box.setContentDescription("Instruction for the campaign studio");
        composer.addView(box, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        // the model the studio writes with, as the chat's level pill: tap to choose another
        modelChip = Ui.text(this, "", 13, Ui.SUB, false);
        modelChip.setBackgroundResource(ai.ibiko.R.drawable.bg_think);
        modelChip.setCompoundDrawablesRelativeWithIntrinsicBounds(ai.ibiko.R.drawable.ic_set_models, 0, ai.ibiko.R.drawable.ic_chevron_down, 0);
        modelChip.setCompoundDrawablePadding(Ui.dp(this, 4));
        modelChip.setGravity(Gravity.CENTER);
        modelChip.setMaxLines(1);
        modelChip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        modelChip.setMaxWidth(Ui.dp(this, 200));
        modelChip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 8), 0);
        modelChip.setOnClickListener(v -> pickModel());
        Ui.press(modelChip);
        tools.addView(modelChip, new LinearLayout.LayoutParams(-2, Ui.dp(this, 32)));
        tools.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        ImageView add = iconButton(ai.ibiko.R.drawable.ic_attach, "Add your pictures", v -> pickPictures());
        add.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8));
        tools.addView(add, new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)));
        android.widget.ImageButton send = new android.widget.ImageButton(this);
        send.setImageResource(ai.ibiko.R.drawable.ic_send);
        send.setBackgroundResource(ai.ibiko.R.drawable.bg_send_button);
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setContentDescription("Send to the studio");
        send.setOnClickListener(v -> send(box.getText().toString()));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        sl.setMarginStart(Ui.dp(this, 4));
        tools.addView(send, sl);
        composer.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 10));
        root.addView(composer, cl);

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
        CampaignStudio.Choice model = CampaignStudio.chosen(this);
        modelChip.setText(model == null ? "Choose a model" : model.shown());
        modelChip.setContentDescription("The model the studio uses: " + (model == null ? "none" : model.shown()) + ". Tap to change.");

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

    /**
     * The row over the message box, as in the chat: the main next step for where the campaign stands, then the tools:
     * Campaign (plan, review, pause, delete), Posts, Media, Results and Edit (quick changes), each a sheet or a screen.
     */
    private void chips(final Campaign c, boolean planning) {
        chips.removeAllViews();
        // the one next step, lit
        if (planning) {
            chip("Stop planning", ai.ibiko.R.drawable.ic_stop, 0, true, () -> CampaignDraft.stop(c.id));
        } else if (c.status == Campaign.Status.DRAFT && c.items.isEmpty()) {
            chip("Plan it", ai.ibiko.R.drawable.ic_mode_plan, 0, true, () -> send("Plan it"));
        } else if (c.status == Campaign.Status.DRAFT) {
            chip("Review and approve", ai.ibiko.R.drawable.ic_check, 0, true, () -> CampaignReview.show(this, c, () -> fill(true)));
        } else if (c.status == Campaign.Status.ACTIVE) {
            chip("How is it doing?", ai.ibiko.R.drawable.ic_usage, 0, true, () -> send("How is it doing?"));
        } else if (c.status == Campaign.Status.PAUSED) {
            chip("Resume", ai.ibiko.R.drawable.ic_play, 0, true, () -> {
                CampaignRunner.setRunning(this, c.id, true);
                fill(false);
            });
        }
        chip("Campaign", ai.ibiko.R.drawable.ic_tools_chip, ai.ibiko.R.drawable.ic_chevron_down, false, () -> campaignSheet(c, planning));
        chip("Posts", ai.ibiko.R.drawable.ic_media, ai.ibiko.R.drawable.ic_chevron_right, false,
                () -> startActivity(new Intent(this, CampaignActivity.class).putExtra(CampaignActivity.EXTRA_ID, c.id)));
        chip("Media", ai.ibiko.R.drawable.ic_image, ai.ibiko.R.drawable.ic_chevron_right, false, this::pickPictures);
        if (c.status != Campaign.Status.ACTIVE) {
            chip("Results", ai.ibiko.R.drawable.ic_usage, ai.ibiko.R.drawable.ic_chevron_right, false, () -> send("How did it do?"));
        }
        chip("Edit", ai.ibiko.R.drawable.ic_edit, ai.ibiko.R.drawable.ic_chevron_down, false, () -> editSheet(c));
    }

    /** One chip in the row: an icon, the words, and a chevron (a sheet opens) or an arrow (a screen opens). */
    private void chip(String label, int icon, int end, boolean lit, Runnable act) {
        TextView chip = Ui.text(this, label, 13, lit ? Ui.ACCENT : Ui.TEXT, lit);
        chip.setBackgroundResource(ai.ibiko.R.drawable.bg_chip);
        chip.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, end, 0);
        chip.setCompoundDrawablePadding(Ui.dp(this, 5));
        chip.setGravity(Gravity.CENTER);
        chip.setMinWidth(Ui.dp(this, 48));
        chip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, end == 0 ? 12 : 8), 0);
        chip.setContentDescription(label);
        chip.setOnClickListener(v -> act.run());
        Ui.press(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 34));
        lp.setMarginEnd(Ui.dp(this, 6));
        chips.addView(chip, lp);
    }

    /**
     * Campaign ▾: everything that can be done with the campaign as a whole, for where it stands. The posting controls
     * (now, skip, retry, move) act on the campaign at once, offline; the line under the title says where it stands.
     */
    private void campaignSheet(final Campaign c, boolean planning) {
        final List<BottomSheet.Item> items = new ArrayList<BottomSheet.Item>();
        final List<Runnable> acts = new ArrayList<Runnable>();
        final long now = System.currentTimeMillis();
        if (planning) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_stop, "Stop planning", "Keep what is planned so far"));
            acts.add(() -> CampaignDraft.stop(c.id));
        } else if (c.status == Campaign.Status.DRAFT) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_mode_plan, c.items.isEmpty() ? "Plan it" : "Plan it again",
                    "The strategy, every post with its words and picture"));
            acts.add(() -> send("Plan it"));
            if (!c.items.isEmpty()) {
                items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_check, "Review and approve", "Read, change and approve each post"));
                acts.add(() -> CampaignReview.show(this, c, () -> fill(true)));
            }
        }
        boolean waiting = CampaignControls.next(c) != null;
        if (waiting && c.status != Campaign.Status.DRAFT) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_send, "Post the next one now", "Don't wait for its time"));
            acts.add(() -> change(x -> CampaignControls.postNextNow(x, System.currentTimeMillis())));
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_close, "Skip the next post", "The others keep their times"));
            acts.add(() -> change(CampaignControls::skipNext));
        }
        int failed = CampaignControls.failed(c);
        if (failed > 0) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_retry, "Retry the failed posts (" + failed + ")",
                    "Back in line, a quarter of an hour apart"));
            acts.add(() -> change(x -> {
                int n = CampaignControls.retryFailed(x, System.currentTimeMillis());
                return n == 0 ? null : n + (n == 1 ? " failed post is" : " failed posts are") + " back in line, starting in 15 minutes.";
            }));
        }
        if (waiting) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_calendar, "Move the schedule", "Everything still to go: later or earlier"));
            acts.add(() -> moveSheet());
        }
        if (c.status == Campaign.Status.ACTIVE) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_pause, "Pause", "Nothing more goes out until you resume"));
            acts.add(() -> {
                CampaignRunner.setRunning(this, c.id, false);
                fill(false);
            });
        }
        if (c.status == Campaign.Status.PAUSED) {
            items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_play, "Resume", "Posting carries on where it stopped"));
            acts.add(() -> {
                CampaignRunner.setRunning(this, c.id, true);
                fill(false);
            });
        }
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_settings, "What it does besides posting",
                "Answer comments, find people, pitch, WhatsApp Status"));
        acts.add(() -> settings(c));
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_share, "Share all the posts", "As text: to keep, check or send"));
        acts.add(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, c.name).putExtra(Intent.EXTRA_TEXT, CampaignControls.export(c)), "Share the posts")));
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_edit, "Rename", c.name.isEmpty() ? "Give it a name" : c.name));
        acts.add(() -> rename(c));
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_copy, "Run it again", "A copy with the same brief, planned fresh"));
        acts.add(() -> duplicate(c));
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_set_models, "Model", "Which model plans and writes"));
        acts.add(this::pickModel);
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_plus, "New campaign", "Start another from a brief"));
        acts.add(() -> startActivity(new Intent(this, CampaignsActivity.class)));
        items.add(new BottomSheet.Item(ai.ibiko.R.drawable.ic_delete, "Delete the campaign", "Posts already out stay on the networks"));
        acts.add(() -> confirmDelete(c));
        BottomSheet.show(this, "Campaign", c.items.isEmpty() ? null : CampaignControls.standing(c, now),
                items.toArray(new BottomSheet.Item[0]), -1, which -> acts.get(which).run());
    }

    /**
     * A change made on the campaign itself: done, saved, the runner re-armed, and said in the conversation (so the
     * campaign's story keeps it). {@code how} says what it did, or null when there was nothing to do.
     */
    private void change(java.util.function.Function<Campaign, String> how) {
        Campaign c = campaign();
        if (c == null) {
            return;
        }
        String said = how.apply(c);
        if (said == null) {
            android.widget.Toast.makeText(this, "Nothing to change there.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            CampaignRunner.store(this).put(c);
        } catch (java.io.IOException e) {
            android.widget.Toast.makeText(this, "That couldn't be saved: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        CampaignRunner.arm(this, 0);
        CampaignStudio.chat(this, c.id).add(CampaignChat.STUDIO, "note", said, System.currentTimeMillis());
        fill(true);
    }

    /** Move the schedule: everything still to go, later or earlier. */
    private void moveSheet() {
        final String[][] moves = {
            {"An hour later", "3600000"}, {"Three hours later", "10800000"}, {"A day later", "86400000"},
            {"An hour earlier", "-3600000"}, {"A day earlier", "-86400000"}};
        BottomSheet.Item[] items = new BottomSheet.Item[moves.length];
        for (int i = 0; i < moves.length; i++) {
            items[i] = new BottomSheet.Item(ai.ibiko.R.drawable.ic_calendar, moves[i][0],
                    moves[i][1].startsWith("-") ? "Never into the past" : "Every post still to go");
        }
        BottomSheet.show(this, "Move the schedule", items, which -> change(x -> {
            int n = CampaignControls.shift(x, Long.parseLong(moves[which][1]), System.currentTimeMillis());
            return n == 0 ? null : "Moved " + n + (n == 1 ? " post " : " posts ") + moves[which][0].toLowerCase(java.util.Locale.ROOT) + ".";
        }));
    }

    /** What the campaign does besides posting: each on or off. */
    private void settings(final Campaign c) {
        final String[] labels = {"Answer comments on its posts", "Find people who need it", "Pitch them on its own (up to 5 a day)",
            "Offer each post for WhatsApp Status"};
        final boolean[] on = {c.engage, c.prospect, c.autoPitch, c.whatsappShare};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Besides posting")
                .setMultiChoiceItems(labels, on, (d, w, checked) -> on[w] = checked)
                .setPositiveButton("Save", (d, w) -> change(x -> {
                    x.engage = on[0];
                    x.prospect = on[1];
                    x.autoPitch = on[2];
                    x.whatsappShare = on[3];
                    List<String> said = new ArrayList<String>();
                    for (int i = 0; i < labels.length; i++) {
                        said.add((on[i] ? "on: " : "off: ") + labels[i].toLowerCase(java.util.Locale.ROOT));
                    }
                    return "Settings changed: " + String.join("; ", said) + ".";
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void rename(final Campaign c) {
        final EditText field = new EditText(this);
        field.setText(c.name);
        field.setSelection(field.getText().length());
        field.setSingleLine(true);
        int p = Ui.dp(this, 20);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(p, Ui.dp(this, 8), p, 0);
        wrap.addView(field);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Rename the campaign")
                .setView(wrap)
                .setPositiveButton("Rename", (d, w) -> {
                    final String name = CampaignControls.name(field.getText().toString());
                    change(x -> {
                        if (name == null || name.equals(x.name)) {
                            return null;
                        }
                        x.name = name;
                        return "Renamed to “" + name + "”.";
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Run it again: a copy, planned fresh, opened at once. */
    private void duplicate(final Campaign c) {
        Campaign copy = CampaignControls.duplicate(c, System.currentTimeMillis());
        try {
            CampaignRunner.store(this).put(copy);
        } catch (java.io.IOException e) {
            android.widget.Toast.makeText(this, "The copy couldn't be saved: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        CampaignStudio.chat(this, copy.id).add(CampaignChat.STUDIO, "note", "A copy of “" + c.name
                + "”, with the same brief and accounts. Say “plan it” to plan it fresh.", System.currentTimeMillis());
        startActivity(open(this, copy.id));
        finish();
    }

    /** Edit ▾: quick changes, sent to the studio as if typed. */
    private void editSheet(final Campaign c) {
        final String[][] edits = {
            {"Make the posts shorter", "Fewer words in every post"},
            {"Friendlier tone", "Warmer, more personal words"},
            {"More about my products", "Name and show what you sell"},
            {"Post in the evenings", "Move the times to the evening"},
            {"Add 3 posts for this weekend", "More posts on Saturday and Sunday"},
            {"Add two more days", "Keep it running longer, with new posts"},
            {"Add a clear call to action", "Tell people what to do: message, call, buy"},
            {"Add hashtags", "A few that fit each network"},
            {"More emojis", "Livelier posts"},
            {"No emojis", "Plain, professional posts"},
            {"More professional", "Expert, confident wording"},
            {"Write in Nigerian Pidgin", "For a local, friendly audience"},
            {"New pictures", "Draw the pictures again"}};
        BottomSheet.Item[] items = new BottomSheet.Item[edits.length];
        for (int i = 0; i < edits.length; i++) {
            items[i] = new BottomSheet.Item(ai.ibiko.R.drawable.ic_edit, edits[i][0], edits[i][1]);
        }
        BottomSheet.show(this, "Edit the campaign", items, which -> send(edits[which][0]));
    }

    private void confirmDelete(final Campaign c) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setMessage("Delete “" + c.name + "” and its conversation? Posts already out stay on the networks.")
                .setPositiveButton("Delete", (d2, w2) -> {
                    CampaignRunner.delete(this, c);
                    finish();
                })
                .setNegativeButton("Keep", null)
                .show();
    }

    /** Several pictures from the phone at once (the system photo picker: no permission asked). */
    private final androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> picker =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(30),
                    this::picked);

    private void pickPictures() {
        picker.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
    }

    /** The pictures picked: kept with the campaign and put on its next posts, as they are; the studio says which. */
    private void picked(java.util.List<android.net.Uri> uris) {
        if (uris == null || uris.isEmpty() || id == null) {
            return;
        }
        final android.content.Context app = getApplicationContext();
        final java.util.List<android.net.Uri> chosen = new java.util.ArrayList<android.net.Uri>(uris);
        android.widget.Toast.makeText(this, "Adding " + chosen.size() + (chosen.size() == 1 ? " picture…" : " pictures…"),
                android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            java.util.List<String> files = ai.ibiko.social.campaign.CampaignMedia.bring(app, id, chosen);
            String said;
            synchronized (CampaignRunner.class) {
                Campaign c = CampaignRunner.store(app).get(id);
                if (c == null) {
                    return;
                }
                said = files.isEmpty() ? "Those pictures couldn't be read." : ai.ibiko.social.campaign.CampaignMedia.place(c, files);
                try {
                    CampaignRunner.store(app).put(c);
                } catch (java.io.IOException e) {
                    said = "The pictures couldn't be kept: " + e.getMessage();
                }
            }
            ai.ibiko.social.campaign.CampaignStudio.chat(app, id).add(ai.ibiko.social.campaign.CampaignChat.STUDIO, "note", said,
                    System.currentTimeMillis());
            runOnUiThread(() -> {
                if (!isFinishing()) {
                    fill(true);
                }
            });
        }, "ibiko-campaign-pictures").start();
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

    /** The model the studio plans and edits with: any API model set up in AI providers. */
    private void pickModel() {
        final List<CampaignStudio.Choice> all = CampaignStudio.choices(this);
        if (all.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("No API model yet")
                    .setMessage("The studio plans and writes with an API model. Add one (a key from OpenAI, Google, Anthropic,"
                            + " DeepSeek or another) in AI providers, then pick it here.")
                    .setPositiveButton("Open AI providers", (d, w) -> startActivity(new Intent(this, ProvidersActivity.class)))
                    .setNegativeButton("Not now", null)
                    .show();
            return;
        }
        CampaignStudio.Choice now = CampaignStudio.chosen(this);
        String[] labels = new String[all.size()];
        int at = 0;
        for (int i = 0; i < all.size(); i++) {
            CampaignStudio.Choice ch = all.get(i);
            labels[i] = ch.model + "  \u00B7  " + ch.provider.title;
            if (now != null && ch.provider == now.provider && ch.model.equals(now.model)) {
                at = i;
            }
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Model for campaigns")
                .setSingleChoiceItems(labels, at, (d, w) -> {
                    CampaignStudio.choose(this, all.get(w));
                    d.dismiss();
                    fill(false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** ⋮: the same Campaign sheet as the chip, everything in one place. */
    private void menu() {
        final Campaign c = campaign();
        if (c != null) {
            campaignSheet(c, CampaignDraft.planning(c.id) != null);
        }
    }
}
