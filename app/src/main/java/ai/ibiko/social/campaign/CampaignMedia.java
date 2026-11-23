package ai.ibiko.social.campaign;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The person's own pictures for a campaign: picked from the phone (several at once), kept with the campaign, and put
 * on its posts as they are. The next posts still to go out that have no picture of the person's take them in time
 * order; what's left waits in the campaign's pool and the next posts written take it. Each is made a JPEG of at most
 * {@link #SIDE} pixels a side, small enough to attach on any network.
 */
public final class CampaignMedia {
    /** The longest side a picture is kept at. */
    static final int SIDE = 1600;
    /** The largest video or animation kept (bigger ones are left out, and said). */
    static final long MOST_FILE = 100L * 1024 * 1024;

    /** What a picked file is kept as: "jpg" for a still picture (made a JPEG), else its own kind (mp4, mov, gif…). */
    static String kind(String type, String name) {
        String t = type == null ? "" : type.toLowerCase(java.util.Locale.ROOT);
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (t.equals("image/gif") || n.endsWith(".gif")) {
            return "gif";
        }
        if (t.startsWith("video/") || n.matches(".*\\.(mp4|m4v|mov|webm|3gp|mkv)$")) {
            String ext = t.contains("quicktime") || n.endsWith(".mov") ? "mov" : t.contains("webm") || n.endsWith(".webm") ? "webm"
                    : t.contains("3gp") || n.endsWith(".3gp") ? "3gp" : t.contains("matroska") || n.endsWith(".mkv") ? "mkv" : "mp4";
            return ext;
        }
        return "jpg";
    }

    /** A video or an animation is a moving file; anything else here is a still picture. */
    public static boolean moving(String path) {
        return path != null && path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(gif|mp4|m4v|mov|webm|3gp|mkv)$");
    }

    private static boolean copy(Context c, Uri u, File to, long most) {
        long total = 0;
        try (InputStream in = c.getContentResolver().openInputStream(u); FileOutputStream o = new FileOutputStream(to)) {
            if (in == null) {
                return false;
            }
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                total += n;
                if (total > most) {
                    throw new IOException("too big");
                }
                o.write(buf, 0, n);
            }
        } catch (IOException | RuntimeException e) {
            to.delete();
            return false;
        }
        return total > 0;
    }

    private CampaignMedia() {
    }

    /** The folder a campaign's own pictures are kept in. */
    static File folder(Context c, String campaign) {
        return new File(new File(new File(c.getFilesDir(), "campaigns"), CampaignRunner.safe(campaign)), "media");
    }

    /** Copies the picked pictures into the campaign's folder, each as a JPEG; the files made (unreadable ones left out). */
    public static List<String> bring(Context c, String campaign, List<Uri> picked) {
        List<String> out = new ArrayList<String>();
        File dir = folder(c, campaign);
        dir.mkdirs();
        for (Uri u : picked) {
            String type = null;
            try {
                type = c.getContentResolver().getType(u);
            } catch (RuntimeException ignored) {
                // read as a picture below
            }
            // a video or an animated GIF is kept as it is (made smaller it would be a still); a still is made a JPEG
            String kept = kind(type, u.getLastPathSegment());
            if (!kept.equals("jpg")) {
                File f = new File(dir, "own-" + System.currentTimeMillis() + "-" + out.size() + "." + kept);
                if (copy(c, u, f, MOST_FILE)) {
                    out.add(f.getPath());
                }
                continue;
            }
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = c.getContentResolver().openInputStream(u)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                if (bounds.outWidth <= 0) {
                    continue;
                }
                int scale = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / scale > SIDE * 2) {
                    scale *= 2;
                }
                BitmapFactory.Options load = new BitmapFactory.Options();
                load.inSampleSize = scale;
                Bitmap b;
                try (InputStream in = c.getContentResolver().openInputStream(u)) {
                    b = BitmapFactory.decodeStream(in, null, load);
                }
                if (b == null) {
                    continue;
                }
                float k = Math.min(1f, SIDE / (float) Math.max(b.getWidth(), b.getHeight()));
                if (k < 1f) {
                    Bitmap s = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                    b.recycle();
                    b = s;
                }
                File f = new File(dir, "own-" + System.currentTimeMillis() + "-" + out.size() + ".jpg");
                try (FileOutputStream o = new FileOutputStream(f)) {
                    b.compress(Bitmap.CompressFormat.JPEG, 88, o);
                }
                b.recycle();
                out.add(f.getPath());
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                // that one is left out; the rest come in
            }
        }
        return out;
    }

    /**
     * Puts the pictures on the posts still to go out that don't carry one of the person's yet, earliest first; the
     * rest wait in the campaign's pool for the next posts written. What happened, in words (for the studio's chat).
     */
    public static String place(Campaign c, List<String> files) {
        List<Campaign.Item> on = new ArrayList<Campaign.Item>();
        List<String> left = new ArrayList<String>(files);
        for (Campaign.Item it : c.planned()) {
            if (left.isEmpty()) {
                break;
            }
            if (it.own) {
                continue;
            }
            it.own = true;
            it.imagePath = left.remove(0);
            it.imageHtml = "";
            it.designed = false;
            on.add(it);
        }
        c.media.addAll(left);
        java.text.DateFormat when = new java.text.SimpleDateFormat("EEE d MMM, HH:mm", java.util.Locale.getDefault());
        int moving = 0;
        for (String f : files) {
            moving += moving(f) && !f.toLowerCase(java.util.Locale.ROOT).endsWith(".gif") ? 1 : 0; // a GIF is a picture, moving
        }
        int still = files.size() - moving;
        String what = (still > 0 ? still + (still == 1 ? " picture" : " pictures") : "")
                + (still > 0 && moving > 0 ? " and " : "")
                + (moving > 0 ? moving + (moving == 1 ? " video" : " videos") : "");
        StringBuilder s = new StringBuilder().append(what).append(" added.");
        if (!on.isEmpty()) {
            s.append(" On ").append(on.size() == 1 ? "the next post: " : "the next " + on.size() + " posts: ");
            for (int i = 0; i < on.size(); i++) {
                s.append(i == 0 ? "" : ", ").append(when.format(new java.util.Date(on.get(i).at)));
            }
            s.append(", posted as they are.");
        }
        if (!left.isEmpty()) {
            s.append(' ').append(left.size()).append(left.size() == 1 ? " waits" : " wait")
                    .append(" for the next posts written.");
        }
        return s.toString();
    }

    /** A post just written takes the next picture waiting in the pool, if any. */
    static void fromPool(Campaign c, Campaign.Item it) {
        while (!c.media.isEmpty()) {
            String f = c.media.remove(0);
            if (new File(f).isFile()) {
                it.own = true;
                it.imagePath = f;
                it.imageHtml = "";
                it.designed = false;
                return;
            }
        }
    }
}
