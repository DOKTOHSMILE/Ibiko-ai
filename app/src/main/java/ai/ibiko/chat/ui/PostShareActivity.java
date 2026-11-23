package ai.ibiko.chat.ui;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
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
 * Hands one campaign post (its words and its picture) to an app where the person sends it themselves: WhatsApp, to
 * share to My status or a group; Instagram and LinkedIn, which let no app post for the person. Instagram drops the
 * words of a share, so they are copied for pasting. Free, nothing sent without the person's tap. No screen of its
 * own; opened from a campaign's posts and from the notifications below.
 */
public final class PostShareActivity extends Activity {
    static final String CAMPAIGN = "campaign", ITEM = "item", TO = "to";
    private static final String CHANNEL_WA = "campaign_whatsapp", CHANNEL_DUE = "campaign_share";

    /** The apps for each place a post can be handed to, the first installed one used. */
    static String[] apps(String to) {
        switch (to == null ? "" : to) {
            case "instagram":
                return new String[]{"com.instagram.android"};
            case "linkedin":
                return new String[]{"com.linkedin.android"};
            case "x":
                return new String[]{"com.twitter.android", "com.twitter.android.lite"};
            case "facebook":
                return new String[]{"com.facebook.katana", "com.facebook.lite"};
            default:
                return WhatsApp.APPS;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String campId = getIntent().getStringExtra(CAMPAIGN), itemId = getIntent().getStringExtra(ITEM);
        final String to = getIntent().getStringExtra(TO) == null ? "whatsapp" : getIntent().getStringExtra(TO);
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
                    share(this, to, text, picture);
                }
                finish();
            });
        }, "ibiko-post-share").start();
    }

    /** The share to the app for {@code to}; the share sheet when it isn't on the phone. */
    static void share(Activity a, String to, String text, File picture) {
        if (picture != null && picture.isFile() && toGallery(a, picture)) {
            // in the gallery too: if the app drops the picture, it is one tap away in any app's photo picker
            Toast.makeText(a, "The picture is in your gallery (Pictures › Ibiko).", Toast.LENGTH_SHORT).show();
        }
        Intent send = new Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text);
        if (picture != null && picture.isFile()) {
            Uri uri = androidx.core.content.FileProvider.getUriForFile(a, "ai.ibiko.files", picture);
            send.setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            send.setType("text/plain");
        }
        if ("instagram".equals(to) || "linkedin".equals(to) || "facebook".equals(to)) {
            ClipboardManager cm = a.getSystemService(ClipboardManager.class);
            if (cm != null) {
                // Instagram and the Facebook app drop shared words: they are pasted instead
                cm.setPrimaryClip(ClipData.newPlainText("Post", text));
                Toast.makeText(a, "The post's words are copied: paste them as the caption.", Toast.LENGTH_LONG).show();
            }
        }
        String app = installed(a.getPackageManager(), apps(to));
        try {
            if (app != null) {
                a.startActivity(send.setPackage(app));
            } else {
                Toast.makeText(a, CampaignRunner.shown(to) + " isn't on this phone: pick where to share it.", Toast.LENGTH_SHORT).show();
                a.startActivity(Intent.createChooser(send, "Share the post"));
            }
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "Nothing on this phone can share it.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * The post's picture saved to the phone's gallery (Pictures/Ibiko), once: an app that drops a shared picture, or a
     * post the person makes by hand, can pick it from there. True when it is there.
     */
    public static boolean toGallery(Context c, File picture) {
        android.content.ContentResolver cr = c.getContentResolver();
        String name = "ibiko-" + picture.getName();
        Uri images = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
        try (android.database.Cursor q = cr.query(images, new String[]{android.provider.MediaStore.Images.Media._ID},
                android.provider.MediaStore.Images.Media.DISPLAY_NAME + "=?", new String[]{name}, null)) {
            if (q != null && q.moveToFirst()) {
                return true; // saved before
            }
        } catch (RuntimeException ignored) {
            // can't look: saved again below at worst
        }
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Ibiko");
        v.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
        Uri at = null;
        try {
            at = cr.insert(images, v);
            if (at == null) {
                return false;
            }
            try (java.io.OutputStream o = cr.openOutputStream(at)) {
                if (o == null) {
                    cr.delete(at, null, null);
                    return false;
                }
                java.nio.file.Files.copy(picture.toPath(), o);
            }
            android.content.ContentValues done = new android.content.ContentValues();
            done.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
            cr.update(at, done, null, null);
            return true;
        } catch (java.io.IOException | RuntimeException e) {
            if (at != null) {
                try {
                    cr.delete(at, null, null);
                } catch (RuntimeException ignored) {
                    // nothing more to undo
                }
            }
            return false;
        }
    }

    /** The first of {@code apps} that is installed, or null. */
    static String installed(PackageManager pm, String[] apps) {
        for (String p : apps) {
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
        return intent(c, campaignId, itemId, "whatsapp");
    }

    public static Intent intent(Context c, String campaignId, String itemId, String to) {
        return new Intent(c, PostShareActivity.class).putExtra(CAMPAIGN, campaignId).putExtra(ITEM, itemId).putExtra(TO, to);
    }

    /** A post just went out: a notification offers it to WhatsApp Status or a group, one tap (one per campaign). */
    public static void offer(Context c, Campaign camp, Campaign.Item it) {
        notify(c, CHANNEL_WA, "Share to WhatsApp", "A campaign post that went out, ready to share to your WhatsApp Status or a group",
                ("wa-" + camp.id).hashCode(), "Share to WhatsApp Status?", it.text, intent(c, camp.id, it.id, "whatsapp"));
    }

    /** A post for Instagram or LinkedIn is due: a notification hands it to the app, its words ready to paste. */
    public static void offerDue(Context c, Campaign camp, Campaign.Item it) {
        String net = CampaignRunner.shown(it.network);
        notify(c, CHANNEL_DUE, "Posts to share", "A campaign post that is due on a network you post on yourself",
                ("due-" + it.id).hashCode(), "Time to post on " + net + " (" + camp.name + ")",
                "Tap to open " + net + " with the picture; the words are copied for the caption.\n\n" + it.text,
                intent(c, camp.id, it.id, it.network));
    }

    /**
     * A post Ibiko couldn't put out on the network's page itself: handed to the person, one tap opens the network's
     * app with the picture and the words ready (copied too, where the app drops shared words). It isn't lost.
     */
    public static void offerFailed(Context c, Campaign camp, Campaign.Item it, String why) {
        String net = CampaignRunner.shown(it.network);
        if (!it.imagePath.isEmpty() && new File(it.imagePath).isFile()) {
            toGallery(c, new File(it.imagePath)); // ready to pick by hand, whatever the app does with a share
        }
        notify(c, CHANNEL_DUE, "Posts to share", "A campaign post that is due on a network you post on yourself",
                ("due-" + it.id).hashCode(), "Tap to post on " + net + " (" + camp.name + ")",
                "It didn't go out in the background (" + why.replaceAll("^I couldn't post on \\S+ myself: ", "").replaceAll("\\.$", "")
                        + "). Tap and Ibiko posts it on screen, where " + net + "'s page works as it does for you; or share it"
                        + " yourself. The picture is also in your gallery (Pictures › Ibiko).\n\n" + it.text,
                PostingActivity.intent(c, camp.id, it.id), "Share it myself", intent(c, camp.id, it.id, it.network));
    }

    private static void notify(Context c, String channel, String channelName, String about, int id, String title, String text, Intent open) {
        notify(c, channel, channelName, about, id, title, text, open, null, null);
    }

    private static void notify(Context c, String channel, String channelName, String about, int id, String title, String text, Intent open,
            String otherLabel, Intent other) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null) {
            return;
        }
        if (nm.getNotificationChannel(channel) == null) {
            NotificationChannel ch = new NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(about);
            nm.createNotificationChannel(ch);
        }
        PendingIntent tap = PendingIntent.getActivity(c, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder nb = new Notification.Builder(c, channel)
                .setSmallIcon(ai.ibiko.R.drawable.ic_send)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(tap)
                .setAutoCancel(true);
        if (other != null) {
            PendingIntent second = PendingIntent.getActivity(c, id + 1, other.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            nb.addAction(new Notification.Action.Builder(null, otherLabel, second).build());
        }
        Notification n = nb.build();
        try {
            nm.notify(id, n);
        } catch (SecurityException noPermission) {
            // notifications off: the campaign's posts can still be shared from Social accounts
        }
    }
}
