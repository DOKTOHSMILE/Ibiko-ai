package ai.ibiko.chat.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import ai.ibiko.social.PostWindow;
import ai.ibiko.social.campaign.CampaignRunner;

/**
 * The posting screen: one campaign post put out on screen, the network's own page shown while Ibiko opens its
 * composer, types the words, attaches the picture and presses Post, as the person would. A page on screen runs at full
 * speed, so a composer that never opened in a hidden page opens here; the person watches each step and can close it at
 * any time. It says how it went, and closes itself after a post went out.
 */
public final class PostingActivity extends Activity {
    static final String CAMPAIGN = "campaign", ITEM = "item";

    private FrameLayout frame;
    private TextView status, shareInstead;
    private volatile boolean running;

    public static Intent intent(Context c, String campaignId, String itemId) {
        return new Intent(c, PostingActivity.class).putExtra(CAMPAIGN, campaignId).putExtra(ITEM, itemId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); // the post finishes before the screen sleeps
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 16);
        TextView title = Ui.text(this, "Posting for you", 18, Ui.TEXT, true);
        title.setPadding(pad, pad, pad, Ui.dp(this, 4));
        root.addView(title);
        status = Ui.text(this, "Opening the page…", 14, Ui.SUB, false);
        status.setPadding(pad, 0, pad, Ui.dp(this, 10));
        root.addView(status);
        frame = new FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        shareInstead = Ui.button(this, "Share it myself instead", true);
        shareInstead.setVisibility(android.view.View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.setMargins(pad, Ui.dp(this, 8), pad, 0);
        root.addView(shareInstead, sp);
        TextView close = Ui.button(this, "Close", false);
        close.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(pad, Ui.dp(this, 8), pad, pad);
        root.addView(close, lp);
        setContentView(root);
        Ui.fitInsets(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        PostWindow.attach(frame);
        if (running) {
            return;
        }
        running = true;
        final String camp = getIntent().getStringExtra(CAMPAIGN), item = getIntent().getStringExtra(ITEM);
        final Context app = getApplicationContext();
        new Thread(() -> {
            final CampaignRunner.OnScreen done = CampaignRunner.postOnScreen(app, camp, item, line -> runOnUiThread(() -> status.setText(line)));
            runOnUiThread(() -> {
                status.setText(done.said);
                if (done.posted) {
                    frame.postDelayed(this::finish, 2500); // seen it go out: back to where the person was
                } else if (done.network != null) {
                    shareInstead.setVisibility(android.view.View.VISIBLE);
                    shareInstead.setOnClickListener(v -> {
                        startActivity(PostShareActivity.intent(this, camp, item, done.network));
                        finish();
                    });
                }
            });
        }, "ibiko-posting").start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        PostWindow.detach(frame); // a post still running finishes in a hidden page
    }
}
