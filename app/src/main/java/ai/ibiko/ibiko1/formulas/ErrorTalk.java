package ai.ibiko.ibiko1.formulas;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A pasted error message explained to a person: what it means, what usually causes it, and the fix, with an example in
 * the language it came from. The errors are knowledge (knowledge/code/errors.tsv, tools/build_code_errors.py): each was
 * made for real by a small program, run with the real interpreter or compiler, and its pattern matched against what was
 * printed, so the wording is a real tool's. A newer file on the phone replaces the shipped one. Pure Java.
 *
 * <pre>
 * E1  find     the first known error in the message, where it's printed as output, not written in code
 *              ("except KeyError: pass" is code; "KeyError: 'b'" on its own line is an error)
 * E2  where    the file and line, when the message says (a traceback, "main.go:2:20:", "Main.java:1:")
 * E3  explain  what it means, its usual causes, the fix and an example, the names from the message filled in
 * </pre>
 */
public final class ErrorTalk {
    /** One known error. */
    public static final class Known {
        public final String id, lang, means, causes, fix, example, seen;
        final Pattern pattern;

        Known(String[] f) {
            id = f[0];
            lang = f[1];
            pattern = Pattern.compile(f[2]);
            means = f[3];
            causes = f[4];
            fix = f[5];
            example = f[6].replace("\\n", "\n");
            seen = f[7];
        }
    }

    /** An error found in a message: which one, the line it was on, its names, and where it happened. */
    public static final class Found {
        public final Known known;
        public final String line;
        final List<String> groups = new ArrayList<String>();
        public String file;
        public int at;

        Found(Known known, String line, Matcher m) {
            this.known = known;
            this.line = line;
            for (int i = 1; i <= m.groupCount(); i++) {
                groups.add(m.group(i));
            }
        }
    }

    private final List<Known> known = new ArrayList<Known>();

