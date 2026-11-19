package ai.ibiko.chat.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialWeb;
import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignDraft;
import ai.ibiko.social.campaign.CampaignPlanner;
import ai.ibiko.social.campaign.LeadSender;

/**
 * A new campaign's brief, on a screen of its own: each part named above its box (the name stays when the box is
 * filled), roomy boxes that grow for long answers, the accounts to post from, how long and how often, and "Plan it"
 * always in reach at the bottom. What was typed survives turning the phone. RESULT_OK means the brief was handed to
 * the brain ({@link CampaignDraft#offer}).
 */
public final class CampaignFormActivity extends AppCompatActivity {
    private static final int ID_BASE = 0x7f0c1000; // fixed ids: Android keeps each box's words across a rotation

    private final List<SocialAccounts.Account> usable = new ArrayList<SocialAccounts.Account>();
    private final List<CheckBox> picks = new ArrayList<CheckBox>();
    private EditText business, offer, audience, goal, tone, link, days, perDay;
    private CheckBox engage, prospect, autoPitch;
    private int nextId = ID_BASE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        for (SocialAccounts.Account a : SocialWeb.accounts(this).all()) {
            if (a.signedIn && (a.platform == Platform.X || a.platform == Platform.FACEBOOK)) {
                usable.add(a);
            }
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 4));
        android.widget.ImageView close = Ui.icon(this, ai.ibiko.R.drawable.ic_close, Ui.ACCENT, ai.ibiko.IconSize.STANDARD);
        close.setContentDescription("Cancel");
        int cp = Ui.dp(this, 12);
        close.setPadding(cp, cp, cp, cp);
        close.setOnClickListener(v -> finish());
        Ui.press(close);
        head.addView(close, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        TextView title = Ui.text(this, "New campaign", 19, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        head.addView(new android.view.View(this), new LinearLayout.LayoutParams(Ui.dp(this, 48), 1));
        root.addView(head);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, Ui.dp(this, 4), pad, Ui.dp(this, 24));
        box.addView(Ui.text(this, "Tell Ibiko about the business and what you want. The more you say, the better the posts."
                + " Nothing is posted until you approve the plan.", 14, Ui.SUB, false));

        business = big(box, "Your business", "Name, what you do, where you are. E.g. Ada's Bakery, custom cakes in Lekki, Lagos", 3);
        offer = big(box, "What this campaign promotes", "Products, prices, a sale, what makes it worth buying", 4);
        audience = big(box, "Who it's for", "E.g. young professionals in Lagos who shop online", 2);
        goal = big(box, "The goal", "Orders, enquiries, sign-ups, visits…", 2);
        tone = big(box, "Tone", "Friendly, expert, playful…", 1);
        link = label(box, "Link or contact (optional)", Ui.field(this, "A website or wa.me link", "", false, false));

        box.addView(Ui.text(this, "Post from", 15, Ui.TEXT, true), Ui.wide(this, 20));
        if (usable.isEmpty()) {
            box.addView(Ui.text(this, "Sign in to a Facebook or X account on Social accounts first.", 14, Ui.SUB, false), Ui.wide(this, 4));
        }
        for (SocialAccounts.Account a : usable) {
            picks.add(check(box, a.label + " (" + a.platform.shown + ")", a.inUse));
        }

        box.addView(Ui.text(this, "How long and how often", 15, Ui.TEXT, true), Ui.wide(this, 20));
        LinearLayout pair = new LinearLayout(this);
        pair.setOrientation(LinearLayout.HORIZONTAL);
        days = number("7");
        perDay = number("1");
        pair.addView(labelled("Days (1–" + CampaignPlanner.MOST_DAYS + ")", days), new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1f);
        right.leftMargin = Ui.dp(this, 12);
        pair.addView(labelled("Posts a day per account (1–" + CampaignPlanner.MOST_PER_DAY + ")", perDay), right);
        box.addView(pair, Ui.wide(this, 4));

        box.addView(Ui.text(this, "Beyond posting", 15, Ui.TEXT, true), Ui.wide(this, 20));
        engage = check(box, "Answer comments on its posts", true);
        prospect = check(box, "Find people who need this, and draft a reply for each", true);
        autoPitch = check(box, "Send those replies without asking (up to " + LeadSender.MOST_AUTO_PITCHES + " a day)", false);
        prospect.setOnCheckedChangeListener((b, on) -> autoPitch.setEnabled(on));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(box);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // the actions stay at the bottom, above the keyboard, however long the brief
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.HORIZONTAL);
        foot.setPadding(pad, Ui.dp(this, 8), pad, Ui.dp(this, 12));
        foot.setBackgroundColor(Ui.BG);
        TextView cancel = Ui.button(this, "Cancel", false);
        cancel.setOnClickListener(v -> finish());
        TextView plan = Ui.button(this, "Plan it", true);
        plan.setOnClickListener(v -> plan());
        foot.addView(cancel, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, -2, 2f);
        pl.leftMargin = Ui.dp(this, 12);
        foot.addView(plan, pl);
        root.addView(foot);

        setContentView(root);
        Ui.fitInsets(root);
    }

    /** A named box for long answers: starts {@code lines} tall and grows as the words do. */
    private EditText big(LinearLayout box, String name, String example, int lines) {
        EditText e = Ui.field(this, example, "", false, true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        e.setMinLines(Math.max(1, lines));
        e.setMaxLines(12);
        e.setHorizontallyScrolling(false);
        return label(box, name, e);
    }

    private EditText label(LinearLayout box, String name, EditText e) {
        box.addView(labelled(name, e), Ui.wide(this, 16));
        return e;
    }

    /** The part's name above its box; the name also tells TalkBack what the box is for. */
    private LinearLayout labelled(String name, EditText e) {
        e.setId(nextId++);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView l = Ui.text(this, name, 13, Ui.SUB, true);
        l.setLabelFor(e.getId());
        col.addView(l);
        col.addView(e, Ui.wide(this, 6));
        return col;
    }

    private EditText number(String value) {
        EditText e = Ui.field(this, "", value, false, false);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        return e;
    }

    private CheckBox check(LinearLayout box, String text, boolean on) {
        CheckBox cb = new CheckBox(this);
        cb.setId(nextId++);
        cb.setText(text);
        cb.setTextColor(Ui.TEXT);
        cb.setChecked(on);
        cb.setMinHeight(Ui.dp(this, 48));
        box.addView(cb);
        return cb;
    }

    private void plan() {
        Campaign c = new Campaign();
        long now = System.currentTimeMillis();
        c.id = "c" + now;
        c.createdAt = now;
        c.startAt = now;
        c.brief.business = words(business);
        c.brief.offer = words(offer);
        c.brief.audience = words(audience);
        c.brief.goal = words(goal);
        c.brief.tone = words(tone);
        c.brief.link = words(link);
        for (int i = 0; i < picks.size(); i++) {
            if (picks.get(i).isChecked()) {
                c.accounts.add(usable.get(i).id);
            }
        }
        c.days = number(days, 7, 1, CampaignPlanner.MOST_DAYS);
        c.perDay = number(perDay, 1, 1, CampaignPlanner.MOST_PER_DAY);
        c.engage = engage.isChecked();
        c.prospect = prospect.isChecked();
        c.autoPitch = prospect.isChecked() && autoPitch.isChecked();
        if (c.brief.business.isEmpty() && c.brief.offer.isEmpty()) {
            business.setError("Say what the business is, or what the campaign promotes");
            business.requestFocus();
            return; // what was typed stays
        }
        if (c.accounts.isEmpty()) {
            Toast.makeText(this, "Choose at least one account to post from.", Toast.LENGTH_LONG).show();
            return;
        }
        CampaignDraft.offer(c);
        setResult(RESULT_OK);
        finish();
    }

    private static String words(EditText e) {
        return e.getText().toString().trim();
    }

    static int number(EditText f, int fallback, int least, int most) {
        try {
            String t = f.getText().toString().trim();
            int n = t.isEmpty() ? fallback : Integer.parseInt(t);
            return Math.max(least, Math.min(most, n));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
