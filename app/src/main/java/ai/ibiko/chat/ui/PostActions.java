package ai.ibiko.chat.ui;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.PlatformRules;

/**
 * What can be done with one planned post, from the plan's review and from a running campaign's posts: see its
 * picture, change its words, take it out, share it from an app, or post it although its network's rules are against
 * it. A change is saved at once; a post that already went out can only be shared.
 */
final class PostActions {
    private PostActions() {
    }

    static void menu(final Activity a, final Campaign c, final Campaign.Item it, final Runnable redraw) {
        final List<String> labels = new ArrayList<String>();
        boolean planned = it.state == Campaign.State.PLANNED || it.state == Campaign.State.FAILED;
        if (!it.imageHtml.isEmpty()) {
            labels.add("See its picture");
        }
        if (planned) {
            labels.add("Edit the words");
        }
        if (planned && !it.riskOk && PlatformRules.banned(it.network, it.text)) {
            labels.add("Post it anyway");
        }
        if (it.handoff) {
            labels.add("Share to " + CampaignRunner.shown(it.network) + " now");
        }
        labels.add("Share to WhatsApp Status or a group");
        if (planned) {
            labels.add("Take this post out");
        }
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    String l = labels.get(which);
                    if (l.equals("See its picture")) {
                        a.startActivity(new Intent(a, HtmlViewActivity.class).putExtra(HtmlViewActivity.EXTRA_HTML,
                                CampaignRunner.catalog(a).inline(it.imageHtml)));
                    } else if (l.equals("Edit the words")) {
                        edit(a, c, it, redraw);
                    } else if (l.equals("Post it anyway")) {
                        new androidx.appcompat.app.AlertDialog.Builder(a)
                                .setMessage(PlatformRules.said(PlatformRules.check(it.network, it.text))
                                        + "\n\nThe network may take it down and restrict the account. Post it anyway?")
                                .setPositiveButton("Post it anyway", (d2, w2) -> {
                                    it.riskOk = true;
                                    if (it.state == Campaign.State.FAILED) {
                                        it.state = Campaign.State.PLANNED; // held back: it goes out at the next run
                                        it.result = "";
                                        it.at = Math.max(it.at, System.currentTimeMillis());
                                    }
                                    save(a, c, redraw);
                                    CampaignRunner.arm(a, 0);
                                })
                                .setNegativeButton("Keep it held", null)
                                .show();
                    } else if (l.startsWith("Share to WhatsApp")) {
                        a.startActivity(PostShareActivity.intent(a, c.id, it.id, "whatsapp"));
                    } else if (l.startsWith("Share to ")) {
                        a.startActivity(PostShareActivity.intent(a, c.id, it.id, it.network));
                    } else {
                        c.items.remove(it);
                        save(a, c, redraw);
                    }
                })
                .show();
    }

    /** The words in a roomy box; saved with the rules checked again. */
    static void edit(final Activity a, final Campaign c, final Campaign.Item it, final Runnable redraw) {
        final EditText box = Ui.field(a, "The post", it.text, false, true);
        box.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        box.setMinLines(6);
        box.setMaxLines(14);
        LinearLayout wrap = new LinearLayout(a);
        int p = Ui.dp(a, 20);
        wrap.setPadding(p, Ui.dp(a, 8), p, 0);
        wrap.addView(box, new LinearLayout.LayoutParams(-1, -2));
        final int most = "x".equals(it.network) ? 280 : 2200;
        new androidx.appcompat.app.AlertDialog.Builder(a)
                .setTitle("Edit the post")
                .setView(wrap)
                .setPositiveButton("Save", (d, w) -> {
                    String t = box.getText().toString().trim();
                    if (t.isEmpty()) {
                        Toast.makeText(a, "A post needs words. To drop it, take it out instead.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (t.length() > most) {
                        Toast.makeText(a, CampaignRunner.shown(it.network) + " takes " + most + " characters; this is "
                                + t.length() + ". It was cut to fit.", Toast.LENGTH_LONG).show();
                        t = t.substring(0, most);
                    }
                    it.text = t;
                    it.risk = PlatformRules.said(PlatformRules.check(it.network, t));
                    it.riskOk = false; // the new words are checked afresh
                    save(a, c, redraw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    static void save(Activity a, Campaign c, Runnable redraw) {
        try {
            CampaignRunner.store(a).put(c);
        } catch (IOException e) {
            Toast.makeText(a, "The change couldn't be saved.", Toast.LENGTH_SHORT).show();
        }
        if (redraw != null) {
            redraw.run();
        }
    }

    /** How many posts of {@code c} still planned break a rule their network bans, and weren't let through. */
    static int held(Campaign c) {
        int n = 0;
        for (Campaign.Item it : c.items) {
            if (it.state == Campaign.State.PLANNED && !it.riskOk && PlatformRules.banned(it.network, it.text)) {
                n++;
            }
        }
        return n;
    }
}
