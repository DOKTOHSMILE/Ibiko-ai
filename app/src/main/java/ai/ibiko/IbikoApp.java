package ai.ibiko;

import android.app.Application;

/**
 * The app as a whole. It installs the crash catcher before any screen, service or thread starts, so a crash anywhere
 * leaves a report in Download/Ibiko. It also collects, off the main thread, any native crash or freeze from the last
 * run that the catcher could not see.
 */
public final class IbikoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        CrashLog.install(this); // first of all: nothing below can crash without leaving a report
        // how the app closed last time that the catcher couldn't see (a native crash, a freeze, the system ending it for
        // memory), collected at once: a start that crashes quickly still gets it written
        Thread exits = new Thread(() -> {
            try {
                CrashLog.collectExits(this);
            } catch (Throwable ignored) {
                // never let the report keep the app from starting
            }
        }, "ibiko-exits");
        exits.setDaemon(true);
        exits.start();
        // a start that keeps crashing: the next one skips the loading behind the splash, so the app opens and the
        // report can be read (MainActivity marks a start as good once it has run a few seconds)
        android.content.SharedPreferences starts = getSharedPreferences("ibiko", MODE_PRIVATE);
        int failed = starts.getInt(START_FAILS, 0);
        starts.edit().putInt(START_FAILS, failed + 1).commit();
        safeStart = failed >= 2;
        // where the dictionary is: the brain's coded files are read with it, so it opens itself the first time it is needed
        ai.ibiko.learn.WordBook.at(new java.io.File(getFilesDir(), "brain/knowledge/lexicon.tsv"));
        ai.ibiko.learn.Symbols.open(getFilesDir()); // first: word ids are read back before any is made, the same for good
        ai.ibiko.main.FormerName.move(this); // once: what an older install kept under the former name moves over
        try {
            ai.ibiko.net.Transfers.use(new ai.ibiko.chat.ui.DownloadNotice(this)); // every download shows its progress
        } catch (RuntimeException ignored) {
            // no notifications: downloads still run
        }
        ai.ibiko.chat.Tuning.resetOnce(getSharedPreferences("ibiko", MODE_PRIVATE));
        try {
            ai.ibiko.net.NetSwitch.start(this); // a reply cut by Wi-Fi ↔ mobile data (as when the app is left) is asked again
        } catch (RuntimeException ignored) {
            // no network service: requests still run, only without the quick recovery
        }
        android.content.SharedPreferences p = getSharedPreferences("ibiko", MODE_PRIVATE);
        if (!p.getBoolean("approvals_back_v1", false)) {
            // "Always run" in the old approval dialog turned asking off for good, without saying so: asking is back on
            // once; Settings › Coding › Run commands without asking turns it off again
            p.edit().putBoolean("auto_accept", false).putBoolean("approvals_back_v1", true).apply();
        }
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            // Google search with no key: google.com read in a browser on the device that isn't shown
            ai.ibiko.net.WebSearch.browser = (q, most) -> ai.ibiko.net.GoogleWeb.search(this, q, most);
        }
        plugIbiko1();
        if (!"robolectric".equals(android.os.Build.FINGERPRINT) && !safeStart) {
            ai.ibiko.main.Warmup.start(this); // gets the chat and Ibiko v1 ready while the splash shows
        }
        // the voice is loaded as Ibiko starts and kept on standby, so the first reply read aloud doesn't wait for it
        if (ai.ibiko.main.AppFiles.ready(getFilesDir()) && !"robolectric".equals(android.os.Build.FINGERPRINT) && !safeStart) {
            ai.ibiko.voice.NeuralVoice.preload(getFilesDir(), ai.ibiko.voice.VoicePacks.KOKORO.key); // the one voice
            final java.io.File files = getFilesDir();
            new Thread(() -> ai.ibiko.voice.VoicePacks.dropOthers(files), "ibiko-old-voices").start(); // Piper, no longer offered
        }
    }

    static final String START_FAILS = "start_fails";

    /** The last starts crashed: this one skips the loading behind the splash (it loads when first needed instead). */
    public static volatile boolean safeStart;

    /** The app has been up a few seconds: this start was good. */
    public static void startedWell(android.content.Context c) {
        c.getSharedPreferences("ibiko", MODE_PRIVATE).edit().putInt(START_FAILS, 0).apply();
    }

    /**
     * Ibiko v1 (the ibiko1 folder) is given what it needs from the app: the web, the workers board, what the pilot is
     * into, an icon. This and the screen's {@code main/Ibiko1Screen} are the only places it is plugged in.
     */
    /** Posts for a search: public Reddit, then each network signed in here (each post a paragraph of what was read). */
    static java.util.List<ai.ibiko.ibiko1.formulas.Corroborate.Post> socialPosts(android.content.Context app, String query) {
        java.util.List<ai.ibiko.ibiko1.formulas.Corroborate.Post> out = new java.util.ArrayList<ai.ibiko.ibiko1.formulas.Corroborate.Post>();
        try {
            ai.ibiko.social.intel.RedditPublicProvider reddit = new ai.ibiko.social.intel.RedditPublicProvider(url -> {
                ai.ibiko.net.WebGet.Page p = ai.ibiko.net.WebGet.page(url, 10_000, 400_000);
                return p.status == 200 ? p.body : null;
            });
            for (ai.ibiko.social.intel.SocialPost p : reddit.search(query, 25)) {
                out.add(new ai.ibiko.ibiko1.formulas.Corroborate.Post("Reddit", p.author, p.content));
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // no Reddit: the signed-in networks may still answer
        }
        try {
            if (!ai.ibiko.social.SocialWeb.accounts(app).ready().isEmpty()) {
                String read = ai.ibiko.social.SocialWeb.search(app, null, query, 6000);
                String platform = "";
                for (String part : read.split("\\n\\s*\\n|\\n(?=\\S)")) {
                    String t = part.trim();
                    java.util.regex.Matcher head = java.util.regex.Pattern.compile("^(X|Facebook|Instagram|LinkedIn|Telegram)\\b").matcher(t);
                    if (head.find()) {
                        platform = head.group(1);
                    }
                    if (t.length() >= 20) {
                        out.add(new ai.ibiko.ibiko1.formulas.Corroborate.Post(platform.isEmpty() ? "social" : platform, "", t));
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // the networks couldn't be read now: Reddit's posts stand
        }
        return out;
    }

    private void plugIbiko1() {
        final android.content.Context app = this;
        // knowledge is upgradable: a newer copy downloaded to the phone is read before the one in the app
        ai.ibiko.ibiko1.Plug.asset = ai.ibiko.net.KnowledgeFiles.over(getFilesDir(), path -> {
            try {
                return getAssets().open(path);
            } catch (java.io.IOException e) {
                return null;
            }
        });
        ai.ibiko.ibiko1.Plug.web = new ai.ibiko.ibiko1.Plug.Web() {
            @Override
            public String get(String url) {
                ai.ibiko.net.WebGet.Page p = ai.ibiko.net.WebGet.page(url, 12_000, 400_000);
                return p.status == 200 ? p.body : null;
            }

            @Override
            public String search(String question) {
                return new ai.ibiko.connect.ConnectorStore(app).research(question, 2, 8000);
            }
        };
        // social media as a cross-check: public Reddit (no account needed) and the networks signed in on this phone
        if (!"robolectric".equals(android.os.Build.FINGERPRINT)) {
            ai.ibiko.ibiko1.Plug.social = query -> socialPosts(app, query); // tests never reach the networks
        }
        ai.ibiko.ibiko1.Plug.workers = new ai.ibiko.ibiko1.Plug.Workers() {
            @Override
            public String start(String role, String task, String brain) {
                return ai.ibiko.agent.AgentBoard.start(role, task, brain);
            }

            @Override
            public void step(String code, String what) {
                ai.ibiko.agent.AgentBoard.step(code, what);
            }

            @Override
            public void end(String code, boolean done) {
                ai.ibiko.agent.AgentBoard.end(code, done ? ai.ibiko.agent.AgentBoard.Outcome.DONE : ai.ibiko.agent.AgentBoard.Outcome.STOPPED);
            }
        };
        ai.ibiko.ibiko1.Plug.interests = () -> {
            ai.ibiko.learn.PilotProfile profile = ai.ibiko.learn.PilotProfile.at(new java.io.File(getFilesDir(), "brain/pilot.tsv"));
            java.util.List<String> out = new java.util.ArrayList<String>();
            for (String key : new String[] { "building", "likes", "works as", "works at", "home", "from" }) {
                if (profile.get(key) != null) {
                    out.add(profile.get(key));
                }
            }
            return out;
        };
        ai.ibiko.ibiko1.Plug.icon = R.drawable.ic_send;
    }

    /**
     * Android is short of memory. Drop the on-device weights if nothing is being written. The file stays
     * on the device and is loaded again for the next offline question. An API reply already parks them.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_RUNNING_LOW) {
            return;
        }
        try {
            ai.ibiko.llm.ModelHub hub = ai.ibiko.llm.ModelHub.get(this);
            hub.worker.execute(hub.model::releaseIfIdle);
        } catch (RuntimeException ignored) {
            // a trim must never take the app down
        }
    }
}
