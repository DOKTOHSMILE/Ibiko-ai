package ai.ibiko.chat.ui;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Share › Ibiko from any app. It has no screen: it copies what was shared into Ibiko's own cache (the other app's
 * permission to read it ends when this closes), keeps it for the chat, and opens Ibiko, where the files appear in the
 * composer and shared text in the field. Nothing is sent until the user sends it.
 */
public final class ShareActivity extends Activity {
    /** What was shared: copies of the files (as Ibiko's own content links) and the text. */
    public static final class Shared {
        public final List<Uri> files = new ArrayList<Uri>();
        public String text;
    }

    static final int MOST_FILES = 20;
    static final long MOST_BYTES = 200L * 1024 * 1024;
    private static Shared waiting;

    /** What is waiting for the chat, once; null when nothing is. */
    public static synchronized Shared take() {
        Shared s = waiting;
        waiting = null;
        return s;
    }

    /** Words for the message box, put in the next time the chat comes to the front (nothing is sent). */
    public static void offerText(String words) {
        if (words == null || words.isEmpty()) {
            return;
        }
        Shared s = new Shared();
        s.text = words;
        keep(s);
    }

    private static synchronized void keep(Shared s) {
        if (waiting != null) {
            s.files.addAll(0, waiting.files);
            if (waiting.text != null) {
                s.text = s.text == null ? waiting.text : waiting.text + "\n" + s.text;
            }
        }
        waiting = s;
    }

    /** A file name safe to keep in the cache: the name only, no path, never empty. */
    /**
     * Whether a shared link is a file another app is handing over: a content:// link from another app's provider. Any
     * app can "share" a link, and a file:// path or a link to Ibiko's own provider made Ibiko copy one of its own
     * private files (its settings, its chats) into a chat, and from there to the model.
     */
    static boolean fromAnotherApp(Uri u, String ownPackage) {
        if (u == null || !"content".equalsIgnoreCase(u.getScheme())) {
            return false;
        }
        String authority = u.getAuthority();
        return authority != null && !authority.equals("ai.ibiko.files") && !authority.startsWith(ownPackage + ".")
                && !authority.equals(ownPackage);
    }

    static String safeName(String name, int n) {
        String s = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        s = s.replaceAll("^\\.+", "");
        if (s.length() > 80) {
            s = s.substring(s.length() - 80);
        }
        return s.isEmpty() ? "shared-" + n : s;
    }

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        final Intent intent = getIntent();
        final List<Uri> from = new ArrayList<Uri>();
        try {
            if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
                if (many != null) {
                    from.addAll(many);
                }
            } else if (Intent.ACTION_SEND.equals(intent.getAction())) {
                Uri one = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
                if (one != null) {
                    from.add(one);
                }
            }
        } catch (RuntimeException e) {
            // packed in a way that can't be read: the text, if any, is still taken
        }
        CharSequence words = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        final Shared shared = new Shared();
        shared.text = words == null ? null : words.toString();
        new Thread(() -> {
            File dir = new File(getCacheDir(), "shared/" + System.currentTimeMillis());
            dir.mkdirs();
            for (int i = 0; i < from.size() && i < MOST_FILES; i++) {
                Uri u = from.get(i);
                if (u == null || !fromAnotherApp(u, getPackageName())) {
                    continue; // only what another app hands over: never a file:// path or Ibiko's own files
                }
                File to = new File(dir, safeName(nameOf(u), i));
                try (InputStream in = getContentResolver().openInputStream(u); FileOutputStream out = new FileOutputStream(to)) {
                    if (in == null) {
                        continue;
                    }
                    byte[] buf = new byte[65536];
                    long all = 0;
                    int n;
                    while ((n = in.read(buf)) > 0 && all <= MOST_BYTES) {
                        out.write(buf, 0, n);
                        all += n;
                    }
                    if (all > MOST_BYTES) {
                        to.delete(); // too large to take in
                        continue;
                    }
                    shared.files.add(androidx.core.content.FileProvider.getUriForFile(this, "ai.ibiko.files", to));
                } catch (java.io.IOException | RuntimeException e) {
                    to.delete();
                }
            }
            keep(shared);
            runOnUiThread(() -> {
                Intent open = getPackageManager().getLaunchIntentForPackage(getPackageName());
                if (open != null) {
                    startActivity(open);
                }
                finish();
            });
        }, "ibiko-share").start();
    }

    private String nameOf(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, new String[] { android.provider.OpenableColumns.DISPLAY_NAME }, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) {
                return c.getString(0);
            }
        } catch (RuntimeException e) {
            // no name given: the last part of the link
        }
        return u.getLastPathSegment();
    }
}
