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
            labels.add("Change the date and time");
        }
        if (it.state == Campaign.State.FAILED) {
            labels.add("Try posting it again");
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
                    } else if (l.equals("Change the date and time")) {
                        when(a, c, it, redraw);
                    } else if (l.equals("Try posting it again")) {
                        Runnable again = () -> {
                            it.state = Campaign.State.PLANNED; // it goes out at the next run
                            it.result = "";
                            it.checked = 0;
                            it.at = Math.max(it.at, System.currentTimeMillis());
                            save(a, c, redraw);
                            CampaignRunner.arm(a, 0);
                        };
                        if (it.checked == -1) {
                            new androidx.appcompat.app.AlertDialog.Builder(a)
                                    .setMessage("Ibiko pressed Post on this one before but couldn't find it on your profile."
                                            + " If it did go out, posting again doubles it. Look on your profile first.")
                                    .setPositiveButton("Post it again", (d2, w2) -> again.run())
                                    .setNegativeButton("Keep it", null)
                                    .show();
                        } else {
                            again.run();
                        }
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

    /** A new date, then a new time, for a post still to go out; never in the past. */
    static void when(final Activity a, final Campaign c, final Campaign.Item it, final Runnable redraw) {
        final java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(Math.max(it.at, System.currentTimeMillis()));
        android.app.DatePickerDialog d = new android.app.DatePickerDialog(a, (v, y, m, day) -> {
            cal.set(y, m, day);
            new android.app.TimePickerDialog(a, (v2, h, min) -> {
                cal.set(java.util.Calendar.HOUR_OF_DAY, h);
                cal.set(java.util.Calendar.MINUTE, min);
                cal.set(java.util.Calendar.SECOND, 0);
                long at = cal.getTimeInMillis();
                if (at < System.currentTimeMillis()) {
                    Toast.makeText(a, "That time has passed: pick a later one.", Toast.LENGTH_SHORT).show();
                    return;
                }
                it.at = at;
                if (it.state == Campaign.State.FAILED) {
                    it.state = Campaign.State.PLANNED; // a new time is a new try
                    it.result = "";
                }
                c.items.sort((x, y2) -> Long.compare(x.at, y2.at));
                save(a, c, redraw);
                CampaignRunner.arm(a, 0);
            }, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE),
                    android.text.format.DateFormat.is24HourFormat(a)).show();
        }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH));
        d.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        d.show();
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

}
