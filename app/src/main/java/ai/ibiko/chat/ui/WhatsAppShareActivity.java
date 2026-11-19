package ai.ibiko.chat.ui;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;

import ai.ibiko.social.campaign.Campaign;
import ai.ibiko.social.campaign.CampaignRunner;
import ai.ibiko.social.campaign.WhatsApp;

/**
 * Hands one campaign post (its words and its picture) to WhatsApp, where the person picks My status or a group and
 * sends it: free, from their own WhatsApp, nothing sent without their tap. No screen of its own; opened from a
 * campaign's posts and from the notification shown when a post goes out.
 */
public final class WhatsAppShareActivity extends Activity {
    static final String CAMPAIGN = "campaign", ITEM = "item";
    private static final String CHANNEL = "campaign_whatsapp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String campId = getIntent().getStringExtra(CAMPAIGN), itemId = getIntent().getStringExtra(ITEM);
        final Context app = getApplicationContext();
        new Thread(() -> {
            Campaign camp = campId == null ? null : CampaignRunner.store(app).get(campId);
            Campaign.Item it = null;
            if (camp != null) {
                for (Campaign.Item i : camp.items) {
                    if (i.id.equals(itemId)) {
                        it = i;
                    }
                }
            }
            final File picture = it == null ? null : CampaignRunner.pictureOf(app, camp, it);
            final String text = it == null ? null : it.text;
            runOnUiThread(() -> {
                if (text == null) {
                    Toast.makeText(this, "That post is no longer in the campaign.", Toast.LENGTH_SHORT).show();
                } else {
                    share(this, text, picture);
                }
                finish();
            });
        }, "ibiko-whatsapp-share").start();
    }

    /** The share to WhatsApp (or WhatsApp Business); the share sheet when neither is on the phone. */
    static void share(Activity a, String text, File picture) {
        Intent send = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text);
        if (picture != null && picture.isFile()) {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(a, "ai.ibiko.files", picture);
            send.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            send.setType("text/plain");
        }
        String app = installed(a.getPackageManager());
        try {
            if (app != null) {
                a.startActivity(send.setPackage(app));
            } else {
                Toast.makeText(a, "WhatsApp isn't on this phone: pick where to share it.", Toast.LENGTH_SHORT).show();
                a.startActivity(Intent.createChooser(send, "Share the post"));
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Nothing on this phone can share it.", Toast.LENGTH_SHORT).show();
        }
    }

    /** WhatsApp's package when it is installed, else WhatsApp Business's, else null. */
    static String installed(PackageManager pm) {
        for (String p : WhatsApp.APPS) {
            try {
                pm.getPackageInfo(p, 0);
                return p;
            } catch (PackageManager.NameNotFoundException ignored) {
                // not this one
            }
        }
        return null;
    }

    public static Intent intent(Context c, String campaignId, String itemId) {
        return new Intent(c, WhatsAppShareActivity.class).putExtra(CAMPAIGN, campaignId).putExtra(ITEM, itemId);
    }

    /** A post just went out: a notification offers it to WhatsApp Status or a group, one tap (one per campaign). */
    public static void offer(Context c, Campaign camp, Campaign.Item it) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Share to WhatsApp", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("A campaign post that went out, ready to share to your WhatsApp Status or a group");
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = PendingIntent.getActivity(c, ("wa-" + camp.id).hashCode(),
                intent(c, camp.id, it.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle("Share to WhatsApp Status?")
                .setContentText(it.text)
                .setStyle(new Notification.BigTextStyle().bigText(it.text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(("wa-" + camp.id).hashCode(), n);
        } catch (SecurityException noPermission) {
            // notifications off: the campaign's posts can still be shared from Social accounts
        }
    }
}
