package ai.ibiko.chat.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.List;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.Results;

/**
 * A campaign's posts as a grid of their pictures, three across, like a profile: many posts at a glance, each with
 * its day and time and a dot for how it went (green out, amber didn't go out, grey still to go). A tap opens the post
 * full size: the picture, every word, when and where, its responses and link, and what can be done with it.
 */
final class PostGrid {
    private PostGrid() {
    }

    static final int ACROSS = 3;

    /** A square that takes its width as its height. */
    static final class Square extends FrameLayout {
        Square(android.content.Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            super.onMeasure(widthSpec, widthSpec);
        }
    }

    /** The grid of {@code items} (in time order), added to {@code into}. */
    static void add(final Activity a, final Campaign c, List<Campaign.Item> items, final Results results, LinearLayout into,
            final Runnable redraw) {
        DateFormat when = new java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault());
        int gap = Ui.dp(a, 4);
        LinearLayout row = null;
        for (int i = 0; i < items.size(); i++) {
            if (i % ACROSS == 0) {
                row = new LinearLayout(a);
                into.addView(row, Ui.wide(a, i == 0 ? 8 : 0));
            }
            final Campaign.Item it = items.get(i);
            View tile = tile(a, it, when.format(new java.util.Date(it.at)));
            tile.setOnClickListener(v -> open(a, c, it, results, redraw));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            lp.setMargins(i % ACROSS == 0 ? 0 : gap / 2, 0, i % ACROSS == ACROSS - 1 ? 0 : gap / 2, gap);
            row.addView(tile, lp);
        }
        if (row != null) {
            for (int k = items.size() % ACROSS; k > 0 && k < ACROSS; k++) {
                row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1f)); // the last row keeps the grid's widths
            }
        }
    }

    /** The colour of a post's state dot. */
    static int stateColor(Campaign.Item it) {
        return it.state == Campaign.State.POSTED ? Ui.GREEN : it.state == Campaign.State.FAILED || it.checked == -1 ? Ui.AMBER : Ui.SUB;
    }

    /** A post's state in words. */
    static String state(Campaign.Item it) {
        return it.state == Campaign.State.POSTED ? (it.handoff ? "Handed to you" : it.checked == 1 ? "Posted ✓" : "Posted")
                : it.state == Campaign.State.FAILED ? "Didn't go out" : it.state == Campaign.State.SKIPPED ? "Skipped (a repeat)"
                : it.result.startsWith("waiting") ? "Waiting" : "Planned";
    }

    private static View tile(Activity a, Campaign.Item it, String when) {
        Square sq = new Square(a);
        sq.setBackground(Ui.round(a, Ui.SURFACE, 0, 10));
        sq.setClipToOutline(true);
        ImageView pic = new ImageView(a);
        pic.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sq.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        String head = it.headline.isEmpty() ? it.text : it.headline;
        TextView words = Ui.text(a, head.length() > 60 ? head.substring(0, 60) + "…" : head, 12, Ui.TEXT, true);
        words.setGravity(Gravity.CENTER);
        int p = Ui.dp(a, 8);
        words.setPadding(p, p, p, p);
        sq.addView(words, new FrameLayout.LayoutParams(-1, -1));
        TextView label = Ui.text(a, when, 10, 0xFFFFFFFF, true);
        label.setPadding(Ui.dp(a, 6), Ui.dp(a, 3), Ui.dp(a, 6), Ui.dp(a, 3));
        label.setBackgroundColor(0x99000000);
        label.setSingleLine(true);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        sq.addView(label, lp);
        View dot = new View(a);
        dot.setBackground(Ui.round(a, stateColor(it), 0, 6));
        int d = Ui.dp(a, 10);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(d, d, Gravity.TOP | Gravity.END);
        dp.setMargins(0, Ui.dp(a, 6), Ui.dp(a, 6), 0);
        sq.addView(dot, dp);
        sq.setContentDescription(when + ", " + state(it) + ". " + it.text);
        Ui.press(sq);
        thumb(a, pic, words, it.imagePath, 360);
        return sq;
    }

    /** The picture, decoded small off the main thread; the headline stays shown until (unless) there is one. */
    static void thumb(final Activity a, final ImageView into, final View instead, final String path, final int side) {
        if (path == null || path.isEmpty() || !new File(path).isFile()) {
            return;
        }
        new Thread(() -> {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int scale = 1;
            while (o.outWidth / (scale * 2) >= side && o.outHeight / (scale * 2) >= side) {
                scale *= 2;
            }
            BitmapFactory.Options load = new BitmapFactory.Options();
            load.inSampleSize = scale;
            final Bitmap b = BitmapFactory.decodeFile(path, load);
            if (b != null) {
                a.runOnUiThread(() -> {
                    into.setImageBitmap(b);
                    if (instead != null) {
                        instead.setVisibility(View.GONE);
                    }
                });
            }
        }, "ibiko-post-thumb").start();
    }

    /** One post, full size, with what can be done with it. */
    static void open(final Activity a, final Campaign c, final Campaign.Item it, Results results, final Runnable redraw) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        Square sq = new Square(a);
        sq.setBackground(Ui.round(a, Ui.SURFACE, 0, 14));
        sq.setClipToOutline(true);
        final ImageView pic = new ImageView(a);
        pic.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sq.addView(pic, new FrameLayout.LayoutParams(-1, -1));
        final TextView drawing = Ui.text(a, it.imagePath.isEmpty() && !it.imageHtml.isEmpty() ? "Drawing the picture…" : "No picture", 13, Ui.SUB, false);
        drawing.setGravity(Gravity.CENTER);
        sq.addView(drawing, new FrameLayout.LayoutParams(-1, -1));
        box.addView(sq);
        if (!it.imagePath.isEmpty() && new File(it.imagePath).isFile()) {
            thumb(a, pic, drawing, it.imagePath, 1080);
        } else if (!it.imageHtml.isEmpty()) {
            final android.content.Context app = a.getApplicationContext();
            new Thread(() -> {
                File f = CampaignRunner.pictureOf(app, c, it); // drawn now, kept for the post
                a.runOnUiThread(() -> {
                    if (f != null) {
                        thumb(a, pic, drawing, f.getPath(), 1080);
                    } else {
                        drawing.setText("The picture couldn't be drawn");
                    }
                });
            }, "ibiko-post-draw").start();
        }
        DateFormat when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        TextView meta = Ui.text(a, when.format(new java.util.Date(it.at)) + " · " + CampaignRunner.shown(it.network) + " · " + state(it),
                13, stateColor(it), true);
        box.addView(meta, Ui.wide(a, 12));
        TextView words = Ui.text(a, it.text, 15, Ui.TEXT, false);
        words.setTextIsSelectable(true);
        box.addView(words, Ui.wide(a, 8));
        if (it.state == Campaign.State.POSTED && !it.handoff) {
            Results.Count n = results.of(it.id);
            box.addView(Ui.text(a, n.reactions + (n.reactions == 1 ? " reaction" : " reactions") + " · " + n.talk
                    + (n.talk == 1 ? " comment or share" : " comments or shares"), 13, Ui.SUB, false), Ui.wide(a, 8));
        } else if (!it.result.isEmpty()) {
            box.addView(Ui.text(a, it.result.replaceFirst("^waiting:\\s*", ""), 13, Ui.SUB, false), Ui.wide(a, 8));
        }
        final Dialog[] sheet = new Dialog[1];
        LinearLayout acts = new LinearLayout(a);
        if (!it.url.isEmpty()) {
            TextView go = Ui.button(a, "Open the post", false);
            go.setOnClickListener(v -> {
                try {
                    a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(it.url)));
                } catch (RuntimeException ignored) {
                    // no browser
                }
            });
            acts.addView(go, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        if (it.state == Campaign.State.FAILED && !it.handoff) {
            TextView again = Ui.button(a, "Post it on screen", true);
            again.setOnClickListener(v -> {
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
                a.startActivity(PostingActivity.intent(a, c.id, it.id));
            });
            acts.addView(again, gapped(a, acts));
        }
        TextView more = Ui.button(a, "Edit, share…", false);
        more.setOnClickListener(v -> {
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
            PostActions.menu(a, c, it, redraw);
        });
        acts.addView(more, gapped(a, acts));
        box.addView(acts, Ui.wide(a, 14));
        android.widget.ScrollView scroll = new android.widget.ScrollView(a); // a long post scrolls; the sheet stays on screen
        scroll.addView(box);
        sheet[0] = BottomSheet.showView(a, "Post", scroll);
    }

    private static LinearLayout.LayoutParams gapped(Activity a, LinearLayout row) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
        lp.setMarginStart(row.getChildCount() > 0 ? Ui.dp(a, 8) : 0);
        return lp;
    }
}
