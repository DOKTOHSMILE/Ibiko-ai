package ai.ibiko.social.campaign;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A new campaign's brief on its way from the Social accounts screen to the brain (the screen has no brain of its own),
 * and the campaigns being planned now: each one's progress, and a stop. Planning runs on its own, apart from every
 * chat, so it never clashes with work going on there; a notification shows how far it is.
 */
public final class CampaignDraft {
    private static Campaign waiting;
    private static final Map<String, String> progress = new ConcurrentHashMap<String, String>();
    private static final Set<String> stopped = ConcurrentHashMap.newKeySet();
    static final String CHANNEL = "campaign_planning";

    private CampaignDraft() {
    }

    public static synchronized void offer(Campaign c) {
        waiting = c;
    }

    public static synchronized Campaign take() {
        Campaign c = waiting;
        waiting = null;
        return c;
    }

    /** What a campaign being planned is doing now ("Writing posts 9–16 of 70"), or null when it isn't being planned. */
    public static String planning(String id) {
        return progress.get(id);
    }

    public static void started(String id) {
        stopped.remove(id);
        progress.put(id, "Planning the campaign");
    }

    public static void at(Context c, String id, String name, String line) {
        progress.put(id, line);
        show(c, id, "Planning “" + name + "”", line, true);
    }

    public static void finished(Context c, String id) {
        progress.remove(id);
        stopped.remove(id);
    }

    /** The person stopped this campaign's planning. */
    public static void stop(String id) {
        stopped.add(id);
    }

    public static boolean isStopped(String id) {
        return stopped.contains(id);
    }

    /** The planning notification: ongoing while it works, then one that opens Social accounts to review the plan. */
    /** Takes the campaign's planning notification away (it was deleted). */
    public static void cancel(Context c, String id) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(("plan-" + id).hashCode());
        }
    }

    public static void show(Context c, String id, String title, String text, boolean ongoing) {
        if (CampaignStore.deleted(new java.io.File(c.getFilesDir(), "campaigns"), id)) {
            return; // deleted while planning: no notice brings it back to mind
        }
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Campaign planning", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("A campaign being planned, and when its plan is ready to review");
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(c, id.hashCode(),
                ai.ibiko.CampaignRoom.intent(c, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_calendar)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(ongoing)
                .setOnlyAlertOnce(true)
                .setAutoCancel(!ongoing)
                .build();
        try {
            nm.notify(("plan-" + id).hashCode(), n);
        } catch (SecurityException noPermission) {
            // notifications off: Social accounts › Campaigns shows it
        }
    }
}
