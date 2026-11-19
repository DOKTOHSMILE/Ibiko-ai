package ai.ibiko.connect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A repository's .gitignore rules, as git reads them: one file per folder, each rule relative to its folder. Comments
 * and blank lines are skipped; "!" brings a path back; a trailing "/" matches folders only; a leading or middle "/"
 * anchors the rule to its folder (otherwise it matches a name at any depth); "*", "?", "[…]" and "**" work as in git.
 * The last matching rule wins. Pure Java.
 */
public final class GitIgnore {
    private static final class Rule {
        final String base;
        final Pattern pattern;
        final boolean negate, dirOnly;

        Rule(String base, Pattern pattern, boolean negate, boolean dirOnly) {
            this.base = base;
            this.pattern = pattern;
            this.negate = negate;
            this.dirOnly = dirOnly;
        }
    }

    private final List<Rule> rules = new ArrayList<Rule>();

    /** Adds the rules of one .gitignore, kept in the folder {@code base} ("" for the repository's root, else "a/b"). */
    public void add(String base, String text) {
        if (text == null) {
            return;
        }
        String b = base == null ? "" : base.replaceAll("^/+|/+$", "");
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            line = line.replaceAll("(?<!\\\\)\\s+$", ""); // trailing spaces are dropped unless escaped
            if (line.isEmpty()) {
                continue;
            }
            boolean negate = line.startsWith("!");
            if (negate) {
                line = line.substring(1);
            } else if (line.startsWith("\\!") || line.startsWith("\\#")) {
                line = line.substring(1);
            }
            boolean dirOnly = line.endsWith("/");
            if (dirOnly) {
                line = line.replaceAll("/+$", "");
            }
            if (line.isEmpty()) {
                continue;
            }
            boolean anchored = line.contains("/");
            if (line.startsWith("/")) {
                line = line.substring(1);
            }
            String glob = anchored ? line : "**/" + line;
            rules.add(new Rule(b, Pattern.compile(regex(glob)), negate, dirOnly));
        }
    }

    /** Reads every .gitignore under {@code root} (not inside .git). */
    public static GitIgnore of(File root) {
        GitIgnore g = new GitIgnore();
        read(root, root, g, 0);
        return g;
    }

    private static void read(File root, File dir, GitIgnore g, int depth) {
        if (depth > 40) {
            return;
        }
        File here = new File(dir, ".gitignore");
        if (here.isFile() && here.length() < 1_000_000) {
            try {
                String rel = root.toPath().relativize(dir.toPath()).toString().replace(File.separatorChar, '/');
                g.add(rel, new String(Files.readAllBytes(here.toPath()), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // an unreadable .gitignore adds no rules
            }
        }
        File[] kids = dir.listFiles();
        if (kids == null) {
            return;
        }
        for (File k : kids) {
            if (k.isDirectory() && !k.getName().equals(".git") && !Files.isSymbolicLink(k.toPath())) {
                read(root, k, g, depth + 1);
            }
        }
    }

    /** Whether {@code path} ("a/b/c.txt", relative to the root) is ignored: it, or a folder it is in. */
    public boolean ignored(String path, boolean isDir) {
        String p = path == null ? "" : path.replaceAll("^/+|/+$", "");
        if (p.isEmpty()) {
            return false;
        }
        // a folder that is ignored hides everything in it (git does not look inside, so "!" can't bring a file back)
        String[] parts = p.split("/");
        StringBuilder at = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            at.append(i == 0 ? "" : "/").append(parts[i]);
            if (match(at.toString(), true)) {
                return true;
            }
        }
        return match(p, isDir);
    }

    private boolean match(String path, boolean isDir) {
        boolean out = false;
        for (Rule r : rules) {
            if (r.dirOnly && !isDir) {
                continue;
            }
            String rel;
            if (r.base.isEmpty()) {
                rel = path;
            } else if (path.startsWith(r.base + "/")) {
                rel = path.substring(r.base.length() + 1);
            } else {
                continue; // a rule applies only inside its own folder
            }
            if (r.pattern.matcher(rel).matches()) {
                out = !r.negate;
            }
        }
        return out;
    }

    /** A git glob as a regular expression over a "/"-separated path. */
    static String regex(String glob) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' && i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                boolean slashAfter = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                if (slashAfter) {
                    b.append("(?:.*/)?"); // "**/" : any folders, or none
                    i += 2;
                } else {
                    b.append(".*"); // a trailing "**": everything inside
                    i += 1;
                }
            } else if (c == '*') {
                b.append("[^/]*");
            } else if (c == '?') {
                b.append("[^/]");
            } else if (c == '[') {
                int end = glob.indexOf(']', i + 1);
                if (end < 0) {
                    b.append("\\[");
                } else {
                    String set = glob.substring(i + 1, end).replace("\\", "\\\\");
                    if (set.startsWith("!")) {
                        set = "^" + set.substring(1);
                    }
                    b.append('[').append(set).append(']');
                    i = end;
                }
            } else if (c == '\\' && i + 1 < glob.length()) {
                b.append(Pattern.quote(String.valueOf(glob.charAt(++i))));
            } else {
                b.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return b.toString();
    }
}
