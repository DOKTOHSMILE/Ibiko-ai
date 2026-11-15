package ai.ibiko.main;

import ai.ibiko.MainActivity;

/**
 * The screen the user is looking at, and the screen that still owns a job. Leaving Ibiko can destroy the
 * screen while the job keeps running on it. The next screen is a new one: it finds the job here, shows that
 * it is still going, and redraws when the reply is saved. A message typed on the new screen is handed to the
 * job, not started as a second reply.
 */
public final class LiveWork {
    private static volatile MainActivity shown;
    private static volatile MainActivity owner;

    private LiveWork() {
    }

    /** The screen just came to the front. */
    public static void show(MainActivity a) {
        if (a != null) {
            shown = a;
        }
    }

    /** The screen is gone. The job, if this screen still owns it, is left running. */
    public static void hide(MainActivity a) {
        if (shown == a) {
            shown = null;
        }
    }

    /** This screen started a reply or a coding job. A later job replaces it. */
    public static void began(MainActivity a) {
        if (a != null) {
            owner = a;
        }
    }

    /** The reply or job this screen started has finished. */
    public static void ended(MainActivity a) {
        if (owner == a) {
            owner = null;
        }
    }

    public static MainActivity owner() {
        return owner;
    }

    public static MainActivity shown() {
        return shown;
    }

    /** The open screen, when it is not the one that just finished, redraws the chat. */
    public static void refresh(MainActivity from) {
        MainActivity now = shown;
        if (now == null || now == from) {
            return;
        }
        now.redrawChat();
    }

    /** The open screen should ask anything that was waiting on a screen that has closed. */
    public static void askWaiting(MainActivity from) {
        MainActivity now = shown;
        if (now == null || now == from) {
            return;
        }
        now.askWaiting();
    }

    /**
     * A message typed on {@code from} while another screen still owns the job. True when it was handed to that job.
     */
    public static boolean passSteer(MainActivity from, String text) {
        MainActivity job = owner;
        if (job == null || job == from || text == null || text.trim().isEmpty()) {
            return false;
        }
        job.acceptSteer(text);
        return true;
    }
}
