package ai.ibiko.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Buttons that come with a reply: when the person has a clear choice of next steps, the brain ends its reply with one
 * line, {@code [buttons: Yes, do it | Show me first | Not now]}. The line is taken out of the words; the chat shows
 * the choices as buttons under the newest reply, and a tap sends that choice as the person's next message. Pure Java.
 */
public final class ReplyButtons {
    /** At most this many buttons; each at most {@link #MOST_CHARS} characters. */
    public static final int MOST = 4, MOST_CHARS = 40;

    /** What the brain is told (added to its instructions). */
    public static final String HOW = "\nWhen the person has a clear choice of next steps (yes or no, which option, what to do"
            + " next), you may end your reply with one last line of buttons they can tap, in exactly this form:"
            + " [buttons: First choice | Second choice | Third choice]. At most 4, each 2 to 6 words, worded as the person"
            + " would say it. Only when it truly helps; never for a plain answer.";

    private static final Pattern LINE = Pattern.compile("(?im)^[ \\t]*\\[\\s*buttons?\\s*:\\s*([^\\]\\n]*)\\][ \\t]*$");

    /** A reply's words and its buttons. */
    public static final class Split {
        public final String text;
        public final List<String> buttons;

        Split(String text, List<String> buttons) {
            this.text = text;
            this.buttons = buttons;
        }
    }

    private ReplyButtons() {
    }

    /** The reply without its buttons line (the last one counts), and the buttons it offered. */
    public static Split split(String reply) {
        if (reply == null || reply.indexOf('[') < 0) {
            return new Split(reply == null ? "" : reply, new ArrayList<String>());
        }
        Matcher m = LINE.matcher(reply);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        if (last == null) {
            return new Split(reply, new ArrayList<String>());
        }
        List<String> out = new ArrayList<String>();
        for (String b : last.split("\\|")) {
            String t = b.trim().replaceAll("^[\"'“]+|[\"'”]+$", "").trim();
            if (!t.isEmpty() && t.length() <= MOST_CHARS && !out.contains(t) && out.size() < MOST) {
                out.add(t);
            }
        }
        String text = LINE.matcher(reply).replaceAll("").replaceAll("\\n{3,}", "\n\n").trim();
        return new Split(text, out);
    }

    /** Words being written now, without a buttons line that has started (so it never shows half-written). */
    public static String writing(String soFar) {
        if (soFar == null) {
            return "";
        }
        int at = soFar.toLowerCase(java.util.Locale.US).lastIndexOf("[button");
        if (at >= 0 && (at == 0 || soFar.charAt(at - 1) == '\n' || soFar.charAt(at - 1) == ' ')) {
            return soFar.substring(0, at).trim();
        }
        return split(soFar).text;
    }
}
