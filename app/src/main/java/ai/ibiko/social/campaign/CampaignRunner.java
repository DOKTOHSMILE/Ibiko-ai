package ai.ibiko.social.campaign;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import ai.ibiko.social.Platform;
import ai.ibiko.social.SocialAccounts;
import ai.ibiko.social.SocialPoster;
import ai.ibiko.social.SocialWeb;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs the approved campaigns: one alarm for the next post due across every running campaign. When it goes off, each
 * due post goes out from its own account (that account is put in use for the post, and the one in use before is put
 * back), with its picture. One post an account a run, the next run a few minutes later, so accounts keep a person's
 * pace; the daily limits ({@code SocialLimits}) still hold. A post that can't go out is not guessed at: it waits (a
 * limit), or it is marked failed with why, and the person is told. The alarm is set again after the phone restarts.
 */
public final class CampaignRunner extends BroadcastReceiver {
    static final String DUE = "ai.ibiko.CAMPAIGN_DUE";
    static final String CHANNEL = "campaigns";
    /** Between two runs while posts are waiting: like a person, never all at once. */
    static final long GAP_MS = 4 * 60_000L;
    /** A post held by a limit is tried again this much later. */
    static final long LIMIT_WAIT_MS = 60 * 60_000L;

    /** The campaigns on this phone. */
    public static CampaignStore store(Context c) {
        return new CampaignStore(new File(c.getFilesDir(), "campaigns"));
    }

    /** How people responded to the campaigns' posts. */
    public static Results results(Context c) {
        return new Results(new File(c.getFilesDir(), "campaigns"));
    }

