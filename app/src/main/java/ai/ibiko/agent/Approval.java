package ai.ibiko.agent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What waits for the pilot's yes before the agent does it (the permission check): shell commands, pushes and pull
 * requests, and any connector call that changes something on the pilot's accounts (creating, deleting, posting,
 * merging…). Reading, searching, listing and fetching go ahead. Writing files in the workspace goes ahead too
 * ("Accept edits", as in Claude Code); Plan mode changes nothing at all. Pure Java.
 */
public final class Approval {
    private Approval() {
    }

    /** Words in a connector's tool name that mean it changes something. */
    static final Pattern CHANGES = Pattern.compile("(?i)(^|[._\\-\\s/])(create|delete|remove|update|edit|write|post|send|reply"
            + "|comment|merge|push|close|reopen|archive|move|rename|upload|publish|invite|add|set|star|unstar|fork|approve"
            + "|dismiss|lock|unlock|transfer|assign|label|submit|cancel|rerun|trigger|dispatch|enable|disable|share|schedule"
            + "|like|follow|unfollow|block|mute|pin|unpin|tag|commit)(?=$|[._\\-\\s/A-Z]|s\\b|_)");

    /** Ibiko's own connector tools that only read. */
    static final java.util.Set<String> READS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "web.search", "docs.search", "docs.read", "calendar.week", "github.repos", "github.search", "github.issues",
            "github.file", "notion.search", "gmail.inbox", "gmail.search", "gmail.read", "social.search", "social.trending",
            "social.open"));

    /** Words in a tool's name that mean it only reads. */
    static final Pattern READING = Pattern.compile("(?i)(^|[._\\-\\s/])(get|list|search|read|find|fetch|view|show|describe"
            + "|query|lookup|count|status|info|inspect|browse|retrieve|check)(?=$|[._\\-\\s/]|s\\b|_)");

    /**
     * A connector call ("github.create_issue {...}") that may change something, and so asks first. Only a call known to
     * read goes ahead: Ibiko's own reading tools, or a tool whose name says it reads and nothing that changes. A list
     * of changing words let anything it didn't name through without a yes: gmail.draft (writes into Gmail), and an MCP
     * server's execute_sql, deploy, insert, drop, restart or revoke.
     */
    public static boolean changes(String call) {
        if (call == null) {
            return false;
        }
        String name = call.trim().split("\\s+", 2)[0];
        if (READS.contains(name.toLowerCase(Locale.US))) {
            return false;
        }
        // camelCase ("createIssue") is read as words too
        String words = name.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.US);
        if (CHANGES.matcher(words).find()) {
            return true;
        }
        return !READING.matcher(words).find(); // a tool whose name doesn't say it reads asks first
    }

    /** What the pilot is asked, in words. */
    public static String connectorQuestion(String call) {
        String c = call == null ? "" : call.trim();
        return "Use the connector: " + (c.length() > 400 ? c.substring(0, 400) + "…" : c);
    }
}
