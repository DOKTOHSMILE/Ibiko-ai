package ai.ibiko.social;

import android.view.ViewGroup;

/**
 * Where a post can be made on screen: the posting screen ({@code PostingActivity}) lends its frame while it is shown,
 * and {@link SocialPoster} puts the network's page in it instead of a hidden one. A page on screen is the page as the
 * person sees it: drawn and run at full speed, so composers that never open in a hidden page open there, and the
 * person watches every step. Nothing posts on screen unless that screen is open.
 */
public final class PostWindow {
    private static volatile ViewGroup frame;

    private PostWindow() {
    }

    /** The posting screen is showing: posts go in this frame. Main thread. */
    public static void attach(ViewGroup g) {
        frame = g;
    }

    /** The posting screen went away: posts are made in a hidden page again. Main thread. */
    public static void detach(ViewGroup g) {
        if (frame == g) {
            frame = null;
        }
    }

    /** The frame a post is shown in, or null (hidden). */
    static ViewGroup frame() {
        return frame;
    }

    /** True while the posting screen is open. */
    public static boolean open() {
        return frame != null;
    }
}
