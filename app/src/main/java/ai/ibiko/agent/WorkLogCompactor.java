package ai.ibiko.agent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Keeps the coding agent's work log stable while it grows, the way CodeAssist's history compactor keeps an agent
 * turn cacheable. Re-sending a tool result byte-for-byte is cheap on a cached prefix; rewriting or dropping a little
 * text on every step invalidates that prefix at full price. So this compactor does nothing until the log crosses the
 * caller's budget, then elides old oversized tool results in one pass down to a lower target, and never brings an
 * elided result back. User text, the task and the most recent tool results are never touched.
 *
 * <p>Pure Java; no Android, no model.
 */
final class WorkLogCompactor {
    /** A tool result longer than this is worth eliding once it is old. */
    static final int MAX_TOOL_RESULT_CHARS = 4000;
    /** The last few tool results are the model's active working set and stay verbatim. */
    static final int KEEP_RECENT = 4;
    /** The marker left where an old result was left out. */
    static final String ELISION = "\n…(earlier tool result left out to fit the agent's context)…\n";

    private final Set<String> elided = new HashSet<String>();

    /** The fixed-size form used by the unit test: unchanged text comes back unchanged, not null. */
    String compact(String all) {
        String compacted = compact(all, 120_000, 60_000);
        return compacted == null ? all : compacted;
    }

    /**
     * Returns a compacted copy, or null when nothing was elided. {@code all} is read as a CharSequence so a caller
     * with a huge log does not have to copy it just to find out that nothing needs doing.
     */
    String compact(CharSequence all, int trigger, int target) {
        if (all == null || all.length() <= trigger) {
            return null;
        }
        List<Span> spans = toolSpans(all);
        int keepFrom = Math.max(0, spans.size() - KEEP_RECENT);
        int size = all.length();
        boolean added = false;
        for (int i = 0; i < keepFrom && size > target; i++) {
            Span s = spans.get(i);
            if (s.length() <= MAX_TOOL_RESULT_CHARS || s.marker || elided.contains(s.id)) {
                continue;
            }
            elided.add(s.id);
            added = true;
            size -= s.length();
        }
        if (!added) {
            return null;
        }
        StringBuilder b = new StringBuilder(Math.min(size + 512, all.length()));
        int at = 0;
        for (int i = 0; i < spans.size(); i++) {
            Span s = spans.get(i);
            b.append(all, at, s.from);
            if (i >= keepFrom || !elided.contains(s.id)) {
                b.append(all, s.from, s.to);
            } else {
                b.append(ELISION);
            }
            at = s.to;
        }
        b.append(all, at, all.length());
        return b.toString();
    }

    /** Forgets past elisions when a new task starts. */
    void reset() {
        elided.clear();
    }

    /**
     * The tool results in the log: from the start of a tool call to the end of the following {@code </result>}, with
     * its raw text as the stable identity (the same result re-read keeps the same id).
     *
     * <p>An elision marker is returned as a zero-cost span of its own so a subsequent pass preserves the exact bytes
     * already committed to the cached prefix.
     */
    private static List<Span> toolSpans(CharSequence all) {
        List<Span> out = new ArrayList<Span>();
        int at = 0;
        while (true) {
            int marker = indexOf(all, ELISION, at);
            int start = indexOf(all, "<tool ", at);
            if (marker >= 0 && (start < 0 || marker < start)) {
                out.add(new Span(marker, marker + ELISION.length(), ELISION, true));
                at = marker + ELISION.length();
                continue;
            }
            if (start < 0) {
                break;
            }
            int self = indexOf(all, "/>", start);
            int close = indexOf(all, ">", start);
            int callEnd = self >= 0 && self <= close + 1 ? self : close;
            if (callEnd < 0) {
                break;
            }
            int result = indexOf(all, "<result>\n", callEnd);
            if (result < 0) {
                result = indexOf(all, "<result>", callEnd);
            }
            if (result < 0) {
                break;
            }
            int body = result + (startsWith(all, "<result>\n", result) ? 9 : 8);
            int end = indexOf(all, "</result>", body);
            if (end < 0) {
                break;
            }
            end += "</result>".length();
            out.add(new Span(start, end, sub(all, start, end), false));
            at = end;
        }
        return out;
    }

    private static String sub(CharSequence all, int from, int to) {
        return all instanceof String ? ((String) all).substring(from, to) : all.subSequence(from, to).toString();
    }

    private static boolean startsWith(CharSequence all, String p, int at) {
        if (at + p.length() > all.length()) {
            return false;
        }
        for (int i = 0; i < p.length(); i++) {
            if (all.charAt(at + i) != p.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(CharSequence all, String p, int from) {
        for (int i = Math.max(0, from); i + p.length() <= all.length(); i++) {
            if (startsWith(all, p, i)) {
                return i;
            }
        }
        return -1;
    }

    private static final class Span {
        final int from, to;
        final String id;
        final boolean marker;

        Span(int from, int to, String id, boolean marker) {
            this.from = from;
            this.to = to;
            this.id = id;
            this.marker = marker;
        }

        int length() {
            return to - from;
        }
    }
}
