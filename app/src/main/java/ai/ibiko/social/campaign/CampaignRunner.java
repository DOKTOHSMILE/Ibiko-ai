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

    /** A run is going: an alarm that comes meanwhile steps aside (the run sets the next alarm when it ends). */
    private static final java.util.concurrent.atomic.AtomicBoolean RUNNING = new java.util.concurrent.atomic.AtomicBoolean();
    /** If a run is cut off (the app killed mid-post), this alarm comes back for it. */
    static final long SAFETY_MS = 15 * 60_000L;

    @Override
    public void onReceive(Context c, Intent intent) {
        final Context app = c.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            return; // one run at a time; a second waiting on the first held the alarm open until Android said "not responding"
        }
        // posting waits for pages and for each post to show (minutes): it runs on its own thread, and the alarm's
        // broadcast is let go at once rather than held open for all of it (which froze the app on a long run)
        safety(app, System.currentTimeMillis() + SAFETY_MS);
        new Thread(() -> {
            try {
                run(app, System.currentTimeMillis());
                LeadSender.remind(app, System.currentTimeMillis()); // pitches waiting for a follow-up
            } catch (RuntimeException e) {
                // the safety alarm tries again
            } finally {
                RUNNING.set(false);
            }
        }, "ibiko-campaign").start();
    }

    /** The alarm, set for exactly {@code at}: replaced by {@link #arm} when the run ends. */
    static void safety(Context c, long at) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) {
            return;
        }
        PendingIntent fire = PendingIntent.getBroadcast(c, 1, new Intent(c, CampaignRunner.class).setAction(DUE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire);
        } catch (RuntimeException ignored) {
            // the run's own alarm still comes
        }
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

    /** A post due while the phone is offline waits this long, then tries again. */
    static final long OFFLINE_WAIT_MS = 10 * 60_000L;

    /** True when the phone has a working internet connection (or it can't be told: then the post is tried). */
    static boolean online(Context c) {
        try {
            android.net.ConnectivityManager cm = c.getSystemService(android.net.ConnectivityManager.class);
            if (cm == null) {
                return true;
            }
            android.net.NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            return nc != null && nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (RuntimeException e) {
            return true;
        }
    }

    /**
     * What the check after posting found: seen on the network (posted, checked), not there (said plainly, and not
     * posted again so nothing is doubled), or not known yet (looked for again later).
     */
    static void checked(Context c, Campaign camp, Campaign.Item it, PostCheck.Seen seen, String account, String network, String said) {
        if (seen != null && seen.yes()) {
            it.state = Campaign.State.POSTED;
            it.checked = 1;
            it.url = seen.url == null ? "" : seen.url;
            it.result = "Posted and checked: it's on " + network + " (" + account + ")"
                    + (it.url.isEmpty() ? "." : ": " + it.url);
        } else if (seen != null && seen.no()) {
            it.state = Campaign.State.FAILED;
            it.checked = -1;
            it.result = "Ibiko pressed Post, but the post isn't on your " + network + " profile. Look there; it won't be"
                    + " posted again by itself, so nothing is doubled. Ibiko looks once more later.";
            tell(c, it.id.hashCode(), camp.name + ": a post isn't showing on " + network, it.result + "\n\n" + it.text);
        } else {
            it.state = Campaign.State.POSTED;
            it.checked = 0;
            it.result = said + " Not checked yet: Ibiko looks for it on your profile at its next round.";
        }
    }

    /**
     * Looks again for posts of {@code camp} not yet seen on their network (posted in the last two days): one found
     * is marked posted and checked; one still missing stays as it is. Switches to each post's account to look.
     * Returns how many changed.
     */
    public static int recheck(Context c, Campaign camp) {
        long now = System.currentTimeMillis();
        int changed = 0;
        for (Campaign.Item it : camp.items) {
            boolean open = it.state == Campaign.State.POSTED && it.checked == 0
                    || it.state == Campaign.State.FAILED && it.checked == -1;
            if (!open || it.handoff || now - it.at > 2 * 24 * 3_600_000L) {
                continue;
            }
            final SocialAccounts.Account a = SocialWeb.accounts(c).get(it.account);
            if (a == null || !a.signedIn) {
                continue;
            }
            final String text = it.text;
            PostCheck.Seen seen = LeadSender.as(c, a, () -> PostCheck.look(c, a.platform, text), null);
            if (seen != null && seen.yes()) {
                it.state = Campaign.State.POSTED;
                it.checked = 1;
                it.url = seen.url == null ? "" : seen.url;
                it.result = "Posted and checked: it's on " + a.platform.shown + " (" + a.label + ")"
                        + (it.url.isEmpty() ? "." : ": " + it.url);
                keep(c, camp.id, it);
                changed++;
            } else if (seen != null && seen.no() && it.checked == 0) {
                it.checked = -1;
                it.state = Campaign.State.FAILED;
                it.result = "Ibiko pressed Post, but the post isn't on your " + a.platform.shown + " profile. Look there;"
                        + " it won't be posted again by itself, so nothing is doubled.";
                keep(c, camp.id, it);
                changed++;
            }
        }
        return changed;
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
        it.checked = done.checked;
        it.url = done.url;
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
        String whose = it.handoff ? "share:" + it.network : it.account;
        if (repeat(c, camp, it, whose, now)) {
            return;
        }
        if (it.handoff) {
            // a network Ibiko can't post on itself: the post, its picture drawn, is offered to the person to share
            picture(c, camp, it);
            ai.ibiko.chat.ui.PostShareActivity.offerDue(c, camp, it);
            posted(c).add(whose, it.text, it.headline, now);
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
        if (!online(c)) {
            it.at = now + OFFLINE_WAIT_MS; // everything is ready on the phone: it goes out when the internet is back
            it.result = "waiting: no internet on the phone";
            return;
        }
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
        PostCheck.Seen seen = null;
        synchronized (SocialWeb.SWITCH) { // switch, post, switch back: nothing else posts or switches meanwhile
            SocialAccounts.Account before = accounts.inUse(net);
            boolean switched = before == null || !before.id.equals(a.id);
            if (switched && !SocialWeb.switchTo(c, a.id)) {
                fail(c, camp, it, "the sign-in for " + a.label + " wasn't kept; sign in again under Social accounts");
                return;
            }
            try {
                r = SocialPoster.post(c, net, it.text, null, null, picture);
                if (r.posted) {
                    seen = PostCheck.verify(c, net, it.text); // pressing Post is not proof: it is looked for
                }
            } finally {
                if (switched && before != null) {
                    SocialWeb.switchTo(c, before.id); // the account the person had in use comes back
                }
            }
        }
        if (r.posted) {
            posted(c).add(a.id, it.text, it.headline, now); // pressed Post: never put out again on this account
            checked(c, camp, it, seen, a.label, net.shown, r.said);
            return;
        }
        if (r.said.startsWith("Not posted:")) {
            it.at = now + LIMIT_WAIT_MS; // a daily or hourly limit: later, not failed
            it.result = "waiting: " + r.said;
            return;
        }
        if (handOver(r.said)) {
            // the network's page didn't do its part (its composer, its picture field, its Post button): the post is
            // handed to the person, ready in the app, rather than lost
            it.state = Campaign.State.FAILED;
            it.result = "Handed to you to post (" + r.said + ")";
            if (!ai.ibiko.social.PostWindow.open()) {
                ai.ibiko.chat.ui.PostShareActivity.offerFailed(c, camp, it, r.said); // on screen, the screen offers it
            }
            return;
        }
        fail(c, camp, it, r.said);
    }

    /** How a post made on the posting screen went. */
    public static final class OnScreen {
        public final boolean posted;
        public final String said;
        /** The network the post is for ("x", "facebook"), to hand it over by share when it didn't go out. */
        public final String network;

        OnScreen(boolean posted, String said, String network) {
            this.posted = posted;
            this.said = said;
            this.network = network;
        }
    }

    /**
     * One post put out on the posting screen ({@code PostingActivity}, which lends its frame to the page): the same
     * checks and the same steps as a timed post, the page shown while it runs. A post that had failed is tried again;
     * one already out is not (it would be doubled). Off the main thread.
     */
    public static OnScreen postOnScreen(Context c, String campaignId, String itemId, java.util.function.Consumer<String> step) {
        Campaign camp = campaignId == null ? null : store(c).get(campaignId);
        Campaign.Item it = camp == null ? null : item(camp, itemId);
        if (it == null) {
            return new OnScreen(false, "That post is no longer in the campaign.", null);
        }
        if (it.state == Campaign.State.POSTED) {
            return new OnScreen(false, "That post already went out: " + it.result, null);
        }
        String net = shown(it.network);
        step.accept("Drawing the picture and opening " + net + "…");
        Campaign.State before = it.state;
        it.state = Campaign.State.PLANNED;
        postOne(c, camp, it, System.currentTimeMillis());
        if (it.state == Campaign.State.PLANNED && it.result != null && it.result.startsWith("waiting")) {
            it.state = before == Campaign.State.FAILED ? Campaign.State.FAILED : Campaign.State.PLANNED;
        }
        keep(c, camp.id, it);
        boolean ok = it.state == Campaign.State.POSTED;
        return new OnScreen(ok, ok ? "Posted on " + net + ". " + (it.result == null ? "" : it.result)
                : "It didn't go out: " + (it.result == null ? "the page didn't take it." : it.result), it.network);
    }

    private static PostedLog posted;

    /** What every account has put out, across campaigns. */
    static synchronized PostedLog posted(Context c) {
        if (posted == null) {
            posted = new PostedLog(new File(new File(c.getFilesDir(), "campaigns"), "posted.tsv"));
        }
        return posted;
    }

    /**
     * Strict about repeats: the same words already out on this account (within the window, from any campaign) are not
     * posted again: the post is skipped and says when it went. The same picture headline with new words gets a headline
     * of its own (the post's first words) and a fresh picture, so the page doesn't show the same graphic twice.
     * True when the post was skipped.
     */
    static boolean repeat(Context c, Campaign camp, Campaign.Item it, String whose, long now) {
        PostedLog log = posted(c);
        long same = log.sameWords(whose, it.text, now);
        if (same > 0) {
            it.state = Campaign.State.SKIPPED;
            it.result = "Not posted: the same post already went out on this account on "
                    + new java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(new java.util.Date(same))
                    + ", so it isn't doubled.";
            return true;
        }
        if (!it.imageHtml.isEmpty() && !it.headline.isEmpty() && log.sameHeadline(whose, it.headline, now) > 0) {
            String fresh = firstWords(it.text, 6);
            if (!fresh.isEmpty() && log.sameHeadline(whose, fresh, now) == 0) {
                it.headline = fresh;
                it.seed = DesignGen.freshSeed(it.id);
                it.designed = false;
                it.imageHtml = CampaignPlanner.picture(it, it.product.isEmpty() ? "" : Catalog.PHOTO + Catalog.safe(it.product));
                if (!it.imagePath.isEmpty()) {
                    new File(it.imagePath).delete();
                }
                it.imagePath = "";
            }
        }
        return false;
    }

    /** The first few words of a post, without links or hashtags, for a headline. */
    static String firstWords(String text, int most) {
        String[] w = (text == null ? "" : text).replaceAll("(?i)https?://\\S+|#\\w+", " ").trim().split("\\s+");
        StringBuilder h = new StringBuilder();
        for (int i = 0; i < w.length && i < most; i++) {
            h.append(i == 0 ? "" : " ").append(w[i]);
        }
        return h.toString().replaceAll("[.,!?:;]+$", "");
    }

    /** The page, not the account, stopped the post: the person can post it in the app with one tap. */
    static boolean handOver(String said) {
        return said != null && said.matches("(?s).*(composer didn't open|couldn't attach the picture|Post button wasn't on the page"
                + "|Post button stayed greyed out|words didn't go into its composer|couldn't post on .* myself).*")
                && !said.matches("(?s).*signed out.*");
    }

    /**
     * A new picture for a post still to go out: the same words and the model's design, drawn fresh from a new seed,
     * saved and kept. Off the main thread. The picture's file, or null when it couldn't be drawn.
     */
    public static File redraw(Context c, Campaign camp, Campaign.Item it) {
        return redraw(c, camp, it, null);
    }

    /** As above, with {@code page} (a picture the model designed) in place of the formula's when given. */
    public static File redraw(Context c, Campaign camp, Campaign.Item it, String page) {
        if (it.headline.isEmpty()) {
            String[] w = it.text.replaceAll("(?i)https?://\\S+|#\\w+", " ").trim().split("\\s+");
            StringBuilder h = new StringBuilder();
            for (int i = 0; i < w.length && i < 5; i++) {
                h.append(i == 0 ? "" : " ").append(w[i]);
            }
            it.headline = h.toString().replaceAll("[.,!?:;]+$", "");
        }
        it.seed = DesignGen.freshSeed(it.id);
        it.designed = page != null && !page.isEmpty();
        it.imageHtml = it.designed ? page : CampaignPlanner.picture(it, it.product.isEmpty() ? "" : Catalog.PHOTO + Catalog.safe(it.product));
        if (!it.imagePath.isEmpty()) {
            new File(it.imagePath).delete();
        }
        it.imagePath = "";
        File made = picture(c, camp, it);
        CampaignStore st = store(c);
        Campaign latest = st.get(camp.id);
        Campaign.Item kept = latest == null ? null : item(latest, it.id);
        if (kept != null) {
            kept.headline = it.headline;
            kept.seed = it.seed;
            kept.imageHtml = it.imageHtml;
            kept.designed = it.designed;
            kept.imagePath = it.imagePath;
            try {
                st.put(latest);
            } catch (IOException ignored) {
                // drawn; kept with the next change
            }
        }
        return made;
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
        CampaignImage.Drawn d = CampaignImage.drawn(c, catalog(c).inline(it.imageHtml), out, CampaignImage.SIDE, CampaignImage.SIDE);
        // strict about the words: a picture whose words still collide or run off it after the layout check is not
        // posted; the formula, whose layout keeps its words clear, draws it again (a few fresh seeds at most)
        for (int tries = 0; d.file != null && d.problem != null && tries < 3; tries++) {
            if (it.headline.isEmpty()) {
                break; // nothing for the formula to set: the checked picture stands
            }
            it.seed = DesignGen.freshSeed(it.id + tries);
            it.designed = false;
            it.imageHtml = CampaignPlanner.picture(it, it.product.isEmpty() ? "" : Catalog.PHOTO + Catalog.safe(it.product));
            d = CampaignImage.drawn(c, catalog(c).inline(it.imageHtml), out, CampaignImage.SIDE, CampaignImage.SIDE);
        }
        File made = d.file;
        if (made != null) {
            it.imagePath = made.getPath();
        }
        return made;
    }

    /** Deletes a campaign with its leads and results; posts already out stay on the networks. */
    public static void delete(Context c, Campaign camp) {
        CampaignDraft.stop(camp.id); // planning it stops at the next post, and what it writes after is refused
        CampaignDraft.cancel(c, camp.id);
        try {
            store(c).remove(camp.id);
            LeadSender.store(c).forget(camp.id);
            Results r = results(c);
            r.forget(camp);
            r.save();
            CampaignStudio.chat(c, camp.id).delete();
        } catch (IOException ignored) {
            // the list shows what was kept
        }
        arm(c, 0);
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