    /** The business's products and their photos. */
    public static Catalog catalog(Context c) {
        return new Catalog(new File(c.getFilesDir(), "catalog"));
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        final PendingResult later = goAsync(); // posting waits for the page: off the main thread
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                run(app, System.currentTimeMillis());
                LeadSender.remind(app, System.currentTimeMillis()); // pitches waiting for a follow-up
            } catch (RuntimeException e) {
                // the next alarm tries again
            } finally {
                later.finish();
            }
        }, "ibiko-campaign").start();
    }

    /** One run: every due post that can go now, one an account; then the next alarm. */
    static synchronized void run(Context c, long now) {
        CampaignStore store = store(c);
        Set<String> usedThisRun = new HashSet<String>();
        boolean more = false;
        for (Campaign camp : store.all()) {
            if (camp.status != Campaign.Status.ACTIVE) {
                continue;
            }
            for (Campaign.Item it : camp.planned()) {
                if (it.at > now) {
                    break; // the rest are later
                }
                if (usedThisRun.contains(it.account)) {
                    more = true; // this account posted this run: its next one waits for the next run
                    continue;
                }
                // the person may have edited, let through or taken out this post since the run began: as it is now
                Campaign latest = store(c).get(camp.id);
                Campaign.Item fresh = latest == null ? null : item(latest, it.id);
                if (latest == null || latest.status != Campaign.Status.ACTIVE) {
                    break; // deleted or paused meanwhile
                }
                if (fresh == null || fresh.state != Campaign.State.PLANNED) {
                    continue;
                }
                it.text = fresh.text;
                it.riskOk = fresh.riskOk;
                usedThisRun.add(it.account);
                postOne(c, camp, it, now);
                keep(c, camp.id, it);
                if (camp.whatsappShare && it.state == Campaign.State.POSTED && !it.handoff) {
                    ai.ibiko.chat.ui.PostShareActivity.offer(c, camp, it);
                }
            }
            Campaign latest = store(c).get(camp.id);
            if (latest != null && latest.status == Campaign.Status.ACTIVE && latest.planned().isEmpty()) {
                latest.status = Campaign.Status.DONE;
                try {
                    store(c).put(latest);
                } catch (IOException e) {
                    // marked done next run
                }
                tell(c, camp.id.hashCode(), camp.name + ": the campaign is done", latest.progress());
            }
        }
        arm(c, more ? now + GAP_MS : 0);
    }

    static Campaign.Item item(Campaign c, String id) {
        for (Campaign.Item i : c.items) {
            if (i.id.equals(id)) {
                return i;
            }
        }
        return null;
    }

    /**
     * What happened to one post, saved into the campaign as it is now (not this run's copy, so the person's edits to
     * other posts made meanwhile are kept).
     */
    static void keep(Context c, String campaign, Campaign.Item done) {
        CampaignStore st = store(c);
        Campaign latest = st.get(campaign);
        Campaign.Item it = latest == null ? null : item(latest, done.id);
        if (it == null) {
            return; // the campaign or the post was removed meanwhile
        }
        it.state = done.state;
        it.result = done.result;
        it.at = done.at;
        it.imagePath = done.imagePath;
        try {
            st.put(latest);
        } catch (IOException e) {
            // posted all the same; the action log has it
        }
    }

    /** Posts one item from its own account and records what happened. */
    static void postOne(Context c, Campaign camp, Campaign.Item it, long now) {
        if (!it.riskOk && PlatformRules.banned(it.network, it.text)) {
            // the network bans what it says (it may have been edited since the review): held, not posted
            fail(c, camp, it, "held back: " + PlatformRules.said(PlatformRules.check(it.network, it.text))
                    + ". Edit it, or choose to post it anyway, under Social accounts › Campaigns.");
            return;
        }
        if (it.handoff) {
            // a network Ibiko can't post on itself: the post, its picture drawn, is offered to the person to share
            picture(c, camp, it);
            ai.ibiko.chat.ui.PostShareActivity.offerDue(c, camp, it);
            it.state = Campaign.State.POSTED;
            it.result = "Offered to you to share on " + shown(it.network) + " (you tap Share)";
            return;
        }
        SocialAccounts accounts = SocialWeb.accounts(c);
        SocialAccounts.Account a = accounts.get(it.account);
        if (a == null || !a.signedIn) {
            fail(c, camp, it, "the account " + (a == null ? "was removed" : a.label + " is signed out") + " (Social accounts)");
            return;
        }
        Platform net = a.platform;
        String paused = SocialWeb.limits(c).pausedWhy(a.id, now);
        if (paused != null) {
            it.at = now + LIMIT_WAIT_MS; // the site asked for a check: this account rests, the post waits
            it.result = "waiting: " + paused;
            return;
        }
        File picture = picture(c, camp, it);
        if (!it.imageHtml.isEmpty() && picture == null) {
            fail(c, camp, it, "its picture couldn't be drawn on this phone");
            return;
        }
        SocialPoster.Result r;
        synchronized (SocialWeb.SWITCH) { // switch, post, switch back: nothing else posts or switches meanwhile
            SocialAccounts.Account before = accounts.inUse(net);
            boolean switched = before == null || !before.id.equals(a.id);
            if (switched && !SocialWeb.switchTo(c, a.id)) {
                fail(c, camp, it, "the sign-in for " + a.label + " wasn't kept; sign in again under Social accounts");
                return;
            }
            try {
                r = SocialPoster.post(c, net, it.text, null, null, picture);
            } finally {
                if (switched && before != null) {
                    SocialWeb.switchTo(c, before.id); // the account the person had in use comes back
                }
            }
        }
        if (r.posted) {
            it.state = Campaign.State.POSTED;
            it.result = r.said;
            return;
        }
        if (r.said.startsWith("Not posted:")) {
            it.at = now + LIMIT_WAIT_MS; // a daily or hourly limit: later, not failed
            it.result = "waiting: " + r.said;
            return;
        }
        fail(c, camp, it, r.said);
    }

    /** {@link #picture}, for a share from outside this package. */
    public static File pictureOf(Context c, Campaign camp, Campaign.Item it) {
        return picture(c, camp, it);
    }

    /** The post's picture, drawn now if it wasn't yet; null when there is none or it couldn't be drawn. */
    static File picture(Context c, Campaign camp, Campaign.Item it) {
        if (it.imageHtml.isEmpty()) {
            return null;
        }
        File kept = it.imagePath.isEmpty() ? null : new File(it.imagePath);
        if (kept != null && kept.isFile()) {
            return kept;
        }
        File out = new File(new File(new File(c.getFilesDir(), "campaigns"), safe(camp.id)), safe(it.id) + ".jpg");
        File made = CampaignImage.render(c, catalog(c).inline(it.imageHtml), out);
        if (made != null) {
            it.imagePath = made.getPath();
        }
        return made;
    }

    /** A network's name for the person ("instagram" → "Instagram"). */
    public static String shown(String network) {
        String n = network == null ? "" : network.trim().toLowerCase(java.util.Locale.ROOT);
        switch (n) {
            case "x":
                return "X";
            case "linkedin":
                return "LinkedIn";
            default:
                return n.isEmpty() ? "" : Character.toUpperCase(n.charAt(0)) + n.substring(1);
        }
    }

    static String safe(String s) {
        String t = s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "_");
        return t.isEmpty() ? "_" : t;
    }

    private static void fail(Context c, Campaign camp, Campaign.Item it, String why) {
        it.state = Campaign.State.FAILED;
        it.result = why;
        tell(c, it.id.hashCode(), camp.name + ": a post didn't go out", why + "\n\n" + it.text);
    }

    /** Sets the alarm for the next due post of a running campaign ({@code soonest}, when set, if that is sooner). */
    public static void arm(Context c, long soonest) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        PendingIntent fire = PendingIntent.getBroadcast(c, 1, new Intent(c, CampaignRunner.class).setAction(DUE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        long first = soonest > 0 ? soonest : Long.MAX_VALUE;
        for (Campaign camp : store(c).all()) {
            if (camp.status == Campaign.Status.ACTIVE) {
                for (Campaign.Item it : camp.planned()) {
                    first = Math.min(first, it.at);
                    break;
                }
            }
        }
        if (first == Long.MAX_VALUE) {
            am.cancel(fire);
            return;
        }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, Math.max(first, System.currentTimeMillis() + 5_000), fire);
    }

    /** Starts an approved campaign: its pictures are drawn as it starts, and the alarm is set. Off the main thread. */
    public static String start(Context c, Campaign camp) {
        camp.status = Campaign.Status.ACTIVE;
        for (Campaign.Item it : camp.items) {
            picture(c, camp, it); // drawn now, so a picture that can't be drawn shows up before its day
        }
        try {
            store(c).put(camp);
        } catch (IOException e) {
            return "The campaign couldn't be saved: " + e.getMessage();
        }
        arm(c, 0);
        return null;
    }

    /** Pauses or resumes a campaign. */
    public static void setRunning(Context c, String id, boolean running) {
        CampaignStore store = store(c);
        Campaign camp = store.get(id);
        if (camp == null || camp.status == Campaign.Status.DONE) {
            return;
        }
        camp.status = running ? Campaign.Status.ACTIVE : Campaign.Status.PAUSED;
        try {
            store.put(camp);
        } catch (IOException ignored) {
            // the screen shows what was kept
        }
        arm(c, 0);
    }

    private static void tell(Context c, int id, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Campaigns", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("How your social media campaigns are going");
            nm.createNotificationChannel(ch);
        }
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(id, n);
        } catch (SecurityException noPermission) {
            // notifications off: the campaign screen shows it
        }
    }
}