    public ErrorTalk(InputStream tsv) {
        if (tsv == null) {
            return;
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(tsv, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] f = line.split("\t");
                if (!line.startsWith("#") && f.length >= 8) {
                    known.add(new Known(f));
                }
            }
        } catch (IOException ignored) {
            // none: the message goes on to the rest of Ibiko
        }
    }

    public int size() {
        return known.size();
    }

    /** E1: what may come before an error on its line when a tool prints it (and nothing that code would have). */
    private static final Pattern PRINTED = Pattern.compile("^[\\s>|]*(?:"
            + "\\S+:\\d+(?::\\d+)?:\\s*(?:error:\\s*)?"            // main.go:2:20:  Main.java:1: error:
            + "|(?:\\S+:\\s*)?line \\d+:\\s*(?:\\d+\\s+)?"          // bash: line 1:   main.sh: line 2: 1234
            + "|(?:Uncaught|Caused by:|Exception in thread \"[^\"]*\")\\s*(?:[a-z]\\w*\\.)*"
            + "|(?:[a-z]\\w*\\.)+"                                 // java.lang.
            + "|(?:PHP )?(?:Warning|Fatal error|Notice|Parse error):\\s*"
            + "|error(?:\\[\\w+\\])?:\\s*|Error:\\s*|fatal:\\s*|bash:\\s*|zsh:\\s*|sh:\\s*(?:\\d+:\\s*)?"
            + "|\\[[^\\]]*\\]\\s*|\\$\\s*"
            + ")?$");

    /** E1: or said in words before it: "and I get KeyError: 'coffee'", "it says: …", "the error is …". */
    private static final Pattern SAID = Pattern.compile("(?i)^.*\\b(?:i (?:get|got|keep getting|am getting|see|saw)|i'm getting|getting|"
            + "it (?:says|said|gives|gave|shows|showed|throws|threw|returns)|(?:the |this |an? )?error(?: message)?(?: is| says| was)?|says|shows)"
            + "\\s*(?:this|the error|an error|that)?\\s*:?\\s*[\"“'`]?$");

    /** E1–E2: the first known error printed in the message, or null. */
    public Found find(String message) {
        if (message == null || known.isEmpty()) {
            return null;
        }
        Found best = null;
        int bestAt = Integer.MAX_VALUE;
        for (Known k : known) {
            Matcher m = k.pattern.matcher(message);
            while (m.find()) {
                int start = message.lastIndexOf('\n', m.start()) + 1;
                String before = message.substring(start, m.start());
                boolean printed = PRINTED.matcher(before).matches();
                if (printed || SAID.matcher(before).matches()) {
                    if (m.start() < bestAt) {
                        int end = message.indexOf('\n', m.end());
                        // printed: the whole line, as the tool wrote it; said: the error from where it starts
                        String line = message.substring(printed ? start : m.start(), end < 0 ? message.length() : end).trim();
                        best = new Found(k, line, m);
                        bestAt = m.start();
                    }
                    break;
                }
            }
        }
        if (best != null) {
            where(best, message);
        }
        return best;
    }

    private static final Pattern[] WHERE = {
        Pattern.compile("File \"([^\"]+)\", line (\\d+)"),                         // Python: the last one is where it broke
        Pattern.compile("(?:\\(|\\bat )([\\w./\\\\-]+\\.(?:m?js|cjs|ts|jsx|tsx)):(\\d+):\\d+"), // Node: "(app.js:3:17)" or "at /srv/form.js:8:31"
        Pattern.compile("at [\\w.$<>]+\\((\\w+\\.(?:java|kt)):(\\d+)\\)"),          // Java
        Pattern.compile("-->\\s*([\\w./-]+\\.rs):(\\d+)"),                             // Rust
        Pattern.compile("([\\w./-]+\\.(?:go|java|c|cpp|kt|ts|php)):(\\d+)"),           // file:line
        Pattern.compile(" in ([\\w./-]+\\.php) on line (\\d+)")};                     // PHP

    /** E2: the file and line it happened at, when the message says. */
    private static void where(Found f, String message) {
        for (Pattern p : WHERE) {
            Matcher m = p.matcher(message);
            String file = null;
            int line = 0;
            while (m.find()) {
                if (m.group(1).contains("node:internal") || m.group(1).startsWith("<")) {
                    continue;
                }
                file = m.group(1);
                line = Integer.parseInt(m.group(2));
                if (p != WHERE[0]) {
                    break; // the first frame, except in Python, where the last is the line that broke
                }
            }
            if (file != null) {
                f.file = file;
                f.at = line;
                return;
            }
        }
    }

    /** E3: the error explained, with its names filled in. */
    public static String explain(Found f) {
        return explain(f, null);
    }

    /**
     * E4: the line of the person's own code the error is about: the line the error names, when the code has it; else the
     * first line that uses the name the error is about (the missing module, the undefined variable, the key). Null: none.
     */
    public static String[] inCode(Found f, String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        String[] lines = code.split("\n", -1);
        if (f.at > 0 && f.at <= lines.length && !lines[f.at - 1].trim().isEmpty()) {
            return new String[]{String.valueOf(f.at), lines[f.at - 1].trim()};
        }
        for (String g : f.groups) {
            if (g == null || g.length() < 2 || !g.matches("[\\w.$]+")) {
                continue;
            }
            Pattern word = Pattern.compile("(?<![\\w$])" + Pattern.quote(g) + "(?![\\w$])");
            for (int i = 0; i < lines.length; i++) {
                if (word.matcher(lines[i]).find()) {
                    return new String[]{String.valueOf(i + 1), lines[i].trim()};
                }
            }
        }
        return null;
    }

    /** E3–E4: the error explained, pointing at the line of the code pasted with it, when there is one. */
    public static String explain(Found f, String code) {
        Known k = f.known;
        StringBuilder b = new StringBuilder("That's ").append(article(lang(k.lang))).append(" error: ").append(f.line).append("\n\n");
        b.append("What it means: ").append(fill(k.means, f)).append(".\n\n");
        String[] mine = inCode(f, code);
        if (mine != null) {
            b.append("In your code: line ").append(mine[0]).append(": ").append(mine[1]).append("\n\n");
        } else if (f.file != null) {
            b.append("Where: ").append(f.file).append(f.at > 0 ? ", line " + f.at : "").append(". Look there first.\n\n");
        }
        b.append("Usually because: ").append(fill(k.causes, f)).append(".\n\n");
        b.append("The fix: ").append(fill(k.fix, f)).append(":\n\n```").append(fence(k.lang)).append("\n").append(fill(k.example, f))
                .append("\n```");
        return b.toString();
    }

    private static String fill(String s, Found f) {
        String out = s;
        for (int i = 0; i < f.groups.size(); i++) {
            String g = f.groups.get(i);
            if (g != null) {
                out = out.replace("{" + (i + 1) + "}", g);
            }
        }
        // an alternative the message didn't take: its name is the first one it did
        String any = null;
        for (String g : f.groups) {
            if (g != null) {
                any = g;
                break;
            }
        }
        return any == null ? out : out.replaceAll("\\{\\d\\}", Matcher.quoteReplacement(any));
    }

    private static String lang(String id) {
        switch (id) {
            case "python": return "Python";
            case "javascript": return "JavaScript";
            case "java": return "Java";
            case "go": return "Go";
            case "rust": return "Rust";
            case "c": return "C";
            case "php": return "PHP";
            default: return "shell";
        }
    }

    private static String article(String word) {
        return ("AEIOU".indexOf(word.charAt(0)) >= 0 ? "an " : "a ") + word;
    }

    private static String fence(String id) {
        return id.equals("shell") ? "bash" : id;
    }

    // ------------------------------------------------------------------------------------------------- follow-ups

    /** What a follow-up about the error just explained asks for. */
    public enum Ask { FIX, WHY, EXAMPLE }

    private static final Pattern FIX = Pattern.compile("(?i)^\\s*(?:so |ok,? |okay,? )?(?:how (?:do|can|should) i (?:fix|solve|sort out|get rid of|stop) (?:it|this|that)"
            + "|what (?:do|should) i do(?: now| about it)?|how to fix (?:it|this|that)|fix (?:it|this|that)(?: for me)?|what'?s the fix)\\s*[?.!]*$");
    private static final Pattern WHY = Pattern.compile("(?i)^\\s*(?:but )?(?:why(?: does| did| is)? (?:it|this|that)(?: happen(?:ing)?)?|why|what causes? (?:it|this|that)"
            + "|what(?:'s| is) causing (?:it|this|that)|how did (?:it|this|that) happen)\\s*[?.!]*$");
    private static final Pattern EXAMPLE = Pattern.compile("(?i)^\\s*(?:can you )?(?:show me|give me)? ?(?:an |the )?(?:example|code)(?: of the fix| for (?:it|the fix))?\\s*[?.!]*$");

    /** A follow-up about the error just explained ("how do I fix it?", "why?"), or null. */
    public static Ask followUp(String message) {
        String m = message == null ? "" : message.trim();
        return FIX.matcher(m).matches() ? Ask.FIX : WHY.matcher(m).matches() ? Ask.WHY : EXAMPLE.matcher(m).matches() ? Ask.EXAMPLE : null;
    }

    /** The answer to a follow-up: the part of the explanation asked for, said on its own. */
    public static String followed(Found f, Ask ask, String code) {
        Known k = f.known;
        String example = "```" + fence(k.lang) + "\n" + fill(k.example, f) + "\n```";
        String[] mine = inCode(f, code);
        String at = mine == null ? "" : " Start at line " + mine[0] + " of your code: " + mine[1];
        switch (ask) {
            case WHY:
                return "It happens because " + fill(k.means, f) + ". The usual reasons: " + fill(k.causes, f) + "." + at;
            case EXAMPLE:
                return "Here's the fix in code:\n\n" + example;
            default:
                return "To fix it: " + fill(k.fix, f) + "." + at + "\n\n" + example + "\n\nRun it again after: if a new error comes up, "
                        + "paste it and I'll read that one too.";
        }
    }
}
