package ai.ibiko.chat;

import android.content.SharedPreferences;

/**
 * The limits Ibiko works within. They used to be sliders under Settings › Advanced; they are fixed here now, chosen
 * for speed and for what a reply really needs, so nothing hidden can change how much a chat or a job uses. A value
 * once saved by the old sliders is ignored (and cleared once, see {@link #resetOnce}).
 */
public final class Tuning {
    /** One setting: its key, name, explanation, default and range. */
    public static final class Knob {
        public final String key, name, about;
        public final int def, min, max;
        /** A model-tier choice (0 automatic, 1 fast, 2 small, 3 big, 4 strong) rather than a count. */
        public final boolean tier;

        Knob(String key, String name, String about, int def, int min, int max) {
            this(key, name, about, def, min, max, false);
        }

        Knob(String key, String name, String about, int def, int min, int max, boolean tier) {
            this.key = key;
            this.name = name;
            this.about = about;
            this.def = def;
            this.min = min;
            this.max = max;
            this.tier = tier;
        }

        /** The fixed value. {@code p} is no longer read: a number saved by the old Advanced sliders has no effect. */
        public int get(SharedPreferences p) {
            return def;
        }
    }

    public static final Knob CHAT_TOKENS = new Knob("adv_chat_tokens", "Everyday reply length, tokens",
            "How long an ordinary answer may be. A cap, not a target: a model stops when it is done, so room costs nothing"
                    + " unless used. 160 cut answers off mid-sentence; 700 fits a full answer and a reasoning model's short think.",
            700, 64, 8000);
    public static final Knob BIG_TOKENS = new Knob("adv_big_tokens", "Long reply length, tokens",
            "For real work: writing, research, analysis. 3,000 fits about 2,200 words before it is cut.", 3000, 256, 32000);
    public static final Knob AGENT_STEPS = new Knob("adv_agent_steps", "Agent steps per task",
            "How many tool steps Ibiko (or a persona reading files) may take before reporting back.", 30, 3, 100);
    public static final Knob AGENT_TOKENS = new Knob("adv_agent_tokens", "Agent step length, tokens",
            "How much the model may write in one step (a whole file or a full report needs more).", 4000, 256, 32000);
    public static final Knob PASTE_CHARS = new Knob("adv_paste_chars", "Paste becomes a file at, characters",
            "A longer paste turns into an attachment card.", 1500, 200, 100000);
    public static final Knob IMAGE_SIDE = new Knob("adv_image_side", "Image size sent, pixels",
            "The long side of an attached photo. An image costs about (width × height) / 750 tokens: 1,280 keeps text in"
                    + " a screenshot readable at about 1,600 tokens, against 2,400 at 1,568.", 1280, 256, 4096);
    public static final Knob WEB_RESULTS = new Knob("adv_web_results", "Web results per search",
            "How many results a search lists.", 8, 1, 20);
    public static final Knob WEB_PAGES = new Knob("adv_web_pages", "Pages read per search",
            "How many of the results are opened and read.", 3, 0, 10);

    // How many workers run at once is no longer a fixed number: llm/WorkerPace sets it from how the provider answers
    // and how warm the phone is (four to start, up to eight, two at least).

    public static final Knob CLONE_FILES = new Knob("adv_clone_files", "Most files in a clone",
            "A repository with more files than this isn't cloned onto the device (a bigger number needs more storage and time).",
            5000, 100, 50000);

    public static final Knob OFFLINE_CONTEXT = new Knob("adv_offline_context", "Offline model memory, tokens",
            "How much of the conversation and the work the device's own model keeps in mind. More lets Ibiko work offline and"
                    + " remember longer chats, but takes more of the device's memory and starts slower.", 8192, 1024, 32768);
    public static final Knob SLOW_SPEED = new Knob("adv_slow_speed", "Offline model is slow below, tokens a second",
            "When the device's own model writes slower than this, Ibiko suggests a smaller size or less memory.", 4, 1, 50);

    public static final Knob MODEL_THREADS = new Knob("adv_model_threads", "Offline model threads (0 = automatic)",
            "How many cores the offline model uses. Automatic uses two. More than two makes the phone hum and does not go faster.",
            0, 0, 2);

    /**
     * The name the app answers to and the one it works for. It is text rather than a number, so it has no range: the
     * default is empty and the helper below falls back to "you". It is what every persona line reads, so the answer
     * changes everywhere at once.
     */
    public static final String NAME_KEY = "adv_name";
    public static final String NAME_DEF = "";

    /**
     * The user's name, or "you" when none has been set. Every line that speaks to them goes through here, so a name
     * typed once appears in the whole app.
     */
    public static String face(SharedPreferences p) {
        String n = p == null ? NAME_DEF : p.getString(NAME_KEY, NAME_DEF).trim();
        return n.isEmpty() ? "you" : n;
    }

    /** The name of a model tier, or null when it isn't a tier ("3" for a count). */
    public static final Knob TASK_TIER = new Knob("adv_task_tier", "Task model tier (0 automatic)",
            "Which model answers the task. Automatic lets Ibiko pick; a higher tier is slower and uses more of the plan.",
            0, 0, 4, true);

    public static final Knob BRAIN_TIER = new Knob("adv_brain_tier", "Brain model tier (0 automatic)",
            "Which model writes each step of the work. Automatic follows the task tier unless the step is small.",
            0, 0, 4, true);

    public static final Knob RUN_MODE = new Knob("adv_run_mode", "How long a job may run (0 quick … 4 autonomous)",
            "0 quick, 1 balanced (today's limits), 2 deep reasoning, 3 extended project, 4 autonomous development."
                    + " Deep and above do not use the short reply timer. Android can still stop the app; the Scribe record is what resumes.",
            2, 0, 4); // deep by default: the short reply timer stopped long jobs that were still working

    /** Marks that the one-time clear below has run (a new mark: it runs once more, when the sliders were removed). */
    static final String RESET_MARK = "adv_reset_fixed";

    /**
     * Once, on the version that removed Settings › Advanced: the numbers its sliders saved are deleted, so nothing
     * left behind is kept on the device. They are not read any more either. True when it ran now.
     */
    public static boolean resetOnce(SharedPreferences p) {
        if (p == null || p.getBoolean(RESET_MARK, false)) {
            return false;
        }
        SharedPreferences.Editor e = p.edit();
        for (Knob k : ALL) {
            e.remove(k.key);
        }
        e.putBoolean(RESET_MARK, true).apply();
        return true;
    }

    public static final Knob[] ALL = {CHAT_TOKENS, BIG_TOKENS, AGENT_STEPS, AGENT_TOKENS, PASTE_CHARS, IMAGE_SIDE, WEB_RESULTS, WEB_PAGES, CLONE_FILES, OFFLINE_CONTEXT, SLOW_SPEED, MODEL_THREADS, TASK_TIER, BRAIN_TIER, RUN_MODE};

    /** The name of a model tier, or null when it isn't a tier ("3" for a count). */
    public static String tierName(android.content.SharedPreferences p, int v) {
        if (!TASK_TIER.tier) return null;
        if (v <= 0) return "Automatic";
        String[] names = {"", "Fast", "Small", "Big", "Strong"};
        return v < names.length ? names[v] : "Strong";
    }
    /** The threads for the phone's own model: the setting, or the fast cores when it is automatic. */
    public static int modelThreads(android.content.SharedPreferences p) {
        int set = MODEL_THREADS.get(p);
        int chosen = set > 0 ? set : ai.ibiko.llm.LocalModel.threads(Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(2, chosen));
    }

    private Tuning() {
    }
}
